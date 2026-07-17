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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.wakemethere.app.ui.components.rememberPulse
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

/** One onboarding page: a welcome intro or a single permission. */
private data class OnboardPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
    val actionRes: Int,
    /** null for the welcome page (always "complete"). */
    val isGranted: (Context) -> Boolean,
    val request: (OnboardStepActions) -> Unit,
)

/** Runtime hooks each page can use to fire its system dialog. */
private class OnboardStepActions(
    val context: Context,
    val launchLocation: () -> Unit,
    val launchNotifications: () -> Unit,
)

/**
 * Full-screen, step-by-step onboarding in the Liquid Transit style:
 * one permission per page, each with an animated glass illustration
 * (pulsing rings + big icon), a clear Persian explanation, a capsule
 * action button and progress dots. Pages advance automatically when the
 * permission is granted; every step can be skipped with "later".
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Re-check grant states whenever we come back from a system dialog/screen.
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    val actions = remember {
        OnboardStepActions(
            context = context,
            launchLocation = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
            launchNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }

    val pages = remember {
        listOf(
            OnboardPage(
                icon = Icons.Default.Train,
                titleRes = R.string.onb_welcome_title,
                bodyRes = R.string.onb_welcome_body,
                actionRes = R.string.onb_welcome_action,
                isGranted = { true },
                request = {},
            ),
            OnboardPage(
                icon = Icons.Default.MyLocation,
                titleRes = R.string.perm_location_title,
                bodyRes = R.string.perm_location_desc,
                actionRes = R.string.onb_allow,
                isGranted = ::hasLocationPermission,
                request = { it.launchLocation() },
            ),
            OnboardPage(
                icon = Icons.Default.NotificationsActive,
                titleRes = R.string.perm_notifications_title,
                bodyRes = R.string.perm_notifications_desc,
                actionRes = R.string.onb_allow,
                isGranted = ::hasNotificationPermission,
                request = { it.launchNotifications() },
            ),
            OnboardPage(
                icon = Icons.Default.Alarm,
                titleRes = R.string.perm_fullscreen_title,
                bodyRes = R.string.perm_fullscreen_desc,
                actionRes = R.string.onb_allow,
                isGranted = ::canUseFullScreenIntent,
                request = { openFullScreenIntentSettings(it.context) },
            ),
            OnboardPage(
                icon = Icons.Default.BatteryChargingFull,
                titleRes = R.string.perm_battery_title,
                bodyRes = R.string.perm_battery_desc,
                actionRes = R.string.onb_allow,
                isGranted = ::isBatteryExempt,
                request = { requestBatteryExemption(it.context) },
            ),
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLast = pagerState.currentPage == pages.lastIndex
    val currentGranted = remember(refresh, pagerState.currentPage) {
        pages[pagerState.currentPage].isGranted(context)
    }

    // Auto-advance shortly after the current page's permission is granted.
    LaunchedEffect(currentGranted, pagerState.currentPage) {
        if (currentGranted && pagerState.currentPage in 1 until pages.lastIndex) {
            kotlinx.coroutines.delay(650)
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // Skip, top-end.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { viewModel.markDone(onFinished) }) {
                    Text(stringResource(R.string.perm_skip))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { index ->
                val page = pages[index]
                val granted = remember(refresh) { page.isGranted(context) }
                OnboardPageContent(page = page, granted = granted && index > 0)
            }

            // Actions + progress dots.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val page = pages[pagerState.currentPage]
                Button(
                    onClick = {
                        when {
                            pagerState.currentPage == 0 ->
                                scope.launch { pagerState.animateScrollToPage(1) }
                            !currentGranted -> page.request(actions)
                            isLast -> viewModel.markDone(onFinished)
                            else -> scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        text = stringResource(
                            when {
                                pagerState.currentPage == 0 -> page.actionRes
                                currentGranted && isLast -> R.string.onb_finish
                                currentGranted -> R.string.onb_next
                                else -> page.actionRes
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            if (isLast) viewModel.markDone(onFinished)
                            else scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.onb_later))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(pages.size) { i ->
                        val active = i == pagerState.currentPage
                        val w by animateFloatAsState(
                            targetValue = if (active) 26f else 8f,
                            animationSpec = tween(250),
                            label = "dot",
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(w.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** The illustrated body of one page: pulsing rings, glass tile, texts. */
@Composable
private fun OnboardPageContent(page: OnboardPage, granted: Boolean) {
    val pulse = rememberPulse(periodMillis = 2600)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Illustration: two expanding rings behind a floating glass tile.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(0.75f + pulse * 0.55f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = (1f - pulse) * 0.18f)),
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(0.85f + pulse * 0.35f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = (1f - pulse) * 0.25f)),
            )
            GlassCard(shape = RoundedCornerShape(36.dp), modifier = Modifier.size(132.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp),
                    )
                }
            }
            if (granted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.perm_granted),
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(28.dp)
                        .size(34.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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

private fun canUseFullScreenIntent(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true

private fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

// --- Settings intents ---------------------------------------------------------

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
