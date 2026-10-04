package app.gamenative.utils

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/**
 * Returns the device's real physical screen resolution in pixels, normalized
 * to landscape (width is always the larger number) since game resolutions
 * are conventionally written landscape-first (1920x1080, not 1080x1920),
 * regardless of which way the phone's UI happens to be rotated right now.
 */
fun getNativeScreenResolution(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val (rawWidth, rawHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.maximumWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        val point = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(point)
        point.x to point.y
    }
    return maxOf(rawWidth, rawHeight) to minOf(rawWidth, rawHeight)
}

data class PercentResolution(val percent: Int, val width: Int, val height: Int) {
    val label: String get() = "$percent% — ${width}x${height}"
}

fun computePercentResolutions(context: Context): List<PercentResolution> {
    val (nativeWidth, nativeHeight) = getNativeScreenResolution(context)
    return listOf(100, 75, 50, 25).map { pct ->
        // Rounded down to the nearest even number — most render pipelines
        // assume even width/height; odd dimensions can break some scalers.
        val w = ((nativeWidth * pct / 100) / 2) * 2
        val h = ((nativeHeight * pct / 100) / 2) * 2
        PercentResolution(pct, w, h)
    }
}
