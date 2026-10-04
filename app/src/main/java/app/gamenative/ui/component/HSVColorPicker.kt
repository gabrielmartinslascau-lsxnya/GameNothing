package app.gamenative.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.gamenative.ui.theme.toHex

@Composable
fun HSVColorPicker(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var hsv by remember {
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.argb(
            (initialColor.alpha * 255).toInt(),
            (initialColor.red * 255).toInt(),
            (initialColor.green * 255).toInt(),
            (initialColor.blue * 255).toInt()
        ), hsvArr)
        mutableStateOf(Triple(hsvArr[0], hsvArr[1], hsvArr[2]))
    }

    val currentColor = Color.hsv(hsv.first, hsv.second, hsv.third)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentColor)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Selected Color", style = MaterialTheme.typography.labelMedium)
                        Text(currentColor.toHex(), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Saturation/Value Pad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(hsv.first) {
                            detectDragGestures { change, _ ->
                                val s = (change.position.x / size.width).coerceIn(0f, 1f)
                                val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                hsv = Triple(hsv.first, s, v)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val saturationGradient = Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.first, 1f, 1f)))
                        drawRect(saturationGradient)
                        val valueGradient = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                        drawRect(valueGradient)

                        // Selector
                        val x = hsv.second * size.width
                        val y = (1f - hsv.third) * size.height
                        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
                        drawCircle(Color.Black, radius = 9.dp.toPx(), center = Offset(x, y), style = Stroke(width = 1.dp.toPx()))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Hue Slider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val h = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                                hsv = Triple(h, hsv.second, hsv.third)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val hueColors = (0..360 step 60).map { Color.hsv(it.toFloat(), 1f, 1f) }
                        drawRect(Brush.horizontalGradient(hueColors))

                        // Selector
                        val x = (hsv.first / 360f) * size.width
                        drawRect(Color.White, topLeft = Offset(x - 2.dp.toPx(), 0f), size = Size(4.dp.toPx(), size.height))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onColorSelected(currentColor)
                        onDismiss()
                    }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
