package com.wakemethere.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wakemethere.app.domain.model.Destination

/**
 * Room row for a saved favorite destination.
 */
@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val createdAt: Long,
) {
    fun toDomain() = Destination(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(destination: Destination) = DestinationEntity(
            id = destination.id,
            name = destination.name,
            latitude = destination.latitude,
            longitude = destination.longitude,
            radiusMeters = destination.radiusMeters,
            createdAt = destination.createdAt,
        )
    }
}
