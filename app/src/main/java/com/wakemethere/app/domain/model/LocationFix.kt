package com.wakemethere.app.domain.model

/**
 * Platform-independent snapshot of a location fix. Timestamps use the
 * elapsed-realtime clock (monotonic, immune to wall-clock changes).
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val elapsedRealtimeMillis: Long,
)
