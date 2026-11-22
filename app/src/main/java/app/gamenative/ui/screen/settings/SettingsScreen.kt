package app.gamenative.ui.screen.settings

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.enums.AppTheme
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.theme.BrokenGlassPanel
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.ui.theme.toHex
import app.gamenative.ui.util.JoystickScrollHandler
import app.gamenative.utils.HapticManager
import com.alorma.compose.settings.ui.base.internal.LocalSettingsTileColors
import com.materialkolor.PaletteStyle

@Composable
fun SettingsScreen(
    appTheme: AppTheme,
    paletteStyle: PaletteStyle,
    onAppTheme: (AppTheme) -> Unit,
    onPaletteStyle: (PaletteStyle) -> Unit,
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    SettingsScreenContent(
        appTheme = appTheme,
        paletteStyle = paletteStyle,
        onAppTheme = onAppTheme,
        onPaletteStyle = onPaletteStyle,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
private fun SettingsScreenContent(
    appTheme: AppTheme,
    paletteStyle: PaletteStyle,
    onAppTheme: (AppTheme) -> Unit,
    onPaletteStyle: (PaletteStyle) -> Unit,
    onBack: () -> Unit,
    viewModel: MainViewModel? = null,
) {
    val scrollState = rememberScrollState()
    JoystickScrollHandler(scrollState = scrollState)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding(),
        ) {
            SettingsHeader(
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // Scrollable content bounded below SettingsHeader so cards & shadows clip cleanly
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Emulation section
                    SettingsSection(
                        title = stringResource(R.string.settings_emulation_title),
                        icon = Icons.Default.Gamepad,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupEmulation()
                    }

                    // GameNative account section
                    SettingsSection(
                        title = stringResource(R.string.gamenative_account_title),
                        icon = Icons.Default.AccountCircle,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupGameNativeAccount()
                    }

                    // Game launcher accounts section
                    SettingsSection(
                        title = stringResource(R.string.settings_accounts_title),
                        icon = Icons.Default.ManageAccounts,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupAccounts()
                    }

                    // Looks section
                    if (viewModel != null) {
                        SettingsSection(
                            title = stringResource(R.string.settings_looks_title),
                            icon = Icons.Default.AutoFixHigh,
                            iconTint = MaterialTheme.colorScheme.primary,
                        ) {
                            SettingsGroupLooks(viewModel = viewModel)
                        }
                    }

                    // Feel section
                    SettingsSection(
                        title = stringResource(R.string.settings_feel_title),
                        icon = Icons.Default.Vibration,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupFeel()
                    }

                    // Experimental section
                    if (viewModel != null) {
                        SettingsSection(
                            title = "Experimental",
                            icon = Icons.Default.Science,
                            iconTint = MaterialTheme.colorScheme.primary,
                        ) {
                            SettingsGroupExperimental(viewModel = viewModel)
                        }
                    }

                    // Performance section
                    SettingsSection(
                        title = stringResource(R.string.settings_performance_title),
                        icon = Icons.Default.Speed,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupPerformance()
                    }

                    // Interface section
                    SettingsSection(
                        title = stringResource(R.string.settings_interface_title),
                        icon = Icons.Default.Palette,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupInterface(
                            appTheme = appTheme,
                            paletteStyle = paletteStyle,
                            onAppTheme = onAppTheme,
                            onPaletteStyle = onPaletteStyle,
                        )
                    }

                    // Info section
                    SettingsSection(
                        title = stringResource(R.string.settings_info_title),
                        icon = Icons.Default.Info,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupInfo()
                    }

                    // Debug section
                    SettingsSection(
                        title = stringResource(R.string.settings_debug_title),
                        icon = Icons.Default.BugReport,
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) {
                        SettingsGroupDebug()
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

@Composable
private fun SettingsHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = glassContentColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackButton(onClick = onBack)

        // Title
        Column {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = contentColor,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.6f),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Settings icon decoration
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "backButtonScale",
    )

    val contentColor = glassContentColor()
    BrokenGlassPanel(
        modifier = modifier
            .scale(scale)
            .size(44.dp)
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    HapticManager.trigger(HapticManager.HapticType.CLICK)
                    onClick()
                },
            ),
        cornerRadius = 22,
        backgroundColor = if (isFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            Color.White.copy(alpha = 0.08f)
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.back),
                tint = if (isFocused) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val contentColor = glassContentColor()
    BrokenGlassPanel(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20,
        backgroundColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        ) {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon with glow background
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp,
                    ),
                    color = contentColor,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = contentColor.copy(alpha = 0.12f),
            )

            // Section content
            CompositionLocalProvider(LocalSettingsTileColors provides settingsTileColorsAlt()) {
                content()
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:width=1920px,height=1080px,dpi=440,orientation=landscape",
)
@Composable
private fun Preview_SettingsScreen() {
    val isPreview = LocalInspectionMode.current
    if (!isPreview) {
        val context = LocalContext.current
        PrefManager.init(context)
    }
    PluviaTheme {
        SettingsScreenContent(
            appTheme = AppTheme.DAY,
            paletteStyle = PaletteStyle.TonalSpot,
            onAppTheme = { },
            onPaletteStyle = { },
            onBack = { },
        )
    }
}
