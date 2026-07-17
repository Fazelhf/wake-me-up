package com.wakemethere.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.wakemethere.app.MainActivity
import com.wakemethere.app.R
import com.wakemethere.app.WakeMeThereApp
import com.wakemethere.app.data.datastore.ArmedStateStore
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.domain.AdaptiveIntervalPolicy
import com.wakemethere.app.domain.TriggerEvaluator
import com.wakemethere.app.domain.model.Destination
import com.wakemethere.app.domain.model.LocationFix
import com.wakemethere.app.domain.model.TrackingStatus
import com.wakemethere.app.location.LocationClient
import com.wakemethere.app.util.formatDistance
import com.wakemethere.app.ui.alarm.AlarmActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that tracks the user's location while an alarm is
 * armed and fires the full-screen alarm when the trigger radius is reached.
 *
 * Behavior highlights:
 *  - adaptive update interval (relaxed while far, fast when close),
 *  - stale/inaccurate fixes never trigger (metro tunnel safety),
 *  - "GPS signal weak" notification state after 60 s without a fix,
 *  - START_STICKY + persisted armed destination so a system restart of the
 *    process resumes tracking automatically.
 */
@AndroidEntryPoint
class TrackingService : LifecycleService() {

    @Inject lateinit var locationClient: LocationClient
    @Inject lateinit var triggerEvaluator: TriggerEvaluator
    @Inject lateinit var intervalPolicy: AdaptiveIntervalPolicy
    @Inject lateinit var stateHolder: TrackingStateHolder
    @Inject lateinit var armedStateStore: ArmedStateStore
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var alarmRinger: AlarmRinger
    @Inject lateinit var tripRepository: com.wakemethere.app.data.repository.TripRepository

    private var destination: Destination? = null
    private var trackingJob: Job? = null
    private var watchdogJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentIntervalMillis: Long = 0L
    private var lastFixElapsedMillis: Long? = null
    private var lastDistanceMeters: Float? = null
    private var signalWeak = false
    private var alarming = false

    /** Wall-clock arm time and the initial distance, used to record the trip. */
    private var tripStartedAtWall: Long = 0L
    private var tripStartDistance: Float? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val dest = intent.readDestination()
                if (dest != null) {
                    startTracking(dest, startedAt = System.currentTimeMillis())
                } else {
                    stopEverything()
                }
            }
            ACTION_STOP -> stopEverything()
            ACTION_DISMISS_ALARM -> stopEverything()
            // Null intent: process was killed and restarted (START_STICKY).
            // Restore the armed destination from persistent storage.
            null -> lifecycleScope.launch {
                val restored = armedStateStore.getArmed()
                val startedAt = armedStateStore.getStartedAt()
                if (restored != null) {
                    startTracking(restored, startedAt = startedAt ?: System.currentTimeMillis())
                } else {
                    stopEverything()
                }
            }
        }
        return START_STICKY
    }

    private fun hasLocationPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Arms (or re-arms) tracking toward [dest]. */
    private fun startTracking(dest: Destination, startedAt: Long) {
        // Starting a location-type foreground service without the permission
        // throws on API 34; bail out gracefully instead of crashing.
        if (!hasLocationPermission()) {
            stopEverything()
            return
        }
        destination = dest
        alarming = false
        lastDistanceMeters = null
        lastFixElapsedMillis = SystemClock.elapsedRealtime()
        signalWeak = false
        tripStartedAtWall = startedAt
        tripStartDistance = null

        goForeground(buildTrackingNotification())
        acquireWakeLock()
        stateHolder.update(TrackingStatus.Tracking(dest, distanceMeters = null))

        lifecycleScope.launch { armedStateStore.setArmed(dest, startedAt) }

        restartLocationUpdates(intervalPolicy.intervalFor(null))
        startSignalWatchdog()
    }

    /**
     * (Re)starts the location stream at [intervalMillis]. Called initially
     * and whenever the adaptive policy asks for a different cadence.
     */
    private fun restartLocationUpdates(intervalMillis: Long) {
        if (trackingJob != null && intervalMillis == currentIntervalMillis) return
        currentIntervalMillis = intervalMillis
        trackingJob?.cancel()
        trackingJob = lifecycleScope.launch {
            locationClient.updates(intervalMillis).collect { fix -> onFix(fix) }
        }
    }

    /** Handles every incoming fix: trigger evaluation + UI/notification updates. */
    private fun onFix(fix: LocationFix) {
        val dest = destination ?: return
        lastFixElapsedMillis = SystemClock.elapsedRealtime()

        val decision = triggerEvaluator.evaluate(
            fix = fix,
            targetLat = dest.latitude,
            targetLon = dest.longitude,
            radiusMeters = dest.radiusMeters,
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
        lastDistanceMeters = decision.distanceMeters ?: lastDistanceMeters
        // Remember the first known distance as the approximate trip length.
        if (tripStartDistance == null) tripStartDistance = decision.distanceMeters

        if (signalWeak) {
            signalWeak = false
        }

        if (alarming) {
            // Keep the alarm screen's distance display fresh.
            stateHolder.update(TrackingStatus.Alarming(dest, lastDistanceMeters))
            return
        }

        if (decision.shouldTrigger) {
            fireAlarm(dest)
        } else {
            stateHolder.update(TrackingStatus.Tracking(dest, lastDistanceMeters, signalWeak = false))
            updateNotification(buildTrackingNotification())
            // Adapt cadence to the remaining distance.
            restartLocationUpdates(intervalPolicy.intervalFor(lastDistanceMeters))
        }
    }

    /**
     * Periodically checks for signal loss (metro tunnels): after 60 s without
     * any fix the notification switches to a "GPS signal weak" hint. Tracking
     * keeps running; the next fresh fix re-evaluates the trigger immediately.
     */
    private fun startSignalWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(WATCHDOG_PERIOD_MILLIS)
                if (alarming) continue
                val last = lastFixElapsedMillis ?: continue
                val quiet = SystemClock.elapsedRealtime() - last > SIGNAL_WEAK_AFTER_MILLIS
                if (quiet != signalWeak) {
                    signalWeak = quiet
                    destination?.let { dest ->
                        stateHolder.update(TrackingStatus.Tracking(dest, lastDistanceMeters, signalWeak))
                        updateNotification(buildTrackingNotification())
                    }
                }
            }
        }
    }

    /** Fires the full-screen alarm: notification + sound + vibration. */
    private fun fireAlarm(dest: Destination) {
        alarming = true
        stateHolder.update(TrackingStatus.Alarming(dest, lastDistanceMeters))
        recordTrip(dest)

        lifecycleScope.launch {
            val settings = settingsStore.current()
            val customUri = settings.customSoundUri
                ?.takeUnless { settings.useDefaultAlarmSound }
                ?.let(Uri::parse)
            alarmRinger.start(
                playSound = true,
                soundUri = customUri,
                vibrate = settings.vibrationEnabled,
            )
        }

        // Promote the foreground notification to the high-importance alarm
        // channel with a full-screen intent so it takes over the lock screen.
        goForeground(buildAlarmNotification(dest))

        // Also launch the alarm activity directly when we are allowed to
        // (works when the app is in the foreground; the full-screen intent
        // covers the locked/background case).
        runCatching {
            startActivity(AlarmActivity.createIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Records the completed journey and signals the UI to show its summary. */
    private fun recordTrip(dest: Destination) {
        val trip = com.wakemethere.app.domain.model.Trip(
            destinationName = dest.name,
            transitType = dest.transitType,
            lineName = dest.lineName,
            // Fall back to the radius if no fix ever arrived (unlikely).
            distanceMeters = tripStartDistance ?: dest.radiusMeters.toFloat(),
            startedAt = tripStartedAtWall.takeIf { it > 0L } ?: System.currentTimeMillis(),
            arrivedAt = System.currentTimeMillis(),
        )
        lifecycleScope.launch {
            val id = runCatching { tripRepository.record(trip) }.getOrNull()
            if (id != null) stateHolder.setCompletedTrip(id)
        }
    }

    /** Stops ringing, tracking and the service itself. */
    private fun stopEverything() {
        alarmRinger.stop()
        trackingJob?.cancel()
        watchdogJob?.cancel()
        trackingJob = null
        watchdogJob = null
        destination = null
        alarming = false
        releaseWakeLock()
        stateHolder.update(TrackingStatus.Idle)
        lifecycleScope.launch {
            armedStateStore.clear()
            ServiceCompat.stopForeground(this@TrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        alarmRinger.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    // --- Notifications -----------------------------------------------------

    private fun goForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            // Permission revoked mid-flight: stop cleanly, never crash.
            stopEverything()
        }
    }

    private fun updateNotification(notification: Notification) {
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildTrackingNotification(): Notification {
        val dest = destination
        val distanceText = when {
            signalWeak -> getString(R.string.notif_signal_weak)
            lastDistanceMeters != null ->
                getString(R.string.notif_distance_remaining, formatDistance(this, lastDistanceMeters!!))
            else -> getString(R.string.notif_waiting_fix)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, WakeMeThereApp.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_tracking_title, dest?.name.orEmpty()))
            .setContentText(distanceText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notif_stop_action), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildAlarmNotification(dest: Destination): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            2,
            AlarmActivity.createIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, TrackingService::class.java).setAction(ACTION_DISMISS_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, WakeMeThereApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_alarm_title, dest.name))
            .setContentText(
                getString(R.string.notif_alarm_text, formatDistance(this, dest.radiusMeters.toFloat()))
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .addAction(0, getString(R.string.alarm_dismiss), dismissIntent)
            .build()
    }

    // --- Wake lock ---------------------------------------------------------

    /**
     * A partial wake lock keeps the CPU processing location callbacks with
     * the screen off. Held only while an alarm is armed.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeMeThere:tracking")
            ?.apply { acquire(MAX_WAKE_LOCK_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    // --- Intent plumbing ---------------------------------------------------

    private fun Intent.readDestination(): Destination? {
        val name = getStringExtra(EXTRA_NAME) ?: return null
        if (!hasExtra(EXTRA_LAT) || !hasExtra(EXTRA_LON)) return null
        return Destination(
            id = getLongExtra(EXTRA_ID, 0L),
            name = name,
            latitude = getDoubleExtra(EXTRA_LAT, 0.0),
            longitude = getDoubleExtra(EXTRA_LON, 0.0),
            radiusMeters = getIntExtra(EXTRA_RADIUS, 500),
            transitType = getStringExtra(EXTRA_TRANSIT) ?: "ANYWHERE",
            lineName = getStringExtra(EXTRA_LINE),
        )
    }

    companion object {
        private const val ACTION_START = "com.wakemethere.app.action.START"
        private const val ACTION_STOP = "com.wakemethere.app.action.STOP"
        private const val ACTION_DISMISS_ALARM = "com.wakemethere.app.action.DISMISS_ALARM"

        private const val EXTRA_ID = "id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LON = "lon"
        private const val EXTRA_RADIUS = "radius"
        private const val EXTRA_TRANSIT = "transit"
        private const val EXTRA_LINE = "line"

        private const val NOTIFICATION_ID = 1001
        private const val WATCHDOG_PERIOD_MILLIS = 10_000L
        private const val SIGNAL_WEAK_AFTER_MILLIS = 60_000L

        /** 12 h upper bound so a forgotten alarm cannot hold the CPU forever. */
        private const val MAX_WAKE_LOCK_MILLIS = 12 * 60 * 60 * 1000L

        /** Arms the alarm for [destination] and starts foreground tracking. */
        fun start(context: Context, destination: Destination) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, destination.id)
                .putExtra(EXTRA_NAME, destination.name)
                .putExtra(EXTRA_LAT, destination.latitude)
                .putExtra(EXTRA_LON, destination.longitude)
                .putExtra(EXTRA_RADIUS, destination.radiusMeters)
                .putExtra(EXTRA_TRANSIT, destination.transitType)
                .putExtra(EXTRA_LINE, destination.lineName)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops tracking (and any ringing alarm). */
        fun stop(context: Context) {
            context.startService(
                Intent(context, TrackingService::class.java).setAction(ACTION_STOP)
            )
        }

        /** Dismisses a ringing alarm and stops the service. */
        fun dismissAlarm(context: Context) {
            context.startService(
                Intent(context, TrackingService::class.java).setAction(ACTION_DISMISS_ALARM)
            )
        }
    }
}
