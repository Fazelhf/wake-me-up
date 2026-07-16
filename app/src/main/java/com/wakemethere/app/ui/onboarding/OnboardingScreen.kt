package com.wakemethere.app.ui.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemethere.app.R
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.ui.components.AmbientBackground
import com.wakemethere.app.ui.components.GlassCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Marks onboarding as completed in DataStore. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    fun markDone(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsStore.setOnboardingDone()
            onDone()
        }
    }
}

/**
 * Step-by-step permission flow. Each step explains WHY the permission is
 * needed before showing the system dialog, and every denial path keeps a
 * button available to open the relevant settings screen.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bumped on resume and after each permission result to re-check statuses
    // (the user may return from system settings at any point).
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationGranted = remember(refresh) { hasLocationPermission(context) }
    val notificationsGranted = remember(refresh) { hasNotificationPermission(context) }
    val fullScreenGranted = remember(refresh) { canUseFullScreenIntent(context) }
    val batteryExempt = remember(refresh) { isBatteryExempt(context) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Box(modifier = Modifier.fillMaxSize()) {
    AmbientBackground()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        PermissionStep(
            title = stringResource(R.string.perm_location_title),
            description = stringResource(R.string.perm_location_desc),
            granted = locationGranted,
            deniedHint = stringResource(R.string.perm_denied_location),
            onGrant = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
            onOpenSettings = { openAppSettings(context) },
        )

        PermissionStep(
            title = stringResource(R.string.perm_notifications_title),
            description = stringResource(R.string.perm_notifications_desc),
            granted = notificationsGranted,
            deniedHint = stringResource(R.string.perm_denied_notifications),
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenSettings = { openAppSettings(context) },
        )

        PermissionStep(
            title = stringResource(R.string.perm_fullscreen_title),
            description = stringResource(R.string.perm_fullscreen_desc),
            granted = fullScreenGranted,
            deniedHint = stringResource(R.string.perm_denied_fullscreen),
            onGrant = { openFullScreenIntentSettings(context) },
            onOpenSettings = { openFullScreenIntentSettings(context) },
        )

        PermissionStep(
            title = stringResource(R.string.perm_battery_title),
            description = stringResource(R.string.perm_battery_desc),
            granted = batteryExempt,
            deniedHint = null,
            onGrant = { requestBatteryExemption(context) },
            onOpenSettings = { requestBatteryExemption(context) },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.markDone(onFinished) },
            enabled = locationGranted,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.perm_continue), fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = { viewModel.markDone(onFinished) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.perm_skip))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    }
}

/** One expandable permission card with grant/settings actions. */
@Composable
private fun PermissionStep(
    title: String,
    description: String,
    granted: Boolean,
    deniedHint: String?,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (granted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.perm_granted),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!granted) {
                if (deniedHint != null) {
                    Text(
                        text = deniedHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onGrant) {
                        Text(stringResource(R.string.perm_grant))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.perm_open_settings))
                    }
                }
            }
        }
    }
}

// --- Permission status helpers ----------------------------------------------

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** On API 34+ full-screen intents need a per-app grant; below it's automatic. */
private fun canUseFullScreenIntent(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true

private fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

// --- Settings intents ---------------------------------------------------------

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
    )
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }
}

private fun requestBatteryExemption(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        )
    )
}
