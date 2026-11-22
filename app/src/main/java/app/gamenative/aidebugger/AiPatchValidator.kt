package app.gamenative.aidebugger

import android.content.Context
import app.gamenative.performance.PerformanceModeManager
import app.gamenative.performance.TuningMode
import app.gamenative.ui.screen.support.SupportGameFiles
import com.winlator.container.ContainerData
import com.winlator.core.envvars.EnvVars
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class AiContainerChange(
    val id: String,
    val key: String,
    val oldValue: String,
    val newValue: String,
    val reason: String,
    val isAllowed: Boolean = true,
    val rejectionReason: String? = null,
)

data class AiGameConfigEdit(
    val id: String,
    val relativePath: String,
    val section: String,
    val key: String,
    val newValue: String,
    val reason: String,
    val canonicalFile: File? = null,
    val isAllowed: Boolean = true,
    val rejectionReason: String? = null,
)

data class AiDebuggerResponse(
    val bottleneck: String,
    val diagnosis: String,
    val containerChanges: List<AiContainerChange>,
    val gameConfigEdits: List<AiGameConfigEdit>,
    val manualTips: List<String>,
    val rawJson: String,
) {
    val allowedContainerChanges: List<AiContainerChange>
        get() = containerChanges.filter { it.isAllowed }

    val allowedGameConfigEdits: List<AiGameConfigEdit>
        get() = gameConfigEdits.filter { it.isAllowed && it.canonicalFile != null }

    val hasApplicableChanges: Boolean
        get() = allowedContainerChanges.isNotEmpty() || allowedGameConfigEdits.isNotEmpty()
}

/**
 * Parses and validates AI Debugger JSON responses against:
 * 1. Explicit allowlist of container keys.
 * 2. Installed components inventory (preventing non-installed Proton/Wine/DXVK/VKD3D/FEX/Box64/Driver versions).
 * 3. No-op diff filtering against live [ContainerData] values (normalized whitespace, sorted core masks, case-insensitive enums).
 * 4. `tuningMode` + managed-field deduplication and contradiction resolution.
 * 5. Canonical path traversal protection (`< 64 KB` `.ini`/`.cfg` inside game or Wine prefix directories).
 */
object AiPatchValidator {

    private const val MAX_CONFIG_FILE_BYTES = 64 * 1024L

    val ALLOWED_STATIC_KEYS = setOf(
        "displayServer",
        "renderer",
        "displayRenderer",
        "rendererPresentMode",
        "fexPreset",
        "fexcorePreset",
        "fexVersion",
        "fexcoreVersion",
        "fexcoreTSOMode",
        "fexcoreX87Mode",
        "fexcoreMultiBlock",
        "box64Preset",
        "box64Version",
        "wineVersion",
        "protonVersion",
        "dxwrapper",
        "dxvkVersion",
        "vkd3dVersion",
        "graphicsDriver",
        "graphicsDriverVersion",
        "cpuList",
        "cpuListWoW64",
        "fpsLimiterEnabled",
        "autoFpsCap",
        "rootlessGamePinningEnabled",
        "tuningMode",
        "performanceMode",
        "screenSize",
        "resolution",
        "videoMemorySize",
        "vram",
        "audioDriver",
        "pulseaudioLowLatency",
        "csmt",
        "strictShaderMath",
        "offScreenRenderingMode",
        "useDRI3",
        "lsfgEnabled",
        "sharpnessEffect",
        "emulator",
        "execArgs",
    )

    private val ALLOWED_FEX_PRESETS = setOf(
        "STABILITY",
        "COMPATIBILITY",
        "INTERMEDIATE",
        "PERFORMANCE",
        "EXTREME",
    )

    private val ALLOWED_BOX64_PRESETS = setOf(
        "STABILITY",
        "COMPATIBILITY",
        "INTERMEDIATE",
        "PERFORMANCE",
    )

    private val BOOLEAN_KEYS = setOf(
        "fpsLimiterEnabled",
        "rootlessGamePinningEnabled",
        "pulseaudioLowLatency",
        "csmt",
        "strictShaderMath",
        "useDRI3",
        "lsfgEnabled",
        "performanceMode",
    )

    private val UPPERCASE_ENUM_KEYS = setOf(
        "fexcorePreset",
        "box64Preset",
        "tuningMode",
    )

    private val LOWERCASE_ENUM_KEYS = setOf(
        "displayServer",
        "rendererPresentMode",
        "fexcoreTSOMode",
        "fexcoreX87Mode",
        "fexcoreMultiBlock",
        "dxwrapper",
        "graphicsDriver",
        "offScreenRenderingMode",
        "sharpnessEffect",
        "emulator",
        "wineVersion",
        "fexcoreVersion",
        "box64Version",
        "dxvkVersion",
        "vkd3dVersion",
        "graphicsDriverVersion",
        "videoMemorySize",
    )

    fun isKeyAllowed(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed in ALLOWED_STATIC_KEYS) return true
        if (trimmed.startsWith("envVars.") && trimmed.length > "envVars.".length) return true
        if (trimmed.startsWith("wincomponents.") && trimmed.length > "wincomponents.".length) return true
        if (trimmed.startsWith("dxwrapperConfig.") && trimmed.length > "dxwrapperConfig.".length) return true
        if (trimmed.startsWith("graphicsDriverConfig.") && trimmed.length > "graphicsDriverConfig.".length) return true
        return false
    }

    fun extractCleanJson(rawText: String): String {
        val trimmed = rawText.trim()
        // Strip ```json ... ``` fences if present
        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val fencedMatch = fenceRegex.find(trimmed)
        val candidate = fencedMatch?.groupValues?.getOrNull(1)?.trim() ?: trimmed

        val firstBrace = candidate.indexOf('{')
        val lastBrace = candidate.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return candidate.substring(firstBrace, lastBrace + 1)
        }
        return candidate
    }

    fun parseAndValidate(
        context: Context?,
        appId: String?,
        rawResponseText: String,
        currentConfig: ContainerData,
        installedComponents: InstalledComponentsSnapshot? = null,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): Result<AiDebuggerResponse> = runCatching {
        val cleanJsonStr = extractCleanJson(rawResponseText)
        val root = JSONObject(cleanJsonStr)

        val bottleneck = root.optString("bottleneck", "CPU").ifBlank { "CPU" }
        val diagnosis = root.optString("diagnosis", "").ifBlank {
            "Analyzed container telemetry and configuration."
        }

        val changesArray = root.optJSONArray("containerChanges") ?: JSONArray()
        val parsedChanges = mutableListOf<AiContainerChange>()
        for (i in 0 until changesArray.length()) {
            val item = changesArray.optJSONObject(i) ?: continue
            val rawKey = item.optString("key", "").trim()
            if (rawKey.isEmpty()) continue
            var normalizedKey = normalizeKey(rawKey)
            var newVal = item.optString("newValue", "").trim()

            // Canonicalize legacy performanceMode boolean key to tuningMode
            if (normalizedKey == "performanceMode") {
                val wantPerf = newVal.equals("true", ignoreCase = true) || newVal == "1"
                val wantOff = newVal.equals("false", ignoreCase = true) || newVal == "0"
                if (wantPerf || wantOff) {
                    normalizedKey = "tuningMode"
                    newVal = if (wantPerf) TuningMode.PERFORMANCE.id else TuningMode.OFF.id
                }
            }

            // Normalize comma-separated core lists by trimming whitespace around tokens
            if (normalizedKey == "cpuList" || normalizedKey == "cpuListWoW64") {
                newVal = normalizeCoreMaskTokens(newVal)
            }

            val liveVal = readCurrentValueForKey(currentConfig, normalizedKey)
            val oldVal = liveVal.ifBlank { item.optString("oldValue", "").trim() }
            val reason = item.optString("reason", "Recommended optimization")

            val (allowed, rejectionReason) = validateContainerChange(
                key = normalizedKey,
                newValue = newVal,
                installed = installedComponents,
            )

            parsedChanges.add(
                AiContainerChange(
                    id = "change_${i}_$normalizedKey",
                    key = normalizedKey,
                    oldValue = oldVal,
                    newValue = newVal,
                    reason = reason,
                    isAllowed = allowed,
                    rejectionReason = rejectionReason,
                ),
            )
        }

        val resolvedChanges = resolveAndFilterChanges(
            changes = parsedChanges,
            currentConfig = currentConfig,
            coreCountOverride = coreCountOverride,
            coreFreqsKhz = coreFreqsKhz,
        )

        val editsArray = root.optJSONArray("gameConfigEdits") ?: JSONArray()
        val parsedEdits = mutableListOf<AiGameConfigEdit>()
        for (i in 0 until editsArray.length()) {
            val item = editsArray.optJSONObject(i) ?: continue
            val relPath = item.optString("relativePath", "").trim()
            val section = item.optString("section", "").trim()
            val key = item.optString("key", "").trim()
            val newVal = item.optString("newValue", "").trim()
            val reason = item.optString("reason", "Recommended game config adjustment")
            if (relPath.isEmpty() || key.isEmpty()) continue

            val (allowed, canonicalFile, rejection) = validateGameConfigEdit(
                context = context,
                appId = appId,
                relativePath = relPath,
            )

            parsedEdits.add(
                AiGameConfigEdit(
                    id = "edit_${i}_${relPath}_$key",
                    relativePath = relPath,
                    section = section,
                    key = key,
                    newValue = newVal,
                    reason = reason,
                    canonicalFile = canonicalFile,
                    isAllowed = allowed,
                    rejectionReason = rejection,
                ),
            )
        }

        val tipsArray = root.optJSONArray("manualTips") ?: JSONArray()
        val tips = mutableListOf<String>()
        for (i in 0 until tipsArray.length()) {
            val tip = tipsArray.optString(i, "").trim()
            if (tip.isNotEmpty()) tips.add(tip)
        }

        AiDebuggerResponse(
            bottleneck = bottleneck,
            diagnosis = diagnosis,
            containerChanges = resolvedChanges,
            gameConfigEdits = parsedEdits,
            manualTips = tips,
            rawJson = root.toString(2),
        )
    }

    /**
     * Filters out no-op diffs against [currentConfig]'s live values and resolves collisions
     * between `tuningMode` and [PerformanceModeManager.TUNING_MANAGED_KEYS]:
     * 1. Drops any allowed change whose normalized new value equals the normalized live value.
     * 2. When `tuningMode` is present alongside managed fields:
     *    - Computes the effective config that `tuningMode` will set.
     *    - If any managed field contradicts that effective value, drops `tuningMode` so the
     *      custom managed fields apply cleanly without colliding or auto-flipping back to `OFF`.
     *    - Otherwise drops all redundant managed fields that already match what `tuningMode`
     *      sets and places `tuningMode` first.
     */
    fun resolveAndFilterChanges(
        changes: List<AiContainerChange>,
        currentConfig: ContainerData,
        coreCountOverride: Int? = null,
        coreFreqsKhz: Map<Int, Long>? = null,
    ): List<AiContainerChange> {
        if (changes.isEmpty()) return emptyList()

        // Step 1: Preserve disallowed changes for diagnostics; filter no-ops and deduplicate allowed changes.
        val disallowed = mutableListOf<AiContainerChange>()
        val allowedByKey = LinkedHashMap<String, AiContainerChange>()

        for (rawChange in changes) {
            if (!rawChange.isAllowed) {
                disallowed.add(rawChange)
                continue
            }

            var normalizedKey = normalizeKey(rawChange.key)
            var normalizedNewVal = rawChange.newValue.trim()
            if (normalizedKey == "performanceMode") {
                val wantPerf = normalizedNewVal.equals("true", ignoreCase = true) || normalizedNewVal == "1"
                val wantOff = normalizedNewVal.equals("false", ignoreCase = true) || normalizedNewVal == "0"
                if (wantPerf || wantOff) {
                    normalizedKey = "tuningMode"
                    normalizedNewVal = if (wantPerf) TuningMode.PERFORMANCE.id else TuningMode.OFF.id
                }
            }
            if (normalizedKey == "cpuList" || normalizedKey == "cpuListWoW64") {
                normalizedNewVal = normalizeCoreMaskTokens(normalizedNewVal)
            }

            val liveValue = readCurrentValueForKey(currentConfig, normalizedKey)
            val normCurrent = normalizeValueForComparison(normalizedKey, liveValue)
            val normProposed = normalizeValueForComparison(normalizedKey, normalizedNewVal)
            if (normProposed == normCurrent) {
                continue
            }

            allowedByKey[normalizedKey] = rawChange.copy(
                key = normalizedKey,
                oldValue = liveValue.ifBlank { rawChange.oldValue },
                newValue = normalizedNewVal,
            )
        }

        val allowedList = allowedByKey.values.toMutableList()

        // Step 2: Resolve tuningMode + managed-field deduplication / contradiction.
        val tuningChange = allowedList.firstOrNull { it.key == "tuningMode" }
        if (tuningChange != null) {
            val targetMode = TuningMode.fromRaw(tuningChange.newValue)
            val effectiveConfig = PerformanceModeManager.previewTuningModeConfig(
                config = currentConfig,
                targetMode = targetMode,
                coreCountOverride = coreCountOverride,
                coreFreqsKhz = coreFreqsKhz,
            )

            val managedChanges = allowedList.filter { it.key in PerformanceModeManager.TUNING_MANAGED_KEYS }
            val hasContradiction = managedChanges.any { change ->
                val effectiveVal = readCurrentValueForKey(effectiveConfig, change.key)
                normalizeValueForComparison(change.key, change.newValue) !=
                    normalizeValueForComparison(change.key, effectiveVal)
            }

            if (hasContradiction) {
                // Custom managed field contradicts tuningMode -> drop tuningMode, keep custom fields.
                allowedList.removeAll { it.key == "tuningMode" }
            } else {
                // Every managed field already matches what tuningMode sets -> drop redundant managed fields
                // and keep tuningMode first.
                allowedList.removeAll { it.key in PerformanceModeManager.TUNING_MANAGED_KEYS }
                allowedList.removeAll { it.key == "tuningMode" }
                allowedList.add(0, tuningChange)
            }
        }

        // Reconstruct preserving original disallowed positions while keeping allowedList order
        // (with tuningMode ordered first among allowed changes when present).
        if (disallowed.isEmpty()) return allowedList
        val allowedIter = allowedList.iterator()
        val result = ArrayList<AiContainerChange>(disallowed.size + allowedList.size)
        for (rawChange in changes) {
            if (!rawChange.isAllowed) {
                result.add(rawChange)
            } else if (allowedIter.hasNext()) {
                result.add(allowedIter.next())
            }
        }
        while (allowedIter.hasNext()) {
            result.add(allowedIter.next())
        }
        return result
    }

    fun normalizeCoreMaskTokens(rawMask: String): String {
        val tokens = rawMask.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val ints = tokens.mapNotNull { it.toIntOrNull() }
        return if (ints.size == tokens.size) {
            ints.distinct().sorted().joinToString(",")
        } else {
            tokens.joinToString(",")
        }
    }

    fun normalizeValueForComparison(normalizedKey: String, rawValue: String): String {
        val trimmed = rawValue.trim()
        if (normalizedKey == "cpuList" || normalizedKey == "cpuListWoW64") {
            return normalizeCoreMaskTokens(trimmed)
        }
        if (normalizedKey in BOOLEAN_KEYS) {
            return when (trimmed.lowercase(Locale.ROOT)) {
                "true", "1", "yes", "on", "enabled" -> "true"
                "false", "0", "no", "off", "disabled" -> "false"
                else -> trimmed.lowercase(Locale.ROOT)
            }
        }
        if (normalizedKey in UPPERCASE_ENUM_KEYS) {
            return trimmed.uppercase(Locale.ROOT)
        }
        if (normalizedKey in LOWERCASE_ENUM_KEYS) {
            return trimmed.lowercase(Locale.ROOT)
        }
        if (normalizedKey == "displayRenderer") {
            return when (trimmed.lowercase(Locale.ROOT)) {
                "opengl", "gl" -> "gl"
                "vulkan", "vk" -> "vk"
                else -> trimmed.lowercase(Locale.ROOT)
            }
        }
        if (normalizedKey == "audioDriver") {
            return when (trimmed.lowercase(Locale.ROOT)) {
                "pulseaudio", "pulse" -> "pulse"
                else -> trimmed.lowercase(Locale.ROOT)
            }
        }
        if (normalizedKey == "screenSize") {
            return trimmed.lowercase(Locale.ROOT).replace(Regex("\\s*x\\s*"), "x")
        }
        if (normalizedKey.startsWith("envVars.")) {
            val lower = trimmed.lowercase(Locale.ROOT)
            if (lower.isEmpty() || lower == "(not set)" || lower == "(remove)" || lower == "null") {
                return ""
            }
            return trimmed
        }
        if (normalizedKey.startsWith("wincomponents.") ||
            normalizedKey.startsWith("dxwrapperConfig.") ||
            normalizedKey.startsWith("graphicsDriverConfig.")
        ) {
            if (trimmed.equals("(default)", ignoreCase = true)) return ""
            return trimmed.lowercase(Locale.ROOT)
        }
        return trimmed
    }

    fun normalizeKey(rawKey: String): String = when (rawKey.trim()) {
        "renderer" -> "displayRenderer"
        "fexPreset" -> "fexcorePreset"
        "fexVersion" -> "fexcoreVersion"
        "protonVersion" -> "wineVersion"
        "autoFpsCap" -> "fpsLimiterEnabled"
        "resolution" -> "screenSize"
        "vram" -> "videoMemorySize"
        else -> rawKey.trim()
    }

    private fun validateContainerChange(
        key: String,
        newValue: String,
        installed: InstalledComponentsSnapshot?,
    ): Pair<Boolean, String?> {
        if (!isKeyAllowed(key)) {
            return false to "Key '$key' is not in the container settings allowlist."
        }
        if (newValue.isEmpty() && !key.startsWith("envVars.") && key != "execArgs") {
            return false to "Value for '$key' cannot be empty."
        }

        when (key) {
            "displayServer" -> {
                if (newValue.lowercase(Locale.ROOT) !in setOf("x11", "gamescope")) {
                    return false to "Invalid displayServer '$newValue' (must be 'x11' or 'gamescope')."
                }
            }
            "displayRenderer" -> {
                if (newValue.lowercase(Locale.ROOT) !in setOf("gl", "vk", "opengl", "vulkan")) {
                    return false to "Invalid displayRenderer '$newValue' (must be 'gl' or 'vk')."
                }
            }
            "rendererPresentMode" -> {
                if (newValue.lowercase(Locale.ROOT) !in setOf("mailbox", "fifo", "immediate")) {
                    return false to "Invalid rendererPresentMode '$newValue'."
                }
            }
            "fexcorePreset" -> {
                if (newValue.uppercase(Locale.ROOT) !in ALLOWED_FEX_PRESETS) {
                    return false to "Invalid fexcorePreset '$newValue'."
                }
            }
            "box64Preset" -> {
                if (newValue.uppercase(Locale.ROOT) !in ALLOWED_BOX64_PRESETS) {
                    return false to "Invalid box64Preset '$newValue'."
                }
            }
            "audioDriver" -> {
                if (newValue.lowercase(Locale.ROOT) !in setOf("alsa", "pulse", "pulseaudio")) {
                    return false to "Invalid audioDriver '$newValue' (must be 'alsa' or 'pulse')."
                }
            }
            "cpuList" -> {
                if (!Regex("^\\d+(,\\d+)*$").matches(newValue.trim())) {
                    return false to "Invalid CPU core list '$newValue'."
                }
            }
            "cpuListWoW64" -> {
                if (!Regex("^\\d+(,\\d+)*$").matches(newValue.trim())) {
                    return false to "Invalid 32-bit CPU core list '$newValue'."
                }
                val coreCount = newValue.trim().split(",").size
                if (coreCount > 4) {
                    return false to "32-bit cpuListWoW64 must be capped to at most 4 cores (got $coreCount)."
                }
            }
            "tuningMode" -> {
                if (newValue.uppercase(Locale.ROOT) !in setOf("OFF", "PERFORMANCE", "ECO")) {
                    return false to "Invalid tuningMode '$newValue' (must be OFF, PERFORMANCE, or ECO)."
                }
            }
            "wineVersion" -> {
                if (installed != null) {
                    val allInstalled = (installed.wine + installed.proton).map { it.lowercase(Locale.ROOT) }
                    if (allInstalled.isNotEmpty() && allInstalled.none { it.contains(newValue.lowercase(Locale.ROOT)) || newValue.lowercase(Locale.ROOT).contains(it) }) {
                        return false to "Wine/Proton version '$newValue' is not installed on this device."
                    }
                }
            }
            "fexcoreVersion" -> {
                if (installed != null && installed.fexcore.isNotEmpty()) {
                    if (installed.fexcore.none { it.equals(newValue, ignoreCase = true) }) {
                        return false to "FEXCore version '$newValue' is not installed on this device."
                    }
                }
            }
            "box64Version" -> {
                if (installed != null && (installed.box64 + installed.wowBox64).isNotEmpty()) {
                    val allBox = installed.box64 + installed.wowBox64
                    if (allBox.none { it.equals(newValue, ignoreCase = true) }) {
                        return false to "Box64 version '$newValue' is not installed on this device."
                    }
                }
            }
            "dxvkVersion" -> {
                if (installed != null && installed.dxvk.isNotEmpty()) {
                    if (installed.dxvk.none { it.equals(newValue, ignoreCase = true) }) {
                        return false to "DXVK version '$newValue' is not installed on this device."
                    }
                }
            }
            "vkd3dVersion" -> {
                if (installed != null && installed.vkd3d.isNotEmpty()) {
                    if (installed.vkd3d.none { it.equals(newValue, ignoreCase = true) }) {
                        return false to "VKD3D version '$newValue' is not installed on this device."
                    }
                }
            }
            "graphicsDriverVersion" -> {
                if (installed != null && installed.installedDrivers.isNotEmpty()) {
                    val validDrivers = installed.installedDrivers + installed.wrapper + listOf("System")
                    if (validDrivers.none { it.equals(newValue, ignoreCase = true) }) {
                        return false to "Graphics driver version '$newValue' is not installed on this device."
                    }
                }
            }
        }

        return true to null
    }

    fun validateGameConfigEdit(
        context: Context?,
        appId: String?,
        relativePath: String,
    ): Triple<Boolean, File?, String?> {
        val normalized = relativePath.replace('\\', '/').trim()
        if (normalized.isEmpty()) {
            return Triple(false, null, "Empty file path.")
        }
        if (normalized.contains("..") || normalized.startsWith("/")) {
            return Triple(false, null, "Path traversal ('..') or absolute paths are blocked.")
        }
        val lower = normalized.lowercase(Locale.ROOT)
        if (!lower.endsWith(".ini") && !lower.endsWith(".cfg")) {
            return Triple(false, null, "Only .ini and .cfg configuration files are permitted.")
        }
        if (lower.contains("steam_api") || lower.contains("token") || lower.contains("save")) {
            return Triple(false, null, "Protected file '$relativePath' cannot be modified.")
        }

        if (context == null || appId.isNullOrBlank()) {
            return Triple(false, null, "No active game container directory available.")
        }

        val roots = runCatching { SupportGameFiles.roots(context, appId) }.getOrNull()
            ?: return Triple(false, null, "Could not resolve container/game roots.")

        val targetFile: File = when {
            normalized.startsWith("%DOCUMENTS%/", ignoreCase = true) -> {
                val base = roots.userHome?.let { File(it, "Documents") }
                    ?: return Triple(false, null, "Wine user Documents folder not found.")
                File(base, normalized.substringAfter('/'))
            }
            normalized.startsWith("%LOCALAPPDATA%/", ignoreCase = true) -> {
                val base = roots.userHome?.let { File(it, "AppData/Local") }
                    ?: return Triple(false, null, "Wine user LocalAppData folder not found.")
                File(base, normalized.substringAfter('/'))
            }
            normalized.startsWith("%APPDATA%/", ignoreCase = true) -> {
                val base = roots.userHome?.let { File(it, "AppData/Roaming") }
                    ?: return Triple(false, null, "Wine user AppData folder not found.")
                File(base, normalized.substringAfter('/'))
            }
            else -> File(roots.install, normalized)
        }

        val canonicalTarget = runCatching { targetFile.canonicalFile }.getOrNull()
            ?: return Triple(false, null, "Failed to resolve canonical file path.")

        val allowedBaseDirs = listOfNotNull(
            runCatching { roots.install.canonicalPath }.getOrNull(),
            runCatching { roots.userHome?.canonicalPath }.getOrNull(),
            runCatching { roots.prefix?.canonicalPath }.getOrNull(),
        )

        val isInsideAllowedRoot = allowedBaseDirs.any { baseCanon ->
            canonicalTarget.path.startsWith(baseCanon + File.separator)
        }
        if (!isInsideAllowedRoot) {
            return Triple(false, null, "Target path escapes container and game directories.")
        }

        if (!canonicalTarget.exists() || !canonicalTarget.isFile) {
            return Triple(false, null, "Target config file '$relativePath' does not exist.")
        }

        if (canonicalTarget.length() >= MAX_CONFIG_FILE_BYTES) {
            return Triple(false, null, "Target config file exceeds 64 KB safety limit.")
        }

        return Triple(true, canonicalTarget, null)
    }

    fun readCurrentValueForKey(config: ContainerData, normalizedKey: String): String {
        if (normalizedKey.startsWith("envVars.")) {
            val envKey = normalizedKey.removePrefix("envVars.")
            val envVars = EnvVars(config.envVars)
            return if (envVars.has(envKey)) envVars.get(envKey) else "(not set)"
        }
        if (normalizedKey.startsWith("wincomponents.")) {
            val compKey = normalizedKey.removePrefix("wincomponents.")
            return extractKeyValueOption(config.wincomponents, compKey) ?: "(default)"
        }
        if (normalizedKey.startsWith("dxwrapperConfig.")) {
            val subKey = normalizedKey.removePrefix("dxwrapperConfig.")
            return extractKeyValueOption(config.dxwrapperConfig, subKey) ?: "(default)"
        }
        if (normalizedKey.startsWith("graphicsDriverConfig.")) {
            val subKey = normalizedKey.removePrefix("graphicsDriverConfig.")
            return extractKeyValueOption(config.graphicsDriverConfig, subKey) ?: "(default)"
        }
        return when (normalizedKey) {
            "displayServer" -> config.displayServer
            "displayRenderer" -> config.displayRenderer
            "rendererPresentMode" -> config.rendererPresentMode
            "fexcorePreset" -> config.fexcorePreset
            "fexcoreVersion" -> config.fexcoreVersion
            "fexcoreTSOMode" -> config.fexcoreTSOMode
            "fexcoreX87Mode" -> config.fexcoreX87Mode
            "fexcoreMultiBlock" -> config.fexcoreMultiBlock
            "box64Preset" -> config.box64Preset
            "box64Version" -> config.box64Version
            "wineVersion" -> config.wineVersion
            "dxwrapper" -> config.dxwrapper
            "dxvkVersion" -> extractKeyValueOption(config.dxwrapperConfig, "version") ?: config.dxwrapper
            "vkd3dVersion" -> extractKeyValueOption(config.dxwrapperConfig, "vkd3dVersion") ?: "(default)"
            "graphicsDriver" -> config.graphicsDriver
            "graphicsDriverVersion" -> config.graphicsDriverVersion.ifBlank {
                extractKeyValueOption(config.graphicsDriverConfig, "version") ?: "System"
            }
            "cpuList" -> config.cpuList
            "cpuListWoW64" -> config.cpuListWoW64
            "fpsLimiterEnabled" -> config.fpsLimiterEnabled.toString()
            "rootlessGamePinningEnabled" -> config.rootlessGamePinningEnabled.toString()
            "tuningMode" -> config.tuningMode
            "performanceMode" -> config.isPerformanceMode.toString()
            "screenSize" -> config.screenSize
            "videoMemorySize" -> config.videoMemorySize
            "audioDriver" -> config.audioDriver
            "pulseaudioLowLatency" -> config.pulseaudioLowLatency.toString()
            "csmt" -> config.csmt.toString()
            "strictShaderMath" -> config.strictShaderMath.toString()
            "offScreenRenderingMode" -> config.offScreenRenderingMode
            "useDRI3" -> config.useDRI3.toString()
            "lsfgEnabled" -> config.lsfgEnabled.toString()
            "sharpnessEffect" -> config.sharpnessEffect
            "emulator" -> config.emulator
            "execArgs" -> config.execArgs
            else -> ""
        }
    }

    fun extractKeyValueOption(rawCommaString: String, targetKey: String): String? {
        if (rawCommaString.isBlank()) return null
        for (part in rawCommaString.split(",")) {
            val eq = part.indexOf('=')
            if (eq > 0) {
                val k = part.substring(0, eq).trim()
                val v = part.substring(eq + 1).trim()
                if (k.equals(targetKey, ignoreCase = true)) return v
            }
        }
        return null
    }

    fun upsertKeyValueOption(rawCommaString: String, targetKey: String, newValue: String): String {
        val map = LinkedHashMap<String, String>()
        if (rawCommaString.isNotBlank()) {
            for (part in rawCommaString.split(",")) {
                val eq = part.indexOf('=')
                if (eq > 0) {
                    val k = part.substring(0, eq).trim()
                    val v = part.substring(eq + 1).trim()
                    map[k] = v
                }
            }
        }
        map[targetKey] = newValue
        return map.entries.joinToString(",") { "${it.key}=${it.value}" }
    }
}
