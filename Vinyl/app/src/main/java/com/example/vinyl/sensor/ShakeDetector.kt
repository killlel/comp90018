package com.example.vinyl.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Reads the accelerometer and calls back when the user shakes the phone.
 *
 * This class is only the [SensorEventListener] plumbing: it reads the raw values, hands them to
 * [ShakeAlgorithm], and acts on what the algorithm says.
 *
 * The accelerometer is registered only while it is needed and unregistered as soon as it is not,
 * so it does not drain the battery. See [enableSensor] and [disableSensor].
 */
class ShakeDetector(
    private val context: Context,
    private val algorithm: ShakeAlgorithm = ShakeAlgorithm(),
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    private var onShake: (() -> Unit)? = null

    private val primarySensor = Sensor.TYPE_ACCELEROMETER

    /**
     * Starts listening to the accelerometer: fetches the SensorManager, then looks up the sensor.
     *
     * Devices (or standard emulators) might not have an accelerometer, and on some OEM builds the
     * sensor service itself can misbehave. Either way this must never crash the app; it only means
     * shaking has no effect. The envelope can still be opened by pressing and holding it, or with
     * the TalkBack action.
     */
    fun enableSensor(onShake: () -> Unit) {
        this.onShake = onShake
        Log.i(TAG, "Enabling sensor...")

        sensorManager = try {
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't reach the sensor service", e)
            null
        }

        val manager = sensorManager
        if (manager == null) {
            Log.i(TAG, "Sensors not supported")
            return
        }

        val foundSensor = manager.getDefaultSensor(primarySensor)
        sensor = foundSensor

        if (foundSensor == null) {
            Log.i(TAG, "Accelerometer is not supported")
        } else {
            try {
                manager.registerListener(this, foundSensor, SensorManager.SENSOR_DELAY_UI)
                Log.i(TAG, "Accelerometer is supported and listening")
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start listening to the accelerometer", e)
            }
        }
    }

    /**
     * Stops receiving sensor updates. Call this when the screen leaves the foreground, so the
     * sensor does not keep running and drain the battery.
     */
    fun disableSensor() {
        sensorManager?.let {
            try {
                it.unregisterListener(this)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't stop listening to the accelerometer", e)
            }
            sensorManager = null
            onShake = null
            Log.i(TAG, "Sensor disabled and unregistered successfully.")
        }
    }

    /**
     * Runs whenever the hardware reports a new reading. Every reading is handed to
     * [ShakeAlgorithm], which returns true on the exact reading that completes a shake; only then
     * is [onShake] called.
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor?.type != sensor?.type) return

        val (x, y, z) = event.values
        if (algorithm.onReading(x, y, z, System.currentTimeMillis())) {
            Log.i(TAG, "Shake detected")
            onShake?.invoke()
        }
    }

    /** Triggered if the sensor's accuracy changes. Not needed for a shake gesture. */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }

    private companion object {
        const val TAG = "ShakeDetector"
    }
}