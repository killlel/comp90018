package com.example.vinyl.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.onboarding.GenreOption
import com.example.vinyl.data.onboarding.OnboardingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreferencesUiState(
    val options: List<GenreOption> = emptyList(),
    /** Favourite genre slugs (`k_pop`), never labels. */
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * The favourite genres on the Settings > Music Preferences page.
 *
 * Reads the same `genres` table the questionnaire and Write screen use, and reads and writes
 * `profiles.favorite_genres`, the column onboarding fills and the daily "My favourites" fallback
 * reads. Toggling a chip saves shortly after the last tap.
 */
class PreferencesViewModel(
    private val repository: OnboardingRepository = OnboardingRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val optionsCall = async { repository.getGenreOptions() }
            val profileCall = async { repository.getMyProfile() }
            val options = optionsCall.await().getOrNull()
            val profile = profileCall.await().getOrNull()

            // Both are needed: showing chips without the saved answer would let a tap overwrite it.
            if (options == null || profile == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Couldn't load your genres. Check your connection and try again.")
                }
                return@launch
            }

            // A retired genre can still sit in someone's favourites; only show what is on offer.
            val offered = options.map { it.slug }.toSet()
            _uiState.update {
                PreferencesUiState(
                    options = options,
                    selected = profile.favoriteGenres.orEmpty().filterTo(mutableSetOf()) { it in offered },
                    isLoading = false,
                )
            }
        }
    }

    fun toggle(slug: String) {
        val state = _uiState.value
        val next = if (slug in state.selected) state.selected - slug else state.selected + slug
        _uiState.update { it.copy(selected = next, error = null) }

        // Waits for a pause in tapping, so a burst of taps is one write of the final choice.
        val ordered = state.options.map { it.slug }.filter { it in next }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MILLIS)
            repository.setFavoriteGenres(ordered).onFailure {
                _uiState.update { s -> s.copy(error = "Couldn't save your genres. Try again.") }
            }
        }
    }

    private companion object {
        const val SAVE_DELAY_MILLIS = 400L
    }
}