package app.gamenative.playground

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Installs and manages the unified DroidDeck Ubuntu arm64 rootfs (`linuxfs-ubuntu.json`)
 * and optional KDE Plasma Desktop addon (`desktop-kde-r3`) into `files/playground_rootfs/`.
 *
 * Uses DroidDeck's symlink + `.l2s.` link2symlink hardlink extraction so that dynamic linkers
 * (`/lib/ld-linux-aarch64.so.1`, `/bin/sh`, etc.), Flatpak, AppImage, and Xbox `xodus-cli` /
 * `xodus-service` execute identically to DroidDeck.
 */
object LinuxRuntimeInstaller {
    const val CATALOG_URL =
        "https://raw.githubusercontent.com/Droid-Deck/DroidDeck-Components/main/linuxfs-ubuntu.json"
    const val DESKTOP_KDE_TAG = "desktop-kde-r3"
    const val DESKTOP_KDE_URL =
        "https://github.com/Droid-Deck/DroidDeck-Components/releases/download/desktop-kde-r3/desktop-kde.tar.zst"
    const val DESKTOP_KDE_SHA256 = "8b7142fcec8d86143ae39fc14ffc86907d30d9a5b5a45f49cb1b55109d6fc4eb"
    const val DESKTOP_KDE_SIZE = 972598429L

    private const val VERSION_FILE = ".version"
    private val installLock = Any()

    data class ReleaseInfo(
        val version: String,
        val url: String,
        val sha256: String?,
        val sizeBytes: Long,
    )

    sealed interface InstallState {
        data object Idle : InstallState
        data class Downloading(
            val progress: Float,
            val label: String = "Downloading Linux runtime...",
            val downloadedBytes: Long = 0L,
            val totalBytes: Long = 0L,
        ) : InstallState
        data class Extracting(val label: String = "Extracting Linux runtime...") : InstallState
        data object Ready : InstallState
        data class Failed(val error: String) : InstallState
    }

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    fun isInstalled(context: Context): Boolean = LinuxRuntime.isInstalled(context)

    fun isDesktopInstalled(context: Context): Boolean = LinuxRuntime.isDesktopInstalled(context)

    fun installedVersion(context: Context): String? {
        val f = File(LinuxRuntime.rootDir(context), VERSION_FILE)
        return if (f.isFile) f.readText().trim() else null
    }

    fun fetchRelease(): ReleaseInfo {
        val conn = URL(CATALOG_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("Failed to fetch Linux catalog (HTTP ${conn.responseCode})")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val url = json.getString("url")
            if (!url.startsWith("https://")) {
                throw IOException("Refusing non-HTTPS rootfs URL: $url")
            }
            return ReleaseInfo(
                version = json.optString("version", "latest"),
                url = url,
                sha256 = json.optString("sha256", "").takeIf { it.isNotEmpty() },
                sizeBytes = json.optLong("size", 0L),
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Blocking helper called by both Xbox (`Xodus.stage`) and PC Playground before spawning
     * PRoot processes. Guarantees the base Ubuntu rootfs, DNS/accounts, and session scripts
     * are installed and staged.
     */
    fun ensureInstalledBlocking(context: Context): Boolean = synchronized(installLock) {
        if (LinuxRuntime.isInstalled(context)) {
            LinuxRuntime.stageSessionFiles(context)
            _state.value = InstallState.Ready
            return true
        }
        return try {
            val release = fetchRelease()
            val root = LinuxRuntime.rootDir(context)
            val cacheArchive = File(context.cacheDir, "linuxfs-ubuntu.tar.zst")
            downloadFile(
                url = release.url,
                dest = cacheArchive,
                expectedSha256 = release.sha256,
                expectedSize = release.sizeBytes,
                label = "Downloading Ubuntu base runtime...",
            )
            _state.value = InstallState.Extracting("Extracting Ubuntu base runtime...")
            extractTarZstIntoRoot(context, cacheArchive, root)
            cacheArchive.delete()
            File(root, VERSION_FILE).writeText(release.version)
            LinuxRuntime.stageSessionFiles(context)
            _state.value = InstallState.Ready
            true
        } catch (e: Exception) {
            Timber.tag("LinuxRuntimeInstaller").e(e, "Failed to install base Ubuntu runtime")
            _state.value = InstallState.Failed(e.message ?: "Failed to install Linux runtime")
            false
        }
    }

    /**
     * Installs both the base Ubuntu rootfs (if not yet installed) and the KDE Plasma Desktop
     * addon (`desktop-kde-r3`), staging all DroidDeck session scripts afterward.
     */
    suspend fun installDesktopEnvironment(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!ensureInstalledBlocking(context)) {
            return@withContext false
        }
        synchronized(installLock) {
            if (LinuxRuntime.isDesktopInstalled(context)) {
                LinuxRuntime.stageSessionFiles(context)
                _state.value = InstallState.Ready
                return@synchronized true
            }
            val root = LinuxRuntime.rootDir(context)
            val cacheArchive = File(context.cacheDir, "desktop-kde.tar.zst")
            try {
                downloadFile(
                    url = DESKTOP_KDE_URL,
                    dest = cacheArchive,
                    expectedSha256 = DESKTOP_KDE_SHA256,
                    expectedSize = DESKTOP_KDE_SIZE,
                    label = "Downloading KDE Plasma Desktop...",
                )
                _state.value = InstallState.Extracting("Extracting KDE Plasma Desktop...")
                extractTarZstIntoRoot(context, cacheArchive, root)
                cacheArchive.delete()
                File(root, LinuxRuntime.DESKTOP_MARKER).writeText(DESKTOP_KDE_TAG + "\n")
                File(root, LinuxRuntime.DESKTOP_PKG_MARKER).writeText(DESKTOP_KDE_TAG + "\n")
                LinuxRuntime.stageSessionFiles(context)
                _state.value = InstallState.Ready
                true
            } catch (e: Exception) {
                Timber.tag("LinuxRuntimeInstaller").e(e, "Failed to install KDE Plasma Desktop addon")
                cacheArchive.delete()
                _state.value = InstallState.Failed(e.message ?: "Desktop installation failed")
                false
            }
        }
    }

    suspend fun installBundledRootfs(context: Context, inputStream: InputStream): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(installLock) {
                _state.value = InstallState.Extracting("Extracting bundled rootfs...")
                val targetDir = LinuxRuntime.rootDir(context).apply { mkdirs() }
                try {
                    extractStreamIntoRoot(context, inputStream, targetDir)
                    File(targetDir, VERSION_FILE).writeText("1.0.0-bundled")
                    LinuxRuntime.stageSessionFiles(context)
                    _state.value = InstallState.Ready
                    true
                } catch (e: Exception) {
                    Timber.tag("LinuxRuntimeInstaller").e(e, "Bundled rootfs extraction failed")
                    _state.value = InstallState.Failed(e.message ?: "Extraction failed")
                    false
                }
            }
        }

    suspend fun downloadAndInstall(context: Context, downloadUrl: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (downloadUrl.isNullOrBlank()) {
                return@withContext ensureInstalledBlocking(context)
            }
            synchronized(installLock) {
                val root = LinuxRuntime.rootDir(context).apply { mkdirs() }
                val archive = File(context.cacheDir, "rootfs-custom.tar.zst")
                try {
                    downloadFile(
                        url = downloadUrl,
                        dest = archive,
                        expectedSha256 = null,
                        expectedSize = 0L,
                        label = "Downloading Linux runtime...",
                    )
                    _state.value = InstallState.Extracting("Extracting Linux runtime...")
                    extractTarZstIntoRoot(context, archive, root)
                    archive.delete()
                    File(root, VERSION_FILE).writeText("custom")
                    LinuxRuntime.stageSessionFiles(context)
                    _state.value = InstallState.Ready
                    true
                } catch (e: Exception) {
                    Timber.tag("LinuxRuntimeInstaller").e(e, "downloadAndInstall failed")
                    archive.delete()
                    _state.value = InstallState.Failed(e.message ?: "Download/install failed")
                    false
                }
            }
        }

    private fun downloadFile(
        url: String,
        dest: File,
        expectedSha256: String?,
        expectedSize: Long,
        label: String,
    ) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, "${dest.name}.part")
        if (part.exists()) part.delete()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("HTTP ${conn.responseCode} downloading $url")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 }
                ?: expectedSize.takeIf { it > 0 }
                ?: 240_000_000L
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastEmit = 0L

            _state.value = InstallState.Downloading(0f, label, 0L, total)
            conn.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 100L) {
                            lastEmit = now
                            val effectiveTotal = total.coerceAtLeast(downloaded)
                            val frac = if (effectiveTotal > 0) {
                                (downloaded.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            _state.value = InstallState.Downloading(frac, label, downloaded, effectiveTotal)
                        }
                    }
                }
            }

            if (!expectedSha256.isNullOrBlank()) {
                val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHex.equals(expectedSha256, ignoreCase = true)) {
                    part.delete()
                    throw IOException("SHA-256 mismatch for ${dest.name}: expected $expectedSha256, got $actualHex")
                }
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                throw IOException("Failed to rename ${part.name} to ${dest.name}")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Extracts a `.tar.zst` archive into `destDir` preserving symlinks, executable permissions,
     * and PRoot `.l2s.` link2symlink hardlinks (ported from DroidDeck's `LinuxRuntimeInstaller.extract`).
     */
    private fun extractTarZstIntoRoot(context: Context, archive: File, destDir: File) {
        BufferedInputStream(FileInputStream(archive), 64 * 1024).use { bis ->
            extractStreamIntoRoot(context, bis, destDir)
        }
    }

    private fun extractStreamIntoRoot(context: Context, rawStream: InputStream, destDir: File) {
        destDir.mkdirs()
        val l2sDir = LinuxRuntime.l2sDir(context)
        val rootCanonical = destDir.canonicalPath + File.separator
        val deferredLinks = mutableListOf<Pair<File, File>>()
        val buf = ByteArray(64 * 1024)

        ZstdCompressorInputStream(rawStream).use { zstd ->
            TarArchiveInputStream(zstd).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    var name = entry.name
                    if (name.startsWith("./")) name = name.substring(2)
                    while (name.startsWith("/")) name = name.substring(1)
                    if (name.isEmpty()) {
                        entry = tar.nextTarEntry
                        continue
                    }

                    val out = File(destDir, name)
                    val outCanonical = out.canonicalPath
                    if (!outCanonical.startsWith(rootCanonical) && outCanonical != destDir.canonicalPath) {
                        throw IOException("Blocked path traversal entry in tar: ${entry.name}")
                    }

                    when {
                        entry.isDirectory -> {
                            out.mkdirs()
                        }
                        entry.isSymbolicLink -> {
                            out.parentFile?.mkdirs()
                            deleteExistingOrSymlink(out)
                            val linkTarget = entry.linkName
                            try {
                                Files.createSymbolicLink(out.toPath(), File(linkTarget).toPath())
                            } catch (_: Throwable) {
                                runCatching { Os.symlink(linkTarget, out.absolutePath) }
                            }
                        }
                        entry.isLink -> {
                            out.parentFile?.mkdirs()
                            var linkName = entry.linkName
                            if (linkName.startsWith("./")) linkName = linkName.substring(2)
                            while (linkName.startsWith("/")) linkName = linkName.substring(1)
                            val target = File(destDir, linkName)
                            val targetCanonical = target.canonicalPath
                            if (!targetCanonical.startsWith(rootCanonical)) {
                                throw IOException("Blocked hardlink target outside root: ${entry.linkName}")
                            }
                            deferredLinks.add(out to target)
                        }
                        entry.isFile -> {
                            out.parentFile?.mkdirs()
                            val tmp = File(out.parentFile, ".dd-part-${out.name}")
                            deleteExistingOrSymlink(tmp)
                            FileOutputStream(tmp).use { fos ->
                                while (true) {
                                    val r = tar.read(buf)
                                    if (r < 0) break
                                    fos.write(buf, 0, r)
                                }
                            }
                            if ((entry.mode and 73) != 0) { // 0111 octal executable mask
                                tmp.setExecutable(true, false)
                            }
                            tmp.setReadable(true, false)
                            deleteExistingOrSymlink(out)
                            if (!tmp.renameTo(out)) {
                                tmp.copyTo(out, overwrite = true)
                                tmp.delete()
                            }
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }

        // Resolve hardlinks after all regular files & symlinks exist
        for ((out, target) in deferredLinks) {
            deleteExistingOrSymlink(out)
            try {
                Files.createLink(out.toPath(), target.toPath())
            } catch (_: Throwable) {
                try {
                    l2sLink(l2sDir, target, out)
                } catch (_: Throwable) {
                    if (target.isFile) {
                        runCatching {
                            target.copyTo(out, overwrite = true)
                            if (target.canExecute()) out.setExecutable(true, false)
                        }
                    }
                }
            }
        }
        LinuxRuntime.repairCoreutilsL2s(destDir)
        LinuxRuntime.sanitizeLdSoPreload(destDir)
    }

    /**
     * Port of DroidDeck's `l2sLink()` for PRoot's `link2symlink` extension when Android SELinux
     * blocks `link(2)` in app data directories.
     */
    private fun l2sLink(l2sDir: File, target: File, link: File) {
        val targetPath = target.toPath()
        if (Files.isSymbolicLink(l2sDir.toPath())) {
            throw IOException("Refusing symlinked l2s directory")
        }
        val backing: File = if (Files.isSymbolicLink(targetPath)) {
            val read = Files.readSymbolicLink(targetPath)
            val resolved = if (read.isAbsolute) read.toFile() else File(target.parentFile, read.toString())
            if (resolved.parentFile?.canonicalFile == l2sDir.canonicalFile) {
                resolved
            } else {
                createL2sBacking(l2sDir, target)
            }
        } else {
            createL2sBacking(l2sDir, target)
        }
        deleteExistingOrSymlink(link)
        Files.createSymbolicLink(link.toPath(), backing.toPath())
    }

    private fun createL2sBacking(l2sDir: File, target: File): File {
        var idx = 0
        var backing: File
        do {
            val suffix = ".%04d".format(idx++)
            val rawName = target.name
            val baseName = if (rawName.length > 200) rawName.substring(0, 200) else rawName
            backing = File(l2sDir, ".l2s.$baseName$suffix")
        } while (backing.exists() || Files.isSymbolicLink(backing.toPath()))

        if (!target.renameTo(backing)) {
            target.copyTo(backing, overwrite = true)
            if (target.canExecute()) backing.setExecutable(true, false)
            target.delete()
        }
        Files.createSymbolicLink(target.toPath(), backing.toPath())
        return backing
    }

    private fun deleteExistingOrSymlink(f: File) {
        try {
            Files.deleteIfExists(f.toPath())
        } catch (_: Throwable) {
            f.delete()
        }
    }
}
