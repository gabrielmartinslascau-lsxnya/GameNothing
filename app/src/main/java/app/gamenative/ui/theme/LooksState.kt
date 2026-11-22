package app.gamenative.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.gamenative.enums.GlassMode

@Immutable
data class LooksState(
    val isJigglyAnimations: Boolean = true,
    val isGlassEnabled: Boolean = true,
    val glassMode: GlassMode = GlassMode.STANDARD,
    val glassTransparency: Float = 0.3f,
    val blurIntensiveness: Float = 16f,
    val brokenGlassRefraction: Float = 0.5f,
    val brokenGlassDispersion: Float = 0.6f,
    val brokenGlassThickness: Float = 58f,
    val brokenGlassLuminance: Float = 0.5f,
    val liquidDistortion: Float = 0.5f,
    val glassColor: Color = Color.White,
    val fontType: FontFamily = NdotFontFamily,
    val mainColor: Color = PluviaPurple,
    val wallpaperColor: Color = Color.Black
) {
    val jigglyAnimations: Boolean get() = isJigglyAnimations
}

/**
 * Snapshot-backed live glass tuning parameters read exclusively inside draw/effect scopes.
 * Mutating these fields during slider drags updates RuntimeShader uniforms at 120 FPS
 * with zero Compose recompositions and zero DataStore disk I/O.
 */
@Stable
class LiveGlassParameters(
    glassTransparency: Float = 0.3f,
    blurIntensiveness: Float = 16f,
    brokenGlassRefraction: Float = 0.5f,
    brokenGlassDispersion: Float = 0.6f,
    brokenGlassThickness: Float = 58f,
    brokenGlassLuminance: Float = 0.5f,
    liquidDistortion: Float = 0.5f,
    videoBackgroundDimAlpha: Float = 0.4f,
) {
    var glassTransparency by mutableFloatStateOf(glassTransparency)
    var blurIntensiveness by mutableFloatStateOf(blurIntensiveness)
    var brokenGlassRefraction by mutableFloatStateOf(brokenGlassRefraction)
    var brokenGlassDispersion by mutableFloatStateOf(brokenGlassDispersion)
    var brokenGlassThickness by mutableFloatStateOf(brokenGlassThickness)
    var brokenGlassLuminance by mutableFloatStateOf(brokenGlassLuminance)
    var liquidDistortion by mutableFloatStateOf(liquidDistortion)
    var videoBackgroundDimAlpha by mutableFloatStateOf(videoBackgroundDimAlpha)
}

val LocalLooksState = staticCompositionLocalOf { LooksState() }
val LocalLiveGlassParameters = staticCompositionLocalOf { LiveGlassParameters() }
