package com.example.vinyl.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    /**
     * The latest hand of music cards, newest first — what the Arrived Today picker shows. Empty
     * until the user has pulled once, which is a normal state, not an error.
     */
    val arrivedToday: List<RoomCard> = emptyList(),
    val error: String? = null,
) {
    val arrivedCount: Int get() = arrivedToday.size
}

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
            val result = repository.getRoom(limit = HAND_SIZE)
            _uiState.update {
                HomeUiState(
                    arrivedToday = result.getOrNull().orEmpty(),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private companion object {
        /** A pull deals three cards, and the shelf holds three sleeves. */
        const val HAND_SIZE = 3
    }
}
