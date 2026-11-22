package app.gamenative.service.xbox

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.Os
import org.json.JSONObject
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal object XboxRuntime {
    private val state = Any()
    private val startup = Any()
    private var child: Xodus.Child? = null
    private var owner: String? = null
    private var serviceSocket: File? = null
    private var generation = 0L
    private val views = XboxLaunchViews()
    private val launches = XboxLaunchLeases { android.os.SystemClock.uptimeMillis() }

    data class WineLaunchConfig(
        val windowsExePath: String,
        val workingDir: File,
        val envVars: Map<String, String>,
    )

    private class ActiveWineSession(
        val gameId: String,
        val leaseToken: String,
        val bridge: XboxSocketBridge,
        val mappedDriveLink: File?,
        val stopRenew: AtomicBoolean = AtomicBoolean(false),
    ) : Closeable {
        override fun close() {
            stopRenew.set(true)
            runCatching { bridge.close() }
            mappedDriveLink?.let { link ->
                runCatching { Files.deleteIfExists(link.toPath()) }
            }
            released(leaseToken)
        }
    }

    @Volatile
    private var activeWineSession: ActiveWineSession? = null

    /**
     * Localhost TCP -> AF_UNIX `xodus.sock` bridge (ported from `start_socket_bridge()` in `droiddeck-xbox`).
     * `xgameruntime.dll` inside Wine connects to `127.0.0.1:<port>`, sends the 64-byte hex `key`,
     * and is bridged bidirectionally to `xodus-service`'s Unix domain socket.
     */
    internal class XboxSocketBridge(private val unixSocketPath: String) : Closeable {
        val key: String = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        private val keyBytes = key.toByteArray(Charsets.US_ASCII)
        private val closed = AtomicBoolean(false)
        private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port: Int = serverSocket.localPort

        init {
            Thread({
                while (!closed.get()) {
                    val client = try {
                        serverSocket.accept()
                    } catch (_: Throwable) {
                        break
                    }
                    Thread({ handleClient(client) }, "xbox-socket-bridge-client").apply {
                        isDaemon = true
                        start()
                    }
                }
            }, "xbox-socket-bridge-accept").apply {
                isDaemon = true
                start()
            }
        }

        private fun handleClient(tcp: Socket) {
            val unix = LocalSocket()
            try {
                tcp.soTimeout = 5_000
                val tcpIn = tcp.getInputStream()
                val header = ByteArray(keyBytes.size)
                var read = 0
                while (read < header.size) {
                    val n = tcpIn.read(header, read, header.size - read)
                    if (n <= 0) {
                        runCatching { tcp.close() }
                        return
                    }
                    read += n
                }
                if (!MessageDigest.isEqual(header, keyBytes)) {
                    runCatching { tcp.close() }
                    return
                }
                tcp.soTimeout = 0
                unix.connect(LocalSocketAddress(unixSocketPath, LocalSocketAddress.Namespace.FILESYSTEM))
                val unixIn = unix.inputStream
                val unixOut = unix.outputStream
                val tcpOut = tcp.getOutputStream()

                val t1 = Thread({ pump(tcpIn, unixOut) { runCatching { unix.shutdownOutput() } } }, "xbox-bridge-tx").apply {
                    isDaemon = true
                    start()
                }
                val t2 = Thread({ pump(unixIn, tcpOut) { runCatching { tcp.shutdownOutput() } } }, "xbox-bridge-rx").apply {
                    isDaemon = true
                    start()
                }
                t1.join()
                t2.join()
            } catch (_: Throwable) {
            } finally {
                runCatching { unix.close() }
                runCatching { tcp.close() }
            }
        }

        private fun pump(input: InputStream, output: OutputStream, onEof: () -> Unit) {
            try {
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    output.flush()
                }
            } catch (_: Throwable) {
            } finally {
                onEof()
            }
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                runCatching { serverSocket.close() }
            }
        }
    }

    fun isActive(id: String): Boolean = launches.active(id)

    fun released(token: String) {
        runCatching { launches.release(token)?.close() }
    }

    fun renew(app: Context, id: String, token: String, sessionId: String? = "active"): Boolean =
        launches.renew(token, id, StoreAccounts.signedInAs(app, XboxStoreId.XBOX), sessionId)

    /**
     * Port of `droiddeck-xbox` launch orchestration for GameNative's Wine/gamescope session.
     * Prepares `xodus-service` + materialized launch view, starts the localhost TCP->Unix socket bridge,
     * maps the materialized view drive in `.wine/dosdevices`, and starts the 15s lease heartbeat.
     */
    fun prepareForWineLaunch(
        app: Context,
        gameId: String,
        installFolder: File,
        exeRelativePath: String,
        winePrefixDir: File,
    ): WineLaunchConfig {
        releaseActiveWineSession()

        val baseEnv = Xodus.stageWinePrefixRuntime(app, winePrefixDir).toMutableMap()
        val reply = prepare(
            app = app,
            id = gameId,
            installFolder = installFolder,
            exeRelativePath = exeRelativePath,
        )
        val socketPath = reply.getString("socket")
        val leaseToken = reply.getString("lease")
        val materializedExe = reply.optString("exe", "").ifEmpty {
            File(installFolder, exeRelativePath).absolutePath
        }
        val playfab = reply.optString("playfab", "")

        val bridge = try {
            XboxSocketBridge(socketPath)
        } catch (e: Exception) {
            released(leaseToken)
            throw e
        }

        val (winExePath, workingDir, mappedDriveLink) = mapViewDrive(
            winePrefixDir = winePrefixDir,
            installFolder = installFolder,
            exeRelativePath = exeRelativePath,
            materializedExePath = materializedExe,
        )

        val session = ActiveWineSession(
            gameId = gameId,
            leaseToken = leaseToken,
            bridge = bridge,
            mappedDriveLink = mappedDriveLink,
        )
        activeWineSession = session

        Thread({
            while (!session.stopRenew.get() && isActive(gameId)) {
                try {
                    Thread.sleep(15_000)
                } catch (_: InterruptedException) {
                    break
                }
                if (session.stopRenew.get()) break
                if (!renew(app, gameId, leaseToken)) {
                    Timber.tag("XboxRuntime").w("Xbox launch lease renewal ended for %s", gameId)
                    break
                }
            }
        }, "xbox-wine-lease-renew").apply {
            isDaemon = true
            start()
        }

        baseEnv["XODUS_SOCKET"] = socketPath
        baseEnv["XODUS_BRIDGE_PORT"] = bridge.port.toString()
        baseEnv["XODUS_BRIDGE_KEY"] = bridge.key
        baseEnv["XODUS_LOG"] = "off"
        baseEnv["RUST_LOG"] = "off"
        baseEnv["XODUS_SAVE_SYNC"] = "auto"
        val contentRootParent = installFolder.parentFile?.absolutePath ?: installFolder.absolutePath
        baseEnv["XODUS_CONTENT_ROOTS"] = "${installFolder.absolutePath}:$contentRootParent"
        if (playfab.isNotEmpty() && playfab != "null") {
            baseEnv["XODUS_XSTS_PLAYFAB"] = playfab
        }

        return WineLaunchConfig(
            windowsExePath = winExePath,
            workingDir = workingDir,
            envVars = baseEnv,
        )
    }

    fun releaseActiveWineSession() {
        val prev = activeWineSession
        activeWineSession = null
        prev?.close()
    }

    /**
     * Port of `view_drive(prefix, path)` from `droiddeck-xbox`.
     * Maps the materialized `droiddeck-xbox-*` launch view parent folder to a free Wine drive letter
     * (`X:`, `W:`, `V:`, ...) in `.wine/dosdevices` so Wine sees a clean shallow Windows path.
     */
    private fun mapViewDrive(
        winePrefixDir: File,
        installFolder: File,
        exeRelativePath: String,
        materializedExePath: String,
    ): Triple<String, File, File?> {
        val exeFile = File(materializedExePath)
        val workDir = exeFile.parentFile?.takeIf { it.isDirectory }
            ?: File(installFolder, exeRelativePath).parentFile
            ?: installFolder

        var viewRoot: File? = null
        var cur: File? = exeFile.parentFile
        while (cur != null) {
            if (cur.name.startsWith("droiddeck-xbox-")) {
                viewRoot = cur.parentFile ?: cur
                break
            }
            cur = cur.parentFile
        }

        if (viewRoot != null) {
            val dosdevices = File(winePrefixDir, "dosdevices").apply { mkdirs() }
            // Clean up stale droiddeck-xbox-* drive symlinks first
            for (letter in "xwvutsrqponmlkjihgfe") {
                val link = File(dosdevices, "$letter:")
                try {
                    if (Files.isSymbolicLink(link.toPath())) {
                        val target = Files.readSymbolicLink(link.toPath()).toString()
                        if (target.contains("droiddeck-xbox-") && !File(target).exists()) {
                            Files.deleteIfExists(link.toPath())
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            for (letter in "xwvutsrqponmlkjihgfe") {
                val link = File(dosdevices, "$letter:")
                val linkPath = link.toPath()
                val canUse = try {
                    if (!link.exists() && !Files.isSymbolicLink(linkPath)) {
                        true
                    } else if (Files.isSymbolicLink(linkPath)) {
                        Files.readSymbolicLink(linkPath).toFile().canonicalFile == viewRoot.canonicalFile
                    } else {
                        false
                    }
                } catch (_: Throwable) {
                    false
                }
                if (canUse) {
                    try {
                        Files.deleteIfExists(linkPath)
                        try {
                            Files.createSymbolicLink(linkPath, viewRoot.toPath())
                        } catch (_: Throwable) {
                            Os.symlink(viewRoot.absolutePath, link.absolutePath)
                        }
                        val rel = exeFile.canonicalFile.relativeTo(viewRoot.canonicalFile).path.replace("/", "\\")
                        return Triple("${letter.uppercaseChar()}:\\$rel", workDir, link)
                    } catch (e: Throwable) {
                        Timber.tag("XboxRuntime").w(e, "Failed mapping dosdevices drive %s:", letter)
                    }
                }
            }
        }

        // Fallback to A:\<exeRelativePath> if viewRoot mapping wasn't used
        val cleanRel = exeRelativePath.replace("/", "\\").trimStart('\\')
        return Triple("A:\\$cleanRel", workDir, null)
    }

    private data class Authorization(
        val account: String,
        val token: String,
        val runtime: Long,
        val credentials: String,
    )

    fun prepare(
        app: Context,
        id: String,
        installFolder: File,
        exeRelativePath: String,
        marketOverride: String? = null,
        sessionId: String = "active",
    ): JSONObject {
        val (account, token, current, credentials) = XboxCredentials.lifecycle(app) { epoch ->
            val account = StoreAccounts.signedInAs(app, XboxStoreId.XBOX) ?: error("Sign in to Xbox before playing")
            synchronized(state) { Authorization(account, launches.begin(id, account, sessionId), generation, epoch) }
        }
        Thread({
            while (isActive(id)) {
                Thread.sleep(10_000)
                launches.stale(
                    StoreAccounts.signedInAs(app, XboxStoreId.XBOX),
                    sessionId,
                    suspended = false,
                ).forEach { runCatching { it.close() } }
            }
        }, "xbox-launch-expiry").start()
        try {
            val socket = service(app, account, current, credentials)
            check(synchronized(state) { generation == current }) { "Xbox launch was cancelled" }
            val market = marketOverride?.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: Xodus.market()
            val directory = views.create(app.filesDir)
            val process = try {
                Xodus.start(
                    app,
                    listOf(
                        "run",
                        installFolder.absolutePath,
                        "droiddeck:prepare",
                        "--exe",
                        exeRelativePath,
                        "--materialize-chain",
                        "--market",
                        market,
                    ),
                    launchDirectory = directory,
                    credentialGeneration = credentials,
                )
            } catch (e: Exception) {
                XboxLaunchViews.remove(directory)
                throw e
            }
            if (!launches.attach(token, process)) {
                process.close()
                error("Xbox launch was cancelled")
            }
            Xodus.diagnostics(process)
            val result = java.util.concurrent.FutureTask {
                val input = process.process.inputStream.buffered()
                var line: String
                do {
                    line = XboxCredentials.readLine(input, 131_072)
                } while (!line.startsWith("DROIDDECK_LAUNCH_V1 "))
                JSONObject(line.removePrefix("DROIDDECK_LAUNCH_V1 "))
            }
            Thread(result, "xbox-launch-view").start()
            val view = result.get(90, java.util.concurrent.TimeUnit.SECONDS)
            check(renew(app, id, token, sessionId) && synchronized(state) { generation == current }) { "Xbox launch was cancelled" }
            return JSONObject()
                .put("ok", true)
                .put("socket", socket.path)
                .put("lease", token)
                .put("exe", view.getString("exe"))
                .put("playfab", view.opt("playfab"))
        } catch (e: Exception) {
            Timber.tag("XboxRuntime").e(e, "Failed to prepare Xbox launch for %s", id)
            released(token)
            throw e
        }
    }

    internal fun newServiceSocket(files: File): File =
        File(files, "xbox-runtime/s-${UUID.randomUUID().toString().replace("-", "")}/xodus.sock")

    private fun service(app: Context, account: String, current: Long, credentials: String): File = synchronized(startup) {
        val existing = synchronized(state) {
            check(generation == current) { "Xbox launch was cancelled" }
            serviceSocket?.takeIf { child?.process?.isAlive == true && owner == account }
        }
        val socket = existing ?: newServiceSocket(app.filesDir)
        if (existing == null) {
            val old = synchronized(state) {
                child.also {
                    child = null
                    owner = null
                    serviceSocket = null
                }
            }
            old?.close()
            socket.delete()
            val service = Xodus.start(
                app,
                emptyList(),
                service = true,
                runtimeDirectory = socket.parentFile!!,
                credentialGeneration = credentials,
            )
            val accepted = synchronized(state) {
                (generation == current).also {
                    if (it) {
                        child = service
                        owner = account
                        serviceSocket = socket
                    }
                }
            }
            if (!accepted) {
                service.close()
                error("Xbox launch was cancelled")
            }
            try {
                Xodus.diagnostics(service)
                Thread({
                    runCatching {
                        service.process.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            while (input.read(buffer) >= 0) {
                                Thread.yield()
                            }
                        }
                    }
                }, "xbox-service-output").start()
                val deadline = android.os.SystemClock.elapsedRealtime() + 90_000
                while (!socket.exists() &&
                    service.process.isAlive &&
                    synchronized(state) { generation == current } &&
                    android.os.SystemClock.elapsedRealtime() < deadline
                ) {
                    Thread.sleep(100)
                }
                check(socket.exists() && service.process.isAlive) {
                    "Xbox account service did not become ready. Check your sign-in and network connection."
                }
            } catch (e: Exception) {
                synchronized(state) {
                    if (child === service) {
                        child = null
                        owner = null
                        serviceSocket = null
                    }
                }
                service.close()
                throw e
            }
        }
        socket
    }

    fun stop() {
        releaseActiveWineSession()
        val processes = synchronized(state) {
            generation++
            val all = launches.clear() + listOfNotNull(child)
            child = null
            owner = null
            serviceSocket = null
            all
        }
        Thread({ processes.forEach { runCatching { it.close() } } }, "xbox-runtime-stop").start()
    }
}
