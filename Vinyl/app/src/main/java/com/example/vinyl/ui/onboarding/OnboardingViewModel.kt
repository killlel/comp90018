package com.example.vinyl.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.onboarding.AvatarOption
import com.example.vinyl.data.onboarding.GenreOption
import com.example.vinyl.data.onboarding.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoadingProfile: Boolean = true,
    /** The database-generated alias. Not the Google name. */
    val username: String = "",
    val isRerollingName: Boolean = false,
    val avatarOptions: List<AvatarOption> = emptyList(),
    /** Slug of the chosen picture, from [avatarOptions]. */
    val selectedAvatarSlug: String? = null,
    val genreOptions: List<GenreOption> = emptyList(),
    /** Slugs, not labels - the database rejects labels. */
    val selectedGenres: Set<String> = emptySet(),
    val listenToEverything: Boolean = false,
    /** UI only for now: the schema has no column to store it in. */
    val notificationsEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Shared across all four onboarding pages so each save-and-advance call updates one source of
 * truth, instead of every page re-fetching the profile.
 *
 * Errors are logged and shown as a generic message: the underlying exception carries the backend
 * URL and query string, which must not end up on screen.
 */
class OnboardingViewModel(
    private val repository: OnboardingRepository = OnboardingRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val profileResult = repository.getMyProfile()
            val avatarsResult = repository.getAvatarOptions()
            val genresResult = repository.getGenreOptions()
            val profile = profileResult.getOrNull()

            val firstError = listOf(profileResult, avatarsResult, genresResult)
                .firstNotNullOfOrNull { it.exceptionOrNull() }

            _uiState.update { state ->
                state.copy(
                    isLoadingProfile = false,
                    username = profile?.username ?: state.username,
                    selectedAvatarSlug = profile?.avatarSlug,
                    selectedGenres = profile?.favoriteGenres?.toSet() ?: emptySet(),
                    listenToEverything = profile?.listensToEverything ?: false,
                    avatarOptions = avatarsResult.getOrNull() ?: emptyList(),
                    genreOptions = genresResult.getOrNull() ?: emptyList(),
                    errorMessage = firstError?.let(::describe),
                )
            }
        }
    }

    /** "Try another name". The database picks and saves it; we just show what came back. */
    fun rerollUsername() {
        if (uiState.value.isRerollingName) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRerollingName = true, errorMessage = null) }
            repository.rerollUsername().fold(
                onSuccess = { name ->
                    _uiState.update { it.copy(isRerollingName = false, username = name) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isRerollingName = false, errorMessage = describe(e)) }
                },
            )
        }
    }

    fun selectAvatar(avatarSlug: String) {
        _uiState.update { it.copy(selectedAvatarSlug = avatarSlug) }
    }

    fun toggleGenre(genreSlug: String) {
        _uiState.update { state ->
            val next = state.selectedGenres.toMutableSet().apply {
                if (!add(genreSlug)) remove(genreSlug)
            }
            state.copy(selectedGenres = next, listenToEverything = false)
        }
    }

    fun setListenToEverything(enabled: Boolean) {
        _uiState.update {
            it.copy(
                listenToEverything = enabled,
                selectedGenres = if (enabled) emptySet() else it.selectedGenres,
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    /** Page 1 -> 2. Nothing to save if no picture was chosen (e.g. none are on offer yet). */
    fun saveAvatar(onSaved: () -> Unit) = saveStep(onSaved) {
        val slug = uiState.value.selectedAvatarSlug
        if (slug == null) Result.success(Unit) else repository.setAvatar(slug)
    }

    /**
     * Page 2 -> 3. "Listen to everything" saves an empty list; picking nothing at all saves
     * nothing, so the profile keeps "not answered yet".
     */
    fun saveGenres(onSaved: () -> Unit) = saveStep(onSaved) {
        val state = uiState.value
        when {
            state.listenToEverything -> repository.setFavoriteGenres(emptyList())
            state.selectedGenres.isNotEmpty() -> repository.setFavoriteGenres(state.selectedGenres.toList())
            else -> Result.success(Unit)
        }
    }

    /** Page 4 -> done. Marks onboarding complete; the notification choice is not stored yet. */
    fun finishOnboarding(onFinished: () -> Unit) = saveStep(onFinished) {
        repository.completeOnboarding()
    }

    private fun saveStep(onSaved: () -> Unit, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = action()
            _uiState.update { it.copy(isSaving = false) }
            result.fold(
                onSuccess = { onSaved() },
                onFailure = { e -> _uiState.update { it.copy(errorMessage = describe(e)) } },
            )
        }
    }

    private fun describe(e: Throwable): String {
        Log.w(TAG, "onboarding request failed", e)
        return "Something went wrong. Check your connection and try again."
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}