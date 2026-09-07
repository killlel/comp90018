package com.example.vinyl.ui.write

import androidx.compose.ui.graphics.Color
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Track

enum class EnvelopeStyle(val label: String, val colors: List<Color>) {
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
}

data class WriteCardUiState(
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val selectedTrack: Track? = null,
    val message: String = "",
    val mood: MoodTag? = null,
    val selectedGenres: Set<String> = emptySet(),
    val attachLocation: Boolean = false,
    val envelopeStyle: EnvelopeStyle = EnvelopeStyle.Rainbow,
    val isPreviewMode: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null,
    val submittedId: String? = null,
) {
    val messageCharsRemaining: Int get() = 280 - message.length
    val canSubmit: Boolean get() = selectedTrack != null && mood != null && message.isNotBlank()
}