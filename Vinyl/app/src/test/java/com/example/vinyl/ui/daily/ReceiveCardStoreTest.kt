package com.example.vinyl.ui.daily

import com.example.vinyl.repository.RoomCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiveCardStoreTest {
    private fun card(delivery: String, record: String = delivery) =
        RoomCard(recommendationId = delivery, submissionId = record)

    @Test
    fun `first install adopts the existing hand`() {
        val hand = listOf(card("a"), card("b"), card("c"))
        assertEquals(hand, unseenDeliveries(hand, emptyList()))
    }

    @Test
    fun `yesterdays deliveries do not become todays hand`() {
        val previous = listOf(card("a"), card("b"), card("c"))
        assertTrue(unseenDeliveries(previous.reversed(), previous).isEmpty())
    }

    @Test
    fun `a new delivery of a known record is still a new delivery`() {
        val previous = listOf(card("old", "same-record"))
        val fresh = card("new", "same-record")
        assertEquals(listOf(fresh), unseenDeliveries(previous + fresh, previous))
    }
}
