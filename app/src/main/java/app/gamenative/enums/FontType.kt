package app.gamenative.enums

import androidx.annotation.StringRes
import app.gamenative.R

enum class FontType(val key: String, @StringRes val labelResId: Int) {
    NDOT("ndot", R.string.font_type_ndot),
    NORMAL("normal", R.string.font_type_normal);

    companion object {
        fun fromKey(key: String): FontType = entries.find { it.key == key } ?: NDOT
    }
}
