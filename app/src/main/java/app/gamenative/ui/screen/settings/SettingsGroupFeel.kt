package app.gamenative.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import app.gamenative.PrefManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import app.gamenative.R
import app.gamenative.ui.component.settings.HapticSettingsSwitch
import app.gamenative.ui.component.settings.HapticSettingsMenuLink
import app.gamenative.ui.components.rememberCustomGameFolderPicker
import app.gamenative.ui.components.requestPermissionsForPath
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.utils.HapticManager
import app.gamenative.utils.MusicManager
import com.alorma.compose.settings.ui.SettingsGroup

@Composable
fun SettingsGroupFeel() {
    val androidContext = LocalContext.current
    var hapticsEnabled by rememberSaveable { mutableStateOf(PrefManager.hapticsEnabled) }
    var hapticIntensity by rememberSaveable { mutableStateOf(PrefManager.hapticIntensity) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    val musicFolderPicker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            requestPermissionsForPath(androidContext, path, storagePermissionLauncher)
            PrefManager.musicFolderPath = path
            MusicManager.loadFolder(path)
        }
    )

    SettingsGroup {
        HapticSettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = hapticsEnabled,
            title = { Text(text = stringResource(R.string.settings_feel_haptics_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_feel_haptics_subtitle)) },
            onCheckedChange = {
                hapticsEnabled = it
                PrefManager.hapticsEnabled = it
            },
        )

        AnimatedVisibility(
            visible = hapticsEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val contentColor = glassContentColor()
                Text(
                    text = stringResource(R.string.settings_feel_intensity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IntensityButton(
                        lines = 1,
                        selected = hapticIntensity == 1,
                        onClick = {
                            hapticIntensity = 1
                            PrefManager.hapticIntensity = 1
                            HapticManager.trigger(HapticManager.HapticType.LONG, 1)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IntensityButton(
                        lines = 2,
                        selected = hapticIntensity == 2,
                        onClick = {
                            hapticIntensity = 2
                            PrefManager.hapticIntensity = 2
                            HapticManager.trigger(HapticManager.HapticType.LONG, 2)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IntensityButton(
                        lines = 3,
                        selected = hapticIntensity == 3,
                        onClick = {
                            hapticIntensity = 3
                            PrefManager.hapticIntensity = 3
                            HapticManager.trigger(HapticManager.HapticType.LONG, 3)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Music Player Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Music Player",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        var musicPlayerEnabled by remember { mutableStateOf(PrefManager.musicPlayerEnabled) }
        HapticSettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = musicPlayerEnabled,
            title = { Text(text = "Show Music Player Bubble") },
            subtitle = { Text(text = "Floating collapsible player for your music") },
            onCheckedChange = {
                musicPlayerEnabled = it
                PrefManager.musicPlayerEnabled = it
            },
        )

        HapticSettingsMenuLink(
            colors = settingsTileColorsAlt(),
            title = { Text("Music Folder") },
            subtitle = {
                Text(
                    if (PrefManager.musicFolderPath.isNotEmpty()) PrefManager.musicFolderPath
                    else "Choose a folder with audio files"
                )
            },
            onClick = { musicFolderPicker.launchPicker() }
        )

        if (MusicManager.playlist.isNotEmpty()) {
            Text(
                text = "${MusicManager.playlist.size} tracks found",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun IntensityButton(
    lines: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = glassContentColor()
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.2f)
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable {
                HapticManager.trigger(HapticManager.HapticType.CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(lines) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else contentColor)
                )
            }
        }
    }
}
