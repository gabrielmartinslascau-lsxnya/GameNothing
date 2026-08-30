package app.gamenative.ui.component

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.gamenative.ui.theme.NdotFontFamily
import app.gamenative.ui.theme.PTMonoFontFamily
import android.os.Build
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.powercontrol.PowerManager
import app.gamenative.ui.component.quickMenus.PowerControlQuickMenuTab
import app.gamenative.ui.data.PerformanceHudConfig
import app.gamenative.ui.data.PerformanceHudSize
import app.gamenative.ui.theme.PluviaAnimation
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.PluviaGlassPanel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.theme.toColor
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.LooksState
import app.gamenative.ui.util.adaptivePanelWidth
import app.gamenative.ui.theme.pluviaGlass
import app.gamenative.utils.hapticClickable
import app.gamenative.utils.hapticSelectable
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import app.gamenative.ui.component.settings.HapticSettingsSwitch
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.theme.settingsTileColorsAlt
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import app.gamenative.enums.FontType
import app.gamenative.ui.model.MainViewModel
import app.gamenative.utils.MathUtils.normalizedProgress
import com.winlator.container.Container
import com.winlator.renderer.GLRenderer
import com.winlator.renderer.VulkanRenderer
import com.winlator.winhandler.ProcessInfo
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.material.icons.filled.Palette
import androidx.hilt.navigation.compose.hiltViewModel
import app.gamenative.utils.HapticManager

object QuickMenuAction {
    const val KEYBOARD = 1
    const val INPUT_CONTROLS = 2
    const val EXIT_GAME = 3
    const val EDIT_CONTROLS = 4
    const val EDIT_PHYSICAL_CONTROLLER = 5
    const val PERFORMANCE_HUD = 6
    const val TOUCHSCREEN_MODE = 7
    const val DISABLE_MOUSE = 8
    const val SHOOTER_MODE = 9
    const val RADIAL_MENU = 10
}

enum class QuickMenuTab(val labelResId: Int, val icon: ImageVector) {
    HUD(R.string.performance_hud, Icons.Default.QueryStats),
    POWER(R.string.power_control, Icons.Default.BatteryChargingFull),
    LSFG(R.string.lsfg_tab_title, Icons.Default.Speed),
    LOOKS(R.string.settings_looks_title, Icons.Default.Palette),
    INVITE(R.string.steam_invite_tab_title, Icons.Default.PersonAdd),
    EFFECTS(R.string.screen_effects, Icons.Default.AutoFixHigh),
    CONTROLLER(R.string.quick_menu_tab_controller, Icons.Default.Gamepad),
    REFRESH_RATE(R.string.settings_looks_refresh_rate, Icons.Default.Palette),
    TOOLS(R.string.task_manager, Icons.Default.BarChart),
    IMMERSIVE(R.string.quick_menu_tab_immersive, Icons.Default.ViewInAr);

    fun isVisible(immersiveHooks: Any?, inviteMenu: Any?, renderer: Any?, glRenderer: Any?, isLsfgAvailable: Boolean): Boolean {
        return when (this) {
            LSFG -> isLsfgAvailable
            INVITE -> inviteMenu != null
            EFFECTS -> renderer != null || glRenderer != null
            IMMERSIVE -> immersiveHooks != null
            else -> true
        }
    }
}

private var PrefManager.quickMenuLastTabEnum: QuickMenuTab
    get() = QuickMenuTab.entries.getOrNull(quickMenuLastTab) ?: QuickMenuTab.HUD
    set(value) { quickMenuLastTab = value.ordinal }

data class QuickMenuItem(
    val id: Int,
    val icon: ImageVector,
    val labelResId: Int,
    val accentColor: Color = Color.Unspecified,
    val enabled: Boolean = true,
)

private enum class PerformanceHudPreset(val labelResId: Int) {
    FPS_ONLY(R.string.performance_hud_preset_fps_only),
    ESSENTIAL(R.string.performance_hud_preset_essential),
    BATTERY(R.string.performance_hud_preset_battery),
    FULL(R.string.performance_hud_preset_full),
}

private fun applyPerformanceHudPreset(currentConfig: PerformanceHudConfig, preset: PerformanceHudPreset): PerformanceHudConfig {
    return when (preset) {
        PerformanceHudPreset.FPS_ONLY -> currentConfig.copy(showFrameRate = true, showCpuUsage = false, showGpuUsage = false, showRamUsage = false, showBatteryLevel = false, showPowerDraw = false, showBatteryRuntime = false, showBatteryTemperature = false, showClockTime = false, showCpuTemperature = false, showGpuTemperature = false, showFrameRateGraph = false, showCpuUsageGraph = false, showGpuUsageGraph = false)
        PerformanceHudPreset.ESSENTIAL -> currentConfig.copy(showFrameRate = true, showCpuUsage = true, showGpuUsage = true, showRamUsage = true, showBatteryLevel = false, showPowerDraw = false, showBatteryRuntime = false, showBatteryTemperature = false, showClockTime = false, showCpuTemperature = false, showGpuTemperature = false, showFrameRateGraph = false, showCpuUsageGraph = false, showGpuUsageGraph = false)
        PerformanceHudPreset.BATTERY -> currentConfig.copy(showFrameRate = true, showCpuUsage = true, showGpuUsage = true, showRamUsage = true, showBatteryLevel = true, showPowerDraw = false, showBatteryRuntime = true, showBatteryTemperature = true, showClockTime = false, showCpuTemperature = false, showGpuTemperature = false, showFrameRateGraph = true, showCpuUsageGraph = false, showGpuUsageGraph = false)
        PerformanceHudPreset.FULL -> currentConfig.copy(showFrameRate = true, showCpuUsage = true, showGpuUsage = true, showRamUsage = true, showBatteryLevel = true, showPowerDraw = true, showBatteryRuntime = true, showBatteryTemperature = true, showClockTime = true, showCpuTemperature = true, showGpuTemperature = true, showFrameRateGraph = true, showCpuUsageGraph = true, showGpuUsageGraph = true)
    }
}

private fun matchesPerformanceHudPreset(currentConfig: PerformanceHudConfig, preset: PerformanceHudPreset): Boolean {
    val p = applyPerformanceHudPreset(currentConfig, preset)
    return currentConfig.showFrameRate == p.showFrameRate && currentConfig.showCpuUsage == p.showCpuUsage && currentConfig.showGpuUsage == p.showGpuUsage && currentConfig.showRamUsage == p.showRamUsage && currentConfig.showBatteryLevel == p.showBatteryLevel && currentConfig.showPowerDraw == p.showPowerDraw && currentConfig.showBatteryRuntime == p.showBatteryRuntime && currentConfig.showBatteryTemperature == p.showBatteryTemperature && currentConfig.showClockTime == p.showClockTime && currentConfig.showCpuTemperature == p.showCpuTemperature && currentConfig.showGpuTemperature == p.showGpuTemperature && currentConfig.showFrameRateGraph == p.showFrameRateGraph && currentConfig.showCpuUsageGraph == p.showCpuUsageGraph && currentConfig.showGpuUsageGraph == p.showGpuUsageGraph
}

class ImmersiveInputBypass(
    val active: Boolean = false,
    private val applyAdjustment: (Pair<() -> Unit, () -> Unit>?) -> Unit = {},
    private val applyActivate: ((() -> Unit)?) -> Unit = {},
) {
    private var adjustmentOwner: Any? = null
    private var activateOwner: Any? = null

    fun reportAdjustment(owner: Any, actions: Pair<() -> Unit, () -> Unit>?) {
        if (actions != null) { adjustmentOwner = owner; applyAdjustment(actions) }
        else if (adjustmentOwner === owner) { adjustmentOwner = null; applyAdjustment(null) }
    }

    fun reportActivate(owner: Any, action: (() -> Unit)?) {
        if (action != null) { activateOwner = owner; applyActivate(action) }
        else if (activateOwner === owner) { activateOwner = null; applyActivate(null) }
    }
}
val LocalImmersiveInputBypass = staticCompositionLocalOf { ImmersiveInputBypass() }

class PerformanceQuickMenuState(
    val hudEnabled: Boolean = false,
    val hudConfig: PerformanceHudConfig = PerformanceHudConfig(),
    val fpsLimiterEnabled: Boolean = true,
    val fpsLimiterTarget: Int = 60,
    val fpsLimiterMax: Int = 60,
    val onHudConfigChanged: (PerformanceHudConfig) -> Unit = {},
    val onFpsLimiterEnabledChanged: (Boolean) -> Unit = {},
    val onFpsLimiterChanged: (Int) -> Unit = {},
)

class LsfgQuickMenuState(
    val isAvailable: Boolean = false,
    val multiplier: Int = 2,
    val flowScale: Float = 0.80f,
    val performanceMode: Boolean = true,
    val onMultiplierChanged: (Int) -> Unit = {},
    val onFlowScaleChanged: (Float) -> Unit = {},
    val onPerformanceModeChanged: (Boolean) -> Unit = {},
)

@Composable
fun QuickMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onItemSelected: (Int) -> Boolean,
    viewModel: MainViewModel = hiltViewModel(),
    renderer: VulkanRenderer? = null,
    glRenderer: GLRenderer? = null,
    container: Container? = null,
    wineProcesses: List<ProcessInfo> = emptyList(),
    isWineProcessesLoading: Boolean = false,
    onToolsVisibilityChanged: (Boolean) -> Unit = {},
    onEndWineProcess: (ProcessInfo) -> Unit = {},
    performance: PerformanceQuickMenuState = PerformanceQuickMenuState(),
    hasPhysicalController: Boolean = false,
    isTouchscreenModeActive: Boolean = false,
    onTouchGestureSettingsClick: () -> Unit = {},
    isShooterModeActive: Boolean = false,
    onShooterModeSettingsClick: () -> Unit = {},
    activeToggleIds: Set<Int> = emptySet(),
    lsfg: LsfgQuickMenuState = LsfgQuickMenuState(),
    onAnimationComplete: (Boolean) -> Unit = {},
    onRequestOpen: () -> Unit = {},
    immersiveHooks: app.gamenative.ui.screen.xr.ImmersiveSessionHooks? = null,
    modifier: Modifier = Modifier,
) {
    val isPerformanceHudEnabled = performance.hudEnabled
    val performanceHudConfig = performance.hudConfig
    val fpsLimiterEnabled = performance.fpsLimiterEnabled
    val fpsLimiterTarget = performance.fpsLimiterTarget
    val fpsLimiterMax = performance.fpsLimiterMax
    val onPerformanceHudConfigChanged = performance.onHudConfigChanged
    val onFpsLimiterEnabledChanged = performance.onFpsLimiterEnabledChanged
    val onFpsLimiterChanged = performance.onFpsLimiterChanged
    val isLsfgAvailable = lsfg.isAvailable
    val lsfgMultiplier = lsfg.multiplier
    val lsfgFlowScale = lsfg.flowScale
    val lsfgPerformanceMode = lsfg.performanceMode
    val onLsfgMultiplierChanged = lsfg.onMultiplierChanged
    val onLsfgFlowScaleChanged = lsfg.onFlowScaleChanged
    val onLsfgPerformanceModeChanged = lsfg.onPerformanceModeChanged
    val focusManager = LocalFocusManager.current
    LaunchedEffect(immersiveHooks) { immersiveHooks?.registerFocusManager?.invoke(focusManager) }
    var activeAdjustment by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    var activeRadioActivate by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(Unit) { immersiveHooks?.registerFocusedActivate?.invoke { activeRadioActivate } }
    var startHeld by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { immersiveHooks?.registerStartHeld?.invoke { startHeld = it } }
    LaunchedEffect(Unit) { immersiveHooks?.registerAdjustmentControl?.invoke { activeAdjustment } }

    val exitGameItem = QuickMenuItem(id = QuickMenuAction.EXIT_GAME, icon = Icons.AutoMirrored.Filled.ExitToApp, labelResId = R.string.exit_game, accentColor = PluviaTheme.colors.accentDanger)

    val controllerItems = buildList {
        add(QuickMenuItem(QuickMenuAction.DISABLE_MOUSE, Icons.Filled.Mouse, R.string.disable_mouse_input, PluviaTheme.colors.accentPurple))
        add(QuickMenuItem(QuickMenuAction.KEYBOARD, Icons.Default.Keyboard, R.string.keyboard, PluviaTheme.colors.accentPurple))
        add(QuickMenuItem(QuickMenuAction.INPUT_CONTROLS, Icons.Default.TouchApp, R.string.input_controls, PluviaTheme.colors.accentPurple))
        if (hasPhysicalController) add(QuickMenuItem(QuickMenuAction.EDIT_PHYSICAL_CONTROLLER, Icons.Default.Gamepad, R.string.edit_physical_controller, PluviaTheme.colors.accentPurple))
        add(QuickMenuItem(QuickMenuAction.EDIT_CONTROLS, Icons.Default.Edit, R.string.edit_controls, PluviaTheme.colors.accentPurple))
        add(QuickMenuItem(QuickMenuAction.TOUCHSCREEN_MODE, Icons.Default.Fingerprint, R.string.touchscreen_mode, PluviaTheme.colors.accentPurple))
        add(QuickMenuItem(QuickMenuAction.SHOOTER_MODE, Icons.Default.Gamepad, R.string.shooter_mode_toggle, PluviaTheme.colors.accentPurple))
        add(QuickMenuItem(QuickMenuAction.RADIAL_MENU, Icons.Default.Settings, R.string.radial_menu, PluviaTheme.colors.accentPurple))
    }

    val inviteMenu = remember(container?.id) { SteamInviteState.createIfAvailable(container) }
    var lsfgPresentMode by remember(container?.id) { mutableStateOf(container?.let { app.gamenative.utils.LsfgQuickMenuHelper.presentMode(it) } ?: "mailbox") }

    var selectedTab by rememberSaveable {
        mutableStateOf(
            when {
                PrefManager.quickMenuLastTabEnum == QuickMenuTab.LSFG && !isLsfgAvailable -> QuickMenuTab.HUD
                PrefManager.quickMenuLastTabEnum == QuickMenuTab.INVITE && inviteMenu == null -> QuickMenuTab.HUD
                PrefManager.quickMenuLastTabEnum == QuickMenuTab.IMMERSIVE && immersiveHooks == null -> QuickMenuTab.HUD
                else -> PrefManager.quickMenuLastTabEnum
            },
        )
    }

    val hudScrollState = rememberScrollState()
    val effectsScrollState = rememberScrollState()
    val lsfgScrollState = rememberScrollState()
    val controllerScrollState = rememberScrollState()
    val immersiveScrollState = rememberScrollState()
    val hudItemFocusRequester = remember { FocusRequester() }
    val lsfgItemFocusRequester = remember { FocusRequester() }
    val inviteItemFocusRequester = remember { FocusRequester() }
    val effectsItemFocusRequester = remember { FocusRequester() }
    val powerItemFocusRequester = remember { FocusRequester() }
    val toolsItemFocusRequester = remember { FocusRequester() }
    val immersiveItemFocusRequester = remember { FocusRequester() }
    val controllerItemFocusRequester = remember { FocusRequester() }

    val visibleState = remember { MutableTransitionState(false) }.apply { targetState = isVisible }
    LaunchedEffect(visibleState.currentState, visibleState.isIdle) { if (visibleState.isIdle) onAnimationComplete(visibleState.currentState) }
    BackHandler(enabled = isVisible) { onDismiss() }

    val availableTabs = remember(isLsfgAvailable, renderer, glRenderer, immersiveHooks, inviteMenu) {
        QuickMenuTab.entries.filter { it.isVisible(immersiveHooks, inviteMenu, renderer, glRenderer, isLsfgAvailable) }
    }

    LaunchedEffect(availableTabs) {
        immersiveHooks?.registerCycleTab?.invoke { forward ->
            val currentIndex = availableTabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0
            val nextTab = availableTabs[if (forward) (currentIndex + 1) % availableTabs.size else (currentIndex - 1 + availableTabs.size) % availableTabs.size]
            selectedTab = nextTab
            PrefManager.quickMenuLastTabEnum = nextTab
        }
    }

    CompositionLocalProvider(
        LocalImmersiveInputBypass provides remember(immersiveHooks != null) {
            ImmersiveInputBypass(
                active = immersiveHooks != null,
                applyAdjustment = { activeAdjustment = it },
                applyActivate = { activeRadioActivate = it },
            )
        },
    ) {
        Box(
            modifier = modifier.fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    if (isVisible && startHeld) return@onPreviewKeyEvent true
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    val currentIndex = availableTabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0
                    val nextTab = if (immersiveHooks == null) null else when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> availableTabs[(currentIndex - 1 + availableTabs.size) % availableTabs.size]
                        KeyEvent.KEYCODE_BUTTON_R1 -> availableTabs[(currentIndex + 1) % availableTabs.size]
                        else -> null
                    }
                    if (nextTab != null) { selectedTab = nextTab; PrefManager.quickMenuLastTabEnum = nextTab; true } else false
                },
        ) {
            AnimatedVisibility(visible = isVisible, enter = fadeIn(animationSpec = tween(200)), exit = fadeOut(animationSpec = tween(150))) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0f)).then(if (immersiveHooks != null) Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } } else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)))
            }

            AnimatedVisibility(visibleState = visibleState, enter = PluviaAnimation.slideInFromLeft(), exit = PluviaAnimation.slideOutToLeft(), modifier = Modifier.align(Alignment.CenterStart)) {
                val panelWidth = if (selectedTab == QuickMenuTab.POWER) adaptivePanelWidth(800.dp, 0.95f) else adaptivePanelWidth(400.dp)
                val contentColor = glassContentColor()
                val looks = LocalLooksState.current
                val shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)

                PluviaGlassPanel(
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight(),
                    shape = shape
                ) {
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = stringResource(R.string.quick_menu_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = contentColor)
                            IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = contentColor) }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Column(modifier = Modifier.width(64.dp).fillMaxHeight().focusGroup(), horizontalAlignment = Alignment.CenterHorizontally) {
                                val tabScrollState = rememberScrollState()
                                Column(modifier = Modifier.weight(1f).verticalScroll(tabScrollState), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    availableTabs.forEach { tab -> QuickMenuTabButton(icon = tab.icon, contentDescriptionResId = tab.labelResId, selected = selectedTab == tab, accentColor = looks.mainColor, onSelected = { selectedTab = tab; PrefManager.quickMenuLastTabEnum = tab }, modifier = Modifier.width(56.dp)) }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                QuickMenuRailActionButton(item = exitGameItem, onClick = { if (onItemSelected(QuickMenuAction.EXIT_GAME)) onDismiss() }, modifier = Modifier.width(56.dp))
                            }

                            Box(modifier = Modifier.padding(horizontal = 12.dp).width(1.dp).fillMaxHeight().background(if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)))

                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(text = stringResource(selectedTab.labelResId), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = contentColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                Box(modifier = Modifier.weight(1f)) {
                                    when (selectedTab) {
                                        QuickMenuTab.HUD -> PerformanceHudQuickMenuTab(isPerformanceHudEnabled = isPerformanceHudEnabled, config = performanceHudConfig, fpsLimiterEnabled = fpsLimiterEnabled, fpsLimiterTarget = fpsLimiterTarget, fpsLimiterMax = fpsLimiterMax, lsfgMultiplier = if (isLsfgAvailable) lsfgMultiplier else 0, onToggle = { onItemSelected(QuickMenuAction.PERFORMANCE_HUD) }, onConfigChanged = onPerformanceHudConfigChanged, onFpsEnabled = onFpsLimiterEnabledChanged, onFpsChanged = onFpsLimiterChanged, scrollState = hudScrollState, focusRequester = hudItemFocusRequester, modifier = Modifier.fillMaxSize())
                                        QuickMenuTab.LOOKS -> LooksQuickMenuTab(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                                        QuickMenuTab.LSFG -> LsfgQuickMenuTab(multiplier = lsfgMultiplier, flowScale = lsfgFlowScale, performanceMode = lsfgPerformanceMode, onMultiplierChanged = onLsfgMultiplierChanged, onFlowScaleChanged = onLsfgFlowScaleChanged, onPerformanceModeChanged = onLsfgPerformanceModeChanged, presentMode = lsfgPresentMode, onPresentModeChanged = { lsfgPresentMode = it; container?.let { c -> app.gamenative.utils.LsfgQuickMenuHelper.applyPresentMode(c, it) } }, scrollState = lsfgScrollState, focusRequester = lsfgItemFocusRequester, modifier = Modifier.fillMaxSize())
                                        QuickMenuTab.INVITE -> inviteMenu?.let { SteamInviteQuickMenuTab(state = it, focusRequester = inviteItemFocusRequester, modifier = Modifier.fillMaxSize()) }
                                        QuickMenuTab.EFFECTS -> { if (renderer != null) ScreenEffectsTabContent(renderer = renderer, container = container, modifier = Modifier.fillMaxSize(), firstItemFocusRequester = effectsItemFocusRequester, scrollState = effectsScrollState) else if (glRenderer != null) GLScreenEffectsTabContent(renderer = glRenderer, container = container, modifier = Modifier.fillMaxSize(), firstItemFocusRequester = effectsItemFocusRequester, scrollState = effectsScrollState) }
                                        QuickMenuTab.POWER -> PowerControlQuickMenuTab(focusRequester = powerItemFocusRequester, modifier = Modifier.fillMaxSize())
                                        QuickMenuTab.TOOLS -> ToolsQuickMenuTab(processes = wineProcesses, isLoadingProcesses = isWineProcessesLoading, onEndProcess = onEndWineProcess, firstItemFocusRequester = toolsItemFocusRequester, modifier = Modifier.fillMaxSize())
                                        QuickMenuTab.REFRESH_RATE -> RefreshRateQuickMenuTab(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                                        QuickMenuTab.IMMERSIVE -> immersiveScrollState?.let { ImmersiveQuickMenuTab(controls = immersiveHooks!!.controls, scrollState = it, focusRequester = immersiveItemFocusRequester, modifier = Modifier.fillMaxSize()) }
                                        else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(controllerScrollState).focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) { controllerItems.forEachIndexed { index, item -> QuickMenuItemRow(item = item, isActive = item.id in activeToggleIds, onClick = { if (onItemSelected(item.id)) onDismiss() }, focusRequester = if (index == 0) controllerItemFocusRequester else null, secondaryIcon = if ((item.id == QuickMenuAction.TOUCHSCREEN_MODE && isTouchscreenModeActive) || (item.id == QuickMenuAction.SHOOTER_MODE && isShooterModeActive)) Icons.Default.Settings else null, secondaryContentDescriptionResId = if (item.id == QuickMenuAction.TOUCHSCREEN_MODE) R.string.gesture_settings_title else R.string.shooter_mode_settings_title, onSecondaryClick = if (item.id == QuickMenuAction.TOUCHSCREEN_MODE) onTouchGestureSettingsClick else onShooterModeSettingsClick) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(isVisible, selectedTab) { onToolsVisibilityChanged(isVisible && selectedTab == QuickMenuTab.TOOLS) }
}

@Composable
private fun QuickMenuTabButton(icon: ImageVector, contentDescriptionResId: Int, selected: Boolean, accentColor: Color, onSelected: () -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }; val isFocused by interactionSource.collectIsFocusedAsState(); val looks = LocalLooksState.current; val contentColor = glassContentColor(); val inputBypass = LocalImmersiveInputBypass.current; LaunchedEffect(isFocused) { inputBypass.reportActivate(interactionSource, if (isFocused) onSelected else null) }
    Box(modifier = modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(when { selected -> accentColor.copy(alpha = 0.18f); isFocused -> accentColor.copy(alpha = 0.12f); else -> if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) }).focusRing(interactionSource, RoundedCornerShape(14.dp), width = 2.dp).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).onFocusChanged { if (it.isFocused && !selected) onSelected() }.hapticSelectable(selected = selected, interactionSource = interactionSource, indication = null, onClick = onSelected).focusable(interactionSource = interactionSource), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = stringResource(contentDescriptionResId), tint = if (selected || isFocused) accentColor else contentColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)) }
}

@Composable
private fun QuickMenuRailActionButton(item: QuickMenuItem, onClick: () -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }; val inputBypass = LocalImmersiveInputBypass.current; val isFocused by interactionSource.collectIsFocusedAsState(); val accentColor = if (item.accentColor != Color.Unspecified) item.accentColor else MaterialTheme.colorScheme.error; val shape = RoundedCornerShape(14.dp)
    Box(modifier = modifier.size(56.dp).then(if (isFocused) Modifier.border(width = 2.dp, color = accentColor.copy(alpha = 0.7f), shape = shape) else Modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), shape = shape)).clip(shape).background(if (isFocused) accentColor.copy(alpha = 0.18f) else accentColor.copy(alpha = 0.08f)).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).hapticClickable(interactionSource = interactionSource, indication = null, onClick = onClick).focusable(interactionSource = interactionSource), contentAlignment = Alignment.Center) { Icon(imageVector = item.icon, contentDescription = stringResource(item.labelResId), tint = if (isFocused) accentColor else accentColor.copy(alpha = 0.9f), modifier = Modifier.size(24.dp)) }
}

@Composable
private fun QuickMenuItemRow(item: QuickMenuItem, isActive: Boolean, onClick: () -> Unit, focusRequester: FocusRequester?, secondaryIcon: ImageVector?, secondaryContentDescriptionResId: Int?, onSecondaryClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }; val inputBypass = LocalImmersiveInputBypass.current; val isFocused by interactionSource.collectIsFocusedAsState(); val looks = LocalLooksState.current; val contentColor = glassContentColor(); val accentColor = if (item.accentColor != Color.Unspecified) item.accentColor else looks.mainColor; val shape = RoundedCornerShape(12.dp)
    val backgroundBrush = if (isFocused && item.enabled) Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.15f), accentColor.copy(alpha = 0.05f))) else null
    val itemBgColor = when { !item.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f); isFocused || isActive -> accentColor.copy(alpha = 0.2f); else -> if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) }

    Row(modifier = modifier.fillMaxWidth().clip(shape).then(if (backgroundBrush != null) Modifier.background(backgroundBrush) else Modifier).focusRing(interactionSource, shape, width = 2.dp).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).hapticSelectable(selected = isFocused, enabled = item.enabled, interactionSource = interactionSource, indication = null, onClick = onClick).focusable(enabled = item.enabled, interactionSource = interactionSource).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(40.dp).then(if (isActive) Modifier.border(BorderStroke(2.dp, accentColor), CircleShape) else Modifier).clip(CircleShape).background(itemBgColor), contentAlignment = Alignment.Center) { Icon(imageVector = item.icon, contentDescription = null, tint = when { !item.enabled -> contentColor.copy(alpha = 0.4f); isFocused || isActive -> accentColor; else -> contentColor.copy(alpha = 0.7f) }, modifier = Modifier.size(22.dp)) }
        Text(text = stringResource(item.labelResId), style = MaterialTheme.typography.bodyLarge, color = when { !item.enabled -> contentColor.copy(alpha = 0.4f); isFocused -> accentColor; else -> contentColor }, modifier = Modifier.weight(1f))
        if (secondaryIcon != null && onSecondaryClick != null) { Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).hapticClickable(role = Role.Button, onClick = onSecondaryClick), contentAlignment = Alignment.Center) { Icon(imageVector = secondaryIcon, contentDescription = stringResource(secondaryContentDescriptionResId ?: R.string.gesture_settings_title), tint = contentColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) } }
    }
}

@Composable
private fun SteamInviteQuickMenuTab(state: SteamInviteState, focusRequester: FocusRequester? = null, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState(); val accentColor = PluviaTheme.colors.accentPurple; val scope = rememberCoroutineScope(); LaunchedEffect(Unit) { state.refresh(); while (true) { delay(3000); state.refreshQuietly() } }
    Column(modifier = modifier.verticalScroll(scrollState).focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QuickMenuSectionHeader(title = when { state.isLoading -> stringResource(R.string.main_loading); state.hostUnavailable -> stringResource(R.string.steam_invite_unavailable); else -> stringResource(R.string.steam_invite_header, state.friends.size) }, subtitle = state.lastError?.let { stringResource(it) })
        state.friends.forEachIndexed { index, friend -> QuickMenuDetailRow(title = friend.name, subtitle = stringResource(when { friend.inOurLobby -> R.string.steam_invite_joined; friend.isJoinable -> R.string.steam_invite_joinable; friend.steamId in state.inviteSent -> R.string.steam_invite_sent; friend.isOnline -> R.string.steam_invite_online; else -> R.string.steam_invite_offline }), accentColor = accentColor, onActivate = { scope.launch { if (friend.isJoinable) state.join(friend) else state.invite(friend.steamId) } }, focusRequester = if (index == 0) focusRequester else null) }
    }
}

@Composable
private fun ToolsQuickMenuTab(processes: List<ProcessInfo>, isLoadingProcesses: Boolean, onEndProcess: (ProcessInfo) -> Unit, firstItemFocusRequester: FocusRequester? = null, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState(); val accentColor = PluviaTheme.colors.accentPurple; val contentColor = glassContentColor()
    Column(modifier = modifier.verticalScroll(scrollState).focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QuickMenuSectionHeader(title = if (isLoadingProcesses) stringResource(R.string.main_loading) else stringResource(R.string.tools_wine_processes_running_hint, processes.size))
        if (!isLoadingProcesses && processes.isEmpty()) { Text(text = stringResource(R.string.tools_wine_processes_empty), style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        else { processes.forEachIndexed { index, process -> QuickMenuDetailRow(title = process.name + if (process.wow64Process) " *32" else "", subtitle = process.formattedMemoryUsage, accentColor = accentColor, onActivate = { onEndProcess(process) }, focusRequester = if (index == 0) firstItemFocusRequester else null) } }
    }
}

@Composable
private fun PerformanceHudQuickMenuTab(isPerformanceHudEnabled: Boolean, config: PerformanceHudConfig, fpsLimiterEnabled: Boolean, fpsLimiterTarget: Int, fpsLimiterMax: Int, lsfgMultiplier: Int, onToggle: () -> Unit, onConfigChanged: (PerformanceHudConfig) -> Unit, onFpsEnabled: (Boolean) -> Unit, onFpsChanged: (Int) -> Unit, scrollState: ScrollState, focusRequester: FocusRequester? = null, modifier: Modifier = Modifier) {
    val accentColor = PluviaTheme.colors.accentPurple; val contentColor = glassContentColor()
    Column(modifier = modifier.verticalScroll(scrollState).focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_fps_limiter), subtitle = if (lsfgMultiplier >= 2) stringResource(R.string.performance_hud_fps_limiter_lsfg_base) else null, enabled = fpsLimiterEnabled, onToggle = { onFpsEnabled(!fpsLimiterEnabled) }, accentColor = accentColor, focusRequester = focusRequester)
        AnimatedVisibility(visible = fpsLimiterEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { Column { Spacer(modifier = Modifier.height(4.dp)); QuickMenuAdjustmentRow(title = stringResource(R.string.performance_hud_fps_limiter_target), valueText = stringResource(R.string.performance_hud_fps_limiter_value, fpsLimiterTarget), progress = (fpsLimiterTarget.toFloat() / fpsLimiterMax.toFloat()).coerceIn(0f, 1f), onDecrease = { onFpsChanged(previousFpsLimiterValue(fpsLimiterTarget, fpsLimiterMax)) }, onIncrease = { onFpsChanged(nextFpsLimiterValue(fpsLimiterTarget, fpsLimiterMax)) }, accentColor = accentColor) } }
        Spacer(modifier = Modifier.height(8.dp)); QuickMenuToggleRow(title = stringResource(R.string.performance_hud), subtitle = stringResource(R.string.performance_hud_description), enabled = isPerformanceHudEnabled, onToggle = onToggle, accentColor = accentColor)
        Spacer(modifier = Modifier.height(8.dp)); QuickMenuSectionHeader(title = stringResource(R.string.performance_hud_presets))
        Row(modifier = Modifier.padding(horizontal = 8.dp).then(if (LocalImmersiveInputBypass.current.active) Modifier.focusGroup() else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp)) { PerformanceHudPreset.entries.forEach { preset -> QuickMenuChoiceChip(text = stringResource(preset.labelResId), selected = matchesPerformanceHudPreset(config, preset), accentColor = accentColor, onClick = { onConfigChanged(applyPerformanceHudPreset(config, preset)); if (!isPerformanceHudEnabled) onToggle() }, modifier = Modifier.width(56.dp)) } }
        Spacer(modifier = Modifier.height(8.dp)); QuickMenuSectionHeader(title = stringResource(R.string.performance_hud_appearance))
        Text(text = stringResource(R.string.performance_hud_size), style = MaterialTheme.typography.labelLarge, color = contentColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        Row(modifier = Modifier.padding(horizontal = 8.dp).then(if (LocalImmersiveInputBypass.current.active) Modifier.focusGroup() else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(PerformanceHudSize.SMALL to R.string.performance_hud_size_small, PerformanceHudSize.MEDIUM to R.string.performance_hud_size_medium, PerformanceHudSize.LARGE to R.string.performance_hud_size_large).forEach { (size, labelRes) -> QuickMenuChoiceChip(text = stringResource(labelRes), selected = config.size == size, accentColor = accentColor, onClick = { onConfigChanged(config.copy(size = size)) }, modifier = Modifier.width(56.dp)) } }
        QuickMenuAdjustmentRow(title = stringResource(R.string.performance_hud_background_opacity), valueText = stringResource(R.string.performance_hud_percentage_value, (config.backgroundOpacity * 100f).roundToInt()), progress = normalizedProgress(config.backgroundOpacity, 0f, 1f), onDecrease = { onConfigChanged(config.copy(backgroundOpacity = (config.backgroundOpacity - 0.05f).coerceIn(0f, 1f))) }, onIncrease = { onConfigChanged(config.copy(backgroundOpacity = (config.backgroundOpacity + 0.05f).coerceIn(0f, 1f))) }, accentColor = accentColor)
        QuickMenuAdjustmentRow(title = stringResource(R.string.performance_hud_color_intensity), valueText = stringResource(R.string.performance_hud_percentage_value, (config.colorIntensity * 100f).roundToInt()), progress = normalizedProgress(config.colorIntensity, 0f, 1f), onDecrease = { onConfigChanged(config.copy(colorIntensity = (config.colorIntensity - 0.05f).coerceIn(0f, 1f))) }, onIncrease = { onConfigChanged(config.copy(colorIntensity = (config.colorIntensity + 0.05f).coerceIn(0f, 1f))) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_text_outline), enabled = config.showTextOutline, onToggle = { onConfigChanged(config.copy(showTextOutline = !config.showTextOutline)) }, accentColor = accentColor)
        Spacer(modifier = Modifier.height(8.dp)); QuickMenuSectionHeader(title = stringResource(R.string.performance_hud_metrics))
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_frame_rate), enabled = config.showFrameRate, onToggle = { onConfigChanged(config.copy(showFrameRate = !config.showFrameRate)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_frame_rate_graph), enabled = config.showFrameRateGraph, onToggle = { onConfigChanged(config.copy(showFrameRateGraph = !config.showFrameRateGraph)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_cpu_usage), enabled = config.showCpuUsage, onToggle = { onConfigChanged(config.copy(showCpuUsage = !config.showCpuUsage)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_cpu_usage_graph), enabled = config.showCpuUsageGraph, onToggle = { onConfigChanged(config.copy(showCpuUsageGraph = !config.showCpuUsageGraph)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_gpu_usage), enabled = config.showGpuUsage, onToggle = { onConfigChanged(config.copy(showGpuUsage = !config.showGpuUsage)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_gpu_usage_graph), enabled = config.showGpuUsageGraph, onToggle = { onConfigChanged(config.copy(showGpuUsageGraph = !config.showGpuUsageGraph)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_ram_usage), enabled = config.showRamUsage, onToggle = { onConfigChanged(config.copy(showRamUsage = !config.showRamUsage)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_battery_level), enabled = config.showBatteryLevel, onToggle = { onConfigChanged(config.copy(showBatteryLevel = !config.showBatteryLevel)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_power_draw), enabled = config.showPowerDraw, onToggle = { onConfigChanged(config.copy(showPowerDraw = !config.showPowerDraw)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_runtime_left), enabled = config.showBatteryRuntime, onToggle = { onConfigChanged(config.copy(showBatteryRuntime = !config.showBatteryRuntime)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_battery_temperature), enabled = config.showBatteryTemperature, onToggle = { onConfigChanged(config.copy(showBatteryTemperature = !config.showBatteryTemperature)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_clock_time), enabled = config.showClockTime, onToggle = { onConfigChanged(config.copy(showClockTime = !config.showClockTime)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_cpu_temperature), enabled = config.showCpuTemperature, onToggle = { onConfigChanged(config.copy(showCpuTemperature = !config.showCpuTemperature)) }, accentColor = accentColor)
        QuickMenuToggleRow(title = stringResource(R.string.performance_hud_gpu_temperature), enabled = config.showGpuTemperature, onToggle = { onConfigChanged(config.copy(showGpuTemperature = !config.showGpuTemperature)) }, accentColor = accentColor)
        if (PowerManager.isFanControlAvailable()) { var showFan by remember { mutableStateOf(PrefManager.showPerformanceHudFan) }; QuickMenuToggleRow(title = stringResource(R.string.power_control_hud_show_fan), enabled = showFan, onToggle = { showFan = !showFan; PrefManager.showPerformanceHudFan = showFan }, accentColor = accentColor) }
        if (PowerManager.isClusterTuningAvailable()) { var showTunerCaps by remember { mutableStateOf(PrefManager.showPerformanceHudTunerCaps) }; QuickMenuToggleRow(title = stringResource(R.string.power_control_hud_show_tuner), enabled = showTunerCaps, onToggle = { showTunerCaps = !showTunerCaps; PrefManager.showPerformanceHudTunerCaps = showTunerCaps }, accentColor = accentColor) }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun LsfgQuickMenuTab(multiplier: Int, flowScale: Float, performanceMode: Boolean, onMultiplierChanged: (Int) -> Unit, onFlowScaleChanged: (Float) -> Unit, onPerformanceModeChanged: (Boolean) -> Unit, presentMode: String, onPresentModeChanged: (String) -> Unit, scrollState: ScrollState, focusRequester: FocusRequester? = null, modifier: Modifier = Modifier) {
    val accentColor = PluviaTheme.colors.accentPurple
    Column(modifier = modifier.verticalScroll(scrollState).focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QuickMenuSectionHeader(title = stringResource(R.string.lsfg_multiplier))
        Row(modifier = Modifier.padding(horizontal = 8.dp).then(if (LocalImmersiveInputBypass.current.active) Modifier.focusGroup() else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 2, 3, 4).forEach { value -> QuickMenuChoiceChip(text = if (value == 0) "Off" else "${value}x", selected = multiplier == value || (value == 0 && multiplier < 2), accentColor = accentColor, onClick = { onMultiplierChanged(value) }, modifier = Modifier.width(56.dp), focusRequester = if (value == 0) focusRequester else null) }
        }
        AnimatedVisibility(visible = multiplier >= 2, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.height(4.dp)); QuickMenuAdjustmentRow(title = stringResource(R.string.lsfg_flow_scale), subtitle = stringResource(R.string.lsfg_flow_scale_desc), valueText = String.format(java.util.Locale.US, "%.2f", flowScale), progress = (flowScale - 0.25f) / 0.75f, onDecrease = { onFlowScaleChanged((flowScale - 0.05f).coerceIn(0.25f, 1.0f)) }, onIncrease = { onFlowScaleChanged((flowScale + 0.05f).coerceIn(0.25f, 1.0f)) }, accentColor = accentColor)
                Spacer(modifier = Modifier.height(4.dp)); QuickMenuToggleRow(title = stringResource(R.string.lsfg_performance_mode), subtitle = stringResource(R.string.lsfg_performance_mode_desc), enabled = performanceMode, onToggle = { onPerformanceModeChanged(!performanceMode) }, accentColor = accentColor)
                Spacer(modifier = Modifier.height(4.dp)); QuickMenuSectionHeader(title = stringResource(R.string.lsfg_present_mode), subtitle = stringResource(R.string.lsfg_present_mode_desc))
                Row(modifier = Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mailbox" to "Mailbox", "fifo" to "FIFO").forEach { (value, label) -> QuickMenuChoiceChip(text = label, selected = presentMode == value, accentColor = accentColor, onClick = { onPresentModeChanged(value) }, modifier = Modifier.width(96.dp)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ImmersiveQuickMenuTab(controls: app.gamenative.ui.screen.xr.ImmersiveControls, scrollState: ScrollState, focusRequester: FocusRequester? = null, modifier: Modifier = Modifier) {
    val accentColor = PluviaTheme.colors.accentPurple; val contentColor = glassContentColor()
    Column(modifier = modifier.verticalScroll(scrollState).focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QuickMenuSectionHeader(title = stringResource(R.string.immersive_passthrough), subtitle = stringResource(R.string.immersive_passthrough_desc))
        QuickMenuToggleRow(title = stringResource(R.string.immersive_passthrough_toggle), enabled = controls.passthroughEnabled, onToggle = { controls.onPassthroughToggle(!controls.passthroughEnabled) }, accentColor = accentColor, focusRequester = focusRequester)
        Spacer(modifier = Modifier.height(4.dp)); QuickMenuSectionHeader(title = stringResource(R.string.immersive_resize_mode_title), subtitle = stringResource(R.string.immersive_resize_mode_desc))
        QuickMenuToggleRow(title = stringResource(R.string.immersive_resize_mode_toggle), enabled = controls.resizeModeEnabled, onToggle = { controls.onResizeModeToggle(!controls.resizeModeEnabled) }, accentColor = accentColor)
        if (controls.directRenderBlockedByEffects != null) {
            Spacer(modifier = Modifier.height(4.dp)); QuickMenuSectionHeader(title = stringResource(R.string.immersive_direct_render_title), subtitle = stringResource(if (controls.directRenderBlockedByEffects) R.string.immersive_reset_effects_status_blocked else R.string.immersive_reset_effects_status_ok))
            val resIntSource = remember { MutableInteractionSource() }; val resIsFocused by resIntSource.collectIsFocusedAsState()
            val resetBgColor = if (resIsFocused) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            val resetBorderModifier = if (resIsFocused) Modifier.border(width = 2.dp, color = accentColor.copy(alpha = 0.7f), shape = RoundedCornerShape(14.dp)) else Modifier
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(resetBgColor).then(resetBorderModifier).selectable(selected = false, interactionSource = resIntSource, indication = null, onClick = controls.onResetScreenEffects).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(R.string.immersive_reset_effects_button), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = contentColor)
                Text(text = stringResource(R.string.immersive_reset_effects_desc), style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun QuickMenuSectionHeader(title: String, subtitle: String? = null) {
    val contentColor = glassContentColor()
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = contentColor, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) { Spacer(modifier = Modifier.height(2.dp)); Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f)) }
    }
}

@Composable
internal fun QuickMenuToggleRow(title: String, enabled: Boolean, selectable: Boolean = true, onToggle: () -> Unit, accentColor: Color, modifier: Modifier = Modifier, subtitle: String? = null, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }; val inputBypass = LocalImmersiveInputBypass.current; val isFocused by interactionSource.collectIsFocusedAsState(); val contentColor = glassContentColor(); val looks = LocalLooksState.current
    LaunchedEffect(isFocused, selectable) { inputBypass.reportActivate(interactionSource, if (isFocused && selectable) onToggle else null) }
    val rowBg = if (isFocused) Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.16f), accentColor.copy(alpha = 0.08f))) else if (looks.isGlassEnabled) Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.05f))) else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)))
    val rowBorderModifier = if (isFocused) Modifier.border(2.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp)) else Modifier
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(rowBg).then(rowBorderModifier).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).hapticSelectable(selected = isFocused, interactionSource = interactionSource, indication = null, onClick = { if (selectable) { onToggle() } }).then(if (inputBypass.active) Modifier else Modifier.focusable(interactionSource = interactionSource)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f).then(if (!selectable) Modifier.alpha(0.5f) else Modifier)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = contentColor, fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) { Spacer(modifier = Modifier.height(4.dp)); Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.7f)) }
        }
        Box(modifier = if (!selectable) Modifier.alpha(0.5f) else Modifier) { QuickMenuSwitch(enabled = enabled, accentColor = accentColor) }
    }
}

@Composable
private fun QuickMenuSwitch(enabled: Boolean, accentColor: Color) {
    val looks = LocalLooksState.current
    Box(modifier = Modifier.width(56.dp).height(32.dp).clip(RoundedCornerShape(999.dp)).background(if (enabled) accentColor else if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)).border(width = 1.dp, color = if (enabled) accentColor.copy(alpha = 0.8f) else if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), shape = RoundedCornerShape(999.dp)).padding(4.dp)) {
        Box(modifier = Modifier.size(22.dp).align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart).background(if (looks.isGlassEnabled && !enabled) Color.Gray else Color.White, CircleShape))
    }
}

@Composable
internal fun QuickMenuAdjustmentRow(title: String, valueText: String, progress: Float, onDecrease: () -> Unit, onIncrease: () -> Unit, accentColor: Color, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null, subtitle: String? = null) {
    val interactionSource = remember { MutableInteractionSource() }; val isFocused by interactionSource.collectIsFocusedAsState(); val shape = RoundedCornerShape(14.dp); var isAdjustmentLocked by remember { mutableStateOf(false) }; val inputBypass = LocalImmersiveInputBypass.current; val contentColor = glassContentColor(); val looks = LocalLooksState.current
    LaunchedEffect(isFocused, isAdjustmentLocked) { inputBypass.reportAdjustment(interactionSource, if (isFocused && isAdjustmentLocked) (onDecrease to onIncrease) else null) }
    val rowBg = if (isFocused) Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.16f), accentColor.copy(alpha = 0.08f))) else if (looks.isGlassEnabled) Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.05f))) else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)))
    val rowBorderModifier = if (isFocused && !isAdjustmentLocked) Modifier.border(2.dp, accentColor.copy(alpha = 0.7f), shape) else Modifier
    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp).clip(shape).background(rowBg).then(rowBorderModifier).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).onFocusChanged { if (!it.isFocused) isAdjustmentLocked = false }.focusable(interactionSource = interactionSource).onPreviewKeyEvent { keyEvent -> if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && isFocused) { when { keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A && (!inputBypass.active || !isAdjustmentLocked) -> { isAdjustmentLocked = if (inputBypass.active) true else !isAdjustmentLocked; true }; isAdjustmentLocked && (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B || (inputBypass.active && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK)) -> { isAdjustmentLocked = false; true }; isAdjustmentLocked && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> { onDecrease(); true }; isAdjustmentLocked && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> { onIncrease(); true }; else -> false } } else false }.hapticSelectable(selected = isFocused, interactionSource = interactionSource, indication = null, onClick = {}).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = contentColor, fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = valueText, style = MaterialTheme.typography.labelLarge, color = if (isFocused) accentColor else contentColor.copy(alpha = 0.7f))
                if (isAdjustmentLocked) Text(text = "●", style = MaterialTheme.typography.labelSmall, color = accentColor)
            }
        }
        if (subtitle != null) { Spacer(modifier = Modifier.height(2.dp)); Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f)) }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickMenuAdjustmentButton(text = "-", rowIsFocused = isFocused, isAdjustmentLocked = isAdjustmentLocked, accentColor = accentColor, onClick = onDecrease)
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)), color = accentColor, trackColor = if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().hapticClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDecrease))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().hapticClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onIncrease))
                }
            }
            QuickMenuAdjustmentButton(text = "+", rowIsFocused = isFocused, isAdjustmentLocked = isAdjustmentLocked, accentColor = accentColor, onClick = onIncrease)
        }
    }
}

@Composable
private fun QuickMenuAdjustmentButton(text: String, rowIsFocused: Boolean, isAdjustmentLocked: Boolean, accentColor: Color, onClick: () -> Unit) {
    val looks = LocalLooksState.current
    val bgColor = if (isAdjustmentLocked) accentColor.copy(alpha = 0.25f) else if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (rowIsFocused) 0.32f else 0.45f)
    val borderColor = if (isAdjustmentLocked) accentColor.copy(alpha = 0.9f) else if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Box(modifier = Modifier.width(44.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(bgColor).border(width = if (isAdjustmentLocked) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp)).hapticClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isAdjustmentLocked) accentColor else glassContentColor().copy(alpha = 0.7f))
    }
}

@Composable
private fun QuickMenuChoiceChip(text: String, selected: Boolean, accentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }; val isFocused by interactionSource.collectIsFocusedAsState(); val shape = RoundedCornerShape(12.dp); val inputBypass = LocalImmersiveInputBypass.current; val contentColor = glassContentColor(); val looks = LocalLooksState.current
    LaunchedEffect(isFocused) { inputBypass.reportActivate(interactionSource, if (isFocused) onClick else null) }
    val borderColor = if (isFocused) accentColor.copy(alpha = 0.7f) else if (selected) accentColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val chipBg = when { selected -> accentColor.copy(alpha = 0.18f); isFocused -> accentColor.copy(alpha = 0.12f); else -> if (looks.isGlassEnabled) Color.Black.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f) }
    Box(modifier = modifier.height(44.dp).then(if (isFocused) Modifier.border(width = 2.dp, color = borderColor, shape = shape) else Modifier.border(width = 1.dp, color = borderColor, shape = shape)).clip(shape).background(chipBg).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).hapticSelectable(selected = selected, interactionSource = interactionSource, indication = null, onClick = onClick).then(if (inputBypass.active) Modifier else Modifier.focusable(interactionSource = interactionSource)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = if (selected || isFocused) accentColor else contentColor, fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickMenuDetailRow(title: String, subtitle: String, accentColor: Color, onActivate: () -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }; val isFocused by interactionSource.collectIsFocusedAsState(); val shape = RoundedCornerShape(14.dp); val contentColor = glassContentColor(); val looks = LocalLooksState.current
    val rowBg = if (isFocused) Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.14f), accentColor.copy(alpha = 0.06f))) else if (looks.isGlassEnabled) Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.05f))) else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)))
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(shape).background(rowBg).then(if (isFocused) Modifier.border(2.dp, accentColor.copy(alpha = 0.8f), shape) else Modifier).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).focusable(interactionSource = interactionSource).onPreviewKeyEvent { keyEvent -> if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && isFocused) { when (keyEvent.nativeKeyEvent.keyCode) { KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onActivate(); true }; else -> false } } else false }.hapticSelectable(selected = isFocused, interactionSource = interactionSource, indication = null, onClick = onActivate).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = if (isFocused) accentColor else contentColor, fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
            Spacer(modifier = Modifier.height(4.dp)); Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = if (isFocused) accentColor.copy(alpha = 0.92f) else contentColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LooksQuickMenuTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val looks = LocalLooksState.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        QuickMenuToggleRow(
            title = stringResource(R.string.settings_looks_jiggly_title),
            subtitle = stringResource(R.string.settings_looks_jiggly_subtitle),
            enabled = looks.isJigglyAnimations,
            onToggle = { viewModel.setJigglyAnimations(!looks.isJigglyAnimations) },
            accentColor = looks.mainColor
        )

        QuickMenuToggleRow(
            title = stringResource(R.string.settings_looks_glass_title),
            subtitle = stringResource(R.string.settings_looks_glass_subtitle),
            enabled = looks.isGlassEnabled,
            onToggle = { viewModel.setGlassPanels(!looks.isGlassEnabled) },
            accentColor = looks.mainColor
        )

        AnimatedVisibility(
            visible = looks.isGlassEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickMenuToggleRow(
                    title = stringResource(R.string.settings_looks_true_glass_title),
                    subtitle = stringResource(R.string.settings_looks_true_glass_subtitle),
                    enabled = looks.isTrueGlassEnabled,
                    onToggle = { viewModel.setTrueGlassEnabled(!looks.isTrueGlassEnabled) },
                    accentColor = looks.mainColor
                )

                QuickMenuAdjustmentRow(
                    title = if (looks.isTrueGlassEnabled) "Blur Intensity" else "Glass Transparency",
                    valueText = if (looks.isTrueGlassEnabled) {
                        "${(5f + looks.glassTransparencyOrBlur * 45f).roundToInt()}px"
                    } else {
                        "${(0.05f + looks.glassTransparencyOrBlur * 0.75f).let { (it * 100).roundToInt() }}%"
                    },
                    progress = looks.glassTransparencyOrBlur,
                    onDecrease = {
                        val newNormalized = (looks.glassTransparencyOrBlur - 0.05f).coerceIn(0f, 1f)
                        if (looks.isTrueGlassEnabled) viewModel.setGlassBlurIntensity(5f + newNormalized * 45f)
                        else viewModel.setGlassTransparency(0.05f + newNormalized * 0.75f)
                    },
                    onIncrease = {
                        val newNormalized = (looks.glassTransparencyOrBlur + 0.05f).coerceIn(0f, 1f)
                        if (looks.isTrueGlassEnabled) viewModel.setGlassBlurIntensity(5f + newNormalized * 45f)
                        else viewModel.setGlassTransparency(0.05f + newNormalized * 0.75f)
                    },
                    accentColor = looks.mainColor
                )

                QuickMenuColorPickerRow(
                    title = "Glass Colour",
                    currentColor = looks.glassColor,
                    onColorSelected = { viewModel.setGlassColor(it) },
                    accentColor = looks.mainColor
                )
            }
        }

        val fontOptions = FontType.entries.map { stringResource(it.labelResId) }
        val currentFontIndex = FontType.entries.indexOfFirst {
            when (it) {
                FontType.NDOT -> looks.fontType == NdotFontFamily
                FontType.NORMAL -> looks.fontType == PTMonoFontFamily
            }
        }.coerceAtLeast(0)

        SettingsListDropdown(
            colors = settingsTileColorsAlt(),
            value = currentFontIndex,
            items = fontOptions,
            onItemSelected = { index ->
                viewModel.setFontType(FontType.entries[index])
            },
            title = { Text(text = stringResource(R.string.settings_looks_font_type_title)) }
        )

        QuickMenuColorPickerRow(
            title = "Main Colour",
            currentColor = looks.mainColor,
            onColorSelected = { viewModel.setMainColor(it) },
            accentColor = looks.mainColor
        )

        QuickMenuColorPickerRow(
            title = "Wallpaper Colour",
            currentColor = looks.wallpaperColor,
            onColorSelected = { viewModel.setWallpaperColor(it) },
            accentColor = looks.mainColor
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun QuickMenuColorPickerRow(
    title: String,
    currentColor: Color,
    onColorSelected: (String) -> Unit,
    accentColor: Color
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
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (expanded) accentColor.copy(alpha = 0.08f) else Color.Transparent)
            .hapticClickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(currentColor)
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
                            val isSelected = hex.toColor() == currentColor
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .hapticClickable { onColorSelected(hex) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(hex.toColor())
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) accentColor else contentColor.copy(alpha = 0.1f),
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

@Composable
private fun RefreshRateQuickMenuTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accentColor = LocalLooksState.current.mainColor
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        QuickMenuSectionHeader(title = stringResource(R.string.settings_looks_refresh_rate))

        val hzOptions = listOf(30, 60, 90, 120, 144)
        val currentHz = state.maxRefreshRate

        QuickMenuAdjustmentRow(
            title = "Target Hz",
            valueText = "$currentHz Hz",
            progress = (hzOptions.indexOf(currentHz).toFloat() / (hzOptions.size - 1)).coerceIn(0f, 1f),
            onDecrease = {
                val currentIndex = hzOptions.indexOf(currentHz)
                if (currentIndex > 0) viewModel.setMaxRefreshRate(hzOptions[currentIndex - 1])
            },
            onIncrease = {
                val currentIndex = hzOptions.indexOf(currentHz)
                if (currentIndex < hzOptions.size - 1) viewModel.setMaxRefreshRate(hzOptions[currentIndex + 1])
            },
            accentColor = accentColor
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun Preview_QuickMenu() {
    PluviaTheme { Box(modifier = Modifier.fillMaxSize()) { QuickMenu(isVisible = true, onDismiss = {}, onItemSelected = { false }, hasPhysicalController = false) } }
}
