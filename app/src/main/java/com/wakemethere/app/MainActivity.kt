package com.wakemethere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.service.TrackingStateHolder
import com.wakemethere.app.ui.home.HomeScreen
import com.wakemethere.app.ui.map.MapScreen
import com.wakemethere.app.ui.onboarding.OnboardingScreen
import com.wakemethere.app.ui.settings.SettingsScreen
import com.wakemethere.app.ui.theme.WakeMeThereTheme
import com.wakemethere.app.ui.trips.TripHistoryScreen
import com.wakemethere.app.ui.trips.TripSummaryScreen
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

    val justCompletedTripId = stateHolder.justCompletedTripId

    fun consumeCompletedTrip() = stateHolder.consumeCompletedTrip()
}

/**
 * Single-activity host for all regular screens (the alarm has its own
 * activity so it can appear over the lock screen).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WakeMeThereTheme {
                val viewModel: MainViewModel = hiltViewModel()
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

                NavHost(navController = navController, startDestination = startRoute) {
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
                            onOpenPermissions = { navController.navigate(Routes.ONBOARDING) },
                        )
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
            }
        }
    }
}
