package com.example.vinyl.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceTest {

    // Melbourne and Sydney city centres — roughly 713 km apart.
    private val melbourneLat = -37.8136
    private val melbourneLng = 144.9631
    private val sydneyLat = -33.8688
    private val sydneyLng = 151.2093

    @Test
    fun `known city pair is within a few km of the real distance`() {
        val km = Distance.betweenOrNull(melbourneLat, melbourneLng, sydneyLat, sydneyLng)!!
        assertEquals(713.0, km, 5.0)
    }

    @Test
    fun `same point is zero`() {
        val km = Distance.betweenOrNull(melbourneLat, melbourneLng, melbourneLat, melbourneLng)!!
        assertEquals(0.0, km, 0.001)
    }

    @Test
    fun `antipodal points do not produce NaN`() {
        val km = Distance.betweenOrNull(0.0, 0.0, 0.0, 180.0)!!
        assertEquals(20015.0, km, 5.0)
    }

    @Test
    fun `missing either end gives null`() {
        assertNull(Distance.betweenOrNull(null, melbourneLng, sydneyLat, sydneyLng))
        assertNull(Distance.betweenOrNull(melbourneLat, null, sydneyLat, sydneyLng))
        assertNull(Distance.betweenOrNull(melbourneLat, melbourneLng, null, sydneyLng))
        assertNull(Distance.betweenOrNull(melbourneLat, melbourneLng, sydneyLat, null))
    }

    @Test
    fun `close range is banded under 20, 50 and 100`() {
        assertEquals("< 20 km", Distance.formatDistance(0.0))
        assertEquals("< 20 km", Distance.formatDistance(19.99))
        assertEquals("< 50 km", Distance.formatDistance(20.0))
        assertEquals("< 50 km", Distance.formatDistance(49.99))
        assertEquals("< 100 km", Distance.formatDistance(50.0))
        assertEquals("< 100 km", Distance.formatDistance(99.99))
    }

    @Test
    fun `hundred to two hundred fills the gap as 100 plus`() {
        assertEquals("100+ km", Distance.formatDistance(100.0))
        assertEquals("100+ km", Distance.formatDistance(199.99))
    }

    @Test
    fun `longer range is banded by floor`() {
        assertEquals("200+ km", Distance.formatDistance(200.0))
        assertEquals("200+ km", Distance.formatDistance(999.99))
        assertEquals("1000+ km", Distance.formatDistance(1000.0))
        assertEquals("1000+ km", Distance.formatDistance(1999.99))
        assertEquals("2000+ km", Distance.formatDistance(2000.0))
        assertEquals("2000+ km", Distance.formatDistance(2999.99))
    }

    @Test
    fun `far distances top out at 3000 plus`() {
        assertEquals("3000+ km", Distance.formatDistance(3000.0))
        assertEquals("3000+ km", Distance.formatDistance(20015.0))
    }

    @Test
    fun `null distance formats as null`() {
        assertNull(Distance.formatDistance(null))
    }

    @Test
    fun `label helper short-circuits on a missing end`() {
        assertNull(Distance.labelOrNull(null, null, sydneyLat, sydneyLng))
        assertEquals("200+ km", Distance.labelOrNull(melbourneLat, melbourneLng, sydneyLat, sydneyLng))
    }
}
