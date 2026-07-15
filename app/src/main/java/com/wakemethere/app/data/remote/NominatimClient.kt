package com.wakemethere.app.data.remote

import com.wakemethere.app.WakeMeThereApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** One geocoding search result. */
data class PlaceResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Minimal client for the free Nominatim (OpenStreetMap) geocoding API.
 *
 * Per the Nominatim usage policy: a real User-Agent identifying the app is
 * sent with every request, and callers must debounce (the map view model
 * waits for the user to stop typing before searching).
 */
@Singleton
class NominatimClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    /**
     * Searches places matching [query]. Returns up to [limit] results.
     * @throws IOException on network or HTTP failure.
     */
    suspend fun search(query: String, limit: Int = 5): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("addressdetails", "0")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", WakeMeThereApp.OSM_USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Nominatim returned HTTP ${response.code}")
                }
                parseResults(response.body?.string().orEmpty())
            }
        }

    private fun parseResults(body: String): List<PlaceResult> {
        val array = JSONArray(body)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    PlaceResult(
                        displayName = item.optString("display_name"),
                        latitude = item.getString("lat").toDouble(),
                        longitude = item.getString("lon").toDouble(),
                    )
                )
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/search"
    }
}
