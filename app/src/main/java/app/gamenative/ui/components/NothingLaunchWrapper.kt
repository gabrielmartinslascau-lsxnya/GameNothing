package app.gamenative.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import timber.log.Timber
import kotlin.random.Random

// Class with mutable properties to allow recycling without re-allocation
class NdotParticle(
    var xOffsetPx: Float,
    var currentYPx: Float,
    var vy: Float,
    var alpha: Float = 0f,
    var wobblePhase: Float = Random.nextFloat() * 2 * Math.PI.toFloat(),
    var wobbleSpeed: Float = 0.05f + Random.nextFloat() * 0.1f
)

/**
 * A ultra-optimized startup wrapper that acts as a true buffer.
 */
@Composable
fun NothingLaunchWrapper(
    onDotsCleared: () -> Unit = {},
    onRevealStarted: () -> Unit = {},
    onAnimationEnd: () -> Unit = {},
    showDots: Boolean = true,
    isReady: Boolean = false,
    introContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var isAnimating by remember { mutableStateOf(true) }
    var bgAlpha by remember { mutableFloatStateOf(1f) }
    var localReleased by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = density.run { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = density.run { configuration.screenWidthDp.dp.toPx() }

    // Use rememberUpdatedState to ensure the LaunchedEffect loop sees the latest value
    val latestIsReady by rememberUpdatedState(isReady)

    val particles = remember {
        Array(120) {
            NdotParticle(
                xOffsetPx = (Random.nextFloat() - 0.5f) * screenWidthPx * 0.9f,
                currentYPx = Random.nextFloat() * screenHeightPx, // Spread them out vertically for continuous bubbles
                vy = 10f + Random.nextFloat() * 18f,
                alpha = 0f
            )
        }
    }

    var frameTicker by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        Timber.tag("NothingWrapper").d("Starting loop. showDots=$showDots")

        // SAFETY TIMEOUT: If we're still black after 8 seconds, force reveal.
        val timeoutJob = launch {
            delay(8000)
            localReleased = true
            if (isAnimating) {
                Timber.tag("NothingWrapper").w("Startup timeout reached, forcing reveal.")
                bgAlpha = 0f
                isAnimating = false
                onAnimationEnd()
            }
        }

        // PHASE 1: Active Horde
        while ((System.currentTimeMillis() - startTime < 1200 || !latestIsReady) && !localReleased) {
            if (showDots) {
                particles.forEach { p ->
                    p.currentYPx += p.vy
                    p.wobblePhase += p.wobbleSpeed

                    // Fade in at bottom, fade out at top
                    val fadeRange = 200f
                    val alphaValue = if (p.currentYPx < fadeRange) {
                        (p.currentYPx / fadeRange).coerceIn(0f, 1f)
                    } else {
                        (1f - (p.currentYPx / screenHeightPx)).coerceIn(0f, 1f)
                    }
                    p.alpha = alphaValue

                    val stillActive = System.currentTimeMillis() - startTime < 1200 || !latestIsReady
                    if (p.currentYPx > screenHeightPx) {
                        if (stillActive) {
                            p.currentYPx = -50f - (Random.nextFloat() * 200f) // More staggered respawn
                            p.xOffsetPx = (Random.nextFloat() - 0.5f) * screenWidthPx * 0.9f
                            p.vy = 8f + Random.nextFloat() * 20f
                            p.wobblePhase = Random.nextFloat() * 2 * Math.PI.toFloat()
                        } else {
                            p.alpha = 0f
                        }
                    }
                }
                frameTicker++
            }
            delay(16)
        }

        if (showDots && !localReleased) {
            var allCleared = false
            while (!allCleared && !localReleased) {
                allCleared = true
                particles.forEach { p ->
                    if (p.alpha > 0.01f && p.currentYPx <= screenHeightPx) {
                        p.currentYPx += p.vy
                        p.wobblePhase += p.wobbleSpeed

                        val fadeRange = 200f
                        val alphaValue = if (p.currentYPx < fadeRange) {
                            (p.currentYPx / fadeRange).coerceIn(0f, 1f)
                        } else {
                            (1f - (p.currentYPx / screenHeightPx)).coerceIn(0f, 1f)
                        }
                        p.alpha = alphaValue
                        allCleared = false
                    } else {
                        p.alpha = 0f
                    }
                }
                frameTicker++
                delay(16)
            }
        }

        // Inform PluviaMain that dots are done and we can show booting text if needed
        Timber.tag("NothingWrapper").d("Phase 2 complete (Dots cleared). Signaling onDotsCleared.")
        onDotsCleared()

        // Wait for final signal or timeout
        Timber.tag("NothingWrapper").d("Waiting for latestIsReady ($latestIsReady) or localReleased ($localReleased)")

        if (!latestIsReady && !localReleased) {
            snapshotFlow { latestIsReady || localReleased }.filter { it }.first()
        }

        Timber.tag("NothingWrapper").d("Reveal signal received. Starting Phase 3 (Fade Out).")
        onRevealStarted()

        // PHASE 3: Reveal UI (500ms Fade)
        val fadeStartTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - fadeStartTime < 500) {
            bgAlpha = (1f - ((System.currentTimeMillis() - fadeStartTime) / 500f)).coerceIn(0f, 1f)
            delay(16)
        }

        bgAlpha = 0f
        isAnimating = false
        onAnimationEnd()
        Timber.tag("NothingWrapper").d("Reveal complete. UI is now fully visible.")
        timeoutJob.cancel()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - bgAlpha }) {
            this@Box.content()
        }

        if (isAnimating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = bgAlpha }
                    .background(Color.Black)
            ) {
                this@Box.introContent()

                if (showDots) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        @Suppress("UNUSED_VARIABLE")
                        val ticker = frameTicker
                        val centerX = size.width / 2f
                        val spawnY = size.height
                        val dotSizePx = 16.dp.toPx()

                        particles.forEach { p ->
                            if (p.alpha > 0f) {
                                // Add horizontal wobble for "boiling" effect
                                val wobbleX = Math.sin(p.wobblePhase.toDouble()).toFloat() * 15f
                                val rawX = centerX + p.xOffsetPx + wobbleX
                                val rawY = spawnY - p.currentYPx
                                val snappedX = rawX - (rawX % dotSizePx)
                                val snappedY = rawY - (rawY % dotSizePx)
                                drawCircle(
                                    color = Color.White.copy(alpha = p.alpha * bgAlpha),
                                    radius = (dotSizePx * 0.45f),
                                    center = Offset(snappedX + dotSizePx / 2f, snappedY + dotSizePx / 2f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
