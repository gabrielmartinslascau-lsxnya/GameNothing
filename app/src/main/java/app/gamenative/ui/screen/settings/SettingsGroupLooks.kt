package app.gamenative.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.enums.FontType
import app.gamenative.ui.component.settings.HapticSettingsSwitch
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.ui.theme.toColor
import com.alorma.compose.settings.ui.SettingsGroup
import kotlin.math.roundToInt

@Composable
fun SettingsGroupLooks(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsGroup {
        HapticSettingsSwitch(
            colors = settingsTileColors(),
            state = state.jigglyAnimations,
            title = { Text(text = stringResource(R.string.settings_looks_jiggly_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_looks_jiggly_subtitle)) },
            onCheckedChange = {
                viewModel.setJigglyAnimations(it)
            },
        )

        Column {
            HapticSettingsSwitch(
                colors = settingsTileColorsAlt(),
                state = state.glassPanels,
                title = { Text(text = stringResource(R.string.settings_looks_glass_title)) },
                subtitle = { Text(text = stringResource(R.string.settings_looks_glass_subtitle)) },
                onCheckedChange = {
                    viewModel.setGlassPanels(it)
                },
            )

            AnimatedVisibility(
                visible = state.glassPanels,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HapticSettingsSwitch(
                        colors = settingsTileColorsAlt(),
                        state = state.trueGlassEnabled,
                        title = { Text(text = stringResource(R.string.settings_looks_true_glass_title)) },
                        subtitle = { Text(text = stringResource(R.string.settings_looks_true_glass_subtitle)) },
                        onCheckedChange = {
                            viewModel.setTrueGlassEnabled(it)
                        },
                    )

                    val contentColor = glassContentColor()

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = if (state.trueGlassEnabled) "Blur Intensiveness" else "Glass Transparency",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
                        )
                        if (state.trueGlassEnabled) {
                            Slider(
                                value = state.glassBlurIntensity,
                                onValueChange = { viewModel.setGlassBlurIntensity(it) },
                                valueRange = 5f..50f
                            )
                            Text(
                                text = "${state.glassBlurIntensity.roundToInt()}px",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Slider(
                                value = state.glassTransparency,
                                onValueChange = { viewModel.setGlassTransparency(it) },
                                valueRange = 0.05f..0.8f
                            )
                            Text(
                                text = "${(state.glassTransparency * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    ColorSelectionRow(
                        title = "Glass Colour",
                        currentColor = state.glassColor,
                        onColorSelected = {
                            viewModel.setGlassColor(it)
                        }
                    )
                }
            }
        }

        val fontOptions = FontType.entries.map { stringResource(it.labelResId) }

        SettingsListDropdown(
            colors = settingsTileColorsAlt(),
            value = state.fontType.ordinal,
            items = fontOptions,
            onItemSelected = { index ->
                viewModel.setFontType(FontType.entries[index])
            },
            title = { Text(text = stringResource(R.string.settings_looks_font_type_title)) }
        )

        val downloadBarStyleOptions = app.gamenative.enums.DownloadBarStyle.entries.map { stringResource(it.labelResId) }

        SettingsListDropdown(
            colors = settingsTileColorsAlt(),
            value = state.downloadBarStyle.ordinal,
            items = downloadBarStyleOptions,
            onItemSelected = { index ->
                viewModel.setDownloadBarStyle(app.gamenative.enums.DownloadBarStyle.entries[index])
            },
            title = { Text(text = stringResource(R.string.settings_looks_download_bar_style_title)) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ColorSelectionRow(
            title = "Main Colour",
            currentColor = state.mainColor,
            onColorSelected = {
                viewModel.setMainColor(it)
            }
        )

        ColorSelectionRow(
            title = "Wallpaper Colour",
            currentColor = state.wallpaperColor,
            onColorSelected = {
                viewModel.setWallpaperColor(it)
            }
        )
    }
}

@Composable
private fun ColorSelectionRow(
    title: String,
    currentColor: String,
    onColorSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val contentColor = glassContentColor()
    val colors = listOf(
        "#E91E63" to "Cherry",
        "#FF9800" to "Orange",
        "#4CAF50" to "Forest",
        "#00BCD4" to "Cyan",
        "#2196F3" to "Ice",
        "#9C27B0" to "Violet",
        "#9E9E9E" to "Steel",
        "#607D8B" to "Slate",
        "#FFFFFF" to "Frost",
        "#000000" to "Volcanic"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(currentColor.toColor())
                    .border(1.dp, contentColor.copy(alpha = 0.2f), CircleShape)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Two rows of colors
                val chunkedColors = colors.chunked(5)
                chunkedColors.forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowColors.forEach { (hex, name) ->
                            val isSelected = hex == currentColor
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onColorSelected(hex) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(hex.toColor())
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = if (isSelected) 1f else 0.6f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                        // Fill empty spaces in the last row if it's shorter
                        if (rowColors.size < 5) {
                            repeat(5 - rowColors.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
