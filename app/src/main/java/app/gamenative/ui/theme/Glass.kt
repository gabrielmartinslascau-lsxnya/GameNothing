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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
 * Simplified as the logic is now primarily handled by PluviaGlassPanel for complex layering.
 */
@Composable
fun Modifier.pluviaGlass(
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp)
): Modifier {
    val looks = LocalLooksState.current
    if (!looks.isGlassEnabled && !looks.isTrueGlassEnabled) return this

    return this.background(
        brush = Brush.verticalGradient(
            colors = listOf(
                looks.glassColor.copy(alpha = 0.2f),
                looks.glassColor.copy(alpha = 0.1f)
            )
        ),
        shape = shape
    ).border(1.dp, looks.glassColor.copy(alpha = 0.3f), shape).clip(shape)
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
        Box(
            modifier = modifier
                .background(looks.wallpaperColor, shape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .clip(shape),
            contentAlignment = Alignment.Center
        ) {
            Box(content = content)
        }
        return
    }

    Box(modifier = modifier) {
        // 1. Shadow Layer (Bottom)
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 12.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
        )

        // 2. Blur & Tint Layer (Middle)
        if (looks.isTrueGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = 5f + (looks.glassTransparencyOrBlur * 45f)
            val effect = remember(radius) {
                try {
                    RenderEffect.createBlurEffect(
                        radius, radius, Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                } catch (_: Exception) {
                    null
                }
            }
            if (effect != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = effect
                            // OPTIMIZATION: Use Auto strategy to let system decide.
                            // This is much faster during controller navigation.
                            compositingStrategy = CompositingStrategy.Auto
                            this.clip = true
                            this.shape = shape
                        }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    looks.glassColor.copy(alpha = 0.25f),
                                    looks.glassColor.copy(alpha = 0.12f)
                                )
                            )
                        )
                )
            }
        } else {
            val alpha = 0.05f + (looks.glassTransparencyOrBlur * 0.75f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                looks.glassColor.copy(alpha = alpha),
                                looks.glassColor.copy(alpha = (alpha * 0.8f).coerceIn(0f, 1f))
                            )
                        ),
                        shape = shape
                    )
            )
        }

        // 3. Sharp Border Layer (Top of background, below content)
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = if (looks.isTrueGlassEnabled) 1.5.dp else 1.dp,
                    color = looks.glassColor.copy(alpha = if (looks.isTrueGlassEnabled) 0.4f else 0.2f),
                    shape = shape
                )
        )

        // 4. Foreground Content (Always sharp)
        Box(
            modifier = Modifier.align(Alignment.Center),
            content = content
        )
    }
}

/**
 * Returns the appropriate content color for glass panels.
 */
@Composable
fun glassContentColor(): Color {
    return Color.White
}

