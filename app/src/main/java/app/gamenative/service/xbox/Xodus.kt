package app.gamenative.service.xbox

import android.content.Context
import android.os.Environment
import android.system.Os
import app.gamenative.playground.LinuxRuntime
import app.gamenative.playground.LinuxRuntimeInstaller
import com.winlator.core.TarCompressorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

internal object Xodus {
    const val DIR = "/usr/local/lib/droiddeck-xbox"

    sealed interface PackState {
        data object Missing : PackState
        data class Downloading(val progress: Float) : PackState
        data object Failed : PackState
        data object Ready : PackState
    }

    private val mutablePackState = MutableStateFlow<PackState>(PackState.Missing)
    val packState: StateFlow<PackState> = mutablePackState.asStateFlow()

    fun market(): String =
        java.util.Locale.getDefault().country.uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US"

    class Child(
        val process: Process,
        private val credentials: XboxCredentials,
        private val runtimeDirectory: File? = null,
        private val launchDirectory: File? = null,
        private val networkLink: com.droiddeck.launcher.runtime.LinuxNetworkLinkComponent? = null,
    ) : Closeable {
        @Synchronized
        fun send(frame: JSONObject) {
            process.outputStream.write((frame.toString() + "\n").toByteArray())
            process.outputStream.flush()
        }

        override fun close() {
            try {
                credentials.close()
                runCatching { process.outputStream.close() }
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    credentials.signalBackend(android.system.OsConstants.SIGINT)
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        credentials.signalBackend(android.system.OsConstants.SIGKILL)
                        if (!process.waitFor(1, TimeUnit.SECONDS)) {
                            val pid = XboxCredentials.pidOf(process)
                            if (pid > 0) android.os.Process.killProcess(pid)
                        }
                    }
                }
            } finally {
                networkLink?.stop()
                runtimeDirectory?.let { directory ->
                    File(directory, "xodus.sock").delete()
                    directory.delete()
                }
                launchDirectory?.let(XboxLaunchViews::remove)
            }
        }
    }

    private fun packRoot(app: Context) = File(app.filesDir, "addons/xbox")

    private fun findInstalledPackDir(app: Context): File? {
        val required = listOf("version", "xodus-cli", "xodus-service")
        return packRoot(app).listFiles().orEmpty()
            .filter { dir ->
                dir.isDirectory && required.all { File(dir, it).isFile }
            }
            .maxByOrNull { it.lastModified() }
    }

    fun ensureAddonPack(
        app: Context,
        running: () -> Boolean = { true },
        onState: (PackState) -> Unit = {},
    ): Boolean {
        findInstalledPackDir(app)?.let {
            mutablePackState.value = PackState.Ready
            onState(PackState.Ready)
            return true
        }
        val root = packRoot(app).apply { mkdirs() }
        val archive = File(root, "xbox.tar.zst")
        try {
            mutablePackState.value = PackState.Downloading(0.05f)
            onState(mutablePackState.value)

            // 1. Try bundled asset first if present
            val copiedFromAssets = runCatching {
                app.assets.open("packs/xbox.tar.zst").use { input ->
                    archive.outputStream().use { input.copyTo(it) }
                }
            }.isSuccess

            // 2. Otherwise query GitHub releases of Droid-Deck/DroidDeck-Components for latest pack-xbox-*
            if (!copiedFromAssets) {
                val downloadUrl = resolveLatestXboxPackUrl()
                if (downloadUrl == null || !downloadPack(downloadUrl, archive, running) { progress ->
                        val s = PackState.Downloading(progress)
                        mutablePackState.value = s
                        onState(s)
                    }
                ) {
                    archive.delete()
                    mutablePackState.value = PackState.Failed
                    onState(PackState.Failed)
                    return false
                }
            }

            val staging = File(root, "staging")
            staging.deleteRecursively()
            staging.mkdirs()
            val extracted = TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                archive,
                staging,
            )
            archive.delete()
            if (!extracted || !File(staging, "xodus-cli").isFile || !File(staging, "xodus-service").isFile) {
                staging.deleteRecursively()
                mutablePackState.value = PackState.Failed
                onState(PackState.Failed)
                return false
            }
            val versionTag = runCatching { File(staging, "version").readText().trim() }.getOrDefault("latest").ifEmpty { "latest" }
            val target = File(root, versionTag)
            target.deleteRecursively()
            staging.renameTo(target)
            File(target, ".complete").writeText(versionTag)
            mutablePackState.value = PackState.Ready
            onState(PackState.Ready)
            return true
        } catch (e: Exception) {
            Timber.tag("Xodus").e(e, "Failed to install Xbox addon pack")
            archive.delete()
            mutablePackState.value = PackState.Failed
            onState(PackState.Failed)
            return false
        }
    }

    private fun resolveLatestXboxPackUrl(): String? = runCatching {
        val conn = URL("https://api.github.com/repos/${XboxConstants.XBOX_PACK_REPO}/releases?per_page=30").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return null
            val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            for (i in 0 until array.length()) {
                val rel = array.optJSONObject(i) ?: continue
                val tag = rel.optString("tag_name")
                if (!tag.startsWith("pack-xbox-")) continue
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    if (asset.optString("name") == "xbox.tar.zst") {
                        return asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
                    }
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun downloadPack(
        url: String,
        dest: File,
        running: () -> Boolean,
        onProgress: (Float) -> Unit,
    ): Boolean = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return false
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            var readTotal = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(65536)
                    while (running()) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            running()
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    @Synchronized
    fun stage(app: Context): File {
        check(LinuxRuntimeInstaller.ensureInstalledBlocking(app)) {
            "Couldn't install the shared Ubuntu Linux runtime required by Xbox. Check your connection and try again."
        }
        LinuxRuntime.ensureResolvAndAccounts(app)
        LinuxRuntime.stageSessionFiles(app)
        LinuxRuntime.repairCoreutilsL2s(LinuxRuntime.rootDir(app))
        LinuxRuntime.sanitizeLdSoPreload(LinuxRuntime.rootDir(app))
        check(ensureAddonPack(app)) { "Couldn't download the Xbox components. Check your connection and try again." }
        val pack = findInstalledPackDir(app) ?: error("Xbox component pack missing after install")
        val root = File(LinuxRuntime.rootDir(app), DIR.removePrefix("/"))
        val packaged = runCatching { File(pack, "version").readText() }.getOrDefault("latest")
        if (runCatching { File(root, "version").readText() }.getOrNull() == packaged &&
            File(root, "xodus-cli").isFile
        ) {
            return pack
        }
        fun copy(source: File, relative: String) {
            if (source.isDirectory) {
                source.listFiles().orEmpty().forEach {
                    copy(it, if (relative.isEmpty()) it.name else "$relative/${it.name}")
                }
                return
            }
            if (relative == "version" || relative == ".complete") return
            val file = File(root, relative)
            file.parentFile?.mkdirs()
            val staged = File(file.path + ".staged")
            source.inputStream().use { input -> staged.outputStream().use { input.copyTo(it) } }
            staged.setExecutable(true, false)
            check(staged.renameTo(file))
        }
        copy(pack, "")
        File(root, "version").writeText(packaged)
        return pack
    }

    /**
     * Stages Xbox Windows Runtime DLLs (`xgameruntime.dll`, `wintypes.dll`, `winhttp.dll`,
     * `windows.web.dll`, `twinapi.appcore.dll`, `libnghttp2.dll`) and `BedrockOnLinux` `GameInput`
     * + `WindowsRuntime\ActivatableClassId` registry keys into the active Wine prefix (`drive_c/windows/system32`).
     */
    fun stageWinePrefixRuntime(app: Context, winePrefixDir: File): Map<String, String> {
        val rootfsRuntimeWindows = File(LinuxRuntime.rootDir(app), "${DIR.removePrefix("/")}/windows")
        val packWindows = findInstalledPackDir(app)?.let { File(it, "windows") }
        val sourceWindowsDir = when {
            rootfsRuntimeWindows.isDirectory -> rootfsRuntimeWindows
            packWindows?.isDirectory == true -> packWindows
            else -> null
        }

        val system32 = File(winePrefixDir, "drive_c/windows/system32").apply { mkdirs() }
        if (sourceWindowsDir != null) {
            for (dllName in XboxConstants.REQUIRED_WINDOWS_DLLS) {
                val src = File(sourceWindowsDir, dllName)
                if (src.isFile) {
                    val dst = File(system32, dllName)
                    runCatching { src.copyTo(dst, overwrite = true) }
                }
            }
        }

        // Inject WinRT ActivatableClassId + GameInput redistributable registry keys into system.reg if missing
        val systemReg = File(winePrefixDir, "system.reg")
        if (systemReg.isFile) {
            runCatching {
                val content = systemReg.readText(Charsets.UTF_8)
                val sb = StringBuilder()
                val classBase = "Software\\\\Microsoft\\\\WindowsRuntime\\\\ActivatableClassId\\\\"
                for ((dll, classes) in XboxConstants.WINRT_ACTIVATABLE_CLASSES) {
                    for (cls in classes) {
                        val keyHeader = "[${classBase}${cls}]"
                        if (!content.contains(keyHeader)) {
                            sb.append("\n$keyHeader\n")
                            sb.append("\"DllPath\"=\"C:\\\\windows\\\\system32\\\\$dll\"\n")
                            sb.append("\"Threading\"=dword:00000000\n")
                            sb.append("\"ActivationType\"=dword:00000000\n")
                        }
                    }
                }
                // BedrockOnLinux GameInput COM / Service entry for GDK games
                val gameInputKey = "[Software\\\\Microsoft\\\\WindowsRuntime\\\\ActivatableClassId\\\\Windows.Gaming.Input.Gamepad]"
                if (!content.contains(gameInputKey)) {
                    sb.append("\n$gameInputKey\n")
                    sb.append("\"DllPath\"=\"C:\\\\windows\\\\system32\\\\xgameruntime.dll\"\n")
                    sb.append("\"Threading\"=dword:00000000\n")
                }
                if (sb.isNotEmpty()) {
                    systemReg.appendText(sb.toString(), Charsets.UTF_8)
                }
            }.onFailure { e ->
                Timber.tag("Xodus").w(e, "Failed to update system.reg with Xbox WinRT/GameInput classes")
            }
        }

        val dllOverrides = XboxConstants.REQUIRED_WINDOWS_DLLS
            .filter { it != "libnghttp2.dll" }
            .joinToString(";") { "${it.removeSuffix(".dll")}=n" }
        return mapOf(
            "WINEDLLOVERRIDES" to dllOverrides,
            "XODUS_SAVE_SYNC" to "auto",
        )
    }

    fun start(
        app: Context,
        args: List<String>,
        service: Boolean = false,
        runtimeDirectory: File? = null,
        launchDirectory: File? = null,
        credentialGeneration: String? = null,
    ): Child {
        val pack = stage(app)
        val binary = "$DIR/${if (service) "xodus-service" else "xodus-cli"}"
        val root = LinuxRuntime.rootDir(app)
        LinuxRuntime.repairCoreutilsL2s(root)
        LinuxRuntime.sanitizeLdSoPreload(root)
        val hostBinary = File(root, binary.removePrefix("/"))
        check(hostBinary.isFile) { "The Xbox backend is missing. Update the Linux runtime." }
        runCatching { Os.chmod(hostBinary.absolutePath, 493) }
        hostBinary.setReadable(true, false)
        hostBinary.setExecutable(true, false)

        val networkLink = runCatching {
            com.droiddeck.launcher.runtime.LinuxNetworkLinkComponent(app, root).apply {
                attach(app)
                publish()
                start()
            }
        }.onFailure { e ->
            Timber.tag("Xodus").w(e, "LinuxNetworkLinkComponent could not start")
        }.getOrNull()

        val credentials = XboxCredentials(app, credentialGeneration)
        try {
            val runtime = (runtimeDirectory ?: File(app.filesDir, "xbox-runtime")).apply { mkdirs() }
            val external = Environment.getExternalStorageDirectory()
            val extraBinds = mutableListOf<String>()
            if (external != null && external.exists()) {
                extraBinds += "${external.absolutePath}:${external.absolutePath}"
            }
            val binds = LinuxRuntime.binds(app, runtime, extraBinds)
            val command = LinuxRuntime.prootPrefix(app, root, cwd = "/root", fakeRoot = true)
            for (b in binds) {
                command += "-b"
                command += b
            }
            command += listOf(
                "/usr/bin/env",
                "-u",
                "LD_LIBRARY_PATH",
                "HOME=/root",
                "PATH=${LinuxRuntime.GUEST_PATH}",
                "XODUS_LOG=off",
                "RUST_LOG=off",
                "XDG_RUNTIME_DIR=${runtime.path}",
                "DROIDDECK_XBOX_CREDENTIAL_SOCKET=${credentials.name}",
                binary,
            )
            command.addAll(args)

            val builder = ProcessBuilder(command).directory(root)
            if (launchDirectory != null) builder.environment()["TMPDIR"] = launchDirectory.path
            builder.environment()["DROIDDECK_XBOX_CREDENTIAL_PORT"] = credentials.port.toString()
            builder.environment()["DROIDDECK_XBOX_CREDENTIAL_KEY"] = credentials.key
            LinuxRuntime.applyProotEnv(app, builder.environment())
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            File(pack, binary.removePrefix("$DIR/")).inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val process = builder.start()
            credentials.bind(process, hostBinary, digest.digest())
            return Child(process, credentials, runtimeDirectory, launchDirectory, networkLink)
        } catch (e: Exception) {
            runCatching { networkLink?.stop() }
            credentials.close()
            throw e
        }
    }

    fun run(
        app: Context,
        args: List<String>,
        timeout: Long = 90,
        credentialGeneration: String? = null,
    ): JSONObject {
        start(app, args, credentialGeneration = credentialGeneration).use { child ->
            val output = java.io.ByteArrayOutputStream()
            var readError: Throwable? = null
            val reader = Thread({
                try {
                    val buffer = ByteArray(8192)
                    child.process.inputStream.use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 8_000_000)
                            output.write(buffer, 0, count)
                        }
                    }
                } catch (t: Throwable) {
                    readError = t
                    child.process.destroy()
                }
            }, "xbox-result").apply { start() }
            val diagnostic = diagnostics(child)
            check(child.process.waitFor(timeout, TimeUnit.SECONDS)) { "Xbox request timed out. Try again." }
            reader.join(3000)
            diagnostic.join(3000)
            check(readError == null && !reader.isAlive) { "Xbox returned an invalid response." }
            check(child.process.exitValue() == 0) { "Xbox request failed. Check your account and network connection." }
            return JSONObject(output.toString("UTF-8"))
        }
    }

    const val SIGN_IN_REJECTED = "Microsoft content licensing rejected sign-in \\(HTTP 401\\)\\. Sign in again in Settings\\."

    fun diagnostics(
        child: Child,
        report: (String) -> Unit = { Timber.tag("Xodus").i("Xbox: %s", it) },
    ): Thread = Thread({
        runCatching {
            child.process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    Timber.tag("Xodus").d("stderr: %s", line)
                    report(line)
                }
            }
        }
    }, "xbox-diagnostics").apply { start() }
}
