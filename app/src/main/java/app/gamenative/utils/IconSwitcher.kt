package app.gamenative.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconSwitcher {

    fun applyLauncherIcon(context: Context, variant: Int) {
        val packageManager = context.packageManager

        val defaultAlias = ComponentName(context, "app.gamenative.MainActivityAliasDefault")
        val altAlias = ComponentName(context, "app.gamenative.MainActivityAliasAlt")
        val legionAlias = ComponentName(context, "app.gamenative.MainActivityAliasLegion")

        val defaultState = if (variant == 0) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val altState = if (variant == 1) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val legionState = if (variant == 2) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        packageManager.setComponentEnabledSetting(defaultAlias, defaultState, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(altAlias, altState, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(legionAlias, legionState, PackageManager.DONT_KILL_APP)
    }
}
