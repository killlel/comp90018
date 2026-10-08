package com.example.vinyl.data.location

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance between a letter's location and the reader's own, and how it's worded.
 *
 * Every distance is shown as a band, never an exact figure. Both ends are approximate — a city
 * or suburb centre — so "12 km" would claim a precision neither coordinate has, and close-range
 * figures in particular would say more about where someone lives than the feature should.
 */
object Distance {

    private const val EARTH_RADIUS_KM = 6371.0088

    /** Shown as "< N km": the first bound the distance falls under wins. */
    private val UNDER_BANDS_KM = listOf(20, 50, 100)

    /** Shown as "N+ km": the largest floor the distance reaches wins. 100 fills the gap to 200. */
    private val OVER_BANDS_KM = listOf(3000, 2000, 1000, 200, 100)

    /**
     * Great-circle distance in kilometres, or null when either end is missing — a letter sent
     * without a location, or a reader who hasn't set one.
     */
    fun betweenOrNull(fromLat: Double?, fromLng: Double?, toLat: Double?, toLng: Double?): Double? {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return null
        return greatCircleKm(fromLat, fromLng, toLat, toLng)
    }

    /**
     * "< 20 km" · "< 50 km" · "< 100 km" · "100+ km" · "200+ km" · "1000+ km" · "2000+ km" ·
     * "3000+ km". Lower bounds are inclusive: exactly 20 km reads "< 50 km", exactly 200 reads
     * "200+ km". Null in, null out, so callers can hand the result straight to a nullable label
     * and let the screen show its fallback.
     */
    fun formatDistance(km: Double?): String? {
        if (km == null) return null
        UNDER_BANDS_KM.firstOrNull { km < it }?.let { return "< $it km" }
        // Anything reaching here is at least the last under-band, which is also the lowest floor.
        return "${OVER_BANDS_KM.first { km >= it }}+ km"
    }

    /** Convenience for the common path: two pairs of coordinates straight to a label. */
    fun labelOrNull(fromLat: Double?, fromLng: Double?, toLat: Double?, toLng: Double?): String? =
        formatDistance(betweenOrNull(fromLat, fromLng, toLat, toLng))

    /**
     * Initial great-circle bearing from one point to another, in degrees clockwise from true
     * north (0 = north, 90 = east, 180 = south, 270 = west), or null when either end is missing -
     * same reason as [betweenOrNull]: a letter sent without a location, or a reader who hasn't
     * set one.
     *
     * This is TRUE north, not magnetic north - [com.example.vinyl.sensor.CompassAlgorithm] is
     * what reads the device's own heading, which a magnetometer reports relative to magnetic
     * north. The two are combined at the point the arrow's rotation is computed, not here.
     *
     * On a straight line between two cities this bearing is constant. On the sphere it is not:
     * unlike distance, a *return* bearing is not simply this plus 180, and the bearing partway
     * along a long route can differ from the bearing at the start. For "which way is this letter
     * from me", the bearing computed from the reader's own fixed position is exactly right, and
     * is all this feature needs.
     */
    fun bearingOrNull(fromLat: Double?, fromLng: Double?, toLat: Double?, toLng: Double?): Double? {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return null
        return initialBearingDeg(fromLat, fromLng, toLat, toLng)
    }

    /** The bearing formula itself, kept apart from the null handling so it can be unit tested. */
    internal fun initialBearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lng2 - lng1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = atan2(y, x)
        // atan2 returns -180..180; shift into the 0..360 "clockwise from north" range the
        // compass arrow expects.
        return (Math.toDegrees(theta) + 360) % 360
    }

    /** Haversine distance in kilometres. Also used to snap a position to its nearest city. */
    internal fun greatCircleKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        // min(1.0, ...) guards asin against a hair over 1.0 from floating-point drift at
        // antipodal points, which would otherwise return NaN.
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
    }
}