package com.example.vinyl.ui.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.ProfileRepository
import com.example.vinyl.data.location.LocationRepository
import com.example.vinyl.data.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Outcome of the most recent [LocationViewModel.captureAndSave]. Every branch except [Saved] is a
 * message the user needs to see; the screen decides the wording.
 */
sealed interface LocationStatus {
    /** Nothing attempted yet, or the last outcome has been shown and cleared. */
    object Idle : LocationStatus

    object Saved : LocationStatus

    /** The user said no. Settings turns this into the deep-link-to-app-settings path. */
    object PermissionDenied : LocationStatus

    /** GPS off, airplane mode, or the fix timed out. Retrying is reasonable. */
    object Unavailable : LocationStatus

    /** Got a fix but couldn't name a city — usually offline. Retrying is reasonable. */
    object GeocodeFailed : LocationStatus

    /** The centroid resolved but Supabase rejected the write. */
    data class SaveFailed(val message: String?) : LocationStatus
}

data class LocationUiState(
    // Starts true: the ViewModel loads the saved location on construction, and a gate that reads
    // hasLocation before that lands would flash on screen for a frame.
    val isLoading: Boolean = true,
    val lat: Double? = null,
    val lng: Double? = null,
    /**
     * Display only, and not stored anywhere — `profiles` keeps the centroid alone. Set when a
     * location is captured, or on demand via [LocationViewModel.loadCityLabel].
     */
    val city: String? = null,
    val status: LocationStatus = LocationStatus.Idle,
) {
    /** Drives the onboarding gate (T5), the Settings copy (T6) and the send toggle (T7). */
    val hasLocation: Boolean get() = lat != null && lng != null
}

/**
 * Owns the user's stored location for onboarding, settings and the send screen.
 *
 * Deliberately does not request the permission itself — that needs an Activity, and the
 * permanently-denied state is only visible to the UI layer via
 * `shouldShowRequestPermissionRationale`. Screens launch the request, then call
 * [captureAndSave] once the user has answered.
 */
class LocationViewModel @JvmOverloads constructor(
    application: Application,
    private val locationRepository: LocationRepository = LocationRepository(application),
    private val profileRepository: ProfileRepository = ProfileRepository(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    init {
        loadSavedLocation()
    }

    /** Whether the permission is granted right now, for deciding what to show before asking. */
    fun hasPermission(): Boolean = locationRepository.hasPermission()

    /** Reads what's already on the profile. Cheap, and safe to call on every screen entry. */
    fun loadSavedLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            profileRepository.getLocation()
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(isLoading = false, lat = saved?.lat, lng = saved?.lng)
                    }
                }
                .onFailure {
                    // Nothing stored is a normal state, not an error worth surfacing here.
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    /**
     * Fills in [LocationUiState.city] for the stored centroid. Costs a geocoder round trip, so
     * only screens that actually show the city name (Settings) should call it.
     */
    fun loadCityLabel() {
        val state = _uiState.value
        if (state.city != null || !state.hasLocation) return

        viewModelScope.launch {
            val label = locationRepository.cityFor(state.lat!!, state.lng!!)
            if (label != null) _uiState.update { it.copy(city = label) }
        }
    }

    /**
     * Resolves the user's city centroid and writes it to their profile.
     *
     * Call after the permission dialog has been answered. Safe to call when it was denied — that
     * comes back as [LocationStatus.PermissionDenied] rather than throwing.
     */
    fun captureAndSave() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = LocationStatus.Idle) }

            when (val result = locationRepository.getCityCentroid()) {
                is LocationResult.Success -> save(result)
                LocationResult.PermissionDenied -> finish(LocationStatus.PermissionDenied)
                LocationResult.LocationUnavailable -> finish(LocationStatus.Unavailable)
                LocationResult.GeocodeFailed -> finish(LocationStatus.GeocodeFailed)
            }
        }
    }

    /** Call once a status message has been shown, so it isn't replayed on recomposition. */
    fun clearStatus() = _uiState.update { it.copy(status = LocationStatus.Idle) }

    private suspend fun save(result: LocationResult.Success) {
        profileRepository.saveLocation(result.lat, result.lng)
            .onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lat = result.lat,
                        lng = result.lng,
                        city = result.city,
                        status = LocationStatus.Saved,
                    )
                }
            }
            .onFailure { e -> finish(LocationStatus.SaveFailed(e.message)) }
    }

    private fun finish(status: LocationStatus) =
        _uiState.update { it.copy(isLoading = false, status = status) }
}
