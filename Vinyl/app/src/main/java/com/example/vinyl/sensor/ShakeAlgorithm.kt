package com.example.vinyl.sensor

import kotlin.math.sqrt

/** Standard gravity, duplicated from android.hardware.SensorManager.GRAVITY_EARTH so this class
 *  has no Android dependency and can run in a plain JVM unit test. */
internal const val EARTH_GRAVITY = 9.80665f

/**
 *
 * Feed it accelerometer readings one at a time via [onReading]. It returns true on the exact
 * reading that should fire a shake, and stays false for everything else: readings below the
 * threshold, noise within a single physical peak, an incomplete shake (not enough peaks close
 * together), and any reading during the cooldown right after a trigger.
 *
 * Tuning:
 * - [thresholdGravity] - how hard a jolt has to be, in multiples of g. 2.7 is a deliberate shake,
 *   not a bump on a table or a phone in a pocket while walking.
 * - [requiredPeakCount] within [peakWindowMs] - a real shake is several jolts in quick succession;
 *   requiring more than one is what tells a shake apart from a single knock.
 * - [minPeakIntervalMs] - readings closer together than this count as the same jolt rather than a
 *   new one, since one swing of the arm crosses the threshold on several consecutive samples.
 * - [cooldownMs] - once a shake fires, further readings are ignored for this long, so one shake
 *   cannot fire the callback more than once.
 */
class ShakeAlgorithm(
    private val thresholdGravity: Float = 2.7f,
    private val minPeakIntervalMs: Long = 100,
    private val requiredPeakCount: Int = 3,
    private val peakWindowMs: Long = 1000,
    private val cooldownMs: Long = 1500,
) {
    // Null, not a numeric sentinel like Long.MIN_VALUE: `now - Long.MIN_VALUE` overflows a Long
    // and wraps around to a large negative number, which made the very first reading look like
    // it arrived too soon and get silently swallowed. Caught by this class's own unit tests.
    private var lastPeakAt: Long? = null
    private var windowStartAt: Long? = null
    private var peakCount = 0
    private var lastTriggerAt: Long? = null

    /** One accelerometer reading (m/s^2, as SensorEvent.values reports it) and its timestamp. */
    fun onReading(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        val gForce = sqrt(x * x + y * y + z * z) / EARTH_GRAVITY
        if (gForce <= thresholdGravity) return false

        val sinceLastPeak = lastPeakAt?.let { nowMs - it }
        if (sinceLastPeak != null && sinceLastPeak < minPeakIntervalMs) return false // same jolt
        lastPeakAt = nowMs

        val sinceWindowStart = windowStartAt?.let { nowMs - it }
        if (sinceWindowStart == null || sinceWindowStart > peakWindowMs) {
            windowStartAt = nowMs
            peakCount = 0
        }
        peakCount++

        if (peakCount < requiredPeakCount) return false
        peakCount = 0

        val sinceLastTrigger = lastTriggerAt?.let { nowMs - it }
        if (sinceLastTrigger != null && sinceLastTrigger < cooldownMs) return false // cooling down
        lastTriggerAt = nowMs
        return true
    }
}