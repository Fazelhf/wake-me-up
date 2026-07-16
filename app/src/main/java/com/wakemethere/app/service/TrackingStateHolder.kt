package com.wakemethere.app.service

import com.wakemethere.app.domain.model.TrackingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory bridge between the tracking service and the UI: the service
 * publishes its live status here, and the home screen / alarm screen collect
 * it. Singleton-scoped so both sides see the same instance.
 */
@Singleton
class TrackingStateHolder @Inject constructor() {

    private val _status = MutableStateFlow<TrackingStatus>(TrackingStatus.Idle)
    val status: StateFlow<TrackingStatus> = _status.asStateFlow()

    /**
     * Id of the most recently completed (arrived) trip, awaiting display in
     * the Trip Summary screen. Set by the service on arrival; consumed by the
     * UI via [consumeCompletedTrip].
     */
    private val _justCompletedTripId = MutableStateFlow<Long?>(null)
    val justCompletedTripId: StateFlow<Long?> = _justCompletedTripId.asStateFlow()

    fun update(status: TrackingStatus) {
        _status.value = status
    }

    fun setCompletedTrip(tripId: Long) {
        _justCompletedTripId.value = tripId
    }

    /** Called by the UI once it has navigated to the summary. */
    fun consumeCompletedTrip() {
        _justCompletedTripId.value = null
    }
}
