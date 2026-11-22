package app.gamenative.aidebugger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pure Android Keystore (`AES/GCM/NoPadding`) storage for GameNothing 2.0 AI Debugger
 * API keys, provider choice, and model selections.
 *
 * Uses a dedicated Keystore alias (`gamenothing_ai_keystore_aes_gcm_v1`) and NEVER touches
 * `gamenative_nexus_oauth_aes_gcm_v1` or `androidx.security:security-crypto`.
 */
object AiKeyStore {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "gamenothing_ai_keystore_aes_gcm_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128

    private const val PREFS_NAME = "gamenothing_ai_debugger_prefs"
    private const val PREF_PROVIDER = "active_provider"
    private const val PREF_OPENROUTER_ENC_KEY = "openrouter_enc_key"
    private const val PREF_OPENROUTER_MODEL = "openrouter_model"
    private const val PREF_GEMINI_ENC_KEY = "gemini_enc_key"
    private const val PREF_GEMINI_MODEL = "gemini_model"
    private const val PREF_CUSTOM_ENC_KEY = "custom_enc_key"
    private const val PREF_CUSTOM_BASE_URL = "custom_base_url"
    private const val PREF_CUSTOM_MODEL = "custom_model"

    const val DEFAULT_OPENROUTER_MODEL = "openrouter/free"
    const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_CUSTOM_BASE_URL = "https://openrouter.ai/api/v1"
    const val DEFAULT_CUSTOM_MODEL = "openrouter/free"

    enum class AiProvider(val displayName: String) {
        OPENROUTER("OpenRouter"),
        GEMINI("Google Gemini"),
        CUSTOM("Custom Endpoint"),
        NONE("Zero-Login / Clipboard"),
    }

    data class ModelPreset(
        val id: String,
        val label: String,
        val badge: String,
        val description: String,
    )

    val OPENROUTER_MODEL_PRESETS: List<ModelPreset> = listOf(
        ModelPreset(
            id = "openrouter/free",
            label = "openrouter/free",
            badge = "FREE DEFAULT",
            description = "Auto-routes to the highest-uptime free reasoning model",
        ),
        ModelPreset(
            id = "google/gemini-3.5-flash-lite",
            label = "Gemini 3.5 Flash-Lite",
            badge = "FAST",
            description = "Google's ultra-fast reasoning model via OpenRouter",
        ),
        ModelPreset(
            id = "deepseek/deepseek-chat",
            label = "DeepSeek V3 / R1",
            badge = "DEEP REASONING",
            description = "Strong technical log & Wine/FEX debugging",
        ),
        ModelPreset(
            id = "anthropic/claude-sonnet-4",
            label = "Claude Sonnet 4",
            badge = "PREMIUM",
            description = "Anthropic's flagship coding & config analysis model",
        ),
        ModelPreset(
            id = "openai/gpt-4o-mini",
            label = "GPT-4o Mini",
            badge = "BALANCED",
            description = "OpenAI's fast structured JSON diagnosis model",
        ),
        ModelPreset(
            id = "moonshotai/kimi-k2",
            label = "Kimi K2",
            badge = "AGENTIC",
            description = "Moonshot AI's high-context technical model",
        ),
        ModelPreset(
            id = "perplexity/sonar",
            label = "Perplexity Sonar",
            badge = "SEARCH",
            description = "Grounded PC/Proton compatibility model",
        ),
    )

    val GEMINI_MODEL_PRESETS: List<ModelPreset> = listOf(
        ModelPreset(
            id = "gemini-3.5-flash-lite",
            label = "Gemini 3.5 Flash-Lite",
            badge = "FREE TIER DEFAULT",
            description = "Fastest free-tier Google AI Studio model",
        ),
        ModelPreset(
            id = "gemini-2.5-flash",
            label = "Gemini 2.5 Flash",
            badge = "BALANCED",
            description = "Google AI Studio general flash model",
        ),
        ModelPreset(
            id = "gemini-2.5-pro",
            label = "Gemini 2.5 Pro",
            badge = "PRO",
            description = "Deep multi-step reasoning model",
        ),
    )

    fun getActiveProvider(context: Context): AiProvider {
        val raw = prefs(context).getString(PREF_PROVIDER, null)
        if (raw != null) {
            runCatching { AiProvider.valueOf(raw) }.getOrNull()?.let { return it }
        }
        return when {
            !getOpenRouterKey(context).isNullOrBlank() -> AiProvider.OPENROUTER
            !getGeminiKey(context).isNullOrBlank() -> AiProvider.GEMINI
            !getCustomKey(context).isNullOrBlank() -> AiProvider.CUSTOM
            else -> AiProvider.NONE
        }
    }

    fun setActiveProvider(context: Context, provider: AiProvider) {
        prefs(context).edit().putString(PREF_PROVIDER, provider.name).apply()
    }

    fun hasActiveApiConnection(context: Context): Boolean {
        return when (getActiveProvider(context)) {
            AiProvider.OPENROUTER -> !getOpenRouterKey(context).isNullOrBlank()
            AiProvider.GEMINI -> !getGeminiKey(context).isNullOrBlank()
            AiProvider.CUSTOM -> !getCustomKey(context).isNullOrBlank() && getCustomBaseUrl(context).isNotBlank()
            AiProvider.NONE -> false
        }
    }

    fun getActiveModelSummary(context: Context): String {
        return when (val provider = getActiveProvider(context)) {
            AiProvider.OPENROUTER -> "OpenRouter · ${getOpenRouterModel(context)}"
            AiProvider.GEMINI -> "Google Gemini · ${getGeminiModel(context)}"
            AiProvider.CUSTOM -> "Custom · ${getCustomModel(context)}"
            AiProvider.NONE -> provider.displayName
        }
    }

    fun saveOpenRouterKey(context: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            prefs(context).edit().remove(PREF_OPENROUTER_ENC_KEY).apply()
            return
        }
        val encrypted = encrypt(trimmed) ?: return
        prefs(context).edit()
            .putString(PREF_OPENROUTER_ENC_KEY, encrypted)
            .putString(PREF_PROVIDER, AiProvider.OPENROUTER.name)
            .apply()
    }

    fun getOpenRouterKey(context: Context): String? {
        val enc = prefs(context).getString(PREF_OPENROUTER_ENC_KEY, null) ?: return null
        return decrypt(enc)
    }

    fun getOpenRouterModel(context: Context): String {
        return prefs(context).getString(PREF_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL)
            ?.trim()
            ?.ifEmpty { DEFAULT_OPENROUTER_MODEL }
            ?: DEFAULT_OPENROUTER_MODEL
    }

    fun setOpenRouterModel(context: Context, modelId: String) {
        prefs(context).edit()
            .putString(PREF_OPENROUTER_MODEL, modelId.trim().ifEmpty { DEFAULT_OPENROUTER_MODEL })
            .apply()
    }

    fun saveGeminiKey(context: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            prefs(context).edit().remove(PREF_GEMINI_ENC_KEY).apply()
            return
        }
        val encrypted = encrypt(trimmed) ?: return
        prefs(context).edit()
            .putString(PREF_GEMINI_ENC_KEY, encrypted)
            .putString(PREF_PROVIDER, AiProvider.GEMINI.name)
            .apply()
    }

    fun getGeminiKey(context: Context): String? {
        val enc = prefs(context).getString(PREF_GEMINI_ENC_KEY, null) ?: return null
        return decrypt(enc)
    }

    fun getGeminiModel(context: Context): String {
        return prefs(context).getString(PREF_GEMINI_MODEL, DEFAULT_GEMINI_MODEL)
            ?.trim()
            ?.ifEmpty { DEFAULT_GEMINI_MODEL }
            ?: DEFAULT_GEMINI_MODEL
    }

    fun setGeminiModel(context: Context, modelId: String) {
        prefs(context).edit()
            .putString(PREF_GEMINI_MODEL, modelId.trim().ifEmpty { DEFAULT_GEMINI_MODEL })
            .apply()
    }

    fun saveCustomEndpoint(context: Context, baseUrl: String, apiKey: String, modelId: String) {
        val trimmedKey = apiKey.trim()
        val editor = prefs(context).edit()
            .putString(PREF_CUSTOM_BASE_URL, baseUrl.trim().trimEnd('/').ifEmpty { DEFAULT_CUSTOM_BASE_URL })
            .putString(PREF_CUSTOM_MODEL, modelId.trim().ifEmpty { DEFAULT_CUSTOM_MODEL })
        if (trimmedKey.isNotEmpty()) {
            val enc = encrypt(trimmedKey)
            if (enc != null) {
                editor.putString(PREF_CUSTOM_ENC_KEY, enc)
            }
            editor.putString(PREF_PROVIDER, AiProvider.CUSTOM.name)
        }
        editor.apply()
    }

    fun getCustomKey(context: Context): String? {
        val enc = prefs(context).getString(PREF_CUSTOM_ENC_KEY, null) ?: return null
        return decrypt(enc)
    }

    fun getCustomBaseUrl(context: Context): String {
        return prefs(context).getString(PREF_CUSTOM_BASE_URL, DEFAULT_CUSTOM_BASE_URL)
            ?.trim()
            ?.trimEnd('/')
            ?.ifEmpty { DEFAULT_CUSTOM_BASE_URL }
            ?: DEFAULT_CUSTOM_BASE_URL
    }

    fun getCustomModel(context: Context): String {
        return prefs(context).getString(PREF_CUSTOM_MODEL, DEFAULT_CUSTOM_MODEL)
            ?.trim()
            ?.ifEmpty { DEFAULT_CUSTOM_MODEL }
            ?: DEFAULT_CUSTOM_MODEL
    }

    /**
     * 1-tap "Disconnect / Clear Key" that wipes all stored encrypted tokens and resets provider.
     */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
    }

    fun disconnectProvider(context: Context, provider: AiProvider) {
        val editor = prefs(context).edit()
        when (provider) {
            AiProvider.OPENROUTER -> editor.remove(PREF_OPENROUTER_ENC_KEY)
            AiProvider.GEMINI -> editor.remove(PREF_GEMINI_ENC_KEY)
            AiProvider.CUSTOM -> editor.remove(PREF_CUSTOM_ENC_KEY)
            AiProvider.NONE -> {}
        }
        if (getActiveProvider(context) == provider) {
            editor.putString(PREF_PROVIDER, AiProvider.NONE.name)
        }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun encrypt(plaintext: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            if (iv == null || iv.size != IV_SIZE_BYTES) return null
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            // Never log API keys or exception details containing plaintext
            null
        }
    }

    private fun decrypt(encoded: String): String? {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_SIZE_BYTES) return null
            val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }
}
