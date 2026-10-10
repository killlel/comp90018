package com.example.vinyl.ui.daily

import android.content.Context
import com.example.vinyl.data.Supabase
import com.example.vinyl.repository.RoomCard
import io.github.jan.supabase.auth.auth
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Device-only opening history and the daily hand, isolated for each signed-in account. */
internal class ReceiveCardStore(context: Context) {
    private val accountId = Supabase.client.auth.currentUserOrNull()?.id ?: "guest"
    private val prefs = context.getSharedPreferences("receive_cards_$accountId", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun wasOpened(id: String): Boolean = id in prefs.getStringSet("opened", emptySet()).orEmpty()

    fun markOpened(id: String) {
        val opened = prefs.getStringSet("opened", emptySet()).orEmpty() + id
        prefs.edit().putStringSet("opened", opened).apply()
    }

    fun todayCards(): List<RoomCard> =
        if (prefs.getString("day", null) == LocalDate.now().toString()) storedCards() else emptyList()

    fun saveToday(cards: List<RoomCard>): List<RoomCard> {
        val existing = todayCards()
        if (existing.isNotEmpty() || cards.isEmpty()) return existing
        val hand = cards.take(3)
        prefs.edit()
            .putString("day", LocalDate.now().toString())
            .putString("cards", json.encodeToString(hand))
            .apply()
        return hand
    }

    /** get_room has no delivery date; never adopt the same known hand again after midnight. */
    fun resolveExisting(cards: List<RoomCard>): List<RoomCard> {
        val today = todayCards()
        if (today.isNotEmpty()) return today
        val fresh = unseenDeliveries(cards, storedCards())
        return saveToday(fresh)
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
}

internal fun unseenDeliveries(cards: List<RoomCard>, previous: List<RoomCard>): List<RoomCard> {
    val previousIds = previous.map { it.recommendationId }.toSet()
    return cards.filter { it.recommendationId !in previousIds }
}
