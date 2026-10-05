package com.example.vinyl.matchmaking

import com.example.vinyl.data.MoodTag
import com.example.vinyl.repository.CandidateCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * What the SQL version could not be checked for without a live database and a fixture set.
 *
 * Each test isolates one term by holding the others equal, so a failure names the term that
 * broke rather than just "the ranking changed".
 */
class MatchmakerTest {

    private val now = Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()

    private fun card(
        id: String,
        artist: String = "Artist $id",
        mood: String? = "happy",
        genres: List<String> = emptyList(),
        timesRecommended: Int = 0,
        ageDays: Long = 0,
    ) = CandidateCard(
        submissionId = id,
        mood = mood,
        genres = genres,
        trackTitle = "Track $id",
        trackArtist = artist,
        timesRecommended = timesRecommended,
        submittedAt = OffsetDateTime
            .ofInstant(Instant.ofEpochMilli(now - ageDays * 86_400_000L), ZoneOffset.UTC)
            .toString(),
    )

    private val matchmaker = Matchmaker(random = Random(1))

    @Test
    fun `mood match outranks a mood mismatch`() {
        val ranked = matchmaker.rank(
            candidates = listOf(card("miss", mood = "sad"), card("hit", mood = "happy")),
            mood = MoodTag.Happy,
            nowEpochMs = now,
        )
        assertEquals("hit", ranked.first().card.submissionId)
    }

    @Test
    fun `a rare genre beats a common one`() {
        // Same mood, same age, same circulation — only the rarity of the matched genre differs.
        val ranked = matchmaker.rank(
            candidates = listOf(card("common", genres = listOf("pop")), card("rare", genres = listOf("shoegaze"))),
            mood = MoodTag.Happy,
            genreWeights = mapOf("pop" to 0.4, "shoegaze" to 3.2),
            preferred = setOf("pop", "shoegaze"),
            nowEpochMs = now,
        )
        assertEquals("rare", ranked.first().card.submissionId)
    }

    @Test
    fun `picking many genres does not simply outscore picking one`() {
        // Normalisation guards against "select everything to win". The same card, scored against
        // a one-genre preference and an eight-genre one, must not gain from the larger set.
        val subject = card("x", genres = listOf("jazz"))
        val weights = mapOf("jazz" to 2.0)

        val narrow = matchmaker.rank(listOf(subject), MoodTag.Happy, genreWeights = weights, preferred = setOf("jazz"), nowEpochMs = now)
        val wide = matchmaker.rank(
            listOf(subject), MoodTag.Happy, genreWeights = weights,
            preferred = setOf("jazz", "pop", "rock", "folk", "soul", "edm", "metal", "disco"),
            nowEpochMs = now,
        )
        assertTrue(
            "a wider selection should not inflate the score",
            wide.first().score <= narrow.first().score,
        )
    }

    @Test
    fun `freshness decays smoothly rather than falling off a cliff`() {
        val fresh = matchmaker.rank(listOf(card("fresh", ageDays = 1)), MoodTag.Happy, nowEpochMs = now)
        val older = matchmaker.rank(listOf(card("older", ageDays = 29)), MoodTag.Happy, nowEpochMs = now)
        val oldest = matchmaker.rank(listOf(card("oldest", ageDays = 31)), MoodTag.Happy, nowEpochMs = now)

        assertTrue("newer should score higher", fresh.first().score > older.first().score)
        // The old rule dropped 0.5 between day 29 and day 31. This one barely moves.
        assertTrue(
            "two days either side of the old cutoff should be near-identical",
            older.first().score - oldest.first().score < 0.05,
        )
        assertTrue("an old record stays reachable", oldest.first().score > 0)
    }

    @Test
    fun `a heavily circulated record is pushed down`() {
        val ranked = matchmaker.rank(
            candidates = listOf(card("seen", timesRecommended = 50), card("unseen", timesRecommended = 0)),
            mood = MoodTag.Happy,
            nowEpochMs = now,
        )
        assertEquals("unseen", ranked.first().card.submissionId)
    }

    @Test
    fun `the same artist is never returned twice`() {
        val picked = matchmaker.pick(
            candidates = listOf(
                card("a", artist = "Radiohead"),
                card("b", artist = "Radiohead"),
                card("c", artist = "Bon Iver"),
                card("d", artist = "Etta James"),
            ),
            mood = MoodTag.Happy,
            limit = 3,
            nowEpochMs = now,
        )
        val artists = picked.map { it.card.trackArtist }
        assertEquals("duplicate artist in the room", artists.size, artists.distinct().size)
    }

    @Test
    fun `artist matching ignores case and padding`() {
        val picked = matchmaker.pick(
            candidates = listOf(card("a", artist = "Radiohead"), card("b", artist = "  radiohead ")),
            mood = MoodTag.Happy,
            limit = 3,
            nowEpochMs = now,
        )
        assertEquals(1, picked.size)
    }

    @Test
    fun `the last slot is exploration, not the next best card`() {
        // Six distinct artists, all equal but for circulation, so the ranking is unambiguous.
        val candidates = (1..6).map { card("c$it", artist = "Artist $it", timesRecommended = it) }

        val picked = matchmaker.pick(candidates, MoodTag.Happy, limit = 3, nowEpochMs = now)

        assertEquals(3, picked.size)
        // The first two are the top of the ranking.
        assertEquals(listOf("c1", "c2"), picked.take(2).map { it.card.submissionId })
        // The third is drawn from the rest, so it is not simply c3.
        assertTrue(
            "the explore slot should come from outside the exploited head",
            picked[2].card.submissionId !in setOf("c1", "c2"),
        )
    }

    @Test
    fun `a null mood still ranks on the other terms`() {
        // "Let the crate decide" — no mood to match, so everything must not collapse to a tie.
        val picked = matchmaker.pick(
            candidates = listOf(
                card("stale", mood = "sad", timesRecommended = 40),
                card("quiet", mood = "angry", timesRecommended = 0),
            ),
            mood = null,
            limit = 1,
            nowEpochMs = now,
        )
        assertEquals("quiet", picked.first().card.submissionId)
    }

    @Test
    fun `no genre preference means no genre filter`() {
        // Empty preferred is "I listen to everything", not "match nothing".
        val ranked = matchmaker.rank(
            candidates = listOf(card("a", genres = listOf("pop")), card("b", genres = emptyList())),
            mood = MoodTag.Happy,
            genreWeights = mapOf("pop" to 3.0),
            preferred = emptySet(),
            nowEpochMs = now,
        )
        assertEquals("both should score identically", ranked[0].score, ranked[1].score, 1e-9)
    }

    @Test
    fun `an unreadable timestamp is treated as new, not ancient`() {
        val broken = card("broken").copy(submittedAt = "not a date")
        val ranked = matchmaker.rank(listOf(broken), MoodTag.Happy, nowEpochMs = now)
        // Would be near zero if a parse failure fell through to "infinitely old".
        assertTrue(ranked.first().score > 3.0)
    }

    @Test
    fun `an empty pool returns nothing rather than throwing`() {
        assertTrue(matchmaker.pick(emptyList(), MoodTag.Happy, limit = 3, nowEpochMs = now).isEmpty())
    }

    @Test
    fun `fewer candidates than slots returns what there is`() {
        val picked = matchmaker.pick(listOf(card("only")), MoodTag.Happy, limit = 3, nowEpochMs = now)
        assertEquals(1, picked.size)
    }
}
