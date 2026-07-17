package com.wakemethere.app.domain.model

/** Public-transport system whose stations can be picked as destinations. */
enum class TransitSystem {
    METRO,
    BRT,
}

/** One station of a transit line. */
data class TransitStation(
    /**
     * Stable id: "<system>:<key>" for v2 data, "<system>:<lineId>:<index>"
     * for legacy v1 data. In v2 a transfer station keeps the SAME id in
     * every line it belongs to — that is how lines are linked when building
     * the routing graph.
     */
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** English name, when the dataset provides one. */
    val nameEn: String? = null,
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
    /**
     * Real track adjacency as index pairs into [stations]. Always populated:
     * v2 data ships explicit edges (branches included); for v1 data the
     * parser derives consecutive-station edges.
     */
    val edges: List<Pair<Int, Int>> = emptyList(),
    /** English line name, when the dataset provides one. */
    val nameEn: String? = null,
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
