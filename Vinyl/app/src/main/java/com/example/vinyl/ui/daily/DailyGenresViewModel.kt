package com.example.vinyl.ui.daily

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.onboarding.GenreOption
import com.example.vinyl.data.onboarding.OnboardingRepository
import kotlinx.coroutines.async
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
)

/**
 * The genre chips on the daily questionnaire.
 *
 * The options come from the `genres` table, so a chip carries the slug the matcher compares
 * against. The chips start on the user's onboarding favourites because the server *replaces*
 * the favourites with today's chips rather than adding to them - starting from the favourites
 * means leaving the chips alone matches exactly as it would without them.
 */
class DailyGenresViewModel(
    private val repository: OnboardingRepository = OnboardingRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyGenresUiState())
    val uiState: StateFlow<DailyGenresUiState> = _uiState.asStateFlow()

    private var favourites: Set<String> = emptySet()
    private var loaded = false
    private var loading = false

    /** Set once the user taps a chip, so a late-arriving load doesn't overwrite their choice. */
    private var touched = false

    /** Call each time the questionnaire opens: back to the favourites, loading them on first use. */
    fun reset() {
        touched = false
        _uiState.update { it.copy(selected = favourites) }
        loadIfNeeded()
    }

    fun toggle(slug: String) {
        touched = true
        _uiState.update {
            val selected = if (slug in it.selected) it.selected - slug else it.selected + slug
            it.copy(selected = selected)
        }
    }

    private fun loadIfNeeded() {
        if (loaded || loading) return
        loading = true
        viewModelScope.launch {
            val optionsCall = async { repository.getGenreOptions() }
            val profileCall = async { repository.getMyProfile() }
            val options = optionsCall.await()
                .onFailure { Log.w(TAG, "couldn't load genres", it) }
                .getOrNull()
            val profile = profileCall.await()
                .onFailure { Log.w(TAG, "couldn't load favourite genres", it) }
                .getOrNull()

            // A retired genre can still sit in someone's favourites. Selecting a chip they can't
            // see would be a filter they can't undo, so keep only the ones on offer.
            val offered = options?.mapTo(mutableSetOf()) { it.slug }
            favourites = profile?.favoriteGenres.orEmpty()
                .filterTo(mutableSetOf()) { offered == null || it in offered }

            _uiState.update {
                it.copy(
                    options = options.orEmpty(),
                    selected = if (touched) it.selected else favourites,
                )
            }
            // Genre is optional, so a failure just hides the chips; the next open tries again.
            loaded = options != null
            loading = false
        }
    }

    private companion object {
        const val TAG = "DailyGenresVM"
    }
}
