package com.wakemethere.app.data.transit

import com.wakemethere.app.domain.model.TransitLine
import com.wakemethere.app.domain.model.TransitNetwork
import com.wakemethere.app.domain.model.TransitStation
import com.wakemethere.app.domain.model.TransitSystem
import org.json.JSONObject

/**
 * Parses the bundled transit-network JSON assets.
 *
 * Expected shape:
 * ```json
 * {
 *   "lines": [
 *     {
 *       "id": "1",
 *       "name": "Line 1",
 *       "color": "#E0001B",
 *       "stations": [ {"name": "Tajrish", "lat": 35.805, "lon": 51.433}, ... ]
 *     }
 *   ]
 * }
 * ```
 */
object TransitParser {

    fun parse(system: TransitSystem, json: String): TransitNetwork {
        val root = JSONObject(json)
        val linesJson = root.getJSONArray("lines")
        val lines = buildList {
            for (i in 0 until linesJson.length()) {
                val lineJson = linesJson.getJSONObject(i)
                val lineId = lineJson.getString("id")
                val stationsJson = lineJson.getJSONArray("stations")
                val stations = buildList {
                    for (j in 0 until stationsJson.length()) {
                        val stationJson = stationsJson.getJSONObject(j)
                        add(
                            TransitStation(
                                id = "${system.name}:$lineId:$j",
                                name = stationJson.getString("name"),
                                latitude = stationJson.getDouble("lat"),
                                longitude = stationJson.getDouble("lon"),
                            )
                        )
                    }
                }
                add(
                    TransitLine(
                        id = lineId,
                        name = lineJson.getString("name"),
                        color = parseColor(lineJson.getString("color")),
                        stations = stations,
                    )
                )
            }
        }
        return TransitNetwork(system = system, lines = lines)
    }

    /** Parses "#RRGGBB" into an opaque ARGB int without android.graphics. */
    private fun parseColor(hex: String): Int {
        val rgb = hex.removePrefix("#").toLong(16).toInt()
        return rgb or 0xFF000000.toInt()
    }
}
