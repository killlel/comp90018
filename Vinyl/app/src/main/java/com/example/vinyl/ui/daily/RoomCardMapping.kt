package com.example.vinyl.ui.daily

import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.location.Distance
import com.example.vinyl.repository.RoomCard

/** Shown in place of a distance when the reader hasn't stored a location. */
internal const val NO_READER_LOCATION_NOTE = "Update your location in Settings to view distance"

/** Shown in place of a distance when the letter was sent without one. */
internal const val NO_SENDER_LOCATION_NOTE = "The sender did not attach their location"

/**
 * Turns a room card into a picker option, working out the distance from the letter's centroid to
 * the reader's own.
 *
 * Exactly one of `distanceLabel` / `distanceNote` comes back non-null. When both ends are
 * missing the reader's note wins — it's the one they can actually do something about.
 */
internal fun RoomCard.toArrivedOption(readerLat: Double?, readerLng: Double?): ArrivedRecordOption {
    val label = Distance.labelOrNull(lat, lng, readerLat, readerLng)
    val note = when {
        label != null -> null
        readerLat == null || readerLng == null -> NO_READER_LOCATION_NOTE
        else -> NO_SENDER_LOCATION_NOTE
    }
    val tag = moodTag

    return ArrivedRecordOption(
        id = recommendationId,
        trackName = trackTitle,
        artistName = trackArtist,
        messagePreview = message,
        moodLabel = MoodOptions.all.firstOrNull { it.tag == tag }?.title ?: mood.orEmpty(),
        distanceLabel = label,
        artworkUrl = artworkUrl,
        distanceNote = note,
        mood = tag,
    )
}
