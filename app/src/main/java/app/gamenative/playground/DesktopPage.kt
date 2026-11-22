package app.gamenative.playground

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.ui.theme.BrokenGlassPanel
import app.gamenative.ui.util.SnackbarManager
import com.droiddeck.launcher.SessionActivity
import com.droiddeck.launcher.session.SessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified DroidDeck Linux Desktop container UI for PC Playground.
 * Installs and launches the mobile-optimized Ubuntu + KDE Plasma / KWin desktop session
 * (`files/playground_rootfs`) shared with the Xbox runtime, and manages Flatpak and AppImage packages.
 */
@Composable
fun DesktopPage(
    modifier: Modifier = Modifier,
    onLaunchDesktop: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val installState by LinuxRuntimeInstaller.state.collectAsState()
    var isBaseInstalled by remember { mutableStateOf(LinuxRuntime.isInstalled(context)) }
    var isDesktopInstalled by remember { mutableStateOf(LinuxRuntime.isDesktopInstalled(context)) }
    var flatpaks by remember { mutableStateOf<List<FlatpakManager.App>>(emptyList()) }
    var appImages by remember { mutableStateOf<List<AppImageManager.Item>>(emptyList()) }

    fun refreshLists() {
        scope.launch(Dispatchers.IO) {
            val baseOk = LinuxRuntime.isInstalled(context)
            val deskOk = LinuxRuntime.isDesktopInstalled(context)
            if (baseOk) {
                LinuxRuntime.stageSessionFiles(context)
            }
            val f = FlatpakManager.installedApps(context)
            val a = AppImageManager.list(context)
            withContext(Dispatchers.Main) {
                flatpaks = f
                appImages = a
                isBaseInstalled = baseOk
                isDesktopInstalled = deskOk
            }
        }
    }

    LaunchedEffect(Unit) {
        BwrapSpawner.start(context)
        refreshLists()
    }

    LaunchedEffect(installState) {
        if (installState is LinuxRuntimeInstaller.InstallState.Ready) {
            refreshLists()
        }
    }

    val isBusy = installState is LinuxRuntimeInstaller.InstallState.Downloading ||
        installState is LinuxRuntimeInstaller.InstallState.Extracting

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Desktop Launch / Setup Card
        BrokenGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 136.dp),
            cornerRadius = 24
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DesktopWindows,
                                contentDescription = "Desktop",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "KDE Plasma Desktop",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val statusSubtitle = when (val st = installState) {
                            is LinuxRuntimeInstaller.InstallState.Downloading -> {
                                val dlMb = st.downloadedBytes / (1024 * 1024)
                                val totMb = st.totalBytes / (1024 * 1024)
                                val pct = (st.progress * 100).toInt()
                                if (totMb > 0) {
                                    "${st.label} ($dlMb MB / $totMb MB • $pct%)"
                                } else {
                                    "${st.label} ($dlMb MB • $pct%)"
                                }
                            }
                            is LinuxRuntimeInstaller.InstallState.Extracting ->
                                "${st.label} (Please wait, unpacking archives...)"
                            is LinuxRuntimeInstaller.InstallState.Failed ->
                                "Setup failed: ${st.error}"
                            else -> when {
                                isDesktopInstalled -> "Unified DroidDeck Ubuntu + KDE Plasma Session Ready"
                                isBaseInstalled -> "Ubuntu base ready (used by Xbox) • Install KDE Plasma addon (~928 MB)"
                                else -> "DroidDeck Ubuntu + KDE Plasma Desktop (~1.1 GB total)"
                            }
                        }
                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        enabled = !isBusy,
                        onClick = {
                            if (isDesktopInstalled) {
                                scope.launch(Dispatchers.IO) {
                                    LinuxRuntime.stageSessionFiles(context)
                                    BwrapSpawner.start(context)
                                    withContext(Dispatchers.Main) {
                                        onLaunchDesktop()
                                    }
                                }
                            } else {
                                scope.launch {
                                    SnackbarManager.show("Downloading DroidDeck Ubuntu + KDE Plasma Desktop...")
                                    val ok = LinuxRuntimeInstaller.installDesktopEnvironment(context)
                                    if (ok) {
                                        isBaseInstalled = LinuxRuntime.isInstalled(context)
                                        isDesktopInstalled = LinuxRuntime.isDesktopInstalled(context)
                                        SnackbarManager.show("KDE Plasma Desktop ready to launch!")
                                    } else {
                                        SnackbarManager.show("Failed to install KDE Plasma Desktop. Check connection.")
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isDesktopInstalled) Icons.Default.PlayArrow else Icons.Default.Download,
                            contentDescription = if (isDesktopInstalled) "Launch" else "Setup"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isDesktopInstalled) "Launch" else if (isBusy) "Installing..." else "Setup")
                    }
                }

                when (val st = installState) {
                    is LinuxRuntimeInstaller.InstallState.Downloading -> {
                        val dlMb = st.downloadedBytes / (1024 * 1024)
                        val totMb = st.totalBytes / (1024 * 1024)
                        val pct = (st.progress * 100).toInt()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { st.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (totMb > 0) "$dlMb MB / $totMb MB" else "$dlMb MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    is LinuxRuntimeInstaller.InstallState.Extracting -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )
                            Text(
                                text = st.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // Section Tabs / Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Linux Apps (Flatpak & AppImage)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { refreshLists() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        // App List
        if (flatpaks.isEmpty() && appImages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Flatpaks or AppImages installed yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(flatpaks) { app ->
                    AppListItem(
                        name = app.name,
                        type = "Flatpak (${app.arch})",
                        detail = app.summary ?: app.id,
                        onClick = {
                            BwrapSpawner.start(context)
                            runCatching {
                                context.startActivity(
                                    Intent(context, SessionActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(SessionService.EXTRA_MODE, SessionService.MODE_RUN)
                                        .putExtra(SessionService.EXTRA_PROGRAM, FlatpakManager.LAUNCHER)
                                        .putExtra(SessionService.EXTRA_PROGRAM_ARGS, FlatpakManager.launchArgs(app).toTypedArray())
                                )
                            }.onFailure {
                                SnackbarManager.show("Failed to launch ${app.name}: ${it.message ?: "unknown error"}")
                            }
                        }
                    )
                }
                items(appImages) { item ->
                    AppListItem(
                        name = item.name,
                        type = "AppImage",
                        detail = item.comment ?: item.id,
                        onClick = {
                            BwrapSpawner.start(context)
                            runCatching {
                                context.startActivity(
                                    Intent(context, SessionActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(SessionService.EXTRA_MODE, SessionService.MODE_RUN)
                                        .putExtra(SessionService.EXTRA_PROGRAM, AppImageManager.LAUNCHER)
                                        .putExtra(SessionService.EXTRA_PROGRAM_ARGS, arrayOf(item.guestDir))
                                )
                            }.onFailure {
                                SnackbarManager.show("Failed to launch ${item.name}: ${it.message ?: "unknown error"}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    name: String,
    type: String,
    detail: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = type,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
