package com.example.vinyl.data.onboarding

/**
 * TESTING ONLY: an in-memory [OnboardingRepository] that never touches Supabase, so the
 * onboarding flow can be clicked through without a real session or applied migrations.
 * Remove this file and its use in `MainActivity`'s `OnboardingGate` before shipping.
 */
class FakeOnboardingRepository : OnboardingRepository() {

    private var username = "Happy Giraffe" // stand-in for the real database-generated name
    private var avatarSlug: String? = null
    private var favoriteGenres: List<String>? = null
    private var onboardingCompleted = false

    // "Try another name" walks through these; the real one asks the database.
    private val rerollNames = listOf("Sleepy Tiger", "Fizzy Hippo", "Cozy Otter", "Jolly Seal", "Starry Kiwi")
    private var rerollIndex = 0

    override suspend fun getMyProfile(): Result<OnboardingProfile> = Result.success(
        OnboardingProfile(
            username = username,
            avatarSlug = avatarSlug,
            favoriteGenres = favoriteGenres,
            onboardingCompleted = onboardingCompleted,
        ),
    )

    override suspend fun getAvatarOptions(): Result<List<AvatarOption>> = Result.success(
        (1..8).map { n ->
            AvatarOption(slug = "avatar_%02d".format(n), url = "placeholder", sortOrder = n)
        },
    )

    // Mirrors the rows seeded into `public.genres` by 20260918000002_genre_lookup.sql, in the same
    // order, so the fake shows what the real table does. If someone edits the table, update this.
    override suspend fun getGenreOptions(): Result<List<GenreOption>> = Result.success(
        listOf(
            GenreOption("pop", "Pop", 10),
            GenreOption("rock", "Rock", 20),
            GenreOption("indie", "Indie", 30),
            GenreOption("alternative", "Alternative", 40),
            GenreOption("electronic", "Electronic", 50),
            GenreOption("edm", "EDM", 60),
            GenreOption("synthpop", "Synth-pop", 70),
            GenreOption("hiphop", "Hip-Hop", 80),
            GenreOption("rap", "Rap", 90),
            GenreOption("rnb", "R&B", 100),
            GenreOption("soul", "Soul", 110),
            GenreOption("funk", "Funk", 120),
            GenreOption("disco", "Disco", 130),
            GenreOption("jazz", "Jazz", 140),
            GenreOption("classical", "Classical", 150),
            GenreOption("folk", "Folk", 160),
            GenreOption("soft_rock", "Soft Rock", 170),
            GenreOption("metal", "Metal", 180),
            GenreOption("ambient", "Ambient", 190),
            GenreOption("shoegaze", "Shoegaze", 200),
            GenreOption("k_pop", "K-pop", 210),
            GenreOption("musical", "Musical", 220),
            GenreOption("avant_garde", "Avant-garde", 230),
        ),
    )

    override suspend fun rerollUsername(): Result<String> {
        if (onboardingCompleted) return Result.failure(IllegalStateException("onboarding finished"))
        username = rerollNames[rerollIndex % rerollNames.size]
        rerollIndex++
        return Result.success(username)
    }

    override suspend fun setAvatar(avatarSlug: String): Result<Unit> {
        this.avatarSlug = avatarSlug
        return Result.success(Unit)
    }

    override suspend fun setFavoriteGenres(slugs: List<String>): Result<Unit> {
        this.favoriteGenres = slugs
        return Result.success(Unit)
    }

    override suspend fun completeOnboarding(): Result<Unit> {
        onboardingCompleted = true
        return Result.success(Unit)
    }
}