package com.example.vinyl.ui.write

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import com.example.vinyl.repository.SubmissionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WriteCardViewModel(
    // Real submissions now — swap in FakeSubmissionRepository() here to send without touching
    // Supabase while working on the UI.
    private val repository: SubmissionRepository = SubmissionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WriteCardUiState())
    val uiState: StateFlow<WriteCardUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var loadingGenres = false

    init {
        loadGenresIfNeeded()
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
                    val results = try {
                        repository.searchSongs(q)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e // expected: collectLatest cancelled this for a newer keystroke
                    } catch (e: Exception) {
                        Log.e("WriteCardVM", "search failed", e)
                        emptyList()
                    }
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
        }
    }

    /**
     * Reads the genre vocabulary from the database. Safe to call again: it does nothing once the
     * genres are loaded, so the screen can call it on entry to retry after being offline. A
     * failure is not shown - genre is optional, so the section is simply hidden.
     */
    fun loadGenresIfNeeded() {
        if (loadingGenres || _uiState.value.genreOptions.isNotEmpty()) return
        loadingGenres = true
        viewModelScope.launch {
            repository.getGenreOptions()
                .onSuccess { options -> _uiState.update { it.copy(genreOptions = options) } }
                .onFailure { e -> Log.w("WriteCardVM", "couldn't load genres", e) }
            loadingGenres = false
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

    /** [genreSlug] is a slug from `genres` (`k_pop`), not a label. */
    fun onGenreToggled(genreSlug: String) = _uiState.update {
        val updated = if (genreSlug in it.selectedGenres) it.selectedGenres - genreSlug else it.selectedGenres + genreSlug
        it.copy(selectedGenres = updated)
    }

    fun onAttachLocationToggled(attach: Boolean) = _uiState.update { it.copy(attachLocation = attach) }

    fun submit() {
        val state = _uiState.value
        val track = state.selectedTrack ?: return
        // The button is disabled until this holds; this is the backstop if it is ever bypassed.
        if (!state.canSubmit) {
            _uiState.update { it.copy(submissionError = state.sendHint) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
            repository.submitSong(
                track = track,
                message = state.message,
                mood = state.mood,
                // context is the recipient's listening moment, not something the sender sets
                context = null,
                submissionGenres = state.selectedGenres.toList(),
                attachLocation = state.attachLocation,
            ).onSuccess { id ->
                // Reset for the next letter, but keep the genres we already loaded.
                _uiState.update { WriteCardUiState(submittedId = id, genreOptions = it.genreOptions) }
            }.onFailure { e ->
                // Not e.message: it includes the backend URL, which would land on screen.
                Log.e("WriteCardVM", "submit failed", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionError = "Couldn't send your record. Check your connection, then try again.",
                    )
                }
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