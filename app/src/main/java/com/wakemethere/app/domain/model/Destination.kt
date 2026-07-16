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
    /** How this destination was chosen: METRO, BRT or ANYWHERE. */
    val transitType: String = "ANYWHERE",
    /** Transit line name when picked from Metro/BRT (e.g. "خط ۴"), else null. */
    val lineName: String? = null,
)
