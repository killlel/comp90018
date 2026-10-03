package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.vinyl.R

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
