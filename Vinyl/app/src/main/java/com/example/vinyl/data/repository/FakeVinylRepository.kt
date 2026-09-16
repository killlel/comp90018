package com.example.vinyl.data.repository

import com.example.vinyl.R
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord

// Stands in for the Supabase-backed repository until the collection table/schema exists.
class FakeVinylRepository : VinylRepository {
    override suspend fun getCollection(): List<VinylRecord> = sampleRecords
}

private val sampleRecords = listOf(
    VinylRecord(
        id = "1",
        songName = "Midnight Drive",
        artist = "Various Artists",
        coverUrl = null,
        accentColor = 0xFF6B3B32,
        localCoverRes = R.drawable.cover_midnight_drive,
        isFavourite = true,
        isNew = true,
        source = RecordSource.RECEIVED,
        mood = "Sad Mood",
    ),
    VinylRecord(
        id = "2",
        songName = "Plastic Dreams",
        artist = "Luna Vale",
        coverUrl = null,
        accentColor = 0xFFF4F0EA,
        source = RecordSource.RECEIVED,
    ),
    VinylRecord(
        id = "3",
        songName = "Solstice",
        artist = "Kai & Sun",
        coverUrl = null,
        accentColor = 0xFF77EDE5,
        isFavourite = true,
        source = RecordSource.RECEIVED,
        mood = "Time to chill",
    ),
    VinylRecord(
        id = "4",
        songName = "A Brighter Day",
        artist = "Nora Fields",
        coverUrl = null,
        accentColor = 0xFF8A4A3E,
        localCoverRes = R.drawable.cover_a_brighter_day,
        source = RecordSource.SENT,
        mood = "Sad Mood",
    ),
    VinylRecord(
        id = "5",
        songName = "Cloud Nine",
        artist = "Sunday Club",
        coverUrl = null,
        accentColor = 0xFF5C6266,
        source = RecordSource.SENT,
        mood = "Time to chill",
    ),
    VinylRecord(
        id = "6",
        songName = "Echoes",
        artist = "Rivo",
        coverUrl = null,
        accentColor = 0xFF4A2E27,
        source = RecordSource.RECEIVED,
        mood = "Sad Mood",
    ),
)
