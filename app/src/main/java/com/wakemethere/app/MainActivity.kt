package com.wakemethere.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wakemethere.app.ui.components.glassModifier
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.data.datastore.ThemeMode
import com.wakemethere.app.service.TrackingStateHolder
import com.wakemethere.app.ui.home.HomeScreen
import com.wakemethere.app.ui.map.MapScreen
import com.wakemethere.app.ui.onboarding.OnboardingScreen
import com.wakemethere.app.ui.settings.PermissionHealthScreen
import com.wakemethere.app.ui.settings.SettingsScreen
import com.wakemethere.app.ui.theme.WakeMeThereTheme
import com.wakemethere.app.ui.trips.TripHistoryScreen
import com.wakemethere.app.ui.trips.TripSummaryScreen
import com.wakemethere.app.util.AppLocale
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Navigation route constants. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val HEALTH = "health"
    const val SUMMARY = "summary" // summary/{tripId}
    fun summary(tripId: Long) = "summary/$tripId"
}

/**
 * Exposes onboarding completion and the "just arrived" trip event, so the
 * host can pick the start screen and auto-open the Trip Summary on arrival.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsStore: SettingsStore,
    private val stateHolder: TrackingStateHolder,
) : ViewModel() {
    val onboardingDone = settingsStore.settings
        .map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    val themeMode = settingsStore.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = ThemeMode.SYSTEM)

    val isTracking = stateHolder.status
        .map { it !is com.wakemethere.app.domain.model.TrackingStatus.Idle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    val justCompletedTripId = stateHolder.justCompletedTripId

    fun consumeCompletedTrip() = stateHolder.consumeCompletedTrip()
}

/**
 * Single-activity host for all regular screens (the alarm has its own
 * activity so it can appear over the lock screen).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        // Apply the stored language (default Persian) before anything renders.
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            WakeMeThereTheme(darkTheme = darkTheme) {
                val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
                val completedTripId by viewModel.justCompletedTripId.collectAsStateWithLifecycle()

                // Wait for DataStore before choosing the start destination.
                val startRoute = when (onboardingDone) {
                    null -> return@WakeMeThereTheme
                    true -> Routes.HOME
                    false -> Routes.ONBOARDING
                }

                val navController = rememberNavController()

                // On arrival, open the Trip Summary once (then consume so it
                // doesn't reopen on recomposition or return navigation).
                LaunchedEffect(completedTripId, onboardingDone) {
                    val id = completedTripId
                    if (id != null && onboardingDone == true) {
                        navController.navigate(Routes.summary(id))
                        viewModel.consumeCompletedTrip()
                    }
                }

                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    enterTransition = {
                        fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 24 }
                    },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(260)) },
                    popExitTransition = {
                        fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 24 }
                    },
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(
                            onFinished = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onSetAlarm = { navController.navigate(Routes.MAP) },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        )
                    }
                    composable(Routes.MAP) {
                        MapScreen(onTrackingStarted = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenPermissions = { navController.navigate(Routes.HEALTH) },
                        )
                    }
                    composable(Routes.HEALTH) {
                        PermissionHealthScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.HISTORY) {
                        TripHistoryScreen(
                            onBack = { navController.popBackStack() },
                            onOpenTrip = { id -> navController.navigate(Routes.summary(id)) },
                        )
                    }
                    composable(
                        route = "${Routes.SUMMARY}/{tripId}",
                        arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("tripId") ?: 0L
                        TripSummaryScreen(
                            tripId = id,
                            onDone = {
                                // Return to Home, clearing the summary from the back stack.
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            },
                        )
                    }
                }

                // Glass bottom navigation on the three main tabs. Hidden on
                // Home while tracking (the Stop capsule takes its place).
                val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
                if (currentRoute in setOf(Routes.HOME, Routes.HISTORY, Routes.SETTINGS) &&
                    !(currentRoute == Routes.HOME && isTracking)
                ) {
                    GlassBottomNav(
                        currentRoute = currentRoute,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onNavigate = { route ->
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
                }
            }
        }
    }
}

/** Floating glass bottom navigation bar (Home / Trips / Settings). */
@Composable
private fun GlassBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Tab(val route: String, val icon: ImageVector, val labelRes: Int)

    val tabs = listOf(
        Tab(Routes.HOME, Icons.Default.Home, R.string.nav_home),
        Tab(Routes.HISTORY, Icons.Default.History, R.string.nav_trips),
        Tab(Routes.SETTINGS, Icons.Default.Settings, R.string.nav_settings),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .then(glassModifier(RoundedCornerShape(28.dp)))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == currentRoute
            // Animated selection: pill color, content tint and a gentle pop.
            val pill by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                else androidx.compose.ui.graphics.Color.Transparent,
                animationSpec = tween(250), label = "pill",
            )
            val tint by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250), label = "tint",
            )
            val pop by animateFloatAsState(
                if (selected) 1.08f else 1f,
                animationSpec = spring(dampingRatio = 0.5f), label = "pop",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(pop)
                    .clip(RoundedCornerShape(20.dp))
                    .background(pill)
                    .clickable { onNavigate(tab.route) }
                    .padding(horizontal = 22.dp, vertical = 7.dp),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
            }
        }
    }
}
