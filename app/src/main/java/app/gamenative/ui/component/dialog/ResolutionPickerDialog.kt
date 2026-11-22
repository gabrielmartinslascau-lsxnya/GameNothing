package app.gamenative.ui.component.dialog

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.theme.BrokenGlassDialogSurface
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.glassContentColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ResolutionAspectRatio(val label: String) {
    SCREEN_RATIO("Screen Ratio"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
}

private const val MAX_RESOLUTION_DIMENSION = 16384

private fun roundToEven(value: Double): Int {
    val rounded = (value / 2.0).roundToInt() * 2
    return rounded.coerceIn(2, MAX_RESOLUTION_DIMENSION)
}

private fun roundToEven(value: Int): Int {
    return (value and 1.inv()).coerceIn(2, MAX_RESOLUTION_DIMENSION)
}

private fun detectDeviceLandscapeResolution(context: Context): Pair<Int, Int> {
    val native = PluviaApp.getNativeDisplaySize()
    if (!native.isNullOrBlank()) {
        val parts = native.split("x", ignoreCase = true)
        val w = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val h = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (w != null && h != null && w > 0 && h > 0) {
            return max(w, h) to min(w, h)
        }
    }
    val dm = context.resources.displayMetrics
    if (dm.widthPixels > 0 && dm.heightPixels > 0) {
        return max(dm.widthPixels, dm.heightPixels) to min(dm.widthPixels, dm.heightPixels)
    }
    return 1920 to 1080
}

private fun baseSizeFor(
    ratio: ResolutionAspectRatio,
    maxScreenX: Int,
    maxScreenY: Int,
): Pair<Int, Int> {
    return when (ratio) {
        ResolutionAspectRatio.SCREEN_RATIO -> roundToEven(maxScreenX) to roundToEven(maxScreenY)
        ResolutionAspectRatio.RATIO_16_9 -> roundToEven(maxScreenY * 16.0 / 9.0) to roundToEven(maxScreenY)
        ResolutionAspectRatio.RATIO_4_3 -> roundToEven(maxScreenY * 4.0 / 3.0) to roundToEven(maxScreenY)
    }
}

private fun activeRatioValue(
    ratio: ResolutionAspectRatio,
    maxScreenX: Int,
    maxScreenY: Int,
): Double {
    return when (ratio) {
        ResolutionAspectRatio.SCREEN_RATIO -> maxScreenX.toDouble() / maxScreenY.toDouble().coerceAtLeast(1.0)
        ResolutionAspectRatio.RATIO_16_9 -> 16.0 / 9.0
        ResolutionAspectRatio.RATIO_4_3 -> 4.0 / 3.0
    }
}

private fun inferInitialAspectRatio(
    width: Int,
    height: Int,
    maxScreenX: Int,
    maxScreenY: Int,
): ResolutionAspectRatio {
    if (width <= 0 || height <= 0) return ResolutionAspectRatio.SCREEN_RATIO
    val currentRatio = width.toDouble() / height.toDouble()
    val nativeRatio = maxScreenX.toDouble() / maxScreenY.toDouble().coerceAtLeast(1.0)
    val candidates = listOf(
        ResolutionAspectRatio.SCREEN_RATIO to abs(currentRatio - nativeRatio),
        ResolutionAspectRatio.RATIO_16_9 to abs(currentRatio - (16.0 / 9.0)),
        ResolutionAspectRatio.RATIO_4_3 to abs(currentRatio - (4.0 / 3.0)),
    )
    return candidates.minByOrNull { it.second }?.first ?: ResolutionAspectRatio.SCREEN_RATIO
}

@Composable
fun ResolutionPickerDialog(
    currentScreenSize: String,
    nonzeroResolutionError: String,
    onDismissRequest: () -> Unit,
    onApply: (String) -> Unit,
) {
    val context = LocalContext.current
    val (maxScreenX, maxScreenY) = remember(context) { detectDeviceLandscapeResolution(context) }

    val initialParts = remember(currentScreenSize) {
        val parts = currentScreenSize.split("x", ignoreCase = true)
        val w = parts.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 1280
        val h = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 720
        w to h
    }

    var selectedRatio by remember(currentScreenSize, maxScreenX, maxScreenY) {
        mutableStateOf(
            inferInitialAspectRatio(
                width = initialParts.first,
                height = initialParts.second,
                maxScreenX = maxScreenX,
                maxScreenY = maxScreenY,
            ),
        )
    }
    var scaleFloat by remember(currentScreenSize, maxScreenY) {
        mutableFloatStateOf(
            ((initialParts.second.toFloat() / maxScreenY.toFloat()) * 100f).coerceIn(1f, 100f),
        )
    }
    var widthText by remember(currentScreenSize) { mutableStateOf(initialParts.first.toString()) }
    var heightText by remember(currentScreenSize) { mutableStateOf(initialParts.second.toString()) }
    var autoCalculateRatio by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val heightFocusRequester = remember { FocusRequester() }
    val scalePresets = remember { listOf(100, 75, 50, 25) }

    fun applyScaleAndRatio(scalePercent: Int, ratio: ResolutionAspectRatio) {
        val clampedScale = scalePercent.coerceIn(1, 100)
        val (baseX, baseY) = baseSizeFor(ratio, maxScreenX, maxScreenY)
        val factor = clampedScale / 100.0
        val newX = roundToEven(baseX * factor)
        val newY = roundToEven(baseY * factor)
        widthText = newX.toString()
        heightText = newY.toString()
        validationError = null
    }

    val isGlass = LocalLooksState.current.isGlassEnabled
    val contentColor = if (isGlass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f)
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            BrokenGlassDialogSurface(
                cornerRadius = 26,
                modifier = Modifier
                    .zIndex(1000f)
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 540.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                alpha = if (isGlass) 0.78f else 1f,
                            ),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header + Detected Native Device Dimensions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.screen_size),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                            Text(
                                text = "Device Panel: $maxScreenX × $maxScreenY",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.72f),
                            )
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = contentColor,
                            )
                        }
                    }

                    HorizontalDivider(color = contentColor.copy(alpha = 0.14f))

                    // 1. Aspect Ratio 3-way hard toggle
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Aspect Ratio",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ResolutionAspectRatio.entries.forEach { ratio ->
                                val selected = selectedRatio == ratio
                                OutlinedButton(
                                    onClick = {
                                        selectedRatio = ratio
                                        applyScaleAndRatio(scaleFloat.roundToInt(), ratio)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) primaryColor else contentColor.copy(alpha = 0.25f),
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) {
                                            primaryColor.copy(alpha = 0.22f)
                                        } else {
                                            Color.Transparent
                                        },
                                        contentColor = if (selected) primaryColor else contentColor,
                                    ),
                                ) {
                                    Text(
                                        text = ratio.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }

                    // 2. Scale Presets (100%, 75%, 50%, 25%) + Smooth 1%-100% Slider
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val currentScaleInt = scaleFloat.roundToInt().coerceIn(1, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Resolution Scale",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                            )
                            Text(
                                text = "$currentScaleInt%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            scalePresets.forEach { preset ->
                                val selected = currentScaleInt == preset
                                OutlinedButton(
                                    onClick = {
                                        scaleFloat = preset.toFloat()
                                        applyScaleAndRatio(preset, selectedRatio)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) primaryColor else contentColor.copy(alpha = 0.25f),
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) {
                                            primaryColor.copy(alpha = 0.22f)
                                        } else {
                                            Color.Transparent
                                        },
                                        contentColor = if (selected) primaryColor else contentColor,
                                    ),
                                ) {
                                    Text(
                                        text = "$preset%",
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                        Slider(
                            value = scaleFloat,
                            onValueChange = { newValue ->
                                scaleFloat = newValue.coerceIn(1f, 100f)
                                applyScaleAndRatio(scaleFloat.roundToInt(), selectedRatio)
                            },
                            valueRange = 1f..100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 3. Manual X and Y Pixel Inputs + Auto-Calculate Ratio Toggle
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NoExtractOutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = widthText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(5)
                                    widthText = digits
                                    validationError = null
                                    if (autoCalculateRatio) {
                                        val parsedW = digits.toIntOrNull()
                                        if (parsedW != null && parsedW > 0) {
                                            val ratio = activeRatioValue(selectedRatio, maxScreenX, maxScreenY)
                                            val computedY = roundToEven(parsedW / ratio)
                                            heightText = computedY.toString()
                                            val (_, baseY) = baseSizeFor(selectedRatio, maxScreenX, maxScreenY)
                                            scaleFloat = ((computedY.toFloat() / baseY.toFloat()) * 100f)
                                                .coerceIn(1f, 100f)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { heightFocusRequester.requestFocus() },
                                ),
                                label = { Text(text = "${stringResource(R.string.width)} (X)") },
                                singleLine = true,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.container_config_custom_resolution_separator),
                                style = MaterialTheme.typography.titleMedium,
                                color = contentColor,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            NoExtractOutlinedTextField(
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(heightFocusRequester),
                                value = heightText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(5)
                                    heightText = digits
                                    validationError = null
                                    if (autoCalculateRatio) {
                                        val parsedH = digits.toIntOrNull()
                                        if (parsedH != null && parsedH > 0) {
                                            val ratio = activeRatioValue(selectedRatio, maxScreenX, maxScreenY)
                                            val computedX = roundToEven(parsedH * ratio)
                                            widthText = computedX.toString()
                                            val (_, baseY) = baseSizeFor(selectedRatio, maxScreenX, maxScreenY)
                                            scaleFloat = ((parsedH.toFloat() / baseY.toFloat()) * 100f)
                                                .coerceIn(1f, 100f)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                label = { Text(text = "${stringResource(R.string.height)} (Y)") },
                                singleLine = true,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !autoCalculateRatio
                                    autoCalculateRatio = next
                                    if (next) {
                                        val parsedH = heightText.toIntOrNull()
                                        if (parsedH != null && parsedH > 0) {
                                            val ratio = activeRatioValue(selectedRatio, maxScreenX, maxScreenY)
                                            val computedX = roundToEven(parsedH * ratio)
                                            widthText = computedX.toString()
                                            heightText = roundToEven(parsedH).toString()
                                            val (_, baseY) = baseSizeFor(selectedRatio, maxScreenX, maxScreenY)
                                            scaleFloat = ((parsedH.toFloat() / baseY.toFloat()) * 100f)
                                                .coerceIn(1f, 100f)
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Auto-Calculate Ratio",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = contentColor,
                                )
                                Text(
                                    text = if (autoCalculateRatio) {
                                        "Locked to ${selectedRatio.label} when editing X or Y"
                                    } else {
                                        "Edit X and Y pixel dimensions independently"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.7f),
                                )
                            }
                            Switch(
                                checked = autoCalculateRatio,
                                onCheckedChange = { checked ->
                                    autoCalculateRatio = checked
                                    if (checked) {
                                        val parsedH = heightText.toIntOrNull()
                                        if (parsedH != null && parsedH > 0) {
                                            val ratio = activeRatioValue(selectedRatio, maxScreenX, maxScreenY)
                                            val computedX = roundToEven(parsedH * ratio)
                                            widthText = computedX.toString()
                                            heightText = roundToEven(parsedH).toString()
                                            val (_, baseY) = baseSizeFor(selectedRatio, maxScreenX, maxScreenY)
                                            scaleFloat = ((parsedH.toFloat() / baseY.toFloat()) * 100f)
                                                .coerceIn(1f, 100f)
                                        }
                                    }
                                },
                            )
                        }

                        if (validationError != null) {
                            Text(
                                text = validationError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    HorizontalDivider(color = contentColor.copy(alpha = 0.14f))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val parsedW = widthText.toIntOrNull() ?: 0
                                val parsedH = heightText.toIntOrNull() ?: 0
                                if (parsedW <= 0 || parsedH <= 0) {
                                    validationError = nonzeroResolutionError
                                } else {
                                    val finalW = roundToEven(parsedW)
                                    val finalH = roundToEven(parsedH)
                                    onApply("${finalW}x${finalH}")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(text = stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }
}
