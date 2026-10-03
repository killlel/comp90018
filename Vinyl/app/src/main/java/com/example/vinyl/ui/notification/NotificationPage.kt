package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.notification.rememberNotificationPermissionRequest
import com.example.vinyl.R
import com.example.vinyl.ui.theme.VinylPalette

/** Explains the benefit before the user requests permission. Completion is saved on Ready. */
@Composable
fun NotificationPage(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    // Set once the user has said no, so the page can explain instead of moving on silently.
    var denied by rememberSaveable { mutableStateOf(false) }

    // Advance only after Android confirms permission; denial keeps the choice optional.
    val permission = rememberNotificationPermissionRequest(
        onAnswered = { granted ->
            viewModel.setNotificationsEnabled(granted)
            if (granted) {
                denied = false
                onFinish()
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
            onFinish()
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
    OnboardingPageLayout(
        title = "Turn on\nnotifications",
        description = "A little music, just for you. Get a reminder when a music card arrives. You can turn notifications off any time.",
        modifier = modifier,
        illustration = R.drawable.onboarding_notification,
        actions = {
            OnboardingPrimaryButton(if (permanentlyDenied) "Open app settings" else "Turn on", onTurnOn)
            OnboardingSecondaryButton("Not now", onNotNow)
        },
    ) {
        if (denied) Text(
            if (permanentlyDenied) "Notifications are turned off for Vinyl. You can enable them in app settings."
            else "No problem. You can turn notifications on later in your phone's settings.",
            color = VinylPalette.TealAccent, fontSize = 13.sp, textAlign = TextAlign.Center)
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
