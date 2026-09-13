package com.example.vinyl.repository

import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import com.example.vinyl.data.Supabase
import com.example.vinyl.network.ITunesApiService
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


open class SubmissionRepository(
    private val iTunes: ITunesApiService = ITunesApiService(),
    private val supabase: io.github.jan.supabase.SupabaseClient = Supabase.client,
) {
    open suspend fun searchSongs(query: String): List<Track> = iTunes.searchSongs(query)


    // Submits a song. Returns the new submission's uuid on success.
    open suspend fun submitSong(
        track: Track,
        message: String,
        mood: MoodTag?,
        context: ContextTag? = null,
        submissionGenres: List<String> = emptyList(),
        lat: Double? = null,
        lng: Double? = null,
    ): Result<String> = runCatching {
        val params = buildJsonObject {
            put("p_provider", "itunes")
            put("p_provider_track_id", track.trackId.toString())
            put("p_title", track.trackName)
            put("p_artist", track.artistName)
            put("p_message", message)
            mood?.let { put("p_mood", it.wireValue) } ?: put("p_mood", JsonNull)

            track.collectionName?.let { put("p_album", it) } ?: put("p_album", JsonNull)
            track.artworkUrl?.let { put("p_artwork_url", it) } ?: put("p_artwork_url", JsonNull)
            track.previewUrl?.let { put("p_preview_url", it) } ?: put("p_preview_url", JsonNull)
            track.durationMs?.let { put("p_duration_ms", it) } ?: put("p_duration_ms", JsonNull)
            put("p_track_genres", JsonArray(track.genre?.let { listOf(JsonPrimitive(it)) } ?: emptyList()))

            context?.let { put("p_context", it.wireValue) } ?: put("p_context", JsonNull)
            put("p_genres", JsonArray(submissionGenres.map { JsonPrimitive(it) }))

            lat?.let { put("p_lat", it) } ?: put("p_lat", JsonNull)
            lng?.let { put("p_lng", it) } ?: put("p_lng", JsonNull)
        }

        // submit_song() returns a bare uuid
        supabase.postgrest.rpc("submit_song", params).decodeAs<String>()
    }
}