package com.example.vinyl.ui.home

import com.example.vinyl.repository.PullStatus
import com.example.vinyl.repository.RoomCard
import com.example.vinyl.ui.daily.sealedTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {

    private fun card(id: String) = RoomCard(recommendationId = "rec-$id", submissionId = id)
    private val hand = listOf(card("a"), card("b"), card("c"))
    private val pulled = PullStatus(dealtToday = 3, remaining = 0)

    @Test
    fun `before the pull the shelf is empty and the button is open`() {
        val state = HomeUiState(isLoading = false, pullStatus = PullStatus(dealtToday = 0, remaining = 3))
        assertTrue(state.onShelf.isEmpty())
        assertFalse(state.doneForToday)
    }

    @Test
    fun `a pulled hand with nothing opened stays off the shelf and the button stays open`() {
        val state = HomeUiState(todayHand = hand, pullStatus = pulled)
        assertTrue(state.onShelf.isEmpty())
        assertEquals(hand, state.unopened)
        assertFalse(state.doneForToday)
    }

    @Test
    fun `each opened card moves to the shelf and the rest wait in the picker`() {
        val state = HomeUiState(todayHand = hand, openedKeys = listOf("b"), pullStatus = pulled)
        assertEquals(listOf(card("b")), state.onShelf)
        assertEquals(listOf(card("a"), card("c")), state.unopened)
        assertFalse(state.doneForToday)
    }

    @Test
    fun `once every card is opened all three are on the shelf and the day is done`() {
        val state = HomeUiState(todayHand = hand, openedKeys = listOf("older", "a", "b", "c"), pullStatus = pulled)
        assertEquals(hand, state.onShelf)
        assertTrue(state.unopened.isEmpty())
        assertTrue(state.doneForToday)
    }

    @Test
    fun `the shelf is in the order the cards were opened, not the order they were dealt`() {
        val state = HomeUiState(todayHand = hand, openedKeys = listOf("c", "a"), pullStatus = pulled)
        assertEquals(listOf(card("c"), card("a")), state.onShelf)
        assertEquals(listOf(card("b")), state.unopened)
    }

    @Test
    fun `pulled on another phone with no hand here is done for today`() {
        assertTrue(HomeUiState(todayHand = emptyList(), pullStatus = pulled).doneForToday)
    }

    @Test
    fun `the envelope names only the mood, with the right article`() {
        assertEquals("A happy record", sealedTitle("Happy"))
        assertEquals("An energetic record", sealedTitle("Energetic"))
        assertEquals("A sealed record", sealedTitle(""))
    }
}
