package com.example.vinyl.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Detects which way the phone is currently facing. After [enableSensor] is called with a
 * callback, that callback is invoked repeatedly with the current heading, in degrees measured
 * clockwise from true north (0 = north, 90 = east, 180 = south, 270 = west). Only handles the
 * Android sensor APIs; the maths for smoothing out noisy readings lives separately in
 * [CompassAlgorithm].
 *
 *
 * One complication: the magnetometer points at magnetic north, which differs from true
 * (geographic) north by an amount - "declination" - that varies by location and can exceed 20
 * degrees. When [readerLat]/[readerLng] are supplied, that offset is looked up and corrected for,
 * so the reported heading is true north. Without them, the heading is left as magnetic north,
 * uncorrected.
 *
 * On a device with none of the required sensors, [enableSensor] simply returns false rather than
 * the app crashing.
 */
class CompassDetector(
    private val context: Context,
    private val readerLat: Double? = null,
    private val readerLng: Double? = null,
    private val algorithm: CompassAlgorithm = CompassAlgorithm(),
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private var onHeading: ((Float) -> Unit)? = null

    private val declinationDegrees: Float by lazy {
        if (readerLat == null || readerLng == null) {
            0f
        } else {
            try {
                GeomagneticField(readerLat.toFloat(), readerLng.toFloat(), 0f, System.currentTimeMillis())
                    .declination
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't compute magnetic declination; using magnetic north as-is", e)
                0f
            }
        }
    }

    // Held between readings for the accelerometer+magnetometer fallback path, which needs both
    // sensors' latest values at once to compute an orientation.
    private val latestAccel = FloatArray(3)
    private val latestMagnetic = FloatArray(3)
    private var haveAccel = false
    private var haveMagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val orientationOut = FloatArray(3)

    /**
     * Returns true the moment something was actually registered, false the moment it's clear
     * nothing can be - synchronously, so the caller (DetectCompassBearing) never has to guess
     * "unavailable" from a timeout; see CompassScreen's CompassState.Unavailable.
     */
    fun enableSensor(onHeading: (Float) -> Unit): Boolean {
        this.onHeading = onHeading
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
            return false
        }

        rotationVectorSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor != null) {
            return registerSafely(manager, rotationVectorSensor!!, "rotation vector")
        }

        Log.i(TAG, "No rotation-vector sensor; falling back to accelerometer + magnetometer")
        accelerometerSensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometerSensor = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometerSensor == null || magnetometerSensor == null) {
            Log.i(TAG, "No compass-capable sensors on this device")
            return false
        }
        val accelOk = registerSafely(manager, accelerometerSensor!!, "accelerometer")
        val magOk = registerSafely(manager, magnetometerSensor!!, "magnetometer")
        return accelOk && magOk
    }

    fun disableSensor() {
        sensorManager?.let {
            try {
                it.unregisterListener(this)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't stop listening to the compass sensors", e)
            }
            sensorManager = null
            onHeading = null
            haveAccel = false
            haveMagnetic = false
            Log.i(TAG, "Sensor disabled and unregistered successfully.")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor?.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                reportHeadingFromMatrix()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, latestAccel, 0, 3)
                haveAccel = true
                maybeReportFromRawPair()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, latestMagnetic, 0, 3)
                haveMagnetic = true
                maybeReportFromRawPair()
            }
            else -> Unit
        }
    }

    private fun maybeReportFromRawPair() {
        if (!haveAccel || !haveMagnetic) return
        val gotMatrix = SensorManager.getRotationMatrix(rotationMatrix, null, latestAccel, latestMagnetic)
        if (gotMatrix) reportHeadingFromMatrix()
    }

    private fun reportHeadingFromMatrix() {
        SensorManager.getOrientation(rotationMatrix, orientationOut)
        val magneticHeading = Math.toDegrees(orientationOut[0].toDouble()).toFloat()
        val trueHeading = CompassAlgorithm.normalizeDegrees(magneticHeading + declinationDegrees)
        onHeading?.invoke(algorithm.onReading(trueHeading))
    }

    private fun registerSafely(manager: SensorManager, sensor: Sensor, label: String): Boolean =
        try {
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            Log.i(TAG, "$label is supported and listening")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start listening to the $label", e)
            false
        }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this feature - low accuracy just means a jumpier heading, which the
        // smoothing in CompassAlgorithm already absorbs.
    }

    private companion object {
        const val TAG = "CompassDetector"
    }
}