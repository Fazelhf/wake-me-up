package com.wakemethere.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the wake-up alarm: looping alarm sound on the ALARM audio stream
 * (bypasses silent/vibrate modes of the ring/media streams) plus a strong
 * repeating vibration pattern.
 */
@Singleton
class AlarmRinger @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /**
     * Starts sound and/or vibration. Safe to call repeatedly; a second call
     * while already ringing is a no-op.
     *
     * @param soundUri custom sound to play, or null for the device default
     *   alarm sound (falling back to ringtone, then notification sound).
     */
    fun start(playSound: Boolean, soundUri: Uri?, vibrate: Boolean) {
        if (playSound && mediaPlayer == null) {
            startSound(soundUri)
        }
        if (vibrate && vibrator == null) {
            startVibration()
        }
    }

    /** Stops sound and vibration. Safe to call when not ringing. */
    fun stop() {
        mediaPlayer?.run {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    private fun startSound(customUri: Uri?) {
        val uri = customUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        mediaPlayer = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
            null
        }
    }

    private fun startVibration() {
        val service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        } ?: return

        vibrator = service
        // Strong on/off pattern repeated indefinitely (repeat index 0).
        val timings = longArrayOf(0, 800, 400, 800, 400)
        service.vibrate(VibrationEffect.createWaveform(timings, /* repeat = */ 0))
    }

    private companion object {
        const val TAG = "AlarmRinger"
    }
}
