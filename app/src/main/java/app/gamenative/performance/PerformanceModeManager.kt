package app.gamenative.performance

import android.content.Context
import app.gamenative.utils.ContainerUtils
import com.winlator.box86_64.Box86_64Preset
import com.winlator.container.Container
import com.winlator.container.ContainerData
import com.winlator.fexcore.FEXCorePreset
import java.io.File
import java.util.Locale
import org.json.JSONObject
import timber.log.Timber

enum class TuningMode(val id: String) {
    OFF("OFF"),
    PERFORMANCE("PERFORMANCE"),
    ECO("ECO"),
    ;

    companion object {
        fun fromRaw(raw: String?, legacyPerformanceMode: Boolean = false): TuningMode {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                PERFORMANCE.id -> PERFORMANCE
                ECO.id -> ECO
                OFF.id -> OFF
                else -> if (legacyPerformanceMode) PERFORMANCE else OFF
            }
        }

        fun fromContainer(container: Container): TuningMode {
            val raw = container.getExtra("tuningMode", "")
            val legacyPerf = container.getExtra("performanceMode", "false").toBoolean()
            return fromRaw(raw, legacyPerf)
        }
    }
}

/**
 * Per-Container Universal Performance / Eco Tuning Manager (GameNothing 2.0-patch4).
 *
 * Manages the disk-persisted tri-state (`OFF` / `PERFORMANCE` / `ECO`) snapshot/restore
 * lifecycle without root.
 *
 * Hard invariants — NEVER touches:
 * - User FPS Cap (`fpsLimiterEnabled`, `DXVK_FRAME_RATE`, etc.)
 * - Online / Xbox Live / Network flags
 * - Proton / Wine version, DXVK / VKD3D version, FEX / Box64 version
 * - Graphics Driver or Display Resolution
 * - GameNative built-in presets
 */
object PerformanceModeManager {

    private const val KEY_DISPLAY_SERVER = "displayServer"
    private const val KEY_FEXCORE_PRESET = "fexcorePreset"
    private const val KEY_BOX64_PRESET = "box64Preset"
    private const val KEY_CPU_LIST = "cpuList"
    private const val KEY_CPU_LIST_WOW64 = "cpuListWoW64"
    private const val KEY_ROOTLESS_GAME_PINNING = "rootlessGamePinningEnabled"
    private const val KEY_ADAPTIVE_FPS_CAP_HAD_VALUE = "adaptiveFpsCapHadValue"
    private const val KEY_ADAPTIVE_FPS_CAP_ENABLED = "adaptiveFpsCapEnabled"

    /**
     * Canonical set of normalized [ContainerData] keys managed by `tuningMode`
     * (`OFF` / `PERFORMANCE` / `ECO`).
     */
    val TUNING_MANAGED_KEYS: Set<String> = setOf(
        KEY_DISPLAY_SERVER,
        KEY_FEXCORE_PRESET,
        KEY_BOX64_PRESET,
        KEY_CPU_LIST,
        KEY_CPU_LIST_WOW64,
        KEY_ROOTLESS_GAME_PINNING,
    )

    private const val MAX_WOW64_CORES = 4

    data class CpuSelection(
        val cores64: List<Int>,
        val cores32: List<Int>,
    ) {
        val cpuList: String get() = cores64.joinToString(",")
        val cpuListWoW64: String get() = cores32.joinToString(",")
    }

    /**
     * Returns the full comma-separated CPU core list `"0,1,...,N-1"` for the device.
     */
    fun getAllCpuCoresList(coreCountOverride: Int? = null): String {
        val count = (coreCountOverride ?: Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
        return (0 until count).joinToString(",")
    }

    /**
     * Reads `/sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq` (in kHz) for online cores.
     */
    fun readCpuMaxFreqsKhz(coreCountOverride: Int? = null): Map<Int, Long> {
        val count = (coreCountOverride ?: Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
        val result = LinkedHashMap<Int, Long>()
        for (core in 0 until count) {
            val file = File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
            val khz = runCatching {
                if (file.exists() && file.canRead()) {
                    file.readText().trim().toLongOrNull() ?: 0L
                } else {
                    0L
                }
            }.getOrDefault(0L)
            if (khz > 0L) {
                result[core] = khz
            }
        }
        return result
    }

    /**
     * Computes `PERFORMANCE` CPU affinity:
     * - 64-bit (`cpuList`): all available cores (`0..N-1`).
     * - 32-bit (`cpuListWoW64`): strictly capped to at most 4 highest-frequency cores.
     */
    fun computePerformanceCpuSelection(
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): CpuSelection {
        val freqs = coreFreqsKhz ?: readCpuMaxFreqsKhz(coreCountOverride)
        val totalCores = (coreCountOverride ?: freqs.keys.maxOrNull()?.plus(1) ?: Runtime.getRuntime().availableProcessors())
            .coerceAtLeast(1)
        val allCores = (0 until totalCores).toList()
        val target32Count = minOf(MAX_WOW64_CORES, totalCores)

        val top32Cores = if (freqs.size >= 2) {
            allCores
                .sortedWith(
                    compareByDescending<Int> { freqs[it] ?: 0L }
                        .thenByDescending { it },
                )
                .take(target32Count)
                .sorted()
        } else {
            allCores.takeLast(target32Count)
        }

        return CpuSelection(cores64 = allCores, cores32 = top32Cores)
    }

    /**
     * Computes `ECO` CPU affinity:
     * - Groups cores by ascending max frequency (`LITTLE`, `MEDIUM`, `PRIME`).
     * - 64-bit (`cpuList`): All `LITTLE` + 1 `MEDIUM`, rounded to a strictly even count in `{2, 4, 6}`
     *   (never selecting all 8 cores on an 8-core SoC).
     *   For 2-cluster SoCs (e.g. 6 Performance + 2 Prime), picks `4` or `6` cores from the lower cluster (never `8`).
     *   Fallback if sysfs is unreadable or uniform: first `min(4, totalCores)` even cores (e.g. `0,1,2,3`).
     * - 32-bit (`cpuListWoW64`): Eco selection capped to at most 4 even cores (`2` or `4`;
     *   if Eco picked 6 cores, assigns the top 4 of those 6).
     */
    fun computeEcoCpuSelection(
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): CpuSelection {
        val freqs = coreFreqsKhz ?: readCpuMaxFreqsKhz(coreCountOverride)
        val totalCores = (coreCountOverride ?: freqs.keys.maxOrNull()?.plus(1) ?: Runtime.getRuntime().availableProcessors())
            .coerceAtLeast(1)

        if (totalCores < 2) {
            return CpuSelection(cores64 = listOf(0), cores32 = listOf(0))
        }

        val maxEcoCores = minOf(6, if (totalCores % 2 == 0) totalCores - 2 else totalCores - 1)
            .let { if (it >= 2 && it % 2 == 1) it - 1 else it }
            .coerceAtLeast(2)

        val distinctClusters: List<List<Int>> = if (freqs.size >= 2) {
            freqs.entries
                .groupBy { it.value }
                .entries
                .sortedBy { it.key }
                .map { entry -> entry.value.map { it.key }.sorted() }
        } else {
            emptyList()
        }

        val eco64: List<Int> = when {
            distinctClusters.size < 2 -> {
                // Sysfs unreadable or uniform frequency across all cores
                val fallbackCount = (minOf(4, totalCores) / 2 * 2).coerceAtLeast(2)
                (0 until fallbackCount).toList()
            }
            distinctClusters.size == 2 -> {
                // 2-cluster SoC (e.g. 6 Performance + 2 Prime, or 4 LITTLE + 4 BIG)
                val lowerCluster = distinctClusters[0]
                val upperCluster = distinctClusters[1]
                val selected = lowerCluster.take(maxEcoCores).toMutableList()
                if (selected.size % 2 == 1) {
                    if (selected.size + 1 <= maxEcoCores && selected.size < lowerCluster.size) {
                        selected.add(lowerCluster[selected.size])
                    } else if (selected.size == 1 && upperCluster.isNotEmpty() && maxEcoCores >= 2) {
                        selected.add(upperCluster.first())
                    } else if (selected.size > 2) {
                        selected.removeAt(selected.lastIndex)
                    }
                }
                if (selected.size < 2) {
                    for (core in upperCluster) {
                        if (selected.size >= 2) break
                        if (core !in selected) selected.add(core)
                    }
                }
                selected.sorted()
            }
            else -> {
                // 3+ cluster SoC: LITTLE (lowest) + MEDIUM (middle) + PRIME (highest)
                val little = distinctClusters.first()
                val medium = distinctClusters.subList(1, distinctClusters.lastIndex).flatten()

                val selected = ArrayList<Int>(little.size + 2)
                selected.addAll(little)
                var mediumIdx = 0
                if (mediumIdx < medium.size) {
                    selected.add(medium[mediumIdx++])
                }

                // If initial count is odd, add 1 more MEDIUM if <= maxEcoCores, else drop 1 LITTLE
                if (selected.size % 2 == 1) {
                    if (selected.size + 1 <= maxEcoCores && mediumIdx < medium.size) {
                        selected.add(medium[mediumIdx++])
                    } else if (selected.size > 2) {
                        selected.removeAt(0)
                    }
                }

                // Clamp to even value in {2, 4, 6} <= maxEcoCores
                while (selected.size > maxEcoCores) {
                    selected.removeAt(0)
                }
                if (selected.size % 2 == 1 && selected.size > 2) {
                    selected.removeAt(0)
                }
                if (selected.size < 2) {
                    val fallbackCount = (minOf(4, totalCores) / 2 * 2).coerceAtLeast(2)
                    (0 until fallbackCount).toList()
                } else {
                    selected.sorted()
                }
            }
        }

        val eco32: List<Int> = if (eco64.size <= MAX_WOW64_CORES) {
            eco64
        } else {
            // Eco picked 6 cores -> assign the top 4 of those 6 (preferring higher freq / index)
            eco64
                .sortedWith(
                    compareByDescending<Int> { freqs[it] ?: 0L }
                        .thenByDescending { it },
                )
                .take(MAX_WOW64_CORES)
                .sorted()
        }

        return CpuSelection(cores64 = eco64, cores32 = eco32)
    }

    /**
     * Formats a 1-line preview of assigned cores for the Performance tab UI.
     */
    fun formatCorePreview(
        config: ContainerData,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): String {
        val mode = TuningMode.fromRaw(config.tuningMode)
        val (cores64, cores32) = when (mode) {
            TuningMode.PERFORMANCE -> {
                val sel = computePerformanceCpuSelection(coreCountOverride, coreFreqsKhz)
                (config.cpuList.ifBlank { sel.cpuList }) to (config.cpuListWoW64.ifBlank { sel.cpuListWoW64 })
            }
            TuningMode.ECO -> {
                val sel = computeEcoCpuSelection(coreCountOverride, coreFreqsKhz)
                (config.cpuList.ifBlank { sel.cpuList }) to (config.cpuListWoW64.ifBlank { sel.cpuListWoW64 })
            }
            TuningMode.OFF -> {
                val fallback64 = config.cpuList.ifBlank { getAllCpuCoresList(coreCountOverride) }
                val fallback32 = config.cpuListWoW64.ifBlank {
                    computePerformanceCpuSelection(coreCountOverride, coreFreqsKhz).cpuListWoW64
                }
                fallback64 to fallback32
            }
        }
        return "64-bit: $cores64 · 32-bit: $cores32"
    }

    /**
     * Switches [config] to [targetMode] (`OFF`, `PERFORMANCE`, or `ECO`).
     *
     * - `OFF -> PERFORMANCE/ECO`: snapshots ONLY managed fields into `tuningModeSnapshot`.
     * - `PERFORMANCE <-> ECO`: preserves the original `OFF` baseline `tuningModeSnapshot`.
     * - `PERFORMANCE/ECO -> OFF`: restores the original `OFF` baseline from `tuningModeSnapshot`.
     * - NEVER touches user FPS cap (`fpsLimiterEnabled`), Online/Xbox Live, Proton/Wine,
     *   DXVK/VKD3D, FEX/Box64 version, Graphics Driver, or Display Resolution.
     */
    fun setTuningMode(
        config: ContainerData,
        targetMode: TuningMode,
        context: Context? = null,
        appId: String? = null,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): ContainerData {
        val currentMode = TuningMode.fromRaw(config.tuningMode)
        if (currentMode == targetMode) {
            return config
        }

        val existingSnapshot = if (currentMode != TuningMode.OFF && config.tuningModeSnapshot.isNotBlank()) {
            runCatching { JSONObject(config.tuningModeSnapshot) }.getOrNull()
        } else {
            null
        }

        if (targetMode == TuningMode.OFF) {
            return restoreFromSnapshot(config, existingSnapshot, context, appId)
        }

        val powerProfileFile = resolvePowerProfileFile(context, appId)
        var hadAdaptiveCap = existingSnapshot?.optBoolean(KEY_ADAPTIVE_FPS_CAP_HAD_VALUE, false) ?: false
        var priorAdaptiveCap = existingSnapshot?.optBoolean(KEY_ADAPTIVE_FPS_CAP_ENABLED, false) ?: false

        // When entering from OFF, read current .power_profile.json adaptiveFpsCapEnabled before altering
        if (existingSnapshot == null && powerProfileFile != null && powerProfileFile.exists()) {
            runCatching {
                val json = JSONObject(powerProfileFile.readText(Charsets.UTF_8))
                hadAdaptiveCap = true
                priorAdaptiveCap = json.optBoolean("adaptiveFpsCapEnabled", false)
            }.onFailure {
                Timber.w(it, "PerformanceModeManager: failed to read .power_profile.json")
            }
        }

        val snapshotJson = existingSnapshot ?: JSONObject().apply {
            put(KEY_DISPLAY_SERVER, config.displayServer)
            put(KEY_FEXCORE_PRESET, config.fexcorePreset)
            put(KEY_BOX64_PRESET, config.box64Preset)
            put(KEY_CPU_LIST, config.cpuList.ifBlank { Container.getFallbackCPUList() })
            put(KEY_CPU_LIST_WOW64, config.cpuListWoW64.ifBlank { Container.getFallbackCPUListWoW64() })
            put(KEY_ROOTLESS_GAME_PINNING, config.rootlessGamePinningEnabled)
            put(KEY_ADAPTIVE_FPS_CAP_HAD_VALUE, hadAdaptiveCap)
            put(KEY_ADAPTIVE_FPS_CAP_ENABLED, priorAdaptiveCap)
        }

        val updated = when (targetMode) {
            TuningMode.PERFORMANCE -> {
                // Disable only hidden dynamic adaptive FPS throttler (separate from user's fpsLimiterEnabled)
                if (powerProfileFile != null && powerProfileFile.exists()) {
                    runCatching {
                        val json = JSONObject(powerProfileFile.readText(Charsets.UTF_8))
                        json.put("adaptiveFpsCapEnabled", false)
                        powerProfileFile.writeText(json.toString(), Charsets.UTF_8)
                    }.onFailure {
                        Timber.w(it, "PerformanceModeManager: failed to update .power_profile.json on PERFORMANCE")
                    }
                }
                val cpu = computePerformanceCpuSelection(coreCountOverride, coreFreqsKhz)
                config.copy(
                    displayServer = Container.DISPLAY_SERVER_X11,
                    fexcorePreset = FEXCorePreset.EXTREME,
                    box64Preset = Box86_64Preset.PERFORMANCE,
                    cpuList = cpu.cpuList,
                    cpuListWoW64 = cpu.cpuListWoW64,
                    rootlessGamePinningEnabled = true,
                    tuningMode = TuningMode.PERFORMANCE.id,
                    tuningModeSnapshot = snapshotJson.toString(),
                )
            }
            TuningMode.ECO -> {
                // Restore hidden adaptive FPS cap to baseline if switching PERFORMANCE -> ECO
                if (powerProfileFile != null && powerProfileFile.exists() && hadAdaptiveCap) {
                    runCatching {
                        val json = JSONObject(powerProfileFile.readText(Charsets.UTF_8))
                        json.put("adaptiveFpsCapEnabled", priorAdaptiveCap)
                        powerProfileFile.writeText(json.toString(), Charsets.UTF_8)
                    }.onFailure {
                        Timber.w(it, "PerformanceModeManager: failed to restore .power_profile.json on ECO")
                    }
                }
                val baselineDisplayServer = snapshotJson.optString(KEY_DISPLAY_SERVER, config.displayServer)
                    .ifBlank { config.displayServer }
                val cpu = computeEcoCpuSelection(coreCountOverride, coreFreqsKhz)
                config.copy(
                    displayServer = baselineDisplayServer,
                    fexcorePreset = FEXCorePreset.INTERMEDIATE,
                    box64Preset = Box86_64Preset.INTERMEDIATE,
                    cpuList = cpu.cpuList,
                    cpuListWoW64 = cpu.cpuListWoW64,
                    rootlessGamePinningEnabled = false,
                    tuningMode = TuningMode.ECO.id,
                    tuningModeSnapshot = snapshotJson.toString(),
                )
            }
            TuningMode.OFF -> config
        }

        persistIfContainerExists(context, appId, updated)
        return updated
    }

    fun enablePerformanceMode(
        config: ContainerData,
        context: Context? = null,
        appId: String? = null,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): ContainerData = setTuningMode(
        config = config,
        targetMode = TuningMode.PERFORMANCE,
        context = context,
        appId = appId,
        coreCountOverride = coreCountOverride,
        coreFreqsKhz = coreFreqsKhz,
    )

    fun enableEcoMode(
        config: ContainerData,
        context: Context? = null,
        appId: String? = null,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): ContainerData = setTuningMode(
        config = config,
        targetMode = TuningMode.ECO,
        context = context,
        appId = appId,
        coreCountOverride = coreCountOverride,
        coreFreqsKhz = coreFreqsKhz,
    )

    fun disableTuningMode(
        config: ContainerData,
        context: Context? = null,
        appId: String? = null,
    ): ContainerData = setTuningMode(
        config = config,
        targetMode = TuningMode.OFF,
        context = context,
        appId = appId,
    )

    private fun restoreFromSnapshot(
        config: ContainerData,
        snapshot: JSONObject?,
        context: Context?,
        appId: String?,
    ): ContainerData {
        if (snapshot == null) {
            val cleared = config.copy(
                tuningMode = TuningMode.OFF.id,
                tuningModeSnapshot = "",
                rootlessGamePinningEnabled = false,
            )
            persistIfContainerExists(context, appId, cleared)
            return cleared
        }

        val powerProfileFile = resolvePowerProfileFile(context, appId)
        if (powerProfileFile != null && powerProfileFile.exists() && snapshot.optBoolean(KEY_ADAPTIVE_FPS_CAP_HAD_VALUE, false)) {
            runCatching {
                val json = JSONObject(powerProfileFile.readText(Charsets.UTF_8))
                json.put("adaptiveFpsCapEnabled", snapshot.optBoolean(KEY_ADAPTIVE_FPS_CAP_ENABLED, false))
                powerProfileFile.writeText(json.toString(), Charsets.UTF_8)
            }.onFailure {
                Timber.w(it, "PerformanceModeManager: failed to restore .power_profile.json on OFF")
            }
        }

        val restoredCpuList = snapshot.optString(KEY_CPU_LIST, config.cpuList)
            .ifBlank { Container.getFallbackCPUList() }
        val restoredCpuListWoW64 = snapshot.optString(KEY_CPU_LIST_WOW64, config.cpuListWoW64)
            .ifBlank { Container.getFallbackCPUListWoW64() }

        val restored = config.copy(
            displayServer = snapshot.optString(KEY_DISPLAY_SERVER, config.displayServer)
                .ifBlank { config.displayServer },
            fexcorePreset = snapshot.optString(KEY_FEXCORE_PRESET, config.fexcorePreset)
                .ifBlank { config.fexcorePreset },
            box64Preset = snapshot.optString(KEY_BOX64_PRESET, config.box64Preset)
                .ifBlank { config.box64Preset },
            cpuList = restoredCpuList,
            cpuListWoW64 = restoredCpuListWoW64,
            rootlessGamePinningEnabled = snapshot.optBoolean(KEY_ROOTLESS_GAME_PINNING, false),
            tuningMode = TuningMode.OFF.id,
            tuningModeSnapshot = "",
        )
        persistIfContainerExists(context, appId, restored)
        return restored
    }

    /**
     * Checks whether any tuning-managed field was manually modified by the user or AI
     * while `tuningMode` is `PERFORMANCE` or `ECO`.
     *
     * If so, automatically flips `tuningMode` back to `OFF` and clears `tuningModeSnapshot`
     * without reverting the user's manual edit.
     */
    fun reconcileManualOverride(
        previous: ContainerData,
        updated: ContainerData,
    ): ContainerData {
        val prevMode = TuningMode.fromRaw(previous.tuningMode)
        val newMode = TuningMode.fromRaw(updated.tuningMode)
        if (prevMode == TuningMode.OFF || newMode != prevMode) {
            return updated
        }

        val displayServerChanged = prevMode == TuningMode.PERFORMANCE &&
            !previous.displayServer.equals(updated.displayServer, ignoreCase = true)

        val managedFieldChanged =
            displayServerChanged ||
                !previous.fexcorePreset.equals(updated.fexcorePreset, ignoreCase = true) ||
                !previous.box64Preset.equals(updated.box64Preset, ignoreCase = true) ||
                previous.cpuList != updated.cpuList ||
                previous.cpuListWoW64 != updated.cpuListWoW64 ||
                previous.rootlessGamePinningEnabled != updated.rootlessGamePinningEnabled

        return if (managedFieldChanged) {
            updated.copy(
                tuningMode = TuningMode.OFF.id,
                tuningModeSnapshot = "",
            )
        } else {
            updated
        }
    }

    /**
     * Pure, side-effect-free computation of the effective [ContainerData] that switching
     * [config] to [targetMode] will produce (without writing to disk or `.power_profile.json`).
     */
    fun previewTuningModeConfig(
        config: ContainerData,
        targetMode: TuningMode,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): ContainerData = setTuningMode(
        config = config,
        targetMode = targetMode,
        context = null,
        appId = null,
        coreCountOverride = coreCountOverride,
        coreFreqsKhz = coreFreqsKhz,
    )

    fun isManagedKey(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed in TUNING_MANAGED_KEYS || trimmed == "fexPreset"
    }

    fun persistIfContainerExists(
        context: Context?,
        appId: String?,
        config: ContainerData,
    ) {
        if (context == null || appId.isNullOrBlank()) return
        runCatching {
            if (ContainerUtils.hasContainer(context, appId)) {
                val container = ContainerUtils.getContainer(context, appId)
                ContainerUtils.applyToContainer(context, container, config)
            }
        }.onFailure { e ->
            Timber.w(e, "PerformanceModeManager: failed to persist container config for %s", appId)
        }
    }

    private fun resolvePowerProfileFile(context: Context?, appId: String?): File? {
        if (context == null || appId.isNullOrBlank()) return null
        return runCatching {
            if (!ContainerUtils.hasContainer(context, appId)) return null
            val container = ContainerUtils.getContainer(context, appId)
            File(container.rootDir, ".power_profile.json")
        }.getOrNull()
    }
}
