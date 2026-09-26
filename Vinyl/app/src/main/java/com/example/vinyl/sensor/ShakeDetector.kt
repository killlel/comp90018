package com.example.vinyl.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * SHAKE SENSOR CONTROLLER
 *  This class is only the SensorEventListener plumbing: read the raw values,
 *  hand them to the algorithm, act on what it says.
 *
 * ENERGY CONSERVATION: the accelerometer is registered only while needed
 * and unregistered immediately once not - see [enableSensor] / [disableSensor].
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
     * Registers this class to start listening to the physical accelerometer hardware sensor, the
     * same two steps as Barometer.enableSensor(): fetch the SensorManager, then look up the
     * specific sensor.
     *
     * Devices (or standard emulators) might not have an accelerometer, and on some OEM builds the
     * sensor service itself can misbehave - either way this must never crash the app, it should
     * just mean shaking has no effect. [onShake] itself still works either way through the "Open
     * it now" button, exactly like the barometer screen still shows its "not supported" label
     * rather than crashing.
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
     * Unregisters our listener to stop receiving sensor updates.
     * ALWAYS call this when the screen goes into the background, to prevent battery drain.
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
     * ONSENSORCHANGED: runs whenever the hardware reports a new reading. Every reading is handed
     * to [ShakeAlgorithm], which returns true on the exact reading that completes a shake; only
     * then is [onShake] called.
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