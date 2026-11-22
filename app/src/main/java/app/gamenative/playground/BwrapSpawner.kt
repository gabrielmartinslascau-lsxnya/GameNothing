package app.gamenative.playground

import android.content.Context

/**
 * Delegates to DroidDeck's unified [com.droiddeck.launcher.runtime.BwrapSpawner] so
 * `:app` and `:droiddeck` share a single abstract socket (`"${context.packageName}.bwrap"`)
 * without double-bind collisions.
 */
object BwrapSpawner {
    fun socket(context: Context): String = com.droiddeck.launcher.runtime.BwrapSpawner.socket(context)

    fun start(context: Context) {
        com.droiddeck.launcher.runtime.BwrapSpawner.start(context)
    }
}
