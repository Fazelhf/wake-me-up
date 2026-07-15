package com.wakemethere.app.data.repository

import com.wakemethere.app.data.local.DestinationDao
import com.wakemethere.app.data.local.DestinationEntity
import com.wakemethere.app.domain.model.Destination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository over the Room-backed favorites list, exposing domain models.
 */
@Singleton
class DestinationRepository @Inject constructor(
    private val dao: DestinationDao,
) {

    /** All favorites, newest first. */
    fun observeFavorites(): Flow<List<Destination>> =
        dao.observeAll().map { list -> list.map(DestinationEntity::toDomain) }

    suspend fun getById(id: Long): Destination? = dao.getById(id)?.toDomain()

    /** Inserts or updates a favorite; returns the row id. */
    suspend fun save(destination: Destination): Long =
        dao.upsert(DestinationEntity.fromDomain(destination))

    suspend fun delete(id: Long) = dao.deleteById(id)
}
