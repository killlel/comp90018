package com.example.vinyl.repository

import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import kotlinx.coroutines.delay

class FakeSubmissionRepository : SubmissionRepository() {
    override suspend fun searchSongs(query: String): List<Track> {
        delay(300) // simulate network latency
        if (query.isBlank()) return emptyList()
        return listOf(
            Track(trackId = 1, trackName = "Landslide", artistName = "Fleetwood Mac", collectionName = "Fleetwood Mac"),
            Track(trackId = 2, trackName = "Nights", artistName = "Frank Ocean", collectionName = "Blonde"),
        )
    }

    override suspend fun submitSong(
        track: Track, message: String, mood: MoodTag, context: ContextTag?,
        submissionGenres: List<String>, lat: Double?, lng: Double?,
    ): Result<String> {
        delay(500)
        return Result.success("fake-submission-id")
    }
}