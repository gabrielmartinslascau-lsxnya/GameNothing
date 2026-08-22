package app.gamenative.ui.screen.downloads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import kotlin.math.sin

@Composable
fun NothingDownloadBar(
    progress: Float,
    downloadSpeedText: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF8B5CF6), // Violet default
) {
    val ndotFont = FontFamily(Font(R.font.ndot_47_inspired_by_nothing))

    val infiniteTransition = rememberInfiniteTransition(label = "NothingDownloadBarTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val cornerRadius = 12.dp.toPx()

            // 1. Outer Container: Rounded rectangle outline
            drawRoundRect(
                color = accentColor,
                style = Stroke(width = strokeWidth),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // 3. Dot Stream Drawing
            val dotSpacing = 16.dp.toPx()
            val progressWidth = size.width * progress.coerceIn(0f, 1f)

            // Dot stream with horizontal positions spaced evenly
            val dotCount = (size.width / dotSpacing).toInt() + 2
            for (i in -1 until dotCount) {
                // Offset x-coordinates using phase for flow effect
                val x = (i + phase) * dotSpacing

                // Don't draw outside the container
                if (x < strokeWidth || x > size.width - strokeWidth) continue

                // Dynamically scale dot's radius based on position (sine wave)
                // Range: 2.dp to 7.dp
                // Reversed phase to propel wave from LEFT to RIGHT
                val scaleFactor = (sin(i * 0.5f - phase * Math.PI * 2).toFloat() + 1f) / 2f
                val dotRadius = 2.dp.toPx() + (5.dp.toPx() * scaleFactor)

                // Dots <= progressWidth are full opacity, others are 20% alpha
                val alpha = if (x <= progressWidth) 1f else 0.2f

                drawCircle(
                    color = accentColor.copy(alpha = alpha),
                    radius = dotRadius,
                    center = Offset(x, size.height / 2f)
                )
            }
        }

        // 4. Text Overlay: downloadSpeedText in Ndot font
        if (downloadSpeedText.isNotEmpty()) {
            Text(
                text = downloadSpeedText,
                color = Color.White,
                fontFamily = ndotFont,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun PreviewNothingDownloadBar() {
    androidx.compose.material3.Surface(
        color = Color(0xFF121212),
        modifier = Modifier.padding(16.dp)
    ) {
        NothingDownloadBar(
            progress = 0.65f,
            downloadSpeedText = "14.2 MB/s",
            accentColor = Color(0xFF8B5CF6),
            modifier = Modifier.height(48.dp)
        )
    }
}
