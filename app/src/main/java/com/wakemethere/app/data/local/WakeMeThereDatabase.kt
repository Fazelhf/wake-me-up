package com.wakemethere.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App database. Currently only stores favorite destinations.
 */
@Database(entities = [DestinationEntity::class], version = 1, exportSchema = false)
abstract class WakeMeThereDatabase : RoomDatabase() {
    abstract fun destinationDao(): DestinationDao
}
