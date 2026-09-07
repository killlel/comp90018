package com.example.vinyl.ui.write

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import com.example.vinyl.repository.SubmissionRepository
import com.example.vinyl.repository.FakeSubmissionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WriteCardViewModel( private val repository: SubmissionRepository = FakeSubmissionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WriteCardUiState())
    val uiState: StateFlow<WriteCardUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(400)
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

    fun onTrackSelected(track: Track) =
        _uiState.update { it.copy(selectedTrack = track, searchResults = emptyList(), query = "") }

    fun onTrackCleared() = _uiState.update { it.copy(selectedTrack = null) }

    fun onMessageChange(message: String) {
        if (message.length <= 280) _uiState.update { it.copy(message = message) }
    }

    fun onMoodSelected(mood: MoodTag) = _uiState.update { it.copy(mood = mood) }

    fun onGenreToggled(genre: String) = _uiState.update {
        val updated = if (genre in it.selectedGenres) it.selectedGenres - genre else it.selectedGenres + genre
        it.copy(selectedGenres = updated)
    }

    fun onAttachLocationToggled(attach: Boolean) = _uiState.update { it.copy(attachLocation = attach) }

    fun submit(lat: Double? = null, lng: Double? = null) {
        val state = _uiState.value
        val track = state.selectedTrack
        val mood = state.mood
        if (track == null || mood == null || state.message.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
            repository.submitSong(
                track = track,
                message = state.message,
                mood = mood,
                // context is the recipient's listening moment, not something the sender sets
                context = null,
                submissionGenres = state.selectedGenres.toList(),
                lat = if (state.attachLocation) lat else null,
                lng = if (state.attachLocation) lng else null,
            ).onSuccess { id ->
                _uiState.update { WriteCardUiState(submittedId = id) } // reset for next letter
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, submissionError = e.message) }
            }
        }
    }

    fun onTogglePreview() = _uiState.update {
        // Guard so you can't preview a half-empty card
        if (!it.isPreviewMode && !it.canSubmit) return@update it
        it.copy(isPreviewMode = !it.isPreviewMode)
    }

    fun onEnvelopeStyleSelected(style: EnvelopeStyle) = _uiState.update { it.copy(envelopeStyle = style) }
}