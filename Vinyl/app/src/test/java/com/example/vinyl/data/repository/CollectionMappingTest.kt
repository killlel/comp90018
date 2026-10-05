package com.example.vinyl.data.repository

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
}
