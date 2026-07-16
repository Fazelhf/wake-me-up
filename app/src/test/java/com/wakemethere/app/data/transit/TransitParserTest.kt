package com.wakemethere.app.data.transit

import com.wakemethere.app.domain.model.TransitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TransitParser]: JSON shape, station ids, color parsing
 * and station lookup.
 */
class TransitParserTest {

    private val json = """
        {
          "lines": [
            {
              "id": "1",
              "name": "Line 1",
              "color": "#E0001B",
              "stations": [
                {"name": "Tajrish", "lat": 35.805, "lon": 51.433},
                {"name": "Gheytariyeh", "lat": 35.794, "lon": 51.437}
              ]
            },
            {
              "id": "7",
              "name": "Line 7",
              "color": "#7C2E8E",
              "stations": [
                {"name": "Basij", "lat": 35.663, "lon": 51.497},
                {"name": "Ahang", "lat": 35.653, "lon": 51.478}
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses lines and stations`() {
        val network = TransitParser.parse(TransitSystem.METRO, json)

        assertEquals(TransitSystem.METRO, network.system)
        assertEquals(2, network.lines.size)
        assertEquals("Line 1", network.lines[0].name)
        assertEquals(2, network.lines[0].stations.size)
        assertEquals("Tajrish", network.lines[0].stations[0].name)
        assertEquals(35.805, network.lines[0].stations[0].latitude, 1e-9)
        assertEquals(51.433, network.lines[0].stations[0].longitude, 1e-9)
    }

    @Test
    fun `station ids are stable and unique across lines`() {
        val network = TransitParser.parse(TransitSystem.METRO, json)
        val ids = network.lines.flatMap { line -> line.stations.map { it.id } }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals("METRO:1:0", network.lines[0].stations[0].id)
        assertEquals("METRO:7:1", network.lines[1].stations[1].id)
    }

    @Test
    fun `color hex parses to opaque argb`() {
        val network = TransitParser.parse(TransitSystem.METRO, json)
        assertEquals(0xFFE0001B.toInt(), network.lines[0].color)
        assertEquals(0xFF7C2E8E.toInt(), network.lines[1].color)
    }

    @Test
    fun `findStation returns the line and station, or null for unknown ids`() {
        val network = TransitParser.parse(TransitSystem.METRO, json)

        val found = network.findStation("METRO:7:0")
        assertNotNull(found)
        assertEquals("Line 7", found!!.first.name)
        assertEquals("Basij", found.second.name)

        assertNull(network.findStation("METRO:9:0"))
    }
}
