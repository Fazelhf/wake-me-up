package com.wakemethere.app.domain.model

/** Public-transport system whose stations can be picked as destinations. */
enum class TransitSystem {
    METRO,
    BRT,
}

/** One station of a transit line. */
data class TransitStation(
    /** Stable id: "<system>:<lineId>:<index>". */
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * A transit line: ordered stations plus the display color used for the
 * polyline and station markers on the map.
 */
data class TransitLine(
    val id: String,
    val name: String,
    /** ARGB color int for map rendering. */
    val color: Int,
    val stations: List<TransitStation>,
)

/** A whole network (all lines of one [TransitSystem]). */
data class TransitNetwork(
    val system: TransitSystem,
    val lines: List<TransitLine>,
) {
    fun findStation(stationId: String): Pair<TransitLine, TransitStation>? {
        for (line in lines) {
            for (station in line.stations) {
                if (station.id == stationId) return line to station
            }
        }
        return null
    }
}
