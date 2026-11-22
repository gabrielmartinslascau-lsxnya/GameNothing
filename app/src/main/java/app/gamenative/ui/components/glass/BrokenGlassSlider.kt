package app.gamenative.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BrokenGlassSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val resolvedAccent = accentColor ?: if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.coerceAtLeast(1)

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }
        var didDrag by remember { mutableStateOf(false) }
        var liveDragValue by remember { mutableFloatStateOf(value().coerceIn(valueRange)) }

        val dampedDragAnimation = remember(animationScope, valueRange, trackWidth, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value().coerceIn(valueRange),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                horizontalDrag = true,
                onDragStarted = {
                    isDragging = true
                    didDrag = false
                    liveDragValue = value().coerceIn(valueRange)
                },
                onDragStopped = {
                    isDragging = false
                    if (didDrag) {
                        val committed = liveDragValue.coerceIn(valueRange)
                        onValueChange(committed)
                        onValueChangeFinished?.invoke(committed)
                        didDrag = false
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag && dragAmount.x != 0f) {
                        didDrag = true
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    val nextValue = (
                        if (isLtr) liveDragValue + delta
                        else liveDragValue - delta
                        ).coerceIn(valueRange)
                    liveDragValue = nextValue
                    updateValue(nextValue)
                    onValueChange(nextValue)
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { currentValue ->
                    if (!isDragging) {
                        val clamped = currentValue.coerceIn(valueRange)
                        liveDragValue = clamped
                        if (dampedDragAnimation.targetValue != clamped) {
                            dampedDragAnimation.updateValue(clamped)
                        }
                    }
                }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(animationScope, trackWidth, valueRange, isLtr) {
                    detectTapGestures { position ->
                        val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                        val targetValue =
                            (if (isLtr) valueRange.start + delta
                            else valueRange.endInclusive - delta)
                                .coerceIn(valueRange)
                        liveDragValue = targetValue
                        dampedDragAnimation.animateToValue(targetValue)
                        onValueChange(targetValue)
                        onValueChangeFinished?.invoke(targetValue)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.layerBackdrop(trackBackdrop)) {
                Box(
                    Modifier
                        .clip(Capsule())
                        .background(trackColor)
                        .height(6f.dp)
                        .fillMaxWidth()
                )

                Box(
                    Modifier
                        .clip(Capsule())
                        .background(resolvedAccent)
                        .height(6f.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val width = (constraints.maxWidth * dampedDragAnimation.progress.coerceIn(0f, 1f)).fastRoundToInt()
                            layout(width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                )
            }
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress.coerceIn(0f, 1f))
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
