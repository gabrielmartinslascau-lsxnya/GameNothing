package app.gamenative.service.xbox

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.DownloadInfo
import app.gamenative.data.GameSource
import app.gamenative.data.XboxGame
import app.gamenative.db.dao.XboxGameDao
import app.gamenative.enums.Marker
import app.gamenative.events.AndroidEvent
import app.gamenative.service.DownloadService
import app.gamenative.service.NotificationHelper
import app.gamenative.service.download.GameDownloadService
import app.gamenative.service.xbox.cloud.XboxCloudActivity
import app.gamenative.service.xbox.cloud.XboxCloudGames
import app.gamenative.service.xbox.cloud.XboxCloudSession
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.ExecutableSelectionUtils
import app.gamenative.utils.LocaleHelper
import app.gamenative.utils.MarkerUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Xbox / PC Game Pass foreground & background service. */
@AndroidEntryPoint
class XboxService : Service() {

    override fun attachBaseContext(newBase: Context) {
        PrefManager.init(newBase)
        val languageCode = PrefManager.appLanguage
        val context = LocaleHelper.applyLanguage(newBase, languageCode)
        super.attachBaseContext(context)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface XboxDaoEntryPoint {
        fun xboxGameDao(): XboxGameDao
    }

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var xboxGameDao: XboxGameDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()
    private val activeDownloadPaths = ConcurrentHashMap<String, String>()
    private val activeChildren = ConcurrentHashMap<String, Xodus.Child>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        when (intent?.action) {
            ACTION_SYNC_LIBRARY -> syncLibrary(applicationContext, force = false)
            ACTION_MANUAL_SYNC -> syncLibrary(applicationContext, force = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "XboxService"
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.XBOX_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.XBOX_MANUAL_SYNC"
        private const val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L

        @Volatile
        private var instance: XboxService? = null

        @Volatile
        private var syncInProgress: Boolean = false

        @Volatile
        private var lastSyncTimestamp: Long = 0L

        private var backgroundSyncJob: Job? = null

        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val isRunning: Boolean
            get() = instance != null

        fun isSyncInProgress(): Boolean = syncInProgress

        fun hasActiveOperations(): Boolean =
            syncInProgress || backgroundSyncJob?.isActive == true || hasActiveDownload()

        private fun dao(context: Context = PluviaApp.instance): XboxGameDao {
            instance?.let { if (it::xboxGameDao.isInitialized) return it.xboxGameDao }
            return EntryPointAccessors.fromApplication(
                context.applicationContext,
                XboxDaoEntryPoint::class.java,
            ).xboxGameDao()
        }

        fun start(context: Context) {
            val intent = Intent(context, XboxService::class.java)
            val now = System.currentTimeMillis()
            if (now - lastSyncTimestamp >= SYNC_THROTTLE_MILLIS) {
                intent.action = ACTION_SYNC_LIBRARY
            }
            runCatching { context.startService(intent) }
        }

        fun stop() {
            instance?.stopSelf()
        }

        fun getInstance(): XboxService? = instance

        fun hasStoredCredentials(context: Context = PluviaApp.instance): Boolean =
            StoreAccounts.signedInAs(context, XboxStoreId.XBOX) != null

        fun getDisplayName(context: Context = PluviaApp.instance): String? =
            StoreAccounts.displayName(context, XboxStoreId.XBOX)

        fun triggerLibrarySync(context: Context) {
            syncLibrary(context, force = true)
        }

        fun syncLibrary(context: Context, force: Boolean = false) {
            val app = context.applicationContext
            if (syncInProgress) return
            val account = StoreAccounts.signedInAs(app, XboxStoreId.XBOX) ?: return
            syncInProgress = true
            backgroundSyncJob = ioScope.launch {
                try {
                    val market = Xodus.market()
                    val cached = XboxLibraryCache.read(app, account, market)
                    if (cached != null) {
                        upsertCatalogItemsToDb(app, cached.games)
                    }
                    if (cached != null && !cached.needsRefresh(force)) {
                        XboxGamertag.ensure(app)
                        lastSyncTimestamp = System.currentTimeMillis()
                        return@launch
                    }

                    val response = Xodus.run(app, listOf("library", "--json", "--market", market))
                    val licenses = XboxCatalog.access(response, account, market) {
                        Timber.tag(TAG).i("Xbox: %s", it)
                    }
                    val products = XboxCatalog.rawProducts(licenses.keys.toList(), market)
                    val cloud = runCatching { XboxCatalog.cloudCatalog(market) }.getOrNull()
                    val games = XboxCatalog.mergeEditions(
                        products.mapNotNull { XboxCatalog.product(it, licenses, cloud) },
                    ).sortedBy { it.title.lowercase() }

                    runCatching { XboxLibraryCache.write(app, account, market, games) }
                    upsertCatalogItemsToDb(app, games)

                    // Also sync cloud-enabled titles into XboxCloudGames
                    for (item in games) {
                        if (item.xboxCloud && !XboxCloudGames.isAdded(item.id)) {
                            XboxCloudGames.add(app, item)
                        }
                    }

                    XboxGamertag.ensure(app)
                    lastSyncTimestamp = System.currentTimeMillis()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Xbox library sync failed")
                } finally {
                    syncInProgress = false
                }
            }
        }

        private suspend fun upsertCatalogItemsToDb(context: Context, items: List<XboxCatalogItem>) {
            val entities = items.map { item ->
                val productJson = JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("description", item.description)
                    .put("developer", item.developer)
                    .put("publisher", item.publisher)
                    .put("storeUrl", item.storeUrl)
                    .put("includedWith", item.includedWith)
                    .put("market", item.extra["market"] ?: Xodus.market())
                    .toString()

                XboxGame(
                    productId = item.id,
                    title = item.title,
                    artUrl = item.tallImageUrl ?: item.imageUrl.orEmpty(),
                    heroUrl = item.imageUrl ?: item.tallImageUrl.orEmpty(),
                    developer = item.developer,
                    publisher = item.publisher,
                    downloadSize = item.sizeBytes,
                    accessType = item.extra["access"] ?: if (item.subscription) "gamepass" else "owned",
                    isPcSupported = item.xboxPc,
                    isCloudSupported = item.xboxCloud,
                    cloudProductId = item.xboxCloudId,
                    productJson = productJson,
                )
            }
            dao(context).upsertPreservingInstallStatus(entities)
        }

        suspend fun logout(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                XboxCredentials.invalidate(context) {
                    XboxLibraryCache.clear(context)
                    XboxCloudSession.clear()
                    XboxCloudActivity.clearWebSignIn(context)
                    XboxRuntime.stop()
                }
                dao(context).deleteAllNonInstalledGames()
                stop()
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during Xbox logout")
                Result.failure(e)
            }
        }

        fun getXboxGameOf(productId: String, context: Context = PluviaApp.instance): XboxGame? =
            runBlocking(Dispatchers.IO) {
                dao(context).getByProductId(productId)
            }

        fun getXboxGameByAppId(appId: Int, context: Context = PluviaApp.instance): XboxGame? =
            runBlocking(Dispatchers.IO) {
                dao(context).getByAppId(appId)
            }

        fun getProductIdByAppId(appId: Int, context: Context = PluviaApp.instance): String? =
            getXboxGameByAppId(appId, context)?.productId

        fun isGameInstalled(context: Context, productId: String): Boolean {
            val game = getXboxGameOf(productId, context) ?: return false
            if (game.isInstalled && game.installPath.isNotEmpty()) {
                return File(game.installPath).isDirectory &&
                    (MarkerUtils.hasMarker(game.installPath, Marker.DOWNLOAD_COMPLETE_MARKER) ||
                        File(game.installPath, "MicrosoftGame.config").isFile)
            }
            val expectedPath = game.installPath.takeIf { it.isNotEmpty() }
                ?: game.title.takeIf { it.isNotEmpty() }?.let { XboxConstants.getGameInstallPath(context, it, productId) }
                ?: return false
            val isComplete = MarkerUtils.hasMarker(expectedPath, Marker.DOWNLOAD_COMPLETE_MARKER) ||
                File(expectedPath, "MicrosoftGame.config").isFile
            val isInProgress = MarkerUtils.hasMarker(expectedPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            if (isComplete && !isInProgress) {
                val exe = runCatching { XboxManifest.executable(File(expectedPath)) }.getOrDefault("")
                runBlocking(Dispatchers.IO) {
                    dao(context).markAsInstalled(productId, expectedPath, exe, game.installSize, game.versionId, game.contentId)
                }
                return true
            }
            return false
        }

        fun isGameInstalledByAppId(context: Context, appId: Int): Boolean {
            val game = getXboxGameByAppId(appId, context) ?: return false
            return isGameInstalled(context, game.productId)
        }

        fun getInstallPath(productId: String, context: Context = PluviaApp.instance): String? {
            val game = getXboxGameOf(productId, context) ?: return null
            return if (game.isInstalled && game.installPath.isNotEmpty()) game.installPath else null
        }

        fun getInstallPathByAppId(appId: Int, context: Context = PluviaApp.instance): String? {
            val game = getXboxGameByAppId(appId, context) ?: return null
            return if (game.isInstalled && game.installPath.isNotEmpty()) game.installPath else null
        }

        fun getExpectedInstallPathByAppId(context: Context, appId: Int): String? {
            val game = getXboxGameByAppId(appId, context) ?: return null
            instance?.activeDownloadPaths?.get(game.productId)?.let { return it }
            val title = game.title.ifBlank { return null }
            return XboxConstants.getGameInstallPath(context, title, game.productId)
        }

        fun updateInstallPath(appId: Int, path: String, context: Context = PluviaApp.instance) {
            runBlocking(Dispatchers.IO) {
                val game = dao(context).getByAppId(appId) ?: return@runBlocking
                if (game.isInstalled && game.installPath != path) {
                    dao(context).markAsInstalled(
                        game.productId,
                        path,
                        game.executablePath,
                        game.installSize,
                        game.versionId,
                        game.contentId,
                    )
                }
            }
        }

        fun getLaunchExecutable(containerId: String, context: Context = PluviaApp.instance): String {
            val appId = runCatching { ContainerUtils.extractGameIdFromContainerId(containerId) }.getOrElse { return "" }
            if (appId <= 0) return ""
            val game = getXboxGameByAppId(appId, context) ?: return ""
            val installPath = game.installPath.takeIf { it.isNotEmpty() } ?: return ""
            val installDir = File(installPath)
            if (!installDir.isDirectory) return ""

            if (game.executablePath.isNotEmpty()) {
                val f = File(installDir, game.executablePath)
                if (f.isFile) return f.path
            }
            val manifestExe = runCatching { XboxManifest.executable(installDir) }.getOrNull()
            if (!manifestExe.isNullOrEmpty()) {
                val f = File(installDir, manifestExe)
                if (f.isFile) return f.path
            }
            val exeFile = ExecutableSelectionUtils.choosePrimaryExeFromDisk(
                installDir = installDir,
                gameName = installDir.name,
            ) ?: return ""
            return exeFile.path
        }

        fun getDownloadInfo(productId: String): DownloadInfo? =
            instance?.activeDownloads?.get(productId)

        fun getDownloadInfoByAppId(appId: Int, context: Context = PluviaApp.instance): DownloadInfo? {
            val productId = getProductIdByAppId(appId, context) ?: return null
            return getDownloadInfo(productId)
        }

        fun getActiveDownloads(): Map<String, DownloadInfo> =
            instance?.activeDownloads?.let { HashMap(it) } ?: emptyMap()

        fun hasActiveDownload(): Boolean =
            instance?.activeDownloads?.isNotEmpty() == true

        fun hasPartialDownloadByAppId(context: Context, appId: Int): Boolean {
            if (getDownloadInfoByAppId(appId, context) != null) return true
            if (isGameInstalledByAppId(context, appId)) return false
            val expectedPath = getExpectedInstallPathByAppId(context, appId) ?: return false
            val dir = File(expectedPath)
            if (!dir.exists()) return false
            if (MarkerUtils.hasMarker(expectedPath, Marker.DOWNLOAD_COMPLETE_MARKER)) return false
            if (MarkerUtils.hasMarker(expectedPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)) return true
            val children = dir.listFiles() ?: return false
            return children.isNotEmpty()
        }

        suspend fun getPartialDownloads(context: Context): List<String> {
            val roots = buildList {
                add(XboxConstants.internalXboxGamesPath(context))
                if (PrefManager.externalStoragePath.isNotBlank()) {
                    add(XboxConstants.externalXboxGamesPath(context))
                }
            }.distinct()
            val partialPaths = roots.asSequence()
                .flatMap { root -> MarkerUtils.findResumablePartialInstalls(root).asSequence() }
                .toSet()
            if (partialPaths.isEmpty()) return emptyList()
            val active = instance?.activeDownloads?.keys.orEmpty()
            return dao(context).getNonInstalledGames()
                .asSequence()
                .filter { it.productId !in active }
                .filter { game ->
                    val expected = buildList {
                        game.installPath.takeIf { it.isNotBlank() }?.let(::add)
                        add(XboxConstants.getGameInstallPath(context, game.title, game.productId))
                    }
                    expected.any(partialPaths::contains)
                }
                .map { it.productId }
                .toList()
        }

        fun cancelDownload(productId: String): Boolean {
            val svc = instance ?: return false
            val info = svc.activeDownloads[productId] ?: return false
            info.cancel()
            val child = svc.activeChildren.remove(productId)
            if (child != null) {
                Thread({
                    runCatching {
                        child.send(JSONObject().put("schema", 1).put("command", "pause"))
                    }
                    runCatching { child.close() }
                }, "xbox-cancel").start()
            }
            return true
        }

        fun cancelDownloadByAppId(appId: Int, context: Context = PluviaApp.instance): Boolean {
            val productId = getProductIdByAppId(appId, context) ?: return false
            return cancelDownload(productId)
        }

        fun downloadGame(
            context: Context,
            productId: String,
            installPath: String,
        ): Result<DownloadInfo> {
            val app = context.applicationContext
            if (instance == null) {
                start(app)
            }
            val game = getXboxGameOf(productId, app)
                ?: return Result.failure(Exception("Xbox game not found: $productId"))

            instance?.activeDownloads?.get(productId)?.let { existing ->
                return Result.success(existing)
            }

            val downloadInfo = DownloadInfo(
                jobCount = 1,
                gameId = game.appId,
                downloadingAppIds = CopyOnWriteArrayList(),
            )
            downloadInfo.setPersistencePath(installPath)
            val persistedBytes = downloadInfo.loadPersistedBytesDownloaded(installPath)
            if (persistedBytes > 0L) {
                downloadInfo.initializeBytesDownloaded(persistedBytes)
            }
            downloadInfo.setActive(true)
            downloadInfo.updateStatusMessage(app.getString(R.string.download_preparing))

            instance?.activeDownloads?.put(productId, downloadInfo)
            instance?.activeDownloadPaths?.put(productId, installPath)

            MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            MarkerUtils.addMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)

            PluviaApp.events.emitJava(AndroidEvent.DownloadStatusChanged(game.appId, true))

            GameDownloadService.registerDownload(
                gameSource = GameSource.XBOX,
                gameId = productId,
                downloadInfo = downloadInfo,
                installPath = installPath,
            )

            ioScope.launch {
                var complete = false
                try {
                    val market = Xodus.market()
                    var contentId = game.contentId
                    if (!contentId.matches(Regex("[a-fA-F0-9-]{36}"))) {
                        val plan = Xodus.run(app, listOf("resolve-package", productId, "--json", "--market", market))
                        val choices = XboxCatalog.objects(plan.optJSONArray("choices"))
                            .filter { it.optBoolean("supported") && it.optString("kind") == "Game" }
                        check(choices.isNotEmpty()) { "This Xbox offer has no supported PC package." }
                        contentId = choices.first().getString("contentId")
                    }

                    val folder = File(installPath).apply { mkdirs() }
                    var sequence = -1L
                    var lastCompletedBytes = persistedBytes

                    Xodus.start(
                        app,
                        listOf(
                            "streaming",
                            productId,
                            folder.absolutePath,
                            "--market",
                            market,
                            "--progress-json",
                            "--control-stdin",
                            "--selected-content-id",
                            contentId,
                        ),
                    ).use { process ->
                        instance?.activeChildren?.put(productId, process)
                        Xodus.diagnostics(process)
                        process.process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                if (line.startsWith("NEXUS_PROGRESS ")) {
                                    val progress = JSONObject(line.removePrefix("NEXUS_PROGRESS "))
                                    val seq = progress.optLong("sequence", -1L)
                                    if (seq > sequence) {
                                        sequence = seq
                                        when (progress.optString("phase")) {
                                            "complete" -> complete = true
                                            "downloading", "download", "transfer" -> {
                                                val completed = progress.optLong("completedBytes", 0L)
                                                val total = progress.optLong("totalBytes", game.downloadSize)
                                                if (total > 0L) {
                                                    downloadInfo.setTotalExpectedBytes(total)
                                                    val delta = (completed - lastCompletedBytes).coerceAtLeast(0L)
                                                    if (delta > 0L) {
                                                        downloadInfo.updateBytesDownloaded(delta)
                                                        lastCompletedBytes = completed
                                                    }
                                                    downloadInfo.setProgress((completed.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                                                }
                                            }
                                            "finalizing", "installing" -> {
                                                downloadInfo.updateStatusMessage("Installing…")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        process.process.waitFor(15, TimeUnit.SECONDS)
                        complete = complete && process.process.exitValue() == 0
                    }

                    if (complete) {
                        val folder = File(installPath)
                        val manifestInfo = runCatching { XboxManifest.read(folder) }.getOrNull()
                        val exe = runCatching { XboxManifest.executable(folder) }.getOrDefault("")
                        val installedSize = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                        MarkerUtils.addMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                        dao(app).markAsInstalled(
                            productId = productId,
                            path = installPath,
                            exe = exe,
                            size = installedSize,
                            versionId = manifestInfo?.version.orEmpty(),
                            contentId = contentId,
                        )
                        downloadInfo.setProgress(1f)
                        downloadInfo.setActive(false)
                        DownloadService.invalidateCache()
                        withContext(Dispatchers.Main) {
                            PluviaApp.events.emitJava(AndroidEvent.DownloadStatusChanged(game.appId, false))
                            PluviaApp.events.emitJava(AndroidEvent.LibraryInstallStatusChanged(game.appId, GameSource.XBOX))
                        }
                    } else {
                        downloadInfo.setActive(false)
                        withContext(Dispatchers.Main) {
                            PluviaApp.events.emitJava(AndroidEvent.DownloadStatusChanged(game.appId, false))
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Xbox download failed for $productId")
                    downloadInfo.setActive(false)
                    withContext(Dispatchers.Main) {
                        SnackbarManager.show("Xbox download failed: ${e.message ?: "Unknown error"}")
                        PluviaApp.events.emitJava(AndroidEvent.DownloadStatusChanged(game.appId, false))
                    }
                } finally {
                    instance?.activeChildren?.remove(productId)
                    instance?.activeDownloads?.remove(productId)
                    instance?.activeDownloadPaths?.remove(productId)
                    GameDownloadService.unregisterDownload(app, GameSource.XBOX, productId)
                }
            }
            return Result.success(downloadInfo)
        }

        suspend fun deleteGame(context: Context, productId: String): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                cancelDownload(productId)
                val game = getXboxGameOf(productId, context)
                val path = game?.installPath?.takeIf { it.isNotEmpty() }
                    ?: game?.title?.takeIf { it.isNotEmpty() }?.let { XboxConstants.getGameInstallPath(context, it, productId) }
                if (!path.isNullOrEmpty()) {
                    File(path).deleteRecursively()
                }
                dao(context).markAsUninstalled(productId)
                if (game != null) {
                    withContext(Dispatchers.Main) {
                        PluviaApp.events.emitJava(AndroidEvent.LibraryInstallStatusChanged(game.appId, GameSource.XBOX))
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to uninstall Xbox game $productId")
                Result.failure(e)
            }
        }
    }
}
