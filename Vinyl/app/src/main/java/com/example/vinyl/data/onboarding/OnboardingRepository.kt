package com.example.vinyl.data.onboarding

import com.example.vinyl.data.Supabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One selectable option in the fixed profile-picture set (`avatar_options`).
 *
 * Unlike genres, this has no client-side constant yet (see [OnboardingRepository]), so it's read
 * from Supabase the same way `submit_song()` reads `tracks` — a small table rather than a
 * hardcoded list, since the real icon set isn't decided yet.
 */
@Serializable
data class AvatarOption(
    val slug: String,
    @SerialName("asset_name") val assetName: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** The onboarding-relevant slice of the caller's own `profiles` row. */
@Serializable
data class OnboardingProfile(
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_id") val avatarId: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("genres_all") val genresAll: Boolean = false,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = true,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
)

/**
 * Backs the four onboarding pages. Kept separate from [com.example.vinyl.data.ProfileRepository]
 * rather than extending it: these fields go through dedicated RPCs (`set_profile_avatar`,
 * `set_profile_genres`, `set_notification_preference`) rather than a direct table write, and none
 * of them overlap with what ProfileRepository already owns (location).
 *
 * Genres are NOT read from here — the picker uses the existing `GenreOptions.all` constant in
 * `MoodOptions.kt`, so there's no `getGenreOptions()`. Only the save side is a repository concern.
 */
open class OnboardingRepository(
    private val supabase: SupabaseClient = Supabase.client,
) {

    /** The signed-in user's display name, avatar, genre picks and onboarding progress so far. */
    open suspend fun getMyProfile(): Result<OnboardingProfile> = runCatching {
        val uid = requireUserId()
        supabase.postgrest
            .from(TABLE)
            .select(
                Columns.list(
                    "display_name",
                    "avatar_id",
                    "genres",
                    "genres_all",
                    "notifications_enabled",
                    "onboarding_completed",
                ),
            ) { filter { eq("id", uid) } }
            .decodeSingle<OnboardingProfile>()
    }

    /** The fixed set of selectable profile-picture icons, in display order. */
    open suspend fun getAvatarOptions(): Result<List<AvatarOption>> = runCatching {
        supabase.postgrest
            .from("avatar_options")
            .select(Columns.list("slug", "asset_name", "sort_order")) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<AvatarOption>()
    }

    /** Page 1: sets the chosen icon. The username itself is server-assigned; never sent here. */
    open suspend fun setAvatar(avatarId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "set_profile_avatar",
            buildJsonObject { put("p_avatar_id", avatarId) },
        )
    }.map { }

    /**
     * Page 2: saves the genre picks (values from `GenreOptions.all`). [listenToEverything] = true
     * means "accept all" — the server stores an empty selection plus the flag, so genres added to
     * that constant later still show up as "everything" for someone who chose it before.
     */
    open suspend fun setGenres(genres: List<String>, listenToEverything: Boolean): Result<Unit> =
        runCatching {
            supabase.postgrest.rpc(
                "set_profile_genres",
                buildJsonObject {
                    put("p_genres", buildJsonArray { genres.forEach { add(it) } })
                    put("p_all", listenToEverything)
                },
            )
        }.map { }

    /**
     * Page 4, and the last onboarding step — also flips `onboarding_completed` server-side, so
     * there's no separate "finish onboarding" call.
     */
    open suspend fun setNotificationPreference(enabled: Boolean): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            "set_notification_preference",
            buildJsonObject { put("p_enabled", enabled) },
        )
    }.map { }

    private fun requireUserId(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("No signed-in user; onboarding is unavailable")

    private companion object {
        const val TABLE = "profiles"
    }
}