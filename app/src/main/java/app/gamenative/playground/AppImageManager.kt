package app.gamenative.playground

import android.content.Context
import android.os.StatFs
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/**
 * Controller for importing, validating, and managing AppImage applications
 * in the rootless Linux Playground.
 * Validates Type 2 ARM64 ELF, copies off noexec storage, extracts to /opt/appimages/user/<app>,
 * and keeps APPIMAGE environment variable unset during execution.
 */
object AppImageManager {
    const val GUEST_DIR = "/opt/appimages/user"
    const val LAUNCHER = "/usr/local/bin/droiddeck-appimage-run"

    class Item(
        val id: String,
        val name: String,
        val icon: File?,
        val comment: String?
    ) {
        val guestDir: String get() = "$GUEST_DIR/$id"
    }

    private fun root(context: Context): File =
        File(LinuxRuntime.rootDir(context), GUEST_DIR.substring(1))

    fun list(context: Context): List<Item> =
        root(context).listFiles()?.filter {
            File(it, "app/AppRun").exists() || Files.isSymbolicLink(File(it, "app/AppRun").toPath())
        }?.map { dir ->
            val name = File(dir, "name").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { dir.name } ?: dir.name
            val comment = File(dir, "comment").takeIf { it.isFile }?.readText()?.trim()
            val icon = File(dir, "icon.png").takeIf { it.isFile }
            Item(dir.name, name, icon, comment)
        }?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun validateAppImage(file: File): String? {
        if (!file.isFile) return "File does not exist"
        val head = ByteArray(20)
        try {
            RandomAccessFile(file, "r").use {
                if (it.read(head) < head.size) return "File is too small to be an AppImage"
            }
        } catch (e: Exception) {
            return "Cannot read file: ${e.message}"
        }

        // ELF Magic: 0x7F 'E' 'L' 'F'
        if (head[0] != 0x7f.toByte() || head[1] != 'E'.code.toByte() || head[2] != 'L'.code.toByte() || head[3] != 'F'.code.toByte()) {
            return "File is not an executable Linux binary"
        }

        val machine = (head[18].toInt() and 0xff) or ((head[19].toInt() and 0xff) shl 8)
        if (machine != LinuxFex.ELF_AARCH64 && !LinuxFex.isX86(machine)) {
            return "Architecture not supported ($machine). Only ARM64 and x86_64 are supported."
        }

        // AppImage Magic: 'A' 'I' (bytes 8, 9)
        if (head[8] != 'A'.code.toByte() || head[9] != 'I'.code.toByte()) {
            return "Not a valid AppImage package (missing AI magic)"
        }

        // Type 2 check (byte 10 == 2)
        if (head[10].toInt() != 2) {
            return "Unsupported AppImage type: ${head[10].toInt()}. Only Type 2 is supported."
        }

        return null
    }

    fun idFor(fileName: String, taken: Set<String>): String {
        val base = fileName.removeSuffix(".AppImage").removeSuffix(".appimage").lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "appimage" }
        var id = base
        var n = 2
        while (id in taken) id = "$base-${n++}"
        return id
    }

    fun dir(context: Context, id: String): File = File(root(context), id)

    fun import(
        context: Context,
        file: File,
        name: String? = null,
        onProgress: (String) -> Unit
    ): String? {
        if (!LinuxRuntime.isInstalled(context)) return "Playground runtime is not installed"
        val err = validateAppImage(file)
        if (err != null) return err

        val base = root(context).apply { mkdirs() }
        val requiredBytes = file.length() * 3
        if (StatFs(base.path).availableBytes < requiredBytes) {
            return "Insufficient storage space: need at least ${requiredBytes / (1024 * 1024)} MB free"
        }

        val taken = base.list()?.toSet() ?: emptySet()
        val id = idFor(name ?: file.name, taken)
        val appDir = File(base, id)
        try {
            appDir.mkdirs()
            onProgress("Copying image from external storage...")
            val internalCopy = File(appDir, "image.AppImage")
            file.inputStream().use { input ->
                internalCopy.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            internalCopy.setExecutable(true, false)

            onProgress("Extracting AppImage contents...")
            val extractDir = File(appDir, "app").apply { mkdirs() }

            // Extract AppImage once using unsquashfs or internal binary
            val proc = ProcessBuilder("unsquashfs", "-d", extractDir.absolutePath, internalCopy.absolutePath)
                .redirectErrorStream(true)
                .start()
            val code = proc.waitFor()
            if (code != 0) {
                // If unsquashfs is not on host, run through guest runtime
                val guestCode = FlatpakManager.runGuest(
                    context,
                    listOf("unsquashfs", "-d", "$GUEST_DIR/$id/app", "$GUEST_DIR/$id/image.AppImage"),
                    fakeRoot = true
                ) { line -> Timber.d("AppImage unpack: $line") }
                if (guestCode != 0) {
                    return "Failed to extract AppImage (exit code $guestCode)"
                }
            }

            File(appDir, "name").writeText(name ?: file.name.removeSuffix(".AppImage"))
            LinuxFex.writeArch(appDir, LinuxFex.elfMachine(file))

            return null
        } catch (e: Exception) {
            Timber.e(e, "AppImage import failed")
            appDir.deleteRecursively()
            return e.message ?: "Import failed"
        }
    }
}
