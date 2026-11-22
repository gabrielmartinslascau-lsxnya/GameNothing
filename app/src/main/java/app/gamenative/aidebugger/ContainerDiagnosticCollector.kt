package app.gamenative.aidebugger

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.gamenative.BuildConfig
import app.gamenative.performance.PerformanceModeManager
import app.gamenative.performance.RootlessGamePinner
import app.gamenative.powercontrol.metrics.ThermalHeadroom
import app.gamenative.ui.screen.support.SupportGameFiles
import app.gamenative.utils.AppExitRecorder
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.DebugReportUtils
import app.gamenative.utils.HardwareUtils
import app.gamenative.utils.ManifestComponentHelper
import com.winlator.container.ContainerData
import com.winlator.core.GPUInformation
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DiscoveredGameConfigFile(
    val relativePath: String,
    val canonicalPath: String,
    val sizeBytes: Long,
    val excerpt: String,
)

data class InstalledComponentsSnapshot(
    val wine: List<String>,
    val proton: List<String>,
    val dxvk: List<String>,
    val vkd3d: List<String>,
    val fexcore: List<String>,
    val box64: List<String>,
    val wowBox64: List<String>,
    val wrapper: List<String>,
    val installedDrivers: List<String>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("wine", JSONArray(wine))
        put("proton", JSONArray(proton))
        put("dxvk", JSONArray(dxvk))
        put("vkd3d", JSONArray(vkd3d))
        put("fexcore", JSONArray(fexcore))
        put("box64", JSONArray(box64))
        put("wowBox64", JSONArray(wowBox64))
        put("wrapper", JSONArray(wrapper))
        put("installedDrivers", JSONArray(installedDrivers))
        put("fexcorePresets", JSONArray(listOf("STABILITY", "COMPATIBILITY", "INTERMEDIATE", "PERFORMANCE", "EXTREME")))
        put("box64Presets", JSONArray(listOf("STABILITY", "COMPATIBILITY", "INTERMEDIATE", "PERFORMANCE")))
        put("displayServers", JSONArray(listOf("x11", "gamescope")))
        put("displayRenderers", JSONArray(listOf("gl", "vk")))
        put("rendererPresentModes", JSONArray(listOf("mailbox", "fifo", "immediate")))
        put("audioDrivers", JSONArray(listOf("alsa", "pulse")))
    }
}

data class ContainerDiagnosticBundle(
    val appId: String?,
    val gameName: String,
    val config: ContainerData,
    val primaryBottleneckGuess: String,
    val avgFps: Float?,
    val sessionLengthSec: Int?,
    val adaptiveFpsCapEnabled: Boolean,
    val thermalStatus: Int?,
    val thermalHeadroom: Float?,
    val gpuRenderer: String,
    val socName: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val lastExitReason: String?,
    val filteredLogLines: List<String>,
    val installedComponents: InstalledComponentsSnapshot,
    val discoveredConfigs: List<DiscoveredGameConfigFile>,
    val bundleJson: JSONObject,
) {
    fun toPrettyRedactedText(): String = bundleJson.toString(2)

    fun summaryPillText(): String {
        val parts = mutableListOf<String>()
        avgFps?.let { parts.add(String.format(Locale.US, "%.0f FPS", it)) }
        if (filteredLogLines.isNotEmpty()) parts.add("${filteredLogLines.size} log lines")
        if (discoveredConfigs.isNotEmpty()) parts.add("${discoveredConfigs.size} game configs")
        lastExitReason?.let { parts.add("Exit: $it") }
        return if (parts.isEmpty()) {
            "Attached: Container config + hardware & installed components"
        } else {
            "Attached: ${parts.joinToString(" · ")}"
        }
    }
}

/**
 * Collects the 6-part GameNothing 2.0 Container Diagnostic Bundle on [Dispatchers.IO].
 * Scrubs all credentials, auth tokens, and personal host paths before returning.
 */
object ContainerDiagnosticCollector {

    private const val MAX_LOG_LINES = 100
    private const val MAX_CONFIG_FILE_BYTES = 64 * 1024L
    private const val MAX_CONFIG_EXCERPT_LINES = 70
    private const val MAX_DISCOVERED_CONFIGS = 2

    private val HIGH_SIGNAL_LOG_MARKERS = listOf(
        "err:",
        "error",
        "fixme:d3d",
        "fixme:vulkan",
        "vkcreate",
        "vulkan",
        "dxvk",
        "vkd3d",
        "fex",
        "box64",
        "sigsegv",
        "sigabrt",
        "unhandled exception",
        "page fault",
        "out of memory",
        "x11",
        "gamescope",
        "turnip",
        "adreno",
        "vortek",
        "pulseaudio",
        "alsa",
        "fatal",
    )

    private val EXCLUDED_CONFIG_NAMES = setOf(
        "steam_api.ini",
        "steam_api64.ini",
        "coldclientloader.ini",
        "goldberg.ini",
        "account.ini",
        "user_steam_id.txt",
        "token.ini",
        "desktop.ini",
    )

    private val SECRET_ENV_KEYS = setOf(
        "STEAMHOST_TOKEN",
        "STEAM_TOKEN",
        "AUTH_PASSWORD",
        "EPIC_TOKEN",
        "GOG_TOKEN",
        "API_KEY",
        "ACCESS_TOKEN",
        "REFRESH_TOKEN",
    )

    suspend fun collect(
        context: Context,
        appId: String?,
        currentConfig: ContainerData,
    ): ContainerDiagnosticBundle = withContext(Dispatchers.IO) {
        val gameName = if (!appId.isNullOrBlank()) {
            runCatching { ContainerUtils.resolveGameName(appId) }.getOrNull().orEmpty().ifBlank { appId }
        } else {
            "Container"
        }

        val container = if (!appId.isNullOrBlank()) {
            runCatching {
                if (ContainerUtils.hasContainer(context, appId)) {
                    ContainerUtils.getContainer(context, appId)
                } else {
                    null
                }
            }.getOrNull()
        } else {
            null
        }

        // 1. Telemetry & Last-Run Performance
        val avgFps = container?.getSessionMetadata("avg_fps", "")?.toFloatOrNull()
        val sessionLengthSec = container?.getSessionMetadata("session_length_sec", "")?.toIntOrNull()
        val totalFrames = container?.getSessionMetadata("total_frames", "")?.toLongOrNull()

        var adaptiveFpsCapEnabled = false
        val powerProfileJson = container?.rootDir?.let { root ->
            val file = File(root, ".power_profile.json")
            if (file.exists()) {
                runCatching {
                    val obj = JSONObject(file.readText())
                    adaptiveFpsCapEnabled = obj.optBoolean("adaptiveFpsCapEnabled", false)
                    obj
                }.getOrNull()
            } else {
                null
            }
        }

        val newestRunJson = container?.rootDir?.let { root ->
            val runsDir = File(root, ".gamenative/runs")
            runsDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.maxByOrNull { it.lastModified() }
                ?.let { file -> runCatching { JSONObject(file.readText()) }.getOrNull() }
        }

        val newestReportDir = if (!appId.isNullOrBlank()) {
            runCatching { DebugReportUtils.newestReport(context, appId) }.getOrNull()
        } else {
            null
        }
        val reportPerfJson = newestReportDir?.let { dir ->
            val perfFile = DebugReportUtils.perfFile(dir)
            if (perfFile.exists()) runCatching { JSONObject(perfFile.readText()) }.getOrNull() else null
        }
        val reportHeaderJson = newestReportDir?.let { dir ->
            DebugReportUtils.readHeader(dir)
        }

        val thermalHeadroom = runCatching { ThermalHeadroom.read(context) ?: ThermalHeadroom.latest }.getOrNull()
        val thermalStatus = runCatching { ThermalHeadroom.status(context) ?: ThermalHeadroom.latestStatus }.getOrNull()

        // 2. Exit traces & filtered runtime logs
        val recentExits = runCatching { AppExitRecorder.recentExits(context, 0L, 3) }.getOrDefault(emptyList())
        val lastExitReason = recentExits.firstOrNull()?.optString("reason")?.takeIf { it.isNotBlank() }
        val exitTraceSnippet = recentExits.firstOrNull()?.let { exitObj ->
            runCatching {
                AppExitRecorder.traceFor(context, exitObj)?.takeIf { it.exists() }?.useLines { seq ->
                    seq.take(35).map { redactSecrets(it) }.joinToString("\n")
                }
            }.getOrNull()
        } ?: newestReportDir?.let { dir ->
            val traceFile = DebugReportUtils.exitTraceFile(dir)
            if (traceFile.exists()) {
                runCatching {
                    traceFile.useLines { seq -> seq.take(35).map { redactSecrets(it) }.joinToString("\n") }
                }.getOrNull()
            } else {
                null
            }
        }

        val filteredLogLines = collectFilteredLogLines(context, appId, newestReportDir)

        // 3. Hardware & Thermal Profile
        val gpuRenderer = runCatching { GPUInformation.getRenderer(context) }.getOrNull().orEmpty().ifBlank { "Unknown GPU" }
        val gpuVersion = runCatching { GPUInformation.getVersion(context) }.getOrNull().orEmpty()
        val socName = runCatching { HardwareUtils.getSOCName() }.getOrNull().orEmpty().ifBlank {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = if (memInfo.totalMem > 0) memInfo.totalMem / (1024 * 1024) else 0L
        val availableRamMb = if (memInfo.availMem > 0) memInfo.availMem / (1024 * 1024) else 0L
        val cpuClusters = runCatching { RootlessGamePinner.detectCpuClusters() }.getOrNull()

        // 4. Installed Components Inventory
        val installedLists = runCatching { ManifestComponentHelper.loadInstalledContentLists(context) }.getOrNull()
        val installedComponents = InstalledComponentsSnapshot(
            wine = installedLists?.installed?.wine ?: emptyList(),
            proton = installedLists?.installed?.proton ?: emptyList(),
            dxvk = installedLists?.installed?.dxvk ?: emptyList(),
            vkd3d = installedLists?.installed?.vkd3d ?: emptyList(),
            fexcore = installedLists?.installed?.fexcore ?: emptyList(),
            box64 = installedLists?.installed?.box64 ?: emptyList(),
            wowBox64 = installedLists?.installed?.wowBox64 ?: emptyList(),
            wrapper = installedLists?.installed?.wrapper ?: emptyList(),
            installedDrivers = installedLists?.installedDrivers ?: listOf("System"),
        )

        // 5. Shallow < 64 KB Game .ini / .cfg Discovery
        val discoveredConfigs = if (!appId.isNullOrBlank()) {
            discoverGameConfigFiles(context, appId)
        } else {
            emptyList()
        }

        // 6. Primary Bottleneck Classification
        val bottleneckGuess = classifyPrimaryBottleneck(
            config = currentConfig,
            adaptiveFpsCapEnabled = adaptiveFpsCapEnabled,
            thermalHeadroom = thermalHeadroom,
            thermalStatus = thermalStatus,
            avgFps = avgFps,
            lastExitReason = lastExitReason,
            reportHeaderJson = reportHeaderJson,
            newestRunJson = newestRunJson,
        )

        val bundleJson = JSONObject().apply {
            put(
                "telemetry",
                JSONObject().apply {
                    put("gameName", gameName)
                    put("appId", appId ?: JSONObject.NULL)
                    put("primaryBottleneckGuess", bottleneckGuess)
                    put("avgFps", avgFps?.toDouble() ?: JSONObject.NULL)
                    put("sessionLengthSec", sessionLengthSec ?: JSONObject.NULL)
                    put("totalFrames", totalFrames ?: JSONObject.NULL)
                    put("adaptiveFpsCapEnabled", adaptiveFpsCapEnabled)
                    put("fpsLimiterEnabled", currentConfig.fpsLimiterEnabled)
                    put("tuningMode", currentConfig.tuningMode)
                    put("performanceMode", currentConfig.isPerformanceMode)
                    if (newestRunJson != null) {
                        put("latestRunSummary", newestRunJson)
                    }
                    if (reportPerfJson != null) {
                        put("perfSummary", summarizePerfJson(reportPerfJson))
                    }
                    if (powerProfileJson != null) {
                        put("powerProfile", powerProfileJson)
                    }
                },
            )
            put(
                "logsAndExits",
                JSONObject().apply {
                    put(
                        "recentExits",
                        JSONArray().apply {
                            recentExits.forEach { put(it) }
                        },
                    )
                    if (!exitTraceSnippet.isNullOrBlank()) {
                        put("exitTraceSnippet", exitTraceSnippet)
                    }
                    put("filteredWineAndVulkanLogs", JSONArray(filteredLogLines))
                },
            )
            put("containerConfig", buildContainerConfigJson(currentConfig))
            put(
                "hardwareAndThermal",
                JSONObject().apply {
                    put("manufacturer", Build.MANUFACTURER)
                    put("model", Build.MODEL)
                    put("socName", socName)
                    put("gpuRenderer", gpuRenderer)
                    put("gpuVersion", gpuVersion)
                    put("androidRelease", Build.VERSION.RELEASE)
                    put("androidSdk", Build.VERSION.SDK_INT)
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("totalRamMb", totalRamMb)
                    put("availableRamMb", availableRamMb)
                    put("thermalHeadroom", thermalHeadroom?.toDouble() ?: JSONObject.NULL)
                    put("thermalStatus", thermalStatus ?: JSONObject.NULL)
                    put("allCpuCoresList", PerformanceModeManager.getAllCpuCoresList())
                    if (cpuClusters != null) {
                        put(
                            "cpuClusters",
                            JSONObject().apply {
                                put("allCores", JSONArray(cpuClusters.allCores.toList()))
                                put("littleCores", JSONArray(cpuClusters.littleCores.toList()))
                                put("bigPrimeCores", JSONArray(cpuClusters.bigPrimeCores.toList()))
                                put("bigPrimeMaskHex", String.format(Locale.US, "0x%x", cpuClusters.bigPrimeMask))
                            },
                        )
                    }
                },
            )
            put("installedComponents", installedComponents.toJson())
            put(
                "discoveredGameConfigs",
                JSONArray().apply {
                    discoveredConfigs.forEach { cfg ->
                        put(
                            JSONObject().apply {
                                put("relativePath", cfg.relativePath)
                                put("sizeBytes", cfg.sizeBytes)
                                put("excerpt", cfg.excerpt)
                            },
                        )
                    }
                },
            )
        }

        ContainerDiagnosticBundle(
            appId = appId,
            gameName = gameName,
            config = currentConfig,
            primaryBottleneckGuess = bottleneckGuess,
            avgFps = avgFps,
            sessionLengthSec = sessionLengthSec,
            adaptiveFpsCapEnabled = adaptiveFpsCapEnabled,
            thermalStatus = thermalStatus,
            thermalHeadroom = thermalHeadroom,
            gpuRenderer = gpuRenderer,
            socName = socName,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            lastExitReason = lastExitReason,
            filteredLogLines = filteredLogLines,
            installedComponents = installedComponents,
            discoveredConfigs = discoveredConfigs,
            bundleJson = bundleJson,
        )
    }

    private fun classifyPrimaryBottleneck(
        config: ContainerData,
        adaptiveFpsCapEnabled: Boolean,
        thermalHeadroom: Float?,
        thermalStatus: Int?,
        avgFps: Float?,
        lastExitReason: String?,
        reportHeaderJson: JSONObject?,
        newestRunJson: JSONObject?,
    ): String {
        if (lastExitReason in setOf("CRASH_NATIVE", "CRASH", "ANR", "SIGNALED", "LOW_MEMORY")) {
            return "Crash / Launch"
        }
        if (config.fpsLimiterEnabled || adaptiveFpsCapEnabled) {
            return "FPS Cap"
        }
        if ((thermalHeadroom != null && thermalHeadroom >= 0.95f) || (thermalStatus != null && thermalStatus >= 3)) {
            return "Heat"
        }
        val reportVerdict = reportHeaderJson?.optJSONObject("perf")?.optString("bottleneck")
            ?: newestRunJson?.optString("bottleneck")
        if (!reportVerdict.isNullOrBlank()) {
            return when {
                reportVerdict.contains("gpu", ignoreCase = true) -> "GPU"
                reportVerdict.contains("cpu", ignoreCase = true) -> "CPU"
                reportVerdict.contains("therm", ignoreCase = true) || reportVerdict.contains("heat", ignoreCase = true) -> "Heat"
                else -> reportVerdict
            }
        }
        val allCores = PerformanceModeManager.getAllCpuCoresList()
        if (config.cpuList != allCores || config.fexcorePreset.uppercase(Locale.ROOT) in setOf("STABILITY", "COMPATIBILITY")) {
            return "CPU"
        }
        if (avgFps != null && avgFps < 30f) {
            return "GPU"
        }
        return "CPU"
    }

    private fun summarizePerfJson(perfJson: JSONObject): JSONObject {
        val summary = JSONObject()
        for (key in listOf("verdict", "bottleneck", "fps", "cpu", "gpu", "thermal", "memory", "summary")) {
            if (perfJson.has(key)) {
                summary.put(key, perfJson.opt(key))
            }
        }
        return if (summary.length() > 0) summary else perfJson
    }

    private fun buildContainerConfigJson(config: ContainerData): JSONObject = JSONObject().apply {
        put("containerVariant", config.containerVariant)
        put("wineVersion", config.wineVersion)
        put("displayServer", config.displayServer)
        put("displayRenderer", config.displayRenderer)
        put("rendererPresentMode", config.rendererPresentMode)
        put("graphicsDriver", config.graphicsDriver)
        put("graphicsDriverVersion", config.graphicsDriverVersion)
        put("graphicsDriverConfig", config.graphicsDriverConfig)
        put("dxwrapper", config.dxwrapper)
        put("dxwrapperConfig", config.dxwrapperConfig)
        put("emulator", config.emulator)
        put("fexcoreVersion", config.fexcoreVersion)
        put("fexcorePreset", config.fexcorePreset)
        put("fexcoreTSOMode", config.fexcoreTSOMode)
        put("fexcoreX87Mode", config.fexcoreX87Mode)
        put("fexcoreMultiBlock", config.fexcoreMultiBlock)
        put("box64Version", config.box64Version)
        put("box64Preset", config.box64Preset)
        put("cpuList", config.cpuList)
        put("cpuListWoW64", config.cpuListWoW64)
        put("fpsLimiterEnabled", config.fpsLimiterEnabled)
        put("tuningMode", config.tuningMode)
        put("performanceMode", config.isPerformanceMode)
        put("rootlessGamePinningEnabled", config.rootlessGamePinningEnabled)
        put("screenSize", config.screenSize)
        put("videoMemorySize", config.videoMemorySize)
        put("audioDriver", config.audioDriver)
        put("pulseaudioLowLatency", config.pulseaudioLowLatency)
        put("wincomponents", config.wincomponents)
        put("csmt", config.csmt)
        put("strictShaderMath", config.strictShaderMath)
        put("offScreenRenderingMode", config.offScreenRenderingMode)
        put("useDRI3", config.useDRI3)
        put("lsfgEnabled", config.lsfgEnabled)
        put("sharpnessEffect", config.sharpnessEffect)
        put("steamType", config.steamType)
        put("useLegacyDRM", config.useLegacyDRM)
        put("unpackFiles", config.unpackFiles)
        put("envVars", redactEnvVars(config.envVars))
        put("execArgs", redactSecrets(config.execArgs))
    }

    private fun collectFilteredLogLines(
        context: Context,
        appId: String?,
        newestReportDir: File?,
    ): List<String> {
        val rawLines = mutableListOf<String>()

        if (!appId.isNullOrBlank()) {
            val activeLog = DebugReportUtils.wineLogFile(context, appId)
            if (activeLog.exists() && activeLog.length() > 0L) {
                readHighSignalLinesFromFile(activeLog, isGzip = false, into = rawLines)
            }
        }

        if (rawLines.isEmpty() && newestReportDir != null) {
            val gzLog = DebugReportUtils.logFile(newestReportDir)
            if (gzLog.exists() && gzLog.length() > 0L) {
                readHighSignalLinesFromFile(gzLog, isGzip = true, into = rawLines)
            }
        }

        if (rawLines.isEmpty()) {
            val genericLog = File(context.getExternalFilesDir(null), "wine_logs/wine_debug.log")
            if (genericLog.exists() && genericLog.length() > 0L) {
                readHighSignalLinesFromFile(genericLog, isGzip = false, into = rawLines)
            }
        }

        if (rawLines.isEmpty()) return emptyList()

        // Deduplicate consecutive or repeated lines while preserving signal
        val counts = LinkedHashMap<String, Int>()
        for (line in rawLines) {
            val cleaned = redactSecrets(line.trim()).take(240)
            if (cleaned.isNotEmpty()) {
                counts[cleaned] = (counts[cleaned] ?: 0) + 1
            }
        }

        return counts.entries
            .toList()
            .takeLast(MAX_LOG_LINES)
            .map { (line, count) -> if (count > 1) "$line (repeated ${count}x)" else line }
    }

    private fun readHighSignalLinesFromFile(
        file: File,
        isGzip: Boolean,
        into: MutableList<String>,
    ) {
        runCatching {
            val inputStream = if (isGzip) {
                GZIPInputStream(FileInputStream(file))
            } else {
                FileInputStream(file)
            }
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                var scanned = 0
                while (reader.readLine().also { line = it } != null && scanned < 25_000) {
                    scanned++
                    val current = line ?: continue
                    val lower = current.lowercase(Locale.ROOT)
                    if (HIGH_SIGNAL_LOG_MARKERS.any { lower.contains(it) }) {
                        into.add(current)
                        if (into.size > 600) {
                            into.removeAt(0)
                        }
                    }
                }
            }
        }
    }

    private fun discoverGameConfigFiles(
        context: Context,
        appId: String,
    ): List<DiscoveredGameConfigFile> {
        val roots = runCatching { SupportGameFiles.roots(context, appId) }.getOrNull() ?: return emptyList()
        val candidates = mutableListOf<Pair<String, File>>()

        fun scanDir(baseDir: File, prefixLabel: String, currentDir: File, depth: Int) {
            if (depth > 3 || candidates.size >= 16) return
            val children = currentDir.listFiles() ?: return
            for (child in children) {
                if (candidates.size >= 16) break
                val nameLower = child.name.lowercase(Locale.ROOT)
                if (child.isDirectory) {
                    if (nameLower !in setOf(".git", "shadercache", "crashes", "logs", "temp", "cache")) {
                        scanDir(baseDir, prefixLabel, child, depth + 1)
                    }
                } else if (child.isFile) {
                    val isConfigExt = nameLower.endsWith(".ini") || nameLower.endsWith(".cfg")
                    if (!isConfigExt) continue
                    if (nameLower in EXCLUDED_CONFIG_NAMES) continue
                    if (nameLower.contains("save") || nameLower.contains("steam_api") || nameLower.contains("token")) continue
                    val len = child.length()
                    if (len in 16L until MAX_CONFIG_FILE_BYTES) {
                        val rel = child.canonicalPath.removePrefix(baseDir.canonicalPath).trimStart('\\', '/')
                        val fullRel = if (prefixLabel.isEmpty()) rel.replace('\\', '/') else "$prefixLabel/${rel.replace('\\', '/')}"
                        candidates.add(fullRel to child)
                    }
                }
            }
        }

        if (roots.install.isDirectory) {
            scanDir(roots.install, "", roots.install, 0)
        }
        roots.userHome?.let { userHome ->
            val docs = File(userHome, "Documents")
            if (docs.isDirectory) scanDir(docs, "%DOCUMENTS%", docs, 0)
            val localAppData = File(userHome, "AppData/Local")
            if (localAppData.isDirectory) scanDir(localAppData, "%LOCALAPPDATA%", localAppData, 0)
        }

        return candidates
            .sortedByDescending { (rel, file) ->
                val lower = rel.lowercase(Locale.ROOT)
                var score = 0
                if (lower.contains("engine") || lower.contains("gameusersettings") || lower.contains("graphics") || lower.contains("settings") || lower.contains("system")) {
                    score += 10
                }
                if (file.length() in 64L..16_384L) score += 5
                score
            }
            .take(MAX_DISCOVERED_CONFIGS)
            .mapNotNull { (relPath, file) ->
                runCatching {
                    val excerpt = file.useLines { seq ->
                        seq.take(MAX_CONFIG_EXCERPT_LINES)
                            .map { redactSecrets(it) }
                            .joinToString("\n")
                    }
                    DiscoveredGameConfigFile(
                        relativePath = relPath,
                        canonicalPath = file.canonicalPath,
                        sizeBytes = file.length(),
                        excerpt = excerpt,
                    )
                }.getOrNull()
            }
    }

    fun redactEnvVars(rawEnvVars: String): String {
        if (rawEnvVars.isBlank()) return ""
        return rawEnvVars.split(" ").joinToString(" ") { token ->
            val eqIdx = token.indexOf('=')
            if (eqIdx <= 0) {
                token
            } else {
                val key = token.substring(0, eqIdx)
                if (SECRET_ENV_KEYS.any { key.contains(it, ignoreCase = true) }) {
                    "$key=<REDACTED>"
                } else {
                    redactSecrets(token)
                }
            }
        }
    }

    fun redactSecrets(input: String): String {
        if (input.isBlank()) return input
        var out = input
        out = out.replace(Regex("(?i)(STEAMHOST_TOKEN|AUTH_PASSWORD|epicovt|Bearer\\s+)[=:\\s]+[^\\s\"']+"), "$1=<REDACTED>")
        out = out.replace(Regex("sk-or-v1-[A-Za-z0-9_-]+"), "<REDACTED_OPENROUTER_KEY>")
        out = out.replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "<REDACTED_GEMINI_KEY>")
        return out
    }
}
