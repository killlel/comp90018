package com.example.vinyl.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The location fields of the signed-in user's own profile row.
 *
 * [lat]/[lng] are a city centroid, never a device fix — see LocationRepository for the contract
 * that guarantees it. `profiles` is owner-only under RLS, so this is never another user's data.
 *
 * No city name: by design the centroid is the only thing stored, and a display name is derived
 * from it when a screen needs one (LocationRepository.cityFor).
 */
@Serializable
data class ProfileLocation(
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val hasLocation: Boolean get() = lat != null && lng != null
}

/** The write shape. Separate from [ProfileLocation] so a save can't accidentally send nulls. */
@Serializable
private data class ProfileLocationUpdate(
    val id: String,
    val lat: Double,
    val lng: Double,
    @SerialName("location_updated_at") val locationUpdatedAt: String,
)

open class ProfileRepository(
    private val supabase: SupabaseClient = Supabase.client,
) {
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
     * Upsert rather than update: it's not guaranteed a profile row is created at signup, and an
     * update against a missing row would silently affect nothing. PostgREST only touches the
     * columns sent here, so display_name and friends survive untouched.
     */
    open suspend fun saveLocation(lat: Double, lng: Double): Result<Unit> = runCatching {
        val row = ProfileLocationUpdate(
            id = requireUserId(),
            lat = lat,
            lng = lng,
            locationUpdatedAt = nowIso8601(),
        )
        supabase.postgrest.from(TABLE).upsert(row)
    }.map { }

    /**
     * Wipes the stored location. Built with an explicit JSON null rather than a typed row so the
     * columns are definitely cleared rather than omitted from the payload.
     */
    open suspend fun clearLocation(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val nulls = buildJsonObject {
            put("lat", JsonNull)
            put("lng", JsonNull)
            put("location_updated_at", JsonNull)
        }
        supabase.postgrest.from(TABLE).update(nulls) { filter { eq("id", uid) } }
    }.map { }

    private fun requireUserId(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("No signed-in user; profile location is unavailable")

    private companion object {
        const val TABLE = "profiles"

        /**
         * timestamptz as ISO-8601 UTC. SimpleDateFormat rather than java.time because minSdk is
         * 24 and core library desugaring isn't enabled on this module.
         */
        fun nowIso8601(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
    }
}
