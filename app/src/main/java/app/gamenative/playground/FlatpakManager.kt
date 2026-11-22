package app.gamenative.playground

import android.content.Context
import timber.log.Timber
import java.io.File

/**
 * Controller for Flatpak applications in the rootless Linux Playground.
 * Preserves proot -0 with /var/cache/droiddeck-flatpak and FEX interpreter for x86_64 Flatpaks.
 */
object FlatpakManager {
    const val FLATPAK = "/usr/bin/flatpak"
    const val BWRAP = "/usr/local/bin/droiddeck-bwrap"
    const val LAUNCHER = "/usr/local/bin/droiddeck-flatpak-run"
    private const val HELPER = "/usr/local/bin/droiddeck-flatpak"
    private const val SETUP = "/usr/local/bin/droiddeck-flatpak-setup"
    private const val USER_DIR = "root/.local/share/flatpak"

    @Volatile var busy: String? = null
        private set

    class App(
        val id: String,
        val name: String,
        val icon: File?,
        val branch: String,
        val summary: String?,
        val arch: String = "aarch64"
    )

    fun ready(context: Context): Boolean {
        val root = LinuxRuntime.rootDir(context)
        return File(root, FLATPAK.substring(1)).isFile &&
            File(root, "var/cache/droiddeck-flatpak/installed").isFile &&
            File(root, "$USER_DIR/repo/config").takeIf { it.isFile }?.readText()?.contains("[remote \"flathub\"]") == true
    }

    private fun arch(): String = when (android.os.Build.SUPPORTED_ABIS.firstOrNull()) {
        "x86_64" -> "x86_64"
        else -> "aarch64"
    }

    internal fun arches(native: String = arch()): List<String> =
        if (native == "aarch64") listOf("aarch64", "x86_64") else listOf(native)

    internal fun installedBranch(appDir: File, native: String = arch()): Pair<String, File>? =
        arches(native).firstNotNullOfOrNull { a ->
            File(appDir, a).listFiles()?.firstOrNull { File(it, "active").exists() }?.let { a to it }
        }

    fun foreign(app: App, native: String = arch()): Boolean = app.arch != native

    fun launchArgs(app: App, native: String = arch()): List<String> =
        if (foreign(app, native)) listOf("--arch=${app.arch}", app.id) else listOf(app.id)

    fun installedApps(context: Context): List<App> {
        val apps = File(LinuxRuntime.rootDir(context), "$USER_DIR/app")
        return apps.listFiles()?.mapNotNull { dir ->
            val id = dir.name
            val (a, branch) = installedBranch(dir) ?: return@mapNotNull null
            val active = File(branch, "active")
            val export = File(active, "export/share")
            val desktop = File(export, "applications/$id.desktop").takeIf { it.isFile }?.readText()
            val name = desktop?.let { entry(it, "Name") } ?: id.substringAfterLast('.')
            val summary = desktop?.let { entry(it, "Comment") }
            App(id, name, icon(export, id), branch.name, summary, a)
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun isInstalled(context: Context, id: String): Boolean =
        installedBranch(File(LinuxRuntime.rootDir(context), "$USER_DIR/app/$id")) != null

    private fun entry(desktop: String, key: String): String? {
        var inMain = false
        for (line in desktop.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("[")) { inMain = t == "[Desktop Entry]"; continue }
            if (inMain && t.startsWith("$key=")) return t.substringAfter('=').trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun icon(export: File, id: String): File? {
        val hicolor = File(export, "icons/hicolor")
        return hicolor.listFiles()
            ?.mapNotNull { size -> size.name.substringBefore('x').toIntOrNull()?.let { it to File(size, "apps/$id.png") } }
            ?.filter { it.second.isFile }
            ?.maxByOrNull { it.first }?.second
    }

    private inline fun <T> exclusive(what: String, ifBusy: () -> T, block: () -> T): T {
        synchronized(this) {
            if (busy != null) return ifBusy()
            busy = what
        }
        try { return block() } finally { busy = null }
    }

    fun runGuest(
        context: Context,
        argv: List<String>,
        fakeRoot: Boolean,
        onLine: (String) -> Unit
    ): Int {
        val root = LinuxRuntime.rootDir(context)
        val runtimeDir = File(context.filesDir, ".flatpak-rt").apply { mkdirs() }
        val cmd = LinuxRuntime.prootPrefix(context, root, "/root", fakeRoot)
        val binds = LinuxRuntime.binds(context, runtimeDir)
        binds.forEach { cmd += "-b"; cmd += it }
        cmd += listOf(
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games",
            "XDG_RUNTIME_DIR=${LinuxRuntime.GUEST_RUNTIME_DIR}",
            "XDG_DATA_HOME=/root/.local/share",
            "FLATPAK_BWRAP=$BWRAP",
            "DROIDDECK_BWRAP_SOCKET=${BwrapSpawner.socket(context)}",
            "XDG_DATA_DIRS=/root/.local/share/flatpak/exports/share:/usr/local/share:/usr/share",
        )
        cmd += argv
        val builder = ProcessBuilder(cmd).directory(root).redirectErrorStream(true)
        val proc = builder.start()
        val pid = try {
            val field = proc.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            val p = field.getInt(proc)
            field.isAccessible = false
            p
        } catch (e: Exception) { -1 }

        OrphanReaper.keep(pid)
        try {
            proc.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
            return proc.waitFor()
        } finally {
            OrphanReaper.release(pid)
        }
    }

    fun setup(context: Context, onProgress: (String, Int) -> Unit): String? {
        if (!LinuxRuntime.isInstalled(context)) return "Linux Playground runtime not installed"
        return exclusive("setup", { "Flatpak manager is busy" }) {
            var failure: String? = null
            val status = runGuest(context, listOf("/bin/bash", SETUP), fakeRoot = true) { line ->
                Timber.i("Flatpak setup: $line")
                if (line.startsWith("ERR ")) failure = line.substring(4)
            }
            if (status != 0) failure ?: "Flatpak setup failed with code $status" else null
        }
    }

    fun install(context: Context, id: String, arch: String = "aarch64", onProgress: (Int) -> Unit): String? {
        if (!LinuxRuntime.isInstalled(context)) return "Linux Playground runtime not installed"
        return exclusive("install:$id", { "Another Flatpak operation is in progress" }) {
            var failure: String? = null
            val args = mutableListOf("/bin/bash", HELPER, "install", id)
            if (arch != "aarch64") {
                args += "--arch=$arch"
            }
            val status = runGuest(context, args, fakeRoot = false) { line ->
                Timber.d("Flatpak install: $line")
                if (line.startsWith("ERR ")) failure = line.substring(4)
                if (line.startsWith("PROG ")) {
                    line.substring(5).toIntOrNull()?.let { onProgress(it) }
                }
            }
            if (status != 0) failure ?: "Install failed with code $status" else null
        }
    }

    fun uninstall(context: Context, id: String): String? {
        if (!LinuxRuntime.isInstalled(context)) return "Linux Playground runtime not installed"
        return exclusive("uninstall:$id", { "Another Flatpak operation is in progress" }) {
            var failure: String? = null
            val status = runGuest(context, listOf("/bin/bash", HELPER, "uninstall", id), fakeRoot = false) { line ->
                Timber.d("Flatpak uninstall: $line")
                if (line.startsWith("ERR ")) failure = line.substring(4)
            }
            if (status != 0) failure ?: "Uninstall failed with code $status" else null
        }
    }
}
