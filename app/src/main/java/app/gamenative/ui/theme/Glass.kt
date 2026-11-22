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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import app.gamenative.enums.GlassMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * AGSL Shader for Normal (Standard) Glass Refraction.
 * Implements physical UV magnification (Spherical Bulge) with subtle chromatic edge separation.
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
 * Fallback [Backdrop] drawn on a local canvas when a surface renders outside a
 * parent [LayerBackdrop] (such as standalone Dialog windows, Popups, or XServer overlays).
 */
@Composable
fun rememberBrokenGlassCanvasBackdrop(
    tintColor: Color? = null
): Backdrop {
    val looks = LocalLooksState.current
    val baseColor = tintColor ?: looks.wallpaperColor
    val primaryGlow = looks.mainColor.copy(alpha = 0.42f)
    val rimGlow = looks.glassColor.copy(alpha = 0.22f)
    val onDraw = remember<DrawScope.() -> Unit>(baseColor, primaryGlow, rimGlow) {
        {
            drawRect(baseColor)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.18f),
                    radius = size.maxDimension.coerceAtLeast(1f) * 0.78f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(rimGlow, Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.85f),
                    radius = size.maxDimension.coerceAtLeast(1f) * 0.65f
                )
            )
        }
    }
    return rememberCanvasBackdrop(onDraw)
}

/**
 * Primary Glass container supporting two completely distinct shader engines:
 * - [GlassMode.STANDARD]: AGSL spherical magnifying glass (`LENS_SHADER_NORMAL`) driven by [LooksState.liquidDistortion].
 * - [GlassMode.BROKEN_GLASS]: AndroidLiquidGlass 2.0.1 (`com.kyant.backdrop`) driven by refraction, dispersion, thickness, and luminance.
 */
@Composable
fun BrokenGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp),
    isScrolling: Boolean = false,
    refractionStrength: Float? = null,
    dispersionStrength: Float? = null,
    thickness: Float? = null,
    luminance: Float? = null,
    backgroundColor: Color? = null,
    enableHighlight: Boolean = true,
    enableShadow: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val looks = LocalLooksState.current

    if (!looks.isGlassEnabled) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(backgroundColor ?: looks.wallpaperColor, shape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center
        ) {
            Box(content = content)
        }
        return
    }

    Box(
        modifier = modifier.brokenGlass(
            cornerRadius = cornerRadius,
            shape = shape,
            isScrolling = isScrolling,
            refractionStrength = refractionStrength,
            dispersionStrength = dispersionStrength,
            thickness = thickness,
            luminance = luminance,
            backgroundColor = backgroundColor,
            enableHighlight = enableHighlight,
            enableShadow = enableShadow
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.clip(shape),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Modifier applying either Normal Glass (`LENS_SHADER_NORMAL` + `liquidDistortion`) or
 * Broken Glass (`com.kyant.backdrop` optical refraction/dispersion/thickness/luminance),
 * strictly separated by [LooksState.glassMode].
 */
@Composable
fun Modifier.brokenGlass(
    cornerRadius: Int = 16,
    shape: Shape = RoundedCornerShape(cornerRadius.dp),
    isScrolling: Boolean = false,
    refractionStrength: Float? = null,
    dispersionStrength: Float? = null,
    thickness: Float? = null,
    luminance: Float? = null,
    backgroundColor: Color? = null,
    enableHighlight: Boolean = true,
    enableShadow: Boolean = true
): Modifier {
    val looks = LocalLooksState.current
    if (!looks.isGlassEnabled) return this

    val liveGlass = LocalLiveGlassParameters.current

    if (looks.glassMode == GlassMode.STANDARD) {
        // Inactive-effect short-circuit: without a live captured source (Dialogs, Popups, XServer overlays,
        // or root capture gated off) the AGSL lens has nothing to sample, so skip the offscreen
        // GraphicsLayer, RuntimeShader allocation, and onGloballyPositioned tracking entirely.
        val hasLiveSource = LocalBackgroundLayer.current != null && LocalBrokenGlassBackdrop.current != null
        if (!hasLiveSource) {
            return this.standardGlassStatic(
                shape = shape,
                glassColor = looks.glassColor,
                glassTransparencyProvider = { liveGlass.glassTransparency },
                backgroundColor = backgroundColor,
                enableHighlight = enableHighlight
            )
        }
        return this.standardGlass(
            cornerRadius = cornerRadius,
            shape = shape,
            distortionStrengthProvider = { liveGlass.liquidDistortion },
            blurIntensityProvider = { liveGlass.blurIntensiveness },
            glassColor = looks.glassColor,
            glassTransparencyProvider = { liveGlass.glassTransparency },
            backgroundColor = backgroundColor,
            enableHighlight = enableHighlight
        )
    }

    val backdrop = LocalBrokenGlassBackdrop.current ?: rememberBrokenGlassCanvasBackdrop(backgroundColor)

    val customSurfaceAlpha = backgroundColor?.alpha?.coerceAtMost(0.24f) ?: 0f

    val shapeProvider = remember<() -> Shape>(shape) { { shape } }
    // ZERO slider floats in remember keys: DrawBackdropNode observes liveGlass snapshot reads
    // inside BackdropEffectScope and updates cached RuntimeShader uniforms without recomposition.
    val effectsBlock = remember<BackdropEffectScope.() -> Unit>(
        refractionStrength,
        dispersionStrength,
        thickness,
        luminance,
        liveGlass,
    ) {
        {
            val resolvedRefraction = (refractionStrength ?: liveGlass.brokenGlassRefraction).coerceIn(0f, 1f)
            val resolvedDispersion = (dispersionStrength ?: liveGlass.brokenGlassDispersion).coerceIn(0f, 1f)
            val resolvedThickness = (thickness ?: liveGlass.brokenGlassThickness).coerceIn(12f, 80f)
            val resolvedLuminance = (luminance ?: liveGlass.brokenGlassLuminance).coerceIn(0f, 1f)
            val blurDp = (liveGlass.blurIntensiveness * (0.45f + resolvedLuminance * 0.35f)).coerceIn(2f, 28f)

            val brightness = (resolvedLuminance - 0.5f) * 0.24f
            val contrast = 1.0f + (resolvedLuminance - 0.5f) * 0.18f
            val saturation = 1.2f + resolvedDispersion * 0.28f
            colorControls(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation
            )
            blur(blurDp.dp.toPx())
            if (resolvedRefraction > 0.01f) {
                val refractionHeightPx = (resolvedThickness * 0.35f).coerceIn(4f, 32f).dp.toPx()
                val refractionAmountPx = (8f + resolvedRefraction * 44f).dp.toPx()
                lens(
                    refractionHeight = refractionHeightPx,
                    refractionAmount = refractionAmountPx,
                    depthEffect = true,
                    chromaticAberration = resolvedDispersion > 0.01f,
                    chromaticAberrationIntensity = resolvedDispersion * 1.4f
                )
            }
        }
    }
    val highlightBlock = remember<(() -> Highlight?)?>(enableHighlight, looks.glassColor, luminance, liveGlass) {
        if (enableHighlight) {
            {
                val resolvedLuminance = (luminance ?: liveGlass.brokenGlassLuminance).coerceIn(0f, 1f)
                val highlightColor =
                    looks.glassColor.copy(alpha = (0.42f + resolvedLuminance * 0.28f).coerceIn(0.25f, 0.85f))
                Highlight.Default.copy(
                    style = HighlightStyle.Default(
                        color = highlightColor,
                        falloff = 1.2f
                    )
                )
            }
        } else {
            null
        }
    }
    val shadowBlock = remember<(() -> Shadow?)?>(enableShadow) {
        if (enableShadow) {
            {
                Shadow(
                    radius = 16.dp,
                    color = Color.Black.copy(alpha = 0.26f)
                )
            }
        } else {
            null
        }
    }
    val innerShadowBlock = remember<() -> InnerShadow?> {
        {
            InnerShadow(
                radius = 8.dp,
                alpha = 0.18f
            )
        }
    }
    val surfaceBlock = remember<DrawScope.() -> Unit>(
        backgroundColor,
        customSurfaceAlpha,
        looks.glassColor,
        luminance,
        liveGlass,
    ) {
        {
            if (backgroundColor != null && backgroundColor != Color.Transparent && customSurfaceAlpha > 0f) {
                drawRect(backgroundColor.copy(alpha = customSurfaceAlpha))
            }
            val resolvedLuminance = (luminance ?: liveGlass.brokenGlassLuminance).coerceIn(0f, 1f)
            val surfaceTintAlpha =
                (0.05f + liveGlass.glassTransparency * 0.16f + (resolvedLuminance - 0.5f) * 0.08f)
                    .coerceIn(0.03f, 0.32f)
            drawRect(looks.glassColor.copy(alpha = surfaceTintAlpha))
        }
    }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shapeProvider,
        effects = effectsBlock,
        highlight = highlightBlock,
        shadow = shadowBlock,
        innerShadow = innerShadowBlock,
        onDrawSurface = surfaceBlock
    )
}

/**
 * Legacy Normal Glass (GameNothing 1.2) modifier using AGSL [LENS_SHADER_NORMAL] + [LocalBackgroundLayer].
 * Completely independent of Broken Glass shader parameters.
 */
@Composable
private fun Modifier.standardGlass(
    cornerRadius: Int,
    shape: Shape,
    distortionStrengthProvider: () -> Float,
    blurIntensityProvider: () -> Float,
    glassColor: Color,
    glassTransparencyProvider: () -> Float,
    backgroundColor: Color?,
    enableHighlight: Boolean,
): Modifier {
    val backgroundLayer = LocalBackgroundLayer.current
    val activeBackdrop = LocalBrokenGlassBackdrop.current
    val standardLayer = rememberGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    // RuntimeShader allocated strictly ONCE per node; slider drags only update uniforms in drawWithContent.
    val runtimeShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(LENS_SHADER_NORMAL)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }
    val density = LocalDensity.current
    val cornerRadiusPx = remember(density, cornerRadius) { with(density) { cornerRadius.dp.toPx() } }

    var nodeCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastWidth by remember { mutableFloatStateOf(-1f) }
    var lastHeight by remember { mutableFloatStateOf(-1f) }
    var lastDistortion by remember { mutableFloatStateOf(Float.NaN) }
    var lastBlur by remember { mutableFloatStateOf(Float.NaN) }
    var lastCornerPx by remember { mutableFloatStateOf(Float.NaN) }
    var lastHadLiveLayer by remember { mutableStateOf(false) }

    val fallbackBgAlpha = backgroundColor?.alpha?.coerceAtMost(0.28f) ?: 0f

    return this
        .clip(shape)
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                nodeCoords = coords
            }
        }
        .drawWithContent {
            val distortionStrength = distortionStrengthProvider().coerceIn(0f, 1f)
            val blurIntensity = blurIntensityProvider().coerceIn(1f, 50f)
            val tintAlpha = (0.06f + glassTransparencyProvider() * 0.14f).coerceIn(0.05f, 0.24f)

            val hasLiveBackdrop = backgroundLayer != null && activeBackdrop != null && size.width > 0f && size.height > 0f
            if (hasLiveBackdrop && activeBackdrop != null) {
                val needsEffectUpdate =
                    size.width != lastWidth ||
                        size.height != lastHeight ||
                        distortionStrength != lastDistortion ||
                        blurIntensity != lastBlur ||
                        cornerRadiusPx != lastCornerPx ||
                        !lastHadLiveLayer

                if (needsEffectUpdate) {
                    standardLayer.renderEffect = buildStandardGlassRenderEffect(
                        runtimeShader = runtimeShader,
                        width = size.width,
                        height = size.height,
                        distortionStrength = distortionStrength,
                        cornerRadiusPx = cornerRadiusPx,
                        blurIntensity = blurIntensity
                    )
                    lastWidth = size.width
                    lastHeight = size.height
                    lastDistortion = distortionStrength
                    lastBlur = blurIntensity
                    lastCornerPx = cornerRadiusPx
                    lastHadLiveLayer = true
                }

                standardLayer.record {
                    with(activeBackdrop) {
                        drawBackdrop(
                            density = this@drawWithContent,
                            coordinates = nodeCoords,
                            layerBlock = null
                        )
                    }
                }
                drawLayer(standardLayer)
            } else if (lastHadLiveLayer) {
                standardLayer.renderEffect = null
                lastHadLiveLayer = false
            }

            if (backgroundColor != null && backgroundColor != Color.Transparent) {
                val bgAlpha = if (hasLiveBackdrop) fallbackBgAlpha else backgroundColor.alpha.coerceIn(0.32f, 0.72f)
                if (bgAlpha > 0f) {
                    drawRect(backgroundColor.copy(alpha = bgAlpha))
                }
            } else if (!hasLiveBackdrop) {
                drawRect(glassColor.copy(alpha = 0.14f))
            }

            drawRect(glassColor.copy(alpha = tintAlpha))
            drawContent()

            if (enableHighlight) {
                drawStandardGlassHighlight(shape, glassColor)
            }
        }
}

/**
 * Zero-layer Normal Glass fallback used when no live backdrop source exists.
 * Pixel-identical to [standardGlass]'s no-backdrop branch, but allocates no GraphicsLayer,
 * no RuntimeShader, and registers no global-position callbacks.
 */
private fun Modifier.standardGlassStatic(
    shape: Shape,
    glassColor: Color,
    glassTransparencyProvider: () -> Float,
    backgroundColor: Color?,
    enableHighlight: Boolean,
): Modifier = this
    .clip(shape)
    .drawWithContent {
        val tintAlpha = (0.06f + glassTransparencyProvider() * 0.14f).coerceIn(0.05f, 0.24f)
        if (backgroundColor != null && backgroundColor != Color.Transparent) {
            drawRect(backgroundColor.copy(alpha = backgroundColor.alpha.coerceIn(0.32f, 0.72f)))
        } else {
            drawRect(glassColor.copy(alpha = 0.14f))
        }
        drawRect(glassColor.copy(alpha = tintAlpha))
        drawContent()
        if (enableHighlight) {
            drawStandardGlassHighlight(shape, glassColor)
        }
    }

private fun DrawScope.drawStandardGlassHighlight(shape: Shape, glassColor: Color) {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f),
                Color.Transparent,
                glassColor.copy(alpha = 0.28f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        style = Stroke(width = 1.25.dp.toPx())
    )
}

private fun buildStandardGlassRenderEffect(
    runtimeShader: RuntimeShader?,
    width: Float,
    height: Float,
    distortionStrength: Float,
    cornerRadiusPx: Float,
    blurIntensity: Float,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
        return try {
            runtimeShader.setFloatUniform("uElementSize", width, height)
            runtimeShader.setFloatUniform("uDistortionStrength", distortionStrength)
            runtimeShader.setFloatUniform("uCornerRadius", cornerRadiusPx)
            val shaderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "uBackground")
            val liquidBlur = 12f + (blurIntensity * 0.25f)
            val blurEffect = RenderEffect.createBlurEffect(liquidBlur, liquidBlur, Shader.TileMode.CLAMP)
            RenderEffect.createChainEffect(shaderEffect, blurEffect).asComposeRenderEffect()
        } catch (_: Throwable) {
            RenderEffect.createBlurEffect(blurIntensity, blurIntensity, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return RenderEffect.createBlurEffect(blurIntensity, blurIntensity, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
    return null
}

/**
 * Wraps dialog content in a Glass surface isolated in its own window with a local [CanvasBackdrop]
 * (`LocalBackgroundLayer` forced to `null` so it never samples the parent window's GraphicsLayer).
 */
@Composable
fun BrokenGlassDialogSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 28,
    content: @Composable BoxScope.() -> Unit
) {
    val looks = LocalLooksState.current
    val shape = RoundedCornerShape(cornerRadius.dp)
    if (looks.isGlassEnabled) {
        val localBackdrop = rememberBrokenGlassCanvasBackdrop(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
        CompositionLocalProvider(
            LocalBrokenGlassBackdrop provides localBackdrop,
            LocalBackgroundLayer provides null,
        ) {
            BrokenGlassPanel(
                modifier = modifier,
                cornerRadius = cornerRadius,
                shape = shape,
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                content = content
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
            content = content
        )
    }
}

/**
 * Wraps `DropdownMenu` popup content in a high-contrast, elevated surface (`zIndex(1000f)`).
 * Intentionally avoids `Modifier.brokenGlass` (`CompositingStrategy.Offscreen` + `RenderEffect`)
 * inside `Popup` scroll containers where oversized offscreen layers fail to render on Android HWUI.
 */
@Composable
fun BrokenGlassMenuSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    content: @Composable BoxScope.() -> Unit
) {
    val looks = LocalLooksState.current
    val shape = RoundedCornerShape(cornerRadius.dp)
    if (looks.isGlassEnabled) {
        Box(
            modifier = modifier
                .zIndex(1000f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), shape)
                .border(1.dp, looks.glassColor.copy(alpha = 0.28f), shape),
            content = content
        )
    } else {
        Box(
            modifier = modifier
                .zIndex(1000f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
            content = content
        )
    }
}

@Composable
fun glassContentColor(): Color = Color.White
