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
    fun `sub-kilometre reads as less than one`() {
        assertEquals("< 1 km", Distance.formatDistance(0.0))
        assertEquals("< 1 km", Distance.formatDistance(0.4))
        assertEquals("< 1 km", Distance.formatDistance(0.999))
    }

    @Test
    fun `whole kilometres are rounded and grouped`() {
        assertEquals("1 km", Distance.formatDistance(1.0))
        assertEquals("12 km", Distance.formatDistance(12.4))
        assertEquals("13 km", Distance.formatDistance(12.5))
        assertEquals("713 km", Distance.formatDistance(713.2))
        assertEquals("1,340 km", Distance.formatDistance(1340.0))
    }

    @Test
    fun `far distances are banded`() {
        assertEquals("3,000+ km", Distance.formatDistance(3000.0))
        assertEquals("3,000+ km", Distance.formatDistance(12000.0))
    }

    @Test
    fun `null distance formats as null`() {
        assertNull(Distance.formatDistance(null))
    }

    @Test
    fun `label helper short-circuits on a missing end`() {
        assertNull(Distance.labelOrNull(null, null, sydneyLat, sydneyLng))
        assertEquals("713 km", Distance.labelOrNull(melbourneLat, melbourneLng, sydneyLat, sydneyLng))
    }
}
