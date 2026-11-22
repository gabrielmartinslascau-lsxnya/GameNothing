package app.gamenative.ui.screen.library.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.data.XboxGame
import app.gamenative.playground.LinuxRuntime
import app.gamenative.playground.LinuxRuntimeInstaller
import app.gamenative.service.xbox.StoreAccounts
import app.gamenative.service.xbox.XboxConsoleDiscovery
import app.gamenative.service.xbox.XboxConstants
import app.gamenative.service.xbox.XboxService
import app.gamenative.service.xbox.XboxStoreId
import app.gamenative.service.xbox.cloud.XboxCloudActivity
import app.gamenative.service.xbox.cloud.XboxCloudPlayerActivity
import app.gamenative.ui.theme.LocalLooksState
import app.gamenative.ui.theme.BrokenGlassPanel
import app.gamenative.ui.theme.glassContentColor
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.HapticManager
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class XboxGameFilter {
    ALL, INSTALLED, CLOUD, CONSOLE
}

@Composable
fun XboxStationTab(
    xboxGames: List<XboxGame>,
    onSignInClick: () -> Unit,
    onPlayLocalGame: (XboxGame) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val looks = LocalLooksState.current

    var gamertag by remember { mutableStateOf<String?>(null) }
    var consoles by remember { mutableStateOf<List<XboxConsoleDiscovery.Console>>(emptyList()) }
    var isLoadingConsoles by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(XboxGameFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val installState by LinuxRuntimeInstaller.state.collectAsState()
    var isLinuxInstalled by remember { mutableStateOf(LinuxRuntime.isInstalled(context)) }

    LaunchedEffect(installState) {
        if (installState is LinuxRuntimeInstaller.InstallState.Ready) {
            isLinuxInstalled = LinuxRuntime.isInstalled(context)
        }
    }

    val isLoggedIn = remember { XboxService.hasStoredCredentials(context) }

    // Load Xbox account details and registered consoles
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            val account = StoreAccounts.read(context, XboxStoreId.XBOX)
            gamertag = account?.optString("gamertag")?.takeIf { it.isNotBlank() }
            isLoadingConsoles = true
            consoles = XboxConsoleDiscovery.listConsoles(context)
            isLoadingConsoles = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 96.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TOP HERO: Gamertag, Avatar, Subscription & Actions
        item {
            BrokenGlassPanel(
                cornerRadius = 20,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF107C10)), // Official Xbox Green
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = "Xbox Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) (gamertag ?: "Xbox Player") else "Xbox & Game Pass",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = glassContentColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isLoggedIn) "Game Pass Ultimate • Connected" else "Sign in to play PC, Cloud & Console games",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassContentColor().copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isLoggedIn) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    XboxService.logout(context)
                                    gamertag = null
                                    consoles = emptyList()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.wrapContentWidth().defaultMinSize(minWidth = 84.dp)
                        ) {
                            Text("Sign Out", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = onSignInClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF107C10),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.wrapContentWidth().defaultMinSize(minWidth = 84.dp)
                        ) {
                            Text("Sign In", maxLines = 1)
                        }
                    }
                }
            }
        }

        // 1b. DROIDDECK LINUX RUNTIME CARD FOR LOCAL XBOX INSTALLATION
        item {
            val isBusy = installState is LinuxRuntimeInstaller.InstallState.Downloading ||
                installState is LinuxRuntimeInstaller.InstallState.Extracting

            BrokenGlassPanel(
                cornerRadius = 16,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF107C10).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLinuxInstalled) Icons.Filled.CheckCircle else Icons.Filled.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF107C10),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Xbox Local Engine (DroidDeck Rootfs)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF107C10)
                            )
                            val statusText = when (val st = installState) {
                                is LinuxRuntimeInstaller.InstallState.Downloading -> {
                                    val dlMb = st.downloadedBytes / (1024 * 1024)
                                    val totMb = st.totalBytes / (1024 * 1024)
                                    val pct = (st.progress * 100).toInt()
                                    if (totMb > 0) "${st.label} ($dlMb / $totMb MB • $pct%)"
                                    else "${st.label} ($dlMb MB • $pct%)"
                                }
                                is LinuxRuntimeInstaller.InstallState.Extracting ->
                                    "${st.label} (Unpacking rootfs...)"
                                is LinuxRuntimeInstaller.InstallState.Failed ->
                                    "Error: ${st.error}"
                                else -> if (isLinuxInstalled) {
                                    "Linux Runtime Active • Local MSIXVC & Wine Ready"
                                } else {
                                    "Base Ubuntu Linux runtime (~291 MB) required for local PC Game Pass installs."
                                }
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = glassContentColor().copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }

                        if (!isLinuxInstalled) {
                            Button(
                                enabled = !isBusy,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        SnackbarManager.show("Starting Ubuntu Linux runtime download...")
                                        val ok = LinuxRuntimeInstaller.ensureInstalledBlocking(context)
                                        withContext(Dispatchers.Main) {
                                            isLinuxInstalled = LinuxRuntime.isInstalled(context)
                                            if (ok) {
                                                SnackbarManager.show("Linux Runtime installed! Local installs ready.")
                                            } else {
                                                SnackbarManager.show("Linux Runtime download failed — check internet connection.")
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C10)),
                                modifier = Modifier.defaultMinSize(minWidth = 100.dp)
                            ) {
                                Text(if (isBusy) "Installing..." else "Install")
                            }
                        }
                    }

                    when (val st = installState) {
                        is LinuxRuntimeInstaller.InstallState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { st.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF107C10)
                            )
                        }
                        is LinuxRuntimeInstaller.InstallState.Extracting -> {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF107C10)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // 2. CONSOLE REMOTE PLAY SHELF
        if (isLoggedIn) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "YOUR CONSOLES (REMOTE PLAY)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isLoadingConsoles = true
                                    consoles = XboxConsoleDiscovery.listConsoles(context)
                                    isLoadingConsoles = false
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh Consoles",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isLoadingConsoles) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (consoles.isEmpty()) {
                        BrokenGlassPanel(
                            cornerRadius = 14,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No Xbox Series X|S or One consoles registered. Turn on Remote Features in your Xbox settings to connect directly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassContentColor().copy(alpha = 0.7f),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(consoles) { console ->
                                BrokenGlassPanel(
                                    cornerRadius = 16,
                                    modifier = Modifier.width(220.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(if (console.powerState.equals("On", ignoreCase = true)) Color(0xFF107C10) else Color.Gray)
                                            )
                                            Text(
                                                text = console.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = glassContentColor(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${console.consoleType} • ${console.powerState}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = glassContentColor().copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = Intent(context, XboxCloudPlayerActivity::class.java).apply {
                                                        putExtra("console_id", console.serverId)
                                                        putExtra("console_name", console.name)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    SnackbarManager.show("Error starting Remote Play: ${e.message}")
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C10)),
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Tv,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                            )
                                            Text("Connect", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. SEARCH & FILTER BAR
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Game Pass & Xbox library...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = glassContentColor().copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF107C10),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )

                // Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    XboxGameFilter.values().forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = {
                                HapticManager.trigger(HapticManager.HapticType.CLICK)
                                selectedFilter = filter
                            },
                            label = {
                                Text(
                                    when (filter) {
                                        XboxGameFilter.ALL -> "All (${xboxGames.size})"
                                        XboxGameFilter.INSTALLED -> "Installed"
                                        XboxGameFilter.CLOUD -> "Cloud Enabled"
                                        XboxGameFilter.CONSOLE -> "PC / Console"
                                    }
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF107C10).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFF107C10)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }

        // 4. GAMES LIST
        val filteredGames = xboxGames.filter { game ->
            val matchesSearch = searchQuery.isBlank() ||
                game.title.contains(searchQuery, ignoreCase = true) ||
                game.developer.contains(searchQuery, ignoreCase = true) ||
                game.publisher.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                XboxGameFilter.ALL -> true
                XboxGameFilter.INSTALLED -> game.isInstalled
                XboxGameFilter.CLOUD -> game.isCloudSupported
                XboxGameFilter.CONSOLE -> game.isPcSupported
            }

            matchesSearch && matchesFilter
        }

        if (filteredGames.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoggedIn) "No games match your query." else "Sign in above to see your Game Pass library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassContentColor().copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            items(
                items = filteredGames,
                key = { it.productId }
            ) { game ->
                XboxGameCard(
                    game = game,
                    onPlay = { onPlayLocalGame(game) },
                    onStream = {
                        try {
                            val intent = Intent(context, XboxCloudPlayerActivity::class.java).apply {
                                putExtra("product", game.productId)
                                putExtra("title", game.title)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val webIntent = XboxCloudActivity.intent(context, "https://www.xbox.com/play/launch/game/${game.productId}")
                            context.startActivity(webIntent)
                        }
                    },
                    onInstall = {
                        if (!LinuxRuntime.isInstalled(context)) {
                            SnackbarManager.show("Downloading Linux runtime first...")
                            scope.launch(Dispatchers.IO) {
                                val ok = LinuxRuntimeInstaller.ensureInstalledBlocking(context)
                                withContext(Dispatchers.Main) {
                                    isLinuxInstalled = LinuxRuntime.isInstalled(context)
                                    if (ok) {
                                        val installPath = XboxConstants.getGameInstallPath(context, game.title, game.productId)
                                        XboxService.downloadGame(context, game.productId, installPath)
                                        SnackbarManager.show("Installing ${game.title}...")
                                    } else {
                                        SnackbarManager.show("Failed to install required Linux runtime.")
                                    }
                                }
                            }
                        } else {
                            val installPath = XboxConstants.getGameInstallPath(context, game.title, game.productId)
                            val res = XboxService.downloadGame(context, game.productId, installPath)
                            if (res.isSuccess) {
                                SnackbarManager.show("Installing ${game.title} via DroidDeck Xodus...")
                            } else {
                                SnackbarManager.show("Installation error: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun XboxGameCard(
    game: XboxGame,
    onPlay: () -> Unit,
    onStream: () -> Unit,
    onInstall: () -> Unit
) {
    BrokenGlassPanel(
        cornerRadius = 14,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Box Art
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (game.artUrl.isNotBlank()) {
                    CoilImage(
                        imageModel = { game.artUrl },
                        imageOptions = ImageOptions(
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Title & Badges
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassContentColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = game.developer.ifEmpty { game.publisher }.ifEmpty { "Xbox Game Studios" },
                    style = MaterialTheme.typography.bodySmall,
                    color = glassContentColor().copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (game.isInstalled) {
                        BadgePill("Installed", Color(0xFF107C10))
                    }
                    if (game.isCloudSupported) {
                        BadgePill("Cloud", Color(0xFF0078D4))
                    }
                    if (game.isPcSupported) {
                        BadgePill("PC Game Pass", Color(0xFF107C10))
                    }
                }
            }

            // Action Buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (game.isInstalled) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C10)),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = onInstall,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Install", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (game.isCloudSupported) {
                    FilledTonalButton(
                        onClick = onStream,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stream", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
