package com.example.vinyl.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ITunesSearchResponse(
    val resultCount: Int,
    val results: List<Track>
)

@Serializable
data class Track(
    @SerialName("trackId") val trackId: Long,
    @SerialName("trackName") val trackName: String,
    @SerialName("artistName") val artistName: String,
    @SerialName("collectionName") val collectionName: String? = null,
    @SerialName("artworkUrl100") val artworkUrl: String? = null,
    @SerialName("previewUrl") val previewUrl: String? = null,
    @SerialName("trackTimeMillis") val durationMs: Long? = null,
    @SerialName("primaryGenreName") val genre: String? = null,
) {

    fun toSubmitSongParams(): Map<String, Any?> = mapOf(
        "p_provider" to "itunes",
        "p_provider_track_id" to trackId.toString(),
        "p_title" to trackName,
        "p_artist" to artistName,
        "p_album" to collectionName,
        "p_artwork_url" to artworkUrl,
        "p_preview_url" to previewUrl,
        "p_duration_ms" to durationMs,
        "p_track_genres" to (genre?.let { listOf(it) } ?: emptyList<String>()),
    )
}