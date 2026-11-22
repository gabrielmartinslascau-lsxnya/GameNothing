package app.gamenative.ui.screen.library.components

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gamenative.playground.PlaygroundWidgetHostManager
import app.gamenative.ui.theme.BrokenGlassPanel
import app.gamenative.utils.HapticManager
import app.gamenative.utils.toImageBitmap
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import timber.log.Timber
import app.gamenative.PrefManager
import app.gamenative.ui.util.SnackbarManager

enum class GridMode { SNAP, FREE }
enum class PlaygroundViewMode { DROIDDECK_HUB, ANDROID_DESKTOP }

@Serializable
data class PlaygroundSettings(
    val gridMode: GridMode = GridMode.SNAP,
    val gridSize: Int = 5
)

@Serializable
sealed class PlaygroundItem {
    abstract val id: String
    abstract val xDp: Float
    abstract val yDp: Float
    abstract val isInDock: Boolean

    @Serializable
    data class AppItem(
        override val id: String,
        override val xDp: Float,
        override val yDp: Float,
        override val isInDock: Boolean = false,
        val packageName: String,
        val activityClassName: String
    ) : PlaygroundItem()

    @Serializable
    data class WidgetItem(
        override val id: String,
        override val xDp: Float,
        override val yDp: Float,
        override val isInDock: Boolean = false,
        val appWidgetId: Int,
        val widthDp: Float,
        val heightDp: Float
    ) : PlaygroundItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundTab(
    settings: PlaygroundSettings,
    onSettingsChange: (PlaygroundSettings) -> Unit,
    items: List<PlaygroundItem>,
    editingItemId: String?,
    onEditItem: (String?) -> Unit,
    onItemMoved: (id: String, x: Float, y: Float) -> Unit,
    onItemRemoved: (String) -> Unit,
    onToggleDockStatus: (String, Float?, Float?) -> Unit,
    onItemTapped: (PlaygroundItem) -> Unit,
    dockZoneTop: Dp,
    onAddApps: () -> Unit = {},
    onAddWidget: (PlaygroundWidgetHostManager) -> Unit = {},
    onLaunchDesktop: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hostManager = remember { PlaygroundWidgetHostManager(context) }
    val installState by app.gamenative.playground.LinuxRuntimeInstaller.state.collectAsState()
    var isDesktopInstalled by remember { mutableStateOf(app.gamenative.playground.LinuxRuntime.isDesktopInstalled(context)) }
    var isBaseInstalled by remember { mutableStateOf(app.gamenative.playground.LinuxRuntime.isInstalled(context)) }
    var showDesktopManagerDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(PlaygroundViewMode.DROIDDECK_HUB) }

    LaunchedEffect(installState) {
        if (installState is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Ready) {
            isDesktopInstalled = app.gamenative.playground.LinuxRuntime.isDesktopInstalled(context)
            isBaseInstalled = app.gamenative.playground.LinuxRuntime.isInstalled(context)
        }
    }

    DisposableEffect(Unit) {
        hostManager.startListening()
        onDispose {
            hostManager.stopListening()
        }
    }

    val gridItems = items.filter { !it.isInDock }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = this.maxHeight
        val dockZoneHeight = 120.dp
        val activeDockZoneTop = screenHeight - dockZoneHeight

        if (viewMode == PlaygroundViewMode.DROIDDECK_HUB) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 76.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Segmented View Switcher Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 2.dp
                    ) {
                        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = viewMode == PlaygroundViewMode.DROIDDECK_HUB,
                                onClick = {
                                    HapticManager.trigger(HapticManager.HapticType.CLICK)
                                    viewMode = PlaygroundViewMode.DROIDDECK_HUB
                                },
                                label = { Text("DroidDeck Hub") },
                                shape = RoundedCornerShape(20.dp)
                            )
                            FilterChip(
                                selected = viewMode == PlaygroundViewMode.ANDROID_DESKTOP,
                                onClick = {
                                    HapticManager.trigger(HapticManager.HapticType.CLICK)
                                    viewMode = PlaygroundViewMode.ANDROID_DESKTOP
                                },
                                label = { Text("Android Desktop") },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }

                // DROIDDECK HERO MASTER CONTROL CARD
                val isBusy = installState is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Downloading ||
                    installState is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Extracting

                BrokenGlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // DroidDeck Title & Status Info (Full width)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "DROIDDECK 0.7.x",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val subtitle = when (val st = installState) {
                                is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Downloading -> {
                                    val dlMb = st.downloadedBytes / (1024 * 1024)
                                    val totMb = st.totalBytes / (1024 * 1024)
                                    val pct = (st.progress * 100).toInt()
                                    if (totMb > 0) "${st.label} ($dlMb / $totMb MB • $pct%)"
                                    else "${st.label} ($dlMb MB • $pct%)"
                                }
                                is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Extracting ->
                                    "${st.label} (Unpacking rootfs...)"
                                is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Failed ->
                                    "Error: ${st.error}"
                                else -> when {
                                    isDesktopInstalled -> "Ready • Arch/Ubuntu Linux Runtime & KDE Plasma Active"
                                    isBaseInstalled -> "Base Ready • Tap Setup to install KDE Plasma (~928 MB)"
                                    else -> "Tap Setup to download DroidDeck Ubuntu + KDE Plasma (~1.1 GB)"
                                }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Horizontally scrollable Action Buttons (Never squished)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    HapticManager.trigger(HapticManager.HapticType.CLICK)
                                    try {
                                        val intent = Intent(context, com.droiddeck.launcher.FrontEndActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to launch DroidDeck FrontEndActivity")
                                        SnackbarManager.show("Error: ${e.message}")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.defaultMinSize(minWidth = 150.dp).wrapContentWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SportsEsports,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                )
                                Text("Enter DroidDeck", maxLines = 1)
                            }

                            OutlinedButton(
                                enabled = !isBusy,
                                onClick = {
                                    if (isDesktopInstalled) {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            app.gamenative.playground.LinuxRuntime.stageSessionFiles(context)
                                            app.gamenative.playground.BwrapSpawner.start(context)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                onLaunchDesktop()
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            SnackbarManager.show("Starting KDE Plasma Desktop download...")
                                            val ok = app.gamenative.playground.LinuxRuntimeInstaller.installDesktopEnvironment(context)
                                            isBaseInstalled = app.gamenative.playground.LinuxRuntime.isInstalled(context)
                                            isDesktopInstalled = app.gamenative.playground.LinuxRuntime.isDesktopInstalled(context)
                                            if (ok) {
                                                SnackbarManager.show("KDE Plasma Desktop installed! Tap Launch to start.")
                                            } else {
                                                SnackbarManager.show("Download failed — check network connection.")
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 100.dp).wrapContentWidth()
                            ) {
                                Text(
                                    text = if (isDesktopInstalled) "Launch Desktop" else if (isBusy) "Downloading..." else "Setup Linux (~1.1 GB)",
                                    maxLines = 1
                                )
                            }
                        }

                        when (val st = installState) {
                            is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Downloading -> {
                                val dlMb = st.downloadedBytes / (1024 * 1024)
                                val totMb = st.totalBytes / (1024 * 1024)
                                val pct = (st.progress * 100).toInt()
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { st.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (totMb > 0) "$dlMb MB / $totMb MB" else "$dlMb MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            is app.gamenative.playground.LinuxRuntimeInstaller.InstallState.Extracting -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = st.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // DROIDDECK SUBSYSTEMS GRID
                Text(
                    text = "SUBSYSTEMS & CAPABILITIES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                // 1. KDE Plasma Desktop
                DroidDeckSubsystemCard(
                    title = "KDE Plasma Desktop",
                    description = "Rootless mobile-optimized Linux desktop session with KWin and hardware acceleration",
                    icon = Icons.Filled.DesktopWindows,
                    badge = if (isDesktopInstalled) "Ready" else "Addon",
                    onClick = {
                        if (isDesktopInstalled) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                app.gamenative.playground.LinuxRuntime.stageSessionFiles(context)
                                app.gamenative.playground.BwrapSpawner.start(context)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    onLaunchDesktop()
                                }
                            }
                        } else {
                            scope.launch {
                                SnackbarManager.show("Installing KDE Plasma Desktop...")
                                app.gamenative.playground.LinuxRuntimeInstaller.installDesktopEnvironment(context)
                            }
                        }
                    }
                )

                // 2. Linux Software Center (Flatpak & AppImage)
                DroidDeckSubsystemCard(
                    title = "Flatpak & AppImage Center",
                    description = "Flathub desktop app catalog, package manager and rootless sandbox runner",
                    icon = Icons.Filled.Download,
                    badge = "Flathub",
                    onClick = { showDesktopManagerDialog = true }
                )

                // 4. GPU Drivers & Vulkan Overrides
                DroidDeckSubsystemCard(
                    title = "GPU Driver & Turnip Manager",
                    description = "Adreno Turnip KGSL, Mali Venus/Bridge, PowerVR, Xclipse and Zink overrides",
                    icon = Icons.Filled.Memory,
                    badge = "Vulkan",
                    onClick = {
                        try {
                            val intent = Intent(context, com.droiddeck.launcher.FrontEndActivity::class.java).apply {
                                putExtra("PAGE_KEY", "components")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open DroidDeck Components")
                            SnackbarManager.show("Error: ${e.message}")
                        }
                    }
                )

                // 5. Rootless File Manager
                DroidDeckSubsystemCard(
                    title = "Rootless File Explorer",
                    description = "Browse Linux guest rootfs, internal app storage, Wine prefixes and external drives",
                    icon = Icons.Filled.Folder,
                    badge = "Files",
                    onClick = {
                        try {
                            val intent = Intent(context, com.droiddeck.launcher.files.FileManagerActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to launch FileManagerActivity")
                            SnackbarManager.show("Error: ${e.message}")
                        }
                    }
                )

                // 6. Syncthing Cloud Sync
                DroidDeckSubsystemCard(
                    title = "Syncthing Save Sync",
                    description = "Continuous peer-to-peer game saves and directory synchronization",
                    icon = Icons.Filled.Sync,
                    badge = "P2P Sync",
                    onClick = {
                        try {
                            val intent = Intent(context, com.droiddeck.launcher.sync.SyncthingWebActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to launch SyncthingWebActivity")
                            SnackbarManager.show("Error: ${e.message}")
                        }
                    }
                )

                // 7. Performance & Controller Mapping
                DroidDeckSubsystemCard(
                    title = "Performance & Controller Mapping",
                    description = "CPU core isolation, phantom process limit bypass and gamepad chord mapping",
                    icon = Icons.Filled.Tune,
                    badge = "Settings",
                    onClick = {
                        try {
                            val intent = Intent(context, com.droiddeck.launcher.FrontEndActivity::class.java).apply {
                                putExtra("PAGE_KEY", "performance")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to launch Performance page")
                            SnackbarManager.show("Error: ${e.message}")
                        }
                    }
                )
            }
        } else {
            // Background dismissal for edit mode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (editingItemId != null) onEditItem(null)
                    }
            )

            PlaygroundGrid(
                settings = settings,
                items = gridItems,
                hostManager = hostManager,
                editingItemId = editingItemId,
                onEditItem = onEditItem,
                onItemMoved = onItemMoved,
                onItemRemoved = onItemRemoved,
                onToggleDockStatus = onToggleDockStatus,
                onItemTapped = onItemTapped,
                dockZoneTop = activeDockZoneTop
            )

            // Top Pill Switcher in Android Desktop mode
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 2.dp
                ) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = viewMode == PlaygroundViewMode.DROIDDECK_HUB,
                            onClick = {
                                HapticManager.trigger(HapticManager.HapticType.CLICK)
                                viewMode = PlaygroundViewMode.DROIDDECK_HUB
                            },
                            label = { Text("DroidDeck Hub") },
                            shape = RoundedCornerShape(20.dp)
                        )
                        FilterChip(
                            selected = viewMode == PlaygroundViewMode.ANDROID_DESKTOP,
                            onClick = {
                                HapticManager.trigger(HapticManager.HapticType.CLICK)
                                viewMode = PlaygroundViewMode.ANDROID_DESKTOP
                            },
                            label = { Text("Android Desktop") },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        if (showDesktopManagerDialog) {
            AlertDialog(
                onDismissRequest = { showDesktopManagerDialog = false },
                title = { Text("Flatpak & AppImage Center") },
                text = {
                    Box(modifier = Modifier.height(440.dp)) {
                        app.gamenative.playground.DesktopPage(
                            onLaunchDesktop = {
                                showDesktopManagerDialog = false
                                onLaunchDesktop()
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDesktopManagerDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun DroidDeckSubsystemCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BrokenGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                HapticManager.trigger(HapticManager.HapticType.CLICK)
                onClick()
            },
        cornerRadius = 18
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun PlaygroundDock(
    dockItems: List<PlaygroundItem.AppItem>,
    editingItemId: String?,
    onEditItem: (String?) -> Unit,
    onItemRemoved: (String) -> Unit,
    onToggleDockStatus: (String, Float?, Float?) -> Unit,
    onItemTapped: (PlaygroundItem) -> Unit,
    dockZoneTop: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BrokenGlassPanel(
        modifier = modifier
            .padding(bottom = 16.dp)
            .height(72.dp),
        cornerRadius = 28
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockItems.forEach { item ->
                key(item.id) {
                    var dragOffsetY by remember { mutableStateOf(0f) }

                    Box(
                        modifier = Modifier.pointerInput(item.id) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += with(density) { dragAmount.y.toDp().value }
                                },
                                onDragEnd = {
                                    if (dragOffsetY < -50f) {
                                        // Dragged OUT of dock (upward)
                                        val releaseY = dockZoneTop.value + dragOffsetY
                                        onToggleDockStatus(item.id, item.xDp, releaseY)
                                        HapticManager.trigger(HapticManager.HapticType.CLICK)
                                    }
                                    dragOffsetY = 0f
                                }
                            )
                        }
                    ) {
                        PlaygroundItemWrapper(
                            item = item,
                            isEditing = editingItemId == item.id,
                            onEdit = { onEditItem(item.id) },
                            onRemove = { onItemRemoved(item.id) },
                            onTap = { onItemTapped(item) }
                        ) {
                            PlaygroundAppIcon(item, cellSize = 48.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaygroundGrid(
    settings: PlaygroundSettings,
    items: List<PlaygroundItem>,
    hostManager: PlaygroundWidgetHostManager,
    editingItemId: String?,
    onEditItem: (String?) -> Unit,
    onItemMoved: (id: String, x: Float, y: Float) -> Unit,
    onItemRemoved: (String) -> Unit,
    onToggleDockStatus: (String, Float?, Float?) -> Unit,
    onItemTapped: (PlaygroundItem) -> Unit,
    dockZoneTop: Dp
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellSizeDp = this.maxWidth / settings.gridSize

        items.forEach { item ->
            key(item.id) {
                var offsetX by remember { mutableStateOf(item.xDp) }
                var offsetY by remember { mutableStateOf(item.yDp) }

                Box(
                    modifier = Modifier
                        .offset(x = offsetX.dp, y = offsetY.dp)
                        .pointerInput(item.id, settings.gridMode) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += with(density) { dragAmount.x.toDp().value }
                                    offsetY += with(density) { dragAmount.y.toDp().value }
                                },
                                onDragEnd = {
                                    val iconBottomY = offsetY + 64f // Rough bottom edge
                                    if (iconBottomY > dockZoneTop.value && item is PlaygroundItem.AppItem) {
                                        onToggleDockStatus(item.id, null, null)
                                        HapticManager.trigger(HapticManager.HapticType.CLICK)
                                    } else {
                                        if (settings.gridMode == GridMode.SNAP) {
                                            val cellVal = cellSizeDp.value
                                            offsetX = (offsetX / cellVal).roundToInt() * cellVal
                                            offsetY = (offsetY / cellVal).roundToInt() * cellVal
                                        }
                                        onItemMoved(item.id, offsetX, offsetY)
                                    }
                                }
                            )
                        }
                ) {
                    PlaygroundItemWrapper(
                        item = item,
                        isEditing = editingItemId == item.id,
                        onEdit = { onEditItem(item.id) },
                        onRemove = { onItemRemoved(item.id) },
                        onTap = { onItemTapped(item) }
                    ) {
                        when (item) {
                            is PlaygroundItem.AppItem -> PlaygroundAppIcon(item, cellSizeDp)
                            is PlaygroundItem.WidgetItem -> {
                                PlaygroundWidgetHost(item, hostManager)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaygroundItemWrapper(
    item: PlaygroundItem,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTap: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = if (item is PlaygroundItem.AppItem) {
                Modifier.combinedClickable(
                    onClick = {
                        if (isEditing) onEdit()
                        else onTap()
                    },
                    onLongClick = {
                        HapticManager.trigger(HapticManager.HapticType.LONG)
                        onEdit()
                    }
                )
            } else {
                Modifier.clickable {
                    if (isEditing) onEdit()
                    else onTap()
                }
            }
        ) {
            content()
        }

        if (isEditing) {
            Box(
                modifier = Modifier
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    onClick = onRemove,
                    modifier = Modifier.fillMaxSize(),
                    tonalElevation = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp).padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaygroundWidgetHost(item: PlaygroundItem.WidgetItem, hostManager: PlaygroundWidgetHostManager) {
    Box(modifier = Modifier.size(width = item.widthDp.dp, height = item.heightDp.dp)) {
        AndroidView(
            factory = {
                hostManager.createHostView(it, item.appWidgetId) ?: View(it)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun PlaygroundAppIcon(item: PlaygroundItem.AppItem, cellSize: Dp) {
    val context = LocalContext.current
    val icon = remember(item.packageName, item.activityClassName) {
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            launcherApps.getActivityList(item.packageName, Process.myUserHandle())
                .firstOrNull { it.componentName.className == item.activityClassName }
                ?.getIcon(context.resources.displayMetrics.densityDpi)
        }.getOrNull()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(cellSize)
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.toImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(cellSize * 0.7f)
                    .clip(RoundedCornerShape(cellSize * 0.15f))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(cellSize * 0.7f)
                    .clip(RoundedCornerShape(cellSize * 0.15f))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
fun PlaygroundSettingsPanel(
    settings: PlaygroundSettings,
    onSettingsChange: (PlaygroundSettings) -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onAddApps: () -> Unit,
    onAddWidget: () -> Unit,
    onLaunchDesktop: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Playground",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ListItem(
            headlineContent = { Text("Android Settings") },
            supportingContent = { Text("Open system settings") },
            leadingContent = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
            modifier = Modifier.clickable { onOpenAndroidSettings() }
        )

        ListItem(
            headlineContent = { Text("Add Apps") },
            supportingContent = { Text("Add apps to your playground") },
            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
            modifier = Modifier.clickable { onAddApps() }
        )

        ListItem(
            headlineContent = { Text("Add Widget") },
            supportingContent = { Text("Add system widgets") },
            leadingContent = { Icon(Icons.Filled.Widgets, contentDescription = null) },
            modifier = Modifier.clickable { onAddWidget() }
        )

        val context = LocalContext.current
        var autoLaunch by remember { mutableStateOf(PrefManager.playgroundAutoLaunchDroidDeck) }

        ListItem(
            headlineContent = { Text("Launch DroidDeck Launcher") },
            supportingContent = { Text("Open full DroidDeck interface with all features") },
            leadingContent = { Icon(Icons.Filled.SportsEsports, contentDescription = null) },
            modifier = Modifier.clickable {
                try {
                    val intent = Intent(context, com.droiddeck.launcher.FrontEndActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to launch DroidDeck FrontEndActivity")
                }
            }
        )

        ListItem(
            headlineContent = { Text("Auto-Launch DroidDeck") },
            supportingContent = { Text("Immediately open DroidDeck when selecting Playground tab") },
            trailingContent = {
                Switch(
                    checked = autoLaunch,
                    onCheckedChange = {
                        autoLaunch = it
                        PrefManager.playgroundAutoLaunchDroidDeck = it
                    }
                )
            }
        )

        var showLinuxDesktopDialog by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Linux Desktop (Playground)") },
            supportingContent = { Text("KDE Plasma, Flatpak & AppImage manager") },
            leadingContent = { Icon(Icons.Filled.DesktopWindows, contentDescription = null) },
            modifier = Modifier.clickable { showLinuxDesktopDialog = true }
        )

        if (showLinuxDesktopDialog) {
            AlertDialog(
                onDismissRequest = { showLinuxDesktopDialog = false },
                title = { Text("Linux Playground") },
                text = {
                    Box(modifier = Modifier.height(400.dp)) {
                        app.gamenative.playground.DesktopPage(
                            onLaunchDesktop = {
                                showLinuxDesktopDialog = false
                                onLaunchDesktop()
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLinuxDesktopDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Layout", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = settings.gridMode == GridMode.SNAP,
                onClick = { onSettingsChange(settings.copy(gridMode = GridMode.SNAP)) },
                label = { Text("Snap to grid") }
            )
            FilterChip(
                selected = settings.gridMode == GridMode.FREE,
                onClick = { onSettingsChange(settings.copy(gridMode = GridMode.FREE)) },
                label = { Text("Free placement") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Grid size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (4..8).forEach { size ->
                FilterChip(
                    selected = settings.gridSize == size,
                    onClick = { onSettingsChange(settings.copy(gridSize = size)) },
                    label = { Text("${size}x$size") }
                )
            }
        }

    }
}
