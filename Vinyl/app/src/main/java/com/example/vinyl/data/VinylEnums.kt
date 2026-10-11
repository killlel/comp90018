package com.example.vinyl.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MoodTag {
    @SerialName("happy") Happy,
    @SerialName("sad") Sad,
    @SerialName("calm") Calm,
    @SerialName("energetic") Energetic,
    @SerialName("nostalgic") Nostalgic,
    @SerialName("anxious") Anxious,
    @SerialName("romantic") Romantic,
    @SerialName("angry") Angry,
    @SerialName("hopeful") Hopeful,
    @SerialName("lonely") Lonely;

    val wireValue: String get() = name.lowercase()
}
