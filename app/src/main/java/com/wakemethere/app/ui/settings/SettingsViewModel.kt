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
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

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
}
