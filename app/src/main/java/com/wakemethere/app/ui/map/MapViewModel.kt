package com.wakemethere.app.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemethere.app.data.datastore.AppSettings
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.data.remote.NominatimClient
import com.wakemethere.app.data.remote.PlaceResult
import com.wakemethere.app.data.repository.DestinationRepository
import com.wakemethere.app.data.transit.TransitRepository
import com.wakemethere.app.domain.model.Destination
import com.wakemethere.app.domain.model.TransitNetwork
import com.wakemethere.app.domain.model.TransitSystem
import com.wakemethere.app.location.LocationClient
import com.wakemethere.app.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How the destination is being picked: from the Metro network, the BRT
 * network, or freely anywhere on the map.
 */
enum class PickerMode {
    METRO,
    BRT,
    FREE,
}

/** UI state for the destination-picking map screen. */
data class MapUiState(
    /** Where to initially center the map (user's last known position). */
    val startLatitude: Double = DEFAULT_CENTER_LAT,
    val startLongitude: Double = DEFAULT_CENTER_LON,
    val mode: PickerMode = PickerMode.METRO,
    /** Loaded network for the current transit mode, null in FREE mode. */
    val network: TransitNetwork? = null,
    /** Selected station id (transit modes only). */
    val selectedStationId: String? = null,
    /** Line name/color of the selected station, for the bottom panel. */
    val selectedLineName: String? = null,
    val selectedLineColor: Int? = null,
    /** The chosen destination point, or null before the user picked one. */
    val pickedLatitude: Double? = null,
    val pickedLongitude: Double? = null,
    /** Display name of the chosen destination (station name or free-form). */
    val pickedName: String = "",
    val radiusMeters: Int = AppSettings.DEFAULT_RADIUS_METERS,
    val searchQuery: String = "",
    val searchResults: List<PlaceResult> = emptyList(),
    val searching: Boolean = false,
    val searchError: Boolean = false,
    val saveAsFavorite: Boolean = false,
    val favoriteName: String = "",
) {
    val hasPin: Boolean get() = pickedLatitude != null && pickedLongitude != null

    companion object {
        // Tehran city center: sensible fallback before any GPS fix exists.
        const val DEFAULT_CENTER_LAT = 35.6892
        const val DEFAULT_CENTER_LON = 51.3890
    }
}

/**
 * Drives the map screen: Metro/BRT station picking, free pin drop, radius,
 * debounced Nominatim search and arming the tracking service.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val nominatim: NominatimClient,
    private val repository: DestinationRepository,
    private val transitRepository: TransitRepository,
    private val settingsStore: SettingsStore,
    private val locationClient: LocationClient,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _state.update { it.copy(radiusMeters = settingsStore.current().defaultRadiusMeters) }
        }
        viewModelScope.launch {
            locationClient.lastKnown()?.let { fix ->
                _state.update {
                    it.copy(startLatitude = fix.latitude, startLongitude = fix.longitude)
                }
            }
        }
        // Default mode is METRO: load its network right away.
        loadNetwork(TransitSystem.METRO)

        // Debounced search per the Nominatim usage policy (max ~1 req/s).
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { query -> runSearch(query) }
        }
    }

    // --- Mode & stations -----------------------------------------------------

    fun onModeChanged(mode: PickerMode) {
        if (mode == _state.value.mode) return
        _state.update {
            it.copy(
                mode = mode,
                network = null,
                selectedStationId = null,
                selectedLineName = null,
                selectedLineColor = null,
                pickedLatitude = null,
                pickedLongitude = null,
                pickedName = "",
                favoriteName = "",
                searchResults = emptyList(),
            )
        }
        when (mode) {
            PickerMode.METRO -> loadNetwork(TransitSystem.METRO)
            PickerMode.BRT -> loadNetwork(TransitSystem.BRT)
            PickerMode.FREE -> Unit
        }
    }

    private fun loadNetwork(system: TransitSystem) {
        viewModelScope.launch {
            val network = runCatching { transitRepository.network(system) }.getOrNull()
            _state.update { current ->
                // Ignore stale loads after another mode switch.
                val expected = when (current.mode) {
                    PickerMode.METRO -> TransitSystem.METRO
                    PickerMode.BRT -> TransitSystem.BRT
                    PickerMode.FREE -> null
                }
                if (network != null && network.system == expected) {
                    current.copy(network = network)
                } else {
                    current
                }
            }
        }
    }

    fun onStationTapped(stationId: String) {
        val network = _state.value.network ?: return
        val (line, station) = network.findStation(stationId) ?: return
        _state.update {
            it.copy(
                selectedStationId = station.id,
                selectedLineName = line.name,
                selectedLineColor = line.color,
                pickedLatitude = station.latitude,
                pickedLongitude = station.longitude,
                pickedName = station.name,
                favoriteName = station.name,
            )
        }
    }

    /** Free-mode map tap; ignored in transit modes (stations only there). */
    fun onMapTapped(latitude: Double, longitude: Double) {
        if (_state.value.mode != PickerMode.FREE) return
        _state.update {
            it.copy(
                pickedLatitude = latitude,
                pickedLongitude = longitude,
                pickedName = "",
                selectedStationId = null,
                selectedLineName = null,
                selectedLineColor = null,
            )
        }
    }

    // --- Search (FREE mode) --------------------------------------------------

    private suspend fun runSearch(query: String) {
        if (query.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(searchResults = emptyList(), searching = false, searchError = false) }
            return
        }
        _state.update { it.copy(searching = true, searchError = false) }
        runCatching { nominatim.search(query) }
            .onSuccess { results ->
                _state.update { it.copy(searchResults = results, searching = false) }
            }
            .onFailure {
                _state.update { it.copy(searchResults = emptyList(), searching = false, searchError = true) }
            }
    }

    fun onQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        queryFlow.value = query.trim()
    }

    fun onSearchResultPicked(result: PlaceResult) {
        val shortName = result.displayName.substringBefore(",").take(MAX_NAME_LENGTH)
        _state.update {
            it.copy(
                pickedLatitude = result.latitude,
                pickedLongitude = result.longitude,
                pickedName = shortName,
                searchQuery = result.displayName,
                searchResults = emptyList(),
                favoriteName = it.favoriteName.ifBlank { shortName },
            )
        }
    }

    // --- Radius / favorite / arm ----------------------------------------------

    fun onRadiusChanged(radiusMeters: Int) {
        _state.update { it.copy(radiusMeters = radiusMeters) }
    }

    fun onSaveAsFavoriteChanged(save: Boolean) {
        _state.update { it.copy(saveAsFavorite = save) }
    }

    fun onFavoriteNameChanged(name: String) {
        _state.update { it.copy(favoriteName = name.take(MAX_NAME_LENGTH)) }
    }

    /**
     * Optionally saves the favorite, then arms the tracking service.
     * Invokes [onArmed] once the service has been started.
     */
    fun onStartTracking(onArmed: () -> Unit) {
        val current = _state.value
        val lat = current.pickedLatitude ?: return
        val lon = current.pickedLongitude ?: return

        viewModelScope.launch {
            var destination = Destination(
                name = current.favoriteName.ifBlank {
                    current.pickedName.ifBlank { DEFAULT_DESTINATION_NAME }
                },
                latitude = lat,
                longitude = lon,
                radiusMeters = current.radiusMeters,
            )
            if (current.saveAsFavorite) {
                val id = repository.save(destination)
                destination = destination.copy(id = id)
            }
            TrackingService.start(appContext, destination)
            onArmed()
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 700L
        const val MIN_QUERY_LENGTH = 3
        const val MAX_NAME_LENGTH = 60
        const val DEFAULT_DESTINATION_NAME = "Destination"
    }
}
