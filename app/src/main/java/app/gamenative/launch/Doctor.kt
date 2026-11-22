package app.gamenative.launch

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.PrefManager
import app.gamenative.ui.component.dialog.LaunchReadinessDialog
import app.gamenative.utils.StorageUtils
import com.winlator.container.BasePrefix
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

object Doctor {

    private const val BUILDING_MARKER = ".building"

    sealed class DiagnosticIssue {
        data class HeldPrefixLock(
            val prefixPath: String,
            val detail: String,
        ) : DiagnosticIssue()

        data class UnpluggedExternalVolume(
            val volumePath: String,
            val detail: String,
        ) : DiagnosticIssue()

        data class StorageSpaceLow(
            val path: String,
            val availableBytes: Long,
        ) : DiagnosticIssue()
    }

    sealed class DiagnosticResult {
        object Healthy : DiagnosticResult()
        data class Issue(val issue: DiagnosticIssue) : DiagnosticResult()
    }

    /**
     * Checks if a shared Wine prefix lock is held on internal storage.
     * This occurs when a base prefix is actively building (.building marker exists),
     * when a staging prefix exists, or when the prefix directory is currently locked.
     */
    fun checkSharedPrefixLock(context: Context, wineVersion: String? = null): DiagnosticIssue.HeldPrefixLock? {
        val contentsManager = ContentsManager(context)
        contentsManager.syncContents()
        val versionsToCheck = mutableListOf<String>()
        if (!wineVersion.isNullOrBlank()) {
            versionsToCheck.add(wineVersion)
        } else {
            contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)?.forEach {
                it.verName?.let { name -> versionsToCheck.add(name) }
            }
            contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)?.forEach {
                it.verName?.let { name -> versionsToCheck.add(name) }
            }
        }

        for (version in versionsToCheck) {
            val baseDir = BasePrefix.getBaseDir(context, contentsManager, version) ?: continue
            val buildingMarker = File(baseDir, BUILDING_MARKER)
            if (buildingMarker.exists()) {
                val detail = "Shared prefix build marker present at ${buildingMarker.absolutePath}"
                Timber.w("Doctor: Held prefix lock detected: $detail")
                return DiagnosticIssue.HeldPrefixLock(baseDir.absolutePath, detail)
            }
            val stagingDir = File(baseDir, ".staging")
            if (stagingDir.exists() && (stagingDir.list()?.isNotEmpty() == true)) {
                val detail = "Shared prefix staging active at ${stagingDir.absolutePath}"
                Timber.w("Doctor: Held prefix lock detected: $detail")
                return DiagnosticIssue.HeldPrefixLock(baseDir.absolutePath, detail)
            }
        }
        return null
    }

    /**
     * Checks if a target path (or configured external storage path) is on an external volume
     * that is currently unplugged, unmounted, or missing.
     */
    fun checkExternalVolume(context: Context, targetPath: String? = null): DiagnosticIssue.UnpluggedExternalVolume? {
        val pathToCheck = targetPath?.takeIf { it.isNotBlank() }
            ?: PrefManager.externalStoragePath.takeIf { PrefManager.useExternalStorage && it.isNotBlank() }
            ?: return null

        val resolved = StorageUtils.resolveStoragePath(context, pathToCheck) ?: pathToCheck
        val file = File(resolved)

        // Internal app storage or primary emulated storage is not an external removable volume
        val internalData = context.dataDir.absolutePath
        if (resolved.startsWith(internalData) || resolved.startsWith("/data/data/") || resolved.startsWith("/data/user/0/")) {
            return null
        }

        val isExternalCandidate = (resolved.startsWith("/storage/") && !resolved.startsWith("/storage/emulated/")) ||
            resolved.startsWith("/mnt/media_rw/") ||
            (PrefManager.useExternalStorage && !file.exists())

        if (isExternalCandidate) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            val volume = sm?.getStorageVolume(file)

            val isMounted = volume?.let { it.state == Environment.MEDIA_MOUNTED } ?: file.exists()
            if (!isMounted || !file.exists()) {
                val detail = "External storage at $resolved is not mounted or disconnected"
                Timber.w("Doctor: Unplugged external volume detected: $detail")
                return DiagnosticIssue.UnpluggedExternalVolume(resolved, detail)
            }
        }
        return null
    }

    /**
     * Runs full diagnostics, explicitly distinguishing between a held internal shared-prefix lock
     * and an unplugged external USB game volume.
     */
    fun diagnose(
        context: Context,
        gamePath: String? = null,
        wineVersion: String? = null,
    ): DiagnosticResult {
        // 1. Check if external USB/game volume is unplugged
        val unplugged = checkExternalVolume(context, gamePath)
        if (unplugged != null) {
            return DiagnosticResult.Issue(unplugged)
        }

        // 2. Check if internal shared-prefix lock is held
        val prefixLock = checkSharedPrefixLock(context, wineVersion)
        if (prefixLock != null) {
            return DiagnosticResult.Issue(prefixLock)
        }

        return DiagnosticResult.Healthy
    }
}

/**
 * LaunchReadiness check implementation using Doctor diagnostics.
 */
class DoctorLaunchReadinessCheck(private val context: Context) : LaunchReadiness.Check {
    var currentIssue by mutableStateOf<Doctor.DiagnosticIssue?>(null)
        private set

    override val pending: Boolean
        get() = currentIssue != null

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        val result = Doctor.diagnose(context)
        withContext(Dispatchers.Main) {
            currentIssue = (result as? Doctor.DiagnosticResult.Issue)?.issue
        }
    }

    override suspend fun resolve(activity: Activity): Boolean {
        refresh()
        return !pending
    }

    @Composable
    override fun Prompt(activity: Activity, onResolved: () -> Unit) {
        val issue = currentIssue
        if (issue == null) {
            onResolved()
            return
        }

        val (title, message) = when (issue) {
            is Doctor.DiagnosticIssue.UnpluggedExternalVolume -> {
                "External Storage Disconnected" to
                    "The external USB game volume (${issue.volumePath}) is unplugged or disconnected. Please reconnect your external drive or insert the SD card to continue."
            }
            is Doctor.DiagnosticIssue.HeldPrefixLock -> {
                "Wine Prefix Busy" to
                    "The shared Wine prefix is locked or being updated. Please wait for the background initialization to complete."
            }
            is Doctor.DiagnosticIssue.StorageSpaceLow -> {
                "Low Storage Space" to
                    "Storage space on ${issue.path} is critically low. Please free up space before continuing."
            }
        }

        LaunchReadinessDialog(
            isLoading = false,
            title = title,
            message = message,
            confirmText = "Retry",
            dismissText = "Dismiss",
            onConfirm = {
                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    refresh()
                    if (!pending) {
                        onResolved()
                    }
                }
            },
            onDismiss = {
                onResolved()
            },
        )
    }
}
