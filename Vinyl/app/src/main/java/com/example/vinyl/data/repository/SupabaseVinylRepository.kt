package com.example.vinyl.data.repository

import android.util.Log
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.repository.RoomRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The Collection backed by Supabase: kept records from `get_shelf()`, sent ones from
 * `get_my_submissions()`, and favourites from the caller's own `shelf_items` rows.
 */
class SupabaseVinylRepository(private val room: RoomRepository = RoomRepository()) : VinylRepository {

    override suspend fun getCollection(): Result<List<VinylRecord>> = coroutineScope {
        // Independent calls, so they overlap rather than queue.
        val shelfCall = async { room.getShelf() }
        val entriesCall = async { room.getShelfEntries() }
        val sentCall = async { room.getMySubmissions() }

        val shelf = shelfCall.await()
        val entries = entriesCall.await()
        val sent = sentCall.await()

        shelf.exceptionOrNull()?.let { Log.w(TAG, "get_shelf failed", it) }
        entries.exceptionOrNull()?.let { Log.w(TAG, "shelf_items read failed; no favourites shown", it) }
        sent.exceptionOrNull()?.let { Log.w(TAG, "get_my_submissions failed", it) }

        if (shelf.isFailure && sent.isFailure) {
            Result.failure(shelf.exceptionOrNull()!!)
        } else {
            Result.success(
                mergeCollection(
                    shelf = shelf.getOrNull().orEmpty(),
                    entries = entries.getOrNull().orEmpty(),
                    sent = sent.getOrNull().orEmpty(),
                ),
            )
        }
    }

    override suspend fun setFavourite(id: String, favourite: Boolean): Result<Unit> =
        room.setFavourite(id, favourite)

    override suspend fun remove(id: String): Result<Unit> = room.unkeep(id)

    private companion object {
        const val TAG = "SupabaseVinylRepository"
    }
}
