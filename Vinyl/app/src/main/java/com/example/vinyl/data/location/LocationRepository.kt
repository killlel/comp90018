package com.example.vinyl.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
        return LocationResult.Success(city.lat, city.lng, city.displayLabel)
    }

    /**
     * The city label for a point already stored on the profile, for display only. Offline-safe.
     * "Chicago, US" once assets/cities.tsv has a country column (see [City.displayLabel])
     *
     * For anything saved since the switch to the city list this is an exact match. Rows saved
     * earlier hold a suburb centre, and resolve to the city that suburb belongs to.
     */
    suspend fun cityFor(lat: Double, lng: Double): String? =
        runCatching { cityIndex.nearest(lat, lng)?.displayLabel }.getOrNull()

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
     *
     * Two sources race, and the first fix wins:
     * - Fused (Play Services). High priority so it can use GPS when the app holds fine
     *   permission. With approximate only, Play Services quietly downgrades the request to
     *   balanced, which is Wi-Fi/cell positioning alone, and that is off whenever Google Location
     *   Accuracy is disabled (the default on emulators, and a common choice on phones).
     * - The platform GPS provider. Covers exactly that gap: since Android 12 an approximate-only
     *   app may use it and gets a coarsened position back. Device testing showed the fused
     *   request alone timing out every time in that state.
     *
     * Android's own cache is checked first, because an approximate-only app gets at most one
     * fresh GPS fix per 10 minutes: within that window the GPS provider stays silent and only
     * the cached fix is available.
     */
    private suspend fun currentFix(): Location? {
        recentPlatformFix()?.let { return it }

        // Both sources give up on their own after FIX_TIMEOUT_MS; this is a backstop in case
        // either never calls back at all.
        val fix = withTimeoutOrNull(FIX_TIMEOUT_MS + TIMEOUT_GRACE_MS) {
            channelFlow {
                launch { send(fusedFix()) }
                launch { send(platformGpsFix()) }
            }.filterNotNull().firstOrNull()
        }
        // No coordinates here, per the privacy contract — only that the fix failed.
        if (fix == null) Log.w(TAG, "No location fix within ${FIX_TIMEOUT_MS}ms")
        return fix
    }

    /** The newest fix Android already holds from any provider, if it's within [MAX_FIX_AGE_MS]. */
    @SuppressLint("MissingPermission") // guarded by hasPermission() in getCityCentroid()
    private fun recentPlatformFix(): Location? {
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
        val now = SystemClock.elapsedRealtimeNanos()
        return locationManager.getProviders(true)
            .mapNotNull { provider ->
                // A provider this permission level can't read throws instead of returning null.
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
            }
            .filter { (now - it.elapsedRealtimeNanos) / 1_000_000 <= MAX_FIX_AGE_MS }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    @SuppressLint("MissingPermission") // guarded by hasPermission() in getCityCentroid()
    private suspend fun fusedFix(): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
            .setDurationMillis(FIX_TIMEOUT_MS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            fusedClient.getCurrentLocation(request, cancellation.token).awaitOrNull()
        } finally {
            cancellation.cancel()
        }
    }

    @SuppressLint("MissingPermission") // guarded by hasPermission() in getCityCentroid()
    private suspend fun platformGpsFix(): Location? {
        // Before Android 12 the GPS provider needs fine permission and would throw.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return null
        }
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null

        return withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        LocationManager.GPS_PROVIDER,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { location -> if (cont.isActive) cont.resume(location) }
                } catch (e: SecurityException) {
                    Log.w(TAG, "GPS provider refused the request", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /**
     * Hand-rolled rather than pulling in kotlinx-coroutines-play-services for one call site.
     * A failed task resolves to null; the caller already treats "no fix" as a normal outcome.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            // Cancelled is the normal case when the other source answered first.
            if (!task.isSuccessful && !task.isCanceled) Log.w(TAG, "Location request failed", task.exception)
            if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val TAG = "LocationRepository"

        const val FIX_TIMEOUT_MS = 10_000L
        const val TIMEOUT_GRACE_MS = 2_000L

        /**
         * Oldest cached fix we'll accept instead of asking the hardware. Matches Android's
         * 10-minute limit on fresh fixes for approximate-only apps, so there's never a gap where
         * the cache is too old and the hardware won't answer yet.
         */
        const val MAX_FIX_AGE_MS = 10 * 60_000L
    }
}