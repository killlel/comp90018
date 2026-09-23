package com.example.vinyl.ui.daily

import androidx.lifecycle.ViewModel
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
)

/** Loads the letters behind the Arrived Today picker. */
class RoomViewModel(private val repository: RoomRepository = RoomRepository()) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    /**
     * A chosen mood asks the server for new matches. "Let the crate decide" has no mood to match
     * on, and request_recommendations requires one, so it replays what's already been delivered
     * instead.
     */
    fun load(mood: MoodTag?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = if (mood != null) {
                repository.requestRecommendations(mood)
            } else {
                repository.getRoom()
            }
            result
                .onSuccess { cards -> _uiState.update { RoomUiState(cards = cards) } }
                .onFailure { e -> _uiState.update { RoomUiState(error = e.message) } }
        }
    }
}
