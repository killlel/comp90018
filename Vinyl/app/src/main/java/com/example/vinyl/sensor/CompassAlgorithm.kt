package com.example.vinyl.sensor

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Smooths a noisy stream of compass headings, and combines a smoothed heading with a target
 * bearing to get the angle the arrow should be drawn at.
 *
 * Heading is a circular quantity (359 and 1 degrees are 2 degrees apart, not 358), so smoothing is
 * done by averaging sine/cosine and recombining with atan2, rather than a plain arithmetic mean.
 */
class CompassAlgorithm(
    private val smoothing: Float = 0.15f,
) {
    private var smoothedSin: Float? = null
    private var smoothedCos: Float? = null

    /** Feed one raw heading reading (degrees, 0..360). Returns the smoothed heading so far. */
    fun onReading(rawDegrees: Float): Float {
        val rad = Math.toRadians(rawDegrees.toDouble())
        val x = sin(rad).toFloat()
        val y = cos(rad).toFloat()

        smoothedSin = lerp(smoothedSin, x)
        smoothedCos = lerp(smoothedCos, y)

        return normalizeDegrees(
            Math.toDegrees(atan2(smoothedSin!!.toDouble(), smoothedCos!!.toDouble())).toFloat(),
        )
    }

    private fun lerp(previous: Float?, new: Float): Float =
        if (previous == null) new else previous + smoothing * (new - previous)

    companion object {
        /** Wraps a degrees value (which may be negative, or over 360) into 0..360. */
        fun normalizeDegrees(degrees: Float): Float = ((degrees % 360) + 360) % 360

        /** The angle to rotate the arrow by so it keeps pointing at [targetBearing] as the phone
         *  (and [deviceHeading]) turns under it. Both are true-north degrees. */
        fun arrowRotationDegrees(deviceHeading: Float, targetBearing: Float): Float =
            normalizeDegrees(targetBearing - deviceHeading)

        /** How close to "straight ahead" counts as facing the sender's direction. */
        const val DEFAULT_FACING_TOLERANCE_DEGREES = 15f

        /** How far [arrowRotationDegrees] is from 0 ("straight up"), always 0..180. */
        fun degreesFromFacingTarget(arrowRotationDegrees: Float): Float =
            minOf(arrowRotationDegrees, 360f - arrowRotationDegrees)

        /** True once [degreesFromFacingTarget] is within [toleranceDegrees] of 0. */
        fun isFacingTarget(
            arrowRotationDegrees: Float,
            toleranceDegrees: Float = DEFAULT_FACING_TOLERANCE_DEGREES,
        ): Boolean = degreesFromFacingTarget(arrowRotationDegrees) <= toleranceDegrees

        /** The 8 compass points, starting at north, every 45 degrees clockwise. */
        private val COMPASS_POINTS = listOf(
            "North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest",
        )

        /** [bearingDegrees] rounded to the nearest of the 8 compass points, e.g. "Southeast". */
        fun compassPointLabel(bearingDegrees: Float): String {
            val index = (normalizeDegrees(bearingDegrees) / 45f).roundToInt() % COMPASS_POINTS.size
            return COMPASS_POINTS[index]
        }
    }
}