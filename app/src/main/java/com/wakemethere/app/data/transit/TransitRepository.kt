package com.wakemethere.app.data.transit

import android.content.Context
import com.wakemethere.app.domain.model.TransitNetwork
import com.wakemethere.app.domain.model.TransitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled Tehran Metro / BRT station networks from app assets.
 * Data ships offline (there is no connectivity in the metro) and is cached
 * in memory after the first load.
 *
 * Station coordinates are approximate; `tools/fetch_stations.py` in the repo
 * regenerates the JSON files with exact OpenStreetMap data.
 */
@Singleton
class TransitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val cache = mutableMapOf<TransitSystem, TransitNetwork>()
    private val mutex = Mutex()

    suspend fun network(system: TransitSystem): TransitNetwork = mutex.withLock {
        cache.getOrPut(system) {
            withContext(Dispatchers.IO) {
                val fileName = when (system) {
                    TransitSystem.METRO -> "transit/tehran_metro.json"
                    TransitSystem.BRT -> "transit/tehran_brt.json"
                }
                val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
                TransitParser.parse(system, json)
            }
        }
    }
}
