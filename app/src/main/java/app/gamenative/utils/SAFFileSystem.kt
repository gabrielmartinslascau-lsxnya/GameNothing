package app.gamenative.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import okio.*
import java.io.FileNotFoundException
import java.io.IOException

/**
 * An Okio [FileSystem] that operates on Android's Storage Access Framework (SAF).
 * It uses a root [DocumentFile] as the base for all operations.
 */
class SAFFileSystem(
    private val context: Context,
    rootUri: Uri
) : FileSystem() {

    private val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
        ?: throw IOException("Could not open root DocumentFile from URI: $rootUri")

    private fun findFile(path: Path, createIfMissing: Boolean = false, isDirectory: Boolean = false): DocumentFile? {
        // Fast Path: Try direct Document URI construction via rootId:relPath
        val pathStr = path.toString()
        StorageUtils.getFastDocumentUri(context, pathStr)?.let { directUri ->
            val doc = if (isDirectory) {
                DocumentFile.fromTreeUri(context, directUri)
            } else {
                DocumentFile.fromSingleUri(context, directUri)
            }
            if (doc?.exists() == true) return doc
        }

        val segments = path.segments
        var current: DocumentFile = rootDoc

        for (i in segments.indices) {
            val segment = segments[i]
            if (segment == "." || segment == "..") continue
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

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        val doc = findFile(file) ?: if (mustExist) throw FileNotFoundException("File not found: $file") else findFile(file, true)
        val uri = doc?.uri ?: throw IOException("Could not get URI for $file")
        val os = context.contentResolver.openOutputStream(uri, "wa") ?: throw IOException("Could not open output stream for $file")
        return os.sink()
    }

    override fun atomicMove(source: Path, target: Path) {
        throw IOException("atomicMove not supported in SAFFileSystem")
    }

    override fun canonicalize(path: Path): Path {
        return path
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        val existing = findFile(dir)
        if (existing != null) {
            if (mustCreate) throw IOException("Directory already exists: $dir")
            if (!existing.isDirectory) throw IOException("Path exists but is not a directory: $dir")
            return
        }
        findFile(dir, createIfMissing = true, isDirectory = true) ?: throw IOException("Could not create directory: $dir")
    }

    override fun createSymlink(source: Path, target: Path) {
        throw IOException("Symlinks not supported in SAFFileSystem")
    }

    override fun delete(path: Path, mustExist: Boolean) {
        val doc = findFile(path)
        if (doc == null) {
            if (mustExist) throw FileNotFoundException("File not found: $path")
            return
        }
        if (!doc.delete()) throw IOException("Could not delete $path")
    }

    override fun list(dir: Path): List<Path> {
        return listOrNull(dir) ?: throw FileNotFoundException("Directory not found: $dir")
    }

    override fun listOrNull(dir: Path): List<Path>? {
        val doc = findFile(dir) ?: return null
        if (!doc.isDirectory) return null
        return doc.listFiles().mapNotNull { it.name?.let { name -> dir / name } }
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        val doc = findFile(path) ?: return null
        return FileMetadata(
            isRegularFile = doc.isFile,
            isDirectory = doc.isDirectory,
            size = if (doc.isFile) doc.length() else null,
            lastModifiedAtMillis = doc.lastModified().takeIf { it > 0 }
        )
    }

    override fun openReadOnly(file: Path): FileHandle {
        throw IOException("FileHandle not supported in SAFFileSystem")
    }

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
        throw IOException("FileHandle not supported in SAFFileSystem")
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        val existing = findFile(file)
        if (existing != null && mustCreate) throw IOException("File already exists: $file")

        val doc = existing ?: findFile(file, createIfMissing = true)
        val uri = doc?.uri ?: throw IOException("Could not get URI for $file")
        val os = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open output stream for $file")
        return os.sink()
    }

    override fun source(file: Path): Source {
        val doc = findFile(file) ?: throw FileNotFoundException("File not found: $file")
        val uri = doc.uri
        val `is` = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open input stream for $file")
        return `is`.source()
    }
}
