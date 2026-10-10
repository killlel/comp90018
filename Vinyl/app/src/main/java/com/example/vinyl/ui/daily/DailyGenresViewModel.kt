package com.example.vinyl.ui.daily

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.onboarding.GenreOption
import com.example.vinyl.data.onboarding.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyGenresUiState(
    /** Empty until loaded, or if loading failed - the screen hides the section then. */
    val options: List<GenreOption> = emptyList(),
    /** Selected genre slugs (`k_pop`), never labels. */
    val selected: Set<String> = emptySet(),
    /** "Any genre": genre carries no weight in today's matching. */
    val anyGenre: Boolean = false,
)

/**
 * The genre chips on the daily questionnaire.
 *
 * The options come from the `genres` table, so a chip carries the slug the matcher compares
 * against. The chips start empty on purpose: when none are selected the app sends no genres,
 * and the server falls back to the user's saved favourite genres. Picking chips replaces those
 * favourites for today's pull rather than adding to them. "Any genre" sends a value that matches
 * no real genre, so genre adds nothing to the score.
 */
class DailyGenresViewModel(
    private val repository: OnboardingRepository = OnboardingRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyGenresUiState())
    val uiState: StateFlow<DailyGenresUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var loading = false

    /** Call each time the questionnaire opens: nothing selected, loading the options on first use. */
    fun reset() {
        _uiState.update { it.copy(selected = emptySet(), anyGenre = false) }
        loadIfNeeded()
    }

    fun toggle(slug: String) {
        _uiState.update {
            val selected = if (slug in it.selected) it.selected - slug else it.selected + slug
            it.copy(selected = selected, anyGenre = false)
        }
    }

    /** "Any genre": clears the chips and turns genre weighting off. */
    fun pickAnyGenre() = _uiState.update { it.copy(selected = emptySet(), anyGenre = true) }

    /** "My favourites": clears the chips; the server falls back to the saved favourites. */
    fun pickFavourites() = _uiState.update { it.copy(selected = emptySet(), anyGenre = false) }

    /** What to pass as `genres` to RoomViewModel.load(mood, genres). */
    fun genresToSend(): Set<String> = with(_uiState.value) {
        if (anyGenre) setOf(ANY_GENRE_SLUG) else selected
    }

    private fun loadIfNeeded() {
        if (loaded || loading) return
        loading = true
        viewModelScope.launch {
            val options = repository.getGenreOptions()
                .onFailure { Log.w(TAG, "couldn't load genres", it) }
                .getOrNull()

            _uiState.update { it.copy(options = options.orEmpty()) }
            // Genre is optional, so a failure just hides the chips; the next open tries again.
            loaded = options != null
            loading = false
        }
    }

    private companion object {
        const val TAG = "DailyGenresVM"
    }
}