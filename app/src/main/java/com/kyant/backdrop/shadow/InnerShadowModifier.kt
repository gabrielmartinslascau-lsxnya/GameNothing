package com.kyant.backdrop.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.clipOutline
import com.kyant.backdrop.isRenderEffectSupported

internal class InnerShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> InnerShadow?
) : ModifierNodeElement<InnerShadowNode>() {

    override fun create(): InnerShadowNode {
        return InnerShadowNode(shapeProvider, shadow)
    }

    override fun update(node: InnerShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (shadow != other.shadow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

internal class InnerShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> InnerShadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null

    private val paint = Paint()
    private var clipPath: Path? = null

    private var prevRadius = Float.NaN
    private var lastSize: androidx.compose.ui.geometry.Size = androidx.compose.ui.geometry.Size.Unspecified
    private var lastDensity: Float = Float.NaN
    private var lastFontScale: Float = Float.NaN
    private var lastLayoutDirection: androidx.compose.ui.unit.LayoutDirection? = null
    private var lastShape: androidx.compose.ui.graphics.Shape? = null
    private var lastShadow: InnerShadow? = null
    private var lastOutline: Outline? = null

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return

        val shadow = shadow() ?: return

        val shadowLayer = shadowLayer
        if (shadowLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection
            val shape = shapeProvider.shape

            val needsRecord =
                size != lastSize ||
                    density.density != lastDensity ||
                    density.fontScale != lastFontScale ||
                    layoutDirection != lastLayoutDirection ||
                    shape != lastShape ||
                    shadow != lastShadow ||
                    lastOutline == null

            if (needsRecord) {
                val radius = shadow.radius.toPx()
                val offsetX = shadow.offset.x.toPx()
                val offsetY = shadow.offset.y.toPx()

                val outline = shape.createOutline(size, layoutDirection, density)
                val resolvedClipPath =
                    if (outline is Outline.Rounded) {
                        clipPath ?: Path().also { clipPath = it }
                    } else {
                        null
                    }

                configurePaint(shadow)

                shadowLayer.alpha = shadow.alpha
                shadowLayer.blendMode = shadow.blendMode
                if (prevRadius != radius) {
                    shadowLayer.renderEffect =
                        if (radius > 0f) {
                            BlurEffect(radius, radius, TileMode.Decal)
                        } else {
                            null
                        }
                    prevRadius = radius
                }
                shadowLayer.record {
                    val canvas = drawContext.canvas
                    canvas.save()
                    canvas.clipOutline(outline, resolvedClipPath)
                    canvas.drawOutline(outline, paint)
                    canvas.translate(offsetX, offsetY)
                    canvas.drawOutline(outline, ShadowMaskPaint)
                    canvas.translate(-offsetX, -offsetY)
                    canvas.restore()
                }

                lastSize = size
                lastDensity = density.density
                lastFontScale = density.fontScale
                lastLayoutDirection = layoutDirection
                lastShape = shape
                lastShadow = shadow
                lastOutline = outline
            }

            val outline = lastOutline ?: return
            val resolvedClipPath = if (outline is Outline.Rounded) clipPath else null
            val canvas = drawContext.canvas
            canvas.save()
            canvas.clipOutline(outline, resolvedClipPath)
            drawLayer(shadowLayer)
            canvas.restore()
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer =
            graphicsContext.createGraphicsLayer().apply {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        clipPath = null
        prevRadius = Float.NaN
        lastSize = androidx.compose.ui.geometry.Size.Unspecified
        lastDensity = Float.NaN
        lastFontScale = Float.NaN
        lastLayoutDirection = null
        lastShape = null
        lastShadow = null
        lastOutline = null
    }

    private fun DrawScope.configurePaint(shadow: InnerShadow) {
        paint.color = shadow.color
    }
}

private val ShadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}
