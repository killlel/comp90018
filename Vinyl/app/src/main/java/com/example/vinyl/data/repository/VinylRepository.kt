package com.example.vinyl.data.repository

import com.example.vinyl.data.model.VinylRecord

interface VinylRepository {
    suspend fun getCollection(): List<VinylRecord>
}
