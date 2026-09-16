package com.example.vinyl.data.model

enum class RecordSource {
    RECEIVED,
    SENT,
}

data class VinylRecord(
    val id: String,
    val songName: String,
    val artist: String,
    val coverUrl: String?,
    val accentColor: Long,
    // Bundled drawable for the handful of records that ship with real designed cover art
    // (already composited with the peeking vinyl disc) instead of the flat-color placeholder.
    val localCoverRes: Int? = null,
    val isFavourite: Boolean = false,
    val isNew: Boolean = false,
    val source: RecordSource = RecordSource.RECEIVED,
    val mood: String? = null,
)
