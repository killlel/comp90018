package com.example.vinyl.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.RoomRepository
import com.example.vinyl.ui.daily.ReceiveCardStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    /**
     * The latest hand of music cards, newest first — what the Arrived Today picker shows. Empty
     * until the user has pulled once, which is a normal state, not an error.
     */
    val arrivedToday: List<RoomCard> = emptyList(),
    /** A delivered hand waiting for the first Play on turntable action. */
    val pendingToday: List<RoomCard> = emptyList(),
    val error: String? = null,
) {
    val arrivedCount: Int get() = arrivedToday.size
    val todayHand: List<RoomCard> get() = arrivedToday.ifEmpty { pendingToday }
}

/**
 * The home screen reads, it never matches. `get_room` replays what the server has already handed
 * out; asking for new letters is `request_recommendations`, and that belongs to the receive flow
 * where the user has actually answered the mood question.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RoomRepository()
    private val cardStore = ReceiveCardStore(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val cached = cardStore.todayCards()
            if (cached.isNotEmpty()) {
                showHand(cached)
                return@launch
            }
            if (cardStore.manuallyPreparedTestHand() != null) {
                _uiState.value = HomeUiState(isLoading = false)
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.getRoom(limit = HAND_SIZE)
            _uiState.update {
                HomeUiState(
                    isLoading = false,
                    arrivedToday = cardStore.resolveExisting(result.getOrNull().orEmpty()),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun showToday(cards: List<RoomCard>) {
        showHand(cardStore.saveToday(cards))
    }

    fun publishToday() {
        cardStore.publishToday()
        showHand(cardStore.todayCards())
    }

    private fun showHand(cards: List<RoomCard>) {
        _uiState.value = if (cardStore.isShelfVisible()) {
            HomeUiState(isLoading = false, arrivedToday = cards)
        } else {
            HomeUiState(isLoading = false, pendingToday = cards)
        }
    }

    private companion object {
        /** A pull deals three cards, and the shelf holds three sleeves. */
        const val HAND_SIZE = 3
    }
}
