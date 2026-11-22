package app.gamenative.aidebugger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * OpenRouter 1-Tap "Continue with Google" OAuth PKCE Client (GameNothing 2.0 Section 4.1.1).
 *
 * Implements:
 * 1. Cryptographically random 32-byte `code_verifier` (Base64URL without padding),
 *    SHA-256 `code_challenge` (`S256`), and random `state` token.
 * 2. Ephemeral loopback HTTP listener on `127.0.0.1` (`ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))`)
 *    running on `Dispatchers.IO` with a 3-minute timeout.
 * 3. Chrome Custom Tabs launch to `https://openrouter.ai/auth?callback_url=...&code_challenge=...&code_challenge_method=S256&state=...`.
 * 4. Automatic `/callback` capture + friendly HTML page + `POST https://openrouter.ai/api/v1/auth/keys`
 *    exchange + encrypted save via [AiKeyStore].
 * 5. Manual fallback (`exchangeManualCode`) if the user's browser blocks localhost redirects.
 */
object OpenRouterPkceAuth {

    private const val AUTH_URL = "https://openrouter.ai/auth"
    private const val TOKEN_EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"
    private const val LOOPBACK_TIMEOUT_MS = 180_000 // 3 minutes

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    data class PkceSession(
        val codeVerifier: String,
        val codeChallenge: String,
        val state: String,
    )

    @Volatile
    private var activeSession: PkceSession? = null

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    sealed class AuthResult {
        data object Connected : AuthResult()
        data class NeedsManualCode(val reason: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    fun generatePkceSession(): PkceSession {
        val random = SecureRandom()
        val verifierBytes = ByteArray(32)
        random.nextBytes(verifierBytes)
        val codeVerifier = base64UrlNoPad(verifierBytes)

        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = base64UrlNoPad(digest)

        val stateBytes = ByteArray(16)
        random.nextBytes(stateBytes)
        val state = base64UrlNoPad(stateBytes)

        return PkceSession(
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
            state = state,
        ).also {
            activeSession = it
        }
    }

    /**
     * Starts the 1-tap OpenRouter OAuth PKCE flow with loopback server + Chrome Custom Tab.
     */
    suspend fun startOneTapLogin(
        context: Context,
        onBrowserLaunched: () -> Unit = {},
    ): AuthResult = withContext(Dispatchers.IO) {
        cancelActiveServer()
        val session = generatePkceSession()

        val serverSocket = try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
                soTimeout = LOOPBACK_TIMEOUT_MS
            }
        } catch (e: Exception) {
            return@withContext AuthResult.NeedsManualCode(
                "Could not open local callback listener (${e.message ?: "port unavailable"}). Paste the code from your browser instead.",
            )
        }
        activeServerSocket = serverSocket

        val port = serverSocket.localPort
        val callbackUrl = "http://127.0.0.1:$port/callback"
        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("callback_url", callbackUrl)
            .appendQueryParameter("code_challenge", session.codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", session.state)
            .build()

        val launched = withContext(Dispatchers.Main) {
            launchBrowser(context, authUri)
        }
        if (!launched) {
            cancelActiveServer()
            return@withContext AuthResult.Error("No browser available to open OpenRouter sign-in.")
        }
        onBrowserLaunched()

        try {
            val client = serverSocket.accept()
            client.use { socket ->
                socket.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: ""
                val queryMap = parseHttpGetQuery(requestLine)

                val htmlBody = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8"/>
                        <meta name="viewport" content="width=device-width,initial-scale=1"/>
                        <title>GameNothing Connected</title>
                        <style>
                            body { background:#0c0f17; color:#f1f5f9; font-family:system-ui,-apple-system,sans-serif; display:flex; align-items:center; justify-content:center; height:100vh; margin:0; text-align:center; }
                            .card { background:#161b29; border:1px solid rgba(255,255,255,0.14); border-radius:18px; padding:28px 24px; max-width:360px; }
                            h2 { color:#34d399; margin-top:0; }
                            p { color:#94a3b8; line-height:1.5; }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h2>Connected to GameNothing!</h2>
                            <p>You can close this tab and return to GameNothing.</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                val bodyBytes = htmlBody.toByteArray(Charsets.UTF_8)
                val httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                val out = socket.getOutputStream()
                out.write(httpResponse.toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.flush()

                val returnedState = queryMap["state"]
                if (!returnedState.isNullOrBlank() && returnedState != session.state) {
                    return@withContext AuthResult.Error("OAuth state mismatch. Please try again.")
                }

                val code = queryMap["code"]?.trim().orEmpty()
                if (code.isEmpty()) {
                    return@withContext AuthResult.NeedsManualCode(
                        "No authorization code received on callback. If your browser showed a code, paste it below.",
                    )
                }

                exchangeCodeForApiKey(context, code, session.codeVerifier)
            }
        } catch (_: SocketTimeoutException) {
            AuthResult.NeedsManualCode("Browser callback timed out. You can paste the code or API key manually.")
        } catch (e: Exception) {
            AuthResult.NeedsManualCode("Callback interrupted (${e.message ?: "closed"}). You can paste the code or API key manually.")
        } finally {
            cancelActiveServer()
        }
    }

    /**
     * Exchanges a manually pasted OpenRouter authorization code (or directly saves an `sk-or-v1-...` API key).
     */
    suspend fun exchangeManualCodeOrKey(
        context: Context,
        codeOrKey: String,
    ): AuthResult = withContext(Dispatchers.IO) {
        val trimmed = codeOrKey.trim()
        if (trimmed.isEmpty()) {
            return@withContext AuthResult.Error("Please enter an authorization code or OpenRouter API key.")
        }

        // If the user pasted a direct OpenRouter API key (`sk-or-...`), store it directly
        if (trimmed.startsWith("sk-or-", ignoreCase = true)) {
            AiKeyStore.saveOpenRouterKey(context, trimmed)
            return@withContext AuthResult.Connected
        }

        // If the user pasted a full callback URL containing `?code=...`, extract the code
        val extractedCode = if (trimmed.contains("code=")) {
            runCatching {
                Uri.parse(trimmed).getQueryParameter("code")
            }.getOrNull() ?: trimmed.substringAfter("code=").substringBefore("&")
        } else {
            trimmed
        }

        val session = activeSession
        exchangeCodeForApiKey(context, extractedCode.trim(), session?.codeVerifier)
    }

    fun cancelActiveServer() {
        runCatching { activeServerSocket?.close() }
        activeServerSocket = null
    }

    private fun exchangeCodeForApiKey(
        context: Context,
        code: String,
        codeVerifier: String?,
    ): AuthResult {
        return try {
            val payload = JSONObject().apply {
                put("code", code)
                if (!codeVerifier.isNullOrBlank()) {
                    put("code_verifier", codeVerifier)
                    put("code_challenge_method", "S256")
                }
            }
            val request = Request.Builder()
                .url(TOKEN_EXCHANGE_URL)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://gamenothing.app")
                .header("X-Title", "GameNothing")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return AuthResult.Error("OpenRouter key exchange failed (HTTP ${response.code}).")
                }
                val json = JSONObject(bodyText)
                val apiKey = json.optString("key").trim()
                if (apiKey.isEmpty()) {
                    return AuthResult.Error("OpenRouter response did not include an API key.")
                }
                AiKeyStore.saveOpenRouterKey(context, apiKey)
                AuthResult.Connected
            }
        } catch (e: Exception) {
            AuthResult.Error("Network error exchanging OpenRouter code: ${e.message ?: "unknown error"}")
        }
    }

    private fun launchBrowser(context: Context, uri: Uri): Boolean {
        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun parseHttpGetQuery(requestLine: String): Map<String, String> {
        // Example: "GET /callback?code=abc&state=xyz HTTP/1.1"
        val parts = requestLine.split(' ')
        if (parts.size < 2) return emptyMap()
        val pathAndQuery = parts[1]
        val query = pathAndQuery.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[k] = v
            }
        }
        return result
    }

    private fun base64UrlNoPad(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }
}
