package com.wakemethere.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App database: favorite destinations and recorded trips.
 */
@Database(
    entities = [DestinationEntity::class, TripEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class WakeMeThereDatabase : RoomDatabase() {
    abstract fun destinationDao(): DestinationDao
    abstract fun tripDao(): TripDao
}
