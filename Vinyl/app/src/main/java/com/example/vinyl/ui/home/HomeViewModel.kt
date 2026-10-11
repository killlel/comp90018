package com.example.vinyl.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.repository.parseTimestampMs
import com.example.vinyl.repository.PullStatus
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.RoomRepository
import com.example.vinyl.ui.daily.ReceiveCardStore
import com.example.vinyl.ui.daily.openKey
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
     * The cards today's pull dealt, newest first. Empty until the pull, which is a normal state,
     * not an error.
     */
    val todayHand: List<RoomCard> = emptyList(),
    /** Which cards have been opened on this phone, by [openKey], in the order they were opened. */
    val openedKeys: List<String> = emptyList(),
    val error: String? = null,
    /** Whether today's pull has been made, from the server. Null until it answers. */
    val pullStatus: PullStatus? = null,
) {
    /**
     * Today's cards that have been opened, in the order they were opened: the first opened stands
     * on the left of the shelf and each later one joins on its right.
     */
    val onShelf: List<RoomCard>
        get() = openedKeys.mapNotNull { key -> todayHand.firstOrNull { it.openKey == key } }

    /** Today's cards still sealed: these wait in the picker. */
    val unopened: List<RoomCard> get() = todayHand.filterNot { it.openKey in openedKeys }

    /** Today's pull is still to make. Unknown counts as yes: the server has the final say. */
    val canPull: Boolean get() = pullStatus?.canPull ?: true

    /**
     * Nothing left to do today: every card of the hand is open, or the server says today's pull
     * is made and there's no hand here to open (it was made on another phone and not loaded).
     */
    val doneForToday: Boolean
        get() = if (todayHand.isNotEmpty()) unopened.isEmpty() else !canPull
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
            if (cardStore.manuallyPreparedTestHand() != null || (status != null && status.dealtToday == 0)) {
                showHand(emptyList())
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.getRoom(limit = status?.dealtToday ?: HAND_SIZE)
            val cards = result.getOrNull().orEmpty()
            showHand(
                cards = if (status != null) cardStore.saveToday(cards) else cardStore.resolveExisting(cards),
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    /** Remembers a hand the receive flow was just dealt. */
    fun showToday(cards: List<RoomCard>) {
        showHand(cardStore.saveToday(cards))
    }

    /** A card was opened: it moves from the picker to the shelf. */
    fun markOpened(key: String) {
        cardStore.markOpened(key)
        _uiState.update { it.copy(openedKeys = cardStore.openedKeys()) }
    }

    private fun showHand(cards: List<RoomCard>, error: String? = null) {
        _uiState.update {
            HomeUiState(
                isLoading = false,
                todayHand = cards,
                openedKeys = cardStore.openedKeys(),
                error = error,
                pullStatus = it.pullStatus,
            )
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
