package com.example.vinyl.ui.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.vinyl.data.location.LocationRepository

/**
 * Looks up the display label for a sender's city ("Chicago, US") from their coordinates, for
 * CompassScreen's [CompassUiState.cityLabel] subtitle.
 *
 * Returns null immediately if either coordinate is missing - there's nothing to look up.
 */
@Composable
internal fun rememberSenderCityLabel(senderLat: Double?, senderLng: Double?): State<String?> {
    val context = LocalContext.current
    val repository = remember(context) { LocationRepository(context) }
    val cityLabel = remember(senderLat, senderLng) { mutableStateOf<String?>(null) }

    LaunchedEffect(senderLat, senderLng) {
        cityLabel.value = if (senderLat == null || senderLng == null) {
            null
        } else {
            repository.cityFor(senderLat, senderLng)
        }
    }

    return cityLabel
}