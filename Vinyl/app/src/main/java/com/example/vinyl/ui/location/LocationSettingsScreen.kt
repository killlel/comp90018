package com.example.vinyl.ui.location

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme

/**
 * Where the user changes or removes the location attached to their letters.
 *
 * Scoped to location alone rather than being a general Settings screen, for the same reason
 * LocationGateScreen isn't an onboarding flow: neither exists yet, and inventing one here would
 * squat on someone else's sprint. Fold this in as a section when a real Settings screen lands.
 */
@Composable
fun LocationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasAsked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAsked = true
        viewModel.captureAndSave()
    }

    // Only costs a geocoder round trip when there's a centroid and no name for it yet.
    LaunchedEffect(state.hasLocation) { viewModel.loadCityLabel() }

    val permanentlyDenied = remember(state.status, hasAsked) {
        state.status is LocationStatus.PermissionDenied && context.isLocationPermanentlyDenied(hasAsked)
    }

    LocationSettingsContent(
        state = state,
        permanentlyDenied = permanentlyDenied,
        onBack = onBack,
        onUpdate = {
            if (permanentlyDenied) context.openAppSettings() else launcher.launch(LOCATION_PERMISSIONS)
        },
        onRemove = viewModel::clearLocation,
        modifier = modifier,
    )
}

@Composable
private fun LocationSettingsContent(
    state: LocationUiState,
    permanentlyDenied: Boolean,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = VinylPalette.TextPrimary,
                )
            }
            Text(
                text = "Location",
                color = VinylPalette.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "YOUR CITY",
                color = VinylPalette.TextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
            )

            Text(
                text = currentLocationLabel(state),
                color = VinylPalette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = "Letters you send can carry roughly how far away you are — " +
                    "\"12 km away\", never where you actually are. We save your city, " +
                    "not your position.",
                color = VinylPalette.TextMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            statusMessage(state.status, permanentlyDenied)?.let { message ->
                Text(
                    text = message,
                    color = VinylPalette.TealAccent,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    color = VinylPalette.TealAccent,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Button(
                    onClick = onUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VinylPalette.TealAccent,
                        contentColor = VinylPalette.Background,
                    ),
                ) {
                    Text(
                        text = primaryButtonLabel(state, permanentlyDenied),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                }

                if (state.hasLocation) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove my location", color = VinylPalette.TextMuted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun currentLocationLabel(state: LocationUiState): String = when {
    !state.hasLocation -> "Not set"
    state.city != null -> state.city
    // Centroid stored but the name couldn't be resolved — offline, usually.
    else -> "Saved"
}

private fun primaryButtonLabel(state: LocationUiState, permanentlyDenied: Boolean): String = when {
    permanentlyDenied -> "Open app settings"
    state.hasLocation -> "Update my location"
    else -> "Add my location"
}

private fun statusMessage(status: LocationStatus, permanentlyDenied: Boolean): String? = when (status) {
    LocationStatus.Idle -> null

    LocationStatus.Saved -> "Location updated."

    LocationStatus.Cleared -> "Location removed. Letters you send won't carry a distance."

    LocationStatus.PermissionDenied ->
        if (permanentlyDenied) {
            "Location is turned off for Vinyl. Turn it back on in app settings, then try again."
        } else {
            "Vinyl needs location permission to work out your city."
        }

    LocationStatus.Unavailable ->
        "Couldn't get a location. Check that location is switched on, then try again."

    LocationStatus.GeocodeFailed ->
        "Couldn't work out your city. Check your connection, then try again."

    is LocationStatus.SaveFailed ->
        "Couldn't save. ${status.message ?: "Try again in a moment."}"
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun LocationSettingsPreview() {
    VinylTheme {
        LocationSettingsContent(
            state = LocationUiState(isLoading = false, lat = -37.81, lng = 144.96, city = "Melbourne"),
            permanentlyDenied = false,
            onBack = {},
            onUpdate = {},
            onRemove = {},
        )
    }
}
