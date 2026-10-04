package app.gamenative.ui.screen.library

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.gamenative.BuildConfig
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.*
import app.gamenative.events.AndroidEvent
import app.gamenative.playground.PlaygroundWidgetHostManager
import app.gamenative.service.amazon.AmazonService
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGService
import app.gamenative.service.gog.GOGAuthManager
import app.gamenative.service.epic.EpicAuthManager
import app.gamenative.service.amazon.AmazonAuthManager
import app.gamenative.ui.component.*
import app.gamenative.ui.components.rememberCustomGameFolderPicker
import app.gamenative.ui.components.LoopingVideoBackground
import app.gamenative.ui.components.requestPermissionsForPath
import app.gamenative.ui.util.JoystickScrollHandler
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.data.GameViewState
import app.gamenative.ui.enums.*
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.model.LibraryViewModel
import app.gamenative.ui.screen.auth.AmazonOAuthActivity
import app.gamenative.ui.screen.auth.EpicOAuthActivity
import app.gamenative.ui.screen.auth.GOGOAuthActivity
import app.gamenative.ui.screen.library.components.*
import app.gamenative.ui.theme.*
import app.gamenative.ui.util.*
import app.gamenative.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onPlayWithDiagnostics: (String) -> Unit,
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
        onNavigateRoute = onNavigateRoute,
        onLogout = onLogout,
        onGoOnline = onGoOnline,
        onDownloadsClick = onDownloadsClick,
        onSourceToggle = viewModel::onSourceToggle,
        onAddCustomGameFolder = viewModel::addCustomGameFolder,
        onSortOptionChanged = viewModel::onSortOptionChanged,
        onSteamCollectionToggle = viewModel::onSteamCollectionToggle,
        onClearSteamCollections = viewModel::onClearSteamCollections,
        onFocusedIndexChanged = viewModel::onFocusedIndexChanged,
        onSetMainColor = viewModel::setMainColor,
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
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
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
                }
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
            onDismiss = { showAndroidAppPicker = false }
        )
    }

    if (showWidgetPicker) {
        val hostManager = viewModel.playgroundWidgetHostManager
        val providers = remember { hostManager.getAvailableWidgets() }

        ModalBottomSheet(
            onDismissRequest = { showWidgetPicker = false },
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ) {
            val pm = context.packageManager
            Text(
                "Pick a Widget",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                items(providers) { info ->
                    ListItem(
                        headlineContent = { Text(info.loadLabel(pm)) },
                        supportingContent = { Text(info.provider.packageName) },
                        modifier = Modifier.clickable {
                            PluviaApp.events.emit(AndroidEvent.AddWidget(hostManager, info))
                            showWidgetPicker = false
                        }
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
    onFocusedIndexChanged: (Int) -> Unit,
    onSetMainColor: (String) -> Unit,
    onSetGameViewState: (GameViewState) -> Unit,
    onOptionsPanelToggle: (Boolean) -> Unit,
    onTabChanged: (LibraryTab) -> Unit,
    onPreviousTab: () -> Unit,
    onNextTab: () -> Unit,
    onPlaygroundSettingsChange: (PlaygroundSettings) -> Unit = {},
    onPlaygroundItemMoved: (id: String, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onPlaygroundItemRemoved: (String) -> Unit = {},
    onTogglePlaygroundItemDockStatus: (String, Float?, Float?) -> Unit = { _, _, _ -> },
    playgroundEditingItemId: String? = null,
    onPlaygroundEditItem: (String?) -> Unit = {},
    onShowPlaygroundSettings: () -> Unit = {},
    onShowAndroidAppPickerChange: (Boolean) -> Unit = {},
    onShowWidgetPicker: () -> Unit = {},
    isOffline: Boolean = false,
    isSteamConnected: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope

    val gogOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_ERROR) ?: context.getString(R.string.gog_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        val code = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)
        if (code != null) {
            lifecycleScope.launch {
                PlatformOAuthHandlers.handleGogAuthentication(context, code, lifecycleScope, {}, { msg -> msg?.let { SnackbarManager.show(it) } }, { SnackbarManager.show(context.getString(R.string.gog_login_success_title)) }, {})
            }
        }
    }

    val epicOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_ERROR) ?: context.getString(R.string.epic_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        val code = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_AUTH_CODE)
        if (code != null) {
            lifecycleScope.launch {
                PlatformOAuthHandlers.handleEpicAuthentication(context, code, lifecycleScope, {}, { msg -> msg?.let { SnackbarManager.show(it) } }, { SnackbarManager.show(context.getString(R.string.epic_login_success_title)) }, {})
            }
        }
    }

    val amazonOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(AmazonOAuthActivity.EXTRA_ERROR) ?: context.getString(R.string.amazon_login_cancel)
            SnackbarManager.show(message)
            return@rememberLauncherForActivityResult
        }
        val code = result.data?.getStringExtra(AmazonOAuthActivity.EXTRA_AUTH_CODE)
        if (code != null) {
            lifecycleScope.launch {
                PlatformOAuthHandlers.handleAmazonAuthentication(context, code, lifecycleScope, {}, { msg -> msg?.let { SnackbarManager.show(it) } }, { SnackbarManager.show(context.getString(R.string.amazon_login_success_title)) }, {})
            }
        }
    }

    var selectedAppId by remember { mutableStateOf<String?>(null) }
    var selectedLibraryItem by remember { mutableStateOf<LibraryItem?>(null) }
    var backgroundLayer by remember { mutableStateOf<GraphicsLayer?>(null) }

    val carouselListState = rememberLazyListState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isControllerConnected = isGameControllerConnected()

    var currentPaneType by remember {
        mutableStateOf(PrefManager.getLibraryLayout(isLandscape, isControllerConnected))
    }

    LaunchedEffect(isLandscape, isControllerConnected) {
        val preferredLayout = PrefManager.getLibraryLayout(isLandscape, isControllerConnected)
        if (currentPaneType != preferredLayout) {
            currentPaneType = preferredLayout
        }
    }

    var recDisclosureShown by remember { mutableStateOf(PrefManager.recDisclosureShown) }
    var showRecTeaserDialog by remember { mutableStateOf(false) }
    val onRecTeaserTapped = {
        showRecTeaserDialog = true
    }

    val gridFirstItemFocusRequester = remember { FocusRequester() }
    val carouselFocusRequester = remember { FocusRequester() }
    val mode360FocusRequester = remember { FocusRequester() }
    var gridFocusTargetListIndex by remember { mutableIntStateOf(0) }
    var carouselFocusTargetListIndex by remember { mutableIntStateOf(0) }
    var mode360FocusTargetListIndex by remember { mutableIntStateOf(0) }
    var isSystemMenuOpen by remember { mutableStateOf(false) }
    val favorites by FavoritesManager.favorites.collectAsStateWithLifecycle()
    val favoritesLoaded by FavoritesManager.loaded.collectAsStateWithLifecycle()
    var tabBarHasFocus by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    val folderPicker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            val folder = File(path)
            val canAccess = try {
                folder.exists() && (folder.isDirectory && folder.canRead())
            } catch (e: Exception) {
                false
            }

            if (!canAccess && !CustomGameScanner.hasStoragePermission(context, path)) {
                requestPermissionsForPath(context, path, storagePermissionLauncher)
            }
            onAddCustomGameFolder(path)
        },
        onFailure = { message ->
            SnackbarManager.show(message)
        },
    )

    var showModernImportDialog by remember { mutableStateOf(false) }
    var importRemoveOriginal by rememberSaveable { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onImportCustomGame(uri, importRemoveOriginal)
        }
    }

    var showAddCustomGameDialog by remember { mutableStateOf(false) }
    var showImportTypeDialog by remember { mutableStateOf(false) }

    val onAddCustomGameClick = {
        HapticManager.trigger(HapticManager.HapticType.CLICK)
        showImportTypeDialog = true
    }

    fun isListFocusable(): Boolean = state.appInfoList.isNotEmpty() || state.currentTab == LibraryTab.RECOMMENDED

    fun requestContentFocusOrDefer() {
        if (!isListFocusable()) return
        lifecycleScope.launch {
            delay(64)
            runCatching {
                when (currentPaneType) {
                    PaneType.CAROUSEL -> carouselFocusRequester.requestFocus()
                    PaneType.MODE_360 -> mode360FocusRequester.requestFocus()
                    else -> gridFirstItemFocusRequester.requestFocus()
                }
            }
        }
    }

    fun toggleFocusedFavorite(): Boolean {
        val focusedIndex = when (currentPaneType) {
            PaneType.CAROUSEL -> carouselFocusTargetListIndex
            PaneType.MODE_360 -> mode360FocusTargetListIndex
            else -> gridFocusTargetListIndex
        }
        val item = state.appInfoList.getOrNull(focusedIndex) ?: return false
        toggleFavorite(context, item.appId, item.name)
        return true
    }

    fun focusedLibraryItem(): LibraryItem? {
        if (state.currentTab == LibraryTab.RECOMMENDED) return null
        val focusedIndex = when (currentPaneType) {
            PaneType.CAROUSEL -> carouselFocusTargetListIndex
            PaneType.MODE_360 -> mode360FocusTargetListIndex
            else -> gridFocusTargetListIndex
        }
        return state.appInfoList.getOrNull(focusedIndex)?.takeUnless { it.isRecommended }
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

    LaunchedEffect(selectedAppId) {
        if (selectedAppId == null) {
            delay(100)
            if (isListFocusable()) {
                requestContentFocusOrDefer()
            }
        }
    }

    LaunchedEffect(state.currentTab) {
        delay(150)
        if (tabBarHasFocus) return@LaunchedEffect
        if (isListFocusable()) {
            requestContentFocusOrDefer()
        }
    }

    LaunchedEffect(
        state.appInfoList.size,
        selectedAppId,
        isSystemMenuOpen,
        state.isOptionsPanelOpen,
        state.isSearching,
    ) {
        if (selectedAppId == null && !isSystemMenuOpen && !state.isOptionsPanelOpen && !state.isSearching && !tabBarHasFocus) {
            if (state.appInfoList.isNotEmpty()) {
                requestContentFocusOrDefer()
            }
        }
    }

    val safePaddingModifier = Modifier.windowInsetsPadding(
        WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )

    CompositionLocalProvider(LocalBackgroundLayer provides backgroundLayer) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (PrefManager.videoBackgroundPath.isNotEmpty()) Color.Transparent
                    else MaterialTheme.colorScheme.background
                )
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_L1 -> { onPreviousTab(); true }
                            KeyEvent.KEYCODE_BUTTON_R1 -> { onNextTab(); true }
                            KeyEvent.KEYCODE_BUTTON_START -> { isSystemMenuOpen = !isSystemMenuOpen; true }
                            KeyEvent.KEYCODE_BUTTON_SELECT -> { onOptionsPanelToggle(!state.isOptionsPanelOpen); true }
                            KeyEvent.KEYCODE_BUTTON_Y -> { onIsSearching(!state.isSearching); true }
                            KeyEvent.KEYCODE_BUTTON_X -> { if (!state.isSearching) toggleFocusedFavorite(); true }
                            KeyEvent.KEYCODE_BUTTON_A -> {
                                if (currentPaneType == PaneType.MODE_360) {
                                    when (state.gameViewState) {
                                        GameViewState.CAROUSEL -> {
                                            onSetGameViewState(GameViewState.HERO_DETAILS)
                                            true
                                        }
                                        GameViewState.HERO_DETAILS -> {
                                            onSetGameViewState(GameViewState.ACTION_MENU)
                                            val focusedItem = focusedLibraryItem()
                                            if (focusedItem != null) {
                                                selectedAppId = focusedItem.appId
                                                selectedLibraryItem = focusedItem
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            KeyEvent.KEYCODE_BUTTON_B -> {
                                if (currentPaneType == PaneType.MODE_360 && state.gameViewState != GameViewState.CAROUSEL) {
                                    onSetGameViewState(GameViewState.CAROUSEL)
                                    true
                                } else if (selectedAppId != null) {
                                    selectedAppId = null
                                    selectedLibraryItem = null
                                    true
                                } else if (isSystemMenuOpen) {
                                    isSystemMenuOpen = false
                                    true
                                } else if (state.isOptionsPanelOpen) {
                                    onOptionsPanelToggle(false)
                                    true
                                } else if (state.isSearching) {
                                    onIsSearching(false)
                                    true
                                } else {
                                    isSystemMenuOpen = true
                                    true
                                }
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            val screenHeight = this.maxHeight
            val dockZoneTop = screenHeight - 120.dp

            if (selectedAppId == null) {
                val mainContent = @Composable { blur: Dp, gridOverride: LazyGridState?, listOverride: LazyListState? ->
                    Box(modifier = Modifier.fillMaxSize().blur(blur)) {
                        if (showRecTeaserDialog) {
                            RecommendationDisclosureDialog(
                                onContinue = {
                                    PrefManager.recDisclosureShown = true
                                    showRecTeaserDialog = false
                                    PluviaApp.events.emit(AndroidEvent.RecommendationToggleChanged)
                                },
                                onDismiss = {
                                    PrefManager.recTeaserDismissedDay = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
                                    showRecTeaserDialog = false
                                },
                            )
                        }
                    }

                    val showEmptyStateSplash = when (state.currentTab) {
                        LibraryTab.STEAM -> !SteamUtils.hasStoredCredentials() && !state.isLoading
                        LibraryTab.GOG -> !GOGService.hasStoredCredentials(context)
                        LibraryTab.EPIC -> !EpicService.hasStoredCredentials(context)
                        LibraryTab.AMAZON -> !AmazonService.hasStoredCredentials(context)
                        LibraryTab.LOCAL -> PrefManager.customGamesCount == 0
                        else -> false
                    }
                    val showFavoritesEmptyState = state.currentTab == LibraryTab.FAVORITES &&
                        favoritesLoaded && !state.isLoading && state.appInfoList.isEmpty()

                    if (state.currentTab == LibraryTab.PLAYGROUND) {
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
                            onAddWidget = { _ -> onShowWidgetPicker() }
                        )
                    } else if (showEmptyStateSplash) {
                        val (messageResId, buttonResId, onAction) = when (state.currentTab) {
                            LibraryTab.STEAM -> Triple(R.string.library_source_not_logged_in_steam, R.string.steam_sign_in, onGoOnline)
                            LibraryTab.GOG -> Triple(R.string.library_source_not_logged_in_gog, R.string.gog_settings_login_title, { gogOAuthLauncher.launch(Intent(context, GOGOAuthActivity::class.java)) })
                            LibraryTab.EPIC -> Triple(R.string.library_source_not_logged_in_epic, R.string.epic_settings_login_title, { epicOAuthLauncher.launch(Intent(context, EpicOAuthActivity::class.java)) })
                            LibraryTab.AMAZON -> Triple(R.string.library_source_not_logged_in_amazon, R.string.amazon_settings_login_title, { amazonOAuthLauncher.launch(Intent(context, AmazonOAuthActivity::class.java)) })
                            LibraryTab.LOCAL -> Triple(R.string.library_source_no_custom_games, R.string.add_custom_game_dialog_title, onAddCustomGameClick)
                            else -> throw IllegalStateException("showEmptyStateSplash error")
                        }
                        LibrarySourceNotLoggedInSplash(messageResId, buttonResId, onAction, Modifier.fillMaxSize())
                    } else if (showFavoritesEmptyState) {
                        if (favorites.isEmpty()) {
                            LibraryFavoritesEmptyState(
                                titleResId = R.string.favorites_empty_title,
                                messageResId = R.string.favorites_empty_message,
                                modifier = Modifier.fillMaxSize(),
                                actionLabelResId = R.string.favorites_empty_action,
                                onAction = { onTabChanged(LibraryTab.ALL) }
                            )
                        } else {
                            LibraryFavoritesEmptyState(
                                titleResId = R.string.favorites_empty_filtered_title,
                                messageResId = R.string.favorites_empty_filtered_message,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        if (currentPaneType == PaneType.CAROUSEL) {
                            LibraryCarouselPane(state, listOverride ?: carouselListState, onPageChange, { appId ->
                                val item = state.appInfoList.find { it.appId == appId }
                                if (item?.isRecTeaser == true) { if (!item.isRecLoading && !recDisclosureShown) onRecTeaserTapped() }
                                else { selectedAppId = appId; selectedLibraryItem = item }
                            }, onRefresh, Modifier.fillMaxSize(), carouselFocusRequester, carouselFocusTargetListIndex, { carouselFocusTargetListIndex = it; onFocusedIndexChanged(it) })
                        } else if (currentPaneType == PaneType.MODE_360) {
                            Library360Carousel(state, { appId -> selectedAppId = appId; selectedLibraryItem = state.appInfoList.find { it.appId == appId } }, onSetGameViewState, Modifier.fillMaxSize(), mode360FocusRequester, { mode360FocusTargetListIndex = it; onFocusedIndexChanged(it) })
                        } else {
                            LibraryListPane(state, gridOverride ?: listState, currentPaneType, gridFirstItemFocusRequester, gridFocusTargetListIndex, onPageChange, { appId ->
                                val item = state.appInfoList.find { it.appId == appId }
                                if (item?.isRecTeaser == true) { if (!item.isRecLoading && !recDisclosureShown) onRecTeaserTapped() }
                                else { selectedAppId = appId; selectedLibraryItem = item }
                            }, onRefresh, Modifier.fillMaxSize(), { gridFocusTargetListIndex = it; onFocusedIndexChanged(it) })
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().drawWithCache {
                        val layer = obtainGraphicsLayer()
                        onDrawWithContent {
                            layer.record { this@onDrawWithContent.drawContent() }
                            this@onDrawWithContent.drawLayer(layer)
                            backgroundLayer = layer
                        }
                    }) {
                        if (state.videoBackgroundPath.isNotEmpty()) {
                            LoopingVideoBackground(state.videoBackgroundPath, Modifier.fillMaxSize(), state.videoBackgroundDimAlpha, { hex -> if (state.mainColor != hex) onSetMainColor(hex) })
                        }
                        Box(modifier = Modifier.fillMaxSize().then(safePaddingModifier)) {
                            mainContent(0.dp, null, null)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {

                        Box(modifier = Modifier.fillMaxSize().then(safePaddingModifier)) {
                            val isScrolling = listState.isScrollInProgress || carouselListState.isScrollInProgress
                            val looks = LocalLooksState.current

                            if (state.currentTab == LibraryTab.PLAYGROUND) {
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
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }

                            if (state.isSearching) {
                                LibrarySearchBar(true, state.searchQuery, state.totalAppsInFilter, { listState.scrollToItem(0) }, onSearchQuery, { onIsSearching(false) }, isScrolling, looks.liquidDistortion, Modifier.align(Alignment.TopCenter).fillMaxWidth())
                            } else {
                                LibraryTabBar(
                                    currentTab = state.currentTab,
                                    tabCounts = mapOf(
                                        LibraryTab.ALL to state.allCount,
                                        LibraryTab.FAVORITES to state.favoritesCount,
                                        LibraryTab.PLAYGROUND to state.playgroundItems.size,
                                        LibraryTab.CLOUD to state.cloudCount,
                                        LibraryTab.STEAM to state.steamCount,
                                        LibraryTab.GOG to state.gogCount,
                                        LibraryTab.EPIC to state.epicCount,
                                        LibraryTab.AMAZON to state.amazonCount,
                                        LibraryTab.LOCAL to state.localCount,
                                    ),
                                    onTabSelected = onTabChanged,
                                    onOptionsClick = { onOptionsPanelToggle(true) },
                                    onPlaygroundSettingsClick = onShowPlaygroundSettings,
                                    onSearchClick = { onIsSearching(true) },
                                    onAddGameClick = onAddCustomGameClick,
                                    onMenuClick = { isSystemMenuOpen = true },
                                    onNavigateDownToGrid = { requestContentFocusOrDefer() },
                                    onPreviousTab = onPreviousTab,
                                    onNextTab = onNextTab,
                                    isScrolling = isScrolling,
                                    distortionStrength = looks.liquidDistortion,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp).fillMaxWidth().onFocusChanged { tabBarHasFocus = it.hasFocus },
                                )
                            }
                        }
                    }
                }
            } else {
                LibraryDetailPane(selectedLibraryItem, { direct -> onClickPlay(selectedLibraryItem?.appId ?: "", direct) }, { onTestGraphics(selectedLibraryItem?.appId ?: "") }, { onPlayWithDiagnostics(selectedLibraryItem?.appId ?: "") }, { selectedAppId = null; selectedLibraryItem = null })
            }

            if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                val libraryActions = if (state.isSearching) {
                    listOf(LibraryActions.select, GamepadAction(GamepadButton.B, R.string.back, { onIsSearching(false); onSearchQuery("") }))
                } else {
                    listOf(LibraryActions.select, GamepadAction(GamepadButton.SELECT, R.string.options, { onOptionsPanelToggle(true) }), GamepadAction(GamepadButton.START, R.string.action_system, { isSystemMenuOpen = true }), GamepadAction(GamepadButton.B, R.string.back, { isSystemMenuOpen = true }), GamepadAction(GamepadButton.Y, R.string.search, { onIsSearching(true) })) +
                    listOfNotNull(focusedLibraryItem()?.let { item -> GamepadAction(GamepadButton.X, if (item.appId in favorites) R.string.option_remove_from_favorites else R.string.option_add_to_favorites, { toggleFocusedFavorite() }) })
                }
                GamepadActionBar(libraryActions, Modifier.align(Alignment.BottomCenter), true)
            }

            if (selectedAppId == null) {
                LibraryOptionsPanel(state.isOptionsPanelOpen, { onOptionsPanelToggle(false) }, state.appInfoSortType, onFilterChanged, state.currentSortOption, onSortOptionChanged, currentPaneType, { currentPaneType = it; PrefManager.setLibraryLayout(isLandscape, isControllerConnected, it) }, state.steamCollections, state.selectedSteamCollectionIds, state.steamCollectionCounts, state.skippedDynamicCollections, isSteamConnected, isOffline, onSteamCollectionToggle, onClearSteamCollections)
                SystemMenu(isSystemMenuOpen, { isSystemMenuOpen = false }, onNavigateRoute, onDownloadsClick, onLogout, onGoOnline, isOffline, GOGAuthManager.hasStoredCredentials(context), EpicAuthManager.hasStoredCredentials(context), AmazonAuthManager.hasStoredCredentials(context), { gogOAuthLauncher.launch(Intent(context, GOGOAuthActivity::class.java)) }, { PlatformAuthUiHelpers.logoutGog(context, lifecycleScope, PlatformLogoutCallbacks()) }, { epicOAuthLauncher.launch(Intent(context, EpicOAuthActivity::class.java)) }, { PlatformAuthUiHelpers.logoutEpic(context, lifecycleScope, PlatformLogoutCallbacks()) }, { amazonOAuthLauncher.launch(Intent(context, AmazonOAuthActivity::class.java)) }, { PlatformAuthUiHelpers.logoutAmazon(context, lifecycleScope, PlatformLogoutCallbacks()) })
            }
        }

        if (showImportTypeDialog) {
            AlertDialog(onDismissRequest = { showImportTypeDialog = false }, title = { Text(stringResource(R.string.add_custom_game_dialog_title)) }, text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable { showImportTypeDialog = false; if (BuildConfig.MODERN_ANDROID) showModernImportDialog = true else if (PrefManager.showAddCustomGameDialog) showAddCustomGameDialog = true else folderPicker.launchPicker() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DesktopWindows, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column { Text(stringResource(R.string.import_pc), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.import_pc_desc), style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(modifier = Modifier.fillMaxWidth().clickable { showImportTypeDialog = false; onShowAndroidAppPickerChange(true) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column { Text(stringResource(R.string.import_app), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.import_app_desc), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }, confirmButton = {}, dismissButton = { TextButton({ showImportTypeDialog = false }) { Text(stringResource(R.string.cancel)) } })
        }

        if (showModernImportDialog) {
            AlertDialog(
                onDismissRequest = { showModernImportDialog = false },
                title = { Text(stringResource(R.string.add_custom_game_dialog_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.custom_game_import_dialog_message))
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.hapticClickable { importRemoveOriginal = !importRemoveOriginal }) {
                            Checkbox(importRemoveOriginal, { importRemoveOriginal = it })
                            Text(stringResource(R.string.custom_game_import_remove_original))
                        }
                    }
                },
                confirmButton = { TextButton({ HapticManager.trigger(HapticManager.HapticType.CLICK); showModernImportDialog = false; importLauncher.launch(null) }) { Text(stringResource(R.string.continue_action)) } },
                dismissButton = { TextButton({ HapticManager.trigger(HapticManager.HapticType.CLICK); showModernImportDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (importState.isImporting) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text(stringResource(R.string.custom_game_importing)) }, text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Column { val mb = (importState.progress?.copiedBytes ?: 0L) / 1_000_000L; Text("$mb MB"); importState.progress?.currentFile?.let { Text(it, maxLines = 1) } }
                }
            })
        }

        if (showAddCustomGameDialog) {
            var dontShowAgain by remember { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { showAddCustomGameDialog = false }, title = { Text(stringResource(R.string.add_custom_game_dialog_title)) }, text = {
                Column {
                    Text(stringResource(R.string.add_custom_game_dialog_message), Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(dontShowAgain, { dontShowAgain = it }); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_custom_game_dont_show_again), Modifier.weight(1f)) }
                }
            }, confirmButton = { TextButton({ if (dontShowAgain) PrefManager.showAddCustomGameDialog = false; showAddCustomGameDialog = false; folderPicker.launchPicker() }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { TextButton({ showAddCustomGameDialog = false }) { Text(stringResource(android.R.string.cancel)) } })
        }
    }
}
