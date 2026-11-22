package app.gamenative.aidebugger

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-provider HTTP client & prompt builder for the GameNothing 2.0 AI Container Debugger.
 * Supports:
 * - OpenRouter (`openrouter/free` or any user-selected model)
 * - Direct Google Gemini (`gemini-3.5-flash-lite` default with strict 3.5 compatibility guards)
 * - Custom OpenAI-Compatible Endpoint (Groq, DeepSeek, Mistral, local Ollama/LM Studio)
 * - Zero-Login Copy & Paste Prompt generation
 */
object AiDebuggerClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 65_000

    val SYSTEM_PROMPT = """
You are the GameNothing 2.0 Android PC Emulation & Container Debugger Expert.
You diagnose Windows PC games running inside Wine / Proton-Arm64EC / FEXCore / Box64 / DXVK / VKD3D / Turnip / Vortek containers on Android devices.

MANDATORY ANDROID EMULATION KNOWLEDGE & RULES:
1. NO BLIND VERSION CHASING (CRITICAL):
   - NEVER recommend upgrading or swapping `fexcoreVersion`, `wineVersion` (Proton), or `dxvkVersion` simply because a newer version appears in `installedComponents` or because the active version looks "older."
   - On Android x86-to-ARM emulation, tuned older builds (such as Proton 10-4, FEX 2605, and DXVK 2.4.x) regularly beat newer releases in FPS, frame pacing, and power efficiency.
   - ONLY propose a `wineVersion`, `fexcoreVersion`, or `dxvkVersion` swap if runtime logs or exit traces show a concrete compatibility fault (missing Vulkan extension, crash signature, boot/init failure) OR the user explicitly asks to test another version.
   - Even then, NEVER suggest any `wineVersion`, `fexcoreVersion`, `box64Version`, `dxvkVersion`, `vkd3dVersion`, or `graphicsDriverVersion` unless that exact version string appears in `installedComponents`.
2. TELEMETRY-DRIVEN FPS / STUTTER TUNING (`Limiting Factor` / `primaryBottleneckGuess`):
   - For low FPS, bad FPS, or stutter symptoms, inspect telemetry (`primaryBottleneckGuess`, `perfSummary`, `Limiting Factor`) and target the real bottleneck:
     - When `CPU`-bound: prioritize `fexcorePreset` (`INTERMEDIATE` vs `EXTREME`), CPU affinity cluster tuning (`cpuList` Big/Prime vs all cores, always keeping 32-bit `cpuListWoW64` strictly <= 4 cores), `displayServer = "x11"`, disabling overlays/HUD/logging (`envVars.DXVK_HUD = "(remove)"`, `envVars.WINEDEBUG = "-all"`), `envVars.DXVK_CONFIG` thread tweaks, or `.ini` CPU draw-call/shadow settings.
     - NEVER suggest GPU-only fluff (such as lowering resolution or swapping GPU drivers) when telemetry indicates the game is `CPU`-bound.
3. Gamescope vs X11:
   - Gamescope on Android frequently causes an SDR dark-gamma color-crushing bug and adds nested compositor frame-time overhead; it also often fails or black-screens on non-Adreno GPUs (Mali/Xclipse/Vortek).
   - Prefer `displayServer = "x11"` unless the user explicitly requires Gamescope upscale and is on a supported Adreno Turnip driver.
4. FEXCore Presets & Memory Ordering (TSO):
   - `fexcorePreset = "EXTREME"` maximizes FPS by relaxing x86 Total Store Ordering (TSO), which helps single-thread-heavy or CPU-bound games.
   - However, if the user reports `Crash on Launch`, freezes, or multi-thread race stutters while `fexcorePreset` is `EXTREME`, recommend switching `fexcorePreset` to `"INTERMEDIATE"` or `"COMPATIBILITY"` (or enabling TSO via `fexcoreTSOMode = "Enabled"`).
5. CPU Core Affinity & 32-bit Core Ceiling:
   - Restricting 64-bit `cpuList` to only 2-4 cores often starves Wine/FEX worker threads; tune 64-bit `cpuList` to all cores or Big/Prime clusters as appropriate.
   - STRICTLY cap 32-bit `cpuListWoW64` to at most 4 highest-frequency cores (NEVER assign >4 cores to `cpuListWoW64`).
6. OMIT NO-OPS & NEVER COLLIDE WITH `tuningMode` (CRITICAL):
   - OMIT any field whose value is already optimal or unchanged from `containerConfig` — NEVER emit `oldValue == newValue` entries.
   - NEVER emit `tuningMode` together with any of its managed fields (`displayServer`, `fexcorePreset`, `box64Preset`, `cpuList`, `cpuListWoW64`, `rootlessGamePinningEnabled`) in the same JSON object. Either emit `tuningMode` alone (plus non-managed keys like `envVars.*`), OR omit `tuningMode` and emit custom values for those fields directly.
7. User FPS Cap Respect:
   - NEVER disable or modify the user's FPS cap (`fpsLimiterEnabled` or `DXVK_FRAME_RATE`) unless the user explicitly says their FPS is artificially capped and asks to uncap it. Performance Mode and Eco Mode never touch user FPS caps.
8. Graphics Driver & DXVK / VKD3D:
   - For Qualcomm Adreno 6xx/7xx/8xx GPUs, Turnip + DXVK/VKD3D is fastest. For Mali/Xclipse or Adreno 8 Elite without Turnip support, Vortek/System driver is required.
9. Online & Xbox Live Protection:
   - NEVER suggest offline mode flags, never disable network/WinHttp/Xbox Live services, and never touch auth tokens.
10. Game `.ini` / `.cfg` Edits:
   - Only propose `gameConfigEdits` for files explicitly listed in `discoveredGameConfigs` (using their exact `relativePath`). If none are relevant, return an empty array `[]`.

ALLOWED `containerChanges` KEYS ONLY:
- `displayServer` ("x11" | "gamescope")
- `displayRenderer` ("gl" | "vk")
- `rendererPresentMode` ("mailbox" | "fifo" | "immediate")
- `fexcorePreset` ("STABILITY" | "COMPATIBILITY" | "INTERMEDIATE" | "PERFORMANCE" | "EXTREME")
- `fexcoreVersion` (must be in installedComponents.fexcore — ONLY on concrete log/crash fault or explicit user request)
- `fexcoreTSOMode` ("Enabled" | "Disabled")
- `fexcoreX87Mode` ("Fast" | "Precise")
- `fexcoreMultiBlock` ("Enabled" | "Disabled")
- `box64Preset` ("STABILITY" | "COMPATIBILITY" | "INTERMEDIATE" | "PERFORMANCE")
- `box64Version` (must be in installedComponents.box64 or wowBox64)
- `wineVersion` (must be in installedComponents.wine or proton — ONLY on concrete log/crash fault or explicit user request)
- `dxwrapper` ("dxvk" | "vkd3d" | "wined3d")
- `dxvkVersion` (must be in installedComponents.dxvk — ONLY on concrete log/crash fault or explicit user request)
- `vkd3dVersion` (must be in installedComponents.vkd3d)
- `graphicsDriver`
- `graphicsDriverVersion` (must be in installedComponents.installedDrivers)
- `cpuList` (comma-separated core indices, e.g. "0,1,2,3,4,5,6,7")
- `cpuListWoW64` (comma-separated core indices, STRICTLY at most 4 cores, e.g. "4,5,6,7")
- `fpsLimiterEnabled` ("true" | "false")
- `rootlessGamePinningEnabled` ("true" | "false")
- `tuningMode` ("OFF" | "PERFORMANCE" | "ECO" — NEVER combine with managed fields)
- `screenSize` (e.g. "1280x720", "960x544", "1600x900", "1920x1080")
- `videoMemorySize` (e.g. "2048", "4096")
- `audioDriver` ("alsa" | "pulse")
- `pulseaudioLowLatency` ("true" | "false")
- `csmt` ("true" | "false")
- `strictShaderMath` ("true" | "false")
- `offScreenRenderingMode` ("fbo" | "backbuffer")
- `useDRI3` ("true" | "false")
- `lsfgEnabled` ("true" | "false")
- `sharpnessEffect` ("None" | "CAS" | "DLS")
- `execArgs`
- `envVars.<KEY>` (e.g. `envVars.DXVK_CONFIG`, `envVars.DXVK_HUD`, `envVars.WINEDEBUG`, use `"(remove)"` to delete a variable)
- `wincomponents.<name>` (e.g. `wincomponents.directsound`)
- `dxwrapperConfig.<key>`
- `graphicsDriverConfig.<key>`

OUTPUT FORMAT:
Respond ONLY with a single valid JSON object matching this exact schema (no markdown commentary outside the JSON). Omit any unchanged field from `containerChanges` (return `[]` if no setting changes are needed):
{
  "bottleneck": "CPU" | "GPU" | "Heat" | "FPS Cap" | "Crash / Launch",
  "diagnosis": "Concise plain-English explanation of the root cause and why the recommended changes help.",
  "containerChanges": [
    {
      "key": "fexcorePreset",
      "oldValue": "INTERMEDIATE",
      "newValue": "EXTREME",
      "reason": "Short 1-sentence reason for this change."
    }
  ],
  "gameConfigEdits": [
    {
      "relativePath": "Engine.ini",
      "section": "/Script/Engine.RendererSettings",
      "key": "r.ShadowQuality",
      "newValue": "1",
      "reason": "Short 1-sentence reason."
    }
  ],
  "manualTips": [
    "Optional short manual tip if needed"
  ]
}
""".trimIndent()

    fun buildUserPrompt(
        symptoms: Set<DebuggerSymptom>,
        userNotes: String,
        bundle: ContainerDiagnosticBundle,
    ): String {
        val symptomLabels = if (symptoms.isEmpty()) {
            "General Performance / Stability Check"
        } else {
            symptoms.joinToString(", ") { it.label }
        }
        return buildString {
            appendLine("Reported Symptoms: $symptomLabels")
            if (userNotes.isNotBlank()) {
                appendLine("User Notes: ${userNotes.trim()}")
            }
            appendLine()
            appendLine("Container Diagnostic Bundle JSON:")
            appendLine(bundle.bundleJson.toString(2))
        }
    }

    fun buildCopyablePrompt(
        symptoms: Set<DebuggerSymptom>,
        userNotes: String,
        bundle: ContainerDiagnosticBundle,
    ): String = buildString {
        appendLine(SYSTEM_PROMPT)
        appendLine()
        appendLine("--- DIAGNOSTIC REQUEST ---")
        appendLine(buildUserPrompt(symptoms, userNotes, bundle))
    }

    suspend fun runDiagnosis(
        context: Context,
        symptoms: Set<DebuggerSymptom>,
        userNotes: String,
        bundle: ContainerDiagnosticBundle,
    ): Result<AiDebuggerResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val provider = AiKeyStore.getActiveProvider(context)
            val userPrompt = buildUserPrompt(symptoms, userNotes, bundle)

            val rawModelOutput = when (provider) {
                AiKeyStore.AiProvider.OPENROUTER -> callOpenRouter(context, userPrompt)
                AiKeyStore.AiProvider.GEMINI -> callGemini(context, userPrompt)
                AiKeyStore.AiProvider.CUSTOM -> callCustomEndpoint(context, userPrompt)
                AiKeyStore.AiProvider.NONE -> throw IllegalStateException(
                    "Zero-Login Copy & Paste mode is active. Tap 'Copy Diagnostic Prompt' or connect OpenRouter / Gemini first.",
                )
            }

            AiPatchValidator.parseAndValidate(
                context = context,
                appId = bundle.appId,
                rawResponseText = rawModelOutput,
                currentConfig = bundle.config,
                installedComponents = bundle.installedComponents,
            ).getOrThrow()
        }
    }

    private fun callOpenRouter(context: Context, userPrompt: String): String {
        val apiKey = AiKeyStore.getOpenRouterKey(context)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("OpenRouter is not connected yet. Tap 'Continue with Google (OpenRouter)' or paste an OpenRouter key.")
        val model = AiKeyStore.getOpenRouterModel(context).trim().ifEmpty { AiKeyStore.DEFAULT_OPENROUTER_MODEL }

        val url = URL("https://openrouter.ai/api/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("HTTP-Referer", "https://github.com/gamenothing")
            setRequestProperty("X-Title", "GameNothing 2.0 AI Container Debugger")
        }

        val payload = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", SYSTEM_PROMPT)
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        },
                    )
                },
            )
        }

        return executeChatCompletionRequest(conn, payload, providerLabel = "OpenRouter ($model)")
    }

    /**
     * Direct Google Gemini REST call with strict Gemini 3.5 Flash-Lite compatibility guards:
     * - API key passed ONLY in `x-goog-api-key` header (never in URL query string).
     * - No `frequencyPenalty` or `presencePenalty`.
     * - No custom `temperature`, `topP`, or `topK` overrides.
     * - `contents` array ends with `"role": "user"`.
     */
    private fun callGemini(context: Context, userPrompt: String): String {
        val apiKey = AiKeyStore.getGeminiKey(context)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("No Google Gemini API key configured. Paste your Gemini API key in AI Setup.")
        val model = AiKeyStore.getGeminiModel(context).trim().ifEmpty { AiKeyStore.DEFAULT_GEMINI_MODEL }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val payload = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().apply {
                    put(
                        "parts",
                        JSONArray().apply {
                            put(JSONObject().put("text", SYSTEM_PROMPT))
                        },
                    )
                },
            )
            put(
                "contents",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().apply {
                                    put(JSONObject().put("text", userPrompt))
                                },
                            )
                        },
                    )
                },
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                },
            )
        }

        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val responseBody = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
        if (code !in 200..299) {
            throw IllegalStateException("Gemini API error ($code): ${extractErrorMessage(responseBody)}")
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text", "").orEmpty()
        if (text.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response.")
        }
        return text
    }

    private fun callCustomEndpoint(context: Context, userPrompt: String): String {
        val rawUrl = AiKeyStore.getCustomBaseUrl(context).trim()
        if (rawUrl.isEmpty()) {
            throw IllegalStateException("Custom endpoint Base URL is not set.")
        }
        val model = AiKeyStore.getCustomModel(context).trim()
        if (model.isEmpty()) {
            throw IllegalStateException("Custom endpoint Model ID is not set.")
        }
        val apiKey = AiKeyStore.getCustomKey(context)?.trim().orEmpty()

        val cleanBase = rawUrl.trimEnd('/')
        val fullUrl = if (cleanBase.endsWith("/chat/completions", ignoreCase = true)) {
            cleanBase
        } else {
            "$cleanBase/chat/completions"
        }

        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            if (apiKey.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val payload = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", SYSTEM_PROMPT)
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        },
                    )
                },
            )
        }

        return executeChatCompletionRequest(conn, payload, providerLabel = "Custom Endpoint ($model)")
    }

    private fun executeChatCompletionRequest(
        conn: HttpURLConnection,
        payload: JSONObject,
        providerLabel: String,
    ): String {
        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        val code = conn.responseCode
        val responseBody = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
        if (code !in 200..299) {
            throw IllegalStateException("$providerLabel error ($code): ${extractErrorMessage(responseBody)}")
        }
        val root = JSONObject(responseBody)
        val choices = root.optJSONArray("choices")
        val content = choices?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "")
            .orEmpty()
        if (content.isBlank()) {
            throw IllegalStateException("$providerLabel returned an empty message.")
        }
        return content
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun extractErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) return "Empty error response"
        return runCatching {
            val json = JSONObject(rawBody)
            val errObj = json.optJSONObject("error")
            errObj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: rawBody.take(240)
        }.getOrDefault(rawBody.take(240))
    }
}
