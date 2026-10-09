package com.example.vinyl.data.model

import com.example.vinyl.data.MoodTag

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
    /** The note that came with the record, shown when it's opened from the shelf. */
    val message: String? = null,
    /** When it was kept (received) or sent, as epoch millis. Orders "Recently collected". */
    val collectedAtMs: Long? = null,
    /** The mood as the app's enum, for the music card's mood badge. Null for one it doesn't know. */
    val moodTag: MoodTag? = null,
    /** Where a received card was sent from (a city centre). Null for sent cards and for senders who didn't share. */
    val senderLat: Double? = null,
    val senderLng: Double? = null,
    /** When the card was sent, as the server's ISO timestamp. Shown as "3 hr. ago" on the card. */
    val sentAt: String? = null,
    /** The song's 30-second preview, for "Play this song". Null when the track has none. */
    val previewUrl: String? = null,
)
