package com.example.vinyl.data.repository

import com.example.vinyl.data.model.VinylRecord

interface VinylRepository {
    /**
     * Everything on the user's shelf, newest first. Fails only when nothing at all could be
     * loaded; if one half (received or sent) fails, the other half still comes back.
     */
    suspend fun getCollection(): Result<List<VinylRecord>>

    /** Stars or unstars a received record. Sent records can't be favourites. */
    suspend fun setFavourite(id: String, favourite: Boolean): Result<Unit>

    /** Takes a received record off the shelf. */
    suspend fun remove(id: String): Result<Unit>
}
