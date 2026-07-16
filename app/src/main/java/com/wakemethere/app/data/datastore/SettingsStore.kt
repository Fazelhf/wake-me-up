package com.wakemethere.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App-wide theme preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User-configurable settings snapshot. */
data class AppSettings(
    /** When false, use the custom sound in [customSoundUri] if set. */
    val useDefaultAlarmSound: Boolean = true,
    /** URI string of a user-picked ringtone, or null. */
    val customSoundUri: String? = null,
    val vibrationEnabled: Boolean = true,
    val defaultRadiusMeters: Int = DEFAULT_RADIUS_METERS,
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 500
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 2000
    }
}

/**
 * DataStore-backed app settings (alarm sound, vibration, default radius,
 * onboarding completion flag).
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            useDefaultAlarmSound = prefs[KEY_USE_DEFAULT_SOUND] ?: true,
            customSoundUri = prefs[KEY_CUSTOM_SOUND_URI],
            vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
            defaultRadiusMeters = prefs[KEY_DEFAULT_RADIUS] ?: AppSettings.DEFAULT_RADIUS_METERS,
            onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false,
            themeMode = runCatching { ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setUseDefaultAlarmSound(useDefault: Boolean) {
        context.settingsDataStore.edit { it[KEY_USE_DEFAULT_SOUND] = useDefault }
    }

    suspend fun setCustomSoundUri(uri: String?) {
        context.settingsDataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_CUSTOM_SOUND_URI) else prefs[KEY_CUSTOM_SOUND_URI] = uri
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_VIBRATION] = enabled }
    }

    suspend fun setDefaultRadiusMeters(radius: Int) {
        context.settingsDataStore.edit { it[KEY_DEFAULT_RADIUS] = radius }
    }

    suspend fun setOnboardingDone() {
        context.settingsDataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    private companion object {
        val KEY_USE_DEFAULT_SOUND = booleanPreferencesKey("use_default_sound")
        val KEY_CUSTOM_SOUND_URI = stringPreferencesKey("custom_sound_uri")
        val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
        val KEY_DEFAULT_RADIUS = intPreferencesKey("default_radius")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
