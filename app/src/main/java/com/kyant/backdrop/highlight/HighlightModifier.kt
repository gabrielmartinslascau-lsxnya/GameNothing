package com.kyant.backdrop.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.RuntimeShaderCacheImpl
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.blur
import com.kyant.backdrop.internal.clipOutline
import com.kyant.backdrop.internal.setRuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    private var lastSize: androidx.compose.ui.geometry.Size = androidx.compose.ui.geometry.Size.Unspecified
    private var lastDensity: Float = Float.NaN
    private var lastFontScale: Float = Float.NaN
    private var lastLayoutDirection: androidx.compose.ui.unit.LayoutDirection? = null
    private var lastShape: androidx.compose.ui.graphics.Shape? = null
    private var lastHighlight: Highlight? = null

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
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
                    highlight != lastHighlight

            if (needsRecord) {
                val safeSize =
                    IntSize(
                        ceil(size.width).toInt() + 2,
                        ceil(size.height).toInt() + 2
                    )

                val outline = shape.createOutline(size, layoutDirection, density)
                val clipPath =
                    if (outline is Outline.Rounded) {
                        clipPath ?: Path().also { clipPath = it }
                    } else {
                        null
                    }

                configurePaint(highlight)

                highlightLayer.alpha = highlight.alpha
                highlightLayer.blendMode = highlight.style.blendMode
                highlightLayer.record(safeSize) {
                    translate(1f, 1f) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }

                lastSize = size
                lastDensity = density.density
                lastFontScale = density.fontScale
                lastLayoutDirection = layoutDirection
                lastShape = shape
                lastHighlight = highlight
            }

            translate(-1f, -1f) {
                drawLayer(highlightLayer)
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        runtimeShaderCache.clear()
        lastSize = androidx.compose.ui.geometry.Size.Unspecified
        lastDensity = Float.NaN
        lastFontScale = Float.NaN
        lastLayoutDirection = null
        lastShape = null
        lastHighlight = null
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        paint.color = highlight.style.color
        paint.strokeWidth = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        paint.blur(highlight.blurRadius.toPx())
        if (isRuntimeShaderSupported()) {
            val shader =
                with(highlight.style) {
                    createShader(
                        shape = shapeProvider.innerShape,
                        runtimeShaderCache = runtimeShaderCache
                    )
                }
            paint.setRuntimeShader(shader)
        }
    }
}
