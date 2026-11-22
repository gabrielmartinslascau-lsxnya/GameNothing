package app.gamenative.enums

import androidx.annotation.StringRes
import app.gamenative.R

enum class GlassMode(val key: Int, @StringRes val labelResId: Int) {
    STANDARD(0, R.string.glass_mode_standard),
    BROKEN_GLASS(1, R.string.glass_mode_broken_glass);

    companion object {
        fun fromKey(key: Int): GlassMode = when (key) {
            1, 2 -> BROKEN_GLASS
            else -> STANDARD
        }
    }
}
