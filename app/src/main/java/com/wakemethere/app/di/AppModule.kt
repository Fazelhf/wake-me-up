package com.wakemethere.app.di

import android.content.Context
import android.location.Location
import androidx.room.Room
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.wakemethere.app.data.local.DestinationDao
import com.wakemethere.app.data.local.TripDao
import com.wakemethere.app.data.local.WakeMeThereDatabase
import com.wakemethere.app.domain.AdaptiveIntervalPolicy
import com.wakemethere.app.domain.TriggerEvaluator
import com.wakemethere.app.location.FallbackLocationClient
import com.wakemethere.app.location.FusedLocationClient
import com.wakemethere.app.location.LocationClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Application-wide dependency bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WakeMeThereDatabase =
        Room.databaseBuilder(context, WakeMeThereDatabase::class.java, "wakemethere.db")
            // Trips were added in v2; the app is pre-release, so recreating the
            // DB on schema change is acceptable (no migration to maintain yet).
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDestinationDao(db: WakeMeThereDatabase): DestinationDao = db.destinationDao()

    @Provides
    fun provideTripDao(db: WakeMeThereDatabase): TripDao = db.tripDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    /**
     * Prefers the fused provider; falls back to the framework LocationManager
     * on devices without (working) Google Play Services.
     */
    @Provides
    @Singleton
    fun provideLocationClient(@ApplicationContext context: Context): LocationClient {
        val playServicesStatus =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return if (playServicesStatus == ConnectionResult.SUCCESS) {
            FusedLocationClient(context)
        } else {
            FallbackLocationClient(context)
        }
    }

    @Provides
    @Singleton
    fun provideTriggerEvaluator(): TriggerEvaluator =
        TriggerEvaluator(distanceMeters = ::platformDistanceMeters)

    @Provides
    @Singleton
    fun provideAdaptiveIntervalPolicy(): AdaptiveIntervalPolicy = AdaptiveIntervalPolicy()

    /** Geodesic distance via the platform implementation. */
    private fun platformDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
