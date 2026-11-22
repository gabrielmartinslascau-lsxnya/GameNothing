package app.gamenative.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Provides access to the captured background [Backdrop] (from AndroidLiquidGlass 2.0.1)
 * for real-time AGSL optical refraction, dispersion, and blur across Broken Glass panels.
 */
val LocalBrokenGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Provides access to the captured [GraphicsLayer] used by Standard (Normal) Glass
 * AGSL spherical lens refraction (`LENS_SHADER_NORMAL`).
 * Set to `null` inside standalone `Dialog` or `Popup` windows to prevent cross-window sampling.
 */
val LocalBackgroundLayer = staticCompositionLocalOf<GraphicsLayer?> { null }

/**
 * Creates and remembers a [LayerBackdrop] to capture screen backgrounds for Glass panels.
 */
@Composable
fun rememberBrokenGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/**
 * Hard-off variant of [rememberBrokenGlassBackdrop].
 *
 * Returns `null` when [enabled] is false so that NO [GraphicsLayer] is allocated. When the
 * toggle flips from on → off, the previously remembered layer leaves composition and
 * `rememberGraphicsLayer` releases its RenderNode.
 */
@Composable
fun rememberBrokenGlassBackdropIfEnabled(enabled: Boolean): LayerBackdrop? =
    if (enabled) rememberLayerBackdrop() else null

/**
 * Applies [layerBackdrop] only when [backdrop] is non-null.
 *
 * When `null`, the modifier chain is left untouched: no per-frame `recordLayer` re-recording
 * of the subtree and no `onGloballyPositioned` coordinate tracking.
 */
fun Modifier.layerBackdropIfActive(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this
