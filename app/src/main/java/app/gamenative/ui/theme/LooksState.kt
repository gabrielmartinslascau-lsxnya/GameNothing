package app.gamenative.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.gamenative.enums.GlassMode

@Immutable
data class LooksState(
    val isJigglyAnimations: Boolean = true,
    val isGlassEnabled: Boolean = false,
    val glassMode: GlassMode = GlassMode.STANDARD,
    val glassTransparency: Float = 0.3f,
    val blurIntensiveness: Float = 16f,
    val liquidDistortion: Float = 0.5f,
    val glassColor: Color = Color.White,
    val fontType: FontFamily = NdotFontFamily,
    val mainColor: Color = PluviaPurple,
    val wallpaperColor: Color = Color.Black
)

val LocalLooksState = staticCompositionLocalOf { LooksState() }
