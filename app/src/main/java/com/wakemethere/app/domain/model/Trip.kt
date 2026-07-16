package com.wakemethere.app.domain.model

/**
 * A completed journey, recorded when the user arrives at (and dismisses the
 * alarm for) a destination. Powers the Trip Summary and Trip History screens.
 */
data class Trip(
    val id: Long = 0L,
    val destinationName: String,
    /** METRO, BRT or ANYWHERE. */
    val transitType: String,
    val lineName: String?,
    /** Approximate journey length in meters (distance at the time of arming). */
    val distanceMeters: Float,
    /** Wall-clock start (arm) and arrival times, epoch millis. */
    val startedAt: Long,
    val arrivedAt: Long,
) {
    /** Trip duration in whole minutes (at least 1). */
    val durationMinutes: Int
        get() = ((arrivedAt - startedAt).coerceAtLeast(0L) / 60_000L).toInt().coerceAtLeast(1)
}
