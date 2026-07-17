package com.wakemethere.app.domain.route

import com.wakemethere.app.domain.model.TransitLine
import com.wakemethere.app.domain.model.TransitNetwork
import com.wakemethere.app.domain.model.TransitSystem
import java.util.PriorityQueue
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Origin→destination journey planner over the Metro + BRT networks.
 *
 * Pure Kotlin (JVM-testable). The graph is built once per data load:
 *  - nodes are (station, line) pairs, so changing lines has an explicit cost;
 *  - ride edges follow the real track adjacency of each line;
 *  - transfer edges connect the lines of one physical station;
 *  - walk edges connect nearby Metro/BRT stations (different systems).
 *
 * Costs are minutes; distances are carried along for display.
 */
class RoutePlanner(networks: List<TransitNetwork>) {

    /** One logical station (merged across the lines that serve it). */
    data class StationOption(
        val id: String,
        val system: TransitSystem,
        val name: String,
        val nameEn: String?,
        val latitude: Double,
        val longitude: Double,
        val lineNames: List<String>,
        val lineColors: List<Int>,
    )

    /** A continuous ride on one line. */
    data class RouteLeg(
        val system: TransitSystem,
        val lineId: String,
        val lineName: String,
        val lineNameEn: String?,
        val color: Int,
        /** Ordered stations of the ride, endpoints inclusive. */
        val stations: List<StationOption>,
        val distanceMeters: Double,
        val minutes: Double,
    ) {
        val stops: Int get() = stations.size - 1
        val from: StationOption get() = stations.first()
        val to: StationOption get() = stations.last()
    }

    /** A change between two legs: same-station transfer or a short walk. */
    data class Transfer(
        val fromStation: StationOption,
        val toStation: StationOption,
        val fromLineName: String,
        val toLineName: String,
        val walkMeters: Double,
        val minutes: Double,
    ) {
        val isWalk: Boolean get() = fromStation.id != toStation.id
    }

    data class RoutePlan(
        val legs: List<RouteLeg>,
        val transfers: List<Transfer>,
        val totalMinutes: Int,
        val totalDistanceMeters: Double,
    )

    private data class LineRef(val system: TransitSystem, val line: TransitLine)

    private enum class HopType { RIDE, TRANSFER, WALK }

    private data class Hop(
        val to: Int,
        val minutes: Double,
        val meters: Double,
        val type: HopType,
        val lineRef: LineRef?,
    )

    /** Node = (stationId, lineKey); packed into dense ints for Dijkstra. */
    private val nodeIds = ArrayList<Pair<String, String>>()
    private val nodeIndex = HashMap<Pair<String, String>, Int>()
    private val adjacency = ArrayList<MutableList<Hop>>()
    private val stationsById = LinkedHashMap<String, StationOption>()
    private val nodesByStation = HashMap<String, MutableList<Int>>()

    /** All logical stations, for pickers and nearest-station lookup. */
    val stations: List<StationOption> get() = stationsById.values.toList()

    init {
        val lineRefs = networks.flatMap { network ->
            network.lines.map { LineRef(network.system, it) }
        }

        // Logical stations merged by id, with the serving lines.
        val servingLines = HashMap<String, MutableList<LineRef>>()
        for (ref in lineRefs) {
            for (station in ref.line.stations) {
                servingLines.getOrPut(station.id) { mutableListOf() }.add(ref)
                stationsById.getOrPut(station.id) {
                    StationOption(
                        id = station.id,
                        system = ref.system,
                        name = station.name,
                        nameEn = station.nameEn,
                        latitude = station.latitude,
                        longitude = station.longitude,
                        lineNames = emptyList(),
                        lineColors = emptyList(),
                    )
                }
            }
        }
        for ((id, refs) in servingLines) {
            val unique = refs.distinctBy { it.system to it.line.id }
            stationsById[id] = stationsById.getValue(id).copy(
                lineNames = unique.map { it.line.name },
                lineColors = unique.map { it.line.color },
            )
        }

        fun node(stationId: String, ref: LineRef): Int {
            val key = stationId to "${ref.system}:${ref.line.id}"
            return nodeIndex.getOrPut(key) {
                nodeIds.add(key)
                adjacency.add(mutableListOf())
                nodesByStation.getOrPut(stationId) { mutableListOf() }.add(nodeIds.lastIndex)
                nodeIds.lastIndex
            }
        }

        // Ride edges along real track adjacency, both directions.
        for (ref in lineRefs) {
            val speedKmh = when (ref.system) {
                TransitSystem.METRO -> METRO_SPEED_KMH
                TransitSystem.BRT -> BRT_SPEED_KMH
            }
            val dwell = when (ref.system) {
                TransitSystem.METRO -> METRO_DWELL_MINUTES
                TransitSystem.BRT -> BRT_DWELL_MINUTES
            }
            for ((a, b) in ref.line.edges) {
                val sa = ref.line.stations[a]
                val sb = ref.line.stations[b]
                val meters = distanceMeters(sa.latitude, sa.longitude, sb.latitude, sb.longitude)
                val minutes = meters / 1000.0 / speedKmh * 60.0 + dwell
                val u = node(sa.id, ref)
                val v = node(sb.id, ref)
                adjacency[u].add(Hop(v, minutes, meters, HopType.RIDE, ref))
                adjacency[v].add(Hop(u, minutes, meters, HopType.RIDE, ref))
            }
        }

        // Same-station transfers between all serving lines.
        for ((id, refs) in servingLines) {
            val unique = refs.distinctBy { it.system to it.line.id }
            for (i in unique.indices) {
                for (j in unique.indices) {
                    if (i == j) continue
                    val u = node(id, unique[i])
                    val v = node(id, unique[j])
                    adjacency[u].add(
                        Hop(v, TRANSFER_MINUTES, 0.0, HopType.TRANSFER, unique[j])
                    )
                }
            }
        }

        // Walk links between nearby stations of different systems.
        val all = stationsById.values.toList()
        val metroStations = all.filter { it.system == TransitSystem.METRO }
        val brtStations = all.filter { it.system == TransitSystem.BRT }
        for (m in metroStations) {
            for (b in brtStations) {
                val d = distanceMeters(m.latitude, m.longitude, b.latitude, b.longitude)
                if (d > MAX_WALK_LINK_METERS) continue
                val minutes = d / 1000.0 / WALK_SPEED_KMH * 60.0 + WALK_PENALTY_MINUTES
                for (u in nodesByStation[m.id].orEmpty()) {
                    for (v in nodesByStation[b.id].orEmpty()) {
                        adjacency[u].add(Hop(v, minutes, d, HopType.WALK, null))
                        adjacency[v].add(Hop(u, minutes, d, HopType.WALK, null))
                    }
                }
            }
        }
    }

    fun station(id: String): StationOption? = stationsById[id]

    /** Closest stations to a point, for "from my location" and free pins. */
    fun nearest(
        latitude: Double,
        longitude: Double,
        limit: Int = 3,
        maxMeters: Double = 3_000.0,
    ): List<Pair<StationOption, Double>> =
        stationsById.values
            .map { it to distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { it.second <= maxMeters }
            .sortedBy { it.second }
            .take(limit)

    /** Best route, or null when the ids are unknown/equal or disconnected. */
    fun plan(fromStationId: String, toStationId: String): RoutePlan? =
        plan(fromStationId, toStationId, penalizedLineIds = emptySet())

    /**
     * Best route plus (when one exists) a genuinely different alternative,
     * found by re-planning with the best route's lines made expensive.
     */
    fun planWithAlternative(fromStationId: String, toStationId: String): List<RoutePlan> {
        val best = plan(fromStationId, toStationId) ?: return emptyList()
        val usedLines = best.legs.map { "${it.system}:${it.lineId}" }.toSet()
        val alt = plan(fromStationId, toStationId, penalizedLineIds = usedLines)
        val plans = mutableListOf(best)
        if (alt != null &&
            alt.legs.map { "${it.system}:${it.lineId}" } != best.legs.map { "${it.system}:${it.lineId}" } &&
            alt.totalMinutes <= best.totalMinutes * ALTERNATIVE_MAX_FACTOR
        ) {
            plans.add(alt)
        }
        return plans
    }

    private fun plan(
        fromStationId: String,
        toStationId: String,
        penalizedLineIds: Set<String>,
    ): RoutePlan? {
        if (fromStationId == toStationId) return null
        val sources = nodesByStation[fromStationId] ?: return null
        val targets = nodesByStation[toStationId]?.toSet() ?: return null

        val dist = DoubleArray(nodeIds.size) { Double.POSITIVE_INFINITY }
        val prevNode = IntArray(nodeIds.size) { -1 }
        val prevHop = arrayOfNulls<Hop>(nodeIds.size)
        val queue = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        for (s in sources) {
            dist[s] = 0.0
            queue.add(s to 0.0)
        }
        val done = BooleanArray(nodeIds.size)
        var goal = -1
        while (queue.isNotEmpty()) {
            val (u, d) = queue.poll()
            if (done[u]) continue
            done[u] = true
            if (u in targets) {
                goal = u
                break
            }
            for (hop in adjacency[u]) {
                var cost = hop.minutes
                if (hop.type == HopType.RIDE &&
                    "${hop.lineRef!!.system}:${hop.lineRef.line.id}" in penalizedLineIds
                ) {
                    cost *= PENALTY_FACTOR
                }
                val nd = d + cost
                if (nd < dist[hop.to]) {
                    dist[hop.to] = nd
                    prevNode[hop.to] = u
                    prevHop[hop.to] = hop
                    queue.add(hop.to to nd)
                }
            }
        }
        if (goal < 0) return null

        // Reconstruct the hop chain (source -> goal).
        val hops = ArrayList<Pair<Int, Hop>>() // (fromNode, hop)
        var cur = goal
        while (prevHop[cur] != null) {
            hops.add(prevNode[cur] to prevHop[cur]!!)
            cur = prevNode[cur]
        }
        hops.reverse()

        // Fold hops into ride legs and transfer steps. Costs are re-summed
        // from the hops' true minutes (penalties are search-only).
        val legs = mutableListOf<RouteLeg>()
        val transfers = mutableListOf<Transfer>()
        var totalMinutes = 0.0
        var totalMeters = 0.0
        var currentLine: LineRef? = null
        var currentStations = mutableListOf<StationOption>()
        var currentMeters = 0.0
        var currentMinutes = 0.0

        fun closeLeg() {
            val line = currentLine ?: return
            if (currentStations.size >= 2) {
                legs.add(
                    RouteLeg(
                        system = line.system,
                        lineId = line.line.id,
                        lineName = line.line.name,
                        lineNameEn = line.line.nameEn,
                        color = line.line.color,
                        stations = currentStations.toList(),
                        distanceMeters = currentMeters,
                        minutes = currentMinutes,
                    )
                )
            }
            currentLine = null
            currentStations = mutableListOf()
            currentMeters = 0.0
            currentMinutes = 0.0
        }

        for ((fromNode, hop) in hops) {
            val fromStation = stationsById.getValue(nodeIds[fromNode].first)
            val toStation = stationsById.getValue(nodeIds[hop.to].first)
            totalMinutes += hop.minutes
            totalMeters += hop.meters
            when (hop.type) {
                HopType.RIDE -> {
                    val ref = hop.lineRef!!
                    if (currentLine?.let { it.system to it.line.id } != ref.system to ref.line.id) {
                        closeLeg()
                        currentLine = ref
                        currentStations.add(fromStation)
                    }
                    currentStations.add(toStation)
                    currentMeters += hop.meters
                    currentMinutes += hop.minutes
                }
                HopType.TRANSFER, HopType.WALK -> {
                    val fromLine = currentLine?.line?.name ?: fromStation.lineNames.firstOrNull().orEmpty()
                    closeLeg()
                    transfers.add(
                        Transfer(
                            fromStation = fromStation,
                            toStation = toStation,
                            fromLineName = fromLine,
                            toLineName = hop.lineRef?.line?.name
                                ?: toStation.lineNames.firstOrNull().orEmpty(),
                            walkMeters = hop.meters,
                            minutes = hop.minutes,
                        )
                    )
                }
            }
        }
        closeLeg()
        if (legs.isEmpty()) return null

        // Leading/trailing transfer bookkeeping: a transfer recorded before
        // the first ride or after the last one is not a real change.
        val trimmedTransfers = transfers.filter { t ->
            legs.first().from.id != t.fromStation.id || t.isWalk
        }

        return RoutePlan(
            legs = legs,
            transfers = trimmedTransfers,
            totalMinutes = ceil(totalMinutes).toInt(),
            totalDistanceMeters = totalMeters,
        )
    }

    private companion object {
        const val METRO_SPEED_KMH = 34.0
        const val BRT_SPEED_KMH = 17.0
        const val METRO_DWELL_MINUTES = 0.7
        const val BRT_DWELL_MINUTES = 0.6
        const val TRANSFER_MINUTES = 4.0
        const val MAX_WALK_LINK_METERS = 400.0
        const val WALK_SPEED_KMH = 4.5
        const val WALK_PENALTY_MINUTES = 3.0
        const val PENALTY_FACTOR = 2.5
        const val ALTERNATIVE_MAX_FACTOR = 1.8

        const val EARTH_RADIUS_METERS = 6_371_000.0

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val la1 = Math.toRadians(lat1)
            val la2 = Math.toRadians(lat2)
            val dLa = Math.toRadians(lat2 - lat1)
            val dLo = Math.toRadians(lon2 - lon1)
            val h = sin(dLa / 2) * sin(dLa / 2) +
                cos(la1) * cos(la2) * sin(dLo / 2) * sin(dLo / 2)
            return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
        }
    }
}
