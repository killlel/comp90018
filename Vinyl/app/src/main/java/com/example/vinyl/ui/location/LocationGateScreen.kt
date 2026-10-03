package com.example.vinyl.ui.location

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.ui.onboarding.OnboardingPageLayout
import com.example.vinyl.ui.onboarding.OnboardingPrimaryButton
import com.example.vinyl.ui.onboarding.OnboardingSecondaryButton
import com.example.vinyl.R
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme

/** Optional city sharing, using the existing permission and city-centroid save pipeline. */
@Composable
fun LocationGateScreen(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: LocationViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val permission = rememberLocationPermissionRequest(
        hasPermission = viewModel::hasPermission,
        onAnswered = viewModel::captureAndSave,
    )

    LaunchedEffect(state.status) {
        if (state.status is LocationStatus.Saved) {
            viewModel.clearStatus()
            onDone()
        }
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
    OnboardingPageLayout(
        title = "Share your city",
        description = "Let music put your city on the map. Other listeners see your city, never your precise location. You can change this in Settings.",
        modifier = modifier,
        illustration = R.drawable.onboarding_location,
        actions = {
            OnboardingPrimaryButton(
                text = if (state.isLoading) "Finding your city…" else if (permanentlyDenied) "Open app settings" else "Share my city",
                onClick = onPrimary, enabled = !state.isLoading)
            OnboardingSecondaryButton(text = "Not now", onClick = onSkip, enabled = !state.isLoading)
        },
    ) {
        statusMessage(state.status, permanentlyDenied)?.let { message ->
            Text(message, color = VinylPalette.TealAccent, textAlign = TextAlign.Center, fontSize = 13.sp)
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
