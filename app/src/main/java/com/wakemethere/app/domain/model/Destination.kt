package com.wakemethere.app.domain.model

/**
 * A destination the user can be woken up at. Persisted in Room when saved as
 * a favorite; also used transiently for one-off "tap on map" destinations.
 */
data class Destination(
    val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
