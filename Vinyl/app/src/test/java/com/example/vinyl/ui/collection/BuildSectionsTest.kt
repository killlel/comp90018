package com.example.vinyl.ui.collection

import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildSectionsTest {

    private fun record(id: String, source: RecordSource, favourite: Boolean = false, mood: String? = null) =
        VinylRecord(
            id = id, songName = id, artist = "", coverUrl = null, accentColor = 0,
            isFavourite = favourite, source = source, mood = mood,
        )

    private val records = listOf(
        record("r1", RecordSource.RECEIVED, favourite = true, mood = "Sad"),
        record("r2", RecordSource.RECEIVED, mood = "Calm"),
        record("s1", RecordSource.SENT, mood = "Sad"),
    )

    private fun List<CollectionSection>.titles() = map { it.title }
    private fun List<CollectionSection>.ids(title: String) = first { it.title == title }.records.map { it.id }

    @Test
    fun `all shows every row, with mood rows in first-seen order`() {
        val sections = buildSections(records, CollectionFilter.ALL)

        assertEquals(listOf("Recently collected", "Favourites", "Sad", "Calm"), sections.titles())
        assertEquals(listOf("r1", "s1"), sections.ids("Sad"))
    }

    @Test
    fun `received and sent tabs only hold their own records`() {
        assertEquals(listOf("r1", "r2"), buildSections(records, CollectionFilter.RECEIVED).ids("Recently collected"))
        assertEquals(listOf("s1"), buildSections(records, CollectionFilter.SENT).ids("Recently collected"))
    }

    @Test
    fun `favourites tab keeps its row even when empty`() {
        val sections = buildSections(records.filterNot { it.isFavourite }, CollectionFilter.FAVOURITES)

        assertEquals(listOf("Favourites"), sections.titles())
        assertEquals(emptyList<String>(), sections.ids("Favourites"))
    }

    @Test
    fun `an empty shelf has no rows`() {
        assertEquals(emptyList<CollectionSection>(), buildSections(emptyList(), CollectionFilter.ALL))
    }

    @Test
    fun `no favourites means no favourites row outside its own tab`() {
        val sections = buildSections(records.filterNot { it.isFavourite }, CollectionFilter.ALL)

        assertEquals(listOf("Recently collected", "Calm", "Sad"), sections.titles())
    }

    @Test
    fun `favourites tab holds only starred records, across sources`() {
        val withStarredSent = records + record("s2", RecordSource.SENT, favourite = true)

        assertEquals(listOf("r1", "s2"), buildSections(withStarredSent, CollectionFilter.FAVOURITES).ids("Favourites"))
    }

    @Test
    fun `records without a mood get no mood row but still appear in recently collected`() {
        val sections = buildSections(listOf(record("x", RecordSource.RECEIVED)), CollectionFilter.ALL)

        assertEquals(listOf("Recently collected"), sections.titles())
        assertEquals(listOf("x"), sections.ids("Recently collected"))
    }

    @Test
    fun `mood rows are scoped to the tab`() {
        assertEquals(listOf("Recently collected", "Sad"), buildSections(records, CollectionFilter.SENT).titles())
    }

    @Test
    fun `a tab with nothing in it has no rows`() {
        val receivedOnly = records.filter { it.source == RecordSource.RECEIVED }

        assertEquals(emptyList<CollectionSection>(), buildSections(receivedOnly, CollectionFilter.SENT))
    }

    @Test
    fun `rows keep the order records came in`() {
        val ordered = listOf(
            record("c", RecordSource.RECEIVED, mood = "Calm"),
            record("a", RecordSource.RECEIVED, mood = "Calm"),
            record("b", RecordSource.RECEIVED, mood = "Calm"),
        )

        assertEquals(listOf("c", "a", "b"), buildSections(ordered, CollectionFilter.ALL).ids("Calm"))
    }
}
