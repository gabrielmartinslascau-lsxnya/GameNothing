package app.gamenative.service.xbox

import android.content.Context
import timber.log.Timber

internal object XboxGamertag {
    private const val TIMEOUT_SECONDS = 75L
    private val triedGeneration = java.util.concurrent.atomic.AtomicReference<String?>(null)

    internal fun valid(value: String): Boolean =
        value.isNotEmpty() && value.length <= 64 && value.none { it.isISOControl() }

    fun refresh(app: Context, credentialGeneration: String? = null): Boolean = try {
        val gamertag = Xodus.run(
            app,
            listOf("profile", "--json"),
            timeout = TIMEOUT_SECONDS,
            credentialGeneration = credentialGeneration,
        ).optString("gamertag").trim()
        require(valid(gamertag))
        XboxCredentials.lifecycle(app, credentialGeneration) {
            val record = StoreAccounts.read(app, XboxStoreId.XBOX) ?: error("Xbox credentials missing")
            record.put("gamertag", gamertag)
            StoreAccounts.write(app, XboxStoreId.XBOX, record)
        }
        Timber.tag("XboxGamertag").i("Xbox: gamertag read")
        true
    } catch (e: Exception) {
        Timber.tag("XboxGamertag").w("Xbox: gamertag unavailable (%s)", XboxLoginActivity.failure(e))
        false
    }

    fun ensure(app: Context) {
        Thread({
            runCatching {
                val generation = XboxCredentials.lifecycle(app) { it }
                if (!StoreAccounts.read(app, XboxStoreId.XBOX)?.optString("gamertag").isNullOrBlank()) return@runCatching
                if (!shouldTryRefresh(generation)) return@runCatching
                refresh(app, generation)
            }.onFailure {
                Timber.tag("XboxGamertag").w("Xbox: gamertag check failed (%s)", it.javaClass.simpleName)
            }
        }, "xbox-gamertag").start()
    }

    internal fun shouldTryRefresh(generation: String): Boolean {
        while (true) {
            val previous = triedGeneration.get()
            if (previous == generation) return false
            if (triedGeneration.compareAndSet(previous, generation)) return true
        }
    }
}
