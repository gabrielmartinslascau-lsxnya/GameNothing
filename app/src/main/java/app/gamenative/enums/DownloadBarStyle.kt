package app.gamenative.enums

import androidx.annotation.StringRes
import app.gamenative.R

enum class DownloadBarStyle(val key: String, @StringRes val labelResId: Int) {
    NOTHING("nothing", R.string.download_bar_style_nothing),
    NORMAL("normal", R.string.download_bar_style_normal);

    companion object {
        fun fromKey(key: String): DownloadBarStyle = entries.find { it.key == key } ?: NOTHING
    }
}
