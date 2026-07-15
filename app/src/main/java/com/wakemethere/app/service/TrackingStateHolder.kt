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

    fun update(status: TrackingStatus) {
        _status.value = status
    }
}
