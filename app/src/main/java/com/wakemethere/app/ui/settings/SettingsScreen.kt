package com.wakemethere.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakemethere.app.R
import com.wakemethere.app.data.datastore.AppSettings
import com.wakemethere.app.data.datastore.ThemeMode
import com.wakemethere.app.util.AppLocale

/**
 * Settings: default vs custom alarm sound, vibration toggle and the default
 * trigger radius, plus a shortcut to the permission overview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // System ringtone picker for the custom alarm sound.
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? =
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setCustomSoundUri(uri?.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Alarm sound: default vs custom.
            Text(
                text = stringResource(R.string.settings_sound_title),
                style = MaterialTheme.typography.titleMedium,
            )
            SettingRow(
                title = stringResource(R.string.settings_sound_default),
                checked = settings.useDefaultAlarmSound,
                onCheckedChange = viewModel::setUseDefaultSound,
            )
            if (!settings.useDefaultAlarmSound) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_ALARM,
                            )
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                settings.customSoundUri?.let(Uri::parse),
                            )
                        }
                        ringtoneLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_sound_pick))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Vibration.
            Text(
                text = stringResource(R.string.settings_vibration_title),
                style = MaterialTheme.typography.titleMedium,
            )
            SettingRow(
                title = stringResource(R.string.settings_vibration_desc),
                checked = settings.vibrationEnabled,
                onCheckedChange = viewModel::setVibration,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Default radius. Local slider state so dragging is smooth; the
            // value is persisted when the gesture ends.
            var sliderRadius by remember(settings.defaultRadiusMeters) {
                mutableFloatStateOf(settings.defaultRadiusMeters.toFloat())
            }
            Text(
                text = stringResource(R.string.settings_default_radius_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_default_radius_value, sliderRadius.toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = sliderRadius,
                onValueChange = { sliderRadius = it },
                onValueChangeFinished = { viewModel.setDefaultRadius(sliderRadius.toInt()) },
                valueRange = AppSettings.MIN_RADIUS_METERS.toFloat()..AppSettings.MAX_RADIUS_METERS.toFloat(),
                steps = 18,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Language.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val currentLang = AppLocale.current(ctx)
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = currentLang == AppLocale.PERSIAN,
                    onClick = { AppLocale.set(ctx, AppLocale.PERSIAN) },
                    label = { Text(stringResource(R.string.settings_language_fa)) },
                )
                FilterChip(
                    selected = currentLang == AppLocale.ENGLISH,
                    onClick = { AppLocale.set(ctx, AppLocale.ENGLISH) },
                    label = { Text(stringResource(R.string.settings_language_en)) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Theme.
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    label = { Text(stringResource(R.string.settings_theme_system)) },
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    label = { Text(stringResource(R.string.settings_theme_light)) },
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    label = { Text(stringResource(R.string.settings_theme_dark)) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Permission overview shortcut.
            Text(
                text = stringResource(R.string.settings_permissions_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = onOpenPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.settings_permissions_desc))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Exact station data from OpenStreetMap (runs on the device).
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            Text(
                text = stringResource(R.string.settings_data_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    when (updateState) {
                        DataUpdateState.RUNNING -> R.string.settings_data_running
                        DataUpdateState.DONE -> R.string.settings_data_done
                        DataUpdateState.FAILED -> R.string.settings_data_failed
                        DataUpdateState.IDLE -> R.string.settings_data_desc
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = when (updateState) {
                    DataUpdateState.FAILED -> MaterialTheme.colorScheme.error
                    DataUpdateState.DONE -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedButton(
                onClick = viewModel::updateStations,
                enabled = updateState != DataUpdateState.RUNNING,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.settings_data_action))
            }

            // Creator credit.
            Text(
                text = stringResource(R.string.settings_credit),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 12.dp),
            )
        }
    }
}

/** A labeled switch row. */
@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
