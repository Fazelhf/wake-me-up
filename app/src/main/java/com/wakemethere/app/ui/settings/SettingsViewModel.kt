package com.wakemethere.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemethere.app.data.datastore.AppSettings
import com.wakemethere.app.data.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes and mutates the persisted app settings.
 */
/** State of the in-app OSM station-data update. */
enum class DataUpdateState { IDLE, RUNNING, DONE, FAILED }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val transitUpdater: com.wakemethere.app.data.transit.TransitUpdater,
) : ViewModel() {

    private val _updateState =
        kotlinx.coroutines.flow.MutableStateFlow(DataUpdateState.IDLE)
    val updateState: StateFlow<DataUpdateState> = _updateState

    /** Downloads exact station data from OpenStreetMap on the device. */
    fun updateStations() {
        if (_updateState.value == DataUpdateState.RUNNING) return
        _updateState.value = DataUpdateState.RUNNING
        viewModelScope.launch {
            _updateState.value = runCatching { transitUpdater.updateAll() }
                .fold({ DataUpdateState.DONE }, { DataUpdateState.FAILED })
        }
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setUseDefaultSound(useDefault: Boolean) {
        viewModelScope.launch { settingsStore.setUseDefaultAlarmSound(useDefault) }
    }

    fun setCustomSoundUri(uri: String?) {
        viewModelScope.launch {
            settingsStore.setCustomSoundUri(uri)
            if (uri != null) settingsStore.setUseDefaultAlarmSound(false)
        }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setVibrationEnabled(enabled) }
    }

    fun setDefaultRadius(radiusMeters: Int) {
        viewModelScope.launch { settingsStore.setDefaultRadiusMeters(radiusMeters) }
    }

    fun setThemeMode(mode: com.wakemethere.app.data.datastore.ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }
}
