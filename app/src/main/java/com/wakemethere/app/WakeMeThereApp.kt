package com.wakemethere.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Application entry point. Initializes Hilt, OSMdroid configuration and the
 * notification channels used by the tracking service and the alarm.
 */
@HiltAndroidApp
class WakeMeThereApp : Application() {

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        createNotificationChannels()
    }

    /**
     * OSMdroid requires a user agent (OSM tile usage policy) and a writable
     * cache directory. Using app-private storage avoids any storage permission.
     */
    private fun configureOsmdroid() {
        Configuration.getInstance().apply {
            userAgentValue = OSM_USER_AGENT
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService<NotificationManager>() ?: return

        val tracking = NotificationChannel(
            CHANNEL_TRACKING,
            getString(R.string.notif_channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_tracking_desc)
            setShowBadge(false)
        }

        // Sound is played by the service on the ALARM stream (so it bypasses
        // silent mode); the channel itself stays silent to avoid double audio.
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.notif_channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_channel_alarm_desc)
            setSound(null, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build())
            enableVibration(false)
            setBypassDnd(true)
        }

        manager.createNotificationChannels(listOf(tracking, alarm))
    }

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_ALARM = "alarm"

        /** Identifies the app to OSM services per their usage policies. */
        const val OSM_USER_AGENT = "WakeMeThere/1.0 (Android; contact: fazel.hafezi@gmail.com)"
    }
}
