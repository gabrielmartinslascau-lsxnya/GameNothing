package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.LaunchedEffect
import app.gamenative.PrefManager
import app.gamenative.linux.LinuxContainerManager
import app.gamenative.service.LinuxEnvironmentService
import app.gamenative.utils.ManifestComponentHelper
import app.gamenative.utils.ManifestContentTypes
import app.gamenative.utils.downloader.LinuxRootfsDownloader
import com.winlator.contents.AdrenotoolsManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxSetupDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onSwitchToLinux: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var protonVersion by remember { mutableStateOf(PrefManager.linuxProtonVersion) }
    var dxvkVersion by remember { mutableStateOf(PrefManager.linuxDxvkVersion) }
    var fexPreset by remember { mutableStateOf(PrefManager.linuxFexPreset) }
    var driver by remember { mutableStateOf(PrefManager.linuxDriver) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var isInstalled by remember { mutableStateOf(LinuxRootfsDownloader.isRootfsInstalled(context)) }

    var protonOptions by remember {
        mutableStateOf(
            listOf(
                "proton-11.0-1-arm64ec-1",
                "proton-11.0-1-x86_64-1",
                "proton-10.0-4-arm64ec-1",
                "proton-10.0-4-x86_64-1",
                "proton-10.0-arm64ec-2",
                "wine-9.2-x86_64"
            )
        )
    }

    LaunchedEffect(Unit) {
        runCatching {
            val availability = ManifestComponentHelper.loadComponentAvailability(context)
            val protonEntries = (availability.manifest.items[ManifestContentTypes.PROTON] ?: emptyList()) +
                    (availability.manifest.items[ManifestContentTypes.WINE] ?: emptyList())
            val installedProtons = availability.installed.proton + availability.installed.wine
            val ids = (protonEntries.map { it.id } + installedProtons).distinct()
            if (ids.isNotEmpty()) {
                protonOptions = ids
                if (protonVersion.isEmpty() || !ids.contains(protonVersion)) {
                    val match = ids.find { it.contains(protonVersion, ignoreCase = true) || protonVersion.contains(it, ignoreCase = true) }
                    protonVersion = match ?: ids.first()
                }
            }
        }
    }

    val dxvkOptions = listOf("2.7.1", "2.4.1", "2.3.1", "2.3", "2.2", "1.10.3", "wined3d", "vkd3d")
    val fexOptions = listOf("compatibility", "intermediate", "performance", "extreme", "stability", "denuvo")

    val adrenotoolsManager = remember { AdrenotoolsManager(context) }
    val installedDrivers = remember { adrenotoolsManager.enumarateInstalledDrivers() }
    val driverOptions = remember(installedDrivers) { (listOf("System") + installedDrivers).distinct() }

    if (driver.isEmpty() && driverOptions.isNotEmpty()) {
        driver = driverOptions.first()
    }

    Dialog(onDismissRequest = { if (!isDownloading) onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Linux Environment Setup",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Proton Selection Dropdown
                DropdownSelector(
                    label = "Proton Version",
                    selected = protonVersion,
                    options = protonOptions,
                    onSelected = { protonVersion = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // DXVK Selection Dropdown
                DropdownSelector(
                    label = "DXVK Version",
                    selected = dxvkVersion,
                    options = dxvkOptions,
                    onSelected = { dxvkVersion = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // FEX Preset Selection Dropdown
                DropdownSelector(
                    label = "FEX Preset",
                    selected = fexPreset,
                    options = fexOptions,
                    onSelected = { fexPreset = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Driver Selection Dropdown
                DropdownSelector(
                    label = "Graphics Driver",
                    selected = driver,
                    options = driverOptions,
                    onSelected = { driver = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isDownloading) {
                    Text(
                        text = statusMessage.ifEmpty { "Downloading rootfs... ${(downloadProgress * 100).roundToInt()}%" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        enabled = !isDownloading
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isInstalled) {
                        Button(
                            onClick = {
                                isDownloading = true
                                scope.launch {
                                    val result = LinuxContainerManager.ensureAndSetupLinuxEnvironment(
                                        context = context,
                                        protonVersion = protonVersion,
                                        dxvkVersion = dxvkVersion,
                                        fexPreset = fexPreset,
                                        driver = driver,
                                        onProgress = { downloadProgress = it },
                                        onStatusChange = { statusMessage = it }
                                    )
                                    isDownloading = false
                                    if (result.isSuccess) {
                                        LinuxEnvironmentService.start(context)
                                        onSwitchToLinux()
                                    } else {
                                        statusMessage = "Setup error: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            enabled = !isDownloading
                        ) {
                            Text("Switch to Linux")
                        }
                    } else {
                        Button(
                            onClick = {
                                isDownloading = true
                                scope.launch {
                                    val result = LinuxContainerManager.ensureAndSetupLinuxEnvironment(
                                        context = context,
                                        protonVersion = protonVersion,
                                        dxvkVersion = dxvkVersion,
                                        fexPreset = fexPreset,
                                        driver = driver,
                                        onProgress = { downloadProgress = it },
                                        onStatusChange = { statusMessage = it }
                                    )
                                    isDownloading = false
                                    if (result.isSuccess) {
                                        isInstalled = true
                                        LinuxEnvironmentService.start(context)
                                        onSwitchToLinux()
                                    } else {
                                        statusMessage = "Download failed: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            enabled = !isDownloading
                        ) {
                            Text("Download & Setup")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
