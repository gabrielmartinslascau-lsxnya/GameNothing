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
                            wallpaperColor = state.wallpaperColor
                        )
                        2 -> TutorialPage(
                            icon = Icons.Default.Speed,
                            title = "Performance Tips",
                            description = "For the most reliable experience across most games, we recommend using Proton 11 and DXVK 2.4.1. You can configure these in the Container Settings for each game."
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
                    repeat(3) { index ->
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
                        if (currentPage < 2) currentPage++ else onComplete()
                    }
                ) {
                    Text(if (currentPage < 2) "Next" else "Get Started")
                    if (currentPage < 2) {
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
    wallpaperColor: String
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

        Spacer(modifier = Modifier.height(32.dp))

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

        // Simple Color Picker
        val colors = listOf(
            "#E91E63" to "Cherry",
            "#4CAF50" to "Forest",
            "#2196F3" to "Ice",
            "#9C27B0" to "Violet",
            "#FFFFFF" to "Frost",
            "#000000" to "Volcanic"
        )

        Text("Main Colour", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { (hex, _) ->
                ColorPill(
                    hex = hex,
                    selected = hex == mainColor,
                    onClick = { viewModel.setMainColor(hex) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Wallpaper Colour", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { (hex, _) ->
                ColorPill(
                    hex = hex,
                    selected = hex == wallpaperColor,
                    onClick = { viewModel.setWallpaperColor(hex) }
                )
            }
        }
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
