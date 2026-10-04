package app.gamenative.utils

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import timber.log.Timber

class AndroidAppIconFetcher(
    private val data: String,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val packageName = data.removePrefix("android-app://")
        return try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            DrawableResult(
                drawable = icon,
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            Timber.tag("AppIconFetcher").e(e, "Failed to fetch icon for $packageName")
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.startsWith("android-app://")) return null
            return AndroidAppIconFetcher(data, context)
        }
    }
}
