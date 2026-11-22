package app.gamenative.aidebugger

import android.content.Context
import app.gamenative.performance.PerformanceModeManager
import app.gamenative.performance.TuningMode
import app.gamenative.utils.ContainerUtils
import com.winlator.container.ContainerData
import com.winlator.core.envvars.EnvVars
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Applies validated AI Debugger patches to [ContainerData] and game `.ini`/`.cfg` files,
 * persisting a full undo snapshot in [ContainerData.lastAiPatchSnapshot] and `.gamenothing.bak`
 * backups on disk.
 */
object AiPatchApplier {

    const val BACKUP_SUFFIX = ".gamenothing.bak"

    fun hasUndoSnapshot(config: ContainerData): Boolean =
        config.lastAiPatchSnapshot.isNotBlank()

    fun applyPatch(
        context: Context?,
        appId: String?,
        currentConfig: ContainerData,
        response: AiDebuggerResponse,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): ContainerData {
        val allowedChanges = AiPatchValidator.resolveAndFilterChanges(
            changes = response.allowedContainerChanges,
            currentConfig = currentConfig,
            coreCountOverride = coreCountOverride,
            coreFreqsKhz = coreFreqsKhz,
        ).filter { it.isAllowed }
        val allowedEdits = response.allowedGameConfigEdits

        if (allowedChanges.isEmpty() && allowedEdits.isEmpty()) {
            return currentConfig
        }

        val containerRootDir: File? = if (context != null && !appId.isNullOrBlank()) {
            runCatching {
                if (ContainerUtils.hasContainer(context, appId)) {
                    ContainerUtils.getContainer(context, appId).rootDir
                } else {
                    null
                }
            }.getOrNull()
        } else {
            null
        }

        val previousFieldsJson = JSONObject()
        var working = currentConfig
        var toggledTuningModeDirectly = false

        val (tuningChanges, nonTuningChanges) = allowedChanges.partition { change ->
            val key = AiPatchValidator.normalizeKey(change.key)
            key == "tuningMode" || key == "performanceMode"
        }

        // 1. Apply tuningMode FIRST (updating its baseline snapshot cleanly)
        for (change in tuningChanges) {
            toggledTuningModeDirectly = true
            val key = AiPatchValidator.normalizeKey(change.key)
            if (!previousFieldsJson.has("tuningMode")) {
                previousFieldsJson.put("tuningMode", readExactFieldForSnapshot(currentConfig, "tuningMode"))
            }
            for (managedKey in PerformanceModeManager.TUNING_MANAGED_KEYS) {
                if (!previousFieldsJson.has(managedKey)) {
                    previousFieldsJson.put(managedKey, readExactFieldForSnapshot(currentConfig, managedKey))
                }
            }
            val targetMode = if (key == "performanceMode") {
                val wantEnabled = change.newValue.equals("true", ignoreCase = true) || change.newValue == "1"
                if (wantEnabled) TuningMode.PERFORMANCE else TuningMode.OFF
            } else {
                TuningMode.fromRaw(change.newValue)
            }
            working = PerformanceModeManager.setTuningMode(
                config = working,
                targetMode = targetMode,
                context = context,
                appId = appId,
                coreCountOverride = coreCountOverride,
                coreFreqsKhz = coreFreqsKhz,
            )
        }

        // 2. Apply remaining non-managed (or custom non-colliding) keys
        for (change in nonTuningChanges) {
            val key = AiPatchValidator.normalizeKey(change.key)
            if (!previousFieldsJson.has(key)) {
                previousFieldsJson.put(key, readExactFieldForSnapshot(currentConfig, key))
            }
            working = applySingleChange(working, key, change.newValue, containerRootDir)
        }

        // 3. Apply game .ini / .cfg edits with .gamenothing.bak backup
        val backedUpFilesArray = JSONArray()
        val backedUpPaths = mutableSetOf<String>()
        for (edit in allowedEdits) {
            val targetFile = edit.canonicalFile ?: continue
            runCatching {
                if (targetFile.exists() && targetFile.isFile) {
                    val backupFile = File(targetFile.parentFile, targetFile.name + BACKUP_SUFFIX)
                    if (targetFile.canonicalPath !in backedUpPaths) {
                        targetFile.copyTo(backupFile, overwrite = true)
                        backedUpPaths.add(targetFile.canonicalPath)
                        backedUpFilesArray.put(
                            JSONObject().apply {
                                put("targetPath", targetFile.canonicalPath)
                                put("backupPath", backupFile.canonicalPath)
                            },
                        )
                    }
                    applyIniOrCfgEdit(targetFile, edit.section, edit.key, edit.newValue)
                }
            }.onFailure { e ->
                Timber.w(e, "AiPatchApplier: Failed to apply game config edit to %s", edit.relativePath)
            }
        }

        // If an AI patch modified a tuning-managed field directly (without toggling tuningMode itself),
        // reconcile manual override so tuningMode flips cleanly back to OFF.
        val reconciled = if (!toggledTuningModeDirectly) {
            PerformanceModeManager.reconcileManualOverride(currentConfig, working)
        } else {
            working
        }

        val snapshotJson = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("previousFields", previousFieldsJson)
            put("previousTuningMode", currentConfig.tuningMode)
            put("previousTuningModeSnapshot", currentConfig.tuningModeSnapshot)
            put("backedUpFiles", backedUpFilesArray)
        }

        val finalConfig = reconciled.copy(
            lastAiPatchSnapshot = snapshotJson.toString(),
        )

        persistIfContainerExists(context, appId, finalConfig)
        return finalConfig
    }

    fun undoLastPatch(
        context: Context?,
        appId: String?,
        currentConfig: ContainerData,
    ): ContainerData {
        val rawSnapshot = currentConfig.lastAiPatchSnapshot
        if (rawSnapshot.isBlank()) return currentConfig

        val containerRootDir: File? = if (context != null && !appId.isNullOrBlank()) {
            runCatching {
                if (ContainerUtils.hasContainer(context, appId)) {
                    ContainerUtils.getContainer(context, appId).rootDir
                } else {
                    null
                }
            }.getOrNull()
        } else {
            null
        }

        val restored = runCatching {
            val root = JSONObject(rawSnapshot)
            var working = currentConfig

            val prevFields = root.optJSONObject("previousFields")
            if (prevFields != null) {
                val keys = prevFields.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val oldVal = prevFields.optString(key, "")
                    working = restoreSingleFieldFromSnapshot(working, key, oldVal, containerRootDir)
                }
            }

            if (root.has("previousTuningMode")) {
                working = working.copy(
                    tuningMode = root.optString("previousTuningMode", working.tuningMode),
                    tuningModeSnapshot = root.optString("previousTuningModeSnapshot", working.tuningModeSnapshot),
                )
            } else if (root.has("previousPerformanceMode")) {
                val prevPerf = root.optBoolean("previousPerformanceMode", working.isPerformanceMode)
                working = working.copy(
                    tuningMode = if (prevPerf) TuningMode.PERFORMANCE.id else TuningMode.OFF.id,
                    tuningModeSnapshot = root.optString("previousPerformanceModeSnapshot", working.tuningModeSnapshot),
                )
            }

            val backedUpFiles = root.optJSONArray("backedUpFiles")
            if (backedUpFiles != null) {
                for (i in 0 until backedUpFiles.length()) {
                    val entry = backedUpFiles.optJSONObject(i) ?: continue
                    val targetPath = entry.optString("targetPath", "")
                    val backupPath = entry.optString("backupPath", "")
                    if (targetPath.isNotEmpty() && backupPath.isNotEmpty()) {
                        runCatching {
                            val targetFile = File(targetPath)
                            val backupFile = File(backupPath)
                            if (backupFile.exists() && backupFile.isFile) {
                                backupFile.copyTo(targetFile, overwrite = true)
                                backupFile.delete()
                            }
                        }.onFailure { e ->
                            Timber.w(e, "AiPatchApplier: Failed to restore backup %s", backupPath)
                        }
                    }
                }
            }

            working.copy(lastAiPatchSnapshot = "")
        }.getOrElse { e ->
            Timber.w(e, "AiPatchApplier: Failed to parse lastAiPatchSnapshot")
            currentConfig.copy(lastAiPatchSnapshot = "")
        }

        persistIfContainerExists(context, appId, restored)
        return restored
    }

    private fun persistIfContainerExists(
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
            Timber.w(e, "AiPatchApplier: Failed to persist container config for %s", appId)
        }
    }

    private fun readExactFieldForSnapshot(config: ContainerData, key: String): String {
        if (key.startsWith("envVars.")) return config.envVars
        if (key.startsWith("wincomponents.")) return config.wincomponents
        if (key.startsWith("dxwrapperConfig.")) return config.dxwrapperConfig
        if (key.startsWith("graphicsDriverConfig.")) return config.graphicsDriverConfig
        return when (key) {
            "dxvkVersion", "vkd3dVersion" -> config.dxwrapperConfig
            "graphicsDriverVersion" -> "${config.graphicsDriverVersion}|${config.graphicsDriverConfig}"
            else -> AiPatchValidator.readCurrentValueForKey(config, key)
        }
    }

    private fun restoreSingleFieldFromSnapshot(
        config: ContainerData,
        key: String,
        snapshotValue: String,
        containerRootDir: File?,
    ): ContainerData {
        if (key.startsWith("envVars.")) return config.copy(envVars = snapshotValue)
        if (key.startsWith("wincomponents.")) return config.copy(wincomponents = snapshotValue)
        if (key.startsWith("dxwrapperConfig.") || key == "dxvkVersion" || key == "vkd3dVersion") {
            return config.copy(dxwrapperConfig = snapshotValue)
        }
        if (key.startsWith("graphicsDriverConfig.")) return config.copy(graphicsDriverConfig = snapshotValue)
        if (key == "graphicsDriverVersion" && snapshotValue.contains("|")) {
            val ver = snapshotValue.substringBefore("|")
            val cfg = snapshotValue.substringAfter("|")
            return config.copy(graphicsDriverVersion = ver, graphicsDriverConfig = cfg)
        }
        return applySingleChange(config, key, snapshotValue, containerRootDir)
    }

    private fun applySingleChange(
        config: ContainerData,
        key: String,
        newValue: String,
        containerRootDir: File?,
    ): ContainerData {
        if (key.startsWith("envVars.")) {
            val envKey = key.removePrefix("envVars.").trim()
            val envVars = EnvVars(config.envVars)
            if (newValue.isEmpty() || newValue.equals("(remove)", ignoreCase = true) || newValue.equals("null", ignoreCase = true)) {
                envVars.remove(envKey)
            } else {
                envVars.put(envKey, newValue)
            }
            return config.copy(envVars = envVars.toString())
        }
        if (key.startsWith("wincomponents.")) {
            val compKey = key.removePrefix("wincomponents.").trim()
            return config.copy(
                wincomponents = AiPatchValidator.upsertKeyValueOption(config.wincomponents, compKey, newValue),
            )
        }
        if (key.startsWith("dxwrapperConfig.")) {
            val subKey = key.removePrefix("dxwrapperConfig.").trim()
            return config.copy(
                dxwrapperConfig = AiPatchValidator.upsertKeyValueOption(config.dxwrapperConfig, subKey, newValue),
            )
        }
        if (key.startsWith("graphicsDriverConfig.")) {
            val subKey = key.removePrefix("graphicsDriverConfig.").trim()
            return config.copy(
                graphicsDriverConfig = AiPatchValidator.upsertKeyValueOption(config.graphicsDriverConfig, subKey, newValue),
            )
        }

        return when (key) {
            "displayServer" -> config.copy(displayServer = newValue.lowercase(Locale.ROOT))
            "displayRenderer" -> {
                val mapped = when (newValue.lowercase(Locale.ROOT)) {
                    "opengl", "gl" -> "gl"
                    "vulkan", "vk" -> "vk"
                    else -> newValue.lowercase(Locale.ROOT)
                }
                config.copy(displayRenderer = mapped)
            }
            "rendererPresentMode" -> config.copy(rendererPresentMode = newValue.lowercase(Locale.ROOT))
            "fexcorePreset" -> config.copy(fexcorePreset = newValue.uppercase(Locale.ROOT))
            "fexcoreVersion" -> config.copy(fexcoreVersion = newValue)
            "fexcoreTSOMode" -> config.copy(fexcoreTSOMode = newValue)
            "fexcoreX87Mode" -> config.copy(fexcoreX87Mode = newValue)
            "fexcoreMultiBlock" -> config.copy(fexcoreMultiBlock = newValue)
            "box64Preset" -> config.copy(box64Preset = newValue.uppercase(Locale.ROOT))
            "box64Version" -> config.copy(box64Version = newValue)
            "wineVersion" -> config.copy(wineVersion = newValue)
            "dxwrapper" -> config.copy(dxwrapper = newValue)
            "dxvkVersion" -> config.copy(
                dxwrapperConfig = AiPatchValidator.upsertKeyValueOption(config.dxwrapperConfig, "version", newValue),
            )
            "vkd3dVersion" -> config.copy(
                dxwrapperConfig = AiPatchValidator.upsertKeyValueOption(config.dxwrapperConfig, "vkd3dVersion", newValue),
            )
            "graphicsDriver" -> config.copy(graphicsDriver = newValue)
            "graphicsDriverVersion" -> config.copy(
                graphicsDriverVersion = newValue,
                graphicsDriverConfig = AiPatchValidator.upsertKeyValueOption(config.graphicsDriverConfig, "version", newValue),
            )
            "cpuList" -> config.copy(cpuList = newValue)
            "cpuListWoW64" -> config.copy(cpuListWoW64 = newValue)
            "fpsLimiterEnabled" -> {
                val enabled = newValue.equals("true", ignoreCase = true) || newValue == "1"
                syncPowerProfileAdaptiveFpsCap(containerRootDir, enabled)
                config.copy(fpsLimiterEnabled = enabled)
            }
            "rootlessGamePinningEnabled" -> {
                val enabled = newValue.equals("true", ignoreCase = true) || newValue == "1"
                config.copy(rootlessGamePinningEnabled = enabled)
            }
            "screenSize" -> config.copy(screenSize = newValue)
            "videoMemorySize" -> config.copy(videoMemorySize = newValue)
            "audioDriver" -> {
                val mapped = if (newValue.equals("pulseaudio", ignoreCase = true)) "pulse" else newValue.lowercase(Locale.ROOT)
                config.copy(audioDriver = mapped)
            }
            "pulseaudioLowLatency" -> {
                val enabled = newValue.equals("true", ignoreCase = true) || newValue == "1"
                config.copy(pulseaudioLowLatency = enabled)
            }
            "csmt" -> config.copy(csmt = newValue.equals("true", ignoreCase = true) || newValue == "1")
            "strictShaderMath" -> config.copy(strictShaderMath = newValue.equals("true", ignoreCase = true) || newValue == "1")
            "offScreenRenderingMode" -> config.copy(offScreenRenderingMode = newValue)
            "useDRI3" -> config.copy(useDRI3 = newValue.equals("true", ignoreCase = true) || newValue == "1")
            "lsfgEnabled" -> config.copy(lsfgEnabled = newValue.equals("true", ignoreCase = true) || newValue == "1")
            "sharpnessEffect" -> config.copy(sharpnessEffect = newValue)
            "emulator" -> config.copy(emulator = newValue)
            "execArgs" -> config.copy(execArgs = newValue)
            else -> config
        }
    }

    private fun syncPowerProfileAdaptiveFpsCap(containerRootDir: File?, adaptiveFpsCapEnabled: Boolean) {
        if (containerRootDir == null) return
        val file = File(containerRootDir, ".power_profile.json")
        if (!file.exists()) return
        runCatching {
            val json = JSONObject(file.readText())
            json.put("adaptiveFpsCapEnabled", adaptiveFpsCapEnabled)
            file.writeText(json.toString())
        }
    }

    internal fun applyIniOrCfgEdit(
        file: File,
        section: String,
        key: String,
        newValue: String,
    ) {
        val lines = file.readLines().toMutableList()
        val targetSectionHeader = if (section.isNotBlank()) {
            val clean = section.trim().removePrefix("[").removeSuffix("]")
            "[$clean]"
        } else {
            ""
        }

        val keyPattern = Regex("^\\s*${Regex.escape(key)}\\s*[:=].*$", RegexOption.IGNORE_CASE)
        var currentSection = ""
        var replaced = false
        var lastIndexInTargetSection = -1

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed
                if (targetSectionHeader.isNotEmpty() && currentSection.equals(targetSectionHeader, ignoreCase = true)) {
                    lastIndexInTargetSection = i
                }
                continue
            }
            val inMatchingSection = targetSectionHeader.isEmpty() ||
                currentSection.equals(targetSectionHeader, ignoreCase = true)
            if (inMatchingSection) {
                if (trimmed.isNotEmpty() && !trimmed.startsWith(";") && !trimmed.startsWith("#")) {
                    lastIndexInTargetSection = i
                }
                if (keyPattern.matches(lines[i])) {
                    val separator = if (lines[i].contains(":") && !lines[i].contains("=")) ": " else "="
                    lines[i] = "$key$separator$newValue"
                    replaced = true
                    break
                }
            }
        }

        if (!replaced) {
            if (targetSectionHeader.isNotEmpty() && lastIndexInTargetSection >= 0) {
                lines.add(lastIndexInTargetSection + 1, "$key=$newValue")
            } else if (targetSectionHeader.isNotEmpty()) {
                if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                    lines.add("")
                }
                lines.add(targetSectionHeader)
                lines.add("$key=$newValue")
            } else {
                lines.add("$key=$newValue")
            }
        }

        file.writeText(lines.joinToString("\n"))
    }
}
