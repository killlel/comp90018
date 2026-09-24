package com.example.vinyl.ui.write

import androidx.compose.ui.graphics.Color
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track
import com.example.vinyl.data.onboarding.GenreOption

enum class EnvelopeMotif { NONE, MOON, HEARTS }

enum class EnvelopeStyle(
    val label: String,
    val colors: List<Color>,
    val motif: EnvelopeMotif = EnvelopeMotif.NONE,
    val showFold: Boolean = true,
) {
    Rainbow(
        "Rainbow",
        listOf(
            Color(0xFFFF6B6B), Color(0xFFFFA94D), Color(0xFFFFD43B),
            Color(0xFF69DB7C), Color(0xFF4A7FE0), Color(0xFFB197FC),
        )
    ),
    Sunset("Sunset", listOf(Color(0xFFFF6B6B), Color(0xFFFFA94D), Color(0xFFFFD43B))),
    Ocean("Ocean", listOf(Color(0xFF4A7FE0), Color(0xFF77EDE5), Color(0xFF4ECDC4))),
    Berry("Berry", listOf(Color(0xFFE64980), Color(0xFFCC5DE8), Color(0xFF845EF7))),

    // Soft pastel, playful
    Cute("Cute", listOf(Color(0xFFFFD6E8), Color(0xFFFFE8F0), Color(0xFFFFF3D6))),

    // Deep night sky with a crescent moon + stars
    Midnight(
        "Midnight",
        listOf(Color(0xFF0B1E3D), Color(0xFF1B2A4A), Color(0xFF3A2E5C)),
        motif = EnvelopeMotif.MOON,
    ),

    // Flat, single colour — no fold crease, just clean and plain
    Simple("Simple", listOf(Color(0xFF6E3A2C), Color(0xFF6E3A2C)), showFold = false),

    // Pink/red with scattered hearts
    Sweetheart(
        "Sweetheart",
        listOf(Color(0xFFE64980), Color(0xFFFF8FA3), Color(0xFFFFC2D1)),
        motif = EnvelopeMotif.HEARTS,
    ),
}

data class WriteCardUiState(
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val selectedTrack: Track? = null,
    val message: String = "",
    val mood: MoodTag? = null,
    /** Loaded from the `genres` table. Empty until it arrives (or if offline) - genre is optional. */
    val genreOptions: List<GenreOption> = emptyList(),
    /** Slugs (`k_pop`), never labels: the database rejects labels. */
    val selectedGenres: Set<String> = emptySet(),
    val attachLocation: Boolean = true,
    val envelopeStyle: EnvelopeStyle = EnvelopeStyle.Rainbow,
    val isPreviewMode: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null,
    val submittedId: String? = null,
) {
    val messageCharsRemaining: Int get() = 280 - message.length
    /** What to show for [selectedGenres]: labels from [genreOptions], falling back to the slug. */
    val selectedGenreLabels: List<String>
        get() = selectedGenres.map { slug -> genreOptions.firstOrNull { it.slug == slug }?.label ?: slug }

    // The database requires all three: submit_song() rejects a missing message (22023) and a
    // missing mood (23502), and matching depends on the mood. Genre stays optional.
    val canSubmit: Boolean get() = selectedTrack != null && mood != null && message.isNotBlank()

    /** Null when the letter can be sent; otherwise says what is still missing. */
    val sendHint: String?
        get() {
            val missing = buildList {
                if (selectedTrack == null) add("a song")
                if (mood == null) add("a mood")
                if (message.isBlank()) add("a message")
            }
            if (missing.isEmpty()) return null
            val list = if (missing.size == 1) missing.first()
            else missing.dropLast(1).joinToString(", ") + " and " + missing.last()
            return "Still needed to send: $list"
        }
}