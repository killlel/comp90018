package com.example.vinyl.ui.onboarding

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.example.vinyl.ui.settings.AvatarMakerBody
import com.example.vinyl.ui.settings.rememberAvatarAppearance
import com.example.vinyl.ui.theme.VinylPalette

@Composable
fun IconPage(viewModel: OnboardingViewModel, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val appearance = rememberAvatarAppearance()
    var localIcon by rememberSaveable { mutableIntStateOf(appearance.iconIndex) }
    var gradient by rememberSaveable { mutableIntStateOf(appearance.gradientIndex) }
    val remoteIndex = state.avatarOptions.indexOfFirst { it.slug == state.selectedAvatarSlug }
    val icon = if (state.avatarOptions.isEmpty()) localIcon else remoteIndex.coerceAtLeast(0)
    OnboardingPageLayout(
        title = "Pick your\nprofile icon",
        description = "Choose an icon and a background that feel like you.",
        modifier = modifier,
        actions = {
            OnboardingPrimaryButton(if (state.isSaving) "Saving…" else "Continue", onClick = {
                // Only save identifiers returned by the backend; never invent a foreign key.
                state.avatarOptions.getOrNull(icon)?.let { viewModel.selectAvatar(it.slug) }
                viewModel.saveAvatar {
                    appearance.save(icon, gradient, state.avatarOptions.getOrNull(icon)?.url)
                    onNext()
                }
            }, enabled = !state.isLoadingProfile && !state.isSaving)
        },
    ) {
        state.errorMessage?.let { Text(it, color = VinylPalette.TealAccent, textAlign = TextAlign.Center) }
        if (state.isLoadingProfile) CircularProgressIndicator(color = VinylPalette.TealAccent)
        else AvatarMakerBody(iconIndex = icon, gradientIndex = gradient,
            onIconChange = { index ->
                localIcon = index
                state.avatarOptions.getOrNull(index)?.let { viewModel.selectAvatar(it.slug) }
            }, onGradientChange = { gradient = it }, scrollable = false, avatarOptions = state.avatarOptions, horizontalPadding = 0.dp, compact = true)
    }
}
