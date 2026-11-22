package app.gamenative.data

import android.content.Context
import app.gamenative.NetworkMonitor
import app.gamenative.data.gog.GogRecCard
import app.gamenative.data.gog.GogRecommendationsRepository
import app.gamenative.PrefManager
import app.gamenative.utils.Net
import java.io.File
import java.time.Instant
import kotlin.random.Random
import okhttp3.Request
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Server-driven sponsor card rendered over the booting splash. */
@Serializable
data class BootAdItem(
    val campaignId: String,
    val template: String = "cta_card",
    val imageUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val videoUrl: String = "",
    // Advertiser-supplied locale -> localized copy; resolved on-device.
    val title: Map<String, String> = emptyMap(),
    val body: Map<String, String> = emptyMap(),
    val action: FeaturedAction? = null,
    val appId: Int? = null,
    val maxShowsPerDay: Int = 5,
    // Fraction of eligible boots that show the card; the rest get the plain splash.
    val showRate: Double = 1.0,
    // Rotation share among the cached boot campaigns.
    val weight: Double = 1.0,
    // False for the house recommendation card: no Sponsored badge, no ad bookkeeping.
    val sponsored: Boolean = true,
    // House recommendation card only: store price shown in the buy button.
    val priceLabel: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    // template "quiz_card": one is picked at random per boot.
    val questions: List<BootQuizQuestion> = emptyList(),
    // Build flavors this campaign targets ("modern", "legacy"); empty = every build.
    val flavors: List<String> = emptyList(),
)

@Serializable
data class BootQuizQuestion(
    // Advertiser-supplied locale -> localized copy; resolved on-device.
    val prompt: Map<String, String> = emptyMap(),
    // Optional monospace block (code snippets); rendered verbatim, not localized.
    val code: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val timerSeconds: Int = 7,
    val winBody: Map<String, String> = emptyMap(),
    val loseBody: Map<String, String> = emptyMap(),
)

fun BootQuizQuestion.localizedPrompt(context: Context): String = prompt.forLocale(context) ?: ""

fun BootQuizQuestion.localizedWinBody(context: Context): String = winBody.forLocale(context) ?: ""

fun BootQuizQuestion.localizedLoseBody(context: Context): String = loseBody.forLocale(context) ?: ""

fun BootQuizQuestion.isPlayable(): Boolean =
    options.size in 2..4 && correctIndex in options.indices && (prompt.isNotEmpty() || code.isNotEmpty())

fun BootAdItem.localizedTitle(context: Context): String = title.forLocale(context) ?: ""

fun BootAdItem.localizedBody(context: Context): String = body.forLocale(context) ?: ""

object BootAdRepository {

    const val TEMPLATE_CTA_CARD = "cta_card"
    const val TEMPLATE_VIDEO_CARD = "video_card"
    const val TEMPLATE_QUIZ_CARD = "quiz_card"

    fun store(ads: List<BootAdItem>) {}

    fun cachedAds(): List<BootAdItem> = emptyList()

    fun getActiveAd(): BootAdItem? = null

    fun pickBootCard(): BootAdItem? = null

    fun recommendationCard(): BootAdItem? = null

    suspend fun resolveHouseTrailer(ad: BootAdItem): BootAdItem? = null

    fun noteShown(campaignId: String) {}

    fun cachedVideoFile(context: Context, ad: BootAdItem): File? = null

    fun prefetchVideos(context: Context, ads: List<BootAdItem>) {}

    fun recordShown(campaignId: String) {}
}
