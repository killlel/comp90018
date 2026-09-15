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

@Serializable
enum class ContextTag {
    @SerialName("commuting") Commuting,
    @SerialName("studying") Studying,
    @SerialName("working_out") WorkingOut,
    @SerialName("relaxing") Relaxing,
    @SerialName("sleeping") Sleeping,
    @SerialName("partying") Partying,
    @SerialName("heartbroken") Heartbroken,
    @SerialName("celebrating") Celebrating,
    @SerialName("late_night") LateNight;

    val wireValue: String get() = name.let {
        it.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }
}