package app.gamenative.ui.component.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import app.gamenative.R
import app.gamenative.ui.theme.BrokenGlassDialogSurface
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.glassContentColor
import com.alorma.compose.settings.ui.base.internal.LocalSettingsGroupEnabled
import com.alorma.compose.settings.ui.base.internal.SettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults
import com.alorma.compose.settings.ui.base.internal.SettingsTileScaffold

/**
 * Reusable top-level full-screen glass picker dialog extracted from the Turnip driver picker.
 * Renders on the highest Z-plane with translucent live-wallpaper / Material You accent backdrop,
 * animated glass card container, optional search, single or multi-selection, and D-pad focus support.
 */
@Composable
fun FullScreenGlassSelectorDialog(
    visible: Boolean,
    title: @Composable () -> Unit,
    items: List<String>,
    selectedIndex: Int = -1,
    selectedIndices: Set<Int> = emptySet(),
    isMultiSelect: Boolean = false,
    itemSubtitles: List<String?>? = null,
    itemMuted: List<Boolean>? = null,
    itemDisabled: List<Boolean>? = null,
    searchable: Boolean = items.size > 5,
    emptyText: String? = null,
    footerActionLabel: String? = null,
    onFooterAction: (() -> Unit)? = null,
    onItemSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val looks = LocalLooksState.current
    val isGlass = looks.isGlassEnabled
    val contentColor = if (isGlass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val listFocusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        query = ""
        entered = true
        runCatching { listFocusRequester.requestFocus() }
    }

    val panelScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassPickerScale",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "glassPickerAlpha",
    )

    val filteredItems = remember(query, items) {
        if (query.isBlank()) {
            items.mapIndexed { index, text -> index to text }
        } else {
            items.mapIndexed { index, text -> index to text }
                .filterIndexed { _, (idx, text) ->
                    text.contains(query, ignoreCase = true) ||
                        (itemSubtitles?.getOrNull(idx)?.contains(query, ignoreCase = true) == true)
                }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // Keep underlying live wallpaper & accent colors clearly visible through the glass scrim.
            dialogWindow?.setDimAmount(if (isGlass) 0.18f else 0.45f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            looks.wallpaperColor.copy(alpha = if (isGlass) 0.42f else 0.65f),
                            primaryColor.copy(alpha = if (isGlass) 0.14f else 0.08f),
                            Color.Black.copy(alpha = if (isGlass) 0.34f else 0.55f),
                        ),
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            BrokenGlassDialogSurface(
                cornerRadius = 24,
                modifier = Modifier
                    .zIndex(1000f)
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .graphicsLayer {
                        scaleX = panelScale
                        scaleY = panelScale
                        alpha = panelAlpha
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                alpha = if (isGlass) 0.64f else 0.96f,
                            ),
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalContentColor provides contentColor) {
                                ProvideTextStyle(
                                    value = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                ) {
                                    title()
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val subtitleText = if (isMultiSelect) {
                                "${selectedIndices.size} / ${items.size} selected"
                            } else {
                                "${filteredItems.size} options"
                            }
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.65f),
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = contentColor,
                            )
                        }
                    }

                    if (searchable) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                        runCatching { listFocusRequester.requestFocus() }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.settings_interface_settingslistdropdownsearchable_searchlabel),
                                    color = contentColor.copy(alpha = 0.6f),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = primaryColor.copy(alpha = 0.85f),
                                )
                            },
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = contentColor.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = contentColor,
                                unfocusedTextColor = contentColor,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = contentColor.copy(alpha = 0.22f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions.Default.copy(showKeyboardOnFocus = false),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = contentColor.copy(alpha = 0.14f))

                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = emptyText ?: "No matching options",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.65f),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(
                                items = filteredItems,
                                key = { _, pair -> pair.first },
                            ) { position, (index, text) ->
                                val isDisabled = itemDisabled?.getOrNull(index) == true
                                val isSelected = if (isMultiSelect) {
                                    index in selectedIndices
                                } else {
                                    index == selectedIndex
                                }
                                val isMuted = itemMuted?.getOrNull(index) == true
                                val itemSubtitle = itemSubtitles?.getOrNull(index)
                                val cardShape = RoundedCornerShape(14.dp)
                                val cardBg = when {
                                    isSelected -> primaryColor.copy(alpha = 0.18f)
                                    isGlass -> Color.White.copy(alpha = 0.06f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                                }
                                val cardBorder = when {
                                    isSelected -> primaryColor.copy(alpha = 0.72f)
                                    isGlass -> looks.glassColor.copy(alpha = 0.16f)
                                    else -> contentColor.copy(alpha = 0.1f)
                                }
                                val textColor = when {
                                    isDisabled -> contentColor.copy(alpha = 0.42f)
                                    isSelected -> primaryColor
                                    isMuted -> contentColor.copy(alpha = 0.65f)
                                    else -> contentColor
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (position == 0) {
                                                Modifier.focusRequester(listFocusRequester)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clip(cardShape)
                                        .background(cardBg, cardShape)
                                        .border(1.dp, cardBorder, cardShape)
                                        .clickable(enabled = !isDisabled) {
                                            onItemSelected(index)
                                            if (!isMultiSelect) {
                                                onDismiss()
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = text,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!itemSubtitle.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = itemSubtitle,
                                                color = contentColor.copy(alpha = 0.62f),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    if (isMuted) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.download),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = contentColor.copy(alpha = 0.78f),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(primaryColor.copy(alpha = 0.16f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }

                                    if (isMultiSelect) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = primaryColor,
                                                uncheckedColor = contentColor.copy(alpha = 0.5f),
                                            ),
                                        )
                                    } else if (isSelected) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = contentColor.copy(alpha = 0.14f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (footerActionLabel != null && onFooterAction != null) {
                            Arrangement.SpaceBetween
                        } else {
                            Arrangement.End
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (footerActionLabel != null && onFooterAction != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onFooterAction()
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(text = footerActionLabel, color = primaryColor)
                            }
                        }
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(if (isMultiSelect) R.string.ok else R.string.close),
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen glass selector tile for single-choice Per-Game Container Settings.
 */
@Composable
fun ContainerGlassDropdown(
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalSettingsGroupEnabled.current,
    value: Int,
    items: List<String>,
    itemSubtitles: List<String?>? = null,
    itemMuted: List<Boolean>? = null,
    fallbackDisplay: String = "",
    searchable: Boolean = items.size > 5,
    onItemSelected: (Int) -> Unit,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    colors: SettingsTileColors = SettingsTileDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    action: @Composable (() -> Unit)? = null,
) {
    var isPickerOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val selectedText =
        if (value >= 0 && value < items.size) items[value] else fallbackDisplay

    val dismissPicker = {
        isPickerOpen = false
        runCatching { focusRequester.requestFocus() }
        Unit
    }

    SettingsTileScaffold(
        modifier = Modifier
            .focusRequester(focusRequester)
            .clickable(
                enabled = enabled,
                onClick = { isPickerOpen = true },
            )
            .then(modifier),
        enabled = enabled,
        title = title,
        subtitle = {
            if (subtitle != null) {
                Column {
                    ProvideTextStyle(value = LocalTextStyle.current.merge(TextStyle(fontStyle = FontStyle.Italic))) {
                        subtitle()
                    }
                    Text(
                        text = selectedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                }
            } else {
                Text(
                    text = selectedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                )
            }
        },
        icon = icon,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        FullScreenGlassSelectorDialog(
            visible = isPickerOpen,
            title = title,
            items = items,
            selectedIndex = value,
            isMultiSelect = false,
            itemSubtitles = itemSubtitles,
            itemMuted = itemMuted,
            searchable = searchable,
            onItemSelected = { idx ->
                onItemSelected(idx)
            },
            onDismiss = dismissPicker,
        )
        Row {
            Icon(
                modifier = Modifier.align(Alignment.CenterVertically),
                imageVector = if (isPickerOpen) {
                    Icons.Filled.ArrowDropUp
                } else {
                    Icons.Filled.ArrowDropDown
                },
                contentDescription = "Dropdown arrow",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                Spacer(modifier = Modifier.width(16.dp))
                action()
            }
        }
    }
}

/**
 * Full-screen glass selector tile for multi-choice Per-Game Container Settings
 * (e.g., Exposed Vulkan Extensions, multi-select environment variables).
 */
@Composable
fun ContainerGlassMultiDropdown(
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalSettingsGroupEnabled.current,
    values: List<Int>,
    items: List<String>,
    fallbackDisplay: String = "",
    searchable: Boolean = items.size > 5,
    onItemSelected: (Int) -> Unit,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    colors: SettingsTileColors = SettingsTileDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    action: @Composable (() -> Unit)? = null,
) {
    var isPickerOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val displayText = if (values.isNotEmpty()) {
        values.filter { it in items.indices }.joinToString(", ") { items[it] }
    } else {
        fallbackDisplay
    }

    val dismissPicker = {
        isPickerOpen = false
        runCatching { focusRequester.requestFocus() }
        Unit
    }

    SettingsTileScaffold(
        modifier = Modifier
            .focusRequester(focusRequester)
            .clickable(
                enabled = enabled,
                onClick = { isPickerOpen = true },
            )
            .then(modifier),
        enabled = enabled,
        title = title,
        subtitle = {
            if (subtitle != null) {
                Column {
                    ProvideTextStyle(value = LocalTextStyle.current.merge(TextStyle(fontStyle = FontStyle.Italic))) {
                        subtitle()
                    }
                    Text(
                        text = displayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                }
            } else {
                Text(
                    text = displayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                )
            }
        },
        icon = icon,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        FullScreenGlassSelectorDialog(
            visible = isPickerOpen,
            title = title,
            items = items,
            selectedIndices = values.toSet(),
            isMultiSelect = true,
            searchable = searchable,
            onItemSelected = onItemSelected,
            onDismiss = dismissPicker,
        )
        Row {
            Icon(
                modifier = Modifier.align(Alignment.CenterVertically),
                imageVector = if (isPickerOpen) {
                    Icons.Filled.ArrowDropUp
                } else {
                    Icons.Filled.ArrowDropDown
                },
                contentDescription = "Dropdown arrow",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                Spacer(modifier = Modifier.width(16.dp))
                action()
            }
        }
    }
}
