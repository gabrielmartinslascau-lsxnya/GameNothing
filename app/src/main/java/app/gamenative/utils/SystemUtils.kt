package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object SystemUtils {
    /**
     * Checks if the app is currently set as the default home launcher.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
}
