package app.gamenative.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.theme.PluviaGlassPanel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.toColor
import app.gamenative.ui.component.settings.HapticSettingsSwitch
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.utils.HapticManager

@Composable
fun TutorialScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onComplete) {
                    Text("Skip Tutorial", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Content
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "tutorial_step"
                ) { page ->
                    when (page) {
                        0 -> TutorialPage(
                            icon = Icons.Default.Settings,
                            title = "Settings Menu",
                            description = "You can open the settings menu at any time by tapping the hamburger menu icon on the top right of the library screen, or by pressing the START button on your controller."
                        )
                        1 -> GlassCustomizationPage(
                            viewModel = viewModel,
                            mainColor = state.mainColor,
                            wallpaperColor = state.wallpaperColor,
                            glassPanels = state.glassPanels
                        )
                        2 -> TutorialPage(
                            icon = Icons.Default.Speed,
                            title = "Performance Tips",
                            description = "For the most reliable experience across most games, we recommend using Proton 11 ARM + DXVK 2.7.1 GPLAsync + 2GB VRAM + FEX 2608. You can configure these in the Container Settings for each game."
                        )
                        3 -> PerformanceTuningPage()
                        4 -> RealityCheckPage()
                        5 -> TutorialPage(
                            icon = Icons.Default.AutoFixHigh,
                            title = "Cloud Sync",
                            description = "Your game saves are automatically synced to the cloud whenever you close a game. This ensures your progress is safe across all your devices."
                        )
                        6 -> TutorialPage(
                            icon = Icons.Default.Settings,
                            title = "Sidebar Features",
                            description = "While in-game, you can access the Sidebar to adjust the Refresh Rate and monitor your device's heat. These standalone drawers allow for quick tuning without leaving your game."
                        )
                        7 -> TutorialPage(
                            icon = Icons.Default.Settings,
                            title = "Community Configs",
                            description = "Can't get a game to work? Check the Community Configs button on the game detail page. Other users have shared their best-working settings for your specific hardware."
                        )
                    }
                }
            }

            // Footer Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                if (currentPage > 0) {
                    Button(
                        onClick = { currentPage-- },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(100.dp))
                }

                // Page Indicator
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(8) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (currentPage == index) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f))
                        )
                    }
                }

                // Next/Get Started Button
                Button(
                    onClick = {
                        if (currentPage < 7) currentPage++ else onComplete()
                    }
                ) {
                    Text(if (currentPage < 7) "Next" else "Get Started")
                    if (currentPage < 7) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialPage(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PluviaGlassPanel(
            modifier = Modifier.size(120.dp),
            cornerRadius = 24
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun GlassCustomizationPage(
    viewModel: MainViewModel,
    mainColor: String,
    wallpaperColor: String,
    glassPanels: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PluviaGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 24.dp),
            cornerRadius = 20
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Glass Panels",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Interactive Customization",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HapticSettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = glassPanels,
            title = { Text(text = "Glass Panels", color = Color.White) },
            subtitle = { Text(text = "Enable frosted glass effects", color = Color.White.copy(alpha = 0.6f)) },
            onCheckedChange = {
                viewModel.setGlassPanels(it)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your App, Your Style",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Pick your favorite colors for the accent and background. The glass will update in real-time!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Two-row Color Picker with 10 Colors
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

        Text("Main Colour", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val chunkedMain = colors.chunked(5)
            chunkedMain.forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowColors.forEach { (hex, _) ->
                        ColorPill(
                            hex = hex,
                            selected = hex == mainColor,
                            onClick = {
                                HapticManager.trigger(HapticManager.HapticType.CLICK)
                                viewModel.setMainColor(hex)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Wallpaper Colour", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val chunkedWallpaper = colors.chunked(5)
            chunkedWallpaper.forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowColors.forEach { (hex, _) ->
                        ColorPill(
                            hex = hex,
                            selected = hex == wallpaperColor,
                            onClick = {
                                HapticManager.trigger(HapticManager.HapticType.CLICK)
                                viewModel.setWallpaperColor(hex)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceTuningPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Performance Tuning",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Finding the right balance between battery and performance is key. Here is your cheat sheet:",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        val tuningInfo = listOf(
            "Ultra-Eco (3 Cores)" to "Either you're desperate to save battery or you genuinely don't care about FPS. Built for basic 2D pixel games or emergency survival when you're on 5%.",
            "Eco (4 Cores)" to "You're trying to save battery, but you still care about playability. Keeps the phone cool and gives you decent framerates in lighter games.",
            "Balanced (6 Cores)" to "It's balanced, what can I say? Turns off the prime core so you can jump from 480p to 720p without killing your battery life.",
            "Performance (8 Cores)" to "Pretty obvious—all 8 cores unlocked for max power in heavy 3D games. Just don't expect more than 3 hours on 100% battery."
        )

        tuningInfo.forEach { (title, desc) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun RealityCheckPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = ":)", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Many games will not run at all, either because of genuine lack of support, or just because your SoC just can't handle it. And no matter what you try, it can appear in the task manager, use all your ram, but it will never load. When you see this, it's best to give up.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = ":'(", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ColorPill(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(hex.toColor())
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}
