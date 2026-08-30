package app.gamenative.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.PrefManager
import app.gamenative.ui.component.settings.HapticSettingsSwitch
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.utils.HapticManager
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch
import kotlin.math.roundToInt

import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Locale

@Composable
fun SettingsGroupExperimental(viewModel: MainViewModel) {
    SettingsGroup {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current

        var aggressiveCaching by remember { mutableStateOf(PrefManager.aggressiveCaching) }
        var ramViewerEnabled by remember { mutableStateOf(PrefManager.ramViewerEnabled) }
        var cacheSizeMb by remember { mutableDoubleStateOf(0.0) }

        LaunchedEffect(Unit) {
            val totalSize = calculateDirSize(context.cacheDir) +
                          calculateDirSize(File(context.filesDir, "image_cache")) +
                          calculateDirSize(context.codeCacheDir)
            cacheSizeMb = totalSize / (1024.0 * 1024.0)
        }

        HapticSettingsSwitch(
            colors = settingsTileColors(),
            state = aggressiveCaching,
            title = { Text(text = "Aggressive Caching") },
            subtitle = {
                Column {
                    Text(text = "Speed up loading times by never clearing the app cache.")
                    Text(
                        text = String.format(Locale.US, "Current Cache: %.1f MB", cacheSizeMb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
            },
            onCheckedChange = {
                aggressiveCaching = it
                PrefManager.aggressiveCaching = it
            }
        )

        HapticSettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = ramViewerEnabled,
            title = { Text(text = "RAM Viewer") },
            subtitle = { Text(text = "Show real-time RAM usage in a small overlay.") },
            onCheckedChange = {
                ramViewerEnabled = it
                PrefManager.ramViewerEnabled = it
            },
        )

        Column {
            var refreshRateEnabled by remember { mutableStateOf(PrefManager.refreshRateEnabled) }

            HapticSettingsSwitch(
                colors = settingsTileColors(),
                state = refreshRateEnabled,
                title = { Text(text = "More Hz!") },
                subtitle = { Text(text = "More, more, more! Extra fluidity on everything, but at the cost of some battery life.") },
                onCheckedChange = {
                    refreshRateEnabled = it
                    PrefManager.refreshRateEnabled = it
                    // Reset to 60 if disabled
                    if (!it) {
                        viewModel.setMaxRefreshRate(60)
                    }
                },
            )

            AnimatedVisibility(
                visible = refreshRateEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                // Discrete Hz Options: 30, 60, 90, 120, 144
                val hzOptions = listOf(30, 60, 90, 120, 144)
                val currentHz = state.maxRefreshRate
                val initialIndex = hzOptions.indexOf(currentHz).coerceAtLeast(0).toFloat()

                var sliderIndex by remember(currentHz) { mutableFloatStateOf(initialIndex) }
                val animatedIndex by animateFloatAsState(
                    targetValue = sliderIndex,
                    label = "hzSliderAnimation"
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Slider(
                        value = animatedIndex,
                        onValueChange = {
                            sliderIndex = it
                            val index = it.roundToInt()
                            viewModel.setMaxRefreshRate(hzOptions[index])
                        },
                        onValueChangeFinished = {
                            HapticManager.trigger(HapticManager.HapticType.CLICK)
                        },
                        valueRange = 0f..(hzOptions.size - 1).toFloat(),
                        steps = hzOptions.size - 2,
                        colors = SliderDefaults.colors(
                            activeTickColor = Color.White.copy(alpha = 0.5f),
                            inactiveTickColor = Color.Black.copy(alpha = 0.2f),
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Text(
                        text = "${hzOptions[sliderIndex.roundToInt()]} Hz",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun calculateDirSize(dir: File): Long {
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) calculateDirSize(file) else file.length()
    }
    return size
}
