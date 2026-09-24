package com.example.vinyl.repository

import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Supabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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

    private companion object {
        /** "One of three" — matches the Arrived Today design. */
        const val DEFAULT_LIMIT = 3
    }
}

/** The named arguments for the `request_recommendations` RPC; see [submitSongParams]. */
internal fun requestRecommendationsParams(mood: MoodTag, context: ContextTag?, limit: Int): JsonObject =
    buildJsonObject {
        put("p_mood", mood.wireValue)
        put("p_context", context?.wireValue)
        put("p_limit", limit)
    }