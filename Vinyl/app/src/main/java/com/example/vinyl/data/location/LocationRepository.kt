package com.example.vinyl.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToLong

/**
 * What [LocationRepository.getCityCentroid] resolved to.
 *
 * Privacy contract: a raw device fix enters the repository and never leaves it. It is not
 * returned, not held in a field, and not logged. Callers only ever see a city centroid — the
 * centre of the user's city, which is what gets stored on the profile and copied onto a letter
 * at send time.
 */
sealed interface LocationResult {
    /** [lat]/[lng] are the city centroid, never the device's own position. */
    data class Success(val lat: Double, val lng: Double, val city: String) : LocationResult

    /** Location permission is not currently granted. */
    object PermissionDenied : LocationResult

    /** Permission is granted but no fix came back: GPS off, airplane mode, or timed out. */
    object LocationUnavailable : LocationResult

    /** Got a fix, but couldn't turn it into a city — usually no network, or no geocoder backend. */
    object GeocodeFailed : LocationResult
}

/**
 * The only place in the app that touches the device's real position. See [LocationResult] for the
 * privacy contract this class exists to enforce.
 */
class LocationRepository(private val context: Context) {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /**
     * Whether we can read location right now. Coarse is all this feature needs — granting
     * "approximate only" in the system dialog is a perfectly good outcome.
     */
    fun hasPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Resolves the user's city centroid, or explains why it couldn't.
     *
     * Safe to call without checking [hasPermission] first — it returns
     * [LocationResult.PermissionDenied] rather than throwing.
     */
    suspend fun getCityCentroid(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied

        val fix = currentFix() ?: return LocationResult.LocationUnavailable
        return toCityCentroid(fix.latitude, fix.longitude)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Last known position first: it's instant and costs no battery, and for a city centroid a fix
     * that's an hour stale is just as good as a fresh one. Only asks the hardware for a new fix
     * when there's nothing cached.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission() in getCityCentroid()
    private suspend fun currentFix(): Location? {
        fusedClient.lastLocation.awaitOrNull()?.let { return it }

        val cancellation = CancellationTokenSource()
        return withTimeoutOrNull(FIX_TIMEOUT_MS) {
            fusedClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .awaitOrNull()
        }.also { if (it == null) cancellation.cancel() }
    }

    /**
     * Two hops, and the second one is the point of the exercise.
     *
     * Reverse-geocoding a fix hands back an address whose coordinates are *the fix you passed in*,
     * just with a city label attached — storing that would still be storing the user's real
     * position. So we take only the city's name from hop one, then forward-geocode that name to
     * get a coordinate for the city itself.
     *
     * If the forward hop fails we fall back to rounding the fix to a ~11 km grid, so even the
     * degraded path never persists a precise point.
     */
    private suspend fun toCityCentroid(rawLat: Double, rawLng: Double): LocationResult {
        if (!Geocoder.isPresent()) return LocationResult.GeocodeFailed

        val geocoder = Geocoder(context, Locale.getDefault())

        val address = geocoder.reverse(rawLat, rawLng).firstOrNull()
            ?: return LocationResult.GeocodeFailed
        val city = address.cityName() ?: return LocationResult.GeocodeFailed

        val centroid = geocoder.forward(address.centroidQuery(city)).firstOrNull()

        return if (centroid != null) {
            LocationResult.Success(centroid.latitude, centroid.longitude, city)
        } else {
            LocationResult.Success(roundToGrid(rawLat), roundToGrid(rawLng), city)
        }
    }

    // --- Geocoder, across the API 33 split ------------------------------------------------------
    //
    // The blocking overloads are deprecated from 33 but remain the only option below it, so while
    // minSdk is 24 both paths have to exist. The 33+ overloads are already async, so only the
    // legacy branch needs moving off the caller's thread.

    private suspend fun Geocoder.reverse(lat: Double, lng: Double): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            awaitGeocode { listener -> getFromLocation(lat, lng, 1, listener) }
        } else {
            blockingGeocode {
                @Suppress("DEPRECATION")
                getFromLocation(lat, lng, 1)
            }
        }

    private suspend fun Geocoder.forward(query: String): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            awaitGeocode { listener -> getFromLocationName(query, 1, listener) }
        } else {
            blockingGeocode {
                @Suppress("DEPRECATION")
                getFromLocationName(query, 1)
            }
        }

    /**
     * Bridges a 33+ [Geocoder.GeocodeListener] call into a coroutine. `onError` is treated as an
     * empty result rather than an exception — the caller already handles "no match" as a normal
     * outcome, and the two are indistinguishable to the user.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun awaitGeocode(
        start: (Geocoder.GeocodeListener) -> Unit,
    ): List<Address> = suspendCancellableCoroutine { cont ->
        val listener = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (cont.isActive) cont.resume(addresses)
            }

            override fun onError(errorMessage: String?) {
                Log.w(TAG, "geocoder error: $errorMessage")
                if (cont.isActive) cont.resume(emptyList())
            }
        }
        try {
            start(listener)
        } catch (e: IllegalArgumentException) {
            if (cont.isActive) cont.resume(emptyList())
        }
    }

    private suspend fun blockingGeocode(block: () -> List<Address>?): List<Address> =
        withContext(Dispatchers.IO) {
            try {
                block().orEmpty()
            } catch (e: IOException) {
                // Geocoding is a network call on most devices; offline lands here.
                Log.w(TAG, "geocoder unavailable", e)
                emptyList()
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "geocoder rejected the input", e)
                emptyList()
            }
        }

    // --- Play Services Task -> coroutine ---------------------------------------------------------

    /**
     * Hand-rolled rather than pulling in kotlinx-coroutines-play-services for two call sites.
     * A failed task resolves to null; the caller already treats "no fix" as a normal outcome.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val TAG = "LocationRepository"
        const val FIX_TIMEOUT_MS = 10_000L

        /** ~11 km at the equator — coarse enough that it isn't a home address. */
        fun roundToGrid(value: Double): Double = (value * 10).roundToLong() / 10.0

        /** Prefer the city; fall back outward for regions where locality comes back empty. */
        fun Address.cityName(): String? = locality ?: subAdminArea ?: adminArea

        /** "Melbourne, Victoria, Australia" — qualified so the forward lookup isn't ambiguous. */
        fun Address.centroidQuery(city: String): String =
            listOfNotNull(city, adminArea, countryName).distinct().joinToString(", ")
    }
}
