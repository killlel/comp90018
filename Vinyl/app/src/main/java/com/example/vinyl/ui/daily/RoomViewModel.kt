package com.example.vinyl.ui.daily

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.MoodTag
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomUiState(
    val isLoading: Boolean = false,
    val cards: List<RoomCard> = emptyList(),
    val error: String? = null,
    /** Submission ids on the shelf right now, seeded from each card's `saved`. */
    val keptIds: Set<String> = cards.filter { it.saved }.map { it.submissionId }.toSet(),
    /** A keep that didn't go through, for the opened card to show. */
    val keepError: String? = null,
)

/** Loads the letters behind the Arrived Today picker. */
class RoomViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RoomRepository()
    private val cardStore = ReceiveCardStore(application)

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    /**
     * A chosen mood asks the server for new matches. "Let the crate decide" has no mood to match
     * on, and request_recommendations requires one, so it replays what's already been delivered
     * instead, and [genres] go unused.
     *
     * @param genres today's chip slugs; empty lets the server use the onboarding favourites
     */
    fun load(mood: MoodTag?, genres: Set<String> = emptySet()) {
        if (_uiState.value.isLoading) return
        val existing = cardStore.todayCards()
        if (existing.isNotEmpty()) {
            showExisting(existing)
            return
        }
        val testHand = cardStore.manuallyPreparedTestHand()
        if (testHand != null) {
            showExisting(cardStore.saveToday(testHand))
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = if (mood != null) {
                repository.requestRecommendations(mood, genres = genres)
            } else {
                repository.getRoom()
            }
            result
                .onSuccess { cards ->
                    val hand = if (mood != null) cardStore.saveToday(cards) else cardStore.resolveExisting(cards)
                    _uiState.value = RoomUiState(cards = hand)
                }
                .onFailure { e -> _uiState.update { RoomUiState(error = e.message) } }
        }
    }

    /** Seeds the picker from the Home shelf without requesting or creating recommendations. */
    fun showExisting(cards: List<RoomCard>) {
        if (_uiState.value.cards != cards) _uiState.value = RoomUiState(cards = cards)
    }

    /**
     * Keeps or un-keeps a record. Optimistic: the button flips at once and flips back if the
     * write fails, since keeping is cheap to retry.
     */
    fun toggleKeep(submissionId: String) {
        val keep = submissionId !in _uiState.value.keptIds
        setKept(submissionId, keep)

        viewModelScope.launch {
            val result = if (keep) repository.keep(submissionId) else repository.unkeep(submissionId)
            result.onFailure {
                setKept(submissionId, !keep)
                _uiState.update { it.copy(keepError = "Couldn't update your shelf. Try again.") }
            }
        }
    }

    private fun setKept(submissionId: String, kept: Boolean) {
        cardStore.setKept(submissionId, kept)
        _uiState.update {
            it.copy(
                keptIds = if (kept) it.keptIds + submissionId else it.keptIds - submissionId,
                keepError = null,
            )
        }
    }
}
