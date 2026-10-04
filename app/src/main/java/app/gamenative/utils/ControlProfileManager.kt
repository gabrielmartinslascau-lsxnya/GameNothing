package app.gamenative.utils

import android.content.Context
import app.gamenative.data.ControlProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import timber.log.Timber

object ControlProfileManager {
    private const val VIRTUAL_DIR = "custom_virtual_profiles"
    private const val PHYSICAL_DIR = "custom_physical_profiles"

    private fun getProfilesDir(context: Context, isPhysical: Boolean): File {
        val dirName = if (isPhysical) PHYSICAL_DIR else VIRTUAL_DIR
        val dir = File(context.filesDir, dirName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Atomic Save: Writes to a temporary file first, then swaps.
     * Prevents data loss if a crash occurs during the write process.
     */
    fun saveProfile(context: Context, profile: ControlProfile, isPhysical: Boolean): Boolean {
        val dir = getProfilesDir(context, isPhysical)
        val finalFile = File(dir, "${profile.name}.json")
        val tempFile = File(dir, "${profile.name}.tmp")

        return try {
            val json = Json.encodeToString(profile)

            // 1. Write to temporary file
            tempFile.writeText(json)

            // 2. Verify write success (check size)
            if (tempFile.length() < 10) throw IllegalStateException("Write failed: File too small")

            // 3. Swap files
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) throw IllegalStateException("Failed to swap temp file")

            true
        } catch (e: Exception) {
            Timber.e(e, "CRITICAL: Failed to save ${if (isPhysical) "physical" else "virtual"} control profile: ${profile.name}")
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    fun loadProfiles(context: Context, isPhysical: Boolean): List<ControlProfile> {
        val dir = getProfilesDir(context, isPhysical)
        return dir.listFiles { file -> file.extension == "json" }?.mapNotNull { file ->
            try {
                Json.decodeFromString<ControlProfile>(file.readText())
            } catch (e: Exception) {
                Timber.e(e, "Failed to load control profile: ${file.name}")
                null
            }
        } ?: emptyList()
    }

    fun deleteProfile(context: Context, name: String, isPhysical: Boolean): Boolean {
        val file = File(getProfilesDir(context, isPhysical), "$name.json")
        return file.delete()
    }
}
