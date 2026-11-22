package app.gamenative.service.xbox

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.utils.StorageUtils
import timber.log.Timber
import java.io.File
import java.nio.file.Paths

/** Constants and storage path helpers for Xbox / PC Game Pass integration. */
object XboxConstants {

    const val XBOX_PACK_REPO = "Droid-Deck/DroidDeck-Components"
    const val XBOX_PACK_NAME = "xbox"
    const val XODUS_ROOTFS_DIR = "/usr/local/lib/droiddeck-xbox"

    val REQUIRED_WINDOWS_DLLS = listOf(
        "xgameruntime.dll",
        "winhttp.dll",
        "windows.web.dll",
        "twinapi.appcore.dll",
        "wintypes.dll",
        "libnghttp2.dll",
    )

    val WINRT_ACTIVATABLE_CLASSES: Map<String, List<String>> = mapOf(
        "twinapi.appcore.dll" to listOf(
            "Windows.ApplicationModel.DataTransfer.DataTransferManager",
            "Windows.Security.ExchangeActiveSyncProvisioning.EasClientDeviceInformation",
            "Windows.System.Profile.AnalyticsInfo",
            "Windows.System.UserProfile.AdvertisingManager",
            "Windows.UI.ViewManagement.ApplicationView",
        ),
        "windows.web.dll" to listOf(
            "Windows.Data.Json.JsonArray",
            "Windows.Data.Json.JsonObject",
            "Windows.Data.Json.JsonValue",
        ),
        "wintypes.dll" to listOf(
            "Windows.Foundation.PropertyValue",
            "Windows.Foundation.Collections.PropertySet",
            "Windows.Foundation.Metadata.ApiInformation",
            "Windows.Storage.Streams.Buffer",
            "Windows.Storage.Streams.DataWriter",
        ),
    )

    fun internalXboxGamesPath(context: Context): String {
        val path = Paths.get(context.dataDir.path, "xbox_games").toString()
        File(path).mkdirs()
        return path
    }

    fun externalXboxGamesPath(context: Context? = null): String {
        val ctx = context ?: PluviaApp.instance
        val root = StorageUtils.resolveStoragePath(ctx, PrefManager.externalStoragePath) ?: PrefManager.externalStoragePath
        val path = Paths.get(root, "Xbox", "games").toString()
        File(path).mkdirs()
        return path
    }

    fun defaultXboxGamesPath(context: Context): String {
        val root = StorageUtils.resolveStoragePath(context, PrefManager.externalStoragePath) ?: PrefManager.externalStoragePath
        return if (PrefManager.useExternalStorage && File(root).exists()) {
            val path = externalXboxGamesPath(context)
            Timber.i("Xbox using external storage: $path")
            path
        } else {
            val path = internalXboxGamesPath(context)
            Timber.i("Xbox using internal storage: $path")
            path
        }
    }

    /** Return the dedicated install subfolder for a specific Xbox game title. */
    fun getGameInstallPath(context: Context, gameTitle: String, productId: String = ""): String {
        val sanitized = gameTitle.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
        val dirName = sanitized.ifEmpty {
            if (productId.isNotEmpty()) "xbox_$productId" else "game_${gameTitle.hashCode().toUInt()}"
        }
        return Paths.get(defaultXboxGamesPath(context), dirName).toString()
    }
}
