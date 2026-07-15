package com.wakemethere.app.domain

/**
 * Chooses the location-update interval from the remaining distance:
 * relaxed while far away, fast when approaching, to balance battery vs.
 * trigger precision.
 */
class AdaptiveIntervalPolicy(
    private val nearThresholdMeters: Float = DEFAULT_NEAR_THRESHOLD_METERS,
    private val farIntervalMillis: Long = DEFAULT_FAR_INTERVAL_MILLIS,
    private val nearIntervalMillis: Long = DEFAULT_NEAR_INTERVAL_MILLIS,
) {

    /**
     * Returns the desired update interval in milliseconds.
     *
     * With no known distance yet (null) the fast interval is used: the trip
     * may already be near the destination, and a first fix must arrive fast.
     */
    fun intervalFor(distanceMeters: Float?): Long =
        if (distanceMeters == null || distanceMeters <= nearThresholdMeters) {
            nearIntervalMillis
        } else {
            farIntervalMillis
        }

    companion object {
        const val DEFAULT_NEAR_THRESHOLD_METERS = 3_000f
        const val DEFAULT_FAR_INTERVAL_MILLIS = 5_000L
        const val DEFAULT_NEAR_INTERVAL_MILLIS = 2_000L
    }
}
