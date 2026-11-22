package app.gamenative.service.xbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.gamenative.service.xbox.cloud.XboxCloudActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Discovers the user's paired Xbox consoles (Xbox Series X|S, Xbox One)
 * and generates 1-click remote play launch intents.
 */
object XboxConsoleDiscovery {

    data class Console(
        val serverId: String,
        val name: String,
        val consoleType: String,
        val powerState: String,
        val isOnline: Boolean,
        val ipAddress: String = ""
    )

    private const val TAG = "XboxConsoleDiscovery"

    suspend fun listConsoles(context: Context): List<Console> = withContext(Dispatchers.IO) {
        val account = StoreAccounts.read(context, XboxStoreId.XBOX) ?: return@withContext emptyList()
        val gamertag = account.optString("gamertag")
        val consoles = mutableListOf<Console>()

        // 1. Query SmartGlass configuration endpoint
        try {
            val sessionToken = account.optString("token")
            if (sessionToken.isNotEmpty()) {
                val url = URL("https://smartglass.xboxlive.com/user/consoles")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "XBL3.0 x=$sessionToken")
                conn.setRequestProperty("Accept", "application/json")

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val id = item.optString("id").ifEmpty { item.optString("serverId") }
                        val name = item.optString("name", "Xbox Console")
                        val type = item.optString("consoleType", "Xbox")
                        val power = item.optString("powerState", "ConnectedStandby")
                        val online = power.equals("On", ignoreCase = true) || power.equals("ConnectedStandby", ignoreCase = true)
                        if (id.isNotEmpty()) {
                            consoles.add(
                                Console(
                                    serverId = id,
                                    name = name,
                                    consoleType = type,
                                    powerState = power,
                                    isOnline = online
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d("SmartGlass endpoint unreachable or token expired: %s", e.message)
        }

        // 2. Fallback / Default registered console if none found via SmartGlass
        if (consoles.isEmpty() && gamertag.isNotEmpty()) {
            consoles.add(
                Console(
                    serverId = "home-console",
                    name = "$gamertag's Xbox",
                    consoleType = "Xbox Series X|S",
                    powerState = "Ready",
                    isOnline = true
                )
            )
        }

        consoles
    }

    /**
     * Launches the remote play session for the specified console.
     * Tries installed apps first (Official Xbox App / XBPlay) with zero delay.
     * Falls back to the dedicated full-screen Chromium player (XboxCloudActivity).
     */
    fun launchRemotePlay(context: Context, console: Console) {
        val pm = context.packageManager

        // Priority 1: XBPlay if installed (supports deep linking directly into stream)
        val xbPlayIntent = pm.getLaunchIntentForPackage("com.studio08.xbplay")
        if (xbPlayIntent != null) {
            xbPlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            xbPlayIntent.putExtra("server_id", console.serverId)
            context.startActivity(xbPlayIntent)
            return
        }

        // Priority 2: Official Xbox App
        val officialXboxIntent = pm.getLaunchIntentForPackage("com.microsoft.xboxone.smartglass")
        if (officialXboxIntent != null) {
            officialXboxIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(officialXboxIntent)
            return
        }

        // Priority 3: Full-screen WebRTC Remote Play Portal via XboxCloudActivity
        val remoteUrl = if (console.serverId == "home-console") {
            "https://www.xbox.com/play/consoles"
        } else {
            "https://www.xbox.com/play/consoles/${console.serverId}"
        }
        val intent = XboxCloudActivity.intent(context, remoteUrl).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
