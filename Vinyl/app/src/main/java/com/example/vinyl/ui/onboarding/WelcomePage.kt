package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.vinyl.R

/**
 * First onboarding page: a short welcome and a "Get started" button that calls [onNext]. It has no
 * state and does not touch the ViewModel.
 */
@Composable
fun WelcomePage(onNext: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingPageLayout(
        title = "Welcome to Vinyl",
        description = "Share a song, send a little thought, and discover what others are listening to. Stay anonymous, be yourself.",
        modifier = modifier,
        illustration = R.drawable.onboarding_welcome,
        actions = { OnboardingPrimaryButton("Get started", onNext) },
    )
}