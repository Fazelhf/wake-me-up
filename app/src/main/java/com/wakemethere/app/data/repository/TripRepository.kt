package com.wakemethere.app.data.repository

import com.wakemethere.app.data.local.TripDao
import com.wakemethere.app.data.local.TripEntity
import com.wakemethere.app.domain.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Repository over recorded trips. */
@Singleton
class TripRepository @Inject constructor(
    private val dao: TripDao,
) {
    fun observeTrips(): Flow<List<Trip>> =
        dao.observeAll().map { list -> list.map(TripEntity::toDomain) }

    suspend fun getById(id: Long): Trip? = dao.getById(id)?.toDomain()

    /** Records a completed trip; returns the new row id. */
    suspend fun record(trip: Trip): Long = dao.insert(TripEntity.fromDomain(trip))

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.clear()
}
