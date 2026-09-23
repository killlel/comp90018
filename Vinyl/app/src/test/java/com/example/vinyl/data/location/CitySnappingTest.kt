package com.example.vinyl.data.location

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

/**
 * Runs the snapping rule against the real bundled city list, so a change to either the rule or
 * the data that breaks a known case fails here rather than on someone's phone.
 */
class CitySnappingTest {

    companion object {
        private lateinit var cities: List<City>

        @BeforeClass
        @JvmStatic
        fun loadCities() {
            // Unit tests run with the module directory as the working directory.
            cities = File("src/main/assets/cities.tsv").useLines { CitySnapping.parse(it) }
        }
    }

    private fun cityAt(lat: Double, lng: Double) = CitySnapping.nearestCity(cities, lat, lng)?.name

    @Test
    fun `list loads and has no suburb rows`() {
        assert(cities.size > 30_000) { "only ${cities.size} cities loaded" }
        assertNull(cities.firstOrNull { it.name == "City of Port Phillip" })
    }

    @Test
    fun `melbourne suburbs resolve to melbourne, not the suburb`() {
        assertEquals("Melbourne", cityAt(-37.80, 144.99)) // Collingwood
        assertEquals("Melbourne", cityAt(-37.7963, 144.9614)) // University of Melbourne
        assertEquals("Melbourne", cityAt(-37.745, 144.80)) // St Albans, listed as its own town
        assertEquals("Melbourne", cityAt(-38.14, 145.12)) // Frankston
    }

    @Test
    fun `a metro's outer fringe still belongs to it`() {
        assertEquals("Melbourne", cityAt(-38.07, 145.49)) // Pakenham, 54 km out
    }

    @Test
    fun `a nearby metro does not swallow a real city`() {
        assertEquals("Geelong", cityAt(-38.15, 144.36))
        assertEquals("Ballarat", cityAt(-37.56, 143.85))
        assertEquals("Utrecht", cityAt(52.09, 5.12))
        assertEquals("Yokohama", cityAt(35.44, 139.64))
    }

    @Test
    fun `mid-sized cities keep their own name`() {
        assertEquals("Mountain View", cityAt(37.39, -122.08))
    }

    @Test
    fun `remote places fall back to the nearest town`() {
        assertEquals("Mildura", cityAt(-34.19, 142.16))
        assertEquals("Alice Springs", cityAt(-25.0, 135.0))
    }

    @Test
    fun `other countries' suburbs resolve to their city`() {
        assertEquals("Sydney", cityAt(-33.89, 151.27)) // Bondi
        assertEquals("Sydney", cityAt(-33.815, 151.0)) // Parramatta
        assertEquals("Tokyo", cityAt(35.66, 139.70)) // Shibuya
    }

    @Test
    fun `everyone in a city gets the same point`() {
        val a = CitySnapping.nearestCity(cities, -37.80, 144.99)
        val b = CitySnapping.nearestCity(cities, -38.14, 145.12)
        assertEquals(a, b)
    }

    @Test
    fun `empty list gives null rather than crashing`() {
        assertNull(CitySnapping.nearestCity(emptyList(), 0.0, 0.0))
    }

    @Test
    fun `malformed rows are skipped`() {
        val parsed = CitySnapping.parse(sequenceOf("# comment", "", "Broken\tnot-a-number\t1\t2", "Ok\t1.0\t2.0\t3"))
        assertEquals(listOf(City("Ok", 1.0, 2.0, 3)), parsed)
    }
}
