package com.example.vinyl.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Checks the compass bearing math against known geographic facts, independent of any sensor or
 * UI code - this is the "is the arrow pointing the right way" half of the feature; the device's
 * own heading (which way the phone itself faces) is a separate, unrelated concern handled by
 * CompassAlgorithm.
 */
class BearingTest {

    private fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double) =
        Distance.initialBearingDeg(lat1, lng1, lat2, lng2)

    @Test
    fun `due north is exactly 0 degrees`() {
        assertEquals(0.0, bearing(-37.81, 144.96, 0.0, 144.96), 0.001)
    }

    @Test
    fun `due east is exactly 90 degrees`() {
        assertEquals(90.0, bearing(0.0, 0.0, 0.0, 10.0), 0.001)
    }

    @Test
    fun `due south is exactly 180 degrees`() {
        assertEquals(180.0, bearing(-37.81, 144.96, -80.0, 144.96), 0.001)
    }

    @Test
    fun `due west is exactly 270 degrees, not negative 90`() {
        // atan2 alone would give -90 here; the +360 wrap in initialBearingDeg is what makes this
        // the 270 the arrow's rotation math expects, not a negative angle.
        assertEquals(270.0, bearing(0.0, 0.0, 0.0, -10.0), 0.001)
    }

    @Test
    fun `Melbourne to Sydney is roughly north-east, not some other quadrant`() {
        val b = bearing(-37.81, 144.96, -33.87, 151.21)
        assertEquals(54.0, b, 1.0) // Sydney is NE of Melbourne; cross-checked against an independent haversine-bearing implementation
    }

    @Test
    fun `the result always lands in 0 until 360, never negative`() {
        // A spread of real and made-up coordinate pairs, including ones that would naturally
        // produce a negative atan2 result before the wrap-around is applied.
        val pairs = listOf(
            Triple(51.51, -0.13, 40.71 to -74.01),
            Triple(-33.87, 151.21, -37.81 to 144.96),
            Triple(35.68, 139.69, -37.81 to 144.96),
            Triple(0.0, 0.0, 0.0 to -179.0),
        )
        pairs.forEach { (lat1, lng1, dest) ->
            val b = bearing(lat1, lng1, dest.first, dest.second)
            assert(b in 0.0..360.0) { "bearing $b out of range for ($lat1,$lng1)->$dest" }
        }
    }

    @Test
    fun `missing either end gives null, same as the distance calculation does`() {
        assertNull(Distance.bearingOrNull(null, 144.96, -33.87, 151.21))
        assertNull(Distance.bearingOrNull(-37.81, 144.96, null, 151.21))
        assertNull(Distance.bearingOrNull(null, null, null, null))
    }

    @Test
    fun `both ends present gives a real bearing`() {
        assertEquals(54.0, Distance.bearingOrNull(-37.81, 144.96, -33.87, 151.21)!!, 1.0)
    }
}