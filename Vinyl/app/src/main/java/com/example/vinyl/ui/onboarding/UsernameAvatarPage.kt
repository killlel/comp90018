package com.example.vinyl.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.data.onboarding.AvatarOption
import com.example.vinyl.ui.theme.VinylPalette

/**
 * Page 1. The display name is read-only here — it was assigned by the database the moment the
 * account was created (see `assign_profile_display_name` in the onboarding migration) and cannot
 * be changed later, by design. The only choice on this page is the icon.
 *
 * Thin wrapper around [UsernameAvatarContent] — same split as `LocationGateScreen`/
 * `LocationGateContent` — so the content composable can be previewed with plain state instead of
 * a real [OnboardingViewModel].
 */
@Composable
fun UsernameAvatarPage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    UsernameAvatarContent(
        state = state,
        onSelectAvatar = viewModel::selectAvatar,
        onNext = { viewModel.saveAvatar(onSaved = onNext) },
        modifier = modifier,
    )
}

@Composable
private fun UsernameAvatarContent(
    state: OnboardingUiState,
    onSelectAvatar: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))


        Text(
            text = "You're known here as",
            color = VinylPalette.TextMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )

        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(50))
                .border(
                    width = 1.dp,
                    color = VinylPalette.TealAccent,
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (state.isLoadingProfile) "…" else state.displayName,
                color = VinylPalette.TealAccent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "Pick your profile icon",
            color = VinylPalette.TextPrimary,
            fontSize = 33.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 50.sp,
            modifier = Modifier.padding(top = 50.dp)
        )


        // Fills the whole space between the intro text and the button — no dead gap regardless
        // of avatar count or screen height, and the icons stay centered within it.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLoadingProfile) {
                CircularProgressIndicator(color = VinylPalette.TealAccent)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.avatarOptions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            row.forEach { avatar ->
                                AvatarIcon(
                                    avatar = avatar,
                                    selected = avatar.slug == state.selectedAvatarId,
                                    onClick = { onSelectAvatar(avatar.slug) },
                                )
                            }
                        }
                    }
                }
            }
        }

        state.errorMessage?.let {
            Text(it, color = VinylPalette.TealAccent, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Button(
            onClick = onNext,
            enabled = state.selectedAvatarId != null && !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(top = 8.dp, bottom = 55.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = VinylPalette.TealAccent,
                contentColor = VinylPalette.Background,
            ),
        ) {
            Text(
                text = if (state.isSaving) "Saving…" else "Next",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * Placeholder rendering: a big plain circle with the option's initials. Swap for `Image` loading
 * `avatar.assetName` from drawables once the real icon set exists — keep the 96dp size, it's
 * deliberately large per the "zoomed in" direction on this page.
 */
@Composable
private fun AvatarIcon(avatar: AvatarOption, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(VinylPalette.TextMuted.copy(alpha = 0.15f))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.3f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatar.slug.takeLast(2),
            color = VinylPalette.TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, heightDp = 800)
@Composable
private fun UsernameAvatarContentPreview() {
    UsernameAvatarContent(
        state = OnboardingUiState(
            isLoadingProfile = false,
            displayName = "Happy Giraffe",
            avatarOptions = (1..8).map { n ->
                AvatarOption(slug = "avatar_%02d".format(n), assetName = "placeholder", sortOrder = n)
            },
            selectedAvatarId = "avatar_04",
        ),
        onSelectAvatar = {},
        onNext = {},
    )
}