package com.example.vinyl.ui.daily

import android.text.format.DateUtils
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.location.Distance
import com.example.vinyl.repository.RoomCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Shown in place of a distance when the reader hasn't stored a location. */
internal const val NO_READER_LOCATION_NOTE = "Add your city in Settings to see the distance"

/** Shown in place of a distance when the letter was sent without one. */
internal const val NO_SENDER_LOCATION_NOTE = "The sender didn't share their city"

/**
 * How long ago a letter was sent, in the phone's language: "Just now", "12 min. ago", "3 hr. ago",
 * "Yesterday", "3 days ago", then a date once it is over a week old. Deliberately not a clock
 * time - the sender's exact minute adds nothing for the reader.
 *
 * [submittedAt] is the ISO timestamp from `room_card.submitted_at`. Anything missing or
 * unreadable gives "Recently" rather than a wrong claim.
 *
 * Uses only classes available on API 24 (java.time needs API 26 or core library desugaring).
 */
internal fun sentTimeLabel(submittedAt: String?, nowMillis: Long = System.currentTimeMillis()): String {
    val sentMillis = submittedAt?.let(::parseTimestamp) ?: return "Recently"

    // Under a minute, and also a phone clock that runs a little behind the server's.
    if (nowMillis - sentMillis < DateUtils.MINUTE_IN_MILLIS) return "Just now"

    return DateUtils.getRelativeTimeSpanString(
        sentMillis,
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

/**
 * The day a letter was sent, day first in English: "8 Oct", with the year added once it is
 * from an earlier year ("8 Oct 2025"). Read from the same timestamp as [sentTimeLabel], in the
 * phone's time zone. Null when the timestamp is missing or unreadable.
 */
internal fun sentDateLabel(submittedAt: String?, nowMillis: Long = System.currentTimeMillis()): String? {
    val sentMillis = submittedAt?.let(::parseTimestamp) ?: return null
    val sentYear = Calendar.getInstance().apply { timeInMillis = sentMillis }.get(Calendar.YEAR)
    val thisYear = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
    val pattern = if (sentYear == thisYear) "d MMM" else "d MMM yyyy"
    return SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(sentMillis))
}

/**
 * Reads `2026-09-24T14:53:53.562098+00:00`. SimpleDateFormat treats `.SSS` as MILLISECONDS, so a
 * six-digit fraction would be read as several minutes too late; whole seconds are plenty here,
 * so the fraction is dropped first.
 */
private fun parseTimestamp(value: String): Long? = runCatching {
    val wholeSeconds = value.replace(Regex("""\.\d+"""), "")
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(wholeSeconds)?.time
}.getOrNull()

/**
 * The distance band between a card's sender and the reader, or the reason there isn't one.
 *
 * Exactly one of the pair is non-null. When both ends are missing the reader's note wins — it's
 * the one they can actually do something about.
 */
internal fun distanceLabelAndNote(
    senderLat: Double?,
    senderLng: Double?,
    readerLat: Double?,
    readerLng: Double?,
): Pair<String?, String?> {
    val label = Distance.labelOrNull(senderLat, senderLng, readerLat, readerLng)
    val note = when {
        label != null -> null
        readerLat == null || readerLng == null -> NO_READER_LOCATION_NOTE
        else -> NO_SENDER_LOCATION_NOTE
    }
    return label to note
}

/**
 * Turns a room card into a picker option, working out the distance from the letter's centroid to
 * the reader's own.
 *
 * Exactly one of `distanceLabel` / `distanceNote` comes back non-null. When both ends are
 * missing the reader's note wins — it's the one they can actually do something about.
 */
internal fun RoomCard.toArrivedOption(readerLat: Double?, readerLng: Double?): ArrivedRecordOption {
    val (label, note) = distanceLabelAndNote(lat, lng, readerLat, readerLng)
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
        submissionId = submissionId,
        sentTimeLabel = sentTimeLabel(submittedAt),
        sentDateLabel = sentDateLabel(submittedAt),
        senderLat = lat,
        senderLng = lng,
        previewUrl = previewUrl,
        envelopeStyle = envelopeStyle,
    )
}