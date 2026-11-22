package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.gamenative.PrefManager
import app.gamenative.ui.components.getPathFromTreeUri as resolveTreeUriPath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber

object StorageUtils {

    fun getAvailableSpace(path: String): Long {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Invalid path: $path")
        }
        val stat = StatFs(path)
        return stat.blockSizeLong * stat.availableBlocksLong
    }

    fun openSystemFileManager(context: Context, folderPath: String) {
        try {
            val file = File(folderPath)
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volume = storageManager.getStorageVolume(file)
            val rootId = if (volume?.isPrimary == true) "primary" else volume?.uuid

            var uri: Uri? = null

            // Check if it's our internal provider first
            val appDataRoot = context.dataDir.absolutePath
            val authority = "${context.packageName}.documents"
            if (folderPath.startsWith(appDataRoot)) {
                val relPath = folderPath.removePrefix(appDataRoot).trimStart('/')
                val docId = if (relPath.isEmpty()) "root" else "root:$relPath"
                uri = DocumentsContract.buildTreeDocumentUri(authority, docId)
            } else if (rootId != null && volume != null) {
                val volumePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory?.absolutePath
                } else {
                    try {
                        val getPath = volume.javaClass.getMethod("getPath")
                        getPath.invoke(volume) as? String
                    } catch (e: Exception) {
                        null
                    }
                }

                if (volumePath != null && folderPath.startsWith(volumePath)) {
                    val relPath = folderPath.substring(volumePath.length).trimStart('/')
                    val docId = "$rootId:$relPath"
                    // Use tree URI for navigation
                    uri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", docId)
                }
            }

            // Fallback for primary storage if volume detection failed but we know the path
            if (uri == null && folderPath.startsWith("/storage/emulated/0")) {
                val relPath = folderPath.substring("/storage/emulated/0".length).trimStart('/')
                uri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:$relPath")
            }

            val browseIntent = Intent("android.provider.action.BROWSE").apply {
                data = uri ?: Uri.fromFile(file)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (uri != null) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }

            if (browseIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(browseIntent)
            } else {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    if (uri != null) {
                        setDataAndType(uri, "vnd.android.document/directory")
                    } else {
                        setDataAndType(Uri.fromFile(file), "vnd.android.document/directory")
                    }
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
                context.startActivity(viewIntent)
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "Failed to open system file manager fallback")
            }
        }
    }

    /**
     * Free space on the volume that would contain [path], even if [path] doesn't exist yet
     * (e.g. a game's install directory before the first download has created it). Walks up to
     * the nearest existing ancestor, which is on the same volume, since [StatFs] needs an
     * existing path. Throws only if no ancestor exists (e.g. a blank/invalid path).
     */
    fun getAvailableSpaceForUncreatedPath(path: String): Long {
        var file: File? = File(path)
        while (file != null && !file.exists()) {
            file = file.parentFile
        }
        if (file == null) {
            throw IllegalArgumentException("Invalid path: $path")
        }
        val stat = StatFs(file.path)
        return stat.blockSizeLong * stat.availableBlocksLong
    }

    // Minimum internal free space required to host a transient download chunk cache.
    // The cache normally stays MB-sized (chunks are deleted as they are assembled),
    // so this only guards against starting a download on a nearly-full data partition.
    private const val MIN_INTERNAL_CACHE_BYTES = 512L * 1024 * 1024

    /**
     * Pre-download disk space check shared by the GOG and Epic download managers.
     * Returns a human-readable error when there is not enough space, or null when
     * the download can proceed. [requiredBytes] is checked against the install
     * volume; the internal volume hosting [internalCacheDir] only needs modest
     * headroom for the transient chunk cache.
     */
    fun downloadSpaceShortfall(installDir: File, requiredBytes: Long, internalCacheDir: File): String? {
        val available = getAvailableSpaceForUncreatedPath(installDir.absolutePath)
        if (available < requiredBytes) {
            return "Not enough free space: need ${formatBinarySize(requiredBytes)}, available ${formatBinarySize(available)}"
        }
        val internalAvailable = getAvailableSpaceForUncreatedPath(internalCacheDir.absolutePath)
        if (internalAvailable < MIN_INTERNAL_CACHE_BYTES) {
            return "Not enough internal storage for the download cache: " +
                "${formatBinarySize(internalAvailable)} free, need at least ${formatBinarySize(MIN_INTERNAL_CACHE_BYTES)}"
        }
        return null
    }

    fun getTotalSpace(path: String): Long {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Invalid path: $path")
        }
        val stat = StatFs(path)
        return stat.blockSizeLong * stat.blockCountLong
    }

    suspend fun getFolderSize(folderPath: String): Long {
        val folder = File(folderPath)
        if (!folder.exists()) return 0L
        val job = currentCoroutineContext()[Job]
        val bytes = directorySizeOnDisk(folder.toPath()) { job?.isActive != false }
        job?.ensureActive()
        return bytes
    }

    /**
     * Bytes of regular files under [root]. Symlinks (file or directory) are never followed
     * or counted, so a link that points back into the tree (GOG's `_CommonRedist/ISI/rootdir`)
     * or out to Workshop content cannot inflate the result.
     *
     * A file with several hard links is counted once, and only if every one of its links lives
     * inside the walked tree: an inode is added when the number of links seen reaches `nlink`.
     * Links that are shared with something outside (every container hard-links Proton, DXVK,
     * VKD3D, FEX and WowBox64 files from a shared store) therefore count zero, as before.
     *
     * [shouldContinue] is polled per file; returning false stops the walk early.
     */
    fun directorySizeOnDisk(root: Path, shouldContinue: () -> Boolean = { true }): Long {
        // The root itself may legitimately be a symlink (callers pass whatever path the store
        // recorded); resolve it so the walk below, which never follows links, still descends.
        val start = runCatching { root.toRealPath() }.getOrDefault(root)
        if (!Files.isDirectory(start)) return 0L

        var totalBytes = 0L
        val linksSeen = HashMap<Any, Int>()
        runCatching {
            Files.walkFileTree(
                start,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (!shouldContinue()) return FileVisitResult.TERMINATE
                        if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                        val nlink = hardLinkCount(file)
                        if (nlink > 1) {
                            val key = attrs.fileKey() ?: inodeKey(file) ?: return FileVisitResult.CONTINUE
                            val seen = (linksSeen[key] ?: 0) + 1
                            linksSeen[key] = seen
                            if (seen != nlink) return FileVisitResult.CONTINUE
                        }
                        totalBytes += attrs.size()
                        return FileVisitResult.CONTINUE
                    }

                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        return if (shouldContinue()) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                        Timber.w(exc, "Failed to size file: $file")
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }.onFailure {
            Timber.w(it, "Failed to calculate size for $root")
        }

        return totalBytes
    }

    private fun hardLinkCount(path: Path): Int =
        runCatching { (Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS) as Number).toInt() }
            .getOrDefault(1)

    private fun inodeKey(path: Path): Pair<Long, Long>? =
        runCatching {
            val dev = (Files.getAttribute(path, "unix:dev", LinkOption.NOFOLLOW_LINKS) as Number).toLong()
            val ino = (Files.getAttribute(path, "unix:ino", LinkOption.NOFOLLOW_LINKS) as Number).toLong()
            dev to ino
        }.getOrNull()

    fun formatBinarySize(bytes: Long, decimalPlaces: Int = 2): String {
        require(bytes > Long.MIN_VALUE) { "Out of range" }
        require(decimalPlaces >= 0) { "Negative decimal places unsupported" }

        val isNegative = bytes < 0
        val absBytes = abs(bytes)

        if (absBytes < 1024) {
            return "$bytes B"
        }

        val units = arrayOf("KiB", "MiB", "GiB", "TiB", "PiB")
        val digitGroups = (63 - absBytes.countLeadingZeroBits()) / 10
        val value = absBytes.toDouble() / (1L shl (digitGroups * 10))

        val result = "%.${decimalPlaces}f %s".format(
            if (isNegative) -value else value,
            units[digitGroups - 1],
        )

        return result
    }

    private const val PUBLIC_INSTALL_DIR_NAME = "GameNative"

    /**
     * Maps an app-specific dir (<volume>/Android/data/<pkg>/files) to a public install root
     * (<volume>/GameNative). MediaProvider disables FUSE kernel caching under Android/data,
     * making per-open metadata ops ~1000x slower there; public dirs get normal dcache treatment.
     */
    fun publicInstallRoot(appFilesDir: File): File? {
        val path = appFilesDir.absolutePath
        val idx = path.indexOf("/Android/data/")
        if (idx <= 0) return null
        return File(path.substring(0, idx), PUBLIC_INSTALL_DIR_NAME)
    }

    fun ensureInstallRoot(dir: File): Boolean {
        if (!dir.isDirectory && !dir.mkdirs()) return false
        runCatching { File(dir, ".nomedia").createNewFile() }
        return true
    }

    fun preferredInstallRoot(appFilesDir: File): String {
        val public = publicInstallRoot(appFilesDir)
        if (public != null && ensureInstallRoot(public)) return public.absolutePath
        return appFilesDir.absolutePath
    }

    fun resolveLegacyGameDir(path: String?): String? {
        if (path.isNullOrBlank()) return path
        val idx = path.indexOf("/Android/data/")
        if (idx <= 0) return path
        val filesIdx = path.indexOf("/files/", idx)
        if (filesIdx < 0) return path
        val legacyRoot = File(path.substring(0, filesIdx + "/files".length))
        val rel = path.substring(filesIdx + "/files/".length)
        val src = File(path)
        val publicRoot = publicInstallRoot(legacyRoot) ?: return path
        val dst = File(publicRoot, rel)
        if (!src.isDirectory) return if (dst.isDirectory) dst.absolutePath else path
        if (dst.exists() || !ensureInstallRoot(publicRoot)) return path
        dst.parentFile?.mkdirs()
        return if (src.renameTo(dst)) {
            Timber.i("Migrated game dir $path to ${dst.absolutePath}")
            dst.absolutePath
        } else {
            Timber.w("Could not migrate $path; leaving in place")
            path
        }
    }

    /**
     * Returns whether [appFilesDir] should be offered as a separate install target.
     *
     * Primary storage is not always backed by built-in storage. When Android migrates primary
     * shared storage to adopted media, the SD-backed volume remains primary but has a non-default
     * storage UUID.
     */
    fun isExternalInstallTarget(storageManager: StorageManager?, appFilesDir: File): Boolean {
        val volume = storageManager?.getStorageVolume(appFilesDir)
            ?: return runCatching { Environment.isExternalStorageRemovable(appFilesDir) }.getOrDefault(false)
        if (!volume.isPrimary) return true

        val resolvedStorageUuid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            volume.storageUuid
        } else {
            try {
                storageManager.getUuidForPath(appFilesDir)
            } catch (_: Exception) {
                null
            }
        }

        return isNonDefaultPrimaryStorage(
            resolvedStorageUuid = resolvedStorageUuid,
            legacyVolumeUuid = volume.uuid,
            isPhysicalPrimary = volume.isRemovable && !volume.isEmulated,
            allowLegacyUuidFallback = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
            defaultStorageUuid = StorageManager.UUID_DEFAULT,
        )
    }

    internal fun isNonDefaultPrimaryStorage(
        resolvedStorageUuid: java.util.UUID?,
        legacyVolumeUuid: String?,
        isPhysicalPrimary: Boolean,
        allowLegacyUuidFallback: Boolean,
        defaultStorageUuid: java.util.UUID,
    ): Boolean {
        if (resolvedStorageUuid != null) return resolvedStorageUuid != defaultStorageUuid
        if (isPhysicalPrimary) return true
        return allowLegacyUuidFallback && !legacyVolumeUuid.isNullOrBlank()
    }

    /**
     * Gets all app-specific external files directories, using StorageManager as a fallback
     * for cases where context.getExternalFilesDirs(null) might return null or incomplete results
     * (e.g. USB OTG drives, which most devices omit from getExternalFilesDirs).
     */
    fun getAllExternalFilesDirs(context: Context): List<File> {
        val result = mutableSetOf<File>()

        // 1. Primary source: Standard Android API
        try {
            context.getExternalFilesDirs(null)?.filterNotNull()?.let {
                result.addAll(it)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calling getExternalFilesDirs")
        }

        // 2. Fallback: Iterate through all storage volumes using StorageManager
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (sm != null) {
            try {
                for (volume in sm.storageVolumes) {
                    if (volume.state != Environment.MEDIA_MOUNTED) continue

                    val volumeDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else {
                        // Use reflection for older APIs (26-29) if getExternalFilesDirs missed it
                        try {
                            val getPath = volume.javaClass.getMethod("getPath")
                            (getPath.invoke(volume) as? String)?.let { File(it) }
                        } catch (re: Exception) {
                            null
                        }
                    } ?: continue

                    // The app-specific dedicated directory is /Android/data/<package_name>/files
                    val appFilesDir = File(volumeDir, "Android/data/${context.packageName}/files")
                    if (!result.contains(appFilesDir) && (appFilesDir.exists() || appFilesDir.mkdirs())) {
                        result.add(appFilesDir)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error iterating storage volumes in fallback")
            }
        }

        return result.toList()
    }

    /**
     * Tries to resolve a raw path to a fast SAF URI using rootId:relPath pattern.
     * Bypasses slow recursive directory listings.
     */
    fun getFastDocumentUri(context: Context, path: String): Uri? {
        val appDataRoot = context.dataDir.absolutePath
        val authority = "${context.packageName}.documents"
        if (path.startsWith(appDataRoot)) {
            val relPath = path.removePrefix(appDataRoot).trimStart('/')
            val docId = if (relPath.isEmpty()) "root" else "root:$relPath"
            return DocumentsContract.buildDocumentUri(authority, docId)
        }

        val safUri: String = PrefManager.externalStoragePath.takeIf { it.startsWith("content://") } ?: return null
        val treeRoot = getPathFromTreeUri(context, safUri.toUri()) ?: return null

        if (path.startsWith(treeRoot)) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            val file = File(path)
            val volume = sm?.getStorageVolume(file)
            val rootId = if (volume?.isPrimary == true) "primary" else volume?.uuid ?: return null

            val relPath = path.removePrefix(treeRoot).trimStart('/')
            val docId = "$rootId:$relPath"
            return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        }
        return null
    }

    /**
     * Resolves a DocumentTree URI to a direct filesystem path if possible.
     * This allows the app to use high-performance java.io.File APIs on USB OTG
     * drives when broad storage permissions are available.
     */
    fun getPathFromTreeUri(context: Context, uri: Uri): String? {
        // Prefer CustomGameFolderPicker volume resolver
        val resolved = resolveTreeUriPath(context, uri)
        if (resolved != null) return resolved

        val docId = try {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.getDocumentId(uri)
            } else {
                treeId
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val split = docId.split(":")
        val type = split[0]
        val relativePath = if (split.size > 1) split[1] else ""

        return if ("primary".equals(type, ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory().absolutePath
            if (relativePath.isNotEmpty()) "$base/$relativePath" else base
        } else {
            // Check all storage volumes to match the UUID/Type
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            sm?.storageVolumes?.forEach { volume ->
                if (volume.uuid != null && volume.uuid.equals(type, ignoreCase = true)) {
                    val volumeDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else {
                        try {
                            val getPath = volume.javaClass.getMethod("getPath")
                            (getPath.invoke(volume) as? String)?.let { File(it) }
                        } catch (re: Exception) {
                            null
                        }
                    }
                    if (volumeDir != null) {
                        return if (relativePath.isNotEmpty()) {
                            volumeDir.absolutePath + "/" + relativePath
                        } else {
                            volumeDir.absolutePath
                        }
                    }
                }
            }

            // Fallback for some devices where UUID isn't used or matches differently
            val fallbackPath = "/storage/$type"
            if (File(fallbackPath).exists()) {
                return if (relativePath.isNotEmpty()) "$fallbackPath/$relativePath" else fallbackPath
            }
            null
        }
    }

    /**
     * Resolves a raw string (which may be a filesystem path, content URI, or tree URI)
     * to a direct filesystem path accessible by File and native I/O.
     */
    fun resolveStoragePath(context: Context, pathOrUri: String?): String? {
        if (pathOrUri.isNullOrBlank()) return null
        if (pathOrUri.startsWith("content://")) {
            val parsedUri = runCatching { pathOrUri.toUri() }.getOrNull() ?: return null
            return getPathFromTreeUri(context, parsedUri)
        }
        return pathOrUri
    }

    /**
     * Returns available space on the volume containing [path] in bytes.
     */
    fun getAvailableSpaceGB(context: Context, path: String): Long {
        val resolved = resolveStoragePath(context, path) ?: path
        return try {
            getAvailableSpaceForUncreatedPath(resolved)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Resolves a relative path to a DocumentFile within a given root DocumentFile,
     * creating directories as needed.
     */
    fun getDocumentFile(
        root: DocumentFile,
        relativePath: String,
        createIfMissing: Boolean = false,
        isDirectory: Boolean = false,
    ): DocumentFile? {
        val segments = relativePath.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        var current = root

        for (i in segments.indices) {
            val segment = segments[i]
            val last = i == segments.lastIndex
            var next = current.findFile(segment)

            if (next == null) {
                if (createIfMissing) {
                    next = if (!last || isDirectory) {
                        current.createDirectory(segment)
                    } else {
                        current.createFile("application/octet-stream", segment)
                    }
                } else {
                    return null
                }
            }
            current = next ?: return null
        }
        return current
    }

    /**
     * Opens a ParcelFileDescriptor for a file at the given relative path within a SAF root.
     */
    fun openSafFileDescriptor(
        context: Context,
        rootUri: Uri,
        relativePath: String,
        mode: String = "rw",
    ): ParcelFileDescriptor? {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val doc = getDocumentFile(root, relativePath, createIfMissing = mode.contains('w'))
        return doc?.let { context.contentResolver.openFileDescriptor(it.uri, mode) }
    }

    /**
     * Opens a FileChannel for a file, using SAF if it's a content URI or an external path
     * where direct IO might fail.
     */
    fun openFileChannel(
        context: Context,
        path: String,
        vararg options: OpenOption,
    ): FileChannel {
        if (path.startsWith("content://")) {
            val mode = if (options.contains(StandardOpenOption.APPEND)) {
                "wa"
            } else if (options.contains(StandardOpenOption.WRITE)) {
                "rw"
            } else {
                "r"
            }
            val pfd = context.contentResolver.openFileDescriptor(path.toUri(), mode)
                ?: throw IOException("Could not open file descriptor for $path")
            return FileOutputStream(pfd.fileDescriptor).channel
        }

        // Try direct IO first
        return try {
            FileChannel.open(Paths.get(path), *options)
        } catch (e: IOException) {
            // Fast Path: Try to resolve SAF URI directly via rootId:relPath
            val directSafUri = getFastDocumentUri(context, path)
            if (directSafUri != null) {
                val mode = if (options.contains(StandardOpenOption.WRITE)) "rw" else "r"
                val pfd = context.contentResolver.openFileDescriptor(directSafUri, mode)
                if (pfd != null) return FileOutputStream(pfd.fileDescriptor).channel
            }

            // Fallback: slow recursive search
            val safUri: String? = PrefManager.externalStoragePath.takeIf { it.startsWith("content://") }
            if (safUri != null) {
                val treeRoot = getPathFromTreeUri(context, safUri.toUri())
                if (treeRoot != null && path.startsWith(treeRoot)) {
                    val relPath = path.removePrefix(treeRoot).trimStart('/')
                    val pfd = openSafFileDescriptor(context, safUri.toUri(), relPath, "rw")
                    if (pfd != null) return FileOutputStream(pfd.fileDescriptor).channel
                }
            }
            throw e
        }
    }

    /**
     * Opens an OutputStream for a file, using SAF if it's a content URI or an external path
     * where direct IO might fail.
     */
    fun openOutputStream(
        context: Context,
        path: String,
        append: Boolean = false,
    ): OutputStream {
        if (path.startsWith("content://")) {
            val mode = if (append) "wa" else "wt"
            return context.contentResolver.openOutputStream(path.toUri(), mode)
                ?: throw IOException("Could not open output stream for $path")
        }

        val file = File(path)
        return try {
            FileOutputStream(file, append)
        } catch (e: IOException) {
            // Fast Path: Try to resolve SAF URI directly
            val directSafUri = getFastDocumentUri(context, path)
            if (directSafUri != null) {
                val mode = if (append) "wa" else "wt"
                val os = context.contentResolver.openOutputStream(directSafUri, mode)
                if (os != null) return os
            }

            val safUri: String? = PrefManager.externalStoragePath.takeIf { it.startsWith("content://") }
            if (safUri != null) {
                val treeRoot = getPathFromTreeUri(context, safUri.toUri())
                if (treeRoot != null && path.startsWith(treeRoot)) {
                    val relPath = path.removePrefix(treeRoot).trimStart('/')
                    val pfd = openSafFileDescriptor(context, safUri.toUri(), relPath, if (append) "wa" else "wt")
                    if (pfd != null) return FileOutputStream(pfd.fileDescriptor)
                }
            }
            throw e
        }
    }

    /**
     * Opens an InputStream for a file, using SAF if it's a content URI or an external path
     * where direct IO might fail.
     */
    fun openInputStream(
        context: Context,
        path: String,
    ): InputStream {
        if (path.startsWith("content://")) {
            return context.contentResolver.openInputStream(path.toUri())
                ?: throw IOException("Could not open input stream for $path")
        }

        val file = File(path)
        return try {
            file.inputStream()
        } catch (e: IOException) {
            // Fast Path: Try to resolve SAF URI directly
            val directSafUri = getFastDocumentUri(context, path)
            if (directSafUri != null) {
                val `is` = context.contentResolver.openInputStream(directSafUri)
                if (`is` != null) return `is`
            }

            val safUri: String? = PrefManager.externalStoragePath.takeIf { it.startsWith("content://") }
            if (safUri != null) {
                val treeRoot = getPathFromTreeUri(context, safUri.toUri())
                if (treeRoot != null && path.startsWith(treeRoot)) {
                    val relPath = path.removePrefix(treeRoot).trimStart('/')
                    val pfd = openSafFileDescriptor(context, safUri.toUri(), relPath, "r")
                    if (pfd != null) return FileInputStream(pfd.fileDescriptor)
                }
            }
            throw e
        }
    }

    /**
     * Checks if the device has any removable storage (SD card or USB OTG).
     */
    fun hasRemovableStorage(context: Context): Boolean {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return false
        return sm.storageVolumes.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
    }

    suspend fun moveDirectory(
        sourceDir: String,
        targetDir: String,
        onProgressUpdate: (currentFile: String, fileProgress: Float, movedFiles: Int, totalFiles: Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceRootPath = Paths.get(sourceDir)
            val targetRootPath = Paths.get(targetDir)

            if (!Files.exists(sourceRootPath) || !Files.isDirectory(sourceRootPath)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid source directory: $sourceDir"))
            }

            if (!Files.exists(targetRootPath)) {
                Files.createDirectories(targetRootPath)
            }

            val allFiles = mutableListOf<Path>()
            Files.walkFileTree(
                sourceRootPath,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (Files.isRegularFile(file)) {
                            allFiles.add(file)
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        Timber.e(exc, "Failed to visit file: $file")
                        return FileVisitResult.CONTINUE
                    }
                },
            )

            val totalFiles = allFiles.size
            var filesMoved = 0

            for (sourceFilePath in allFiles) {
                val relativePath = sourceRootPath.relativize(sourceFilePath)
                val targetFilePath = targetRootPath.resolve(relativePath)

                Files.createDirectories(targetFilePath.parent)

                try {
                    Files.move(sourceFilePath, targetFilePath, StandardCopyOption.ATOMIC_MOVE)

                    withContext(Dispatchers.Main) {
                        onProgressUpdate(relativePath.toString(), 1f, filesMoved++, totalFiles)
                    }
                } catch (e: Exception) {
                    val fileSize = Files.size(sourceFilePath)
                    var bytesCopied = 0L

                    FileChannel.open(sourceFilePath, StandardOpenOption.READ).use { sourceChannel ->
                        FileChannel.open(
                            targetFilePath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                        ).use { targetChannel ->
                            val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
                            var bytesRead: Int

                            while (sourceChannel.read(buffer).also { bytesRead = it } > 0) {
                                buffer.flip()
                                targetChannel.write(buffer)
                                buffer.compact()

                                bytesCopied += bytesRead

                                val fileProgress = if (fileSize > 0) {
                                    bytesCopied.toFloat() / fileSize
                                } else {
                                    1f
                                }

                                withContext(Dispatchers.Main) {
                                    onProgressUpdate(relativePath.toString(), fileProgress, filesMoved, totalFiles)
                                }
                            }

                            targetChannel.force(true)
                        }
                    }

                    Files.delete(sourceFilePath)
                    withContext(Dispatchers.Main) {
                        onProgressUpdate(relativePath.toString(), 1f, filesMoved++, totalFiles)
                    }
                }
            }

            Files.walkFileTree(
                sourceRootPath,
                object : SimpleFileVisitor<Path>() {
                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        if (exc == null) {
                            try {
                                var isEmpty = true
                                Files.newDirectoryStream(dir).use { stream ->
                                    if (stream.iterator().hasNext()) {
                                        isEmpty = false
                                    }
                                }

                                if (isEmpty && dir != sourceRootPath) {
                                    Files.delete(dir)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to delete directory: $dir")
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )

            try {
                var isEmpty = true
                Files.newDirectoryStream(sourceRootPath).use { stream ->
                    if (stream.iterator().hasNext()) {
                        isEmpty = false
                    }
                }

                if (isEmpty) {
                    Files.delete(sourceRootPath)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e)
            Result.failure(e)
        }
    }

    suspend fun moveDirectory(
        context: Context,
        sourceDir: String,
        targetDir: String,
        onProgressUpdate: (currentFile: String, fileProgress: Float, movedFiles: Int, totalFiles: Int) -> Unit,
    ): Result<Unit> = moveDirectory(
        sourceDir = sourceDir,
        targetDir = targetDir,
        onProgressUpdate = onProgressUpdate,
    )

    /**
     * Move games from internal only storage to user storage.
     * This should be removed after a few versions and just
     * remove the old path to free up space.
     */
    suspend fun moveGamesFromOldPath(
        sourceDir: String,
        targetDir: String,
        onProgressUpdate: (currentFile: String, fileProgress: Float, movedFiles: Int, totalFiles: Int) -> Unit,
        onComplete: () -> Unit,
    ) = withContext(Dispatchers.IO) {
        moveDirectory(
            sourceDir = sourceDir,
            targetDir = targetDir,
            onProgressUpdate = onProgressUpdate,
        )

        withContext(Dispatchers.Main) {
            onComplete()
        }
    }

    suspend fun moveGamesFromOldPath(
        context: Context,
        sourceDir: String,
        targetDir: String,
        onProgressUpdate: (currentFile: String, fileProgress: Float, movedFiles: Int, totalFiles: Int) -> Unit,
        onComplete: () -> Unit,
    ) = moveGamesFromOldPath(
        sourceDir = sourceDir,
        targetDir = targetDir,
        onProgressUpdate = onProgressUpdate,
        onComplete = onComplete,
    )
}
