package app.gamenative.ui.screen.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.enums.DownloadBarStyle
import app.gamenative.enums.FontType
import app.gamenative.enums.GlassMode
import app.gamenative.ui.component.HSVColorPicker
import app.gamenative.ui.component.settings.HapticSettingsSwitch
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.ui.theme.toColor
import app.gamenative.ui.theme.toHex
import app.gamenative.ui.util.SnackbarManager
import com.alorma.compose.settings.ui.SettingsGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsGroupLooks(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                        val fileSize = descriptor?.length ?: 0L
                        descriptor?.close()

                        if (fileSize > 50 * 1024 * 1024) {
                            return@withContext "File size must be under 50MB"
                        }

                        val backgroundsDir = File(context.filesDir, "backgrounds")
                        if (!backgroundsDir.exists()) backgroundsDir.mkdirs()

                        val fileName = "custom_bg_${System.currentTimeMillis()}.mp4"
                        val destFile = File(backgroundsDir, fileName)

                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        destFile.absolutePath
                    } catch (e: Exception) {
                        null
                    }
                }

                if (result == "File size must be under 50MB") {
                    SnackbarManager.show(result)
                } else if (result != null) {
                    viewModel.setVideoBackgroundPath(result)
                } else {
                    SnackbarManager.show("Failed to import video")
                }
            }
        }
    }

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
                    // Removed Standard and True Glass modes - Liquid is sole renderer
                    val contentColor = glassContentColor()

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Liquid Distortion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
                        )
                        Slider(
                            value = state.liquidDistortion,
                            onValueChange = { viewModel.setLiquidDistortion(it) },
                            valueRange = 0f..1f
                        )
                        Text(
                            text = String.format(Locale.US, "%.1fx", 1f + state.liquidDistortion * 9f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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

        val downloadBarStyleOptions = DownloadBarStyle.entries.map { stringResource(it.labelResId) }

        SettingsListDropdown(
            colors = settingsTileColorsAlt(),
            value = state.downloadBarStyle.ordinal,
            items = downloadBarStyleOptions,
            onItemSelected = { index ->
                viewModel.setDownloadBarStyle(DownloadBarStyle.entries[index])
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

        val contentColor = glassContentColor()

        // Video Background Option
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { videoPickerLauncher.launch("video/*") }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Add your own ...",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = if (state.videoBackgroundPath.isNotEmpty()) {
                    "Custom video applied: ${File(state.videoBackgroundPath).name}"
                } else {
                    "Select a video as a live wallpaper"
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.6f)
            )

            if (state.videoBackgroundPath.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap here to remove video background",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        viewModel.setVideoBackgroundPath("")
                    }
                )
            }
        }

        // Grayness Scale Slider
        if (state.videoBackgroundPath.isNotEmpty()) {
            HapticSettingsSwitch(
                colors = settingsTileColorsAlt(),
                state = PrefManager.autoExtractWallpaperColor,
                title = { Text(text = "Auto-extract Theme Color") },
                subtitle = { Text(text = "Extract main theme color from video wallpaper") },
                onCheckedChange = {
                    PrefManager.autoExtractWallpaperColor = it
                },
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Grayness Scale",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                Slider(
                    value = state.videoBackgroundDimAlpha,
                    onValueChange = { viewModel.setVideoBackgroundDimAlpha(it) },
                    valueRange = 0f..0.9f
                )
                Text(
                    text = "${(state.videoBackgroundDimAlpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ColorSelectionRow(
    title: String,
    currentColor: String,
    onColorSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val contentColor = glassContentColor()

    if (showPicker) {
        HSVColorPicker(
            initialColor = currentColor.toColor(),
            onColorSelected = { onColorSelected(it.toHex()) },
            onDismiss = { showPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
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
    }
}
