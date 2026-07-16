package com.wakemethere.app.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakemethere.app.R
import com.wakemethere.app.domain.model.TrackingStatus
import com.wakemethere.app.service.TrackingService
import com.wakemethere.app.service.TrackingStateHolder
import com.wakemethere.app.ui.theme.WakeMeThereTheme
import com.wakemethere.app.util.formatDistance
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen alarm shown over the lock screen when the destination radius
 * is reached. Launched via the notification's full-screen intent. The alarm
 * keeps ringing until the user hits Dismiss (no auto-timeout).
 */
@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {

    @Inject lateinit var stateHolder: TrackingStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            WakeMeThereTheme {
                val status by stateHolder.status.collectAsStateWithLifecycle()

                // Close automatically if the alarm was dismissed elsewhere
                // (e.g. via the notification action).
                LaunchedEffect(status) {
                    if (status is TrackingStatus.Idle) finish()
                }

                AlarmScreen(
                    status = status,
                    onDismiss = {
                        TrackingService.dismissAlarm(this)
                        finish()
                    },
                )
            }
        }
    }

    /** Makes the activity visible above the keyguard with the screen on. */
    private fun showOverLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService<KeyguardManager>()?.requestDismissKeyguard(this, null)
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/**
 * The alarm UI: destination name, live distance and a huge dismiss button.
 */
@Composable
private fun AlarmScreen(
    status: TrackingStatus,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val destinationName: String
    val distanceMeters: Float?
    when (status) {
        is TrackingStatus.Alarming -> {
            destinationName = status.destination.name
            distanceMeters = status.distanceMeters
        }
        is TrackingStatus.Tracking -> {
            destinationName = status.destination.name
            distanceMeters = status.distanceMeters
        }
        TrackingStatus.Idle -> {
            destinationName = ""
            distanceMeters = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.height(96.dp).fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.alarm_headline),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = stringResource(R.string.alarm_approaching),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = destinationName,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (distanceMeters != null) {
            Text(
                text = stringResource(
                    R.string.alarm_distance_now,
                    formatDistance(context, distanceMeters),
                ),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White,
            ),
            modifier = Modifier
                .padding(top = 48.dp)
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Text(text = stringResource(R.string.alarm_dismiss), fontSize = 24.sp)
        }
    }
}
