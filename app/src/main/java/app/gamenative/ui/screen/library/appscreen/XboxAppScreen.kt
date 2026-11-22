package app.gamenative.ui.screen.library.appscreen

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.DownloadInfo
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.data.StoreGameDetails
import app.gamenative.data.XboxGame
import app.gamenative.data.withoutTitleOnlyDescription
import app.gamenative.events.AndroidEvent
import app.gamenative.service.DownloadService
import app.gamenative.service.xbox.XboxConstants
import app.gamenative.service.xbox.XboxService
import app.gamenative.service.xbox.cloud.XboxCloudGames
import app.gamenative.ui.component.dialog.AmazonInstallDialog
import app.gamenative.ui.component.dialog.LoadingDialog
import app.gamenative.ui.component.dialog.MessageDialog
import app.gamenative.ui.component.dialog.state.MessageDialogState
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.enums.DialogType
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.StorageUtils
import com.winlator.container.ContainerData
import com.winlator.core.StringUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/** Xbox / PC Game Pass & Xbox Cloud Gaming [BaseAppScreen] implementation. */
class XboxAppScreen : BaseAppScreen() {

    companion object {
        private const val TAG = "XboxAppScreen"

        private val uninstallDialogAppIds = mutableStateListOf<String>()

        data class XboxInstallDialogData(
            val downloadSize: String,
            val installSize: String,
            val availableSpace: String,
            val installEnabled: Boolean,
        )

        private val installDialogDataMap = mutableStateMapOf<String, XboxInstallDialogData>()

        fun showXboxInstallDialog(appId: String, data: XboxInstallDialogData) {
            installDialogDataMap[appId] = data
        }

        fun hideXboxInstallDialog(appId: String) {
            installDialogDataMap.remove(appId)
        }

        fun getXboxInstallDialogData(appId: String): XboxInstallDialogData? =
            installDialogDataMap[appId]

        fun showUninstallDialog(appId: String) {
            if (!uninstallDialogAppIds.contains(appId)) {
                uninstallDialogAppIds.add(appId)
            }
        }

        fun hideUninstallDialog(appId: String) {
            uninstallDialogAppIds.remove(appId)
        }

        fun shouldShowUninstallDialog(appId: String): Boolean =
            uninstallDialogAppIds.contains(appId)

        var showDeletingDialog by mutableStateOf(false)

        fun productIdOf(libraryItem: LibraryItem): String =
            XboxService.getProductIdByAppId(libraryItem.gameId) ?: ""
    }

    @Composable
    override fun getGameDisplayInfo(
        context: Context,
        libraryItem: LibraryItem,
    ): GameDisplayInfo {
        val productId = productIdOf(libraryItem)
        var game by remember(productId) { mutableStateOf<XboxGame?>(null) }
        var refreshKey by remember(productId) { mutableStateOf(0) }

        DisposableEffect(productId) {
            val listener: (AndroidEvent.LibraryInstallStatusChanged) -> Unit = { event ->
                if (event.appId == libraryItem.gameId) {
                    refreshKey++
                }
            }
            PluviaApp.events.on<AndroidEvent.LibraryInstallStatusChanged, Unit>(listener)
            onDispose {
                PluviaApp.events.off<AndroidEvent.LibraryInstallStatusChanged, Unit>(listener)
            }
        }

        LaunchedEffect(productId, refreshKey) {
            game = XboxService.getXboxGameOf(productId, context)
        }

        val g = game
        val heroImageUrl = g?.heroUrl?.takeIf { it.isNotEmpty() }
            ?: g?.artUrl?.takeIf { it.isNotEmpty() }
            ?: libraryItem.iconHash.takeIf { it.isNotEmpty() }

        val iconUrl = g?.artUrl?.takeIf { it.isNotEmpty() }
            ?: libraryItem.iconHash.takeIf { it.isNotEmpty() }

        val developer = g?.developer?.takeIf { it.isNotEmpty() }
            ?: g?.publisher?.takeIf { it.isNotEmpty() }
            ?: ""

        val sizeFromStore = if ((g?.downloadSize ?: 0L) > 0L) {
            StringUtils.formatBytes(g!!.downloadSize)
        } else {
            null
        }

        val description = runCatching {
            JSONObject(g?.productJson.orEmpty()).optString("description")
        }.getOrDefault("")

        return GameDisplayInfo(
            name = g?.title ?: libraryItem.name,
            iconUrl = iconUrl,
            heroImageUrl = heroImageUrl,
            gameId = libraryItem.gameId,
            appId = libraryItem.appId,
            releaseDate = 0L,
            developer = developer,
            installLocation = if (g?.isInstalled == true && g.installPath.isNotEmpty()) {
                g.installPath
            } else {
                null
            },
            sizeOnDisk = if ((g?.installSize ?: 0L) > 0L) StringUtils.formatBytes(g!!.installSize) else null,
            sizeFromStore = sizeFromStore,
            lastPlayedText = null,
            playtimeText = null,
            storeDetails = StoreGameDetails(
                description = description,
                screenshots = listOfNotNull(heroImageUrl, iconUrl).distinct(),
            ).withoutTitleOnlyDescription(g?.title ?: libraryItem.name),
        )
    }

    override fun isInstalled(context: Context, libraryItem: LibraryItem): Boolean =
        XboxService.isGameInstalledByAppId(context, libraryItem.gameId)

    override fun isValidToDownload(context: Context, libraryItem: LibraryItem): Boolean =
        !isInstalled(context, libraryItem) &&
            XboxService.getDownloadInfoByAppId(libraryItem.gameId, context) == null

    override fun isDownloading(context: Context, libraryItem: LibraryItem): Boolean =
        XboxService.getDownloadInfoByAppId(libraryItem.gameId, context) != null

    override fun getDownloadProgress(context: Context, libraryItem: LibraryItem): Float =
        XboxService.getDownloadInfoByAppId(libraryItem.gameId, context)?.getProgress() ?: 0f

    override fun hasPartialDownload(context: Context, libraryItem: LibraryItem): Boolean =
        XboxService.hasPartialDownloadByAppId(context, libraryItem.gameId)

    override fun onDownloadInstallClick(
        context: Context,
        libraryItem: LibraryItem,
        onClickPlay: (Boolean) -> Unit,
    ) {
        val productId = productIdOf(libraryItem)
        val installed = isInstalled(context, libraryItem)
        val downloading = isDownloading(context, libraryItem)
        val hasPartial = hasPartialDownload(context, libraryItem)

        if (downloading) return

        if (hasPartial) {
            performDownload(context, libraryItem)
            return
        }

        if (installed) {
            onClickPlay(false)
            return
        }

        val game = XboxService.getXboxGameOf(productId, context)
        if (game != null && !game.isPcSupported && game.isCloudSupported) {
            // Cloud-only title: stream directly via xCloud
            XboxCloudGames.stream(context, game.cloudProductId.ifEmpty { productId }, game.title)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val g = XboxService.getXboxGameOf(productId, context)
                val downloadBytes = g?.downloadSize ?: 0L
                val installBytes = g?.installSize ?: downloadBytes
                val downloadSize = if (downloadBytes > 0L) StorageUtils.formatBinarySize(downloadBytes) else "Unknown"
                val installSize = if (installBytes > 0L) StorageUtils.formatBinarySize(installBytes) else "Unknown"
                val availableBytes = StorageUtils.getAvailableSpace(XboxConstants.defaultXboxGamesPath(context))
                val availableSpace = StorageUtils.formatBinarySize(availableBytes)

                withContext(Dispatchers.Main) {
                    showXboxInstallDialog(
                        libraryItem.appId,
                        XboxInstallDialogData(
                            downloadSize = downloadSize,
                            installSize = installSize,
                            availableSpace = availableSpace,
                            installEnabled = availableBytes >= installBytes || installBytes <= 0L,
                        ),
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to show Xbox install confirmation for: ${libraryItem.appId}")
            }
        }
    }

    private fun performDownload(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        CoroutineScope(Dispatchers.IO).launch {
            val game = XboxService.getXboxGameOf(productId, context) ?: run {
                SnackbarManager.show("Xbox game not found — try syncing library")
                return@launch
            }
            val installPath = XboxConstants.getGameInstallPath(context, game.title, productId)
            val result = XboxService.downloadGame(context, productId, installPath)
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                SnackbarManager.show("Failed to start Xbox download: $msg")
            }
        }
    }

    override fun onPauseResumeClick(context: Context, libraryItem: LibraryItem) {
        val appId = libraryItem.gameId
        if (XboxService.getDownloadInfoByAppId(appId, context) != null) {
            XboxService.cancelDownloadByAppId(appId, context)
        } else {
            performDownload(context, libraryItem)
        }
    }

    override fun onDeleteDownloadClick(context: Context, libraryItem: LibraryItem) {
        if (isDownloading(context, libraryItem) || hasPartialDownload(context, libraryItem)) {
            showInstallDialog(
                libraryItem.appId,
                MessageDialogState(
                    visible = true,
                    type = DialogType.CANCEL_APP_DOWNLOAD,
                    title = context.getString(R.string.cancel_download_prompt_title),
                    message = context.getString(R.string.epic_delete_download_message),
                    confirmBtnText = context.getString(R.string.yes),
                    dismissBtnText = context.getString(R.string.no),
                ),
            )
        } else if (isInstalled(context, libraryItem)) {
            showUninstallDialog(libraryItem.appId)
        }
    }

    private fun performUninstall(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        showDeletingDialog = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gameRootDir = getInstallPath(context, libraryItem)?.let(::File)
                val result = XboxService.deleteGame(context, productId)
                DownloadService.invalidateCache()
                if (result.isSuccess) {
                    cleanupNexusModsForApp(context, libraryItem, gameRootDir)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    showDeletingDialog = false
                }
            }
        }
    }

    override fun onUpdateClick(context: Context, libraryItem: LibraryItem) {
        performDownload(context, libraryItem)
    }

    override suspend fun isUpdatePendingSuspend(context: Context, libraryItem: LibraryItem): Boolean = false

    override fun getInstallPath(context: Context, libraryItem: LibraryItem): String? =
        XboxService.getInstallPathByAppId(libraryItem.gameId, context)

    override fun observeGameState(
        context: Context,
        libraryItem: LibraryItem,
        onStateChanged: () -> Unit,
        onProgressChanged: (Float) -> Unit,
        onHasPartialDownloadChanged: ((Boolean) -> Unit)?,
    ): (() -> Unit)? {
        val gameId = libraryItem.gameId
        val productId = productIdOf(libraryItem)
        val disposables = mutableListOf<() -> Unit>()
        var currentProgressListener: ((Float) -> Unit)? = null
        var currentDownloadInfo: DownloadInfo? = null

        val existingDownloadInfo = XboxService.getDownloadInfo(productId)
        if (existingDownloadInfo != null && (existingDownloadInfo.getProgress() ?: 0f) < 1f) {
            val progressListener: (Float) -> Unit = { progress -> onProgressChanged(progress) }
            existingDownloadInfo.addProgressListener(progressListener)
            currentDownloadInfo = existingDownloadInfo
            currentProgressListener = progressListener
            disposables += {
                currentProgressListener?.let { listener ->
                    currentDownloadInfo?.removeProgressListener(listener)
                    currentProgressListener = null
                    currentDownloadInfo = null
                }
            }
            existingDownloadInfo.getProgress()?.let { onProgressChanged(it) }
        }

        val downloadStatusListener: (AndroidEvent.DownloadStatusChanged) -> Unit = { event ->
            if (event.appId == gameId) {
                if (event.isDownloading) {
                    val downloadInfo = XboxService.getDownloadInfo(productId)
                    if (downloadInfo != null) {
                        currentProgressListener?.let { listener ->
                            currentDownloadInfo?.removeProgressListener(listener)
                        }
                        val progressListener: (Float) -> Unit = { progress -> onProgressChanged(progress) }
                        downloadInfo.addProgressListener(progressListener)
                        currentDownloadInfo = downloadInfo
                        currentProgressListener = progressListener
                        disposables += {
                            currentProgressListener?.let { listener ->
                                currentDownloadInfo?.removeProgressListener(listener)
                                currentProgressListener = null
                                currentDownloadInfo = null
                            }
                        }
                    }
                } else {
                    currentProgressListener?.let { listener ->
                        currentDownloadInfo?.removeProgressListener(listener)
                        currentProgressListener = null
                        currentDownloadInfo = null
                    }
                    val nowInstalled = XboxService.isGameInstalledByAppId(context, gameId)
                    onHasPartialDownloadChanged?.invoke(!nowInstalled && hasPartialDownload(context, libraryItem))
                }
                onStateChanged()
            }
        }
        PluviaApp.events.on<AndroidEvent.DownloadStatusChanged, Unit>(downloadStatusListener)
        disposables += { PluviaApp.events.off<AndroidEvent.DownloadStatusChanged, Unit>(downloadStatusListener) }

        val installListener: (AndroidEvent.LibraryInstallStatusChanged) -> Unit = { event ->
            if (event.appId == gameId) {
                onStateChanged()
            }
        }
        PluviaApp.events.on<AndroidEvent.LibraryInstallStatusChanged, Unit>(installListener)
        disposables += { PluviaApp.events.off<AndroidEvent.LibraryInstallStatusChanged, Unit>(installListener) }

        return { disposables.forEach { it() } }
    }

    override fun getExportFileExtension(): String = ".xbox"

    @Composable
    override fun getSourceSpecificMenuOptions(
        context: Context,
        libraryItem: LibraryItem,
        onEditContainer: () -> Unit,
        onBack: () -> Unit,
        onClickPlay: (Boolean) -> Unit,
        isInstalled: Boolean,
    ): List<AppMenuOption> {
        val options = mutableListOf<AppMenuOption>()
        val productId = productIdOf(libraryItem)
        val game = remember(productId) { XboxService.getXboxGameOf(productId, context) }
        if (game?.isCloudSupported == true || XboxCloudGames.streams(context, productId)) {
            options.add(
                AppMenuOption(
                    optionType = AppOptionMenuType.CreateShortcut,
                    onClick = {
                        XboxCloudGames.stream(
                            context,
                            game?.cloudProductId?.ifEmpty { productId } ?: productId,
                            game?.title ?: libraryItem.name,
                        )
                    },
                ),
            )
        }
        return options
    }

    @Composable
    override fun getResetContainerOption(
        context: Context,
        libraryItem: LibraryItem,
    ): AppMenuOption? {
        var showDialog by remember { mutableStateOf(false) }

        if (showDialog) {
            ResetConfirmDialog(
                onConfirm = {
                    showDialog = false
                    resetContainerToDefaults(context, libraryItem)
                },
                onDismiss = { showDialog = false },
            )
        }

        return AppMenuOption(
            optionType = AppOptionMenuType.ResetToDefaults,
            onClick = { showDialog = true },
        )
    }

    @Composable
    override fun AdditionalDialogs(
        libraryItem: LibraryItem,
        onDismiss: () -> Unit,
        onEditContainer: () -> Unit,
        onBack: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val appId = libraryItem.appId
        val productId = productIdOf(libraryItem)

        var installDialogState by remember(appId) {
            mutableStateOf(BaseAppScreen.getInstallDialogState(appId) ?: MessageDialogState(false))
        }
        LaunchedEffect(appId) {
            snapshotFlow { BaseAppScreen.getInstallDialogState(appId) }
                .collect { state ->
                    installDialogState = state ?: MessageDialogState(false)
                }
        }

        if (installDialogState.visible) {
            val onConfirmClick: (() -> Unit)? = when (installDialogState.type) {
                DialogType.CANCEL_APP_DOWNLOAD -> {
                    {
                        BaseAppScreen.hideInstallDialog(appId)
                        showDeletingDialog = true
                        val downloadInfo = XboxService.getDownloadInfo(productId)
                        downloadInfo?.cancel()
                        XboxService.cancelDownload(productId)
                        scope.launch {
                            try {
                                downloadInfo?.awaitCompletion()
                                XboxService.deleteGame(context, productId)
                                DownloadService.invalidateCache()
                                withContext(Dispatchers.Main) {
                                    val gameId = libraryItem.gameId
                                    PluviaApp.events.emitJava(AndroidEvent.DownloadStatusChanged(gameId, false))
                                    PluviaApp.events.emitJava(AndroidEvent.LibraryInstallStatusChanged(gameId, GameSource.XBOX))
                                }
                            } finally {
                                withContext(NonCancellable + Dispatchers.Main) {
                                    showDeletingDialog = false
                                }
                            }
                        }
                    }
                }
                else -> null
            }
            MessageDialog(
                visible = installDialogState.visible,
                onDismissRequest = { BaseAppScreen.hideInstallDialog(appId) },
                onConfirmClick = onConfirmClick,
                onDismissClick = { BaseAppScreen.hideInstallDialog(appId) },
                confirmBtnText = installDialogState.confirmBtnText,
                dismissBtnText = installDialogState.dismissBtnText,
                title = installDialogState.title,
                message = installDialogState.message,
            )
        }

        var xboxInstallData by remember(appId) {
            mutableStateOf(getXboxInstallDialogData(appId))
        }
        LaunchedEffect(appId) {
            snapshotFlow { getXboxInstallDialogData(appId) }
                .collect { data -> xboxInstallData = data }
        }

        val currentInstallData = xboxInstallData
        if (currentInstallData != null) {
            val displayInfo = getGameDisplayInfo(context, libraryItem)
            AmazonInstallDialog(
                visible = true,
                displayInfo = displayInfo,
                downloadSize = currentInstallData.downloadSize,
                installSize = currentInstallData.installSize,
                availableSpace = currentInstallData.availableSpace,
                installEnabled = currentInstallData.installEnabled,
                onInstall = {
                    hideXboxInstallDialog(appId)
                    performDownload(context, libraryItem)
                },
                onDismiss = {
                    hideXboxInstallDialog(appId)
                },
            )
        }

        if (showDeletingDialog) {
            LoadingDialog(
                visible = true,
                progress = -1f,
                message = stringResource(R.string.deleting),
            )
        }

        var showUninstallDialog by remember { mutableStateOf(shouldShowUninstallDialog(libraryItem.appId)) }
        LaunchedEffect(libraryItem.appId) {
            snapshotFlow { shouldShowUninstallDialog(libraryItem.appId) }
                .collect { shouldShow ->
                    showUninstallDialog = shouldShow
                }
        }

        if (showUninstallDialog) {
            AlertDialog(
                onDismissRequest = {
                    hideUninstallDialog(libraryItem.appId)
                },
                title = { Text(stringResource(R.string.amazon_uninstall_game_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.amazon_uninstall_confirmation_message,
                            libraryItem.name,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            hideUninstallDialog(libraryItem.appId)
                            performUninstall(context, libraryItem)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.uninstall),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            hideUninstallDialog(libraryItem.appId)
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    override fun loadContainerData(context: Context, libraryItem: LibraryItem): ContainerData {
        val container = ContainerUtils.getOrCreateContainer(context, libraryItem.appId)
        return ContainerUtils.toContainerData(container)
    }

    override fun saveContainerConfig(
        context: Context,
        libraryItem: LibraryItem,
        config: ContainerData,
    ) {
        ContainerUtils.applyToContainer(context, libraryItem.appId, config)
    }

    override fun supportsContainerConfig(): Boolean = true
}
