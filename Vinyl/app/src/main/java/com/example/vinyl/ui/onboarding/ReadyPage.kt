package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.vinyl.R
import com.example.vinyl.ui.theme.VinylPalette

@Composable
fun ReadyPage(viewModel: OnboardingViewModel, onEnter: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    OnboardingPageLayout(
        title = "You are ready",
        description = "Your next music discovery is waiting. Let's start sharing.",
        modifier = modifier,
        illustration = R.drawable.onboarding_done,
        actions = {
            OnboardingPrimaryButton(if (state.isSaving) "Finishing…" else "Enter Vinyl",
                onClick = { viewModel.finishOnboarding(onEnter) }, enabled = !state.isSaving)
        },
    ) {
        state.errorMessage?.let { Text(it, color = VinylPalette.TealAccent, textAlign = TextAlign.Center) }
    }
}
