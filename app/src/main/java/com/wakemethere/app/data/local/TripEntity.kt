package com.wakemethere.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wakemethere.app.domain.model.Trip

/** Room row for a completed trip. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val destinationName: String,
    val transitType: String,
    val lineName: String?,
    val distanceMeters: Float,
    val startedAt: Long,
    val arrivedAt: Long,
) {
    fun toDomain() = Trip(
        id = id,
        destinationName = destinationName,
        transitType = transitType,
        lineName = lineName,
        distanceMeters = distanceMeters,
        startedAt = startedAt,
        arrivedAt = arrivedAt,
    )

    companion object {
        fun fromDomain(trip: Trip) = TripEntity(
            id = trip.id,
            destinationName = trip.destinationName,
            transitType = trip.transitType,
            lineName = trip.lineName,
            distanceMeters = trip.distanceMeters,
            startedAt = trip.startedAt,
            arrivedAt = trip.arrivedAt,
        )
    }
}
