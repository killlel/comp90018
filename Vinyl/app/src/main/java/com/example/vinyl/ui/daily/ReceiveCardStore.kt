package com.example.vinyl.ui.daily

import android.content.Context
import com.example.vinyl.BuildConfig
import com.example.vinyl.data.Supabase
import com.example.vinyl.data.pullDay
import com.example.vinyl.repository.RoomCard
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Device-only memory of the daily hand, isolated for each signed-in account: the cards today's
 * pull dealt, and which of them have been opened. "Today" is the pull day, which turns over at
 * 06:00 rather than midnight. The server keeps the real limit (get_pull_status); this only
 * remembers what happened on this phone.
 *
 * An opened card goes on the Home shelf; the rest wait in the picker until they are opened too.
 */
internal class ReceiveCardStore(context: Context) {
    private val accountId = Supabase.client.auth.currentUserOrNull()?.id ?: "guest"
    private val prefs = context.getSharedPreferences("receive_cards_$accountId", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun wasOpened(id: String): Boolean = id in openedSet()

    /** Remembers [id] as opened, after every card opened before it. Opening it again is a no-op. */
    fun markOpened(id: String) {
        val order = openedOrder()
        if (id in order) return
        prefs.edit()
            .putString("opened_order", (order + id).joinToString(ORDER_SEPARATOR))
            .putStringSet("opened", openedSet() + id)
            .apply()
    }

    /**
     * Every card opened on this phone, by [openKey], in the order they were opened. Cards opened
     * before the order was kept have none on record, so they come first.
     */
    fun openedKeys(): List<String> {
        val order = openedOrder()
        return (openedSet() - order.toSet()).toList() + order
    }

    private fun openedSet(): Set<String> = prefs.getStringSet("opened", emptySet()).orEmpty()

    private fun openedOrder(): List<String> =
        prefs.getString("opened_order", null)?.split(ORDER_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    fun todayCards(): List<RoomCard> =
        if (prefs.getString("day", null) == pullDay().toString()) storedCards() else emptyList()

    /** Remembers [cards] as today's hand, unless one is already remembered. Returns the hand. */
    fun saveToday(cards: List<RoomCard>): List<RoomCard> {
        val existing = todayCards()
        if (existing.isNotEmpty() || cards.isEmpty()) return existing
        val hand = cards.take(3)
        prefs.edit()
            .putString("day", pullDay().toString())
            .putString("cards", json.encodeToString(hand))
            .apply()
        return hand
    }

    /** get_room has no delivery date; never adopt the same known hand again after 06:00. */
    fun resolveExisting(cards: List<RoomCard>): List<RoomCard> {
        val today = todayCards()
        if (today.isNotEmpty()) return today
        return saveToday(unseenDeliveries(cards, storedCards()))
    }

    /** Drops the remembered hand, for when the server says nothing was dealt today. */
    fun forgetToday() {
        prefs.edit().remove("day").remove("cards").apply()
    }

    /** Only an external emulator test script sets this; release builds never read it. */
    fun manuallyPreparedTestHand(): List<RoomCard>? {
        if (!BuildConfig.DEBUG || prefs.getString("manual_test_day", null) != pullDay().toString()) return null
        return runCatching {
            json.decodeFromString<List<RoomCard>>(prefs.getString("manual_test_cards", null) ?: "[]")
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun setKept(id: String, kept: Boolean) {
        val cards = todayCards()
        if (cards.none { it.submissionId == id }) return
        prefs.edit().putString("cards", json.encodeToString(
            cards.map { if (it.submissionId == id) it.copy(saved = kept) else it },
        )).apply()
    }

    private fun storedCards(): List<RoomCard> = runCatching {
        json.decodeFromString<List<RoomCard>>(prefs.getString("cards", null) ?: "[]")
    }.getOrDefault(emptyList())

    private companion object {
        /** Keys are record ids (UUIDs), which never contain a comma. */
        const val ORDER_SEPARATOR = ","
    }
}

/** How a card is remembered as opened: by its record, the same key the receive flow uses. */
internal val RoomCard.openKey: String get() = submissionId

internal fun unseenDeliveries(cards: List<RoomCard>, previous: List<RoomCard>): List<RoomCard> {
    val previousIds = previous.map { it.recommendationId }.toSet()
    return cards.filter { it.recommendationId !in previousIds }
}
