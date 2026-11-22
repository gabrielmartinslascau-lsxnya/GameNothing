package app.gamenative.service.xbox

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AES-256-GCM credential cipher for Xbox / Microsoft Store tokens.
 * Preserves StrongBox fallback to TEE (`5ca1f61`) so devices without StrongBox or with
 * broken StrongBox implementations transparently fall back to Android Keystore TEE.
 */
object CredentialCipher {
    const val VERSION = 1
    const val VERSION_TEE = 2
    const val ALG = "AES/GCM"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun interface KeyProvider {
        fun key(): SecretKey

        fun existing(): SecretKey = key()

        fun legacy(): SecretKey = existing()

        val version: Int get() = VERSION
    }

    class KeyMissing : java.security.GeneralSecurityException("no key")

    fun isPermanent(e: Throwable): Boolean =
        e is javax.crypto.AEADBadTagException ||
            e is android.security.keystore.KeyPermanentlyInvalidatedException ||
            e is KeyMissing ||
            e is org.json.JSONException ||
            e is IllegalArgumentException

    object Keystore : KeyProvider {
        private const val LEGACY_ALIAS = "droiddeck-store-credentials"
        private const val ALIAS = "droiddeck-store-credentials-tee"
        private const val PROVIDER = "AndroidKeyStore"

        override val version: Int get() = VERSION_TEE

        @Synchronized
        override fun existing(): SecretKey = stored(ALIAS) ?: throw KeyMissing()

        @Synchronized
        override fun legacy(): SecretKey = stored(LEGACY_ALIAS) ?: throw KeyMissing()

        @Synchronized
        override fun key(): SecretKey = stored(ALIAS) ?: generate()

        private fun stored(alias: String): SecretKey? =
            runCatching {
                KeyStore.getInstance(PROVIDER).apply { load(null) }.getKey(alias, null) as? SecretKey
            }.getOrNull()

        /**
         * Preserves commit `5ca1f61`: try StrongBox first when supported, and cleanly fall back to
         * standard TEE (`setIsStrongBoxBacked(false)`) if StrongBox throws `StrongBoxUnavailableException`
         * or any `ProviderException` during key generation or self-test.
         */
        private fun generate(): SecretKey {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    val strongBoxSpec = KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(false)
                        .setIsStrongBoxBacked(true)
                        .build()
                    val k = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
                        init(strongBoxSpec)
                        generateKey()
                    }
                    // Verify StrongBox key actually works before committing to it
                    val testCipher = Cipher.getInstance(TRANSFORMATION)
                    testCipher.init(Cipher.ENCRYPT_MODE, k)
                    testCipher.doFinal("test".toByteArray(Charsets.UTF_8))
                    return k
                }.onFailure { e ->
                    Timber.tag("CredentialCipher").w(e, "StrongBox key generation failed; falling back to TEE")
                    runCatching {
                        KeyStore.getInstance(PROVIDER).apply {
                            load(null)
                            deleteEntry(ALIAS)
                        }
                    }
                }
            }
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
                init(spec)
                generateKey()
            }
        }
    }

    fun seal(plain: String, keys: KeyProvider): JSONObject {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keys.key()) }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("v", keys.version)
            .put("alg", ALG)
            .put("iv", b64(cipher.iv))
            .put("ct", b64(ct))
    }

    fun open(envelope: JSONObject, keys: KeyProvider): String {
        val version = envelope.optInt("v", -1)
        require((version == VERSION || version == VERSION_TEE) && envelope.optString("alg") == ALG) { "unknown envelope" }
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ct = Base64.decode(envelope.getString("ct"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                if (version == VERSION) keys.legacy() else keys.existing(),
                GCMParameterSpec(TAG_BITS, iv),
            )
        }
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    fun isEnvelope(json: JSONObject): Boolean = json.has("v") && json.has("ct") && json.has("iv")

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
