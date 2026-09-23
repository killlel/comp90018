package com.example.vinyl.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinyl.data.GenreOptions
import com.example.vinyl.data.onboarding.AvatarOption
import com.example.vinyl.data.onboarding.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoadingProfile: Boolean = true,
    val displayName: String = "",
    val avatarOptions: List<AvatarOption> = emptyList(),
    val selectedAvatarId: String? = null,
    val genreOptions: List<String> = GenreOptions.all,
    val selectedGenres: Set<String> = emptySet(),
    val listenToEverything: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Shared across all four onboarding pages so each save-and-advance call updates one source of
 * truth, instead of every page re-fetching the profile.
 *
 * [OnboardingUiState.genreOptions] comes from the existing `GenreOptions.all` constant, not a
 * network call — only avatars and the profile itself need fetching.
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
            val profile = profileResult.getOrNull()

            _uiState.update { state ->
                state.copy(
                    isLoadingProfile = false,
                    displayName = profile?.displayName ?: state.displayName,
                    selectedAvatarId = profile?.avatarId,
                    selectedGenres = profile?.genres?.toSet() ?: emptySet(),
                    listenToEverything = profile?.genresAll ?: false,
                    notificationsEnabled = profile?.notificationsEnabled ?: true,
                    avatarOptions = avatarsResult.getOrNull() ?: emptyList(),
                    errorMessage = listOf(profileResult, avatarsResult)
                        .firstNotNullOfOrNull { it.exceptionOrNull()?.message },
                )
            }
        }
    }

    fun selectAvatar(avatarId: String) {
        _uiState.update { it.copy(selectedAvatarId = avatarId) }
    }

    fun toggleGenre(genre: String) {
        _uiState.update { state ->
            val next = state.selectedGenres.toMutableSet().apply {
                if (!add(genre)) remove(genre)
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

    /** Page 1 → 2. */
    fun saveAvatar(onSaved: () -> Unit) = saveStep(onSaved) {
        val avatarId = uiState.value.selectedAvatarId
        if (avatarId == null) Result.success(Unit) else repository.setAvatar(avatarId)
    }

    /** Page 2 → 3. */
    fun saveGenres(onSaved: () -> Unit) = saveStep(onSaved) {
        repository.setGenres(uiState.value.selectedGenres.toList(), uiState.value.listenToEverything)
    }

    /** Page 4 → done. Also marks onboarding complete server-side. */
    fun finishOnboarding(onFinished: () -> Unit) = saveStep(onFinished) {
        repository.setNotificationPreference(uiState.value.notificationsEnabled)
    }

    private fun saveStep(onSaved: () -> Unit, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = action()
            _uiState.update { it.copy(isSaving = false) }
            result.fold(
                onSuccess = { onSaved() },
                onFailure = { e ->
                    _uiState.update { it.copy(errorMessage = e.message ?: "Something went wrong.") }
                },
            )
        }
    }
}