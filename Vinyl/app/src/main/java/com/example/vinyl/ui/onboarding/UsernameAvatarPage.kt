package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Compatibility entry point; the pager now presents name and avatar as separate steps. */
@Deprecated("Use NamePage followed by IconPage")
@Composable
fun UsernameAvatarPage(viewModel: OnboardingViewModel, onNext: () -> Unit, modifier: Modifier = Modifier) {
    NamePage(viewModel, onNext, modifier)
}
