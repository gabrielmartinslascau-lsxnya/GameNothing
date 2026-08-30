package app.gamenative.ui.component.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.gamenative.utils.HapticManager
import app.gamenative.utils.hapticClickable
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import com.alorma.compose.settings.ui.base.internal.SettingsTileColors

@Composable
fun HapticSettingsMenuLink(
    modifier: Modifier = Modifier,
    colors: SettingsTileColors,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    SettingsMenuLink(
        modifier = modifier.hapticClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            type = HapticManager.HapticType.CLICK,
            onClick = onClick
        ),
        colors = colors,
        icon = icon,
        title = title,
        subtitle = subtitle,
        action = action,
        enabled = enabled,
        onClick = {} // Already handled by hapticClickable
    )
}

@Composable
fun HapticSettingsSwitch(
    modifier: Modifier = Modifier,
    colors: SettingsTileColors,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    state: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitch(
        modifier = modifier,
        colors = colors,
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        state = state,
        onCheckedChange = {
            HapticManager.trigger(HapticManager.HapticType.CLICK)
            onCheckedChange(it)
        }
    )
}
