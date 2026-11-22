package app.gamenative.ui.component.dialog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.component.settings.ContainerGlassDropdown
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.winlator.container.Container
import com.winlator.container.ContainerData
import com.winlator.core.DefaultVersion
import com.winlator.core.KeyValueSet
import com.winlator.core.StringUtils
import com.winlator.contents.ContentProfile
import com.winlator.xenvironment.components.PulseAudioComponent
import java.util.Locale

@Composable
fun GeneralTabContent(
    state: ContainerConfigState,
    nonzeroResolutionError: String,
) {
    val config = state.config.value

    // Microphone input is opt-in. The RECORD_AUDIO runtime permission is only requested when
    // the user turns the toggle on (never at screen entry), and only when it isn't already
    // granted. The grant state is checked fresh at toggle time rather than cached, so a
    // permission revoked in system settings is picked up correctly.
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Only enable the option once the user actually granted access.
        if (granted) {
            state.config.value = state.config.value.copy(micEnabled = true)
        }
    }

    val graphicsDrivers = state.graphicsDrivers.value
    val glibcWineEntries = state.glibcWineEntries.value
    val bionicWineEntries = state.bionicWineEntries.value
    val suspendBehaviorEntries = listOf(
        stringResource(R.string.suspend_behavior_manual),
        stringResource(R.string.suspend_behavior_auto),
        stringResource(R.string.suspend_behavior_never),
    )
    val suspendBehaviorIndex = when {
        config.suspendPolicy.equals(Container.SUSPEND_POLICY_MANUAL, ignoreCase = true) -> 0
        config.suspendPolicy.equals(Container.SUSPEND_POLICY_NEVER, ignoreCase = true) -> 2
        else -> 1
    }

    var showResolutionPicker by rememberSaveable { mutableStateOf(false) }

    if (showResolutionPicker) {
        ResolutionPickerDialog(
            currentScreenSize = config.screenSize,
            nonzeroResolutionError = nonzeroResolutionError,
            onDismissRequest = { showResolutionPicker = false },
            onApply = { newScreenSize ->
                state.config.value = state.config.value.copy(screenSize = newScreenSize)
                showResolutionPicker = false
            },
        )
    }

    ContainerGlassCard {
        SettingsGroup {
            run {
                val variantIndex = rememberSaveable {
                    mutableIntStateOf(
                        state.containerVariants.indexOfFirst { it.equals(config.containerVariant, true) }.coerceAtLeast(0)
                    )
                }
                ContainerGlassDropdown(
                    colors = settingsTileColors(),
                    title = { Text(text = stringResource(R.string.container_variant)) },
                    value = variantIndex.value,
                    items = state.containerVariants,
                    onItemSelected = { idx ->
                        variantIndex.value = idx
                        val newVariant = state.containerVariants[idx]
                        if (newVariant.equals(Container.GLIBC, ignoreCase = true)) {
                            val defaultDriver = Container.DEFAULT_GRAPHICS_DRIVER
                            val newCfg = KeyValueSet(config.graphicsDriverConfig).apply {
                                put("version", "")
                                put("syncFrame", "0")
                                put("disablePresentWait", get("disablePresentWait").ifEmpty { "0" })
                                if (get("presentMode").isEmpty()) put("presentMode", "mailbox")
                                if (get("resourceType").isEmpty()) put("resourceType", "auto")
                                if (get("bcnEmulation").isEmpty()) put("bcnEmulation", "auto")
                                if (get("bcnEmulationType").isEmpty()) put("bcnEmulationType", "compute")
                                if (get("bcnEmulationCache").isEmpty()) put("bcnEmulationCache", "1")
                                put("adrenotoolsTurnip", "1")
                            }
                            state.graphicsDriverIndex.value =
                                graphicsDrivers.indexOfFirst { StringUtils.parseIdentifier(it) == defaultDriver }.coerceAtLeast(0)
                            state.graphicsDriverVersionIndex.value = 0
                            state.syncEveryFrameChecked.value = false
                            state.disablePresentWaitChecked.value = newCfg.get("disablePresentWait", "0") == "1"
                            state.bcnEmulationCacheEnabled.value = newCfg.get("bcnEmulationCache", "1") == "1"
                            state.adrenotoolsTurnipChecked.value = true

                            val defaultGlibcWine = glibcWineEntries.firstOrNull() ?: Container.DEFAULT_WINE_VERSION
                            state.config.value = config.copy(
                                containerVariant = newVariant,
                                wineVersion = defaultGlibcWine,
                                graphicsDriver = defaultDriver,
                                graphicsDriverVersion = "",
                                graphicsDriverConfig = newCfg.toString(),
                                box64Version = "0.3.6",
                            )
                        } else {
                            val defaultBionicDriver = StringUtils.parseIdentifier(state.bionicGraphicsDrivers.first())
                            val newWine = if (config.wineVersion == (glibcWineEntries.firstOrNull() ?: Container.DEFAULT_WINE_VERSION))
                                bionicWineEntries.firstOrNull() ?: config.wineVersion
                            else config.wineVersion
                            val newCfg = KeyValueSet(config.graphicsDriverConfig).apply {
                                put("version", DefaultVersion.WRAPPER)
                                put("syncFrame", "0")
                                put("adrenotoolsTurnip", "1")
                                put("disablePresentWait", get("disablePresentWait").ifEmpty { "0" })
                                if (get("exposedDeviceExtensions").isEmpty()) put("exposedDeviceExtensions", "all")
                                if (get("maxDeviceMemory").isEmpty()) put("maxDeviceMemory", "4096")
                                if (get("presentMode").isEmpty()) put("presentMode", "mailbox")
                                if (get("resourceType").isEmpty()) put("resourceType", "auto")
                                if (get("bcnEmulation").isEmpty()) put("bcnEmulation", "auto")
                                if (get("bcnEmulationType").isEmpty()) put("bcnEmulationType", "compute")
                                if (get("bcnEmulationCache").isEmpty()) put("bcnEmulationCache", "1")
                            }
                            state.bionicDriverIndex.value = 0
                            state.wrapperVersionIndex.value = state.wrapperOptions.ids
                                .indexOfFirst { it.equals(DefaultVersion.WRAPPER, true) }
                                .let { if (it >= 0) it else 0 }
                            state.syncEveryFrameChecked.value = false
                            state.disablePresentWaitChecked.value = newCfg.get("disablePresentWait", "0") == "1"
                            state.bcnEmulationCacheEnabled.value = newCfg.get("bcnEmulationCache", "1") == "1"
                            state.adrenotoolsTurnipChecked.value = true
                            state.maxDeviceMemoryIndex.value =
                                listOf("0", "512", "1024", "2048", "4096").indexOf("4096").coerceAtLeast(0)

                            val currentConfig = KeyValueSet(config.dxwrapperConfig)
                            currentConfig.put("version", "async-1.10.3")
                            currentConfig.put("async", "1")
                            currentConfig.put("asyncCache", "0")
                            state.config.value = config.copy(dxwrapperConfig = currentConfig.toString())

                            state.config.value = config.copy(
                                containerVariant = newVariant,
                                wineVersion = newWine,
                                graphicsDriver = defaultBionicDriver,
                                graphicsDriverVersion = "",
                                graphicsDriverConfig = newCfg.toString(),
                                box64Version = "0.3.7",
                                dxwrapperConfig = currentConfig.toString(),
                            )
                        }
                    },
                )
                if (config.containerVariant.equals(Container.BIONIC, ignoreCase = true)) {
                    ContainerCardDivider()
                    val wineIndex = state.bionicWineOptions.ids.indexOfFirst { it == config.wineVersion }.coerceAtLeast(0)
                    ContainerGlassDropdown(
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.wine_version)) },
                        value = wineIndex,
                        items = state.bionicWineOptions.labels,
                        itemMuted = state.bionicWineOptions.muted,
                        onItemSelected = { idx ->
                            val selectedId = state.bionicWineOptions.ids.getOrNull(idx).orEmpty()
                            val isManifestNotInstalled = state.bionicWineOptions.muted.getOrNull(idx) == true
                            val manifestEntry = state.bionicWineManifestById[selectedId]
                            if (isManifestNotInstalled && manifestEntry != null) {
                                val expectedType = if (selectedId.startsWith("proton", true)) {
                                    ContentProfile.ContentType.CONTENT_TYPE_PROTON
                                } else {
                                    ContentProfile.ContentType.CONTENT_TYPE_WINE
                                }
                                state.launchManifestContentInstall(manifestEntry, expectedType) {
                                    state.config.value = config.copy(wineVersion = selectedId)
                                }
                                return@ContainerGlassDropdown
                            }
                            state.config.value = config.copy(wineVersion = selectedId.ifEmpty { state.bionicWineOptions.labels[idx] })
                        },
                    )
                }
            }
            ContainerCardDivider()
            ExecutablePathDropdown(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                value = config.executablePath,
                onValueChange = { state.config.value = config.copy(executablePath = it) },
                containerData = config,
            )
            NoExtractOutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                value = config.execArgs,
                onValueChange = { state.config.value = config.copy(execArgs = it) },
                label = { Text(text = stringResource(R.string.exec_arguments)) },
                placeholder = { Text(text = stringResource(R.string.exec_arguments_example)) },
                singleLine = true,
            )
            ContainerCardDivider()
            val displayNameForLanguage: (String) -> String = { code ->
                when (code) {
                    "schinese" -> "Simplified Chinese"
                    "tchinese" -> "Traditional Chinese"
                    "koreana" -> "Korean"
                    "latam" -> "Spanish (Latin America)"
                    "brazilian" -> "Portuguese (Brazil)"
                    else -> code.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
                }
            }
            ContainerGlassDropdown(
                enabled = true,
                value = state.languageIndex.value,
                items = state.languages.map(displayNameForLanguage),
                fallbackDisplay = displayNameForLanguage("english"),
                onItemSelected = { index ->
                    state.languageIndex.value = index
                    state.config.value = config.copy(language = state.languages[index])
                },
                title = { Text(text = stringResource(R.string.language)) },
                colors = settingsTileColors(),
            )
        }
    }

    ContainerGlassCard {
        SettingsGroup {
            SettingsMenuLink(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.screen_size)) },
                subtitle = {
                    Text(
                        text = config.screenSize,
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                },
                onClick = { showResolutionPicker = true },
            )
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.portrait_mode)) },
                subtitle = { Text(text = stringResource(R.string.portrait_mode_description)) },
                state = config.portraitMode,
                onCheckedChange = { state.config.value = config.copy(portraitMode = it) },
            )
            if (config.portraitMode && displayHasCutout(LocalView.current)) {
                ContainerCardDivider()
                SettingsSwitch(
                    colors = settingsTileColorsAlt(),
                    title = { Text(text = stringResource(R.string.portrait_below_cutout)) },
                    subtitle = { Text(text = stringResource(R.string.portrait_below_cutout_description)) },
                    state = config.portraitBelowCutout,
                    onCheckedChange = { state.config.value = config.copy(portraitBelowCutout = it) },
                )
            }
        }
    }

    ContainerGlassCard {
        SettingsGroup {
            ContainerGlassDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.audio_driver)) },
                value = state.audioDriverIndex.value,
                items = state.audioDrivers,
                onItemSelected = {
                    state.audioDriverIndex.value = it
                    state.config.value = config.copy(audioDriver = StringUtils.parseIdentifier(state.audioDrivers[it]))
                },
            )
            if (config.audioDriver == "pulseaudio") {
                ContainerCardDivider()
                SettingsSwitch(
                    colors = settingsTileColorsAlt(),
                    title = { Text(text = stringResource(R.string.pulseaudio_low_latency)) },
                    state = config.pulseaudioLowLatency,
                    onCheckedChange = { state.config.value = config.copy(pulseaudioLowLatency = it) },
                )
            }
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.microphone_input)) },
                subtitle = { Text(text = stringResource(R.string.microphone_input_description)) },
                state = config.micEnabled,
                onCheckedChange = { enabled ->
                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (enabled && !hasMicPermission) {
                        // Ask only now that the user explicitly wants mic input; the launcher
                        // callback flips the switch on if they grant.
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        state.config.value = config.copy(micEnabled = enabled)
                    }
                },
            )
        }
    }

    ContainerGlassCard {
        SettingsGroup {
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.force_dlc)) },
                subtitle = { Text(text = stringResource(R.string.force_dlc_description)) },
                state = config.forceDlc,
                onCheckedChange = { state.config.value = config.copy(forceDlc = it) },
            )
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.use_legacy_drm)) },
                state = config.useLegacyDRM,
                onCheckedChange = { state.config.value = config.copy(useLegacyDRM = it) },
            )
            if (!config.useLegacyDRM) {
                ContainerCardDivider()
                SettingsSwitch(
                    colors = settingsTileColorsAlt(),
                    title = { Text(text = stringResource(R.string.unpack_files)) },
                    subtitle = { Text(text = stringResource(R.string.unpack_files_description)) },
                    state = config.unpackFiles,
                    onCheckedChange = { state.config.value = config.copy(unpackFiles = it) },
                )
            }
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.load_mods)) },
                subtitle = { Text(text = AnnotatedString.fromHtml(stringResource(R.string.load_mods_description))) },
                state = config.loadMods,
                onCheckedChange = { state.config.value = config.copy(loadMods = it) },
            )
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.steam_offline_mode)) },
                subtitle = { Text(text = stringResource(R.string.steam_offline_mode_description)) },
                state = config.steamOfflineMode,
                onCheckedChange = { state.config.value = config.copy(steamOfflineMode = it) },
            )
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.epic_offline_mode)) },
                subtitle = { Text(text = stringResource(R.string.epic_offline_mode_description)) },
                state = config.epicOfflineMode,
                onCheckedChange = { state.config.value = config.copy(epicOfflineMode = it) },
            )
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.disable_epic_overlay_title)) },
                subtitle = { Text(text = stringResource(R.string.disable_epic_overlay_subtitle)) },
                state = config.disableEpicOverlay,
                onCheckedChange = { state.config.value = config.copy(disableEpicOverlay = it) },
            )
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.launch_steam_client_beta)) },
                subtitle = { Text(text = stringResource(R.string.launch_steam_client_description)) },
                state = config.launchRealSteam,
                onCheckedChange = {
                    state.config.value = if (it) {
                        config.copy(launchRealSteam = true, launchBionicSteam = false)
                    } else {
                        config.copy(launchRealSteam = false)
                    }
                },
            )
            if (config.launchRealSteam) {
                ContainerCardDivider()
                val steamTypeItems = listOf("Headless", "Normal", "Light", "Ultra Light")
                val currentSteamTypeIndex = when (config.steamType.lowercase()) {
                    Container.STEAM_TYPE_HEADLESS -> 0
                    Container.STEAM_TYPE_LIGHT -> 2
                    Container.STEAM_TYPE_ULTRALIGHT -> 3
                    else -> 1
                }
                ContainerGlassDropdown(
                    colors = settingsTileColors(),
                    title = { Text(text = stringResource(R.string.steam_type)) },
                    value = currentSteamTypeIndex,
                    items = steamTypeItems,
                    onItemSelected = {
                        val type = when (it) {
                            0 -> Container.STEAM_TYPE_HEADLESS
                            2 -> Container.STEAM_TYPE_LIGHT
                            3 -> Container.STEAM_TYPE_ULTRALIGHT
                            else -> Container.STEAM_TYPE_NORMAL
                        }
                        state.config.value = config.copy(steamType = type)
                    },
                )
            }
            if (config.containerVariant.equals(Container.BIONIC, ignoreCase = true)) {
                ContainerCardDivider()
                SettingsSwitch(
                    colors = settingsTileColorsAlt(),
                    title = { Text(text = stringResource(R.string.launch_bionic_steam)) },
                    subtitle = { Text(text = stringResource(R.string.launch_bionic_steam_description)) },
                    state = config.launchBionicSteam,
                    onCheckedChange = {
                        state.config.value = if (it) {
                            config.copy(launchBionicSteam = true, launchRealSteam = false)
                        } else {
                            config.copy(launchBionicSteam = false)
                        }
                    },
                )
                ContainerCardDivider()
                SettingsSwitch(
                    colors = settingsTileColorsAlt(),
                    title = { Text(text = stringResource(R.string.disable_libredirect_title)) },
                    subtitle = { Text(text = stringResource(R.string.disable_libredirect_subtitle)) },
                    state = config.disableLibredirect,
                    onCheckedChange = {
                        state.config.value = if (it) {
                            config.copy(disableLibredirect = true, fasterExternalLoading = false)
                        } else {
                            config.copy(disableLibredirect = false)
                        }
                    },
                )
            }
            ContainerCardDivider()
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                enabled = !config.disableLibredirect,
                title = { Text(text = stringResource(R.string.faster_external_loading_title)) },
                subtitle = { Text(text = stringResource(R.string.faster_external_loading_subtitle)) },
                state = config.fasterExternalLoading,
                onCheckedChange = { state.config.value = config.copy(fasterExternalLoading = it) },
            )
            ContainerCardDivider()
            ContainerGlassDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.suspend_behavior)) },
                value = suspendBehaviorIndex,
                items = suspendBehaviorEntries,
                onItemSelected = { index ->
                    val policy = when (index) {
                        0 -> Container.SUSPEND_POLICY_MANUAL
                        1 -> Container.SUSPEND_POLICY_AUTO
                        2 -> Container.SUSPEND_POLICY_NEVER
                        else -> Container.SUSPEND_POLICY_MANUAL
                    }
                    state.config.value = config.copy(suspendPolicy = policy)
                },
            )
        }
    }
}

private fun displayHasCutout(view: View): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return view.display?.cutout != null
}
