package com.wakemethere.app.domain.route

import com.wakemethere.app.data.transit.TransitParser
import com.wakemethere.app.domain.model.TransitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Route-planner tests over the real bundled Tehran datasets, so they also
 * guard the generated assets (ids, adjacency, transfer links).
 */
class RoutePlannerTest {

    private val planner: RoutePlanner by lazy {
        val metro = TransitParser.parse(TransitSystem.METRO, asset("tehran_metro.json"))
        val brt = TransitParser.parse(TransitSystem.BRT, asset("tehran_brt.json"))
        RoutePlanner(listOf(metro, brt))
    }

    private fun asset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/transit/$name"),
            File("app/src/main/assets/transit/$name"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("asset $name not found from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun `same line ride needs no transfer`() {
        val plan = planner.plan("METRO:Tajrish", "METRO:Shahid Beheshti")
        assertNotNull(plan)
        plan!!
        assertEquals(1, plan.legs.size)
        assertEquals(0, plan.transfers.size)
        assertEquals("METRO:Tajrish", plan.legs.first().from.id)
        assertEquals("METRO:Shahid Beheshti", plan.legs.first().to.id)
        assertTrue(plan.legs.first().stops > 0)
        assertTrue(plan.totalMinutes > 0)
        assertTrue(plan.totalDistanceMeters > 1_000)
    }

    @Test
    fun `cross line trip changes at a real interchange`() {
        // Tajrish is only on line 1; Meydan-e Azadi is only on line 4.
        val plan = planner.plan("METRO:Tajrish", "METRO:Meydan-e Azadi")
        assertNotNull(plan)
        plan!!
        assertTrue(plan.legs.size >= 2)
        assertTrue(plan.transfers.isNotEmpty())
        // Every transfer happens at a station that serves both legs' lines.
        for (t in plan.transfers) {
            if (!t.isWalk) {
                assertTrue(t.fromStation.lineNames.size >= 2)
            }
        }
        // Legs chain together: each leg starts where the previous one ended
        // (or at the walk target).
        for (i in 1 until plan.legs.size) {
            val prevEnd = plan.legs[i - 1].to.id
            val start = plan.legs[i].from.id
            val linked = prevEnd == start || plan.transfers.any {
                it.fromStation.id == prevEnd && it.toStation.id == start
            }
            assertTrue("leg $i not linked", linked)
        }
    }

    @Test
    fun `metro to brt uses a walk link at a shared square`() {
        // The BRT line-1 stop at Enghelab Square sits on the metro station.
        val plan = planner.plan("METRO:Tajrish", "BRT:Chaharrah-e Tehranpars")
        assertNotNull(plan)
        plan!!
        assertTrue(plan.legs.any { it.system == TransitSystem.BRT })
        assertTrue(plan.transfers.any { it.isWalk })
    }

    @Test
    fun `nearest returns the station under the point first`() {
        val tajrish = planner.station("METRO:Tajrish")!!
        val nearest = planner.nearest(tajrish.latitude, tajrish.longitude, limit = 3)
        assertTrue(nearest.isNotEmpty())
        assertEquals("METRO:Tajrish", nearest.first().first.id)
        assertTrue(nearest.first().second < 50.0)
    }

    @Test
    fun `alternative differs from the best route when present`() {
        val plans = planner.planWithAlternative("METRO:Tajrish", "METRO:Meydan-e Azadi")
        assertTrue(plans.isNotEmpty())
        if (plans.size == 2) {
            val bestLines = plans[0].legs.map { "${it.system}:${it.lineId}" }
            val altLines = plans[1].legs.map { "${it.system}:${it.lineId}" }
            assertTrue(bestLines != altLines)
            assertTrue(plans[1].totalMinutes >= plans[0].totalMinutes)
        }
    }

    @Test
    fun `unknown or equal endpoints yield no plan`() {
        assertNull(planner.plan("METRO:Nope", "METRO:Tajrish"))
        assertNull(planner.plan("METRO:Tajrish", "METRO:Tajrish"))
    }

    @Test
    fun `every station is reachable from Tajrish`() {
        // Guards the generated data: no orphaned station in either network.
        // Stations within walking range of the origin (e.g. the BRT line-7
        // stop on Tajrish Square itself) need no ride at all, and plan()
        // deliberately returns null for ride-less routes — exclude them.
        val origin = planner.station("METRO:Tajrish")!!
        val walkable = planner
            .nearest(origin.latitude, origin.longitude, limit = 10, maxMeters = 400.0)
            .map { it.first.id }
            .toSet()
        val failures = planner.stations
            .filter { it.id != origin.id && it.id !in walkable }
            .filter { planner.plan(origin.id, it.id) == null }
            .map { it.id }
        assertEquals(emptyList<String>(), failures)
    }
}
