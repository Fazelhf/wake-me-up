package com.wakemethere.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * CRUD access to saved favorite destinations.
 */
@Dao
interface DestinationDao {

    @Query("SELECT * FROM destinations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE id = :id")
    suspend fun getById(id: Long): DestinationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DestinationEntity): Long

    @Query("DELETE FROM destinations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
