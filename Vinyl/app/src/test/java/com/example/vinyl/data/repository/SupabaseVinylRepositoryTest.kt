package com.example.vinyl.data.repository

import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.repository.RoomRepository
import com.example.vinyl.repository.SentCard
import com.example.vinyl.repository.ShelfEntry
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseVinylRepositoryTest {

    /**
     * Stands in for the network. The client it's handed is never called — every method the
     * collection uses is overridden — it only exists because RoomRepository's default would
     * build the real one from BuildConfig.
     */
    private class FakeRoom(
        var shelf: Result<List<RoomCard>> = Result.success(emptyList()),
        var entries: Result<List<ShelfEntry>> = Result.success(emptyList()),
        var sent: Result<List<SentCard>> = Result.success(emptyList()),
        var writeResult: Result<Unit> = Result.success(Unit),
    ) : RoomRepository(createSupabaseClient("https://test.supabase.co", "test-key") {}) {
        val favouriteCalls = mutableListOf<Pair<String, Boolean>>()
        val unkeepCalls = mutableListOf<String>()

        override suspend fun getShelf(limit: Int) = shelf
        override suspend fun getShelfEntries() = entries
        override suspend fun getMySubmissions(limit: Int) = sent

        override suspend fun setFavourite(submissionId: String, favourite: Boolean): Result<Unit> {
            favouriteCalls += submissionId to favourite
            return writeResult
        }

        override suspend fun unkeep(submissionId: String): Result<Unit> {
            unkeepCalls += submissionId
            return writeResult
        }
    }

    private fun kept(id: String) = RoomCard(recommendationId = "rec-$id", submissionId = id, trackTitle = id)
    private fun sent(id: String, createdAt: String? = null) =
        SentCard(submissionId = id, trackTitle = id, createdAt = createdAt)

    private val boom = RuntimeException("offline")

    @Test
    fun `kept and sent records come back as one list`() = runBlocking {
        val room = FakeRoom(
            shelf = Result.success(listOf(kept("k"))),
            entries = Result.success(listOf(ShelfEntry("k", isFavourite = true, savedAt = "2026-10-05T00:00:00+00:00"))),
            sent = Result.success(listOf(sent("s", createdAt = "2026-10-01T00:00:00+00:00"))),
        )

        val records = SupabaseVinylRepository(room).getCollection().getOrThrow()

        assertEquals(listOf("k", "s"), records.map { it.id })
        assertEquals(listOf(RecordSource.RECEIVED, RecordSource.SENT), records.map { it.source })
        assertTrue(records.first().isFavourite)
    }

    @Test
    fun `nothing kept or sent is an empty shelf, not an error`() = runBlocking {
        val result = SupabaseVinylRepository(FakeRoom()).getCollection()

        assertEquals(emptyList<Any>(), result.getOrThrow())
    }

    @Test
    fun `a failed shelf still shows sent records`() = runBlocking {
        val room = FakeRoom(shelf = Result.failure(boom), sent = Result.success(listOf(sent("s"))))

        assertEquals(listOf("s"), SupabaseVinylRepository(room).getCollection().getOrThrow().map { it.id })
    }

    @Test
    fun `failed sent records still show the shelf`() = runBlocking {
        val room = FakeRoom(shelf = Result.success(listOf(kept("k"))), sent = Result.failure(boom))

        assertEquals(listOf("k"), SupabaseVinylRepository(room).getCollection().getOrThrow().map { it.id })
    }

    @Test
    fun `failed shelf entries mean no stars, not a failed collection`() = runBlocking {
        val room = FakeRoom(shelf = Result.success(listOf(kept("k"))), entries = Result.failure(boom))

        val records = SupabaseVinylRepository(room).getCollection().getOrThrow()

        assertEquals(listOf("k"), records.map { it.id })
        assertFalse(records.single().isFavourite)
    }

    @Test
    fun `fails only when both halves fail, with the shelf's error`() = runBlocking {
        val shelfError = RuntimeException("shelf down")
        val room = FakeRoom(shelf = Result.failure(shelfError), sent = Result.failure(boom))

        val result = SupabaseVinylRepository(room).getCollection()

        assertTrue(result.isFailure)
        assertSame(shelfError, result.exceptionOrNull())
    }

    @Test
    fun `favourite and remove go to the shelf row for that record`() = runBlocking {
        val room = FakeRoom()
        val repository = SupabaseVinylRepository(room)

        repository.setFavourite("k", true)
        repository.remove("k")

        assertEquals(listOf("k" to true), room.favouriteCalls)
        assertEquals(listOf("k"), room.unkeepCalls)
    }

    @Test
    fun `write failures are passed back to the caller`() = runBlocking {
        val repository = SupabaseVinylRepository(FakeRoom(writeResult = Result.failure(boom)))

        assertSame(boom, repository.setFavourite("k", false).exceptionOrNull())
        assertSame(boom, repository.remove("k").exceptionOrNull())
    }
}
