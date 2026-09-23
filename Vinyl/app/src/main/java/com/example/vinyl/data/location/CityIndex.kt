package com.example.vinyl.data.location

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row of the bundled city list: a city's centre, and how many people live there. */
data class City(val name: String, val lat: Double, val lng: Double, val population: Int)

/**
 * Picks the city a position belongs to.
 *
 * Android's Geocoder can't answer this in Australia (and several other countries): its
 * "locality" is the suburb, so a Melbourne address comes back as "Collingwood" and a stored
 * "city centre" ends up a suburb centre a few km from home. Device testing caught that. Snapping
 * to a list of real cities gives everyone in the same city exactly the same point.
 *
 * Nearest city alone isn't enough either, because a suburb that happens to be listed as its own
 * town would win for anyone living near it. So a city first has to *qualify*: 50k+ people within
 * 50 km, or 1M+ within 80 km so a metro's outer fringe (Pakenham, 54 km from Melbourne) still
 * belongs to it. The nearest qualifying city wins — nearest, not largest, so Melbourne's wider
 * reach can't swallow Geelong. With nothing qualifying (remote areas) the nearest listed town
 * wins, whatever its size.
 */
object CitySnapping {

    private const val TOWN_MIN_POPULATION = 50_000
    private const val TOWN_REACH_KM = 50.0
    private const val METRO_MIN_POPULATION = 1_000_000
    private const val METRO_REACH_KM = 80.0

    fun nearestCity(cities: List<City>, lat: Double, lng: Double): City? {
        var qualifying: City? = null
        var qualifyingKm = Double.MAX_VALUE
        var any: City? = null
        var anyKm = Double.MAX_VALUE

        for (city in cities) {
            val km = Distance.greatCircleKm(lat, lng, city.lat, city.lng)
            if (km < anyKm) {
                any = city
                anyKm = km
            }
            if (km < qualifyingKm && qualifies(city, km)) {
                qualifying = city
                qualifyingKm = km
            }
        }
        return qualifying ?: any
    }

    private fun qualifies(city: City, km: Double): Boolean =
        (city.population >= TOWN_MIN_POPULATION && km <= TOWN_REACH_KM) ||
            (city.population >= METRO_MIN_POPULATION && km <= METRO_REACH_KM)

    /** Parses assets/cities.tsv: `name \t lat \t lng \t population`, `#` lines are comments. */
    fun parse(lines: Sequence<String>): List<City> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) return@mapNotNull null
            City(
                name = f[0],
                lat = f[1].toDoubleOrNull() ?: return@mapNotNull null,
                lng = f[2].toDoubleOrNull() ?: return@mapNotNull null,
                population = f[3].toIntOrNull() ?: 0,
            )
        }
        .toList()
}

/**
 * The bundled city list, loaded once on first use. About 32k cities from GeoNames (CC BY 4.0),
 * suburbs removed — see the header of assets/cities.tsv.
 */
class CityIndex(private val context: Context) {

    // ~100 ms to read and parse, so it's deferred until a lookup actually needs it.
    private val cities: List<City> by lazy {
        context.assets.open(ASSET).bufferedReader().useLines { CitySnapping.parse(it) }
    }

    suspend fun nearest(lat: Double, lng: Double): City? =
        withContext(Dispatchers.Default) { CitySnapping.nearestCity(cities, lat, lng) }

    private companion object {
        const val ASSET = "cities.tsv"
    }
}
