package com.example.vinyl.data.onboarding

/**
 * TESTING ONLY: an in-memory [OnboardingRepository] that never touches Supabase, so the
 * onboarding flow can be clicked through before the migrations are applied / without a real
 * session. Remove this file and its use in `MainActivity`'s `OnboardingGate` before shipping.
 */
class FakeOnboardingRepository : OnboardingRepository() {

    private var avatarId: String? = null
    private var genres: List<String> = emptyList()
    private var genresAll: Boolean = false
    private var notificationsEnabled: Boolean = true
    private var onboardingCompleted: Boolean = false

    override suspend fun getMyProfile(): Result<OnboardingProfile> = Result.success(
        OnboardingProfile(
            displayName = "Happy Giraffe", // stand-in for the real server-generated name
            avatarId = avatarId,
            genres = genres,
            genresAll = genresAll,
            notificationsEnabled = notificationsEnabled,
            onboardingCompleted = onboardingCompleted,
        ),
    )

    override suspend fun getAvatarOptions(): Result<List<AvatarOption>> = Result.success(
        (1..8).map { n ->
            AvatarOption(slug = "avatar_%02d".format(n), assetName = "placeholder", sortOrder = n)
        },
    )

    override suspend fun setAvatar(avatarId: String): Result<Unit> {
        this.avatarId = avatarId
        return Result.success(Unit)
    }

    override suspend fun setGenres(genres: List<String>, listenToEverything: Boolean): Result<Unit> {
        this.genres = genres
        this.genresAll = listenToEverything
        return Result.success(Unit)
    }

    override suspend fun setNotificationPreference(enabled: Boolean): Result<Unit> {
        this.notificationsEnabled = enabled
        this.onboardingCompleted = true
        return Result.success(Unit)
    }
}