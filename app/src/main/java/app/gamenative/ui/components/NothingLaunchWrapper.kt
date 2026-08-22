package app.gamenative.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

// Class with mutable properties to allow recycling without re-allocation
class NdotParticle(
    var xOffsetPx: Float,
    var currentYPx: Float,
    var vy: Float,
    var alpha: Float = 0f // Start invisible
)

/**
 * A ultra-optimized startup wrapper that acts as a true buffer.
 * It uses a pre-allocated object pool to render a dense 60-dot horde
 * with zero garbage collection overhead.
 */
@Composable
fun NothingLaunchWrapper(
    onAnimationEnd: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var isAnimating by remember { mutableStateOf(true) }
    var bgAlpha by remember { mutableFloatStateOf(1f) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = density.run { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = density.run { configuration.screenWidthDp.dp.toPx() }

    // Pre-allocate 60 particles and recycle them throughout the animation
    val particles = remember {
        Array(60) {
            NdotParticle(
                xOffsetPx = 0f,
                currentYPx = screenHeightPx + 100f, // Start off-screen
                vy = 0f,
                alpha = 0f
            )
        }
    }

    var frameTicker by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()

        // PHASE 1: Dense Horde Stream (0-1000ms)
        while (System.currentTimeMillis() - startTime < 1000) {
            particles.forEach { p ->
                // Update physics
                p.currentYPx += p.vy
                val progress = (p.currentYPx / screenHeightPx).coerceIn(0f, 1f)
                p.alpha = (1f - progress).coerceIn(0f, 1f)

                // Recycle/Respawn if the particle finished its journey
                if (p.currentYPx > screenHeightPx || p.alpha <= 0f) {
                    p.currentYPx = 0f
                    p.xOffsetPx = (Random.nextFloat() - 0.5f) * screenWidthPx * 0.85f
                    p.vy = 12f + Random.nextFloat() * 12f
                    p.alpha = 1f
                }
            }

            frameTicker++
            delay(16)
        }

        // PHASE 2: Clear-out (Stop respawning, wait for dots to clear screen)
        var allCleared = false
        while (!allCleared) {
            allCleared = true
            particles.forEach { p ->
                if (p.alpha > 0.01f && p.currentYPx <= screenHeightPx) {
                    p.currentYPx += p.vy
                    val progress = (p.currentYPx / screenHeightPx).coerceIn(0f, 1f)
                    p.alpha = (1f - progress).coerceIn(0f, 1f)
                    allCleared = false
                } else {
                    p.alpha = 0f
                }
            }
            frameTicker++
            delay(16)
        }

        // PHASE 3: Reveal UI
        val fadeStartTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - fadeStartTime < 500) {
            bgAlpha = (1f - ((System.currentTimeMillis() - fadeStartTime) / 500f)).coerceIn(0f, 1f)
            delay(16)
        }

        bgAlpha = 0f
        isAnimating = false
        onAnimationEnd()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Optimization: Concurrent Rendering
        // Always render content so it builds in background, just hide it with alpha.
        Box(modifier = Modifier.graphicsLayer { alpha = 1f - bgAlpha }) {
            content()
        }

        if (isAnimating) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = bgAlpha }
                    .background(Color.Black)
            ) {
                // Dependency on frameTicker ensures this pass runs every 16ms
                @Suppress("UNUSED_VARIABLE")
                val trigger = frameTicker

                val centerX = size.width / 2f
                val spawnY = size.height
                val dotSizePx = 14.dp.toPx() // Retro bold snap

                particles.forEach { p ->
                    if (p.alpha > 0f) {
                        val rawX = centerX + p.xOffsetPx
                        val rawY = spawnY - p.currentYPx

                        val snappedX = rawX - (rawX % dotSizePx)
                        val snappedY = rawY - (rawY % dotSizePx)

                        drawCircle(
                            color = Color.White.copy(alpha = p.alpha),
                            radius = (dotSizePx * 0.45f),
                            center = Offset(snappedX + dotSizePx / 2f, snappedY + dotSizePx / 2f)
                        )
                    }
                }
            }
        }
    }
}
