package com.wakemethere.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wakemethere.app.domain.model.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Primary location source using FusedLocationProviderClient (Google Play
 * Services). Preferred because it blends GPS, Wi-Fi and cell fixes and is
 * more battery efficient than raw GPS.
 */
class FusedLocationClient(context: Context) : LocationClient {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun updates(intervalMillis: Long): Flow<LocationFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }

        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): LocationFix? =
        suspendCancellableCoroutine { continuation ->
            fused.lastLocation
                .addOnSuccessListener { location -> continuation.resume(location?.toFix()) }
                .addOnFailureListener { continuation.resume(null) }
        }
}
