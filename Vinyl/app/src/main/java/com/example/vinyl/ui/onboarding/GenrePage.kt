package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.data.onboarding.GenreOption
import com.example.vinyl.ui.theme.VinylPalette

/**
 * Page 2. Genre options are read from the `genres` table (see [OnboardingViewModel]); the screen
 * shows each option's label but selects and saves its slug, because the database rejects labels.
 *
 * "Listen to everything" and picking individual genres are mutually exclusive - choosing one
 * clears the other, both in the UI and in what gets saved (see
 * [com.example.vinyl.data.onboarding.OnboardingRepository.setFavoriteGenres]).
 *
 * Thin wrapper around [GenreContent] — same split as `LocationGateScreen`/`LocationGateContent` —
 * so the content composable can be previewed with plain state instead of a real [OnboardingViewModel].
 */
@Composable
fun GenrePage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    GenreContent(
        state = state,
        onToggleGenre = viewModel::toggleGenre,
        onSetListenToEverything = viewModel::setListenToEverything,
        onNext = { viewModel.saveGenres(onSaved = onNext) },
        modifier = modifier,
    )
}

@Composable
private fun GenreContent(
    state: OnboardingUiState,
    onToggleGenre: (String) -> Unit,
    onSetListenToEverything: (Boolean) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPageLayout(
        title = "Select your\nmusic vibes",
        description = "Pick the styles you enjoy to help Vinyl find music for you.",
        modifier = modifier,
        actions = {
            OnboardingPrimaryButton(if (state.isSaving) "Saving…" else "Continue", onNext,
                enabled = !state.isLoadingProfile && !state.isSaving)
            TextButton(onClick = { onSetListenToEverything(!state.listenToEverything) }, enabled = !state.isLoadingProfile && !state.isSaving) {
                Text(if (state.listenToEverything) "✓ I listen to everything" else "I listen to everything",
                    color = VinylPalette.TealAccent, fontSize = 14.sp)
            }
        },
    ) {
        state.errorMessage?.let { Text(it, color = VinylPalette.TealAccent, fontSize = 13.sp, textAlign = TextAlign.Center) }
        if (state.isLoadingProfile) androidx.compose.material3.CircularProgressIndicator(color = VinylPalette.TealAccent)
        else GenreGrid(state.genreOptions, state.selectedGenres, !state.listenToEverything && !state.isSaving, onToggleGenre)
    }
}

/** Two-column grid of tall pill buttons, filling the space between the heading and the button. */
@Composable
private fun GenreGrid(
    genres: List<GenreOption>,
    selected: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        genres.chunked(2).forEach { row ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { genre ->
                    Box(Modifier.weight(1f)) {
                        GenrePill(genre.label, genre.slug in selected, !enabled, onClick = { onToggle(genre.slug) })
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GenrePill(
    label: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.12f),
            )
            .border(
                width = 1.dp,
                color = if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
            )
            .toggleable(value = selected, enabled = !dimmed, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            color = if (selected) {
                VinylPalette.Background
            } else {
                VinylPalette.TextPrimary.copy(alpha = if (dimmed) 0.35f else 1f)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, heightDp = 800)
@Composable
private fun GenreContentPreview() {
    GenreContent(
        state = OnboardingUiState(
            isLoadingProfile = false,
            genreOptions = listOf(
                GenreOption("pop", "Pop"),
                GenreOption("jazz", "Jazz"),
                GenreOption("soul", "Soul"),
                GenreOption("k_pop", "K-pop"),
                GenreOption("rnb", "R&B"),
                GenreOption("folk", "Folk"),
            ),
            selectedGenres = setOf("jazz", "soul"),
        ),
        onToggleGenre = {},
        onSetListenToEverything = {},
        onNext = {},
    )
}
