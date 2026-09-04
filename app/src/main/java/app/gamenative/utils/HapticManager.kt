package app.gamenative.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import app.gamenative.PrefManager

import android.view.InputDevice

object HapticManager {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    enum class HapticType {
        CLICK,  // Standard quick feedback
        MENU,   // Double quick feedback
        LONG    // 1s preview
    }

    private fun performVibration(v: Vibrator, type: HapticType, duration: Long, amplitude: Int) {
        try {
            when (type) {
                HapticType.CLICK -> {
                    v.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                }
                HapticType.MENU -> {
                    val gap = 45L
                    val pattern = longArrayOf(0, duration, gap, duration)
                    val amplitudes = intArrayOf(0, amplitude, 0, amplitude)
                    v.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
                }
                HapticType.LONG -> {
                    v.vibrate(VibrationEffect.createOneShot(1000, amplitude))
                }
            }
        } catch (_: Exception) {
            // FALLBACK: Some copycat controllers only support basic vibration calls
            @Suppress("DEPRECATION")
            v.vibrate(duration)
        }
    }

    /**
     * Triggers a haptic feedback effect.
     */
    fun trigger(type: HapticType, intensityOverride: Int? = null) {
        if (!PrefManager.hapticsEnabled) return

        val intensity = intensityOverride ?: PrefManager.hapticIntensity
        val amplitude = when (intensity) {
            1 -> 80
            2 -> 180
            else -> 255
        }
        val duration = when (intensity) {
            1 -> 15L
            2 -> 35L
            else -> 60L
        }

        // 1. Try to vibrate connected game controllers
        var controllerVibrated = false
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id)
            if (device != null && (device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)) {

                val v = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        device.vibratorManager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        device.vibrator
                    }
                } catch (_: Exception) {
                    null
                }

                if (v != null && v.hasVibrator()) {
                    performVibration(v, type, duration, amplitude)
                    controllerVibrated = true
                }
            }
        }

        // 2. Fallback to phone vibrator only if no controller vibrated
        if (!controllerVibrated) {
            vibrator?.let { performVibration(it, type, duration, amplitude) }
        }
    }
}

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: androidx.compose.ui.semantics.Role? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: androidx.compose.foundation.Indication? = null,
    type: HapticManager.HapticType = HapticManager.HapticType.CLICK,
    onClick: () -> Unit
): Modifier = composed {
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        indication = indication ?: LocalIndication.current,
        onClick = {
            HapticManager.trigger(type)
            onClick()
        }
    )
}

fun Modifier.hapticSelectable(
    selected: Boolean,
    enabled: Boolean = true,
    role: androidx.compose.ui.semantics.Role? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: androidx.compose.foundation.Indication? = null,
    type: HapticManager.HapticType = HapticManager.HapticType.CLICK,
    onClick: () -> Unit
): Modifier = composed {
    this.selectable(
        selected = selected,
        enabled = enabled,
        role = role,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        indication = indication ?: LocalIndication.current,
        onClick = {
            HapticManager.trigger(type)
            onClick()
        }
    )
}
