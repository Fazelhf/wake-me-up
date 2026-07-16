package com.wakemethere.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Access to recorded trips (newest first). */
@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY arrivedAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Insert
    suspend fun insert(entity: TripEntity): Long

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trips")
    suspend fun clear()
}
