package app.gamenative.performance

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.utils.ContainerUtils
import com.winlator.xenvironment.ImageFs
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Targeted Shader & Pipeline Cache Cleanup Utility (GameNothing 2.0-patch4).
 *
 * Safely wipes ONLY GPU/translator shader caches (Mesa/Turnip `mesa_shader_cache`,
 * `mesa_shader_cache_db`, DXVK/VKD3D state caches, Fossilize shader caches) and stale
 * temp X11/Pulse sockets (only when no active game session is running).
 *
 * NEVER touches saves, Wine prefixes (`drive_c`, `.wine`), installed components, or
 * container configuration JSONs.
 */
object ShaderCacheCleaner {

    data class CleanupResult(
        val freedBytes: Long,
        val deletedItems: Int,
    ) {
        val freedMb: Double get() = freedBytes / (1024.0 * 1024.0)

        fun formattedMessage(): String {
            return if (freedBytes <= 0L) {
                "Shader cache is already clean (0.0 MB freed)"
            } else {
                String.format(Locale.US, "Cleared shader cache (%.1f MB freed)", freedMb)
            }
        }
    }

    private val SHADER_CACHE_DIR_NAMES = setOf(
        "mesa_shader_cache",
        "mesa_shader_cache_db",
        "radv_builtin_shaders",
        "vulkan_shader_cache",
        "shader_cache",
        "dxvk_cache",
        "dxvk",
        "vkd3d-proton",
        "vkd3d_cache",
        "fossilize",
    )

    private val SHADER_CACHE_FILE_EXTENSIONS = listOf(
        ".dxvk-cache",
        ".vkd3d-proton.cache",
        ".vkd3d-proton.cache.write",
        ".foz",
    )

    suspend fun clearShaderCaches(
        context: Context,
        appId: String? = null,
    ): CleanupResult = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        var totalDeleted = 0

        fun deleteTarget(target: File) {
            if (!target.exists()) return
            val bytes = calculateSizeBytes(target)
            val deleted = runCatching {
                if (target.isDirectory) target.deleteRecursively() else target.delete()
            }.getOrDefault(false)
            if (deleted) {
                totalBytes += bytes
                totalDeleted++
            }
        }

        fun cleanShaderEntriesInDir(dir: File?) {
            if (dir == null || !dir.exists() || !dir.isDirectory) return
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (child in children) {
                val lower = child.name.lowercase(Locale.ROOT)
                if (child.isDirectory && lower in SHADER_CACHE_DIR_NAMES) {
                    deleteTarget(child)
                } else if (child.isFile && SHADER_CACHE_FILE_EXTENSIONS.any { lower.endsWith(it) }) {
                    deleteTarget(child)
                }
            }
        }

        // 1. ImageFs shared shader cache (/data/data/app.gamenative/files/imagefs/home/xuser/.cache)
        //    Preserves unrelated files like wallpaper.bmp.
        runCatching {
            val imageFs = ImageFs.find(context)
            cleanShaderEntriesInDir(File(imageFs.cache_path))

            // 2. Stale temp X11 / PulseAudio sockets ONLY when no game session is active
            if (PluviaApp.xEnvironment == null) {
                val tmpDirs = listOf(
                    imageFs.tmpDir,
                    File(imageFs.rootDir, "tmp"),
                )
                for (tmpDir in tmpDirs) {
                    if (!tmpDir.exists() || !tmpDir.isDirectory) continue
                    val x11Dir = File(tmpDir, ".X11-unix")
                    if (x11Dir.exists() && x11Dir.isDirectory) {
                        x11Dir.listFiles()?.forEach { deleteTarget(it) }
                    }
                    tmpDir.listFiles()?.forEach { child ->
                        val lower = child.name.lowercase(Locale.ROOT)
                        if (lower.startsWith("pulse-") || lower.startsWith("xserver-") || lower.endsWith(".lock")) {
                            deleteTarget(child)
                        }
                    }
                }
            }
        }.onFailure {
            Timber.w(it, "ShaderCacheCleaner: failed cleaning ImageFs shader cache")
        }

        // 3. Android app cacheDir shader entries
        runCatching {
            cleanShaderEntriesInDir(context.cacheDir)
        }.onFailure {
            Timber.w(it, "ShaderCacheCleaner: failed cleaning app cacheDir")
        }

        // 4. Target container's .cache directory (Fossilize / Mesa / DXVK / VKD3D caches)
        if (!appId.isNullOrBlank()) {
            runCatching {
                if (ContainerUtils.hasContainer(context, appId)) {
                    val container = ContainerUtils.getContainer(context, appId)
                    cleanShaderEntriesInDir(File(container.rootDir, ".cache"))
                }
            }.onFailure {
                Timber.w(it, "ShaderCacheCleaner: failed cleaning container shader cache for %s", appId)
            }
        }

        Timber.i("ShaderCacheCleaner: freed %d bytes (%d items)", totalBytes, totalDeleted)
        CleanupResult(freedBytes = totalBytes, deletedItems = totalDeleted)
    }

    private fun calculateSizeBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var sum = 0L
        val stack = ArrayDeque<File>()
        stack.add(file)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.add(child)
                } else {
                    sum += child.length()
                }
            }
        }
        return sum
    }
}
