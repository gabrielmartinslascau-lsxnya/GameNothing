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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.gamenative.enums.GlassMode

/**
 * AGSL Shader for True Liquid Glass Refraction.
 * Uses a pixel-perfect Rounded Rectangle SDF and magnification math for physical accuracy.
 */
private const val LENS_SHADER = """
    uniform shader uBackground;
    uniform float2 uElementSize;
    uniform float2 uElementOffset;
    uniform float uDistortionStrength;
    uniform float uCornerRadius;

    float sdRoundedRect(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 fragCoord) {
        float2 localCoord = fragCoord;
        float2 b = uElementSize * 0.5;

        // Local centered coordinate
        float2 p = localCoord - b;
        float d = sdRoundedRect(p, b, uCornerRadius);

        float2 refractedCoord = localCoord;

        if (d < 0.0) {
            // Optical Warp: physical bulge (Magnification)
            //content is pushed outward from the center
            float edgeDist = abs(d);
            // Mask is strongest at the center area
            float mask = smoothstep(0.0, length(b), length(p));

            // Factor < 1.0 = Magnification
            float factor = 1.0 / (1.0 + (1.0 - mask) * uDistortionStrength * 0.12);
            refractedCoord = b + p * factor;
        }

        return uBackground.eval(refractedCoord);
    }
"""

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
    ).border(1.dp, looks.glassColor.copy(alpha = 0.3f), shape).clip(shape)
}

/**
 * A wrapper composable that implements the unified liquid glass stack.
 */
@Composable
fun PluviaGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp),
    isTrueGlass: Boolean = false, // Parameters kept for compatibility
    forceStandard: Boolean = false, // Bypasses complex shaders in-game
    content: @Composable BoxScope.() -> Unit
) {
    val looks = LocalLooksState.current
    val backgroundLayer = LocalBackgroundLayer.current

    if (!looks.isGlassEnabled) {
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

    // Effective Glass Mode based on API Version Guards
    val effectiveMode = when (looks.glassMode) {
        GlassMode.LIQUID -> {
            if (forceStandard) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlassMode.TRUE else GlassMode.STANDARD
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                GlassMode.LIQUID
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlassMode.TRUE
            } else {
                GlassMode.STANDARD
            }
        }
        GlassMode.TRUE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlassMode.TRUE else GlassMode.STANDARD
        GlassMode.STANDARD -> GlassMode.STANDARD
    }

    // ADAPTIVE SHAPE: More organic rounding for Liquid mode
    val liquidRadius = 48.dp
    val effectiveShape = if (effectiveMode == GlassMode.LIQUID) RoundedCornerShape(liquidRadius) else shape
    val currentCornerRadiusPx = if (effectiveMode == GlassMode.LIQUID) 48f else cornerRadius.toFloat()

    // Track window position and size for accurate refraction sampling
    var nodeOffset by remember { mutableStateOf(Offset.Zero) }
    var nodeSize by remember { mutableStateOf(Size.Zero) }

    Box(modifier = modifier.onGloballyPositioned { coordinates ->
        nodeOffset = coordinates.positionInWindow()
        nodeSize = coordinates.size.toSize()
    }, contentAlignment = Alignment.Center) {

        // 1. Ambient Drop Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = if (effectiveMode == GlassMode.LIQUID) 16.dp else 12.dp,
                    shape = effectiveShape,
                    clip = false,
                    ambientColor = if (effectiveMode == GlassMode.LIQUID) looks.glassColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.25f),
                    spotColor = if (effectiveMode == GlassMode.LIQUID) looks.glassColor.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.25f)
                )
        )

        // 2. Refraction / Backdrop Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (effectiveMode == GlassMode.LIQUID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backgroundLayer != null && nodeSize.width > 0f) {
                        // True Optical Refraction + Subtle Hardware Blur
                        val shader = RuntimeShader(LENS_SHADER).apply {
                            setFloatUniform("uElementSize", nodeSize.width, nodeSize.height)
                            setFloatUniform("uDistortionStrength", looks.liquidDistortion * 10f)
                            setFloatUniform("uCornerRadius", currentCornerRadiusPx.dp.toPx())
                        }

                        val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "uBackground")
                        // Tuned "Wet" blur for a premium weighted feel
                        val liquidBlur = (12f + (looks.blurIntensiveness * 0.25f))
                        val blurEffect = RenderEffect.createBlurEffect(liquidBlur, liquidBlur, Shader.TileMode.CLAMP)

                        renderEffect = RenderEffect.createChainEffect(shaderEffect, blurEffect).asComposeRenderEffect()
                    } else if (effectiveMode == GlassMode.TRUE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect.createBlurEffect(
                            looks.blurIntensiveness,
                            looks.blurIntensiveness,
                            Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }

                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                    this.shape = effectiveShape
                }
                .background(
                    if (effectiveMode == GlassMode.STANDARD) Color.Transparent else
                    looks.glassColor.copy(alpha = if (effectiveMode == GlassMode.LIQUID) 0.08f else 0.15f)
                )
                .then(
                    if (effectiveMode == GlassMode.LIQUID && backgroundLayer != null) {
                        Modifier.drawWithContent {
                            // Perfect Refraction Alignment:
                            // Shift the captured background so screen (X,Y) matches panel (X,Y)
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

        // 3. Lighting & Content Layer (Un-refracted)
        Box(
            modifier = Modifier
                .clip(effectiveShape)
                .background(
                    if (effectiveMode == GlassMode.STANDARD) looks.glassColor.copy(alpha = looks.glassTransparency) else Color.Transparent
                )
                .drawWithContent {
                    drawContent()

                    if (effectiveMode == GlassMode.LIQUID) {
                        // Liquid Rim Highlight
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    looks.glassColor.copy(alpha = 0.3f)
                                ),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            ),
                            cornerRadius = CornerRadius(currentCornerRadiusPx.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Diagonal Gloss Sweep
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                                start = Offset.Zero,
                                end = Offset(size.width * 0.5f, size.height * 0.4f)
                            ),
                            cornerRadius = CornerRadius(currentCornerRadiusPx.dp.toPx())
                        )
                    } else {
                        // Standard Border
                        drawRoundRect(
                            color = looks.glassColor.copy(alpha = if (effectiveMode == GlassMode.TRUE) 0.4f else 0.2f),
                            cornerRadius = CornerRadius(currentCornerRadiusPx.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
        ) {
            content()
        }
    }
}

/**
 * Returns the appropriate content color for glass panels.
 */
@Composable
fun glassContentColor(): Color {
    return Color.White
}
