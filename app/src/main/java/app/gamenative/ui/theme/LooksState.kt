package app.gamenative.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

@Immutable
data class LooksState(
    val isJigglyAnimations: Boolean = true,
    val isGlassEnabled: Boolean = false,
    val isTrueGlassEnabled: Boolean = false,
    val glassTransparencyOrBlur: Float = 0.3f, // Shared normalized value 0-1
    val glassColor: Color = Color.White,
    val fontType: FontFamily = NdotFontFamily,
    val mainColor: Color = PluviaPurple,
    val wallpaperColor: Color = Color.Black
)

val LocalLooksState = staticCompositionLocalOf { LooksState() }
