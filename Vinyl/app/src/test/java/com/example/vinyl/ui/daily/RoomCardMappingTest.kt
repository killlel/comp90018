package com.example.vinyl.ui.daily

import com.example.vinyl.data.MoodTag
import com.example.vinyl.repository.RoomCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Acceptance criterion 3: missing location data shows a sensible fallback instead of crashing.
 * Exactly one of distanceLabel / distanceNote must be set, and the right note must win.
 */
class RoomCardMappingTest {

    private val melbourne = -37.8136 to 144.9631
    private val sydney = -33.8688 to 151.2093

    private fun card(lat: Double? = null, lng: Double? = null, mood: String? = "calm") = RoomCard(
        recommendationId = "rec-1",
        submissionId = "sub-1",
        message = "hello",
        mood = mood,
        lat = lat,
        lng = lng,
        trackTitle = "Slow Rain, Rooftop",
        trackArtist = "Marin Ochre",
    )

    @Test
    fun `both ends present gives a label and no note`() {
        val option = card(sydney.first, sydney.second).toArrivedOption(melbourne.first, melbourne.second)
        assertEquals("200+ km", option.distanceLabel)
        assertNull(option.distanceNote)
    }

    @Test
    fun `sender without location explains the sender`() {
        val option = card().toArrivedOption(melbourne.first, melbourne.second)
        assertNull(option.distanceLabel)
        assertEquals(NO_SENDER_LOCATION_NOTE, option.distanceNote)
    }

    @Test
    fun `reader without location explains the reader`() {
        val option = card(sydney.first, sydney.second).toArrivedOption(null, null)
        assertNull(option.distanceLabel)
        assertEquals(NO_READER_LOCATION_NOTE, option.distanceNote)
    }

    @Test
    fun `both missing prefers the note the reader can act on`() {
        val option = card().toArrivedOption(null, null)
        assertNull(option.distanceLabel)
        assertEquals(NO_READER_LOCATION_NOTE, option.distanceNote)
    }

    @Test
    fun `half a coordinate counts as missing`() {
        val option = card(sydney.first, null).toArrivedOption(melbourne.first, melbourne.second)
        assertNull(option.distanceLabel)
        assertEquals(NO_SENDER_LOCATION_NOTE, option.distanceNote)
    }

    @Test
    fun `known mood maps to its title and tag`() {
        val option = card().toArrivedOption(null, null)
        assertEquals("Calm", option.moodLabel)
        assertEquals(MoodTag.Calm, option.mood)
    }

    @Test
    fun `unknown mood from the server degrades instead of crashing`() {
        val option = card(mood = "wistful").toArrivedOption(null, null)
        assertEquals("wistful", option.moodLabel)
        assertNull(option.mood)
    }

    @Test
    fun `card fields carry through`() {
        val option = card().toArrivedOption(null, null)
        assertEquals("rec-1", option.id)
        assertEquals("Slow Rain, Rooftop", option.trackName)
        assertEquals("Marin Ochre", option.artistName)
        assertEquals("hello", option.messagePreview)
    }
}
