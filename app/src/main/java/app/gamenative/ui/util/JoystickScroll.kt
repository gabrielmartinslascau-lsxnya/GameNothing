package app.gamenative.ui.util

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Handles scrolling using the Right Joystick.
 */
@Composable
fun JoystickScrollHandler(
    scrollState: ScrollState? = null,
    lazyListState: LazyListState? = null,
    lazyGridState: LazyGridState? = null,
    scrollSpeed: Float = 35f
) {
    val scope = rememberCoroutineScope()

    DisposableEffect(scrollState, lazyListState, lazyGridState) {
        val listener: (AndroidEvent.MotionEvent) -> Boolean = { motionEvent ->
            val event = motionEvent.event
            if (event != null && (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)) {
                val ry = event.getAxisValue(MotionEvent.AXIS_RZ) // Right Stick Y

                if (abs(ry) > 0.15f) {
                    val scrollAmount = ry * scrollSpeed
                    scope.launch {
                        scrollState?.scrollBy(scrollAmount)
                        lazyListState?.scrollBy(scrollAmount)
                        lazyGridState?.scrollBy(scrollAmount)
                    }
                    true
                } else false
            } else false
        }

        PluviaApp.events.on(listener)
        onDispose {
            PluviaApp.events.off(listener)
        }
    }
}
