package com.wakemethere.app.data.transit

import android.content.Context
import com.wakemethere.app.WakeMeThereApp
import com.wakemethere.app.domain.model.TransitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads exact Tehran Metro / BRT station data from OpenStreetMap
 * (Overpass API) on the user's device and caches it in app files. The
 * bundled asset coordinates are approximate; this replaces them with
 * surveyed data — triggered from Settings.
 *
 * Route relations in OSM carry stations as ordered members, which is
 * exactly what the map polylines need.
 */
@Singleton
class TransitUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val transitRepository: TransitRepository,
) {

    /** Fetches both networks; returns total stations written. */
    suspend fun updateAll(): Int = withContext(Dispatchers.IO) {
        val metro = fetchNetwork(
            filter = """relation["route"="subway"](35.4,50.7,35.95,51.75);""",
            colors = METRO_COLORS,
        )
        val brt = fetchNetwork(
            filter = """relation["route"="bus"]["network"~"BRT|تندرو",i](35.4,50.7,35.95,51.75);""",
            colors = null,
        )
        var written = 0
        if (metro.length() > 0) {
            written += save(TransitSystem.METRO, metro)
        }
        if (brt.length() > 0) {
            written += save(TransitSystem.BRT, brt)
        }
        if (written == 0) throw IOException("Overpass returned no usable lines")
        transitRepository.invalidate()
        written
    }

    private fun fetchNetwork(filter: String, colors: Map<String, String>?): JSONArray {
        val query = "[out:json][timeout:60];($filter);out body;>;out skel qt;"
        val request = Request.Builder()
            .url(OVERPASS_URL)
            .header("User-Agent", WakeMeThereApp.OSM_USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()

        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Overpass HTTP ${response.code}")
            response.body?.string().orEmpty()
        }

        val elements = JSONObject(body).getJSONArray("elements")
        val nodes = HashMap<Long, JSONObject>()
        val relations = ArrayList<JSONObject>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            when (e.getString("type")) {
                "node" -> nodes[e.getLong("id")] = e
                "relation" -> relations.add(e)
            }
        }

        // One line per ref: keep the direction variant with the most stops.
        val best = LinkedHashMap<String, JSONObject>()
        for (rel in relations) {
            val tags = rel.optJSONObject("tags") ?: continue
            val ref = tags.optString("ref").ifBlank { tags.optString("name") }
            if (ref.isBlank()) continue

            val stations = JSONArray()
            val seen = HashSet<String>()
            val members = rel.optJSONArray("members") ?: continue
            for (m in 0 until members.length()) {
                val member = members.getJSONObject(m)
                if (member.getString("type") != "node") continue
                val role = member.optString("role")
                if (role.isNotEmpty() && !role.startsWith("stop")) continue
                val node = nodes[member.getLong("ref")] ?: continue
                val name = node.optJSONObject("tags")?.optString("name").orEmpty()
                if (name.isBlank() || !seen.add(name)) continue
                stations.put(
                    JSONObject()
                        .put("name", name)
                        .put("lat", node.getDouble("lat"))
                        .put("lon", node.getDouble("lon"))
                )
            }
            if (stations.length() < 2) continue

            val color = tags.optString("colour").takeIf { it.startsWith("#") }
                ?: colors?.get(ref)
                ?: FALLBACK_COLORS[best.size % FALLBACK_COLORS.size]
            val line = JSONObject()
                .put("id", ref)
                .put("name", tags.optString("name", ref))
                .put("color", color)
                .put("stations", stations)

            val existing = best[ref]
            if (existing == null ||
                stations.length() > existing.getJSONArray("stations").length()
            ) {
                best[ref] = line
            }
        }
        return JSONArray(best.values.toList())
    }

    private fun save(system: TransitSystem, lines: JSONArray): Int {
        val root = JSONObject()
            .put("note", "Exact data from OpenStreetMap, fetched in-app.")
            .put("lines", lines)
        val dir = File(context.filesDir, "transit").apply { mkdirs() }
        val name = when (system) {
            TransitSystem.METRO -> "tehran_metro.json"
            TransitSystem.BRT -> "tehran_brt.json"
        }
        File(dir, name).writeText(root.toString())
        var stations = 0
        for (i in 0 until lines.length()) {
            stations += lines.getJSONObject(i).getJSONArray("stations").length()
        }
        return stations
    }

    private companion object {
        const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        val METRO_COLORS = mapOf(
            "1" to "#E0001B", "2" to "#2F4389", "3" to "#0098D8", "4" to "#F9A825",
            "5" to "#00A651", "6" to "#E5097F", "7" to "#7C2E8E",
        )
        val FALLBACK_COLORS = listOf(
            "#C62828", "#EF6C00", "#2E7D32", "#4527A0", "#00838F", "#6D4C41",
        )
    }
}
