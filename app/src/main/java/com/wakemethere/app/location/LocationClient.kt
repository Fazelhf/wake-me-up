package com.wakemethere.app.location

import android.location.Location
import com.wakemethere.app.domain.model.LocationFix
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over a location source so the tracking service does not care
 * whether fixes come from Google Play Services (fused) or the plain Android
 * [android.location.LocationManager] fallback.
 */
interface LocationClient {

    /**
     * Streams high-accuracy location fixes roughly every [intervalMillis].
     * The flow stops delivering when collection is cancelled.
     *
     * Callers must hold ACCESS_FINE_LOCATION before collecting.
     */
    fun updates(intervalMillis: Long): Flow<LocationFix>

    /** Last cached fix, or null if none is available. */
    suspend fun lastKnown(): LocationFix?
}

/** Converts a platform [Location] into the domain [LocationFix]. */
fun Location.toFix() = LocationFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
    elapsedRealtimeMillis = elapsedRealtimeNanos / 1_000_000,
)
