package app.gamenative.playground

import android.content.Context
import android.os.Environment
import android.system.Os
import com.winlator.core.TarCompressorUtils
import timber.log.Timber
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Unified mobile-optimized Linux runtime environment coordinator (ported from DroidDeck).
 * Powers both PC Playground (KDE Plasma Desktop, Flatpak, AppImage) and the Xbox Xodus
 * daemon (`xodus-cli` / `xodus-service`) inside a single shared Ubuntu arm64 rootfs at
 * `files/playground_rootfs/`.
 */
object LinuxRuntime {
    const val DIR = "linuxfs"
    const val SESSION_SCRIPT = "/usr/local/bin/droiddeck-session"
    const val DESKTOP_SCRIPT = "/usr/local/bin/droiddeck-desktop"
    const val MODE_DESKTOP = "desktop"
    const val MODE_RUN = "run"

    const val GUEST_RUNTIME_DIR = "/run/droiddeck"
    const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games"
    const val DESKTOP_MARKER = ".dd-addon-desktop-kde"
    const val DESKTOP_PKG_MARKER = ".droiddeck-pkg-desktop-kde"

    private const val FAKE_STAT =
        "cpu  1000 0 1000 100000 0 0 0 0 0 0\n" +
            "cpu0 250 0 250 25000 0 0 0 0 0 0\n" +
            "cpu1 250 0 250 25000 0 0 0 0 0 0\n" +
            "cpu2 250 0 250 25000 0 0 0 0 0 0\n" +
            "cpu3 250 0 250 25000 0 0 0 0 0 0\n" +
            "intr 0\nctxt 0\nbtime 1700000000\nprocesses 1\nprocs_running 1\nprocs_blocked 0\nsoftirq 0 0 0 0 0 0 0 0 0 0 0\n"
    private const val FAKE_VERSION =
        "Linux version 6.1.0-droiddeck (droiddeck@localhost) (gcc 13.2.0) #1 SMP PREEMPT_DYNAMIC\n"
    private const val FAKE_UPTIME = "1000.00 4000.00\n"
    private const val FAKE_LOADAVG = "0.10 0.10 0.10 1/64 1000\n"
    private const val FAKE_VMSTAT = "nr_free_pages 262144\nnr_inactive_anon 1024\nnr_active_anon 8192\n"
    private const val FAKE_CAP_LAST_CAP = "40\n"

    fun rootDir(context: Context): File {
        val dir = File(context.filesDir, DIR)
        val legacy = File(context.filesDir, "playground_rootfs")
        if (legacy.exists() && (!dir.exists() || dir.list().isNullOrEmpty())) {
            Timber.tag("LinuxRuntime").i("Migrating legacy playground_rootfs to linuxfs")
            if (dir.exists()) dir.deleteRecursively()
            legacy.renameTo(dir)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun l2sDir(context: Context): File {
        val dir = File(rootDir(context), ".l2s")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sessionRoot(context: Context): File {
        val dir = File(context.filesDir, "session")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun liveDir(context: Context): File {
        val dir = File(context.noBackupFilesDir, "guest-runtime")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun prootTempDir(context: Context): File {
        val dir = File(liveDir(context), "proot")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun logDir(context: Context): File {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Checks if the base Ubuntu rootfs is installed (sufficient for Xbox `xodus-cli`/`xodus-service`
     * and headless Linux commands).
     */
    fun isInstalled(context: Context): Boolean {
        val root = rootDir(context)
        val hasShell = hasFileOrSymlink(File(root, "bin/sh")) || hasFileOrSymlink(File(root, "usr/bin/sh"))
        val hasVersion = File(root, ".version").isFile
        val hasProot = prootBinary(context).isFile
        return (hasShell || hasVersion || com.droiddeck.launcher.runtime.LinuxRuntime.hasRootfs(context)) && hasProot
    }

    /**
     * Checks if the KDE Plasma Desktop addon (`desktop-kde-r3`) is installed on top of the base rootfs.
     */
    fun isDesktopInstalled(context: Context): Boolean {
        if (!isInstalled(context)) return false
        val root = rootDir(context)
        return File(root, DESKTOP_MARKER).isFile ||
            File(root, DESKTOP_PKG_MARKER).isFile ||
            hasFileOrSymlink(File(root, "usr/bin/startplasma-wayland")) ||
            hasFileOrSymlink(File(root, "usr/bin/kwin_wayland")) ||
            hasFileOrSymlink(File(root, "usr/bin/startplasma-x11")) ||
            hasFileOrSymlink(File(root, "usr/bin/plasmashell"))
    }

    private fun hasFileOrSymlink(f: File): Boolean {
        if (f.exists()) return true
        return try {
            Os.lstat(f.absolutePath) != null
        } catch (_: Throwable) {
            false
        }
    }

    fun prootBinary(context: Context): File {
        val dd = com.droiddeck.launcher.runtime.LinuxRuntime.prootBinary(context)
        if (dd.isFile) return dd
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val candidate = File(nativeDir, "libproot.so")
        if (candidate.isFile) return candidate
        return File(rootDir(context), "usr/bin/proot")
    }

    fun prootLoader(context: Context): File {
        val dd = com.droiddeck.launcher.runtime.LinuxRuntime.prootLoader(context)
        if (dd.isFile) return dd
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val candidate = File(nativeDir, "libproot-loader.so")
        if (candidate.isFile) return candidate
        return File(rootDir(context), "usr/lib/proot/loader")
    }

    /**
     * Ensures `/etc/resolv.conf`, `/etc/hosts`, `/etc/passwd`, `/etc/group`, and fake `/proc`
     * files exist inside `playground_rootfs` so PRoot processes (both Xbox Xodus and Desktop)
     * have working DNS and user resolution on Android.
     */
    @Synchronized
    fun ensureResolvAndAccounts(context: Context) {
        val root = rootDir(context)
        val etc = File(root, "etc").apply { mkdirs() }

        writeFileSafe(
            File(etc, "resolv.conf"),
            "nameserver 1.1.1.1\nnameserver 8.8.8.8\n",
        )
        val hosts = File(etc, "hosts")
        if (!hosts.isFile) {
            writeFileSafe(hosts, "127.0.0.1 localhost\n::1 localhost\n")
        }
        val passwd = File(etc, "passwd")
        if (!passwd.isFile) {
            writeFileSafe(
                passwd,
                "root:x:0:0:root:/root:/bin/bash\n" +
                    "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin\n",
            )
        }
        val group = File(etc, "group")
        if (!group.isFile) {
            writeFileSafe(
                group,
                "root:x:0:\n" +
                    "nogroup:x:65534:\n",
            )
        }

        File(root, "root").mkdirs()
        File(root, "tmp/shm").mkdirs()
        File(root, "run/droiddeck").mkdirs()
        File(root, "var/empty").mkdirs()

        val fakeProc = File(root, "etc/.proc").apply { mkdirs() }
        writeIfMissing(File(fakeProc, "stat"), FAKE_STAT)
        writeIfMissing(File(fakeProc, "version"), FAKE_VERSION)
        writeIfMissing(File(fakeProc, "uptime"), FAKE_UPTIME)
        writeIfMissing(File(fakeProc, "loadavg"), FAKE_LOADAVG)
        writeIfMissing(File(fakeProc, "vmstat"), FAKE_VMSTAT)
        writeIfMissing(File(fakeProc, "cap_last_cap"), FAKE_CAP_LAST_CAP)
    }

    /**
     * Copies DroidDeck overlay scripts from `assets/linuxfs/` (`usr/local/bin/droiddeck-*`, etc.)
     * into `playground_rootfs`, extracts bundled addon packs (`xbox.tar.zst`, `directaudio.tar.zst`),
     * and configures `/etc/droiddeck/bwrap-socket`.
     */
    @Synchronized
    fun stageSessionFiles(context: Context) {
        ensureResolvAndAccounts(context)
        val root = rootDir(context)
        runCatching {
            copyAssetTree(context, "linuxfs", root)
        }.onFailure { e ->
            Timber.tag("LinuxRuntime").w(e, "Failed staging linuxfs asset tree")
        }
        runCatching {
            com.droiddeck.launcher.session.SessionFiles.stage(context, root, false)
        }.onFailure { e ->
            Timber.tag("LinuxRuntime").w(e, "Failed DroidDeck SessionFiles.stage")
        }
        if (isDesktopInstalled(context) && !File(root, DESKTOP_PKG_MARKER).isFile) {
            writeFileSafe(File(root, DESKTOP_PKG_MARKER), "desktop-kde-r3\n")
        }
        val binDir = File(root, "usr/local/bin")
        binDir.listFiles()?.forEach { f ->
            if (f.isFile) {
                runCatching { Os.chmod(f.absolutePath, 493) }
                f.setReadable(true, false)
                f.setExecutable(true, false)
            }
        }
        val usrBin = File(root, "usr/bin")
        usrBin.listFiles()?.forEach { f ->
            if (f.isFile && (f.name.startsWith("steamos-") || f.name.startsWith("jupiter-"))) {
                runCatching { Os.chmod(f.absolutePath, 493) }
                f.setReadable(true, false)
                f.setExecutable(true, false)
            }
        }
        val ddEtc = File(root, "etc/droiddeck").apply { mkdirs() }
        writeFileSafe(
            File(ddEtc, "bwrap-socket"),
            BwrapSpawner.socket(context) + "\n",
        )

        // Ensure bundled xbox pack is staged into /usr/local/lib/droiddeck-xbox
        val xboxDir = File(root, "usr/local/lib/droiddeck-xbox")
        if (!File(xboxDir, "xodus-cli").isFile || !File(xboxDir, "xodus-service").isFile) {
            runCatching {
                val tmpArchive = File(context.cacheDir, "xbox-stage.tar.zst")
                context.assets.open("packs/xbox.tar.zst").use { input ->
                    tmpArchive.outputStream().use { input.copyTo(it) }
                }
                val staging = File(context.cacheDir, "xbox-staging").apply { mkdirs() }
                if (TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, tmpArchive, staging)) {
                    xboxDir.mkdirs()
                    staging.copyRecursively(xboxDir, overwrite = true)
                    val cli = File(xboxDir, "xodus-cli")
                    val srv = File(xboxDir, "xodus-service")
                    runCatching { Os.chmod(cli.absolutePath, 493) }
                    runCatching { Os.chmod(srv.absolutePath, 493) }
                    cli.setReadable(true, false)
                    cli.setExecutable(true, false)
                    srv.setReadable(true, false)
                    srv.setExecutable(true, false)
                }
                tmpArchive.delete()
                staging.deleteRecursively()
            }.onFailure { e ->
                Timber.tag("LinuxRuntime").w(e, "Failed staging bundled xbox pack into rootfs")
            }
        } else {
            val cli = File(xboxDir, "xodus-cli")
            val srv = File(xboxDir, "xodus-service")
            runCatching { Os.chmod(cli.absolutePath, 493) }
            runCatching { Os.chmod(srv.absolutePath, 493) }
            cli.setReadable(true, false)
            cli.setExecutable(true, false)
            srv.setReadable(true, false)
            srv.setExecutable(true, false)
        }

        // Ensure bundled directaudio pack is staged into /usr/local/lib/directaudio
        val daDir = File(root, "usr/local/lib/directaudio")
        if (!File(daDir, "linux-wine11").isFile) {
            runCatching {
                val tmpArchive = File(context.cacheDir, "da-stage.tar.zst")
                context.assets.open("packs/directaudio.tar.zst").use { input ->
                    tmpArchive.outputStream().use { input.copyTo(it) }
                }
                val staging = File(context.cacheDir, "da-staging").apply { mkdirs() }
                if (TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, tmpArchive, staging)) {
                    daDir.mkdirs()
                    staging.copyRecursively(daDir, overwrite = true)
                }
                tmpArchive.delete()
                staging.deleteRecursively()
            }.onFailure { e ->
                Timber.tag("LinuxRuntime").w(e, "Failed staging bundled directaudio pack into rootfs")
            }
        }

        sanitizeLdSoPreload(root)
        repairCoreutilsL2s(root)
    }

    private fun copyAssetTree(context: Context, assetPath: String, destDir: File) {
        val children = context.assets.list(assetPath)
        if (children.isNullOrEmpty()) {
            runCatching {
                destDir.parentFile?.mkdirs()
                val tmp = File(destDir.parentFile, ".${destDir.name}.staged")
                context.assets.open(assetPath).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (destDir.exists()) destDir.deleteRecursively()
                if (!tmp.renameTo(destDir)) {
                    tmp.copyTo(destDir, overwrite = true)
                    tmp.delete()
                }
                if (assetPath.contains("/bin/") || destDir.parentFile?.name == "bin" || assetPath.endsWith(".sh") || destDir.name.startsWith("droiddeck-") || destDir.name == "innoextract" || destDir.name.endsWith(".so") || destDir.name.contains(".so.")) {
                    runCatching { Os.chmod(destDir.absolutePath, 493) }
                    destDir.setReadable(true, false)
                    destDir.setExecutable(true, false)
                }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) {
            copyAssetTree(context, "$assetPath/$child", File(destDir, child))
        }
    }

    private fun writeFileSafe(target: File, content: String) {
        runCatching {
            if (target.exists() && !target.isFile) target.delete()
            // Remove dangling symlink if present
            try {
                Os.lstat(target.absolutePath)
                if (!target.isFile) target.delete()
            } catch (_: Throwable) {
            }
            target.parentFile?.mkdirs()
            target.writeText(content, StandardCharsets.UTF_8)
        }
    }

    private fun writeIfMissing(target: File, content: String) {
        if (!target.isFile) {
            writeFileSafe(target, content)
        }
    }

    fun binds(
        context: Context,
        runtimeDir: File,
        extraBinds: List<String>? = null
    ): List<String> {
        ensureResolvAndAccounts(context)
        val root = rootDir(context)
        val binds = mutableListOf<String>()

        binds += "/dev"
        binds += "/dev/urandom:/dev/random"
        binds += "/proc/self/fd:/dev/fd"
        binds += "/proc/self/fd/0:/dev/stdin"
        binds += "/proc/self/fd/1:/dev/stdout"
        binds += "/proc/self/fd/2:/dev/stderr"
        val devShm = File(root, "tmp/shm").apply { mkdirs() }
        binds += "${devShm.absolutePath}:/dev/shm"
        if (File("/dev/pts").exists()) binds += "/dev/pts"

        val gpuNode = findHostGpuNode()
        if (gpuNode != null && !File("/dev/dri/renderD128").exists()) {
            binds += "$gpuNode:/dev/dri/renderD128"
        }

        binds += "/proc"
        val fakeProc = File(root, "etc/.proc")
        for (pair in listOf(
            "stat" to "/proc/stat",
            "version" to "/proc/version",
            "uptime" to "/proc/uptime",
            "loadavg" to "/proc/loadavg",
            "vmstat" to "/proc/vmstat",
            "cap_last_cap" to "/proc/sys/kernel/cap_last_cap",
        )) {
            val src = File(fakeProc, pair.first)
            if (src.isFile && !File(pair.second).canRead()) {
                binds += "${src.absolutePath}:${pair.second}"
            }
        }

        binds += "/sys"
        // Empty dir over /sys/fs/selinux so libselinux inside Ubuntu treats SELinux as disabled
        val selinuxEmpty = File(root, "var/empty").apply { mkdirs() }
        binds += "${selinuxEmpty.absolutePath}:/sys/fs/selinux"

        binds += "${runtimeDir.absolutePath}:$GUEST_RUNTIME_DIR"
        binds += "${runtimeDir.absolutePath}:${runtimeDir.absolutePath}"

        // Expose internal application storage files so PRoot and BwrapSpawner share host paths
        val appData = context.dataDir.absolutePath
        binds += "$appData:$appData"

        val sdcard = Environment.getExternalStorageDirectory()
        if (sdcard != null && sdcard.exists()) {
            binds += "${sdcard.absolutePath}:/sdcard"
            binds += "${sdcard.absolutePath}:${sdcard.absolutePath}"
        }

        extraBinds?.forEach { if (!binds.contains(it)) binds += it }
        return binds
    }

    private fun findHostGpuNode(): String? {
        for (p in listOf("/dev/kgsl-3d0", "/dev/mali0", "/dev/pvr_sync")) {
            if (File(p).exists()) return p
        }
        return null
    }

    /**
     * Sanitizes <rootfs>/etc/ld.so.preload so that ld.so never tries to preload missing shared libraries
     * like /usr/local/lib/libblfastpath.so, which causes glibc ld.so errors and crashes.
     */
    fun sanitizeLdSoPreload(rootfsDir: File) {
        val preloadFile = File(rootfsDir, "etc/ld.so.preload")
        if (!preloadFile.isFile) return
        val lines = runCatching { preloadFile.readLines(StandardCharsets.UTF_8) }.getOrNull() ?: return
        val validLines = mutableListOf<String>()
        var changed = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val target = File(rootfsDir, trimmed.removePrefix("/"))
            if (target.isFile) {
                validLines += trimmed
            } else {
                Timber.tag("LinuxRuntime").w("Removing missing preload entry from ld.so.preload: %s", trimmed)
                changed = true
            }
        }
        if (changed || validLines.size != lines.size) {
            val newContent = if (validLines.isEmpty()) "" else validLines.joinToString("\n") + "\n"
            writeFileSafe(preloadFile, newContent)
        }
    }

    /**
     * Automatic Rootfs `.l2s.` Coreutils Repair Step:
     * Ubuntu's Rust uutils-coreutils verifies that readlink("/proc/self/exe") matches argv[0].
     * When PRoot extraction created .l2s.<name>0001.xxxx backing files, readlink("/proc/self/exe")
     * returns .l2s.env0001.0116 instead of "env", causing "Security violation: Requested utility env does not match executable name".
     *
     * This function replaces any symlink in usr/lib/cargo/bin/coreutils/ and usr/bin/ that points to
     * a .l2s.* backing file with a real POSIX hardlink (or direct file copy if hardlinking fails)
     * and chmods it 0755 so /proc/self/exe is guaranteed by the Linux kernel to be the utility name itself.
     */
    fun repairCoreutilsL2s(rootfsDir: File) {
        if (!rootfsDir.isDirectory) return
        val candidateDirs = listOf(
            File(rootfsDir, "usr/lib/cargo/bin/coreutils"),
            File(rootfsDir, "usr/bin"),
            File(rootfsDir, "bin"),
        )

        val criticalUtils = setOf(
            "env", "ls", "uname", "basename", "dirname", "mkdir", "rm", "cp", "mv", "chmod", "chown",
            "cat", "head", "tail", "tr", "cut", "sort", "uniq", "wc", "id", "whoami", "sleep", "touch",
            "readlink", "realpath", "stat", "mktemp", "tee", "true", "false", "expr", "seq", "date",
            "dd", "df", "du", "ln", "nohup", "printenv", "printf", "pwd", "sync", "test", "timeout",
            "tty", "yes", "["
        )

        for (dir in candidateDirs) {
            if (!dir.isDirectory) continue
            val entries = dir.listFiles() ?: continue
            for (f in entries) {
                try {
                    val p = f.toPath()
                    if (Files.isSymbolicLink(p)) {
                        val symlinkTarget = Files.readSymbolicLink(p).toString()
                        val realFile = resolveRealTarget(f)
                        val isL2s = symlinkTarget.contains(".l2s.") || (realFile != null && realFile.name.contains(".l2s."))
                        val isCriticalInCargo = dir.name == "coreutils" && criticalUtils.contains(f.name)
                        if (isL2s || isCriticalInCargo) {
                            if (realFile != null && realFile.isFile) {
                                Files.delete(p)
                                var linked = false
                                try {
                                    Os.link(realFile.absolutePath, f.absolutePath)
                                    linked = true
                                } catch (_: Throwable) {
                                    try {
                                        Files.createLink(p, realFile.toPath())
                                        linked = true
                                    } catch (_: Throwable) {
                                        linked = false
                                    }
                                }
                                if (!linked) {
                                    realFile.copyTo(f, overwrite = true)
                                }
                                try {
                                    Os.chmod(f.absolutePath, 493) // 0755
                                } catch (_: Throwable) {
                                    f.setReadable(true, false)
                                    f.setExecutable(true, false)
                                }
                                Timber.tag("LinuxRuntime").d("Repaired .l2s. coreutils binary: %s", f.name)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Timber.tag("LinuxRuntime").w(e, "Failed repairing coreutils entry: %s", f.name)
                }
            }

            if (dir.name == "coreutils") {
                val coreutilsBin = File(dir, "coreutils")
                if (coreutilsBin.isFile) {
                    for (name in criticalUtils) {
                        val util = File(dir, name)
                        if (!util.exists() || Files.isSymbolicLink(util.toPath())) {
                            try {
                                if (Files.isSymbolicLink(util.toPath()) || util.exists()) {
                                    Files.delete(util.toPath())
                                }
                                var linked = false
                                try {
                                    Os.link(coreutilsBin.absolutePath, util.absolutePath)
                                    linked = true
                                } catch (_: Throwable) {
                                    try {
                                        Files.createLink(util.toPath(), coreutilsBin.toPath())
                                        linked = true
                                    } catch (_: Throwable) {
                                        linked = false
                                    }
                                }
                                if (!linked) {
                                    coreutilsBin.copyTo(util, overwrite = true)
                                }
                                try {
                                    Os.chmod(util.absolutePath, 493)
                                } catch (_: Throwable) {
                                    util.setReadable(true, false)
                                    util.setExecutable(true, false)
                                }
                            } catch (e: Throwable) {
                                Timber.tag("LinuxRuntime").w(e, "Failed ensuring critical coreutils utility: %s", name)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveRealTarget(file: File): File? {
        var cur = file
        for (i in 0 until 16) {
            try {
                if (!Files.isSymbolicLink(cur.toPath())) {
                    return if (cur.isFile) cur else null
                }
                val target = Files.readSymbolicLink(cur.toPath())
                cur = if (target.isAbsolute) target.toFile() else File(cur.parentFile, target.toString()).canonicalFile
            } catch (_: Throwable) {
                return null
            }
        }
        return if (cur.isFile) cur else null
    }

    fun prootPrefix(
        context: Context,
        root: File,
        cwd: String = "/root",
        fakeRoot: Boolean = true
    ): MutableList<String> {
        repairCoreutilsL2s(root)
        sanitizeLdSoPreload(root)
        val cmd = mutableListOf<String>()
        val proot = prootBinary(context)
        cmd += proot.absolutePath
        cmd += "--kill-on-exit"
        cmd += "--kernel-release=6.1.0-droiddeck"
        cmd += "--link2symlink"
        cmd += "-r"
        cmd += root.absolutePath
        if (fakeRoot) {
            cmd += "-0"
        }
        cmd += "-w"
        cmd += cwd
        cmd += "--sysvipc"
        return cmd
    }

    fun applyProotEnv(context: Context, env: MutableMap<String, String>) {
        env.remove("LD_PRELOAD")
        env.remove("LD_AUDIT")
        env.remove("LD_LIBRARY_PATH")
        env["PROOT_TMP_DIR"] = prootTempDir(context).absolutePath
        env["PROOT_L2S_DIR"] = l2sDir(context).absolutePath
        val loader = prootLoader(context)
        if (loader.isFile) {
            env["PROOT_LOADER"] = loader.absolutePath
        }
        env["PROOT_NO_SECCOMP"] = "1"
    }

    /**
     * Spawns the KDE Plasma / Linux Desktop environment inside `playground_rootfs` attached
     * to the active Winlator XServer socket (`tmp/.X11-unix/X0` -> `/tmp/.X11-unix/X0`).
     */
    fun launchDesktopOnXServer(context: Context, imageFsRoot: File) {
        Thread({
            runCatching {
                stageSessionFiles(context)
                BwrapSpawner.start(context)
                val root = rootDir(context)
                val runtimeDir = liveDir(context)
                val x11HostDir = File(imageFsRoot, "tmp/.X11-unix").apply { mkdirs() }
                val rootX11Dir = File(root, "tmp/.X11-unix").apply { mkdirs() }
                val cmd = prootPrefix(context, root, "/root", true)
                for (b in binds(context, runtimeDir, listOf("${x11HostDir.absolutePath}:/tmp/.X11-unix"))) {
                    cmd += "-b"
                    cmd += b
                }
                cmd += "/usr/bin/env"
                cmd += "-i"
                cmd += "HOME=/root"
                cmd += "USER=root"
                cmd += "LOGNAME=root"
                cmd += "DISPLAY=:0"
                cmd += "XDG_RUNTIME_DIR=$GUEST_RUNTIME_DIR"
                cmd += "PATH=$GUEST_PATH"
                cmd += "LANG=C.UTF-8"
                if (File("/dev/kgsl-3d0").exists()) {
                    cmd += "MESA_LOADER_DRIVER_OVERRIDE=zink"
                }
                cmd += "MOZ_DISABLE_CONTENT_SANDBOX=1"
                cmd += "MOZ_DISABLE_GMP_SANDBOX=1"
                cmd += "ZYPAK_ZYGOTE_STRATEGY_SPAWN=0"
                cmd += "/bin/bash"
                cmd += "-lc"
                cmd += "if command -v startplasma-x11 >/dev/null 2>&1; then exec startplasma-x11; elif command -v plasmashell >/dev/null 2>&1; then (kwin_x11 --replace &); exec plasmashell; elif [ -x /usr/local/bin/droiddeck-desktop ]; then exec /usr/local/bin/droiddeck-desktop; else exec /bin/bash; fi"
                val pb = ProcessBuilder(cmd)
                pb.directory(root)
                applyProotEnv(context, pb.environment())
                pb.redirectErrorStream(true)
                val logFile = File(logDir(context), "desktop-session.log")
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                pb.start()
                Timber.tag("LinuxRuntime").i("Launched KDE Plasma / Linux Desktop session on DISPLAY=:0")
            }.onFailure { e ->
                Timber.tag("LinuxRuntime").w(e, "Failed to launch Linux Desktop session on XServer")
            }
        }, "LinuxDesktopSpawner").apply { isDaemon = true }.start()
    }
}
