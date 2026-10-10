package com.example.vinyl.data.repository

import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.SentCard
import com.example.vinyl.repository.ShelfEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionMappingTest {

    private fun kept(id: String, mood: String? = "calm") = RoomCard(
        recommendationId = "rec-$id",
        submissionId = id,
        message = "hello",
        mood = mood,
        trackTitle = "Song $id",
        trackArtist = "Artist $id",
        artworkUrl = "https://example.com/$id.jpg",
        saved = true,
    )

    private fun sent(id: String, createdAt: String?) = SentCard(
        submissionId = id,
        message = "",
        mood = "happy",
        createdAt = createdAt,
        trackTitle = "Sent $id",
        trackArtist = "Me",
    )

    @Test
    fun `kept record takes its star and kept-at time from the shelf entry`() {
        val record = kept("a").toVinylRecord(
            ShelfEntry("a", isFavourite = true, savedAt = "2026-10-05T10:00:00+00:00"),
        )

        assertEquals(RecordSource.RECEIVED, record.source)
        assertTrue(record.isFavourite)
        assertEquals(1_791_194_400_000L, record.collectedAtMs)
        assertEquals("https://example.com/a.jpg", record.coverUrl)
        assertEquals("Calm", record.mood)
        assertEquals("hello", record.message)
    }

    @Test
    fun `kept record carries what its music card needs`() {
        val card = kept("a").copy(lat = -37.81, lng = 144.96, submittedAt = "2026-10-04T09:00:00+00:00")
        val record = card.toVinylRecord(entry = null)

        assertEquals(MoodTag.Calm, record.moodTag)
        assertEquals(-37.81, record.senderLat!!, 0.0)
        assertEquals(144.96, record.senderLng!!, 0.0)
        assertEquals("2026-10-04T09:00:00+00:00", record.sentAt)
    }

    @Test
    fun `sent record has its send time but no sender location`() {
        val record = sent("s", createdAt = "2026-10-03T00:00:00+00:00").toVinylRecord()

        assertEquals(MoodTag.Happy, record.moodTag)
        assertEquals("2026-10-03T00:00:00+00:00", record.sentAt)
        assertNull(record.senderLat)
        assertNull(record.senderLng)
    }

    @Test
    fun `sent record keeps its preview so it can be played`() {
        val card = sent("s", createdAt = null).copy(previewUrl = "https://example.com/s.m4a")

        assertEquals("https://example.com/s.m4a", card.toVinylRecord().previewUrl)
    }

    @Test
    fun `missing shelf entry means no star rather than a failure`() {
        val record = kept("a").toVinylRecord(entry = null)

        assertFalse(record.isFavourite)
        assertNull(record.collectedAtMs)
    }

    @Test
    fun `unknown mood keeps its raw value and a blank message is dropped`() {
        assertEquals("ecstatic", kept("a", mood = "ecstatic").toVinylRecord(null).mood)
        assertNull(sent("s", createdAt = null).toVinylRecord().message)
    }

    @Test
    fun `merged collection is newest first with undated records last`() {
        val merged = mergeCollection(
            shelf = listOf(kept("old"), kept("new"), kept("undated")),
            entries = listOf(
                ShelfEntry("old", savedAt = "2026-10-01T00:00:00+00:00"),
                ShelfEntry("new", savedAt = "2026-10-05T00:00:00+00:00"),
            ),
            sent = listOf(sent("mid", createdAt = "2026-10-03T00:00:00.123456+00:00")),
        )

        assertEquals(listOf("new", "mid", "old", "undated"), merged.map { it.id })
        assertEquals(RecordSource.SENT, merged[1].source)
    }

    @Test
    fun `placeholder colour is stable for the same id`() {
        assertEquals(placeholderAccent("abc"), placeholderAccent("abc"))
    }

    @Test
    fun `placeholder colour is valid even for ids with a negative hash`() {
        // "polygenelubricants".hashCode() is Int.MIN_VALUE, where a plain % would go negative.
        placeholderAccent("polygenelubricants")
        placeholderAccent("")
    }

    @Test
    fun `timestamps in any offset parse to the same instant`() {
        val utc = 1_791_194_400_000L
        assertEquals(utc, parseTimestampMs("2026-10-05T10:00:00+00:00"))
        assertEquals(utc, parseTimestampMs("2026-10-05T21:00:00+11:00"))
        assertEquals(utc, parseTimestampMs("2026-10-05T10:00:00Z"))
    }

    @Test
    fun `unreadable or missing timestamps give null rather than throwing`() {
        assertNull(parseTimestampMs(null))
        assertNull(parseTimestampMs(""))
        assertNull(parseTimestampMs("yesterday"))
        assertNull(parseTimestampMs("2026-10-05"))
    }

    @Test
    fun `sent record is marked sent, never starred, and dated by when it was sent`() {
        val record = sent("s", createdAt = "2026-10-05T10:00:00+00:00").toVinylRecord()

        assertEquals(RecordSource.SENT, record.source)
        assertFalse(record.isFavourite)
        assertEquals(1_791_194_400_000L, record.collectedAtMs)
        assertEquals("Happy", record.mood)
    }

    @Test
    fun `missing mood stays missing`() {
        assertNull(kept("a", mood = null).toVinylRecord(null).mood)
    }

    @Test
    fun `shelf entries for records not on the shelf are ignored`() {
        val merged = mergeCollection(
            shelf = listOf(kept("a")),
            entries = listOf(ShelfEntry("gone", isFavourite = true)),
            sent = emptyList(),
        )

        assertEquals(listOf("a"), merged.map { it.id })
        assertFalse(merged.single().isFavourite)
    }

    @Test
    fun `undated records keep the server's order`() {
        val merged = mergeCollection(
            shelf = listOf(kept("1"), kept("2"), kept("3")),
            entries = emptyList(),
            sent = emptyList(),
        )

        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }
}
