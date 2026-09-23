package com.example.vinyl.data.location

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance between a letter's location and the reader's own, and how it's worded.
 *
 * Both ends are city centroids, so the number is only ever as precise as "which city" — the
 * banding in [formatDistance] is deliberate, not laziness. Showing "2847.3 km" would imply a
 * precision neither coordinate has.
 */
object Distance {

    private const val EARTH_RADIUS_KM = 6371.0088

    /** Anything past this is banded, because the exact figure stops meaning anything. */
    private const val FAR_AWAY_KM = 3000

    /**
     * Great-circle distance in kilometres, or null when either end is missing — a letter sent
     * without a location, or a reader who hasn't set one.
     */
    fun betweenOrNull(
        fromLat: Double?,
        fromLng: Double?,
        toLat: Double?,
        toLng: Double?,
    ): Double? {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return null
        return haversineKm(fromLat, fromLng, toLat, toLng)
    }

    /**
     * "< 1 km" · "12 km" · "1,340 km" · "3,000+ km". Null in, null out, so callers can hand the
     * result straight to a nullable label and let the screen show its fallback.
     */
    fun formatDistance(km: Double?): String? = when {
        km == null -> null
        km < 1 -> "< 1 km"
        km >= FAR_AWAY_KM -> "${grouped(FAR_AWAY_KM.toLong())}+ km"
        else -> "${grouped(km.roundToLong())} km"
    }

    /** Convenience for the common path: two pairs of coordinates straight to a label. */
    fun labelOrNull(
        fromLat: Double?,
        fromLng: Double?,
        toLat: Double?,
        toLng: Double?,
    ): String? = formatDistance(betweenOrNull(fromLat, fromLng, toLat, toLng))

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        // min(1.0, ...) guards asin against a hair over 1.0 from floating-point drift at
        // antipodal points, which would otherwise return NaN.
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
    }

    private fun grouped(value: Long): String = String.format(Locale.US, "%,d", value)
}
