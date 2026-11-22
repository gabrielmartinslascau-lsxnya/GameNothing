package app.gamenative.ui.component.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.performance.PerformanceModeManager
import app.gamenative.performance.ShaderCacheCleaner
import app.gamenative.performance.TuningMode
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.util.SnackbarManager
import kotlinx.coroutines.launch

/**
 * Dedicated Performance & Eco Tab (index 0, far left BEFORE General) in Container Settings.
 *
 * Note: The outer [ContainerConfigDialog] wraps all tab content in a `verticalScroll` Column
 * with 16.dp horizontal and 12.dp vertical padding, so this composable emits padded cards
 * directly without nested vertical scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PerformanceTabContent(
    state: ContainerConfigState,
    appId: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val looks = LocalLooksState.current
    val isGlass = looks.isGlassEnabled
    val config = state.config.value
    val activeMode = remember(config.tuningMode) { TuningMode.fromRaw(config.tuningMode) }

    val primaryText = if (isGlass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    val secondaryText = primaryText.copy(alpha = 0.74f)
    val accentColor = when (activeMode) {
        TuningMode.PERFORMANCE -> Color(0xFF38BDF8)
        TuningMode.ECO -> Color(0xFF34D399)
        TuningMode.OFF -> primaryText.copy(alpha = 0.7f)
    }

    val corePreview = remember(config.tuningMode, config.cpuList, config.cpuListWoW64) {
        PerformanceModeManager.formatCorePreview(config)
    }

    var isClearingCache by remember { mutableStateOf(false) }

    ContainerGlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = when (activeMode) {
                            TuningMode.PERFORMANCE -> Icons.Default.Bolt
                            TuningMode.ECO -> Icons.Default.Eco
                            TuningMode.OFF -> Icons.Default.PowerSettingsNew
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Container Tuning Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = activeMode.id,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
            }

            val modes = listOf(
                TuningMode.OFF to "Off",
                TuningMode.PERFORMANCE to "Performance",
                TuningMode.ECO to "Eco",
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = activeMode == mode,
                        onClick = {
                            if (activeMode != mode) {
                                val updated = PerformanceModeManager.setTuningMode(
                                    config = config,
                                    targetMode = mode,
                                    context = context,
                                    appId = appId,
                                )
                                state.config.value = updated
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
                        ),
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            // 1-line live preview of assigned CPU cores
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
            ) {
                Text(
                    text = corePreview,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val modeDescription = when (activeMode) {
                TuningMode.OFF ->
                    "Off uses your exact manual container settings with zero OS performance hooks, zero ADPF sessions, and zero /proc polling."
                TuningMode.PERFORMANCE ->
                    "Performance switches renderer to X11, FEXCore to Extreme, unlocks all 64-bit cores, caps 32-bit WoW64 to the top ≤4 fastest cores, and enables active-gameplay OS boosts + safe Rootless Game Pinning."
                TuningMode.ECO ->
                    "Eco assigns an even LITTLE + Medium core pair (2–6 cores, never all 8), caps 32-bit WoW64 to ≤4 even cores, sets FEXCore to Intermediate, and runs zero OS boosts or background polling for cooler thermals."
            }

            Text(
                text = modeDescription,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )

            Text(
                text = "Protected invariants: Never modifies your FPS Cap, Online/Xbox Live settings, Proton/Wine, DXVK/VKD3D, FEX/Box64 version, Graphics Driver, or Display Resolution. Switching back to Off restores your original baseline.",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryText.copy(alpha = 0.82f),
            )
        }
    }

    ContainerGlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = primaryText,
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    text = "Shader & Pipeline Cache",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                )
            }

            Text(
                text = "Wipes Mesa/Turnip, DXVK/VKD3D, and Fossilize shader caches (plus stale X11/Pulse temp sockets when no game is running) to clear GPU hang / pipeline corruption. Never touches saves, Wine prefixes, or installed components.",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )

            OutlinedButton(
                onClick = {
                    if (isClearingCache) return@OutlinedButton
                    isClearingCache = true
                    scope.launch {
                        val result = ShaderCacheCleaner.clearShaderCaches(context, appId)
                        isClearingCache = false
                        SnackbarManager.show(result.formattedMessage())
                    }
                },
                enabled = !isClearingCache,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryText),
            ) {
                if (isClearingCache) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clearing Shader Cache...")
                } else {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Shader Cache", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
