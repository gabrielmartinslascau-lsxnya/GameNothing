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
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.ui.theme.toColor
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch
import kotlin.math.roundToInt

@Composable
fun SettingsGroupLooks(viewModel: MainViewModel) {
    SettingsGroup {
        var jigglyAnimations by rememberSaveable { mutableStateOf(PrefManager.jigglyAnimations) }
        var glassPanels by rememberSaveable { mutableStateOf(PrefManager.glassPanels) }

        SettingsSwitch(
            colors = settingsTileColors(),
            state = jigglyAnimations,
            title = { Text(text = stringResource(R.string.settings_looks_jiggly_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_looks_jiggly_subtitle)) },
            onCheckedChange = {
                jigglyAnimations = it
                PrefManager.jigglyAnimations = it
            },
        )

        Column {
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                state = glassPanels,
                title = { Text(text = stringResource(R.string.settings_looks_glass_title)) },
                subtitle = { Text(text = stringResource(R.string.settings_looks_glass_subtitle)) },
                onCheckedChange = {
                    glassPanels = it
                    PrefManager.glassPanels = it
                },
            )

            AnimatedVisibility(
                visible = glassPanels,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                var transparency by remember { mutableFloatStateOf(PrefManager.glassTransparency) }
                val contentColor = glassContentColor()

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Glass Transparency",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                    Slider(
                        value = transparency,
                        onValueChange = { transparency = it },
                        onValueChangeFinished = { PrefManager.glassTransparency = transparency },
                        valueRange = 0.05f..0.8f
                    )
                    Text(
                        text = "${(transparency * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val state by viewModel.state.collectAsStateWithLifecycle()

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
        "#4CAF50" to "Forest",
        "#2196F3" to "Ice",
        "#9C27B0" to "Violet",
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
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                colors.forEach { (hex, name) ->
                    val isSelected = hex == currentColor
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onColorSelected(hex) }
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
