package com.example.vinyl.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.vinyl.ui.theme.VinylPalette

/**
 * Page 4, and the last onboarding step — [OnboardingViewModel.finishOnboarding] is what flips
 * `onboarding_completed`, so both "Turn On" and "Not Now" end up calling it.
 *
 * Requires `android.permission.POST_NOTIFICATIONS` in the manifest for API 33+ builds; below 33
 * there's no runtime permission and requesting is the whole decision.
 *
 * Thin wrapper around [NotificationContent] — same split as `LocationGateScreen`/
 * `LocationGateContent` — so the content composable can be previewed with plain state/lambdas
 * instead of a real permission launcher.
 */
@Composable
fun NotificationPage(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // The launcher's callback — not the click that opens it — is what actually knows the
    // outcome, so that's where finishOnboarding() belongs; firing it from the click would save
    // "enabled" before the person has answered the system dialog.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
        viewModel.finishOnboarding(onFinished = onFinish)
    }

    fun requestAndEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

            if (alreadyGranted) {
                viewModel.setNotificationsEnabled(true)
                viewModel.finishOnboarding(onFinished = onFinish)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // No runtime permission below API 33 — requesting is the whole decision.
            viewModel.setNotificationsEnabled(true)
            viewModel.finishOnboarding(onFinished = onFinish)
        }
    }

    NotificationContent(
        state = state,
        onTurnOn = ::requestAndEnable,
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Turn on the notification\nto know when you\nreceive a card",
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

        Button(
            onClick = onTurnOn,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(top = 32.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = VinylPalette.TealAccent,
                contentColor = VinylPalette.Background,
            ),
        ) {
            Text(
                text = if (state.isSaving) "Finishing…" else "Turn On",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
        }
        TextButton(onClick = onNotNow, modifier = Modifier.padding(top = 4.dp)) {
            Text("Not now", color = VinylPalette.TextMuted, fontSize = 14.sp)
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