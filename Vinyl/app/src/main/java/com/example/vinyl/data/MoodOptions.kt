package com.example.vinyl.data

data class MoodOption(val tag: MoodTag, val title: String, val subtitle: String)

object MoodOptions {
    val all = listOf(
        MoodOption(MoodTag.Happy, "Happy", "Things are good, plain and simple"),
        MoodOption(MoodTag.Sad, "Sad", "Carrying something heavy today"),
        MoodOption(MoodTag.Calm, "Calm", "Quiet, unremarkably fine"),
        MoodOption(MoodTag.Energetic, "Energetic", "Can't sit still, buzzing"),
        MoodOption(MoodTag.Nostalgic, "Nostalgic", "Thinking back on something"),
        MoodOption(MoodTag.Anxious, "Anxious", "Mind won't quite settle"),
        MoodOption(MoodTag.Romantic, "Romantic", "Someone is on your mind"),
        MoodOption(MoodTag.Angry, "Angry", "Better out than in"),
        MoodOption(MoodTag.Hopeful, "Hopeful", "Something might be starting"),
        MoodOption(MoodTag.Lonely, "Lonely", "Missing a place or a person"),
    )
}

// Fixed vocabulary for genre
object GenreOptions {
    val all = listOf(
        "Ambient", "Avant-garde", "Classical", "EDM", "Electronic", "Folk",
        "Jazz", "K-pop", "Musical", "Pop", "R&B", "Rap", "Shoegaze", "Soul"
    )
}