package com.example.vinyl.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.repository.parseTimestampMs
import com.example.vinyl.repository.PullStatus
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.RoomRepository
import com.example.vinyl.ui.daily.ReceiveCardStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Whether today's pull has been made, from the server. Null until it answers. */
    val pullStatus: PullStatus? = null,
) {
    val arrivedCount: Int get() = arrivedToday.size
    val todayHand: List<RoomCard> get() = arrivedToday.ifEmpty { pendingToday }

    /** Today's pull is still to make. Unknown counts as yes: the server has the final say. */
    val canPull: Boolean get() = pullStatus?.canPull ?: true
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

    /** Re-runs [refresh] when the day turns over at 06:00, so the button comes back by itself. */
    private var turnover: Job? = null

    /**
     * Today's hand comes from the server's count when it has one: `get_room` has no delivery
     * dates, so only the count says which of its newest cards were dealt today. Without the count
     * (the call failed) it falls back to the phone's memory of what was shown.
     */
    fun refresh() {
        viewModelScope.launch {
            val status = loadPullStatus()

            // The phone remembers a hand the server doesn't count for today — the clock and the
            // server disagree, or the pull was reset. The server wins.
            if (status != null && status.dealtToday == 0) cardStore.forgetToday()

            val cached = cardStore.todayCards()
            if (cached.isNotEmpty()) {
                showHand(cached)
                return@launch
            }
            if (cardStore.manuallyPreparedTestHand() != null) {
                _uiState.update { HomeUiState(isLoading = false, pullStatus = it.pullStatus) }
                return@launch
            }
            if (status != null && status.dealtToday == 0) {
                _uiState.update { HomeUiState(isLoading = false, pullStatus = it.pullStatus) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.getRoom(limit = status?.dealtToday ?: HAND_SIZE)
            val cards = result.getOrNull().orEmpty()
            _uiState.update {
                HomeUiState(
                    isLoading = false,
                    arrivedToday = if (status != null) {
                        cardStore.saveToday(cards, shelfVisible = true)
                    } else {
                        cardStore.resolveExisting(cards)
                    },
                    error = result.exceptionOrNull()?.message,
                    pullStatus = it.pullStatus,
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
        _uiState.update {
            if (cardStore.isShelfVisible()) {
                HomeUiState(isLoading = false, arrivedToday = cards, pullStatus = it.pullStatus)
            } else {
                HomeUiState(isLoading = false, pendingToday = cards, pullStatus = it.pullStatus)
            }
        }
    }

    /**
     * Asks the server whether today's pull is made, and books a [refresh] for the next 06:00.
     * Returns null if the call fails; the last answer is kept, and the server still refuses a
     * second pull either way.
     */
    private suspend fun loadPullStatus(): PullStatus? {
        val status = repository.getPullStatus().getOrNull() ?: return null
        _uiState.update { it.copy(pullStatus = status) }
        parseTimestampMs(status.nextRefreshAt)?.let { turnsOverAt ->
            turnover?.cancel()
            turnover = viewModelScope.launch {
                delay((turnsOverAt - System.currentTimeMillis()).coerceAtLeast(0L) + TURNOVER_GRACE_MILLIS)
                refresh()
            }
        }
        return status
    }

    private companion object {
        /** A pull deals three cards, and the shelf holds three sleeves. */
        const val HAND_SIZE = 3

        /** A moment past 06:00, so the phone's clock and the server's agree the day has turned. */
        const val TURNOVER_GRACE_MILLIS = 5_000L
    }
}
