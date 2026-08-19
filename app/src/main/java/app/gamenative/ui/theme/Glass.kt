package app.gamenative.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager

/**
 * A modifier that applies a "Frosted Glass" look.
 */
@Composable
fun Modifier.pluviaGlass(
    enabled: Boolean = PrefManager.glassPanels,
    cornerRadius: Int = 16
): Modifier {
    if (!enabled) return this

    val shape = RoundedCornerShape(cornerRadius.dp)
    val bgAlpha = PrefManager.glassTransparency

    return this
        .shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.1f),
            spotColor = Color.Black.copy(alpha = 0.1f)
        )
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = bgAlpha),
                    Color.White.copy(alpha = (bgAlpha * 0.8f).coerceIn(0f, 1f))
                )
            ),
            shape = shape
        )
        .border(
            width = 1.dp,
            color = Color.White.copy(alpha = (bgAlpha + 0.1f).coerceIn(0f, 1f)),
            shape = shape
        )
}

/**
 * Returns the appropriate content color for glass panels.
 */
@Composable
fun glassContentColor(enabled: Boolean = PrefManager.glassPanels): Color {
    return Color.White
}
