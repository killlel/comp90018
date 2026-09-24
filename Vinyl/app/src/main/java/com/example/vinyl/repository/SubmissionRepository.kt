package com.example.vinyl.repository

import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import com.example.vinyl.data.Supabase
import com.example.vinyl.network.ITunesApiService
import com.example.vinyl.data.onboarding.GenreOption
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


open class SubmissionRepository(
    private val iTunes: ITunesApiService = ITunesApiService(),
    private val supabase: io.github.jan.supabase.SupabaseClient = Supabase.client,
) {
    open suspend fun searchSongs(query: String): List<Track> = iTunes.searchSongs(query)

    /**
     * The genres a letter can be tagged with, from the `genres` table. The database only accepts
     * their slugs (`k_pop`) - a display label (`K-pop`) is rejected with 23514 - so the write
     * screen must read this rather than use a hardcoded list.
     */
    open suspend fun getGenreOptions(): Result<List<GenreOption>> = runCatching {
        supabase.postgrest
            .from("genres")
            .select(Columns.list("slug", "label", "sort_order")) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<GenreOption>()
    }


    // Submits a song. Returns the new submission's uuid on success.
    open suspend fun submitSong(
        track: Track,
        message: String,
        mood: MoodTag?,
        context: ContextTag? = null,
        submissionGenres: List<String> = emptyList(),
        attachLocation: Boolean = false,
    ): Result<String> = runCatching {
        val params = submitSongParams(track, message, mood, context, submissionGenres, attachLocation)

        // submit_song() returns a bare uuid
        supabase.postgrest.rpc("submit_song", params).decodeAs<String>()
    }
}

/**
 * The named arguments for the `submit_song` RPC. Kept apart from the network call so a test can
 * check exactly what is sent: the names must match the SQL function, or the call fails with
 * PGRST202.
 */
internal fun submitSongParams(
    track: Track,
    message: String,
    mood: MoodTag?,
    context: ContextTag?,
    submissionGenres: List<String>,
    attachLocation: Boolean,
): JsonObject = buildJsonObject {
    put("p_provider", "itunes")
    put("p_provider_track_id", track.trackId.toString())
    put("p_title", track.trackName)
    put("p_artist", track.artistName)
    put("p_message", message)
    put("p_mood", mood?.wireValue)

    put("p_album", track.collectionName)
    put("p_artwork_url", track.artworkUrl)
    put("p_preview_url", track.previewUrl)
    put("p_duration_ms", track.durationMs)
    put("p_track_genres", JsonArray(track.genre?.let { listOf(JsonPrimitive(it)) } ?: emptyList()))

    put("p_context", context?.wireValue)
    put("p_genres", JsonArray(submissionGenres.map { JsonPrimitive(it) }))

    put("p_attach_location", attachLocation)
}