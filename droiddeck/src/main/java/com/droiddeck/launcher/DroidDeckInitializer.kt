package com.droiddeck.launcher

import android.app.Application
import android.os.Build
import android.os.Process
import android.webkit.WebView
import com.droiddeck.launcher.runtime.BwrapSpawner
import com.droiddeck.launcher.session.EnhancedSessionLogShare
import com.droiddeck.launcher.session.GpuClockPin

object DroidDeckInitializer {
    fun init(app: Application) {
        val process = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else (app.getSystemService(Application.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName

        if (process == "${app.packageName}:xboxlogin") {
            if (Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix("xbox")
            return
        }
        if (process == "${app.packageName}:xboxcloud") {
            if (Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix("xboxcloud")
            return
        }
        if (process == "${app.packageName}:xcloudnative") {
            com.droiddeck.launcher.stores.StoresState.init(app)
            return
        }
        if (process == "${app.packageName}:vr_recovery" || process == "${app.packageName}:rgb_diagnostic" ||
            process == "${app.packageName}:shortcuts_sync" || process == "${app.packageName}:bottom_desktop"
        ) return

        runCatching {
            Thread({ runCatching { EnhancedSessionLogShare.cleanupAbandonedStages(app) } }, "diagnostic-stage-cleanup").start()
            com.droiddeck.launcher.ui.Themes.loadGradient(app)
            com.droiddeck.launcher.session.SessionPrefs.applyNtsyncDefault(app)
            com.droiddeck.launcher.session.SessionPrefs.settleGpuDriverMode(app)
            com.droiddeck.launcher.core.DownloadSource.init(app)
            prefetchAddonPacks(app)
            GpuClockPin.clearLeftover(app)
            BwrapSpawner.start(app)
            com.droiddeck.launcher.runtime.RootSpawner.start(app)
            com.droiddeck.launcher.frontend.GameFileSync.start(app)
            com.droiddeck.launcher.stores.StoresState.init(app)
            com.droiddeck.launcher.stores.StoresState.register(com.droiddeck.launcher.stores.gog.GogBackend)
            com.droiddeck.launcher.stores.StoresState.register(com.droiddeck.launcher.stores.epic.EpicBackend)
            com.droiddeck.launcher.stores.StoresState.register(com.droiddeck.launcher.stores.amazon.AmazonBackend)
            com.droiddeck.launcher.stores.StoresState.register(com.droiddeck.launcher.stores.itch.ItchBackend)
            com.droiddeck.launcher.stores.StoresState.register(com.droiddeck.launcher.stores.gamejolt.GameJoltBackend)
            com.droiddeck.launcher.stores.StoresState.register(com.droiddeck.launcher.stores.xbox.XboxBackend)
            com.droiddeck.launcher.stores.StoresState.register(com.droiddeck.launcher.stores.battlenet.BattleNetBackend)
            com.droiddeck.launcher.sync.Syncthing.init(app)
        }.onFailure {
            android.util.Log.e("DroidDeckInitializer", "DroidDeck init failed", it)
        }
    }

    private fun prefetchAddonPacks(app: Application) {
        Thread({
            runCatching {
                if (com.droiddeck.launcher.runtime.LinuxRuntime.rootDir(app).isDirectory) {
                    com.droiddeck.launcher.core.AddonPack.PROOT.ensure(app)
                    com.droiddeck.launcher.core.AddonPack.DIRECTAUDIO.ensure(app)
                }
                if (com.droiddeck.launcher.stores.StoreAccounts.signedInAs(app, com.droiddeck.launcher.stores.Store.XBOX) != null) {
                    com.droiddeck.launcher.core.AddonPack.XBOX.ensure(app)
                }
            }.onFailure {
                android.util.Log.w("DroidDeckInitializer", "add-on pack prefetch failed", it)
            }
        }, "addon-packs").start()
    }
}
