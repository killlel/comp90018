package com.example.vinyl.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The location fields of the signed-in user's own profile row.
 *
 * [lat]/[lng] are a city centre, never a device fix — see LocationRepository for the contract
 * that guarantees it. `profiles` is owner-only under RLS, so this is never another user's data.
 *
 * No city name: by design the coordinates are the only thing stored, and a display name is
 * derived from them when a screen needs one (LocationRepository.cityFor).
 */
@Serializable
data class ProfileLocation(val lat: Double? = null, val lng: Double? = null) {
    val hasLocation: Boolean get() = lat != null && lng != null
}

open class ProfileRepository(private val supabase: SupabaseClient = Supabase.client) {
    /**
     * The current user's stored location, or null if there's no profile row yet.
     * A signed-out caller is a failure, not an empty result — nothing should be asking.
     */
    open suspend fun getLocation(): Result<ProfileLocation?> = runCatching {
        val uid = requireUserId()
        supabase.postgrest
            .from(TABLE)
            .select(Columns.list("lat", "lng")) {
                filter { eq("id", uid) }
            }
            .decodeSingleOrNull<ProfileLocation>()
    }

    /**
     * Writes through `update_my_location()` rather than a direct upsert, per DesignDecision.md §8:
     * it's the one agreed exception to plain profile writes. The server rounds to 2dp and stamps
     * `location_updated_at` itself, so neither happens here.
     */
    open suspend fun saveLocation(lat: Double, lng: Double): Result<Unit> = runCatching {
        callUpdateMyLocation(
            buildJsonObject {
                put("p_lat", lat)
                put("p_lng", lng)
            },
        )
    }

    /** Nulls both columns and the timestamp, via the same RPC. */
    open suspend fun clearLocation(): Result<Unit> = runCatching {
        callUpdateMyLocation(
            buildJsonObject {
                put("p_lat", JsonNull)
                put("p_lng", JsonNull)
            },
        )
    }

    private suspend fun callUpdateMyLocation(params: JsonObject) {
        supabase.postgrest.rpc("update_my_location", params)
    }

    private fun requireUserId(): String = supabase.auth.currentUserOrNull()?.id
        ?: error("No signed-in user; profile location is unavailable")

    private companion object {
        const val TABLE = "profiles"
    }
}
