package app.gamenative.playground

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object FlathubApi {
    private const val BASE = "https://flathub.org/api/v2"
    private const val ARCH = "aarch64"
    private const val TIMEOUT_MS = 20_000

    class AppSummary(
        val id: String,
        val name: String,
        val summary: String,
        val icon: String?,
        val developer: String?,
        val verified: Boolean,
        val installs: Int,
    )

    class Screenshot(val thumb: String, val full: String)

    class AppDetails(
        val id: String,
        val name: String,
        val summary: String,
        val description: String,
        val developer: String?,
        val icon: String?,
        val version: String?,
        val license: String?,
        val homepage: String?,
        val screenshots: List<Screenshot>,
        val categories: List<String>,
        val runtime: String?,
        val downloadSize: Long,
        val installedSize: Long,
        val arches: List<String>,
    )

    class Category(val id: String, val label: String)

    val categories = listOf(
        Category("Game", "Games"),
        Category("AudioVideo", "Audio & Video"),
        Category("Graphics", "Graphics"),
        Category("Network", "Internet"),
        Category("Office", "Office"),
        Category("Development", "Development"),
        Category("Education", "Education"),
        Category("Science", "Science"),
        Category("System", "System"),
        Category("Utility", "Utilities"),
    )

    fun collection(name: String, count: Int = 30): List<AppSummary>? {
        val body = get("$BASE/collection/$name?page=1&per_page=${count * 2}") ?: return null
        return hits(body)?.take(count)
    }

    fun search(query: String, category: String? = null, count: Int = 48): List<AppSummary>? {
        val filters = JSONArray().put(JSONObject().put("filterType", "arches").put("value", ARCH))
        if (category != null) filters.put(JSONObject().put("filterType", "main_categories").put("value", category))
        val payload = JSONObject().put("query", query).put("filters", filters)
            .put("hits_per_page", count).put("page", 1)
        val body = post("$BASE/search", payload.toString()) ?: return null
        return hits(body)
    }

    fun iconUrl(id: String) = "https://dl.flathub.org/media/icons/128x128/$id.png"

    fun details(id: String): AppDetails? {
        val app = get("$BASE/appstream/${enc(id)}")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val summary = get("$BASE/summary/${enc(id)}?arch=$ARCH")?.let { runCatching { JSONObject(it) }.getOrNull() }
        val shots = app.optJSONArray("screenshots")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val sizes = arr.optJSONObject(i)?.optJSONArray("sizes") ?: return@mapNotNull null
                val all = (0 until sizes.length()).mapNotNull { sizes.optJSONObject(it) }
                    .map { (it.optString("width").toIntOrNull() ?: 0) to it.optString("src") }
                    .filter { it.second.startsWith("http") }.sortedBy { it.first }
                if (all.isEmpty()) null
                else Screenshot(
                    all.firstOrNull { it.first >= 600 }?.second ?: all.last().second,
                    all.firstOrNull { it.first >= 1200 }?.second ?: all.last().second
                )
            }
        } ?: emptyList()
        val release = app.optJSONArray("releases")?.optJSONObject(0)
        val cats = app.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        val arches = summary?.optJSONArray("arches")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return AppDetails(
            id = app.optString("id", id),
            name = app.optString("name", id),
            summary = app.optString("summary"),
            description = plainText(app.optString("description")),
            developer = app.optString("developer_name").takeIf { it.isNotEmpty() && it != "null" },
            icon = app.optString("icon").takeIf { it.startsWith("http") },
            version = release?.optString("version"),
            license = app.optString("project_license"),
            homepage = app.optJSONArray("urls")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                    .firstOrNull { it.optString("type") == "homepage" }?.optString("url")
            },
            screenshots = shots,
            categories = cats,
            runtime = summary?.optString("runtime"),
            downloadSize = summary?.optLong("download_size", 0L) ?: 0L,
            installedSize = summary?.optLong("installed_size", 0L) ?: 0L,
            arches = arches,
        )
    }

    private fun hits(json: String): List<AppSummary>? = runCatching {
        val root = JSONObject(json)
        val hits = root.optJSONArray("hits") ?: return null
        (0 until hits.length()).mapNotNull { i ->
            val h = hits.optJSONObject(i) ?: return@mapNotNull null
            AppSummary(
                id = h.getString("app_id"),
                name = h.optString("name").ifEmpty { h.getString("app_id") },
                summary = h.optString("summary"),
                icon = h.optString("icon").takeIf { it.startsWith("http") },
                developer = h.optString("developer_name").takeIf { it.isNotEmpty() && it != "null" },
                verified = h.optBoolean("verification_verified"),
                installs = h.optInt("installs_last_month", 0),
            )
        }
    }.onFailure { Timber.w(it, "FlathubApi: parse hits failed") }.getOrNull()

    private fun plainText(html: String): String = html
        .replace(Regex("<li[^>]*>"), "• ")
        .replace(Regex("</li>"), "\n")
        .replace(Regex("<p[^>]*>"), "")
        .replace(Regex("</p>"), "\n\n")
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .trim()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.setRequestProperty("User-Agent", "GameNothing-Playground")
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    }.onFailure { Timber.w(it, "FlathubApi: GET $url failed") }.getOrNull()

    private fun post(url: String, body: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "GameNothing-Playground")
        c.outputStream.use { it.write(body.toByteArray()) }
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    }.onFailure { Timber.w(it, "FlathubApi: POST $url failed") }.getOrNull()
}
