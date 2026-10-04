package app.gamenative.utils

import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.data.gog.GogMapRepository
import app.gamenative.data.gog.SteamAppEnvelope
import app.gamenative.data.gog.SteamSearchResponse
import app.gamenative.ui.data.GameMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder

object GameMetadataService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMetadata(item: LibraryItem): GameMetadata? = withContext(Dispatchers.IO) {
        val steamId = resolveSteamId(item)
        if (steamId != null) {
            fetchFromSteam(steamId, item.appId)
        } else {
            // Try searching by name on Steam
            val searchedId = searchSteamAppId(item.name)
            if (searchedId != null) {
                fetchFromSteam(searchedId, item.appId)
            } else {
                null
            }
        }
    }

    private fun resolveSteamId(item: LibraryItem): String? {
        return when (item.gameSource) {
            GameSource.STEAM -> item.gameId.toString()
            GameSource.GOG -> GogMapRepository.steamAppIdForGog(item.gameId.toString())
            else -> null
        }
    }

    private fun fetchFromSteam(steamId: String, originalAppId: String): GameMetadata? {
        return try {
            val request = Request.Builder()
                .url("https://store.steampowered.com/api/appdetails?appids=$steamId&cc=us&l=english")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            Net.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.removePrefix("\uFEFF") ?: return null
                val dataMap = json.decodeFromString<Map<String, SteamAppEnvelope>>(body)
                val appData = dataMap[steamId]?.takeIf { it.success }?.data ?: return null

                val description = appData.shortDescription.ifBlank { appData.aboutTheGame }
                val movie = appData.movies.firstOrNull()
                val trailerUrl = movie?.hls?.takeIf { it.isNotBlank() }
                    ?: movie?.mp4?.max?.takeIf { it.isNotBlank() }
                    ?: movie?.webm?.max?.takeIf { it.isNotBlank() }

                GameMetadata(
                    appId = originalAppId,
                    name = appData.name,
                    description = stripHtml(description),
                    trailerUrl = trailerUrl,
                    developer = appData.developers.firstOrNull() ?: "",
                    releaseDate = appData.releaseDate?.date ?: ""
                )
            }
        } catch (e: Exception) {
            Timber.tag("GameMetadata").w(e, "Steam fetch failed for $steamId")
            null
        }
    }

    private fun searchSteamAppId(title: String): String? {
        return try {
            val term = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://store.steampowered.com/api/storesearch/?term=$term&cc=us&l=english")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            Net.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.removePrefix("\uFEFF") ?: return null
                val items = json.decodeFromString<SteamSearchResponse>(body).items
                // Best effort: first match.
                items.firstOrNull()?.id?.toString()
            }
        } catch (e: Exception) {
            Timber.tag("GameMetadata").w(e, "Steam search failed for $title")
            null
        }
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
