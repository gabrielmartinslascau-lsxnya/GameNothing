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

    /**
     * Triggers a haptic feedback effect.
     */
    fun trigger(type: HapticType, intensityOverride: Int? = null) {
        if (!PrefManager.hapticsEnabled) return
        val v = vibrator ?: return

        val intensity = intensityOverride ?: PrefManager.hapticIntensity

        // Tuned mapping for better "Feeling"
        // Increased amplitudes for more impact
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
