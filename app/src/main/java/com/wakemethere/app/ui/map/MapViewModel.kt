package com.wakemethere.app.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemethere.app.data.datastore.AppSettings
import com.wakemethere.app.data.datastore.SettingsStore
import com.wakemethere.app.data.remote.NominatimClient
import com.wakemethere.app.data.remote.PlaceResult
import com.wakemethere.app.data.repository.DestinationRepository
import com.wakemethere.app.domain.model.Destination
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

/** UI state for the destination-picking map screen. */
data class MapUiState(
    /** Where to initially center the map (user's last known position). */
    val startLatitude: Double = DEFAULT_CENTER_LAT,
    val startLongitude: Double = DEFAULT_CENTER_LON,
    /** The dropped pin, or null before the user picked a point. */
    val pickedLatitude: Double? = null,
    val pickedLongitude: Double? = null,
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
 * Drives the map screen: pin position, radius, debounced Nominatim search
 * and arming the tracking service.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val nominatim: NominatimClient,
    private val repository: DestinationRepository,
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
        // Debounced search per the Nominatim usage policy (max ~1 req/s).
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { query -> runSearch(query) }
        }
    }

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

    fun onMapTapped(latitude: Double, longitude: Double) {
        _state.update { it.copy(pickedLatitude = latitude, pickedLongitude = longitude) }
    }

    fun onSearchResultPicked(result: PlaceResult) {
        _state.update {
            it.copy(
                pickedLatitude = result.latitude,
                pickedLongitude = result.longitude,
                searchQuery = result.displayName,
                searchResults = emptyList(),
                favoriteName = it.favoriteName.ifBlank {
                    result.displayName.substringBefore(",").take(MAX_NAME_LENGTH)
                },
            )
        }
    }

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
                name = current.favoriteName.ifBlank { DEFAULT_DESTINATION_NAME },
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
