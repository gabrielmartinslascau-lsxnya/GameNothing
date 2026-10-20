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
 * AGSL Shader for Normal Glass Refraction (Magnifying Glass effect).
 * Implements physical UV magnification (Spherical Bulge) for a true lens effect.
 */
private const val LENS_SHADER_NORMAL = """
    uniform shader uBackground;
    uniform float2 uElementSize;
    uniform float uDistortionStrength;
    uniform float uCornerRadius;

    half4 main(float2 fragCoord) {
        float2 b = uElementSize * 0.5;
        if (b.x <= 0.0 || b.y <= 0.0) {
            return uBackground.eval(fragCoord);
        }

        float2 p = fragCoord - b;
        float dist = length(p);

        // Spherical lens magnification
        float maxRadius = length(b);
        float normDist = clamp(dist / max(maxRadius, 1.0), 0.0, 1.0);

        // Quadratic falloff: maximum magnification in center, tapering smoothly to 1.0 at edges
        float magnification = 1.0 + uDistortionStrength * 0.8;
        float factor = mix(magnification, 1.0, normDist * normDist);

        // Chromatic dispersion for magnifying lens
        float2 refractOffset = (p / max(factor, 0.001)) - p;

        float2 maxBounds = uElementSize - float2(0.5);
        float2 rOffset = clamp(b + p + refractOffset * 1.04, float2(0.5), maxBounds);
        float2 gOffset = clamp(b + p + refractOffset, float2(0.5), maxBounds);
        float2 bOffset = clamp(b + p + refractOffset * 0.96, float2(0.5), maxBounds);

        half r = uBackground.eval(rOffset).r;
        half g = uBackground.eval(gOffset).g;
        half bColor = uBackground.eval(bOffset).b;
        half a = uBackground.eval(gOffset).a;

        return half4(r, g, bColor, a);
    }
"""

/**
 * AGSL Shader for 3D Optical Liquid Glass Refraction (Match Flutter liquid_glass.frag).
 * Calculates edge distance bevel factor and distorts coordinates along radial vectors.
 */
private const val LENS_SHADER_LIQUID = """
    uniform shader uBackground;
    uniform float2 uElementSize;
    uniform float uDistortionStrength;
    uniform float uCornerRadius;

    half4 main(float2 fragCoord) {
        float2 b = uElementSize * 0.5;
        if (b.x <= 0.0 || b.y <= 0.0) {
            return uBackground.eval(fragCoord);
        }

        float2 p = fragCoord - b;

        // Simple distance from center
        float dist = length(p);

        // Distance from edge (in pixels)
        float edgeDist = min(b.x, b.y) - dist;

        // Bevel width (pixels)
        float bevelWidth = 15.0;

        // 1.0 at the edge, fading to 0.0 inside
        float edgeFactor = smoothstep(0.0, bevelWidth, edgeDist);

        // Direction from center
        float2 dir = dist > 0.001 ? (p / dist) : float2(0.0);

        // Distort outward near the edge (Inverted Magnifying Glass)
        float2 refractedCoord = fragCoord + (dir * uDistortionStrength * 20.0 * edgeFactor);

        // Clamp to prevent out-of-bounds sampling
        float2 maxBounds = uElementSize - float2(0.5);
        float2 clampedCoord = clamp(refractedCoord, float2(0.5), maxBounds);

        return uBackground.eval(clampedCoord);
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

    // Determine shader mode
    val selectedShader = when (looks.glassMode) {
        GlassMode.LIQUID -> LENS_SHADER_LIQUID
        else -> LENS_SHADER_NORMAL
    }

    val runtimeShader = remember(selectedShader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try { RuntimeShader(selectedShader) } catch (_: Throwable) { null }
        } else null
    }

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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null && backgroundLayer != null && nodeSize.width > 0f) {
                        try {
                            val liquidDistortionValue = if (isScrolling) 0f else (distortionStrength ?: looks.liquidDistortion)

                            runtimeShader.setFloatUniform("uElementSize", nodeSize.width, nodeSize.height)
                            runtimeShader.setFloatUniform("uDistortionStrength", liquidDistortionValue)
                            runtimeShader.setFloatUniform("uCornerRadius", currentCornerRadiusPx.dp.toPx())

                            val shaderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "uBackground")
                            val liquidBlur = (12f + (looks.blurIntensiveness * 0.25f))
                            val blurEffect = RenderEffect.createBlurEffect(liquidBlur, liquidBlur, Shader.TileMode.CLAMP)

                            renderEffect = RenderEffect.createChainEffect(shaderEffect, blurEffect).asComposeRenderEffect()
                        } catch (_: Throwable) {
                            renderEffect = RenderEffect.createBlurEffect(
                                looks.blurIntensiveness,
                                looks.blurIntensiveness,
                                Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
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
