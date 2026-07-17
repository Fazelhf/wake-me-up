package com.wakemethere.app.ui.planner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.data.transit.TransitRepository
import com.wakemethere.app.domain.model.Destination
import com.wakemethere.app.domain.model.TransitSystem
import com.wakemethere.app.domain.route.RoutePlanner
import com.wakemethere.app.location.LocationClient
import com.wakemethere.app.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the origin→destination route planner: loads both transit networks
 * into a [RoutePlanner], resolves "my location" to the nearest station and
 * exposes the best plan plus an alternative.
 */
@HiltViewModel
class RoutePlannerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val transitRepository: TransitRepository,
    private val settingsStore: SettingsStore,
    private val locationClient: LocationClient,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        /** All logical stations of both systems, for the pickers. */
        val stations: List<RoutePlanner.StationOption> = emptyList(),
        /** Origin: either a station id, or "my location" when null. */
        val originId: String? = null,
        val originIsMyLocation: Boolean = true,
        val destinationId: String? = null,
        /** Walk from the user's position to the resolved origin station. */
        val originWalk: OriginWalk? = null,
        val planning: Boolean = false,
        /** True once a planning attempt finished (plans may be empty). */
        val planned: Boolean = false,
        val plans: List<RoutePlanner.RoutePlan> = emptyList(),
        val locationError: Boolean = false,
    )

    data class OriginWalk(val station: RoutePlanner.StationOption, val meters: Double)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var planner: RoutePlanner? = null

    init {
        viewModelScope.launch {
            val networks = listOfNotNull(
                runCatching { transitRepository.network(TransitSystem.METRO) }.getOrNull(),
                runCatching { transitRepository.network(TransitSystem.BRT) }.getOrNull(),
            )
            val built = RoutePlanner(networks)
            planner = built
            _state.update { it.copy(loading = false, stations = built.stations) }
        }
    }

    fun station(id: String?): RoutePlanner.StationOption? =
        id?.let { planner?.station(it) }

    fun onOriginPicked(stationId: String?) {
        // null means "my location".
        _state.update {
            it.copy(
                originId = stationId,
                originIsMyLocation = stationId == null,
                originWalk = null,
                plans = emptyList(),
                planned = false,
                locationError = false,
            )
        }
    }

    fun onDestinationPicked(stationId: String) {
        _state.update {
            it.copy(destinationId = stationId, plans = emptyList(), planned = false)
        }
    }

    fun onSwap() {
        _state.update {
            val newOrigin = it.destinationId
            val newDestination = if (it.originIsMyLocation) {
                it.originWalk?.station?.id
            } else {
                it.originId
            }
            it.copy(
                originId = newOrigin,
                originIsMyLocation = newOrigin == null,
                destinationId = newDestination,
                originWalk = null,
                plans = emptyList(),
                planned = false,
                locationError = false,
            )
        }
    }

    fun onPlan() {
        val current = _state.value
        val planner = planner ?: return
        val destination = current.destinationId ?: return

        viewModelScope.launch {
            _state.update { it.copy(planning = true, planned = false, locationError = false) }

            var originWalk: OriginWalk? = null
            val origin = if (current.originIsMyLocation) {
                val fix = runCatching { locationClient.lastKnown() }.getOrNull()
                val nearest = fix?.let {
                    planner.nearest(it.latitude, it.longitude, limit = 1).firstOrNull()
                }
                if (nearest == null) {
                    _state.update { it.copy(planning = false, locationError = true) }
                    return@launch
                }
                originWalk = OriginWalk(nearest.first, nearest.second)
                nearest.first.id
            } else {
                current.originId ?: return@launch
            }

            val plans = if (origin == destination) {
                emptyList()
            } else {
                planner.planWithAlternative(origin, destination)
            }
            _state.update {
                it.copy(
                    planning = false,
                    planned = true,
                    plans = plans,
                    originWalk = originWalk,
                )
            }
        }
    }

    /** Arms the wake-up alarm for the planned destination station. */
    fun onArmDestination(onArmed: () -> Unit) {
        val destination = station(_state.value.destinationId) ?: return
        viewModelScope.launch {
            val radius = settingsStore.current().defaultRadiusMeters
            TrackingService.start(
                appContext,
                Destination(
                    name = destination.name,
                    latitude = destination.latitude,
                    longitude = destination.longitude,
                    radiusMeters = radius,
                    transitType = destination.system.name,
                    lineName = destination.lineNames.firstOrNull(),
                ),
            )
            onArmed()
        }
    }
}
