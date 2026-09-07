package com.example.vinyl.network

import com.example.vinyl.data.ITunesSearchResponse
import com.example.vinyl.data.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ITunesApiService(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) {
    companion object {
        // Fixed for every user, regardless of device locale.
        private const val STOREFRONT_COUNTRY = "US"
    }

    suspend fun searchSongs(query: String, limit: Int = 25): List<Track> {
        if (query.isBlank()) return emptyList()

        val response: ITunesSearchResponse = client.get("https://itunes.apple.com/search") {
            parameter("term", query)
            parameter("media", "music")
            parameter("entity", "song")
            parameter("limit", limit)
            parameter("country", STOREFRONT_COUNTRY)
        }.body()

        return response.results
    }
}