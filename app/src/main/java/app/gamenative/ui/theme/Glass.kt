package app.gamenative.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager


/**
 * A modifier that applies a "Frosted Glass" look to the BACKGROUND only.
 */
@Composable
fun Modifier.pluviaGlass(
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp)
): Modifier {
    val looks = LocalLooksState.current
    if (!looks.isGlassEnabled && !looks.isTrueGlassEnabled) return this

    val modifierWithBackground = this
        .shadow(
            elevation = 12.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.15f),
            spotColor = Color.Black.copy(alpha = 0.15f)
        )

    return when {
        // Condition C: True Glass ON
        looks.isTrueGlassEnabled -> {
            val radius = 5f + (looks.glassTransparencyOrBlur * 45f)
            modifierWithBackground.graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = RenderEffect.createBlurEffect(
                        radius, radius, Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .background(looks.glassColor.copy(alpha = 0.15f), shape)
            .border(1.5.dp, looks.glassColor.copy(alpha = 0.4f), shape)
            .clip(shape)
        }
        // Condition B: Glass ON, True Glass OFF
        looks.isGlassEnabled -> {
            val alpha = 0.05f + (looks.glassTransparencyOrBlur * 0.75f)
            modifierWithBackground.background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        looks.glassColor.copy(alpha = alpha),
                        looks.glassColor.copy(alpha = (alpha * 0.8f).coerceIn(0f, 1f))
                    )
                ),
                shape = shape
            )
            .border(1.dp, looks.glassColor.copy(alpha = (alpha + 0.1f).coerceIn(0f, 1f)), shape)
            .clip(shape)
        }
        else -> this
    }
}

/**
 * A wrapper composable that implements the layered glass look (background blur + sharp content).
 */
@Composable
fun PluviaGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp),
    isTrueGlass: Boolean = false, // Parameters kept for compatibility but logic now uses LocalLooksState
    content: @Composable BoxScope.() -> Unit
) {
    val looks = LocalLooksState.current

    if (!looks.isGlassEnabled && !looks.isTrueGlassEnabled) {
        // Condition A: Glass OFF
        Surface(
            modifier = modifier,
            shape = shape,
            color = looks.wallpaperColor,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            content = { Box(content = content) }
        )
        return
    }

    Box(modifier = modifier) {
        // 1. Background Layer (This gets the glass tint/border)
        Box(
            modifier = Modifier
                .matchParentSize()
                .pluviaGlass(cornerRadius = cornerRadius, shape = shape)
        )

        // 2. Foreground Content (Stays 100% sharp)
        Box(
            modifier = Modifier.clip(shape),
            content = content
        )
    }
}

/**
 * Returns the appropriate content color for glass panels.
 */
@Composable
fun glassContentColor(enabled: Boolean = PrefManager.glassPanels): Color {
    return Color.White
}
