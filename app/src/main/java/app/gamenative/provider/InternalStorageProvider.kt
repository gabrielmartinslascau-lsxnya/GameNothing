package app.gamenative.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import app.gamenative.R
import java.io.File
import java.io.FileNotFoundException

class InternalStorageProvider : DocumentsProvider() {

    companion object {
        private const val AUTHORITY = "app.gamenothing.documents"

        private val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID
        )

        private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

        // Only expose specific directories to protect app metadata
        private val EXPOSED_DIRS = setOf("home", "Steam", "Epic", "GOG", "Amazon")
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, "internal")
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "root")
            add(DocumentsContract.Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "GameNothing")
            add(DocumentsContract.Root.COLUMN_SUMMARY, "Internal Game Data")
            add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
            add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }

        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val file = getFileForDocId(documentId ?: "root")
        includeFile(result, documentId ?: "root", file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId ?: "root")

        parent.listFiles()?.forEach { file ->
            // Safeguard: If we are at the root, only show EXPOSED_DIRS
            if (parentDocumentId == "root") {
                if (file.name in EXPOSED_DIRS) {
                    includeFile(result, null, file)
                }
            } else {
                includeFile(result, null, file)
            }
        }

        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId ?: throw FileNotFoundException())
        val accessMode = ParcelFileDescriptor.parseMode(mode ?: "r")
        return ParcelFileDescriptor.open(file, accessMode)
    }

    private fun getFileForDocId(documentId: String): File {
        var root = context?.dataDir ?: throw FileNotFoundException()
        if (documentId == "root") return root

        val path = documentId.removePrefix("root:")
        val file = File(root, path)

        if (!file.exists()) throw FileNotFoundException("File not found: $documentId")
        return file
    }

    private fun includeFile(cursor: MatrixCursor, documentId: String?, file: File) {
        val id = documentId ?: "root:${file.absolutePath.removePrefix(context?.dataDir?.absolutePath ?: "")}"

        var flags = 0
        if (file.isDirectory && file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        }

        // Block deletion of top-level exposed directories
        if (file.parentFile?.absolutePath == context?.dataDir?.absolutePath) {
            flags = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.inv()
        }

        val displayName = if (id == "root") context?.getString(R.string.app_name) ?: "GameNothing" else file.name

        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, getTypeForFile(file))
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        }
    }

    private fun getTypeForFile(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
    }
}
