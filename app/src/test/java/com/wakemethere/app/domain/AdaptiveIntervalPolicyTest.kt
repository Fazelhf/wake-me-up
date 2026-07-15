package com.wakemethere.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AdaptiveIntervalPolicy]: relaxed cadence while far from
 * the destination, fast cadence when close or when the distance is unknown.
 */
class AdaptiveIntervalPolicyTest {

    private val policy = AdaptiveIntervalPolicy()

    @Test
    fun `far from destination uses relaxed interval`() {
        assertEquals(
            AdaptiveIntervalPolicy.DEFAULT_FAR_INTERVAL_MILLIS,
            policy.intervalFor(10_000f),
        )
    }

    @Test
    fun `just beyond the threshold uses relaxed interval`() {
        assertEquals(
            AdaptiveIntervalPolicy.DEFAULT_FAR_INTERVAL_MILLIS,
            policy.intervalFor(AdaptiveIntervalPolicy.DEFAULT_NEAR_THRESHOLD_METERS + 1f),
        )
    }

    @Test
    fun `within threshold uses fast interval`() {
        assertEquals(
            AdaptiveIntervalPolicy.DEFAULT_NEAR_INTERVAL_MILLIS,
            policy.intervalFor(2_500f),
        )
    }

    @Test
    fun `exactly at threshold uses fast interval`() {
        assertEquals(
            AdaptiveIntervalPolicy.DEFAULT_NEAR_INTERVAL_MILLIS,
            policy.intervalFor(AdaptiveIntervalPolicy.DEFAULT_NEAR_THRESHOLD_METERS),
        )
    }

    @Test
    fun `unknown distance uses fast interval to get a first fix quickly`() {
        assertEquals(
            AdaptiveIntervalPolicy.DEFAULT_NEAR_INTERVAL_MILLIS,
            policy.intervalFor(null),
        )
    }

    @Test
    fun `custom thresholds are honored`() {
        val custom = AdaptiveIntervalPolicy(
            nearThresholdMeters = 1_000f,
            farIntervalMillis = 10_000L,
            nearIntervalMillis = 1_000L,
        )
        assertEquals(10_000L, custom.intervalFor(1_500f))
        assertEquals(1_000L, custom.intervalFor(900f))
    }
}
