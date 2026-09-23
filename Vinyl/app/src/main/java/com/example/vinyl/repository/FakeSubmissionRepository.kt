package com.example.vinyl.repository

import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import kotlinx.coroutines.delay

/**
 * Stubs out submission only — search still hits the real iTunes API via the inherited
 * SubmissionRepository.searchSongs(). Use this while the submit_song Postgres function's
 * permissions are being sorted out, so "Send this record" succeeds locally without touching
 * Supabase at all.
 */
class FakeSubmissionRepository : SubmissionRepository() {
    override suspend fun submitSong(
        track: Track, message: String, mood: MoodTag?, context: ContextTag?,
        submissionGenres: List<String>, attachLocation: Boolean,
    ): Result<String> {
        delay(500) // simulate network latency so the send animation still has something to wait on
        return Result.success("fake-submission-id")
    }
}