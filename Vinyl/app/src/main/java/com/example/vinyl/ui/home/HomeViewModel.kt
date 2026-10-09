package com.example.vinyl.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.RoomRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    /** Letters already delivered today and not yet opened. Zero is a normal state, not an error. */
    val arrivedCount: Int = 0,
    val recentlyCollected: List<RoomCard> = emptyList(),
    val error: String? = null,
)

/**
 * The home screen reads, it never matches. `get_room` replays what the server has already handed
 * out; asking for new letters is `request_recommendations`, and that belongs to the receive flow
 * where the user has actually answered the mood question.
 */
class HomeViewModel(private val repository: RoomRepository = RoomRepository()) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Independent RPCs, so they overlap rather than queue.
            val roomCall = async { repository.getRoom() }
            val shelfCall = async { repository.getShelf(limit = RECENT_COUNT) }

            val roomResult = roomCall.await()
            val shelfResult = shelfCall.await()

            val room = roomResult.getOrNull().orEmpty()

            // A failed shelf shouldn't blank the card count, or the other way round: each half of
            // the screen falls back to empty on its own.
            _uiState.update {
                HomeUiState(
                    arrivedCount = room.size,
                    recentlyCollected = shelfResult.getOrNull().orEmpty(),
                    error = roomResult.exceptionOrNull()?.message
                        ?: shelfResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    private companion object {
        /** The design shows three sleeves on the shelf. */
        const val RECENT_COUNT = 3
    }
}
