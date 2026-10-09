package com.example.vinyl.ui.collection

import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterRecordsTest {

    private fun record(
        id: String,
        song: String,
        artist: String,
        mood: String? = null,
        source: RecordSource = RecordSource.RECEIVED,
        favourite: Boolean = false,
    ) = VinylRecord(
        id = id, songName = song, artist = artist, coverUrl = null, accentColor = 0,
        isFavourite = favourite, source = source, mood = mood,
    )

    private val records = listOf(
        record("1", "Blinding Lights", "The Weeknd", mood = "Energetic", favourite = true),
        record("2", "Shake It Off", "Taylor Swift", mood = "Energetic"),
        record("3", "September", "Earth, Wind & Fire", mood = "Happy", source = RecordSource.SENT),
    )

    private fun ids(filter: CollectionFilter, query: String) = filterRecords(records, filter, query).map { it.id }

    @Test
    fun `blank query keeps the whole filter in order`() {
        assertEquals(listOf("1", "2", "3"), ids(CollectionFilter.ALL, ""))
        assertEquals(listOf("1", "2", "3"), ids(CollectionFilter.ALL, "   "))
    }

    @Test
    fun `matches song, artist or mood ignoring case`() {
        assertEquals(listOf("1"), ids(CollectionFilter.ALL, "blinding"))
        assertEquals(listOf("2"), ids(CollectionFilter.ALL, "SWIFT"))
        assertEquals(listOf("3"), ids(CollectionFilter.ALL, "happy"))
    }

    @Test
    fun `query only searches within the selected pill`() {
        assertEquals(listOf("1", "2"), ids(CollectionFilter.RECEIVED, "energetic"))
        assertEquals(emptyList<String>(), ids(CollectionFilter.SENT, "energetic"))
        assertEquals(listOf("1"), ids(CollectionFilter.FAVOURITES, ""))
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals(listOf("3"), ids(CollectionFilter.ALL, "  september "))
    }
}
