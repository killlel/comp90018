package com.example.vinyl.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.notification.rememberNotificationPermissionRequest
import com.example.vinyl.ui.theme.VinylPalette

/**
 * Page 4, and the last onboarding step - [OnboardingViewModel.finishOnboarding] is what flips
 * `onboarding_completed`. It runs when notifications are allowed, or when the user taps "Not now".
 * Saying no in the system dialog does NOT move on by itself: the page explains, and offers the
 * app's notification settings once Android has stopped asking - the same behaviour as the
 * location page.
 *
 * Android owns the permission; nothing here is stored (see NotificationPermissionSupport.kt).
 * Needs `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` in the
 * manifest, or Android 13+ never shows the dialog.
 *
 * Thin wrapper around [NotificationContent] - same split as `LocationGateScreen`/
 * `LocationGateContent` - so the content composable can be previewed with plain state/lambdas.
 */
@Composable
fun NotificationPage(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    // Set once the user has said no, so the page can explain instead of moving on silently.
    var denied by rememberSaveable { mutableStateOf(false) }

    // The callback - not the click that opens the dialog - is what knows the outcome, so that's
    // where finishOnboarding() belongs: firing it from the click would save "enabled" before the
    // person has answered.
    val permission = rememberNotificationPermissionRequest(
        onAnswered = { granted ->
            viewModel.setNotificationsEnabled(granted)
            if (granted) {
                denied = false
                viewModel.finishOnboarding(onFinished = onFinish)
            } else {
                denied = true
            }
        },
    )

    NotificationContent(
        state = state,
        denied = denied,
        permanentlyDenied = permission.permanentlyDenied,
        onTurnOn = permission.request,
        onNotNow = {
            viewModel.setNotificationsEnabled(false)
            viewModel.finishOnboarding(onFinished = onFinish)
        },
        modifier = modifier,
    )
}

@Composable
private fun NotificationContent(
    state: OnboardingUiState,
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    denied: Boolean = false,
    permanentlyDenied: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Everything above the buttons, centred in the space they leave - so the text sits higher
        // than when it was centred together with them.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Turn on the \nnotification for your \ndaily record",
                color = VinylPalette.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
            )

            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = VinylPalette.TealAccent,
                modifier = Modifier
                    .padding(top = 40.dp, bottom = 40.dp)
                    .size(120.dp),
            )

            state.errorMessage?.let {
                Text(
                    it,
                    color = VinylPalette.TealAccent,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (denied) {
                Text(
                    text = if (permanentlyDenied) {
                        "Notifications are turned off for Vinyl. You can turn them on in app settings."
                    } else {
                        "No problem - you can turn notifications on later in your phone's settings."
                    },
                    color = VinylPalette.TealAccent,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        // Same block as the location page, so the buttons sit at the same height on both.
        OnboardingActionArea {
            OnboardingPrimaryButton(
                text = when {
                    state.isSaving -> "Finishing…"
                    permanentlyDenied -> "Open app settings"
                    else -> "Turn On"
                },
                onClick = onTurnOn,
            )
            OnboardingSecondaryButton(text = "Not now", onClick = onNotNow)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, heightDp = 800)
@Composable
private fun NotificationContentPreview() {
    NotificationContent(
        state = OnboardingUiState(isLoadingProfile = false),
        onTurnOn = {},
        onNotNow = {},
    )
}