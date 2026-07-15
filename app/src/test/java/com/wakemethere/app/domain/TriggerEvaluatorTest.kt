package com.wakemethere.app.domain

import com.wakemethere.app.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit tests for [TriggerEvaluator]: trigger decision, stale-fix rejection,
 * accuracy rejection and the tunnel-emergence case.
 *
 * Uses a haversine distance function instead of the Android
 * Location.distanceBetween (not available on the JVM); the small difference
 * between the two is irrelevant for these assertions.
 */
class TriggerEvaluatorTest {

    // Tehran metro stations used as realistic coordinates.
    private val targetLat = 35.7008 // Tajrish-ish area
    private val targetLon = 51.3910

    private val evaluator = TriggerEvaluator(distanceMeters = ::haversineMeters)

    private fun fix(
        lat: Double = targetLat,
        lon: Double = targetLon,
        accuracy: Float = 10f,
        elapsedMillis: Long = 100_000L,
    ) = LocationFix(lat, lon, accuracy, elapsedMillis)

    @Test
    fun `fresh accurate fix inside radius triggers`() {
        // ~222 m north of the target.
        val decision = evaluator.evaluate(
            fix = fix(lat = targetLat + 0.002),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 100_000L,
        )
        assertTrue(decision.shouldTrigger)
        assertNotNull(decision.distanceMeters)
        assertTrue(decision.distanceMeters!! in 150f..300f)
    }

    @Test
    fun `fix outside radius does not trigger`() {
        // ~1.1 km north of the target.
        val decision = evaluator.evaluate(
            fix = fix(lat = targetLat + 0.010),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 100_000L,
        )
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun `stale fix inside radius is rejected`() {
        // Fix is 31 s old: must never trigger, even at distance zero.
        val decision = evaluator.evaluate(
            fix = fix(elapsedMillis = 100_000L),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 131_000L,
        )
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun `stale fix still reports distance for display`() {
        val decision = evaluator.evaluate(
            fix = fix(elapsedMillis = 0L),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 500_000L,
        )
        assertFalse(decision.shouldTrigger)
        assertNotNull(decision.distanceMeters)
    }

    @Test
    fun `fix at exactly max age still triggers`() {
        val decision = evaluator.evaluate(
            fix = fix(elapsedMillis = 100_000L),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 100_000L + TriggerEvaluator.DEFAULT_MAX_FIX_AGE_MILLIS,
        )
        assertTrue(decision.shouldTrigger)
    }

    @Test
    fun `inaccurate fix inside radius is rejected`() {
        // 201 m accuracy: worse than the 200 m limit.
        val decision = evaluator.evaluate(
            fix = fix(accuracy = 201f),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 100_000L,
        )
        assertFalse(decision.shouldTrigger)
    }

    @Test
    fun `accuracy at exactly the limit still triggers`() {
        val decision = evaluator.evaluate(
            fix = fix(accuracy = TriggerEvaluator.DEFAULT_MAX_ACCURACY_METERS),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 100_000L,
        )
        assertTrue(decision.shouldTrigger)
    }

    @Test
    fun `tunnel emergence - first fresh fix after a long gap triggers immediately`() {
        // The previous fix was minutes ago (train in a tunnel); the train
        // emerges already inside the radius. The evaluator is stateless per
        // fix, so a fresh accurate fix inside the radius must trigger no
        // matter how long the silence before it was.
        val emergenceFix = fix(lat = targetLat + 0.001, elapsedMillis = 600_000L)
        val decision = evaluator.evaluate(
            fix = emergenceFix,
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 601_000L,
        )
        assertTrue(decision.shouldTrigger)
    }

    @Test
    fun `distance equal to radius triggers`() {
        val fixedDistanceEvaluator = TriggerEvaluator(distanceMeters = { _, _, _, _ -> 500f })
        val decision = fixedDistanceEvaluator.evaluate(
            fix = fix(),
            targetLat = targetLat,
            targetLon = targetLon,
            radiusMeters = 500,
            nowElapsedRealtimeMillis = 100_000L,
        )
        assertTrue(decision.shouldTrigger)
        assertEquals(500f, decision.distanceMeters)
    }

    /** Great-circle distance in meters (JVM stand-in for Location.distanceBetween). */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusMeters * c).toFloat()
    }
}
