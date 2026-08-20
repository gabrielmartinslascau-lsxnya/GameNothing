package app.gamenative.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconSwitcher {

    fun applyLauncherIcon(context: Context, variant: Int) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        // These strings MUST match the 'android:name' in AndroidManifest.xml exactly (prefixed with the namespace)
        val defaultAlias = ComponentName(packageName, "app.gamenative.MainActivityAliasDefault")
        val altAlias = ComponentName(packageName, "app.gamenative.MainActivityAliasAlt")
        val legionAlias = ComponentName(packageName, "app.gamenative.MainActivityAliasLegion")

        val defaultState = if (variant == 0) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val altState = if (variant == 1) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val legionState = if (variant == 2) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        // Use DONT_KILL_APP to avoid the softlock/crash.
        // The system will refresh the icon when the app is next backgrounded or after a short delay.
        packageManager.setComponentEnabledSetting(defaultAlias, defaultState, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(altAlias, altState, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(legionAlias, legionState, PackageManager.DONT_KILL_APP)
    }
}
