package com.example.vinyl.network

import com.example.vinyl.data.ITunesSearchResponse
import com.example.vinyl.data.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Searches the public iTunes Search API for songs. It gives Vinyl its track metadata, artwork and
 * 30 second preview URLs; no account or API key is needed.
 *
 * The client ignores JSON keys the app does not read, and also accepts the `text/javascript`
 * content type, which is how the iTunes API labels its JSON.
 */
class ITunesApiService(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            val json = Json { ignoreUnknownKeys = true }
            json(json)
            json(json, contentType = ContentType.Text.JavaScript)
        }
    }
) {
    companion object {
        // Fixed for every user, regardless of device locale.
        private const val STOREFRONT_COUNTRY = "US"
    }

    /**
     * Returns up to [limit] songs matching [query], or an empty list for a blank query. Results
     * always come from the US storefront. Network failures are thrown to the caller, which decides
     * how to show them.
     */
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