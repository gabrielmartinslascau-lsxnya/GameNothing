package app.gamenative.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.gamenative.enums.GlassMode

/**
 * AGSL Shader for True Liquid Glass Refraction.
 * Implements physical UV magnification (Spherical Bulge) for a true lens effect.
 */
private const val LENS_SHADER = """
    uniform shader uBackground;
    uniform float2 uElementSize;
    uniform float uDistortionStrength;
    uniform float uCornerRadius;

    float sdRoundedRect(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 fragCoord) {
        float2 localCoord = fragCoord;
        float2 b = uElementSize * 0.5;

        // local centered coordinate
        float2 p = localCoord - b;
        float dist = length(p);

        // Liquid Warp: content is magnified from the center of the panel
        // uDistortionStrength (0.0 to 1.0) - High multiplier for visible effect
        float strength = uDistortionStrength * 1.2;

        // Magnification factor: pulls inner pixels out toward the edges
        float factor = 1.0 / (1.0 + strength * (1.0 - smoothstep(0.0, length(b), dist)));

        float2 refractedCoord = b + p * factor;

        // Sample the background using the corrected local coordinate
        return uBackground.eval(refractedCoord);
    }
"""

@Composable
fun PluviaGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp),
    isScrolling: Boolean = false,
    distortionStrength: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val looks = LocalLooksState.current
    val backgroundLayer = LocalBackgroundLayer.current

    if (!looks.isGlassEnabled) {
        Box(
            modifier = modifier
                .background(looks.wallpaperColor, shape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center
        ) {
            Box(content = content)
        }
        return
    }

    // Liquid Glass is the sole, default renderer
    val liquidRadius = 48.dp
    val effectiveShape = RoundedCornerShape(liquidRadius)
    val currentCornerRadiusPx = 48f

    // Track position relative to root for perfect alignment
    var nodeOffset by remember { mutableStateOf(Offset.Zero) }
    var nodeSize by remember { mutableStateOf(Size.Zero) }

    Box(modifier = modifier.onGloballyPositioned { coordinates ->
        nodeOffset = coordinates.positionInRoot()
        nodeSize = coordinates.size.toSize()
    }, contentAlignment = Alignment.Center) {

        // 1. Ambient Drop Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 16.dp,
                    shape = effectiveShape,
                    clip = false,
                    ambientColor = looks.glassColor.copy(alpha = 0.3f),
                    spotColor = looks.glassColor.copy(alpha = 0.3f)
                )
        )

        // 2. Refraction / Backdrop Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.clip = true
                    this.shape = effectiveShape

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backgroundLayer != null && nodeSize.width > 0f) {
                        val liquidDistortionValue = distortionStrength ?: looks.liquidDistortion

                        val shader = RuntimeShader(LENS_SHADER).apply {
                            setFloatUniform("uElementSize", nodeSize.width, nodeSize.height)
                            setFloatUniform("uDistortionStrength", liquidDistortionValue)
                            setFloatUniform("uCornerRadius", currentCornerRadiusPx.dp.toPx())
                        }

                        val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "uBackground")
                        val liquidBlur = (12f + (looks.blurIntensiveness * 0.25f))
                        val blurEffect = RenderEffect.createBlurEffect(liquidBlur, liquidBlur, Shader.TileMode.CLAMP)

                        renderEffect = RenderEffect.createChainEffect(shaderEffect, blurEffect).asComposeRenderEffect()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect.createBlurEffect(
                            looks.blurIntensiveness,
                            looks.blurIntensiveness,
                            Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .background(looks.glassColor.copy(alpha = 0.08f))
                .then(
                    if (backgroundLayer != null) {
                        Modifier.drawWithContent {
                            drawIntoCanvas { canvas ->
                                canvas.save()
                                canvas.translate(-nodeOffset.x, -nodeOffset.y)
                                drawLayer(backgroundLayer)
                                canvas.restore()
                            }
                        }
                    } else Modifier
                )
        )

        // 3. Content Layer
        Box(
            modifier = Modifier
                .graphicsLayer {
                    this.clip = true
                    this.shape = effectiveShape
                }
                .background(Color.Transparent)
                .drawWithContent {
                    drawContent()
                    // Liquid Rim Highlight
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent, looks.glassColor.copy(alpha = 0.3f)),
                            start = Offset.Zero, end = Offset(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(currentCornerRadiusPx.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
        ) {
            content()
        }
    }
}

/**
 * A modifier that applies a "Frosted Glass" look to the BACKGROUND only.
 */
@Composable
fun Modifier.pluviaGlass(
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp)
): Modifier {
    val looks = LocalLooksState.current
    if (!looks.isGlassEnabled) return this

    return this.background(
        brush = Brush.verticalGradient(
            colors = listOf(
                looks.glassColor.copy(alpha = 0.2f),
                looks.glassColor.copy(alpha = 0.1f)
            )
        ),
        shape = shape
    ).border(1.dp, looks.glassColor.copy(alpha = 0.3f), shape).graphicsLayer {
        clip = true
        this.shape = shape
    }
}

@Composable
fun glassContentColor(): Color = Color.White
