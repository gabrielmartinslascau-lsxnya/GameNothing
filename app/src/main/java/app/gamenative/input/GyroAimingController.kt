package app.gamenative.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.gamenative.PrefManager
import com.winlator.xserver.XServer
import timber.log.Timber

/**
 * Controller that translates device physical gyroscope sensor motion into mouse cursor / camera movement
 * for fine-tuned motion aiming in games.
 */
class GyroAimingController(
    private val context: Context,
    private val xServer: XServer
) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isRegistered = false
    private var lastTimestampNanos: Long = 0

    fun start() {
        if (!PrefManager.gyroAimingEnabled || isRegistered || gyroscopeSensor == null) return
        val success = sensorManager?.registerListener(
            this,
            gyroscopeSensor,
            SensorManager.SENSOR_DELAY_GAME
        ) ?: false
        if (success) {
            isRegistered = true
            lastTimestampNanos = 0
            Timber.i("GyroAimingController registered successfully.")
        }
    }

    fun stop() {
        if (isRegistered) {
            sensorManager?.unregisterListener(this)
            isRegistered = false
            lastTimestampNanos = 0
            Timber.i("GyroAimingController unregistered.")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        if (!PrefManager.gyroAimingEnabled) {
            stop()
            return
        }

        if (lastTimestampNanos == 0L) {
            lastTimestampNanos = event.timestamp
            return
        }

        val dT = (event.timestamp - lastTimestampNanos) * 1.0e-9f
        lastTimestampNanos = event.timestamp

        // Angular speed around axes (rad/s)
        val gyroX = event.values[0] // Rotation around X-axis (pitch)
        val gyroY = event.values[1] // Rotation around Y-axis (yaw)

        val sensitivity = PrefManager.gyroSensitivity.coerceIn(0.1f, 10.0f)
        val invertXMult = if (PrefManager.gyroInvertX) -1f else 1f
        val invertYMult = if (PrefManager.gyroInvertY) -1f else 1f

        // Convert angular velocity to screen mouse pixel displacement
        // Combining Y-rotation (horizontal yaw) and Z/X pitch for motion aiming
        val deltaX = (-gyroY * 1200f * dT * sensitivity * invertXMult).toInt()
        val deltaY = (gyroX * 1200f * dT * sensitivity * invertYMult).toInt()

        if (deltaX != 0 || deltaY != 0) {
            val pointer = xServer.pointer ?: return
            val newX = (pointer.x + deltaX).coerceIn(0, xServer.screenInfo.width - 1)
            val newY = (pointer.y + deltaY).coerceIn(0, xServer.screenInfo.height - 1)
            pointer.setPosition(newX, newY)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
