package com.example.vinyl.matchmaking

import com.example.vinyl.data.ContextTag
import com.example.vinyl.data.MoodTag
import com.example.vinyl.repository.CandidateCard
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Ranks candidate letters. Plain Kotlin — no Android, no network, no clock of its own — so it can
 * be driven from a unit test with fabricated cards.
 *
 * The server decides what you are *allowed* to see; this decides what you see *first*. Everything
 * here is arithmetic over rows the server has already agreed to send, so nothing it computes could
 * leak anything: a candidate carries no sender id by construction.
 *
 * The terms, and where each comes from:
 *
 *   mood          the question the user actually answered, so it dominates
 *   genre         inverse document frequency, as in BM25 — a match on a rare genre says far
 *                 more than a match on one half the pool carries. Normalised to at most 1 per
 *                 genre, so the whole term is capped at the genre weight and can never
 *                 outweigh a mood match
 *   freshness     the Hacker News shape, `w / (age + 2) ^ gravity`; decays smoothly instead of
 *                 falling off a cliff at some arbitrary cutoff
 *   circulation   a penalty on records already shown to many people, so the first submissions
 *                 don't win every round and newer ones are never seen
 *
 * Then two selection rules on top of the score:
 *
 *   diversity     one record per artist — the cheap half of MMR. At this pool size three cards
 *                 by one artist is a likely outcome and looks broken even when correctly ranked
 *   exploration   epsilon-greedy with epsilon fixed at one slot, so the user is not only ever
 *                 shown their own mood reflected back
 *
 * Deliberately not here: anything needing a view across all users. Genre rarity and circulation
 * counts come from the server precisely because a client cannot see enough to compute them
 * honestly — it only ever holds the candidates it was handed.
 */
class Matchmaker(
    private val weights: Weights = Weights(),
    /** Injected so a test can pin the random slot and the tie-breaks. */
    private val random: Random = Random.Default,
) {

    data class Weights(
        val mood: Double = 3.0,
        val genre: Double = 2.0,
        val context: Double = 1.0,
        val freshness: Double = 1.5,
        val circulation: Double = 0.8,
        /** Hacker News uses 1.8 on hours. Days here, so a gentler slope. */
        val gravity: Double = 1.2,
    )

    data class Ranked(val card: CandidateCard, val score: Double)

    /**
     * @param candidates   what `get_candidates()` returned — already filtered and anonymised
     * @param genreWeights slug to normalised idf in (0, 1], from `get_genre_weights()`
     * @param preferred    today's genre chips, or the profile's standing taste; empty means no
     *                     genre filter, which is what "I listen to everything" should do
     * @param nowEpochMs   injected rather than read, so freshness is testable
     */
    fun rank(
        candidates: List<CandidateCard>,
        mood: MoodTag?,
        context: ContextTag? = null,
        genreWeights: Map<String, Double> = emptyMap(),
        preferred: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<Ranked> = candidates
        .map { Ranked(it, score(it, mood, context, genreWeights, preferred, nowEpochMs)) }
        .sortedByDescending { it.score }

    /**
     * Rank, then pick. The last slot is a deliberate random pick from whatever the ranking did not
     * already claim — that is the epsilon in epsilon-greedy, and it is what stops a new submission
     * having to out-score established ones before anyone ever sees it.
     */
    fun pick(
        candidates: List<CandidateCard>,
        mood: MoodTag?,
        limit: Int = DEFAULT_LIMIT,
        context: ContextTag? = null,
        genreWeights: Map<String, Double> = emptyMap(),
        preferred: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<Ranked> {
        if (limit <= 0 || candidates.isEmpty()) return emptyList()

        val ranked = rank(candidates, mood, context, genreWeights, preferred, nowEpochMs)

        // One per artist. distinctBy keeps the first occurrence, and the list is already in score
        // order, so each artist is represented by its best card.
        val diverse = ranked.distinctBy { it.card.trackArtist.lowercase().trim() }

        if (limit == 1) return diverse.take(1)

        val exploit = diverse.take(limit - 1)
        val exploitIds = exploit.mapTo(mutableSetOf()) { it.card.submissionId }
        val explore = diverse
            .filter { it.card.submissionId !in exploitIds }
            .let { pool -> if (pool.isEmpty()) null else pool[random.nextInt(pool.size)] }

        return (exploit + listOfNotNull(explore))
    }

    private fun score(
        card: CandidateCard,
        mood: MoodTag?,
        context: ContextTag?,
        genreWeights: Map<String, Double>,
        preferred: Set<String>,
        nowEpochMs: Long,
    ): Double {
        var total = 0.0

        // A null mood is "let the crate decide" — no mood to match on, so the other terms carry
        // the whole ranking rather than every card scoring zero.
        if (mood != null && card.mood == mood.wireValue) total += weights.mood
        if (context != null && card.context == context.wireValue) total += weights.context

        // Divided by how many genres the user picked, so choosing eight does not simply outscore
        // choosing one. Jaccard-ish, without the cost of a real union.
        //
        // Each weight is clamped to 1, which caps the term at weights.genre (2.0) — below a mood
        // match (3.0). The server already sends values in (0, 1]; the clamp keeps that true
        // against one that predates the normalisation and still sends raw idf.
        if (preferred.isNotEmpty()) {
            val overlap = card.genres
                .filter { it in preferred }
                .sumOf { (genreWeights[it] ?: 0.0).coerceIn(0.0, 1.0) }
            total += weights.genre * overlap / preferred.size
        }

        total += weights.freshness / (ageInDays(card, nowEpochMs) + 2.0).pow(weights.gravity)
        total -= weights.circulation * ln(1.0 + card.timesRecommended)

        return total
    }

    /**
     * An unparseable or missing timestamp is treated as brand new rather than infinitely old. A
     * card the server sent is real; penalising it for a format we failed to read would hide it
     * forever for a reason that has nothing to do with the user.
     */
    private fun ageInDays(card: CandidateCard, nowEpochMs: Long): Double {
        val sentMs = card.submittedAtEpochMs ?: return 0.0
        return ((nowEpochMs - sentMs).coerceAtLeast(0L)).toDouble() / MILLIS_PER_DAY
    }

    private companion object {
        const val DEFAULT_LIMIT = 3
        const val MILLIS_PER_DAY = 86_400_000.0
    }
}
