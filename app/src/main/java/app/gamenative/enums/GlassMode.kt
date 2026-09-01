package app.gamenative.enums

import androidx.annotation.StringRes
import app.gamenative.R

enum class GlassMode(val key: Int, @StringRes val labelResId: Int) {
    STANDARD(0, R.string.glass_mode_standard),
    TRUE(1, R.string.glass_mode_true),
    LIQUID(2, R.string.glass_mode_liquid);

    companion object {
        fun fromKey(key: Int): GlassMode = entries.find { it.key == key } ?: STANDARD
    }
}
