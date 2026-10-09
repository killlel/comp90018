package com.example.vinyl.ui.collection

import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.data.repository.VinylRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {

    /** Records every write, and fails or holds whichever calls a test asks it to. */
    private class FakeRepository(var records: List<VinylRecord>) : VinylRepository {
        var loadFails = false
        var favouriteFails = false
        var removeFails = false
        var loads = 0
        val favouriteCalls = mutableListOf<Pair<String, Boolean>>()
        val removeCalls = mutableListOf<String>()

        /** When set, setFavourite waits on it, so a test can look at the state mid-write. */
        var favouriteGate: CompletableDeferred<Unit>? = null

        override suspend fun getCollection(): Result<List<VinylRecord>> {
            loads++
            return if (loadFails) Result.failure(RuntimeException("offline")) else Result.success(records)
        }

        override suspend fun setFavourite(id: String, favourite: Boolean): Result<Unit> {
            favouriteCalls += id to favourite
            favouriteGate?.await()
            return if (favouriteFails) Result.failure(RuntimeException("offline")) else Result.success(Unit)
        }

        override suspend fun remove(id: String): Result<Unit> {
            removeCalls += id
            return if (removeFails) Result.failure(RuntimeException("offline")) else Result.success(Unit)
        }
    }

    private fun record(id: String, source: RecordSource = RecordSource.RECEIVED, favourite: Boolean = false) =
        VinylRecord(
            id = id, songName = id, artist = "", coverUrl = null, accentColor = 0,
            isFavourite = favourite, source = source, mood = "Calm",
        )

    private val received = record("r1")
    private val sent = record("s1", RecordSource.SENT)

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: CollectionViewModel

    private val state get() = viewModel.uiState.value
    private fun idsIn(title: String) = state.sections.first { it.title == title }.records.map { it.id }
    private fun current(id: String) = state.sections.first().records.first { it.id == id }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeRepository(listOf(received, sent))
        viewModel = CollectionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading before anything is fetched`() {
        assertTrue(state.isLoading)
        assertEquals(0, repository.loads)
    }

    @Test
    fun `refresh publishes records, count and rows`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.totalCount)
        assertEquals(listOf("r1", "s1"), idsIn("Recently collected"))
    }

    @Test
    fun `a failed first load shows an error and stops loading`() = runTest {
        repository.loadFails = true
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals(emptyList<CollectionSection>(), state.sections)
    }

    @Test
    fun `a later refresh doesn't blank a shelf that's already showing`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.refresh()
        runCurrent()

        assertFalse(state.isLoading)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `a failed refresh keeps the records already shown`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        repository.loadFails = true
        viewModel.refresh()
        advanceUntilIdle()

        assertNotNull(state.error)
        assertEquals(listOf("r1", "s1"), idsIn("Recently collected"))
    }

    @Test
    fun `a successful refresh clears an earlier error`() = runTest {
        repository.loadFails = true
        viewModel.refresh()
        advanceUntilIdle()

        repository.loadFails = false
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(state.error)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `refresh picks up records kept since the last load`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        repository.records = repository.records + record("r2")
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(3, state.totalCount)
        assertEquals(2, repository.loads)
    }

    @Test
    fun `switching filter rebuilds rows without fetching again`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.selectFilter(CollectionFilter.SENT)

        assertEquals(CollectionFilter.SENT, state.selectedFilter)
        assertEquals(listOf("s1"), idsIn("Recently collected"))
        assertEquals(1, repository.loads)
    }

    @Test
    fun `the chosen filter survives a refresh`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.selectFilter(CollectionFilter.RECEIVED)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf("r1"), idsIn("Recently collected"))
    }

    @Test
    fun `favourite flips at once, before the write finishes`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        repository.favouriteGate = CompletableDeferred()

        viewModel.toggleFavourite(received)
        runCurrent()

        assertTrue(current("r1").isFavourite)
        assertEquals(listOf("r1" to true), repository.favouriteCalls)

        repository.favouriteGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(current("r1").isFavourite)
        assertNull(state.actionError)
    }

    @Test
    fun `a failed favourite flips back and says so`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        repository.favouriteFails = true

        viewModel.toggleFavourite(received)
        advanceUntilIdle()

        assertFalse(current("r1").isFavourite)
        assertNotNull(state.actionError)
    }

    @Test
    fun `unstarring sends false`() = runTest {
        repository.records = listOf(record("r1", favourite = true))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.toggleFavourite(current("r1"))
        advanceUntilIdle()

        assertEquals(listOf("r1" to false), repository.favouriteCalls)
        assertFalse(current("r1").isFavourite)
    }

    @Test
    fun `favouriting the open record updates the dialog too`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openRecord(received)

        viewModel.toggleFavourite(received)
        advanceUntilIdle()

        assertTrue(state.openRecord!!.isFavourite)
    }

    @Test
    fun `a starred record shows up in the favourites row`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.toggleFavourite(received)
        advanceUntilIdle()

        assertEquals(listOf("r1"), idsIn("Favourites"))
    }

    @Test
    fun `sent records can't be favourited or removed`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.toggleFavourite(sent)
        viewModel.remove(sent)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Boolean>>(), repository.favouriteCalls)
        assertEquals(emptyList<String>(), repository.removeCalls)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `remove takes the record off the shelf and closes it`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openRecord(received)

        viewModel.remove(received)
        advanceUntilIdle()

        assertEquals(listOf("r1"), repository.removeCalls)
        assertNull(state.openRecord)
        assertEquals(1, state.totalCount)
        assertEquals(listOf("s1"), idsIn("Recently collected"))
    }

    @Test
    fun `remove waits for the server before the record disappears`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.remove(received)

        assertEquals(2, state.totalCount)
        advanceUntilIdle()
        assertEquals(1, state.totalCount)
    }

    @Test
    fun `a failed remove keeps the record and leaves the dialog open with an error`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openRecord(received)
        repository.removeFails = true

        viewModel.remove(received)
        advanceUntilIdle()

        assertEquals(2, state.totalCount)
        assertEquals("r1", state.openRecord?.id)
        assertNotNull(state.actionError)
    }

    @Test
    fun `opening or closing a record clears the last action error`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        repository.removeFails = true
        viewModel.openRecord(received)
        viewModel.remove(received)
        advanceUntilIdle()

        viewModel.closeRecord()
        assertNull(state.openRecord)
        assertNull(state.actionError)

        viewModel.remove(received)
        advanceUntilIdle()
        viewModel.openRecord(sent)
        assertEquals("s1", state.openRecord?.id)
        assertNull(state.actionError)
    }

    // ---------------------------------------------------------------- "See all" grid

    private val gridIds get() = state.gridRecords.map { it.id }

    @Test
    fun `see all opens on the shelves' filter with no search`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.selectFilter(CollectionFilter.SENT)

        viewModel.openGrid()

        assertTrue(state.isGridOpen)
        assertFalse(state.isSearching)
        assertEquals("", state.searchQuery)
        assertEquals(listOf("s1"), gridIds)
    }

    @Test
    fun `switching pills in the grid refilters it`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openGrid()

        viewModel.selectFilter(CollectionFilter.RECEIVED)

        assertEquals(listOf("r1"), gridIds)
    }

    @Test
    fun `closing the search box clears the query`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openGrid()
        viewModel.openSearch()
        viewModel.search("nothing matches this")
        assertEquals(emptyList<String>(), gridIds)

        viewModel.closeSearch()

        assertFalse(state.isSearching)
        assertEquals("", state.searchQuery)
        assertEquals(listOf("r1", "s1"), gridIds)
    }

    @Test
    fun `removing the last record leaves the grid open and empty`() = runTest {
        repository.records = listOf(received)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openGrid()
        viewModel.openRecord(received)

        viewModel.remove(received)
        advanceUntilIdle()

        assertTrue(state.isGridOpen)
        assertNull(state.openRecord)
        assertEquals(emptyList<String>(), gridIds)
    }

    @Test
    fun `closing the grid resets its search`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openGrid()
        viewModel.openSearch()
        viewModel.search("r1")

        viewModel.closeGrid()

        assertFalse(state.isGridOpen)
        assertFalse(state.isSearching)
        assertEquals("", state.searchQuery)
    }
}
