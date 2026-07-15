package com.wakemethere.app.domain

import com.wakemethere.app.domain.model.LocationFix

/**
 * Result of evaluating a fix against the armed destination.
 *
 * @param distanceMeters distance from the fix to the destination center, or
 *   null when the fix was rejected outright.
 * @param shouldTrigger true when the alarm must fire now.
 */
data class TriggerDecision(
    val distanceMeters: Float?,
    val shouldTrigger: Boolean,
)

/**
 * Pure trigger logic: decides whether a location fix means "we have arrived".
 *
 * Safety rules for metro/tunnel conditions:
 *  - fixes older than [maxFixAgeMillis] are ignored for triggering (a stale
 *    cached fix must never fire a false alarm),
 *  - fixes with accuracy worse than [maxAccuracyMeters] are ignored for
 *    triggering (cell-tower fixes in tunnels can be kilometers off),
 *  - a *fresh, accurate* fix inside the radius triggers immediately even if
 *    the previous fix was long ago (train emerging from a tunnel already
 *    inside the radius).
 *
 * The distance function is injected so the class stays JVM-testable;
 * production wiring passes `Location.distanceBetween`.
 */
class TriggerEvaluator(
    private val maxFixAgeMillis: Long = DEFAULT_MAX_FIX_AGE_MILLIS,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
    private val distanceMeters: (lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Float,
) {

    /**
     * Evaluates [fix] against a destination at ([targetLat], [targetLon])
     * with the given [radiusMeters].
     *
     * @param nowElapsedRealtimeMillis current monotonic time, compared with
     *   [LocationFix.elapsedRealtimeMillis] to compute the fix age.
     */
    fun evaluate(
        fix: LocationFix,
        targetLat: Double,
        targetLon: Double,
        radiusMeters: Int,
        nowElapsedRealtimeMillis: Long,
    ): TriggerDecision {
        val distance = distanceMeters(fix.latitude, fix.longitude, targetLat, targetLon)

        val ageMillis = nowElapsedRealtimeMillis - fix.elapsedRealtimeMillis
        val usableForTrigger = ageMillis <= maxFixAgeMillis && fix.accuracyMeters <= maxAccuracyMeters
        if (!usableForTrigger) {
            // Distance is still reported for display, but never trusted to fire.
            return TriggerDecision(distanceMeters = distance, shouldTrigger = false)
        }

        return TriggerDecision(
            distanceMeters = distance,
            shouldTrigger = distance <= radiusMeters,
        )
    }

    companion object {
        /** Fixes older than this are considered stale and never trigger. */
        const val DEFAULT_MAX_FIX_AGE_MILLIS = 30_000L

        /** Fixes less accurate than this never trigger. */
        const val DEFAULT_MAX_ACCURACY_METERS = 200f
    }
}
