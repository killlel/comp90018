package com.example.vinyl.data.onboarding

import com.example.vinyl.data.Supabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One selectable profile picture, from the `avatars` table.
 *
 * [slug] is what gets saved on the profile (`profiles.avatar_slug`); [url] is a remote image the
 * screen renders. The table is seeded empty on purpose, so this list is empty until someone
 * inserts rows - see DATABASE.md.
 */
@Serializable
data class AvatarOption(
    val slug: String,
    val url: String,
    @SerialName("sort_order") val sortOrder: Int = 100,
)

/**
 * One selectable genre, from the `genres` table. [slug] (`k_pop`) is stored and matched on;
 * [label] (`K-pop`) is what the user sees. The database rejects a label, so always send slugs.
 */
@Serializable
data class GenreOption(
    val slug: String,
    val label: String,
    @SerialName("sort_order") val sortOrder: Int = 100,
)

/**
 * The onboarding-relevant slice of the caller's own `profiles` row.
 *
 * [username] is the server-generated alias ("Happy Giraffe").
 *
 * [favoriteGenres] keeps the column's three states: null = not answered yet, empty = "I listen to
 * everything", otherwise the chosen slugs.
 */
@Serializable
data class OnboardingProfile(
    val username: String,
    @SerialName("avatar_slug") val avatarSlug: String? = null,
    @SerialName("favorite_genres") val favoriteGenres: List<String>? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
) {
    val listensToEverything: Boolean get() = favoriteGenres?.isEmpty() == true
}

/**
 * Backs the onboarding pages. Kept separate from [com.example.vinyl.data.ProfileRepository], which
 * owns location.
 *
 * Reads and writes go straight to `profiles` - RLS confines them to the caller's own row - except
 * the username, which only the database writes: it is generated on signup and re-rolled through
 * the `reroll_username()` RPC. See DATABASE.md.
 */
open class OnboardingRepository(
    private val supabase: SupabaseClient = Supabase.client,
) {

    /** The signed-in user's username, picture, genre answer and onboarding progress so far. */
    open suspend fun getMyProfile(): Result<OnboardingProfile> = runCatching {
        val uid = requireUserId()
        supabase.postgrest
            .from(PROFILES)
            .select(
                Columns.list("username", "avatar_slug", "favorite_genres", "onboarding_completed"),
            ) { filter { eq("id", uid) } }
            .decodeSingle<OnboardingProfile>()
    }

    /** The pictures on offer, in display order. Retired ones (`is_active = false`) are hidden. */
    open suspend fun getAvatarOptions(): Result<List<AvatarOption>> = runCatching {
        supabase.postgrest
            .from("avatars")
            .select(Columns.list("slug", "url", "sort_order")) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<AvatarOption>()
    }

    /** The genres on offer, in display order, read from the database rather than hardcoded. */
    open suspend fun getGenreOptions(): Result<List<GenreOption>> = runCatching {
        supabase.postgrest
            .from("genres")
            .select(Columns.list("slug", "label", "sort_order")) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<GenreOption>()
    }

    /**
     * "Try another name": the database picks a fresh unique username, saves it and returns it.
     * Only works while onboarding is unfinished - afterwards it fails, by design.
     */
    open suspend fun rerollUsername(): Result<String> = runCatching {
        supabase.postgrest.rpc("reroll_username").decodeAs<String>()
    }

    /** Page 1: saves the chosen picture. Pass a slug from [getAvatarOptions]. */
    open suspend fun setAvatar(avatarSlug: String): Result<Unit> =
        updateMyProfile(buildJsonObject { put("avatar_slug", avatarSlug) })

    /**
     * Page 2: saves the genre answer as slugs from [getGenreOptions].
     *
     * An EMPTY list is stored as "I listen to everything" - it is a real answer, not "nothing
     * chosen". To leave the question unanswered, do not call this at all.
     */
    open suspend fun setFavoriteGenres(slugs: List<String>): Result<Unit> =
        updateMyProfile(
            buildJsonObject { put("favorite_genres", buildJsonArray { slugs.forEach { add(it) } }) },
        )

    /**
     * Last step: marks onboarding done. The database only lets this flip from false to true.
     *
     * The notification choice is deliberately NOT saved here - the schema has nowhere to put it
     * yet. When it gets a real column, save it in this same update.
     */
    open suspend fun completeOnboarding(): Result<Unit> =
        updateMyProfile(buildJsonObject { put("onboarding_completed", true) })

    private suspend fun updateMyProfile(fields: JsonObject): Result<Unit> = runCatching {
        val uid = requireUserId()
        supabase.postgrest
            .from(PROFILES)
            .update(fields) { filter { eq("id", uid) } }
        Unit
    }

    private fun requireUserId(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("No signed-in user; onboarding is unavailable")

    private companion object {
        const val PROFILES = "profiles"
    }
}