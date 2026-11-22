package app.gamenative.ui.screen.library

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import timber.log.Timber
import app.gamenative.ui.util.SnackbarManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.gamenative.BuildConfig
import app.gamenative.PrefManager
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.GameCompatibilityStatus
import app.gamenative.data.FavoritesManager
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.component.GamepadAction
import app.gamenative.ui.component.GamepadActionBar
import app.gamenative.ui.component.GamepadButton
import app.gamenative.ui.component.LibraryActions
import app.gamenative.ui.components.rememberCustomGameFolderPicker
import app.gamenative.ui.components.requestPermissionsForPath
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.LibraryTab
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.enums.SortOption
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.model.LibraryViewModel
import app.gamenative.service.SteamService
import app.gamenative.ui.theme.LocalBackgroundLayer
import app.gamenative.ui.theme.LocalBrokenGlassBackdrop
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.layerBackdropIfActive
import app.gamenative.ui.theme.rememberBrokenGlassBackdropIfEnabled
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import android.provider.Settings
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import app.gamenative.playground.PlaygroundWidgetHostManager
import app.gamenative.ui.component.AndroidAppPicker
import app.gamenative.ui.screen.library.components.Library360Carousel
import app.gamenative.ui.screen.library.components.PlaygroundDock
import app.gamenative.ui.screen.library.components.PlaygroundItem
import app.gamenative.ui.screen.library.components.PlaygroundSettings
import app.gamenative.ui.screen.library.components.PlaygroundSettingsPanel
import app.gamenative.ui.screen.library.components.PlaygroundTab
import app.gamenative.ui.screen.library.components.LibraryCarouselPane
import app.gamenative.ui.screen.library.components.LibraryDetailPane
import app.gamenative.ui.screen.library.components.LibraryListPane
import app.gamenative.ui.screen.library.components.LibraryFavoritesEmptyState
import app.gamenative.ui.screen.library.components.RecommendationDisclosureDialog
import app.gamenative.ui.screen.library.components.LibraryOptionsPanel
import app.gamenative.ui.screen.library.components.LibrarySearchBar
import app.gamenative.ui.screen.library.components.LibrarySourceNotLoggedInSplash
import app.gamenative.ui.screen.library.components.LibraryTabBar
import app.gamenative.ui.screen.library.components.toggleFavorite
import app.gamenative.ui.screen.auth.AmazonOAuthActivity
import app.gamenative.ui.screen.auth.EpicOAuthActivity
import app.gamenative.ui.screen.auth.GOGOAuthActivity
import app.gamenative.ui.screen.library.components.SystemMenu
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.PlatformAuthUiHelpers
import app.gamenative.ui.util.PlatformLogoutCallbacks
import app.gamenative.service.amazon.AmazonService
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGService
import app.gamenative.utils.CustomGameScanner
import app.gamenative.utils.PlatformOAuthHandlers
import app.gamenative.utils.SteamUtils
import com.posthog.PostHog
import kotlinx.coroutines.launch
import android.os.SystemClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onPlayWithDiagnostics: (String) -> Unit,
    onAiDebugRun: (String) -> Unit,
    onNavigateRoute: (String) -> Unit,
    onLogout: () -> Unit,
    onGoOnline: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    isOffline: Boolean = false,
    isSteamConnected: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var showPlaygroundSettings by remember { mutableStateOf(false) }
    var showAndroidAppPicker by remember { mutableStateOf(false) }
    var playgroundEditingItemId by remember { mutableStateOf<String?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }

    LibraryScreenContent(
        state = state,
        importState = importState,
        onImportCustomGame = viewModel::importCustomGame,
        listState = viewModel.listState,
        sheetState = sheetState,
        onFilterChanged = viewModel::onFilterChanged,
        onPageChange = viewModel::onPageChange,
        onModalBottomSheet = viewModel::onModalBottomSheet,
        onIsSearching = viewModel::onIsSearching,
        onSearchQuery = viewModel::onSearchQuery,
        onRefresh = viewModel::onRefresh,
        onClickPlay = onClickPlay,
        onTestGraphics = onTestGraphics,
        onPlayWithDiagnostics = onPlayWithDiagnostics,
        onAiDebugRun = onAiDebugRun,
        onNavigateRoute = onNavigateRoute,
        onLogout = onLogout,
        onGoOnline = onGoOnline,
        onDownloadsClick = onDownloadsClick,
        onSourceToggle = viewModel::onSourceToggle,
        onAddCustomGameFolder = viewModel::addCustomGameFolder,
        onSortOptionChanged = viewModel::onSortOptionChanged,
        onSteamCollectionToggle = viewModel::onSteamCollectionToggle,
        onClearSteamCollections = viewModel::onClearSteamCollections,
        onCuratedListToggle = viewModel::onCuratedListToggle,
        onClearCuratedLists = viewModel::onClearCuratedLists,
        onFocusedIndexChanged = viewModel::onFocusedIndexChanged,
        onSetGameViewState = viewModel::onSetGameViewState,
        onOptionsPanelToggle = viewModel::onOptionsPanelToggle,
        onTabChanged = viewModel::onTabChanged,
        onPreviousTab = viewModel::onPreviousTab,
        onNextTab = viewModel::onNextTab,
        onPlaygroundSettingsChange = viewModel::onPlaygroundSettingsChange,
        onPlaygroundItemMoved = viewModel::onPlaygroundItemMoved,
        onPlaygroundItemRemoved = viewModel::onPlaygroundItemRemoved,
        onTogglePlaygroundItemDockStatus = viewModel::onTogglePlaygroundItemDockStatus,
        playgroundEditingItemId = playgroundEditingItemId,
        onPlaygroundEditItem = { id ->
            playgroundEditingItemId = if (playgroundEditingItemId == id) null else id
        },
        onShowPlaygroundSettings = { showPlaygroundSettings = true },
        onShowAndroidAppPickerChange = { showAndroidAppPicker = it },
        onShowWidgetPicker = { showWidgetPicker = true },
        isOffline = isOffline,
        isSteamConnected = isSteamConnected,
    )

    if (showPlaygroundSettings) {
        ModalBottomSheet(
            onDismissRequest = { showPlaygroundSettings = false },
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        ) {
            PlaygroundSettingsPanel(
                settings = state.playgroundSettings,
                onSettingsChange = viewModel::onPlaygroundSettingsChange,
                onOpenAndroidSettings = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                onAddApps = {
                    showPlaygroundSettings = false
                    showAndroidAppPicker = true
                },
                onAddWidget = {
                    showPlaygroundSettings = false
                    showWidgetPicker = true
                },
                onLaunchDesktop = {
                    showPlaygroundSettings = false
                    try {
                        val intent = Intent(context, com.droiddeck.launcher.SessionActivity::class.java).apply {
                            putExtra(com.droiddeck.launcher.session.SessionService.EXTRA_MODE, com.droiddeck.launcher.session.SessionService.MODE_DESKTOP)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to launch DroidDeck SessionActivity")
                        SnackbarManager.show("Error launching Desktop: ${e.message}")
                    }
                },
            )
        }
    }

    if (showAndroidAppPicker) {
        AndroidAppPicker(
            onAppsSelected = { packageNames ->
                if (state.currentTab == LibraryTab.PLAYGROUND) {
                    packageNames.forEach { pkg ->
                        val pm = context.packageManager
                        val className = pm.getLaunchIntentForPackage(pkg)?.component?.className ?: ""
                        viewModel.addAppToPlayground(pkg, className)
                    }
                } else {
                    packageNames.forEach { pkg -> viewModel.addAndroidApp(pkg) }
                }
                showAndroidAppPicker = false
            },
            onDismiss = { showAndroidAppPicker = false },
        )
    }

    if (showWidgetPicker) {
        val hostManager = remember { PlaygroundWidgetHostManager(context) }
        val providers = remember { hostManager.getAvailableWidgets() }

        ModalBottomSheet(
            onDismissRequest = { showWidgetPicker = false },
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        ) {
            val pm = context.packageManager
            Text(
                "Pick a Widget",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            ) {
                items(providers) { info ->
                    ListItem(
                        headlineContent = { Text(info.loadLabel(pm)) },
                        supportingContent = { Text(info.provider.packageName) },
                        modifier = Modifier.clickable {
                            PluviaApp.events.emit(AndroidEvent.AddWidget(hostManager, info))
                            showWidgetPicker = false
                        },
                    )
                }
            }
        }
    }
}

private fun isGameControllerConnected(): Boolean =
    InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id) ?: return@any false
        val sources = device.sources
        sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenContent(
    state: LibraryState,
    listState: LazyGridState,
    sheetState: SheetState,
    importState: LibraryViewModel.CustomGameImportState = LibraryViewModel.CustomGameImportState(),
    onImportCustomGame: (Uri, Boolean) -> Unit = { _, _ -> },
    onFilterChanged: (AppFilter) -> Unit,
    onPageChange: (Int) -> Unit,
    onModalBottomSheet: (Boolean) -> Unit,
    onIsSearching: (Boolean) -> Unit,
    onSearchQuery: (String) -> Unit,
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onPlayWithDiagnostics: (String) -> Unit,
    onAiDebugRun: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onLogout: () -> Unit,
    onGoOnline: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onSourceToggle: (GameSource) -> Unit,
    onAddCustomGameFolder: (String) -> Unit,
    onSortOptionChanged: (SortOption) -> Unit,
    onSteamCollectionToggle: (String) -> Unit,
    onClearSteamCollections: () -> Unit,
    onCuratedListToggle: (String) -> Unit,
    onClearCuratedLists: () -> Unit,
    onFocusedIndexChanged: (Int) -> Unit = {},
    onSetGameViewState: (app.gamenative.ui.data.GameViewState) -> Unit = {},
    onPlaygroundSettingsChange: (PlaygroundSettings) -> Unit = {},
    onPlaygroundItemMoved: (id: String, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onPlaygroundItemRemoved: (String) -> Unit = {},
    onTogglePlaygroundItemDockStatus: (String, Float?, Float?) -> Unit = { _, _, _ -> },
    playgroundEditingItemId: String? = null,
    onPlaygroundEditItem: (String?) -> Unit = {},
    onShowPlaygroundSettings: () -> Unit = {},
    onShowAndroidAppPickerChange: (Boolean) -> Unit = {},
    onShowWidgetPicker: () -> Unit = {},
    onOptionsPanelToggle: (Boolean) -> Unit,
    onTabChanged: (LibraryTab) -> Unit,
    onPreviousTab: () -> Unit,
    onNextTab: () -> Unit,
    isOffline: Boolean = false,
    isSteamConnected: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope

    val gogOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_ERROR)
                ?: context.getString(R.string.gog_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        val code = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)
        if (code == null) {
            val message = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_ERROR)
                ?: context.getString(R.string.gog_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        lifecycleScope.launch {
            PlatformOAuthHandlers.handleGogAuthentication(
                context = context,
                authCode = code,
                coroutineScope = lifecycleScope,
                onLoadingChange = { },
                onError = { msg ->
                    if (msg != null) {
                        SnackbarManager.show(msg)
                    }
                },
                onSuccess = {
                    SnackbarManager.show(context.getString(R.string.gog_login_success_title))
                },
                onDialogClose = { },
            )
        }
    }

    val epicOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_ERROR)
                ?: context.getString(R.string.epic_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        val code = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_AUTH_CODE)
        if (code == null) {
            val message = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_ERROR)
                ?: context.getString(R.string.epic_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        lifecycleScope.launch {
            PlatformOAuthHandlers.handleEpicAuthentication(
                context = context,
                authCode = code,
                coroutineScope = lifecycleScope,
                onLoadingChange = { },
                onError = { msg ->
                    if (msg != null) {
                        SnackbarManager.show(msg)
                    }
                },
                onSuccess = {
                    SnackbarManager.show(context.getString(R.string.epic_login_success_title))
                },
                onDialogClose = { },
            )
        }
    }

    val amazonOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(AmazonOAuthActivity.EXTRA_ERROR)
                ?: context.getString(R.string.amazon_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        val code = result.data?.getStringExtra(AmazonOAuthActivity.EXTRA_AUTH_CODE)
        if (code == null) {
            val message = result.data?.getStringExtra(AmazonOAuthActivity.EXTRA_ERROR)
                ?: context.getString(R.string.amazon_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        lifecycleScope.launch {
            PlatformOAuthHandlers.handleAmazonAuthentication(
                context = context,
                authCode = code,
                coroutineScope = lifecycleScope,
                onLoadingChange = { },
                onError = { msg ->
                    if (msg != null) {
                        SnackbarManager.show(msg)
                    }
                },
                onSuccess = {
                    SnackbarManager.show(context.getString(R.string.amazon_login_success_title))
                },
                onDialogClose = { },
            )
        }
    }

    val xboxOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            app.gamenative.service.xbox.XboxService.start(context)
            app.gamenative.service.xbox.XboxService.triggerLibrarySync(context)
            SnackbarManager.show(context.getString(R.string.xbox_login_success_title))
        }
    }

    var selectedAppId by remember { mutableStateOf<String?>(null) }
    val carouselListState = rememberLazyListState()
    val isViewWide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var currentPaneType by remember { mutableStateOf(PrefManager.libraryLayout) }
    var recDisclosureShown by remember { mutableStateOf(PrefManager.recDisclosureShown) }
    var showRecTeaserDialog by remember { mutableStateOf(false) }
    val onRecTeaserTapped = {
        if (PrefManager.usageAnalyticsEnabled) PostHog.capture(event = "rec_teaser_tapped")
        showRecTeaserDialog = true
    }
    val recTeaserVisible = state.appInfoList.firstOrNull()?.isRecTeaser == true
    LaunchedEffect(recTeaserVisible) {
        if (recTeaserVisible && PrefManager.usageAnalyticsEnabled) {
            PostHog.capture(event = "rec_teaser_shown")
        }
    }

    // Initialize layout if undecided
    LaunchedEffect(Unit) {
        if (currentPaneType == PaneType.UNDECIDED) {
            currentPaneType = if (isViewWide) PaneType.GRID_HERO else PaneType.GRID_CAPSULE
            PrefManager.libraryLayout = currentPaneType
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val gridFirstItemFocusRequester = remember { FocusRequester() }
    val carouselFocusRequester = remember { FocusRequester() }
    val mode360FocusRequester = remember { FocusRequester() }
    var gridFocusTargetListIndex by remember { mutableIntStateOf(0) }
    var carouselFocusTargetListIndex by remember { mutableIntStateOf(0) }
    var mode360FocusTargetListIndex by remember { mutableIntStateOf(0) }
    var pendingGridFocusRequest by remember { mutableStateOf(false) }
    var pendingCarouselFocusRequest by remember { mutableStateOf(false) }

    var recommendationItemCount by remember { mutableIntStateOf(0) }

    var isSystemMenuOpen by remember { mutableStateOf(false) }
    // Track previous overlay states to detect when they close
    var wasSystemMenuOpen by remember { mutableStateOf(false) }
    var wasOptionsPanelOpen by remember { mutableStateOf(false) }
    // Keep a stable reference to the selected item so detail view doesn't disappear during list refresh/pagination.
    var selectedLibraryItem by remember { mutableStateOf<LibraryItem?>(null) }
    val favorites by FavoritesManager.favorites.collectAsStateWithLifecycle()
    val favoritesLoaded by FavoritesManager.loaded.collectAsStateWithLifecycle()
    val filterFabExpanded by remember(currentPaneType, listState, carouselListState) {
        derivedStateOf {
            if (currentPaneType == PaneType.CAROUSEL) {
                carouselListState.firstVisibleItemIndex == 0
            } else {
                listState.firstVisibleItemIndex == 0
            }
        }
    }

    // Dialog state for add custom game prompt
    var showAddCustomGameDialog by remember { mutableStateOf(false) }
    var showImportTypeDialog by remember { mutableStateOf(false) }
    var dontShowAgain by remember { mutableStateOf(false) }
    var previousAppCount by remember { mutableIntStateOf(state.appInfoList.size) }
    var controllerBootstrapNeeded by remember { mutableStateOf(true) }
    var rootHasFocus by remember { mutableStateOf(false) }
    // True while focus lives in the top tab bar. The delayed focus-restoration effects below must
    // not yank focus back to the grid when the user has moved up into the tab bar (the action
    // buttons would otherwise light up for ~100ms and then lose focus).
    var tabBarHasFocus by remember { mutableStateOf(false) }
    var lastBootstrapAtMs by remember { mutableLongStateOf(0L) }

    fun getContentLastIndex(): Int {
        return if (state.currentTab == LibraryTab.RECOMMENDED) {
            (recommendationItemCount - 1).coerceAtLeast(0)
        } else {
            state.appInfoList.lastIndex.coerceAtLeast(0)
        }
    }

    fun firstVisibleContentIndex(): Int {
        val lastIndex = getContentLastIndex()
        if (lastIndex < 0) return 0

        return if (currentPaneType == PaneType.CAROUSEL) {
            carouselListState.firstVisibleItemIndex.coerceIn(0, lastIndex)
        } else {
            listState.firstVisibleItemIndex.coerceIn(0, lastIndex)
        }
    }

    fun currentCarouselFocusTargetIndex(): Int {
        val lastIndex = getContentLastIndex()
        if (lastIndex < 0) return 0

        return carouselFocusTargetListIndex.coerceIn(0, lastIndex)
    }

    fun preferredContentFocusIndex(): Int =
        if (currentPaneType == PaneType.CAROUSEL) currentCarouselFocusTargetIndex() else firstVisibleContentIndex()

    val inputModeManager = LocalInputModeManager.current
    fun ensureKeyboardInputMode() {
        if (isGameControllerConnected()) {
            if (inputModeManager.inputMode != InputMode.Keyboard) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
            }
        }
    }

    // Moved all state.appInfoList.isNotEmpty() checking to this function
    fun isListFocusable(): Boolean = state.appInfoList.isNotEmpty() || state.currentTab == LibraryTab.RECOMMENDED

    fun requestGridFocusOrDefer() {
        if (!isListFocusable()) return
        ensureKeyboardInputMode()
        try {
            gridFirstItemFocusRequester.requestFocus()
            pendingGridFocusRequest = false
            lastBootstrapAtMs = SystemClock.uptimeMillis()
        } catch (_: IllegalStateException) {
            pendingGridFocusRequest = true
        }
    }

    fun requestCarouselFocusOrDefer(targetListIndex: Int = currentCarouselFocusTargetIndex()) {
        if (!isListFocusable()) return
        ensureKeyboardInputMode()
        carouselFocusTargetListIndex = targetListIndex.coerceIn(0, getContentLastIndex())
        try {
            carouselFocusRequester.requestFocus()
            pendingCarouselFocusRequest = false
            lastBootstrapAtMs = SystemClock.uptimeMillis()
        } catch (_: IllegalStateException) {
            pendingCarouselFocusRequest = true
        }
    }

    fun requestContentFocusOrDefer(targetListIndex: Int = preferredContentFocusIndex()) {
        if (!isListFocusable()) return
        if (currentPaneType == PaneType.CAROUSEL) {
            requestCarouselFocusOrDefer(targetListIndex)
        } else if (currentPaneType == PaneType.MODE_360) {
            try {
                mode360FocusRequester.requestFocus()
            } catch (_: IllegalStateException) {}
        } else {
            gridFocusTargetListIndex = targetListIndex
            requestGridFocusOrDefer()
        }
    }

    fun requestRootFocusSafe() {
        ensureKeyboardInputMode()
        try {
            rootFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {}
    }

    fun focusedLibraryItem(): LibraryItem? {
        if (state.currentTab == LibraryTab.RECOMMENDED) return null
        val focusedIndex = when (currentPaneType) {
            PaneType.CAROUSEL -> currentCarouselFocusTargetIndex()
            PaneType.MODE_360 -> mode360FocusTargetListIndex
            else -> gridFocusTargetListIndex
        }
        return state.appInfoList.getOrNull(focusedIndex)?.takeUnless { it.isRecommended }
    }

    fun toggleFocusedFavorite(): Boolean {
        val item = focusedLibraryItem() ?: return false
        toggleFavorite(context, item.appId, item.name)
        return true
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    val folderPicker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            // When a folder is selected via OpenDocumentTree, the user has already granted
            // URI permissions for that specific folder. We should verify we can access it
            // rather than checking for broad storage permissions.
            val folder = java.io.File(path)
            val canAccess = try {
                folder.exists() && (folder.isDirectory && folder.canRead())
            } catch (e: Exception) {
                false
            }

            // Only request permissions if we can't access the folder AND it's outside the sandbox
            // (folders selected via OpenDocumentTree should already be accessible)
            if (!canAccess && !CustomGameScanner.hasStoragePermission(context, path)) {
                requestPermissionsForPath(context, path, storagePermissionLauncher)
            }
            onAddCustomGameFolder(path)
        },
        onFailure = { message ->
            SnackbarManager.show(message)
        },
    )

    // Modern add path: import the picked folder into app-owned storage via the SAF grant,
    // since the map-in-place flow needs MANAGE_EXTERNAL_STORAGE
    var showModernImportDialog by remember { mutableStateOf(false) }
    var importRemoveOriginal by rememberSaveable { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onImportCustomGame(uri, importRemoveOriginal)
        }
    }

    // Handle opening folder picker (with dialog check)
    val onAddCustomGameClick = {
        if (state.currentTab == LibraryTab.PLAYGROUND) {
            onShowPlaygroundSettings()
        } else {
            showImportTypeDialog = true
        }
    }

    BackHandler(enabled = isSystemMenuOpen) {
        isSystemMenuOpen = false
    }

    BackHandler(enabled = state.isOptionsPanelOpen) {
        onOptionsPanelToggle(false)
    }

    BackHandler(enabled = state.isSearching && selectedAppId == null) {
        onIsSearching(false)
        onSearchQuery("")
    }

    BackHandler(selectedLibraryItem != null) {
        selectedAppId = null
        selectedLibraryItem = null
    }

    // Restore focus when returning from game detail (without reloading list)
    LaunchedEffect(selectedAppId) {
        if (selectedAppId != null) {
            controllerBootstrapNeeded = true
        }
        if (selectedAppId == null) {
            // Brief delay to let the UI settle after transition
            kotlinx.coroutines.delay(100)
            // Restore focus to content area
            if (isListFocusable()) {
                requestContentFocusOrDefer()
            } else {
                requestRootFocusSafe()
            }
        }
    }


    // Padding for the library *list* view (tab bar, grid, search bar) so content
    // never draws behind the display cutout. The window now opts in to
    // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES via Theme.Pluvia, so:
    //   * Status bar visible (portrait):   statusBars insets already cover the top notch.
    //   * Status bar hidden (portrait):    statusBars insets are 0; displayCutout supplies
    //                                       the notch height so content isn't behind the notch.
    //   * Landscape (cutout on a side):    statusBars is top-only; displayCutout supplies
    //                                       the side inset so the tab bar isn't clipped.
    // Bottom is intentionally excluded so scroll content can reach the bottom edge.
    //
    // The detail (game) page deliberately does NOT use this — the hero image is meant
    // to bleed through the cutout, so AppScreenContent insets only the elements that
    // need to stay tappable (e.g. the back button) instead.
    val safePaddingModifier = if (selectedLibraryItem == null) {
        Modifier.windowInsetsPadding(
            WindowInsets.statusBars
                .union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        )
    } else {
        Modifier
    }

    // Restore focus after tab change - handles both empty and populated tabs
    LaunchedEffect(state.currentTab) {
        // Brief delay to let list populate after tab change
        kotlinx.coroutines.delay(150)

        // The user may have moved focus up into the tab bar during the delay; don't yank it back.
        if (tabBarHasFocus) return@LaunchedEffect

        if (isListFocusable()) {
            // Empty tab - focus root so bumpers still work
            requestRootFocusSafe()
        } else {
            // Tab has content - focus the first content item/container
            requestContentFocusOrDefer(targetListIndex = 0)
        }
    }

    LaunchedEffect(
        pendingGridFocusRequest,
        gridFocusTargetListIndex,
        state.appInfoList.size,
        selectedAppId,
        isSystemMenuOpen,
        state.isOptionsPanelOpen,
        state.isSearching,
    ) {
        if (pendingGridFocusRequest && isListFocusable()) {
            if (selectedAppId == null && !isSystemMenuOpen && !state.isOptionsPanelOpen && !state.isSearching) {
                var retries = 0
                while (pendingGridFocusRequest && retries < 8) {
                    try {
                        gridFirstItemFocusRequester.requestFocus()
                        pendingGridFocusRequest = false
                    } catch (_: IllegalStateException) {
                        retries++
                        // FocusRequester can be temporarily detached during recomposition.
                        kotlinx.coroutines.delay(32)
                    }
                }
            }
        }
    }

    LaunchedEffect(
        pendingCarouselFocusRequest,
        carouselFocusTargetListIndex,
        state.appInfoList.size,
        selectedAppId,
        isSystemMenuOpen,
        state.isOptionsPanelOpen,
        state.isSearching,
    ) {
        if (pendingCarouselFocusRequest && isListFocusable()) {
            if (selectedAppId == null && !isSystemMenuOpen && !state.isOptionsPanelOpen && !state.isSearching) {
                val targetIndex = currentCarouselFocusTargetIndex()
                if (carouselListState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
                    carouselListState.scrollToItem(targetIndex)
                }
                var retries = 0
                while (pendingCarouselFocusRequest && retries < 8) {
                    try {
                        carouselFocusRequester.requestFocus()
                        pendingCarouselFocusRequest = false
                    } catch (_: IllegalStateException) {
                        retries++
                        kotlinx.coroutines.delay(32)
                    }
                }
            }
        }
    }

    // If the app list starts empty and populates later, bootstrap controller focus once content is ready.
    LaunchedEffect(
        state.appInfoList.size,
        selectedAppId,
        isSystemMenuOpen,
        state.isOptionsPanelOpen,
        state.isSearching,
    ) {
        val currentCount = state.appInfoList.size
        val listBecameNonEmpty = previousAppCount == 0 && currentCount > 0
        val listBecameEmpty = previousAppCount > 0 && currentCount == 0

        if (listBecameNonEmpty && selectedAppId == null && !isSystemMenuOpen && !state.isOptionsPanelOpen && !state.isSearching && !tabBarHasFocus) {
            requestContentFocusOrDefer()
        }
        if (listBecameEmpty && selectedAppId == null && !isSystemMenuOpen && !state.isOptionsPanelOpen && !state.isSearching && !tabBarHasFocus) {
            // Empty tabs can drop focused children; re-anchor focus at the root so bumper nav keeps working.
            requestRootFocusSafe()
        }

        previousAppCount = currentCount
    }

    // Restore focus when System Menu or Options Panel closes
    LaunchedEffect(isSystemMenuOpen, state.isOptionsPanelOpen) {
        val systemMenuJustClosed = wasSystemMenuOpen && !isSystemMenuOpen
        val optionsPanelJustClosed = wasOptionsPanelOpen && !state.isOptionsPanelOpen

        if ((systemMenuJustClosed || optionsPanelJustClosed) && !state.isSearching) {
            // Give a brief moment for the overlay to animate out
            kotlinx.coroutines.delay(50)
            // Restore focus to the active content layout
            if (isListFocusable()) {
                requestContentFocusOrDefer()
            } else {
                // Empty list - focus root so bumpers still work
                requestRootFocusSafe()
            }
        }

        // Update previous state trackers
        wasSystemMenuOpen = isSystemMenuOpen
        wasOptionsPanelOpen = state.isOptionsPanelOpen
    }

    // Global key/motion bootstrap path for cases where Compose focus was lost by touch mode.
    // This runs at the app event bus layer, independent of current Compose focus target.
    // Helper functions defined in composable scope to capture latest state on each recomposition.
    val canBootstrapContentFocus: () -> Boolean = {
        val now = SystemClock.uptimeMillis()
        selectedAppId == null &&
            !isSystemMenuOpen &&
            !state.isOptionsPanelOpen &&
            !state.isSearching &&
            isListFocusable() &&
            controllerBootstrapNeeded &&
            !rootHasFocus &&
            !tabBarHasFocus &&
            (now - lastBootstrapAtMs) > 250L
    }
    val canNavigateTabsWithoutFocus: () -> Boolean = {
        selectedAppId == null &&
            !isSystemMenuOpen &&
            !state.isOptionsPanelOpen &&
            !state.isSearching &&
            !rootHasFocus
    }

    DisposableEffect(Unit) {
        val onGlobalKeyEvent: (AndroidEvent.KeyEvent) -> Boolean = { androidEvent ->
            val event = androidEvent.event
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_L1 -> {
                        if (canNavigateTabsWithoutFocus()) {
                            onPreviousTab()
                            requestRootFocusSafe()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_BUTTON_R1 -> {
                        if (canNavigateTabsWithoutFocus()) {
                            onNextTab()
                            requestRootFocusSafe()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_BUTTON_L2,
                    KeyEvent.KEYCODE_BUTTON_R2,
                    KeyEvent.KEYCODE_BUTTON_THUMBL,
                    KeyEvent.KEYCODE_BUTTON_THUMBR,
                    -> {
                        if (canBootstrapContentFocus()) {
                            requestContentFocusOrDefer()
                            // Do not consume: let normal key routing continue after bootstrap.
                            false
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
        }

        val onGlobalMotionEvent: (AndroidEvent.MotionEvent) -> Boolean = { androidEvent ->
            val event = androidEvent.event
            if (event == null || !canBootstrapContentFocus()) {
                false
            } else {
                val isMoveLike = event.actionMasked == MotionEvent.ACTION_MOVE
                val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                val leftX = event.getAxisValue(MotionEvent.AXIS_X)
                val leftY = event.getAxisValue(MotionEvent.AXIS_Y)
                val hasDirectionalAxis = kotlin.math.abs(hatX) >= 0.5f ||
                    kotlin.math.abs(hatY) >= 0.5f ||
                    kotlin.math.abs(leftX) >= 0.6f ||
                    kotlin.math.abs(leftY) >= 0.6f

                if (isMoveLike && hasDirectionalAxis) {
                    requestContentFocusOrDefer()
                    // Do not consume: allow normal movement handling after bootstrap.
                    false
                } else {
                    false
                }
            }
        }

        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(onGlobalKeyEvent)
        PluviaApp.events.on<AndroidEvent.MotionEvent, Boolean>(onGlobalMotionEvent)

        onDispose {
            PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(onGlobalKeyEvent)
            PluviaApp.events.off<AndroidEvent.MotionEvent, Boolean>(onGlobalMotionEvent)
        }
    }

    val rootWallpaperBackdrop = LocalBrokenGlassBackdrop.current
    val rootWallpaperLayer = LocalBackgroundLayer.current
    // Hard-off gate: no content GraphicsLayer and no per-frame layerBackdrop recording when Glass is disabled.
    val contentBackdrop = rememberBrokenGlassBackdropIfEnabled(LocalLooksState.current.isGlassEnabled)
    val libraryChromeBackdrop = when {
        contentBackdrop == null -> null
        rootWallpaperBackdrop != null -> rememberCombinedBackdrop(rootWallpaperBackdrop, contentBackdrop)
        else -> contentBackdrop
    }

    CompositionLocalProvider(
        LocalBrokenGlassBackdrop provides libraryChromeBackdrop,
        LocalBackgroundLayer provides contentBackdrop?.graphicsLayer,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .then(safePaddingModifier)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                rootHasFocus = focusState.hasFocus
                if (focusState.hasFocus) {
                    controllerBootstrapNeeded = false
                } else {
                    controllerBootstrapNeeded = true
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                // TODO: consider abstracting this
                // Handle gamepad buttons
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    val canBootstrapContentFocus = selectedAppId == null &&
                        !state.isOptionsPanelOpen &&
                        !isSystemMenuOpen &&
                        !state.isSearching &&
                        isListFocusable() &&
                        controllerBootstrapNeeded &&
                        // Don't pull focus to the grid while the user is on the tab bar (D-pad
                        // up/left/right and analog nudges aren't consumed by the bar otherwise).
                        !tabBarHasFocus

                    when (keyCode) {
                        // Navigation keys should bootstrap focus even before any item is selected.
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_BUTTON_L2,
                        KeyEvent.KEYCODE_BUTTON_R2,
                        KeyEvent.KEYCODE_BUTTON_THUMBL,
                        KeyEvent.KEYCODE_BUTTON_THUMBR,
                        -> {
                            if (canBootstrapContentFocus) {
                                requestContentFocusOrDefer()
                                false
                            } else {
                                false
                            }
                        }

                        // L1 button - previous tab
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                                if (canBootstrapContentFocus) {
                                    requestContentFocusOrDefer()
                                }
                                onPreviousTab()
                                true
                            } else {
                                false
                            }
                        }

                        // R1 button - next tab
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                                if (canBootstrapContentFocus) {
                                    requestContentFocusOrDefer()
                                }
                                onNextTab()
                                true
                            } else {
                                false
                            }
                        }

                        // SELECT button - toggle options panel (library filters/sort)
                        KeyEvent.KEYCODE_BUTTON_SELECT -> {
                            if (selectedAppId == null && !isSystemMenuOpen) {
                                onOptionsPanelToggle(!state.isOptionsPanelOpen)
                                true
                            } else {
                                false
                            }
                        }

                        // START button - toggle system menu (profile/settings)
                        KeyEvent.KEYCODE_BUTTON_START,
                        KeyEvent.KEYCODE_MENU,
                        -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen) {
                                isSystemMenuOpen = !isSystemMenuOpen
                                true
                            } else {
                                false
                            }
                        }

                        // Y button - toggle search
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                                onIsSearching(!state.isSearching)
                                true
                            } else {
                                false
                            }
                        }

                        // X button - toggle favorite for the focused game
                        KeyEvent.KEYCODE_BUTTON_X -> {
                            if (selectedAppId == null &&
                                !state.isSearching &&
                                !state.isOptionsPanelOpen &&
                                !isSystemMenuOpen &&
                                !tabBarHasFocus
                            ) {
                                toggleFocusedFavorite()
                            } else {
                                false
                            }
                        }

                        // B button - contextual back / open system menu
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            if (selectedAppId != null) {
                                // Let LibraryAppScreen handle its own B-button
                                false
                            } else if (isSystemMenuOpen) {
                                isSystemMenuOpen = false
                                true
                            } else if (state.isOptionsPanelOpen) {
                                onOptionsPanelToggle(false)
                                true
                            } else if (state.isSearching) {
                                onIsSearching(false)
                                onSearchQuery("")
                                true
                            } else {
                                // Root library view: open system menu
                                isSystemMenuOpen = true
                                true
                            }
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        if (selectedAppId == null) {
            // Use Box to allow content to scroll behind the tab bar
            Box(modifier = Modifier.fillMaxSize()) {
                // Record the library content into contentBackdrop so glass chrome
                // (TabBar, SearchBar, Options, SystemMenu, Bubbles) refracts live cards + root wallpaper.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdropIfActive(contentBackdrop),
                ) {
                    CompositionLocalProvider(
                        LocalBrokenGlassBackdrop provides rootWallpaperBackdrop,
                        LocalBackgroundLayer provides rootWallpaperLayer,
                    ) {
                if (showRecTeaserDialog) {
                    RecommendationDisclosureDialog(
                        onContinue = {
                            PrefManager.recDisclosureShown = true
                            recDisclosureShown = true
                            showRecTeaserDialog = false
                            PluviaApp.events.emit(AndroidEvent.RecommendationToggleChanged)
                        },
                        onDismiss = {
                            PrefManager.recTeaserDismissedDay = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
                            showRecTeaserDialog = false
                        },
                        source = "hero",
                    )
                }
                // When on Steam/GOG/Epic/Amazon tab and not logged in, or LOCAL tab with no custom games, show splash
                if (state.currentTab == LibraryTab.PLAYGROUND) {
                    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                    val dockZoneTop = screenHeight - 120.dp
                    PlaygroundTab(
                        settings = state.playgroundSettings,
                        onSettingsChange = onPlaygroundSettingsChange,
                        items = state.playgroundItems,
                        editingItemId = playgroundEditingItemId,
                        onEditItem = onPlaygroundEditItem,
                        onItemMoved = onPlaygroundItemMoved,
                        onItemRemoved = onPlaygroundItemRemoved,
                        onToggleDockStatus = onTogglePlaygroundItemDockStatus,
                        onItemTapped = { item ->
                            if (item is PlaygroundItem.AppItem) {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        SnackbarManager.show("Failed to launch app")
                                    }
                                } catch (e: Exception) {
                                    SnackbarManager.show("Error: ${e.message}")
                                }
                            }
                        },
                        dockZoneTop = dockZoneTop,
                        onAddWidget = { _ -> onShowWidgetPicker() },
                        onLaunchDesktop = {
                            try {
                                val intent = Intent(context, com.droiddeck.launcher.SessionActivity::class.java).apply {
                                    putExtra(com.droiddeck.launcher.session.SessionService.EXTRA_MODE, com.droiddeck.launcher.session.SessionService.MODE_DESKTOP)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to launch DroidDeck SessionActivity")
                                SnackbarManager.show("Error launching Desktop: ${e.message}")
                            }
                        },
                    )
                } else if (state.currentTab == LibraryTab.XBOX) {
                    val xboxDao = remember {
                        dagger.hilt.android.EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            app.gamenative.service.xbox.XboxService.XboxDaoEntryPoint::class.java
                        ).xboxGameDao()
                    }
                    val xboxGames by xboxDao.getAll().collectAsState(initial = emptyList())
                    app.gamenative.ui.screen.library.components.XboxStationTab(
                        xboxGames = xboxGames,
                        onSignInClick = {
                            xboxOAuthLauncher.launch(app.gamenative.service.xbox.XboxLoginActivity.createIntent(context))
                        },
                        onPlayLocalGame = { game ->
                            onClickPlay("XBOX_${game.productId}", false)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (state.currentTab == LibraryTab.RECOMMENDED) {
                    if (recDisclosureShown) {
                        RecommendedTabPane(
                            currentPaneType = currentPaneType,
                            onNavigate = { item ->
                                selectedAppId = item.appId
                                selectedLibraryItem = item
                            },
                            modifier = Modifier.fillMaxSize(),
                            firstCarouselItemFocusRequester = carouselFocusRequester,
                            firstGridItemFocusRequester = gridFirstItemFocusRequester,
                            focusTargetListIndex = if (currentPaneType == PaneType.CAROUSEL) currentCarouselFocusTargetIndex() else gridFocusTargetListIndex,
                            onFocusedIndexChanged = { carouselFocusTargetListIndex = it },
                            onItemCountChanged = { recommendationItemCount = it },
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                        RecommendationDisclosureDialog(
                            onContinue = {
                                PrefManager.recDisclosureShown = true
                                recDisclosureShown = true
                                PluviaApp.events.emit(AndroidEvent.RecommendationToggleChanged)
                            },
                            onDismiss = { onTabChanged(LibraryTab.ALL) },
                        )
                    }
                } else {
                val showEmptyStateSplash = when (state.currentTab) {
                    LibraryTab.STEAM -> !SteamUtils.hasStoredCredentials() && !state.isLoading
                    LibraryTab.GOG -> !GOGService.hasStoredCredentials(context)
                    LibraryTab.EPIC -> !EpicService.hasStoredCredentials(context)
                    LibraryTab.AMAZON -> !AmazonService.hasStoredCredentials(context)
                    LibraryTab.CLOUD -> !app.gamenative.service.xbox.XboxService.hasStoredCredentials(context) && state.appInfoList.isEmpty()
                    LibraryTab.LOCAL -> PrefManager.customGamesCount == 0
                    else -> false
                }
                // Favorites tab has its own empty state. Only show it once favorites have loaded and
                // the list has settled, so a genuinely empty tab is explained instead of flashing a
                // blank screen (or the empty message before stored favorites arrive).
                val showFavoritesEmptyState = state.currentTab == LibraryTab.FAVORITES &&
                    favoritesLoaded && !state.isLoading && state.appInfoList.isEmpty()
                if (showEmptyStateSplash) {
                    val (messageResId, buttonResId, onAction) = when (state.currentTab) {
                        LibraryTab.STEAM -> Triple(
                            R.string.library_source_not_logged_in_steam,
                            R.string.steam_sign_in,
                            onGoOnline,
                        )
                        LibraryTab.GOG -> Triple(
                            R.string.library_source_not_logged_in_gog,
                            R.string.gog_settings_login_title,
                            { gogOAuthLauncher.launch(Intent(context, GOGOAuthActivity::class.java)) },
                        )
                        LibraryTab.EPIC -> Triple(
                            R.string.library_source_not_logged_in_epic,
                            R.string.epic_settings_login_title,
                            { epicOAuthLauncher.launch(Intent(context, EpicOAuthActivity::class.java)) },
                        )
                        LibraryTab.AMAZON -> Triple(
                            R.string.library_source_not_logged_in_amazon,
                            R.string.amazon_settings_login_title,
                            { amazonOAuthLauncher.launch(Intent(context, AmazonOAuthActivity::class.java)) },
                        )
                        LibraryTab.CLOUD -> Triple(
                            R.string.library_source_not_logged_in_xbox,
                            R.string.xbox_settings_login_title,
                            { xboxOAuthLauncher.launch(app.gamenative.service.xbox.XboxLoginActivity.createIntent(context)) },
                        )
                        LibraryTab.LOCAL -> Triple(
                            R.string.library_source_no_custom_games,
                            R.string.add_custom_game_dialog_title,
                            onAddCustomGameClick,
                        )
                        else -> throw IllegalStateException("showEmptyStateSplash is true only for Steam/GOG/Epic/Amazon/Xbox/LOCAL")
                    }
                    LibrarySourceNotLoggedInSplash(
                        messageResId = messageResId,
                        signInButtonLabelResId = buttonResId,
                        onSignInClick = onAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (showFavoritesEmptyState) {
                    if (favorites.isEmpty()) {
                        LibraryFavoritesEmptyState(
                            titleResId = R.string.favorites_empty_title,
                            messageResId = R.string.favorites_empty_message,
                            actionLabelResId = R.string.favorites_empty_action,
                            onAction = { onTabChanged(LibraryTab.ALL) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Favorites exist but none are visible — filtered out by the current search
                        // or unavailable (source logged out / game removed).
                        LibraryFavoritesEmptyState(
                            titleResId = R.string.favorites_empty_filtered_title,
                            messageResId = R.string.favorites_empty_filtered_message,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    // Library list (content scrolls behind tab bar)
                    if (currentPaneType == PaneType.CAROUSEL) {
                        LibraryCarouselPane(
                            state = state,
                            listState = carouselListState,
                            onPageChange = onPageChange,
                            onNavigate = { appId ->
                                val item = state.appInfoList.find { it.appId == appId }
                                if (item?.isRecTeaser == true) {
                                    if (!item.isRecLoading && !recDisclosureShown) {
                                        onRecTeaserTapped()
                                    }
                                } else {
                                    selectedAppId = appId
                                    selectedLibraryItem = item
                                }
                            },
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                            firstCarouselItemFocusRequester = carouselFocusRequester,
                            focusTargetListIndex = currentCarouselFocusTargetIndex(),
                            onFocusedIndexChanged = {
                                carouselFocusTargetListIndex = it
                                onFocusedIndexChanged(it)
                            },
                        )
                    } else if (currentPaneType == PaneType.MODE_360) {
                        Library360Carousel(
                            state = state,
                            onNavigate = { appId ->
                                selectedAppId = appId
                                selectedLibraryItem = state.appInfoList.find { it.appId == appId }
                            },
                            onSetGameViewState = onSetGameViewState,
                            modifier = Modifier.fillMaxSize(),
                            firstItemFocusRequester = mode360FocusRequester,
                            onFocusedIndexChanged = {
                                mode360FocusTargetListIndex = it
                                onFocusedIndexChanged(it)
                            },
                        )
                    } else {
                        LibraryListPane(
                            state = state,
                            listState = listState,
                            currentLayout = currentPaneType,
                            firstGridItemFocusRequester = gridFirstItemFocusRequester,
                            focusTargetListIndex = gridFocusTargetListIndex,
                            onPageChange = onPageChange,
                            onNavigate = { appId ->
                                val item = state.appInfoList.find { it.appId == appId }
                                if (item?.isRecTeaser == true) {
                                    if (!item.isRecLoading && !recDisclosureShown) {
                                        onRecTeaserTapped()
                                    }
                                } else {
                                    selectedAppId = appId
                                    selectedLibraryItem = item
                                }
                            },
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                            onFocusedIndexChanged = {
                                gridFocusTargetListIndex = it
                                onFocusedIndexChanged(it)
                            },
                        )
                    }
                }
                }
                }
                }

                // Top overlay: Tab bar OR Search bar
                if (state.currentTab == LibraryTab.PLAYGROUND) {
                    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                    val dockZoneTop = screenHeight - 120.dp
                    val appItems = state.playgroundItems.filterIsInstance<PlaygroundItem.AppItem>()
                    val dockItems = appItems.filter { it.isInDock }
                    PlaygroundDock(
                        dockItems = dockItems,
                        editingItemId = playgroundEditingItemId,
                        onEditItem = onPlaygroundEditItem,
                        onItemRemoved = onPlaygroundItemRemoved,
                        onToggleDockStatus = onTogglePlaygroundItemDockStatus,
                        onItemTapped = { item ->
                            if (item is PlaygroundItem.AppItem) {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        SnackbarManager.show("Failed to launch app")
                                    }
                                } catch (e: Exception) {
                                    SnackbarManager.show("Error: ${e.message}")
                                }
                            }
                        },
                        dockZoneTop = dockZoneTop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (state.isSearching) {
                    // Search overlay replaces tab bar when searching
                    // TODO: Gamepad focus is a bit wonky whenever we show the search bar
                    LibrarySearchBar(
                        isVisible = true,
                        searchQuery = state.searchQuery,
                        resultCount = state.totalAppsInFilter,
                        onScrollToTop = {
                            if (currentPaneType == PaneType.CAROUSEL) {
                                carouselFocusTargetListIndex = 0
                                carouselListState.scrollToItem(0)
                            } else {
                                listState.scrollToItem(0)
                            }
                        },
                        onSearchQuery = onSearchQuery,
                        onDismiss = { onIsSearching(false) },
                        isScrolling = false,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    )
                } else {
                    // Tab bar when not searching
                    LibraryTabBar(
                        currentTab = state.currentTab,
                        tabs = state.visibleLibraryTabs,
                        tabCounts = mapOf(
                            LibraryTab.ALL to state.allCount,
                            LibraryTab.FAVORITES to state.favoritesCount,
                            LibraryTab.PLAYGROUND to state.playgroundItems.size,
                            LibraryTab.CLOUD to state.cloudCount,
                            LibraryTab.STEAM to state.steamCount,
                            LibraryTab.GOG to state.gogCount,
                            LibraryTab.EPIC to state.epicCount,
                            LibraryTab.AMAZON to state.amazonCount,
                            LibraryTab.XBOX to state.xboxCount,
                            LibraryTab.LOCAL to state.localCount,
                        ),
                        onTabSelected = { tab ->
                            if (tab == LibraryTab.PLAYGROUND && PrefManager.playgroundAutoLaunchDroidDeck) {
                                try {
                                    val intent = Intent(context, com.droiddeck.launcher.FrontEndActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to launch DroidDeck FrontEndActivity on tab click")
                                }
                            }
                            onTabChanged(tab)
                        },
                        onOptionsClick = { onOptionsPanelToggle(true) },
                        onPlaygroundSettingsClick = onShowPlaygroundSettings,
                        onSearchClick = { onIsSearching(true) },
                        onAddGameClick = onAddCustomGameClick,
                        onMenuClick = { isSystemMenuOpen = true },
                        onNavigateDownToGrid = {
                            if (isListFocusable()) {
                                requestContentFocusOrDefer()
                            }
                        },
                        onPreviousTab = onPreviousTab,
                        onNextTab = onNextTab,
                        isScrolling = false,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                tabBarHasFocus = focusState.hasFocus
                                // Cancel any deferred grid-focus so the retry loop can't pull focus
                                // back off a tab-bar button the user just landed on.
                                if (focusState.hasFocus) {
                                    pendingGridFocusRequest = false
                                    pendingCarouselFocusRequest = false
                                }
                            },
                    )
                }
            }
        } else {
            LibraryDetailPane(
                libraryItem = selectedLibraryItem,
                onBack = {
                    selectedAppId = null
                    selectedLibraryItem = null
                },
                onClickPlay = {
                    selectedLibraryItem?.let { libraryItem ->
                        onClickPlay(libraryItem.appId, it)
                    }
                },
                onTestGraphics = {
                    selectedLibraryItem?.let { libraryItem ->
                        onTestGraphics(libraryItem.appId)
                    }
                },
                onPlayWithDiagnostics = {
                    selectedLibraryItem?.let { libraryItem ->
                        onPlayWithDiagnostics(libraryItem.appId)
                    }
                },
                onAiDebugRun = {
                    selectedLibraryItem?.let { libraryItem ->
                        onAiDebugRun(libraryItem.appId)
                    }
                },
            )
        }

        // Bottom action bar
        if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
            val libraryActions = if (state.isSearching) {
                listOf(
                    LibraryActions.select,
                    GamepadAction(
                        button = GamepadButton.B,
                        labelResId = R.string.back,
                        onClick = {
                            onIsSearching(false)
                            onSearchQuery("")
                        },
                    ),
                )
            } else {
                listOf(
                    LibraryActions.select,
                    GamepadAction(
                        button = GamepadButton.SELECT,
                        labelResId = R.string.options,
                        onClick = { onOptionsPanelToggle(true) },
                    ),
                    GamepadAction(
                        button = GamepadButton.START,
                        labelResId = R.string.action_system,
                        onClick = { isSystemMenuOpen = true },
                    ),
                    GamepadAction(
                        button = GamepadButton.B,
                        labelResId = R.string.menu,
                        onClick = { isSystemMenuOpen = true },
                    ),
                    GamepadAction(
                        button = GamepadButton.Y,
                        labelResId = R.string.search,
                        onClick = { onIsSearching(true) },
                    ),
                ) + listOfNotNull(
                    focusedLibraryItem()?.let { item ->
                        GamepadAction(
                            button = GamepadButton.X,
                            labelResId = if (item.appId in favorites) {
                                R.string.option_remove_from_favorites
                            } else {
                                R.string.option_add_to_favorites
                            },
                            onClick = { toggleFocusedFavorite() },
                        )
                    },
                )
            }

            GamepadActionBar(
                actions = libraryActions,
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = true,
            )
        }

        // Options panel (SELECT) - renders on top of everything
        if (selectedAppId == null) {
            LibraryOptionsPanel(
                isOpen = state.isOptionsPanelOpen,
                onDismiss = { onOptionsPanelToggle(false) },
                selectedFilters = state.appInfoSortType,
                onFilterChanged = onFilterChanged,
                currentSortOption = state.currentSortOption,
                onSortOptionChanged = onSortOptionChanged,
                currentView = currentPaneType,
                onViewChanged = { newPaneType ->
                    PrefManager.libraryLayout = newPaneType
                    currentPaneType = newPaneType
                },
                steamCollections = state.steamCollections,
                selectedSteamCollectionIds = state.selectedSteamCollectionIds,
                steamCollectionCounts = state.steamCollectionCounts,
                skippedDynamicCollections = state.skippedDynamicCollections,
                isSteamConnected = isSteamConnected,
                hasSteamCredentials = SteamUtils.hasStoredCredentials(),
                isOffline = isOffline,
                onSteamCollectionToggle = onSteamCollectionToggle,
                onClearSteamCollections = onClearSteamCollections,
                curatedLists = state.curatedLists,
                selectedCuratedListIds = state.selectedCuratedListIds,
                curatedListCounts = state.curatedListCounts,
                onCuratedListToggle = onCuratedListToggle,
                onClearCuratedLists = onClearCuratedLists,
            )

            // System menu (START) - renders on top of everything
            val context = LocalContext.current
            val gogLoggedIn = app.gamenative.service.gog.GOGAuthManager.hasStoredCredentials(context)
            val epicLoggedIn = app.gamenative.service.epic.EpicAuthManager.hasStoredCredentials(context)
            val amazonLoggedIn = app.gamenative.service.amazon.AmazonAuthManager.hasStoredCredentials(context)

            SystemMenu(
                isOpen = isSystemMenuOpen,
                onDismiss = { isSystemMenuOpen = false },
                onNavigateRoute = onNavigateRoute,
                onDownloadsClick = onDownloadsClick,
                onLogout = onLogout,
                onGoOnline = onGoOnline,
                isOffline = isOffline,
                gogLoggedIn = gogLoggedIn,
                epicLoggedIn = epicLoggedIn,
                amazonLoggedIn = amazonLoggedIn,
                onGogLoginClick = {
                    gogOAuthLauncher.launch(Intent(context, GOGOAuthActivity::class.java))
                },
                onGogLogoutClick = {
                    PlatformAuthUiHelpers.logoutGog(
                        context = context,
                        scope = lifecycleScope,
                        callbacks = PlatformLogoutCallbacks(),
                    )
                },
                onEpicLoginClick = {
                    epicOAuthLauncher.launch(Intent(context, EpicOAuthActivity::class.java))
                },
                onEpicLogoutClick = {
                    PlatformAuthUiHelpers.logoutEpic(
                        context = context,
                        scope = lifecycleScope,
                        callbacks = PlatformLogoutCallbacks(),
                    )
                },
                onAmazonLoginClick = {
                    amazonOAuthLauncher.launch(Intent(context, AmazonOAuthActivity::class.java))
                },
                onAmazonLogoutClick = {
                    PlatformAuthUiHelpers.logoutAmazon(
                        context = context,
                        scope = lifecycleScope,
                        callbacks = PlatformLogoutCallbacks(),
                    )
                },
                xboxLoggedIn = app.gamenative.service.xbox.XboxService.hasStoredCredentials(context),
                onXboxLoginClick = {
                    xboxOAuthLauncher.launch(app.gamenative.service.xbox.XboxLoginActivity.createIntent(context))
                },
                onXboxLogoutClick = {
                    lifecycleScope.launch {
                        app.gamenative.service.xbox.XboxService.logout(context)
                        PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(0, GameSource.XBOX))
                    }
                },
            )

        }

        // 2-Option Custom Game + Android App Import Type Dialog
        if (showImportTypeDialog) {
            AlertDialog(
                onDismissRequest = { showImportTypeDialog = false },
                title = { Text("Add to Library") },
                text = {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        ListItem(
                            headlineContent = { Text("Link Folder Directly (OTG / USB / SD — Zero Copy)") },
                            supportingContent = { Text("Keep game files on your USB/OTG drive or folder. Only the Wine container is created on device (1.2.0 mode).") },
                            modifier = Modifier.clickable {
                                showImportTypeDialog = false
                                if (PrefManager.showAddCustomGameDialog) {
                                    showAddCustomGameDialog = true
                                } else {
                                    folderPicker.launchPicker()
                                }
                            },
                        )
                        ListItem(
                            headlineContent = { Text("Copy / Import Game to Internal Storage") },
                            supportingContent = { Text("Copy the entire game folder into internal storage for faster local disk speeds (1.4.0 mode).") },
                            modifier = Modifier.clickable {
                                showImportTypeDialog = false
                                showModernImportDialog = true
                            },
                        )
                        ListItem(
                            headlineContent = { Text("Add Android App") },
                            supportingContent = { Text("Import an installed Android app or game into your library.") },
                            modifier = Modifier.clickable {
                                showImportTypeDialog = false
                                onShowAndroidAppPickerChange(true)
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showImportTypeDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        // Pre-import dialog (modern add path)
        if (showModernImportDialog) {
            AlertDialog(
                onDismissRequest = { showModernImportDialog = false },
                title = { Text(stringResource(R.string.add_custom_game_dialog_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.custom_game_import_dialog_message))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { importRemoveOriginal = !importRemoveOriginal },
                        ) {
                            Checkbox(
                                checked = importRemoveOriginal,
                                onCheckedChange = { importRemoveOriginal = it },
                            )
                            Text(stringResource(R.string.custom_game_import_remove_original))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showModernImportDialog = false
                            importLauncher.launch(null)
                        },
                    ) {
                        Text(stringResource(R.string.continue_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showModernImportDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Import progress dialog (modern add path)
        if (importState.isImporting) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.custom_game_importing)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            val mb = (importState.progress?.copiedBytes ?: 0L) / 1_000_000L
                            Text("$mb MB")
                            importState.progress?.currentFile?.let {
                                Text(text = it, maxLines = 1)
                            }
                        }
                    }
                },
                confirmButton = { },
            )
        }

        // Add custom game dialog
        if (showAddCustomGameDialog) {
            AlertDialog(
                onDismissRequest = { showAddCustomGameDialog = false },
                title = { Text(stringResource(R.string.add_custom_game_dialog_title)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.add_custom_game_dialog_message),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = { dontShowAgain = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.add_custom_game_dont_show_again),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (dontShowAgain) {
                                PrefManager.showAddCustomGameDialog = false
                            }
                            showAddCustomGameDialog = false
                            folderPicker.launchPicker()
                        },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddCustomGameDialog = false },
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
    }
}

/***********
 * PREVIEW *
 ***********/

@OptIn(ExperimentalMaterial3Api::class)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:width=1080px,height=1920px,dpi=440,orientation=landscape",
)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "id:pixel_tablet",
)
@Composable
private fun Preview_LibraryScreenContent() {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    PrefManager.init(context)
    var state by remember {
        mutableStateOf(
            LibraryState(
                appInfoList = List(15) { idx ->
                    val item = fakeAppInfo(idx)
                    LibraryItem(
                        index = idx,
                        appId = "${GameSource.STEAM.name}_${item.id}",
                        name = item.name,
                        iconHash = item.iconHash,
                    )
                },
                // Add compatibility map for preview
                compatibilityMap = mapOf(
                    "Game 0" to GameCompatibilityStatus.COMPATIBLE,
                    "Game 1" to GameCompatibilityStatus.GPU_COMPATIBLE,
                    "Game 2" to GameCompatibilityStatus.NOT_COMPATIBLE,
                    "Game 3" to GameCompatibilityStatus.UNKNOWN,
                ),
            ),
        )
    }
    PluviaTheme {
        LibraryScreenContent(
            listState = rememberLazyGridState(),
            state = state,
            sheetState = sheetState,
            onIsSearching = {},
            onSearchQuery = {},
            onFilterChanged = { },
            onPageChange = { },
            onModalBottomSheet = {
                val currentState = state.modalBottomSheet
                println("State: $currentState")
                state = state.copy(modalBottomSheet = !currentState)
            },
            onClickPlay = { _, _ -> },
            onTestGraphics = { },
            onPlayWithDiagnostics = { },
            onAiDebugRun = { },
            onRefresh = { },
            onNavigateRoute = {},
            onLogout = {},
            onGoOnline = {},
            onSourceToggle = {},
            onAddCustomGameFolder = {},
            onSortOptionChanged = {},
            onSteamCollectionToggle = {},
            onClearSteamCollections = {},
            onCuratedListToggle = {},
            onClearCuratedLists = {},
            onOptionsPanelToggle = { isOpen ->
                state = state.copy(isOptionsPanelOpen = isOpen)
            },
            onTabChanged = { tab ->
                state = state.copy(currentTab = tab)
            },
            onPreviousTab = {},
            onNextTab = {},
        )
    }
}
