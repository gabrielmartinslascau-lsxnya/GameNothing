package app.gamenative.utils.downloader

import android.content.Context
import app.gamenative.PrefManager
import com.winlator.core.FileUtils
import com.winlator.core.OnExtractFileListener
import com.winlator.core.TarCompressorUtils
import com.winlator.xenvironment.ImageFs
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import timber.log.Timber

/**
 * Handles downloading and extracting the ARM64 Debian RootFS for the Linux environment.
 */
object LinuxRootfsDownloader {

    const val DEFAULT_ROOTFS_URL =
        "https://github.com/termux/proot-distro/releases/download/v4.17.3/debian-bookworm-aarch64-pd-v4.17.3.tar.xz"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getRootfsDir(context: Context): File {
        return ImageFs.find(context).rootDir
    }

    /**
     * TASK 1.1: Checks if local storage already contains valid ImageFs directory with files.
     */
    fun isRootfsInstalled(context: Context): Boolean {
        val imageFs = ImageFs.find(context)
        return imageFs.isValid && (imageFs.rootDir.list()?.isNotEmpty() == true)
    }

    /**
     * TASK 1.2 & 1.3: Downloads and extracts the rootfs archive into ImageFs rootDir.
     */
    suspend fun downloadAndExtractRootfs(
        context: Context,
        rootfsUrl: String = DEFAULT_ROOTFS_URL,
        onProgress: (Float) -> Unit = {},
        onStatusChange: (String) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val rootfsDir = getRootfsDir(context)
        if (isRootfsInstalled(context)) {
            Timber.d("Linux rootfs already exists at ${rootfsDir.absolutePath}, skipping download.")
            return@withContext Result.success(rootfsDir)
        }

        rootfsDir.mkdirs()
        val tempArchiveFile = File(context.cacheDir, "debian-rootfs-download.tar.xz")

        try {
            Timber.d("Starting download of Linux rootfs from $rootfsUrl")
            onStatusChange("Downloading Linux RootFS...")
            downloadFile(rootfsUrl, tempArchiveFile) { progress ->
                onProgress(progress * 0.60f)
            }

            Timber.d("Download complete. Extracting rootfs archive into ${rootfsDir.absolutePath}...")
            onStatusChange("Extracting RootFS files into container...")
            onProgress(0.65f)

            var extractedCount = 0
            val listener = OnExtractFileListener { file, _ ->
                extractedCount++
                if (extractedCount % 50 == 0) {
                    val extractProgress = 0.65f + ((extractedCount.toFloat() / 15000f).coerceAtMost(0.30f))
                    onProgress(extractProgress)
                }
                file
            }

            val extracted = extractArchive(tempArchiveFile, rootfsDir, listener)
            if (!extracted) {
                Timber.e("Failed to extract rootfs archive to ${rootfsDir.absolutePath}")
                return@withContext Result.failure(IllegalStateException("RootFS extraction failed."))
            }

            tempArchiveFile.delete()

            // Mark ImageFs as valid version 31 bionic variant for GameNothing's native engine
            val imageFs = ImageFs.find(context)
            imageFs.createImgVersionFile(31)
            imageFs.createVariantFile("bionic")

            PrefManager.isLinuxInstalled = true
            onProgress(1.0f)
            onStatusChange("RootFS ready!")
            Timber.d("Linux rootfs extracted and registered in ImageFs successfully.")
            Result.success(rootfsDir)
        } catch (e: Exception) {
            Timber.e(e, "Error during rootfs download/extraction")
            if (tempArchiveFile.exists()) {
                tempArchiveFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * TASK 1.4: Saves user dropdown choices to SharedPreferences.
     */
    fun saveUserChoices(
        protonVersion: String,
        dxvkVersion: String,
        fexPreset: String,
        driver: String
    ) {
        PrefManager.linuxProtonVersion = protonVersion
        PrefManager.linuxDxvkVersion = dxvkVersion
        PrefManager.linuxFexPreset = fexPreset
        PrefManager.linuxDriver = driver
    }

    private fun extractArchive(
        archiveFile: File,
        destDir: File,
        listener: OnExtractFileListener
    ): Boolean {
        return when {
            archiveFile.name.endsWith(".xz") -> {
                TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archiveFile, destDir, listener)
            }
            archiveFile.name.endsWith(".zst") || archiveFile.name.endsWith(".tzst") -> {
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, archiveFile, destDir, listener)
            }
            else -> {
                try {
                    TarArchiveInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(archiveFile)))).use { tarIn ->
                        var entry = tarIn.nextEntry as? TarArchiveEntry
                        while (entry != null) {
                            var outFile = File(destDir, entry.name)
                            outFile = listener.onExtractFile(outFile, entry.size) ?: outFile
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { out ->
                                    tarIn.copyTo(out)
                                }
                                FileUtils.chmod(outFile, entry.mode)
                            }
                            entry = tarIn.nextEntry as? TarArchiveEntry
                        }
                    }
                    true
                } catch (e: Exception) {
                    Timber.e(e, "Failed to extract tar.gz archive")
                    false
                }
            }
        }
    }

    private fun downloadFile(
        urlString: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ) {
        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Android; GameNative)")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Download failed with HTTP ${response.code}: ${response.message}")
        }

        val body = response.body
        val fileLength = body.contentLength()

        body.byteStream().use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (fileLength > 0) {
                        onProgress(totalBytesRead.toFloat() / fileLength.toFloat())
                    }
                }
            }
        }
    }
}
