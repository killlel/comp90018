package com.example.vinyl.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Select the genre/s\n you listen to",
            color = VinylPalette.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
        )

        Text(
            text = "Feel free to select as many as you want",
            color = VinylPalette.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 14.dp),
        )

        Spacer(Modifier.height(20.dp))

        GenreGrid(
            genres = state.genreOptions,
            selected = state.selectedGenres,
            enabled = !state.listenToEverything,
            onToggle = onToggleGenre,
            modifier = Modifier.weight(1f),
        )

        TextButton(onClick = { onSetListenToEverything(!state.listenToEverything) }) {
            Text(
                text = "I listen to everything",
                color = VinylPalette.TealAccent,
                fontSize = 14.sp,
                fontWeight = if (state.listenToEverything) FontWeight.Medium else FontWeight.Normal,
            )
        }

        state.errorMessage?.let {
            Text(it, color = VinylPalette.TealAccent, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        val canProceed = state.listenToEverything || state.selectedGenres.isNotEmpty()
        Button(
            onClick = onNext,
            enabled = canProceed && !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(top = 8.dp, bottom = 50.dp),
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

/** Two-column grid of tall pill buttons, filling the space between the heading and the button. */
@Composable
private fun GenreGrid(
    genres: List<GenreOption>,
    selected: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(genres, key = { it.slug }) { genre ->
            GenrePill(
                label = genre.label,
                selected = genre.slug in selected,
                dimmed = !enabled,
                onClick = { if (enabled) onToggle(genre.slug) },
            )
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
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.12f),
            )
            .border(
                width = 1.dp,
                color = if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = !dimmed, onClick = onClick),
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