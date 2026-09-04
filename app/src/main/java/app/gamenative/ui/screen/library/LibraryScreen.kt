package app.gamenative.ui.screen.library

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.posthog.PostHog
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
        onAddAndroidApp = viewModel::addAndroidApp,
        onSetMainColor = viewModel::setMainColor,
        onSetGameViewState = viewModel::onSetGameViewState,
        onOptionsPanelToggle = viewModel::onOptionsPanelToggle,
        onTabChanged = viewModel::onTabChanged,
        onPreviousTab = viewModel::onPreviousTab,
        onNextTab = viewModel::onNextTab,
        isOffline = isOffline,
        isSteamConnected = isSteamConnected,
    )
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
    onAddAndroidApp: (String) -> Unit,
    onSetMainColor: (String) -> Unit,
    onSetGameViewState: (GameViewState) -> Unit,
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
    var backgroundLayer by remember { mutableStateOf<GraphicsLayer?>(null) }

    val carouselListState = rememberLazyListState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isControllerConnected = isGameControllerConnected()

    var currentPaneType by remember {
        mutableStateOf(PrefManager.getLibraryLayout(isLandscape, isControllerConnected))
    }

    // Auto-switch layout when controller or orientation changes
    LaunchedEffect(isLandscape, isControllerConnected) {
        val preferredLayout = PrefManager.getLibraryLayout(isLandscape, isControllerConnected)
        if (currentPaneType != preferredLayout) {
            currentPaneType = preferredLayout
        }
    }

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

    val rootFocusRequester = remember { FocusRequester() }
    val gridFirstItemFocusRequester = remember { FocusRequester() }
    val carouselFocusRequester = remember { FocusRequester() }
    val mode360FocusRequester = remember { FocusRequester() }
    var gridFocusTargetListIndex by remember { mutableIntStateOf(0) }
    var carouselFocusTargetListIndex by remember { mutableIntStateOf(0) }
    var mode360FocusTargetListIndex by remember { mutableIntStateOf(0) }
    var pendingGridFocusRequest by remember { mutableStateOf(false) }
    var pendingCarouselFocusRequest by remember { mutableStateOf(false) }
    var isSystemMenuOpen by remember { mutableStateOf(false) }
    var selectedLibraryItem by remember { mutableStateOf<LibraryItem?>(null) }
    val favorites by FavoritesManager.favorites.collectAsStateWithLifecycle()
    val favoritesLoaded by FavoritesManager.loaded.collectAsStateWithLifecycle()
    var controllerBootstrapNeeded by remember { mutableStateOf(true) }
    var rootHasFocus by remember { mutableStateOf(false) }
    var tabBarHasFocus by remember { mutableStateOf(false) }
    var recommendationItemCount by remember { mutableIntStateOf(0) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    val folderPicker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            // When a folder is selected via OpenDocumentTree, the user has already granted
            // URI permissions for that specific folder. We should verify we can access it
            // rather than checking for broad storage permissions.
            val folder = File(path)
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

    var showAddCustomGameDialog by remember { mutableStateOf(false) }
    var showImportTypeDialog by remember { mutableStateOf(false) }
    var showAndroidAppPicker by remember { mutableStateOf(false) }

    val onAddCustomGameClick = {
        HapticManager.trigger(HapticManager.HapticType.CLICK)
        showImportTypeDialog = true
    }

    fun isListFocusable(): Boolean = state.appInfoList.isNotEmpty() || state.currentTab == LibraryTab.RECOMMENDED

    fun requestContentFocusOrDefer() {
        if (!isListFocusable()) return
        lifecycleScope.launch {
            // Give composition a moment to settle
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

    // Restore focus when returning from game detail (without reloading list)
    LaunchedEffect(selectedAppId) {
        if (selectedAppId == null) {
            // Brief delay to let the UI settle after transition
            delay(100)
            // Restore focus to content area
            if (isListFocusable()) {
                requestContentFocusOrDefer()
            }
        }
    }

    // Restore focus after tab change - handles both empty and populated tabs
    LaunchedEffect(state.currentTab) {
        // Brief delay to let list populate after tab change
        delay(150)

        // The user may have moved focus up into the tab bar during the delay; don't yank it back.
        if (tabBarHasFocus) return@LaunchedEffect

        if (isListFocusable()) {
            // Tab has content - focus the first content item/container
            requestContentFocusOrDefer()
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
                        delay(32)
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
                val targetIndex = carouselFocusTargetListIndex
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
                        delay(32)
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

        if (selectedAppId == null &&
            !isSystemMenuOpen &&
            !state.isOptionsPanelOpen &&
            !state.isSearching &&
            !tabBarHasFocus
        ) {
            if (currentCount > 0) {
                requestContentFocusOrDefer()
            }
        }
    }

    val safePaddingModifier = Modifier.windowInsetsPadding(
        WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )

    CompositionLocalProvider(LocalBackgroundLayer provides backgroundLayer) {
        Box(
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
                                            // Trigger play dialog/action
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
            if (selectedAppId == null) {
                // Function to render the main library content (grid/carousel)
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
                            LibraryTab.LOCAL -> Triple(
                                R.string.library_source_no_custom_games,
                                R.string.add_custom_game_dialog_title,
                                onAddCustomGameClick,
                            )
                            else -> throw IllegalStateException("showEmptyStateSplash is true only for Steam/GOG/Epic/Amazon/LOCAL")
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
                                listState = listOverride ?: carouselListState,
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
                                focusTargetListIndex = carouselFocusTargetListIndex,
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
                                firstItemFocusRequester = mode360FocusRequester,
                                onFocusedIndexChanged = {
                                    mode360FocusTargetListIndex = it
                                    onFocusedIndexChanged(it)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LibraryListPane(
                                state = state,
                                listState = gridOverride ?: listState,
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

                Box(modifier = Modifier.fillMaxSize()) {
                    // Layer 1: Recorded background content.
                    // ROOT RECORDING: No padding on this box so coordinates are 1:1 with screen.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val layer = obtainGraphicsLayer()
                                onDrawWithContent {
                                    layer.record {
                                        this@onDrawWithContent.drawContent()
                                    }
                                    this@onDrawWithContent.drawLayer(layer)
                                    backgroundLayer = layer
                                }
                            }
                    ) {
                        if (state.videoBackgroundPath.isNotEmpty()) {
                            LoopingVideoBackground(
                                videoPath = state.videoBackgroundPath,
                                dimAlpha = state.videoBackgroundDimAlpha,
                                onColorExtracted = { hex ->
                                    if (state.mainColor != hex) {
                                        onSetMainColor(hex)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 1b. Padding is INSIDE the record box.
                        Box(modifier = Modifier.fillMaxSize().then(safePaddingModifier)) {
                            mainContent(0.dp, null, null)
                        }
                    }

                    // Layer 2: Overlays (Tab bar, Search bar)
                    Box(modifier = Modifier.fillMaxSize().then(safePaddingModifier)) {
                        val isScrolling = listState.isScrollInProgress || carouselListState.isScrollInProgress
                        val looks = LocalLooksState.current

                        if (state.isSearching) {
                            LibrarySearchBar(
                                isVisible = true,
                                searchQuery = state.searchQuery,
                                resultCount = state.totalAppsInFilter,
                                onScrollToTop = { listState.scrollToItem(0) },
                                onSearchQuery = onSearchQuery,
                                onDismiss = { onIsSearching(false) },
                                isScrolling = isScrolling,
                                distortionStrength = looks.liquidDistortion,
                                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                            )
                        } else {
                            LibraryTabBar(
                                currentTab = state.currentTab,
                                tabCounts = mapOf(
                                    LibraryTab.ALL to state.allCount,
                                    LibraryTab.FAVORITES to state.favoritesCount,
                                    LibraryTab.STEAM to state.steamCount,
                                    LibraryTab.GOG to state.gogCount,
                                    LibraryTab.EPIC to state.epicCount,
                                    LibraryTab.AMAZON to state.amazonCount,
                                    LibraryTab.LOCAL to state.localCount,
                                ),
                                onTabSelected = onTabChanged,
                                onOptionsClick = { onOptionsPanelToggle(true) },
                                onSearchClick = { onIsSearching(true) },
                                onAddGameClick = onAddCustomGameClick,
                                onMenuClick = { isSystemMenuOpen = true },
                                onNavigateDownToGrid = { requestContentFocusOrDefer() },
                                onPreviousTab = onPreviousTab,
                                onNextTab = onNextTab,
                                isScrolling = isScrolling,
                                distortionStrength = looks.liquidDistortion,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 26.dp)
                                    .fillMaxWidth()
                                    .onFocusChanged { tabBarHasFocus = it.hasFocus },
                            )
                        }
                    }
                }
            } else {
                LibraryDetailPane(
                    libraryItem = selectedLibraryItem,
                    onBack = { selectedAppId = null; selectedLibraryItem = null },
                    onClickPlay = { onClickPlay(selectedLibraryItem?.appId ?: "", it) },
                    onTestGraphics = { onTestGraphics(selectedLibraryItem?.appId ?: "") },
                    onPlayWithDiagnostics = { onPlayWithDiagnostics(selectedLibraryItem?.appId ?: "") },
                )
            }

            // Action bars and panels
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
                            labelResId = R.string.back,
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
                    currentPaneType = newPaneType
                    PrefManager.setLibraryLayout(isLandscape, isControllerConnected, newPaneType)
                },
                    steamCollections = state.steamCollections,
                    selectedSteamCollectionIds = state.selectedSteamCollectionIds,
                    steamCollectionCounts = state.steamCollectionCounts,
                    skippedDynamicCollections = state.skippedDynamicCollections,
                    isSteamConnected = isSteamConnected,
                    isOffline = isOffline,
                    onSteamCollectionToggle = onSteamCollectionToggle,
                    onClearSteamCollections = onClearSteamCollections,
                )

                val gogLoggedIn = GOGAuthManager.hasStoredCredentials(context)
                val epicLoggedIn = EpicAuthManager.hasStoredCredentials(context)
                val amazonLoggedIn = AmazonAuthManager.hasStoredCredentials(context)

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
                )
            }
        }

        // Add Android App Picker
        if (showAndroidAppPicker) {
            AndroidAppPicker(
                onAppSelected = { app ->
                    onAddAndroidApp(app.packageName)
                    showAndroidAppPicker = false
                },
                onDismiss = { showAndroidAppPicker = false }
            )
        }

        // Dual Import Type Dialog
        if (showImportTypeDialog) {
            AlertDialog(
                onDismissRequest = { showImportTypeDialog = false },
                title = { Text(stringResource(R.string.add_custom_game_dialog_title)) },
                text = {
                    Column {
                        // Import PC
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImportTypeDialog = false
                                    if (BuildConfig.MODERN_ANDROID) {
                                        showModernImportDialog = true
                                    } else if (PrefManager.showAddCustomGameDialog) {
                                        showAddCustomGameDialog = true
                                    } else {
                                        folderPicker.launchPicker()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.import_pc), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.import_pc_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Import App
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImportTypeDialog = false
                                    showAndroidAppPicker = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.import_app), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.import_app_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showImportTypeDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
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
                            modifier = Modifier.hapticClickable { importRemoveOriginal = !importRemoveOriginal },
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
                            HapticManager.trigger(HapticManager.HapticType.CLICK)
                            showModernImportDialog = false
                            importLauncher.launch(null)
                        },
                    ) {
                        Text(stringResource(R.string.continue_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        HapticManager.trigger(HapticManager.HapticType.CLICK)
                        showModernImportDialog = false
                    }) {
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
            var dontShowAgain by remember { mutableStateOf(false) }
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
