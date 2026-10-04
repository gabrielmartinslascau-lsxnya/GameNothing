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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.PluviaGlassPanel
import app.gamenative.utils.HapticManager
import app.gamenative.utils.toImageBitmap
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class GridMode { SNAP, FREE }

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
    onAddWidget: (PlaygroundWidgetHostManager) -> Unit = {}
) {
    val context = LocalContext.current
    val hostManager = remember { PlaygroundWidgetHostManager(context) }

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
    val looks = LocalLooksState.current
    val density = LocalDensity.current

    PluviaGlassPanel(
        modifier = modifier
            .padding(bottom = 16.dp)
            .height(72.dp),
        cornerRadius = 28,
        distortionStrength = looks.liquidDistortion
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
                        onEdit()
                        HapticManager.trigger(HapticManager.HapticType.CLICK)
                    }
                )
            } else {
                // For Widgets, we don't want to block normal clicks when NOT editing.
                Modifier.pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            if (!isEditing) {
                                onEdit()
                                HapticManager.trigger(HapticManager.HapticType.CLICK)
                            }
                        },
                        onDrag = { _, _ -> },
                        onDragEnd = { },
                        onDragCancel = { }
                    )
                }
            }
        ) {
            content()

            if (item is PlaygroundItem.WidgetItem && isEditing) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }
        }

        if (isEditing) {
            Box(
                modifier = Modifier
                    .offset(x = 16.dp, y = (-16).dp)
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onRemove()
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
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
    onAddWidget: () -> Unit
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Layout", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
