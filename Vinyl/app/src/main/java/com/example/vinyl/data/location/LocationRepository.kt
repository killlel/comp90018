package com.example.vinyl.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What [LocationRepository.getCityCentroid] resolved to.
 *
 * Privacy contract: a raw device fix enters the repository and never leaves it. It is not
 * returned, not held in a field, and not logged. Callers only ever see a city centre — the same
 * point for everyone in that city, which is what gets stored on the profile and copied onto a
 * letter at send time.
 */
sealed interface LocationResult {
    /** [lat]/[lng] are the city's centre, never the device's own position. */
    data class Success(val lat: Double, val lng: Double, val city: String) : LocationResult

    /** Location permission is not currently granted. */
    object PermissionDenied : LocationResult

    /** Permission is granted but no fix came back: GPS off, airplane mode, or timed out. */
    object LocationUnavailable : LocationResult

    /** Got a fix but couldn't match it to a city — only if the bundled city list failed to load. */
    object CityUnknown : LocationResult
}

/**
 * The only place in the app that touches the device's real position. See [LocationResult] for the
 * privacy contract this class exists to enforce.
 */
class LocationRepository(private val context: Context, private val cityIndex: CityIndex = CityIndex(context)) {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /**
     * Whether we can read location right now. Coarse is all this feature needs — granting
     * "approximate only" in the system dialog is a perfectly good outcome.
     */
    fun hasPermission(): Boolean = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Resolves the user's city centre, or explains why it couldn't.
     *
     * Safe to call without checking [hasPermission] first — it returns
     * [LocationResult.PermissionDenied] rather than throwing. Needs no network: the city list
     * ships with the app.
     */
    suspend fun getCityCentroid(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied

        val fix = currentFix() ?: return LocationResult.LocationUnavailable
        val city = runCatching { cityIndex.nearest(fix.latitude, fix.longitude) }.getOrNull()
            ?: return LocationResult.CityUnknown
        return LocationResult.Success(city.lat, city.lng, city.name)
    }

    /**
     * The city name for a point already stored on the profile, for display only. Offline-safe.
     *
     * For anything saved since the switch to the city list this is an exact match. Rows saved
     * earlier hold a suburb centre, and resolve to the city that suburb belongs to.
     */
    suspend fun cityFor(lat: Double, lng: Double): String? =
        runCatching { cityIndex.nearest(lat, lng)?.name }.getOrNull()

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * A current fix, accepting a cached one only if it's recent.
     *
     * This used to take `lastLocation` whenever one existed, on the theory that a stale fix is
     * fine for a city. It isn't when the user taps "Update my location" after travelling: the
     * cache can still hold the city they left, and nothing refreshes it unless an app asks.
     * Device testing caught exactly that. [MAX_FIX_AGE_MS] keeps the speed of the cache for the
     * common case without ever saving a city the user has moved away from.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission() in getCityCentroid()
    private suspend fun currentFix(): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
            .setDurationMillis(FIX_TIMEOUT_MS)
            .build()

        val cancellation = CancellationTokenSource()
        // setDurationMillis makes the provider give up on its own; the coroutine timeout is a
        // backstop in case the Task never completes at all.
        return withTimeoutOrNull(FIX_TIMEOUT_MS + TIMEOUT_GRACE_MS) {
            fusedClient.getCurrentLocation(request, cancellation.token).awaitOrNull()
        }.also { if (it == null) cancellation.cancel() }
    }

    /**
     * Hand-rolled rather than pulling in kotlinx-coroutines-play-services for one call site.
     * A failed task resolves to null; the caller already treats "no fix" as a normal outcome.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 10_000L
        const val TIMEOUT_GRACE_MS = 2_000L

        /** Newest cached fix we'll accept instead of asking the hardware. */
        const val MAX_FIX_AGE_MS = 5 * 60_000L
    }
}
