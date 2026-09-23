package com.example.vinyl.ui.submission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import com.example.vinyl.repository.SubmissionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SubmissionUiState(
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val selectedTrack: Track? = null,
    val message: String = "",
    val mood: MoodTag? = null,
    val context: ContextTag? = null,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null,
    val submittedId: String? = null,
)

class SubmissionViewModel(
    private val repository: SubmissionRepository = SubmissionRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmissionUiState())
    val uiState: StateFlow<SubmissionUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(400) // stay well under iTunes' ~20 calls/min limit
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                        return@collectLatest
                    }
                    _uiState.update { it.copy(isSearching = true) }
                    val results = runCatching { repository.searchSongs(q) }.getOrElse { emptyList() }
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        queryFlow.value = newQuery
    }

    fun onTrackSelected(track: Track) {
        _uiState.update { it.copy(selectedTrack = track, searchResults = emptyList()) }
    }

    fun onMessageChange(message: String) {
        if (message.length <= 280) _uiState.update { it.copy(message = message) } // matches submissions_message check
    }

    fun onMoodChange(mood: MoodTag) = _uiState.update { it.copy(mood = mood) }
    fun onContextChange(context: ContextTag?) = _uiState.update { it.copy(context = context) }

    fun submit(attachLocation: Boolean = false) {
        val state = _uiState.value
        val track = state.selectedTrack
        val mood = state.mood

        if (track == null || mood == null || state.message.isBlank()) {
            _uiState.update { it.copy(submissionError = "Pick a song, a mood, and write a message first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
            repository.submitSong(
                track = track,
                message = state.message,
                mood = mood,
                context = state.context,
                attachLocation = attachLocation,
            ).onSuccess { id ->
                _uiState.update { it.copy(isSubmitting = false, submittedId = id) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, submissionError = e.message) }
            }
        }
    }
}