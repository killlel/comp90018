package com.example.vinyl.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme

/**
 * Asks for location once, after sign-in, when the profile has none stored.
 *
 * Deliberately a single-purpose gate rather than a step inside a wider onboarding flow, because
 * the app has no onboarding flow yet — `profiles.onboarding_completed` exists in the schema but
 * nothing on the client sets it. When onboarding is built, this screen should be folded into it
 * as a step and this gate removed from MainActivity; nothing here touches that flag in the
 * meantime.
 *
 * Skipping is always allowed. A user without a location keeps a working app — they just can't
 * attach a location to a letter, and can't be shown a distance, until they set one in Settings.
 */
@Composable
fun LocationGateScreen(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: LocationViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val permission = rememberLocationPermissionRequest(
        hasPermission = viewModel::hasPermission,
        onAnswered = viewModel::captureAndSave,
    )

    LaunchedEffect(state.status) {
        if (state.status is LocationStatus.Saved) onDone()
    }

    // Only the denial branch cares; any other status means the dialog isn't the problem.
    val permanentlyDenied = permission.permanentlyDenied && state.status is LocationStatus.PermissionDenied

    LocationGateContent(
        state = state,
        permanentlyDenied = permanentlyDenied,
        onPrimary = {
            permission.request()
        },
        onSkip = onDone,
        modifier = modifier,
    )
}

@Composable
private fun LocationGateContent(
    state: LocationUiState,
    permanentlyDenied: Boolean,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Text(
            text = "Where are you\nlistening from?",
            color = VinylPalette.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = VinylPalette.TealAccent,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .size(96.dp),
        )
//        Text(
//            text = "Letters travel better with a sense of distance. " +
//                    "Vinyl shows the person you write to roughly how far away you are — " +
//                    "\"12 km away\", never where you actually are.",
//            color = VinylPalette.TextMuted,
//            fontSize = 15.sp,
//            textAlign = TextAlign.Center,
//            lineHeight = 22.sp,
//            modifier = Modifier.padding(top = 20.dp),
//        )

        Text(
            text = "We save your city, not your position. You can change or remove it any time in Settings.",
            color = VinylPalette.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )

        statusMessage(state.status, permanentlyDenied)?.let { message ->
            Text(
                text = message,
                color = VinylPalette.TealAccent,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        // One gap above whichever of spinner or buttons is showing, so they sit in the same place.
        Spacer(modifier = Modifier.height(32.dp))

        if (state.isLoading) {
            CircularProgressIndicator(color = VinylPalette.TealAccent)
        } else {
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VinylPalette.TealAccent,
                    contentColor = VinylPalette.Background,
                ),
            ) {
                Text(
                    text = if (permanentlyDenied) "Open app settings" else "Share my city",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
            }

            TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp)) {
                Text("Not now", color = VinylPalette.TextMuted, fontSize = 14.sp)
            }
        }
    }
}

/**
 * User-facing wording for each failed outcome. Lives here rather than in the ViewModel so the
 * Settings screen can phrase the same statuses differently.
 */
private fun statusMessage(status: LocationStatus, permanentlyDenied: Boolean): String? = when (status) {
    LocationStatus.Idle, LocationStatus.Saved, LocationStatus.Cleared -> null

    LocationStatus.PermissionDenied ->
        if (permanentlyDenied) {
            "Location is turned off for Vinyl. You can turn it back on in app settings."
        } else {
            "No problem — you can add your city later in Settings."
        }

    LocationStatus.Unavailable ->
        "Couldn't get a location. Check that location is switched on, then try again."

    LocationStatus.CityUnknown ->
        "Couldn't work out your city. Try again in a moment."

    LocationStatus.SaveFailed ->
        "Couldn't save your location. Check your connection, then try again."
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun LocationGateScreenPreview() {
    VinylTheme {
        LocationGateContent(
            state = LocationUiState(isLoading = false),
            permanentlyDenied = false,
            onPrimary = {},
            onSkip = {},
        )
    }
}