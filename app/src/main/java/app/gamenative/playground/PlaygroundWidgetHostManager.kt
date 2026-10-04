package app.gamenative.playground

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

class PlaygroundWidgetHostManager(context: Context) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private val appWidgetHost = AppWidgetHost(context, PLAYGROUND_HOST_ID)

    fun startListening() = appWidgetHost.startListening()
    fun stopListening() = appWidgetHost.stopListening()

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()
    fun deleteWidgetId(id: Int) = appWidgetHost.deleteAppWidgetId(id)

    fun createHostView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return appWidgetHost.createView(context, appWidgetId, info)
    }

    fun getAvailableWidgets(): List<AppWidgetProviderInfo> = appWidgetManager.installedProviders

    companion object {
        private const val PLAYGROUND_HOST_ID = 1024
    }
}
