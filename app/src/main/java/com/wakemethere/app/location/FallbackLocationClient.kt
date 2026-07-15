package com.wakemethere.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService
import com.wakemethere.app.domain.model.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Fallback location source using the framework [LocationManager], for
 * devices without Google Play Services. Listens to GPS and network
 * providers simultaneously.
 */
class FallbackLocationClient(context: Context) : LocationClient {

    private val manager: LocationManager? = context.getSystemService<LocationManager>()

    @SuppressLint("MissingPermission")
    override fun updates(intervalMillis: Long): Flow<LocationFix> = callbackFlow {
        val locationManager = manager
        if (locationManager == null) {
            close(IllegalStateException("LocationManager unavailable"))
            return@callbackFlow
        }

        val listener = LocationListener { location -> trySend(location.toFix()) }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter(locationManager.allProviders::contains)
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                intervalMillis,
                /* minDistanceM = */ 0f,
                listener,
                Looper.getMainLooper()
            )
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): LocationFix? {
        val locationManager = manager ?: return null
        var best: Location? = null
        for (provider in locationManager.allProviders) {
            val candidate = runCatching { locationManager.getLastKnownLocation(provider) }
                .getOrNull() ?: continue
            if (best == null || candidate.elapsedRealtimeNanos > best.elapsedRealtimeNanos) {
                best = candidate
            }
        }
        return best?.toFix()
    }
}
