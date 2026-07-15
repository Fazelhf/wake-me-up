package com.wakemethere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.ui.home.HomeScreen
import com.wakemethere.app.ui.map.MapScreen
import com.wakemethere.app.ui.onboarding.OnboardingScreen
import com.wakemethere.app.ui.settings.SettingsScreen
import com.wakemethere.app.ui.theme.WakeMeThereTheme
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
}

/**
 * Exposes whether onboarding has been completed, to pick the start
 * destination (null while still loading from DataStore).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsStore: SettingsStore,
) : ViewModel() {
    val onboardingDone = settingsStore.settings
        .map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
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

                // Wait for DataStore before choosing the start destination.
                val startRoute = when (onboardingDone) {
                    null -> return@WakeMeThereTheme
                    true -> Routes.HOME
                    false -> Routes.ONBOARDING
                }

                val navController = rememberNavController()
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
                }
            }
        }
    }
}
