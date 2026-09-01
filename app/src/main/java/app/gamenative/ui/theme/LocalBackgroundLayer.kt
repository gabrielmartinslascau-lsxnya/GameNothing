package app.gamenative.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * Provides access to the captured background layer for optical refraction.
 */
val LocalBackgroundLayer = staticCompositionLocalOf<GraphicsLayer?> { null }
