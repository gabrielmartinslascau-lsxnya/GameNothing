package app.gamenative.enums

import androidx.annotation.StringRes
import app.gamenative.R

enum class GlassMode(val key: Int, @StringRes val labelResId: Int) {
    STANDARD(0, R.string.glass_mode_standard),
    LIQUID(1, R.string.glass_mode_liquid);

    companion object {
        fun fromKey(key: Int): GlassMode = when (key) {
            1, 2 -> LIQUID
            else -> STANDARD
        }
    }
}
