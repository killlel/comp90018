package com.example.vinyl.data

data class MoodOption(val tag: MoodTag, val title: String, val subtitle: String)

object MoodOptions {
    val all = listOf(
        MoodOption(MoodTag.Happy, "Happy", "Simply feeling good"),
        MoodOption(MoodTag.Sad, "Sad", "Heavy today"),
        MoodOption(MoodTag.Calm, "Calm", "Quiet and fine"),
        MoodOption(MoodTag.Energetic, "Energetic", "Buzzing with energy"),
        MoodOption(MoodTag.Nostalgic, "Nostalgic", "Looking back"),
        MoodOption(MoodTag.Anxious, "Anxious", "Mind won't settle"),
        MoodOption(MoodTag.Romantic, "Romantic", "Thinking of someone"),
        MoodOption(MoodTag.Angry, "Angry", "Better out than in"),
        MoodOption(MoodTag.Hopeful, "Hopeful", "A new start ahead"),
        MoodOption(MoodTag.Lonely, "Lonely", "Missing someone"),
    )
}

// Fixed vocabulary for genre
object GenreOptions {
    val all = listOf(
        "Ambient", "Avant-garde", "Classical", "EDM", "Electronic", "Folk",
        "Jazz", "K-pop", "Musical", "Pop", "R&B", "Rap", "Shoegaze", "Soul"
    )
}