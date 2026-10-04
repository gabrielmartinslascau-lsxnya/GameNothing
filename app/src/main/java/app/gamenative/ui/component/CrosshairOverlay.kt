package app.gamenative.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box

@Composable
fun BoxScope.CrosshairOverlay() {
    if (!PrefManager.crosshairEnabled) return

    Canvas(modifier = Modifier.fillMaxSize().align(Alignment.Center)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val length = 12.dp.toPx()
        val thickness = 2.dp.toPx()

        // Horizontal line
        drawLine(
            color = Color.Green,
            start = Offset(centerX - length / 2, centerY),
            end = Offset(centerX + length / 2, centerY),
            strokeWidth = thickness
        )

        // Vertical line
        drawLine(
            color = Color.Green,
            start = Offset(centerX, centerY - length / 2),
            end = Offset(centerX, centerY + length / 2),
            strokeWidth = thickness
        )

        // Center dot
        drawCircle(
            color = Color.Green,
            radius = 1.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun Preview_Crosshair() {
    Box(modifier = Modifier.size(100.dp)) {
        PrefManager.crosshairEnabled = true
        CrosshairOverlay()
    }
}
