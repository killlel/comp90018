package com.example.vinyl.data.repository

import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.SentCard
import com.example.vinyl.repository.ShelfEntry

/**
 * Sleeve colours for records without artwork, picked by id so a record keeps its colour between
 * visits. Taken from the colours the shelf design already uses.
 */
private val PlaceholderAccents = listOf(
    0xFF6B3B32, 0xFFF4F0EA, 0xFF77EDE5, 0xFF8A4A3E, 0xFF3E5C76, 0xFFB08D57,
)

internal fun placeholderAccent(id: String): Long =
    PlaceholderAccents[Math.floorMod(id.hashCode(), PlaceholderAccents.size)]

/** "Sad", "Calm" … the label the questionnaire uses, or the raw value for one the app doesn't know. */
private fun moodLabel(tag: MoodTag?, raw: String?): String? =
    MoodOptions.all.firstOrNull { it.tag == tag }?.title ?: raw

/**
 * Postgres ISO-8601 to epoch millis, or null. A value we can't read leaves one record unordered
 * rather than failing the whole collection.
 */
internal fun parseTimestampMs(value: String?): Long? = value?.let {
    runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
}

internal fun RoomCard.toVinylRecord(entry: ShelfEntry?): VinylRecord = VinylRecord(
    id = submissionId,
    songName = trackTitle,
    artist = trackArtist,
    coverUrl = artworkUrl,
    accentColor = placeholderAccent(submissionId),
    isFavourite = entry?.isFavourite ?: false,
    source = RecordSource.RECEIVED,
    mood = moodLabel(moodTag, mood),
    message = message.ifBlank { null },
    collectedAtMs = parseTimestampMs(entry?.savedAt),
    moodTag = moodTag,
    senderLat = lat,
    senderLng = lng,
    sentAt = submittedAt,
    previewUrl = previewUrl,
)

internal fun SentCard.toVinylRecord(): VinylRecord = VinylRecord(
    id = submissionId,
    songName = trackTitle,
    artist = trackArtist,
    coverUrl = artworkUrl,
    accentColor = placeholderAccent(submissionId),
    source = RecordSource.SENT,
    mood = moodLabel(moodTag, mood),
    message = message.ifBlank { null },
    collectedAtMs = parseTimestampMs(createdAt),
    moodTag = moodTag,
    sentAt = createdAt,
    previewUrl = previewUrl,
)

/**
 * Kept and sent records as one list, newest first.
 *
 * [entries] supplies the kept-at time and favourite flag that `room_card` doesn't carry. Missing
 * entries (say the favourites column isn't deployed yet) just mean no star and no timestamp.
 * The sort is stable, so records without a timestamp keep the server's own order at the end.
 */
internal fun mergeCollection(
    shelf: List<RoomCard>,
    entries: List<ShelfEntry>,
    sent: List<SentCard>,
): List<VinylRecord> {
    val bySubmission = entries.associateBy { it.submissionId }
    val records = shelf.map { it.toVinylRecord(bySubmission[it.submissionId]) } + sent.map { it.toVinylRecord() }
    return records.sortedByDescending { it.collectedAtMs ?: Long.MIN_VALUE }
}
