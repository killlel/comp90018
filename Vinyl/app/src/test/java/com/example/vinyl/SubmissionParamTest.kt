package com.example.vinyl


import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import com.example.vinyl.repository.requestRecommendationsParams
import com.example.vinyl.repository.submitSongParams
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks exactly what the app sends to the database when a letter is sent (`submit_song`) and when
 * records are pulled (`request_recommendations`). No network: only the JSON that would be sent.
 *
 * These exist because of a real bug: `x?.let { put(k, it) } ?: put(k, JsonNull)` looks right but
 * always sends null (put() returns the PREVIOUS value, null for a new key), so the mood - and the
 * artwork - never reached the database and every send failed.
 */
class SubmissionParamsTest {

    private val track = Track(
            trackId = 1440857781L,
            trackName = "Landslide",
            artistName = "Fleetwood Mac",
            collectionName = "Fleetwood Mac",
            artworkUrl = "https://example.com/art.jpg",
            previewUrl = "https://example.com/preview.m4a",
            durationMs = 200_000L,
            genre = "Rock",
            )

    private fun params(
            track: Track = this.track,
            message: String = "Hits differently depending on how old you are.",
            mood: MoodTag? = MoodTag.Sad,
            context: ContextTag? = null,
            genres: List<String> = emptyList(),
    attachLocation: Boolean = false,
            ) = submitSongParams(track, message, mood, context, genres, attachLocation)

    /** Every argument of the SQL function submit_song(). A name outside this set fails with PGRST202. */
    private val submitSongArguments = setOf(
            "p_provider", "p_provider_track_id", "p_title", "p_artist", "p_message", "p_mood",
            "p_album", "p_artwork_url", "p_preview_url", "p_duration_ms", "p_track_genres",
            "p_context", "p_genres", "p_attach_location",
            )
    private val submitSongRequired = setOf(
            "p_provider", "p_provider_track_id", "p_title", "p_artist", "p_message", "p_mood",
            )

    @Test
    fun `sends the mood that was picked`() {
        assertEquals("sad", params(mood = MoodTag.Sad).getValue("p_mood").jsonPrimitive.content)
        assertEquals("lonely", params(mood = MoodTag.Lonely).getValue("p_mood").jsonPrimitive.content)
    }

    @Test
    fun `sends null for the mood only when none was picked`() {
        assertTrue(params(mood = null).getValue("p_mood") is JsonNull)
    }

    @Test
    fun `sends the message as written`() {
        assertEquals("hello there", params(message = "hello there").getValue("p_message").jsonPrimitive.content)
    }

    @Test
    fun `sends the song details from the track`() {
        val p = params()
        assertEquals("itunes", p.getValue("p_provider").jsonPrimitive.content)
        assertEquals("1440857781", p.getValue("p_provider_track_id").jsonPrimitive.content)
        assertEquals("Landslide", p.getValue("p_title").jsonPrimitive.content)
        assertEquals("Fleetwood Mac", p.getValue("p_artist").jsonPrimitive.content)
    }

    @Test
    fun `sends the artwork, preview, album and duration instead of dropping them`() {
        val p = params()
        assertEquals("https://example.com/art.jpg", p.getValue("p_artwork_url").jsonPrimitive.content)
        assertEquals("https://example.com/preview.m4a", p.getValue("p_preview_url").jsonPrimitive.content)
        assertEquals("Fleetwood Mac", p.getValue("p_album").jsonPrimitive.content)
        assertEquals(200_000L, p.getValue("p_duration_ms").jsonPrimitive.long)
    }

    @Test
    fun `sends null for what the track does not have`() {
        val bare = Track(trackId = 1L, trackName = "T", artistName = "A")
        val p = params(track = bare)
        assertTrue(p.getValue("p_album") is JsonNull)
        assertTrue(p.getValue("p_artwork_url") is JsonNull)
        assertTrue(p.getValue("p_preview_url") is JsonNull)
        assertTrue(p.getValue("p_duration_ms") is JsonNull)
        assertEquals(0, p.getValue("p_track_genres").jsonArray.size)
    }

    @Test
    fun `sends the providers own genre untouched and the chosen genres as given`() {
        val p = params(genres = listOf("k_pop", "indie"))
        // iTunes' label goes to the track as raw text...
        assertEquals(listOf("Rock"), p.getValue("p_track_genres").jsonArray.map { it.jsonPrimitive.content })
        // ...the user's chips go to the submission. The database only accepts slugs here.
        assertEquals(listOf("k_pop", "indie"), p.getValue("p_genres").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `sends no genres when none are chosen`() {
        assertEquals(0, params(genres = emptyList()).getValue("p_genres").jsonArray.size)
    }

    @Test
    fun `sends the context only when there is one`() {
        assertTrue(params(context = null).getValue("p_context") is JsonNull)
        assertEquals("commuting", params(context = ContextTag.Commuting).getValue("p_context").jsonPrimitive.content)
        assertEquals("working_out", params(context = ContextTag.WorkingOut).getValue("p_context").jsonPrimitive.content)
    }

    @Test
    fun `sends a yes or no for the location and never coordinates`() {
        assertTrue(params(attachLocation = true).getValue("p_attach_location").jsonPrimitive.boolean)
        assertFalse(params(attachLocation = false).getValue("p_attach_location").jsonPrimitive.boolean)
        // the server copies the sender's saved city; a client-supplied position would be ignored or rejected
        assertFalse(params().containsKey("p_lat"))
        assertFalse(params().containsKey("p_lng"))
    }

    @Test
    fun `uses only argument names the SQL function has, and all the required ones`() {
        val names = params().keys
        assertTrue("unknown arguments: ${names - submitSongArguments}", names.all { it in submitSongArguments })
        assertTrue("missing arguments: ${submitSongRequired - names}", names.containsAll(submitSongRequired))
    }

    // ------------------------------------------------------------ request_recommendations

    @Test
    fun `pulling records sends the mood, a limit and no context by default`() {
        val p = requestRecommendationsParams(MoodTag.Sad, context = null, limit = 3)
        assertEquals("sad", p.getValue("p_mood").jsonPrimitive.content)
        assertEquals(3, p.getValue("p_limit").jsonPrimitive.int)
        assertTrue(p.getValue("p_context") is JsonNull)
        assertEquals(setOf("p_mood", "p_context", "p_limit"), p.keys)
    }

    @Test
    fun `pulling records sends the context when there is one`() {
        val p = requestRecommendationsParams(MoodTag.Calm, ContextTag.Studying, 3)
        assertEquals("studying", p.getValue("p_context").jsonPrimitive.content)
    }
}