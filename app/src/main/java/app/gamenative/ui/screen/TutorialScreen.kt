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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.enums.GlassMode
import app.gamenative.ui.component.HSVColorPicker
import app.gamenative.ui.component.settings.HapticSettingsSwitch
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.theme.BrokenGlassPanel
import app.gamenative.ui.theme.BricolageGrotesque
import app.gamenative.ui.theme.NdotFontFamily
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.ui.theme.toColor
import app.gamenative.ui.theme.toHex
import app.gamenative.ui.util.JoystickScrollHandler

private const val TOTAL_TUTORIAL_PAGES = 5

@Composable
fun TutorialScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onComplete: () -> Unit,
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pageScrollState = rememberScrollState()
    val contentColor = glassContentColor()

    JoystickScrollHandler(scrollState = pageScrollState)

    LaunchedEffect(currentPage) {
        pageScrollState.scrollTo(0)
    }

    // Transparent surface lets PluviaMain's Layer 0 (wallpaperColor + live video wallpaper)
    // refract through every translucent BrokenGlassPanel card while the dark gradient scrim
    // prevents bright wallpapers from washing out the header, subtitle, or card text.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.24f))
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.72f),
                        0.38f to Color.Black.copy(alpha = 0.34f),
                        0.76f to Color.Black.copy(alpha = 0.22f),
                        1.0f to Color.Black.copy(alpha = 0.50f),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SETUP GUIDE  •  ${currentPage + 1} / $TOTAL_TUTORIAL_PAGES",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = contentColor.copy(alpha = 0.78f),
                    )
                    TextButton(onClick = onComplete) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = BricolageGrotesque,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Scrollable big-screen body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(pageScrollState),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                        label = "tutorial_step",
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 860.dp)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (page) {
                                0 -> ProtonAndDxvkSlide()
                                1 -> TurnipDriversSlide()
                                2 -> DedicatedVramSlide()
                                3 -> CpuCoresVsFexSlide()
                                4 -> SetupLookSlide(
                                    viewModel = viewModel,
                                    mainColor = state.mainColor,
                                    wallpaperColor = state.wallpaperColor,
                                    glassPanels = state.glassPanels,
                                    glassMode = state.glassMode,
                                )
                            }
                        }
                    }
                }

                // Footer Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp)
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentPage > 0) {
                        Button(
                            onClick = { currentPage-- },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = contentColor.copy(alpha = 0.14f),
                                contentColor = contentColor,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Back",
                                fontFamily = BricolageGrotesque,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(104.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(TOTAL_TUTORIAL_PAGES) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (currentPage == index) 11.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (currentPage == index) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            contentColor.copy(alpha = 0.30f)
                                        },
                                    )
                                    .clickable { currentPage = index },
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (currentPage < TOTAL_TUTORIAL_PAGES - 1) currentPage++ else onComplete()
                        },
                    ) {
                        Text(
                            text = if (currentPage < TOTAL_TUTORIAL_PAGES - 1) "Next" else "Finish",
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (currentPage < TOTAL_TUTORIAL_PAGES - 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialSlideHeader(
    icon: ImageVector,
    sectionLabel: String,
    title: String,
    subtitle: String,
) {
    val contentColor = glassContentColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TutorialGlassSurface(
            modifier = Modifier.size(72.dp),
            cornerRadius = 20,
            accentBorder = true,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = sectionLabel.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = NdotFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
            ),
            color = contentColor,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = BricolageGrotesque,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            color = contentColor.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 700.dp),
        )
    }
}

@Composable
private fun TutorialGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    accentBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    val borderColor = if (accentBorder) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    BrokenGlassPanel(
        modifier = modifier.border(1.dp, borderColor, shape),
        cornerRadius = cornerRadius,
        shape = shape,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.24f)),
            content = content,
        )
    }
}

@Composable
private fun TutorialGuideCard(
    title: String,
    body: String,
    accentBorder: Boolean = false,
) {
    val contentColor = glassContentColor()
    TutorialGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18,
        accentBorder = accentBorder,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = BricolageGrotesque,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                ),
                color = contentColor.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun ProtonAndDxvkSlide() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TutorialSlideHeader(
            icon = Icons.Default.Layers,
            sectionLabel = "Screen 1 • Compatibility Layers",
            title = "Proton & DXVK Combos",
            subtitle = "What actually works when a Windows game won't boot, stutters, or black-screens.",
        )

        Spacer(modifier = Modifier.height(4.dp))

        TutorialGuideCard(
            title = "Proton: arm64ec vs. x86_64",
            body = "Use proton-arm64ec as your daily driver for lower overhead on Snapdragon chips. Switch to a standard x86_64 Wine/Proton build only if an older 32-bit game refuses to boot on arm64ec.",
            accentBorder = true,
        )

        TutorialGuideCard(
            title = "DXVK (DirectX 9 / 10 / 11) & VKD3D (DirectX 12)",
            body = "Pair newer DXVK 2.x+ builds with modern DX11 games. If an older DX9 or DX11 title stutters or crashes on launch, drop to DXVK 1.10.3 or an async build. Only switch to VKD3D for DirectX 12-only games.",
        )

        TutorialGuideCard(
            title = "Display Server: Gamescope vs. X11",
            body = "Gamescope handles scaling and frame pacing cleanly for most titles. If a game flickers, black-screens, or fails to map its window in Gamescope, swap Display Server to X11 in Graphics settings.",
        )
    }
}

@Composable
private fun TurnipDriversSlide() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TutorialSlideHeader(
            icon = Icons.Default.Tune,
            sectionLabel = "Screen 2 • GPU Drivers",
            title = "When to Use AdrenoTools",
            subtitle = "Picking between open-source Turnip, stock Qualcomm drivers, and System/Wrapper fallbacks.",
        )

        Spacer(modifier = Modifier.height(4.dp))

        TutorialGuideCard(
            title = "Turnip (AdrenoTools Open-Source Mesa)",
            body = "Your go-to choice on Snapdragon Adreno 6xx and 7xx GPUs. Turnip fixes missing textures, broken shaders, and DXVK crashes that stock phone drivers often hit.",
            accentBorder = true,
        )

        TutorialGuideCard(
            title = "Stock Qualcomm / System Driver",
            body = "Useful on newer Snapdragon 8 Elite / Gen 4 chips where Turnip support isn't ready yet, or on titles that run fine and benefit from stock driver stability and clocks.",
        )

        TutorialGuideCard(
            title = "Wrapper / System Fallback (Non-Adreno GPUs)",
            body = "On Mali, Xclipse, or unsupported Adreno devices, turn off AdrenoTools Turnip and stick with the System / Wrapper driver path so Vulkan initialization succeeds.",
        )
    }
}

@Composable
private fun DedicatedVramSlide() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TutorialSlideHeader(
            icon = Icons.Default.Memory,
            sectionLabel = "Screen 3 • Memory Limits",
            title = "Dedicated VRAM Sizing",
            subtitle = "Phones use unified RAM—setting this higher than your game needs only risks Android killing the app.",
        )

        Spacer(modifier = Modifier.height(4.dp))

        TutorialGuideCard(
            title = "The Unified RAM Rule",
            body = "Android, the OS, and the game all share one physical memory pool. Raising Video Memory Size does not create extra RAM—it only changes how much VRAM Windows reports to the game.",
            accentBorder = true,
        )

        TutorialGuideCard(
            title = "512 MB  •  2D & Pre-2012 Games",
            body = "Ideal for 2D indie titles and older DirectX 9 games so they don't over-allocate address space or hit legacy 32-bit memory limits.",
        )

        TutorialGuideCard(
            title = "1 GB – 2 GB  •  90% of 3D Games (8–12 GB Phones)",
            body = "The sweet spot for almost every 3D title on 8 GB to 12 GB phones—gives textures enough breathing room without starving Android and triggering an OOM kill.",
        )

        TutorialGuideCard(
            title = "4 GB  •  Heavy Modern 3D (12–16 GB+ Phones Only)",
            body = "Use only on 12 GB or 16 GB+ RAM devices for heavy modern 3D titles that refuse to launch or lock texture settings when reported VRAM is under 4 GB.",
        )
    }
}

@Composable
private fun CpuCoresVsFexSlide() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TutorialSlideHeader(
            icon = Icons.Default.Speed,
            sectionLabel = "Screen 4 • Power & Translation",
            title = "CPU Cores vs. FEX",
            subtitle = "Understand what actually controls battery drain and heat versus software translation.",
        )

        Spacer(modifier = Modifier.height(4.dp))

        TutorialGuideCard(
            title = "FEX = x86-to-ARM Translation (Not a Battery Dial)",
            body = "FEX translates PC x86 instructions to ARM in software. Changing FEX versions or TSO / X87 presets helps with game compatibility and shader/CPU stutter—it does not directly save battery.",
        )

        TutorialGuideCard(
            title = "CPU Cores = Physical Core Parking (Real Battery Control)",
            body = "• 3–4 Cores (Eco): Keeps power-hungry Big/Prime cores parked—best for 2D games, visual novels, cool temps, and long battery life.\n• 6 Cores (Balanced): Leaves the Prime core off for steady 720p 3D play without heavy thermal throttling.\n• 8 Cores (Full Send): Unlocks every core for demanding 3D games at the cost of faster battery drain and heat.",
            accentBorder = true,
        )
    }
}

@Composable
private fun SetupLookSlide(
    viewModel: MainViewModel,
    mainColor: String,
    wallpaperColor: String,
    glassPanels: Boolean,
    glassMode: GlassMode,
) {
    val contentColor = glassContentColor()
    var showMainPicker by remember { mutableStateOf(false) }
    var showWallpaperPicker by remember { mutableStateOf(false) }

    if (showMainPicker) {
        HSVColorPicker(
            initialColor = mainColor.toColor(),
            onColorSelected = { viewModel.setMainColor(it.toHex()) },
            onDismiss = { showMainPicker = false },
        )
    }

    if (showWallpaperPicker) {
        HSVColorPicker(
            initialColor = wallpaperColor.toColor(),
            onColorSelected = { viewModel.setWallpaperColor(it.toHex()) },
            onDismiss = { showWallpaperPicker = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TutorialSlideHeader(
            icon = Icons.Default.AutoFixHigh,
            sectionLabel = "Screen 5 • Appearance",
            title = "Set Up Your Look",
            subtitle = "Glass Panels are enabled by default. Preview your glass style, accent, and wallpaper tint live.",
        )

        Spacer(modifier = Modifier.height(4.dp))

        TutorialGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            cornerRadius = 20,
            accentBorder = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (glassPanels) {
                        if (glassMode == GlassMode.BROKEN_GLASS) {
                            "Broken Glass Active"
                        } else {
                            "Standard Glass Active"
                        }
                    } else {
                        "Glass Panels Disabled"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = NdotFontFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Changes apply immediately across the library and container settings.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = BricolageGrotesque,
                        fontSize = 13.sp,
                    ),
                    color = contentColor.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        TutorialGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HapticSettingsSwitch(
                    colors = settingsTileColorsAlt(),
                    state = glassPanels,
                    title = {
                        Text(
                            text = "Glass Panels",
                            fontFamily = BricolageGrotesque,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                    },
                    subtitle = {
                        Text(
                            text = "Translucent blurred and refractive panels",
                            fontFamily = BricolageGrotesque,
                            color = contentColor.copy(alpha = 0.72f),
                        )
                    },
                    onCheckedChange = { viewModel.setGlassPanels(it) },
                )
                if (glassPanels) {
                    HapticSettingsSwitch(
                        colors = settingsTileColorsAlt(),
                        state = glassMode == GlassMode.BROKEN_GLASS,
                        title = {
                            Text(
                                text = "Broken Glass Mode",
                                fontFamily = BricolageGrotesque,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                            )
                        },
                        subtitle = {
                            Text(
                                text = "Off = Normal frosted glass  •  On = Edge refraction & dispersion",
                                fontFamily = BricolageGrotesque,
                                color = contentColor.copy(alpha = 0.72f),
                            )
                        },
                        onCheckedChange = { enabled ->
                            viewModel.setGlassMode(
                                if (enabled) GlassMode.BROKEN_GLASS else GlassMode.STANDARD,
                            )
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { showMainPicker = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor.toColor()),
            ) {
                Text(
                    text = "Main Colour",
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ColorUtils.calculateLuminance(mainColor.toColor().toArgb()) > 0.5) {
                        Color.Black
                    } else {
                        Color.White
                    },
                )
            }

            Button(
                onClick = { showWallpaperPicker = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = wallpaperColor.toColor()),
            ) {
                Text(
                    text = "Wallpaper",
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ColorUtils.calculateLuminance(wallpaperColor.toColor().toArgb()) > 0.5) {
                        Color.Black
                    } else {
                        Color.White
                    },
                )
            }
        }

        Text(
            text = "You can reopen this guide anytime from Repeat Tutorial in the side menu.",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = BricolageGrotesque,
            ),
            color = contentColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
