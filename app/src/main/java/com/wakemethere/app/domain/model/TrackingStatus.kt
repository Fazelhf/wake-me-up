package com.wakemethere.app.domain.model

/**
 * Live state of the tracking service, observed by the home screen, the
 * notification and the alarm screen.
 */
sealed interface TrackingStatus {

    /** No alarm armed. */
    data object Idle : TrackingStatus

    /**
     * An alarm is armed and the service is tracking.
     *
     * @param distanceMeters last known distance to the destination, or null
     *   before the first usable fix arrives.
     * @param signalWeak true when no fix has been received for over a minute
     *   (e.g. metro tunnel) — tracking continues, the UI shows a hint.
     */
    data class Tracking(
        val destination: Destination,
        val distanceMeters: Float?,
        val signalWeak: Boolean = false,
        /** Distance at the moment tracking started, for trip progress. */
        val startDistanceMeters: Float? = null,
    ) : TrackingStatus

    /** The trigger radius was reached and the alarm is ringing. */
    data class Alarming(
        val destination: Destination,
        val distanceMeters: Float?,
    ) : TrackingStatus
}
