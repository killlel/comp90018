package com.example.vinyl.repository

import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Supabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One letter as the recipient sees it — the `room_card` composite returned by the room RPCs.
 *
 * There is deliberately no sender id: the server leaves it out so letters stay anonymous, and a
 * direct select on `submissions` is blocked for recipients by RLS. These RPCs are the only way a
 * recipient can read a letter.
 *
 * [lat]/[lng] are the sender's city centroid snapshotted at send time, or null when they sent
 * without a location. Every field the app doesn't strictly need is defaulted, so a column added
 * or reshaped server-side degrades a card rather than failing the whole list.
 */
@Serializable
data class RoomCard(
    @SerialName("recommendation_id") val recommendationId: String,
    @SerialName("submission_id") val submissionId: String,
    val message: String = "",
    val mood: String? = null,
    val context: String? = null,
    val genres: List<String> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("track_title") val trackTitle: String = "",
    @SerialName("track_artist") val trackArtist: String = "",
    @SerialName("track_album") val trackAlbum: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    val saved: Boolean = false,
) {
    /** Null for a value the app's enum doesn't know, rather than failing to decode. */
    val moodTag: MoodTag? get() = MoodTag.entries.firstOrNull { it.wireValue == mood }
}

/**
 * A letter offered for ranking but not yet delivered — the `candidate_card` composite.
 *
 * Separate from [RoomCard] on purpose. A candidate has no `recommendation_id`, because no match
 * exists yet, and cannot be `saved`, because you cannot shelve what you were never given. Keeping
 * the types apart also means a field added here can never break the decoders that read room_card.
 *
 * Carries [timesRecommended], a count across every user that the client has no way to see for
 * itself. It is a number, never a list of who.
 */
@Serializable
data class CandidateCard(
    @SerialName("submission_id") val submissionId: String,
    val message: String = "",
    val mood: String? = null,
    val context: String? = null,
    val genres: List<String> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("track_title") val trackTitle: String = "",
    @SerialName("track_artist") val trackArtist: String = "",
    @SerialName("track_album") val trackAlbum: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    @SerialName("times_recommended") val timesRecommended: Int = 0,
) {
    val moodTag: MoodTag? get() = MoodTag.entries.firstOrNull { it.wireValue == mood }

    /**
     * [submittedAt] as epoch millis, or null if missing or unparseable. Postgres sends ISO-8601;
     * a value we cannot read is treated as absent rather than throwing, so one odd timestamp
     * degrades a single card instead of failing the whole list.
     */
    val submittedAtEpochMs: Long?
        get() = submittedAt?.let {
            runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }
                .getOrNull()
        }
}

/** One genre and how rare it is in the pool — the idf the client cannot compute for itself. */
@Serializable
data class GenreWeight(val slug: String, val weight: Double)

/**
 * The recipient side of the letter flow. Wraps the Sprint 1 RPCs, which are all
 * `security definer` and scope everything to `auth.uid()` server-side.
 */
open class RoomRepository(private val supabase: SupabaseClient = Supabase.client) {
    /** Picks new letters for this mood and records them as delivered. */
    open suspend fun requestRecommendations(
        mood: MoodTag,
        context: ContextTag? = null,
        limit: Int = DEFAULT_LIMIT,
    ): Result<List<RoomCard>> = runCatching {
        val params = requestRecommendationsParams(mood, context, limit)
        supabase.postgrest.rpc("request_recommendations", params).decodeList<RoomCard>()
    }

    /** Replays letters already delivered, without picking new ones. */
    open suspend fun getRoom(limit: Int = DEFAULT_LIMIT): Result<List<RoomCard>> = runCatching {
        val params = buildJsonObject { put("p_limit", limit) }
        supabase.postgrest.rpc("get_room", params).decodeList<RoomCard>()
    }

    /**
     * Letters the user kept, newest save first. Same `room_card` shape as the room, so a shelf
     * item renders with the card composables already written for it.
     */
    open suspend fun getShelf(limit: Int = SHELF_LIMIT): Result<List<RoomCard>> = runCatching {
        val params = buildJsonObject { put("p_limit", limit) }
        supabase.postgrest.rpc("get_shelf", params).decodeList<RoomCard>()
    }

    /**
     * Candidates to rank on the device. **Writes nothing** — nothing here is marked as seen, so
     * over-fetching twenty and showing three does not burn the other seventeen.
     *
     * The server has already applied the rules that may never move to the client: not your own,
     * not one you have seen, active only, and no sender id in the projection.
     */
    open suspend fun getCandidates(limit: Int = CANDIDATE_LIMIT): Result<List<CandidateCard>> =
        runCatching {
            val params = buildJsonObject { put("p_limit", limit) }
            supabase.postgrest.rpc("get_candidates", params).decodeList<CandidateCard>()
        }

    /**
     * Genre rarity across the whole pool. Changes only as the pool grows, so fetch once per
     * session rather than per match.
     */
    open suspend fun getGenreWeights(): Result<List<GenreWeight>> = runCatching {
        supabase.postgrest.rpc("get_genre_weights").decodeList<GenreWeight>()
    }

    /**
     * Records the cards actually shown, and returns them with their recommendation ids.
     *
     * The server re-checks every id rather than trusting the client — otherwise naming a
     * submission would be enough to hand yourself a match. Ineligible ids are dropped quietly, so
     * a card that went inactive between fetch and commit is a race rather than an error.
     */
    open suspend fun commitRecommendations(
        submissionIds: List<String>,
        mood: MoodTag,
        context: ContextTag? = null,
        scores: List<Double>? = null,
    ): Result<List<RoomCard>> = runCatching {
        val params = buildJsonObject {
            put("p_submission_ids", JsonArray(submissionIds.map { JsonPrimitive(it) }))
            put("p_mood", mood.wireValue)
            put("p_context", context?.wireValue)
            if (scores != null) {
                put("p_scores", JsonArray(scores.map { JsonPrimitive(it) }))
            }
        }
        supabase.postgrest.rpc("commit_recommendations", params).decodeList<RoomCard>()
    }

    private companion object {
        /** "One of three" — matches the Arrived Today design. */
        const val DEFAULT_LIMIT = 3

        /** The shelf is browsed rather than dealt out, so it is not capped at three. */
        const val SHELF_LIMIT = 50

        /** Enough room to rank meaningfully without pulling the pool down a call at a time. */
        const val CANDIDATE_LIMIT = 20
    }
}

/** The named arguments for the `request_recommendations` RPC; see [submitSongParams]. */
internal fun requestRecommendationsParams(mood: MoodTag, context: ContextTag?, limit: Int): JsonObject =
    buildJsonObject {
        put("p_mood", mood.wireValue)
        put("p_context", context?.wireValue)
        put("p_limit", limit)
    }