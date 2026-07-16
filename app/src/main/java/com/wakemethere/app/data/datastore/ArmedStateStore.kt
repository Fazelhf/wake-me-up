package com.wakemethere.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wakemethere.app.domain.model.Destination
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.armedStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "armed_state")

/**
 * Persists the currently armed destination so the tracking service can
 * restore it after process death (START_STICKY restart with a null intent).
 */
@Singleton
class ArmedStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Saves [destination] as the armed target. */
    suspend fun setArmed(destination: Destination, startedAt: Long) {
        context.armedStateDataStore.edit { prefs ->
            prefs[KEY_ID] = destination.id
            prefs[KEY_NAME] = destination.name
            prefs[KEY_LAT] = destination.latitude
            prefs[KEY_LON] = destination.longitude
            prefs[KEY_RADIUS] = destination.radiusMeters
            prefs[KEY_CREATED_AT] = destination.createdAt
            prefs[KEY_TRANSIT] = destination.transitType
            destination.lineName?.let { prefs[KEY_LINE] = it } ?: prefs.remove(KEY_LINE)
            prefs[KEY_STARTED_AT] = startedAt
        }
    }

    /** Wall-clock time the current alarm was armed, or null. */
    suspend fun getStartedAt(): Long? =
        context.armedStateDataStore.data.first()[KEY_STARTED_AT]

    /** Clears the armed target (tracking stopped or alarm dismissed). */
    suspend fun clear() {
        context.armedStateDataStore.edit { it.clear() }
    }

    /** Returns the armed destination, or null when nothing is armed. */
    suspend fun getArmed(): Destination? {
        val prefs = context.armedStateDataStore.data.first()
        val name = prefs[KEY_NAME] ?: return null
        val lat = prefs[KEY_LAT] ?: return null
        val lon = prefs[KEY_LON] ?: return null
        val radius = prefs[KEY_RADIUS] ?: return null
        return Destination(
            id = prefs[KEY_ID] ?: 0L,
            name = name,
            latitude = lat,
            longitude = lon,
            radiusMeters = radius,
            createdAt = prefs[KEY_CREATED_AT] ?: 0L,
            transitType = prefs[KEY_TRANSIT] ?: "ANYWHERE",
            lineName = prefs[KEY_LINE],
        )
    }

    private companion object {
        val KEY_ID = longPreferencesKey("id")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_LAT = doublePreferencesKey("lat")
        val KEY_LON = doublePreferencesKey("lon")
        val KEY_RADIUS = intPreferencesKey("radius")
        val KEY_CREATED_AT = longPreferencesKey("created_at")
        val KEY_TRANSIT = stringPreferencesKey("transit")
        val KEY_LINE = stringPreferencesKey("line")
        val KEY_STARTED_AT = longPreferencesKey("started_at")
    }
}
