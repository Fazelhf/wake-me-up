package com.wakemethere.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakemethere.app.R
import com.wakemethere.app.domain.model.Destination
import com.wakemethere.app.domain.model.TrackingStatus
import com.wakemethere.app.util.formatDistance

/**
 * Home screen: big "Set Alarm" entry point, armed-alarm status card and the
 * favorites list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSetAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val status by viewModel.trackingStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_settings),
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
                .padding(16.dp),
        ) {
            when (val current = status) {
                is TrackingStatus.Tracking -> ArmedCard(
                    destination = current.destination,
                    distanceMeters = current.distanceMeters,
                    signalWeak = current.signalWeak,
                    onCancel = viewModel::cancelTracking,
                )
                is TrackingStatus.Alarming -> ArmedCard(
                    destination = current.destination,
                    distanceMeters = current.distanceMeters,
                    signalWeak = false,
                    onCancel = viewModel::cancelTracking,
                )
                TrackingStatus.Idle -> Button(
                    onClick = onSetAlarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_set_alarm),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.home_favorites_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_no_favorites),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(favorites, key = Destination::id) { favorite ->
                        FavoriteRow(
                            favorite = favorite,
                            armEnabled = status is TrackingStatus.Idle,
                            onArm = { viewModel.armFavorite(favorite) },
                            onDelete = { viewModel.deleteFavorite(favorite) },
                        )
                    }
                }
            }
        }
    }
}

/** Card shown while an alarm is armed: destination, live distance, cancel. */
@Composable
private fun ArmedCard(
    destination: Destination,
    distanceMeters: Float?,
    signalWeak: Boolean,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_armed_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = destination.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    signalWeak -> stringResource(R.string.notif_signal_weak)
                    distanceMeters != null -> stringResource(
                        R.string.home_armed_distance,
                        formatDistance(context, distanceMeters),
                    )
                    else -> stringResource(R.string.home_armed_waiting_fix)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.home_cancel_tracking))
            }
        }
    }
}

/** One favorite destination row with arm and delete actions. */
@Composable
private fun FavoriteRow(
    favorite: Destination,
    armEnabled: Boolean,
    onArm: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(favorite.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${favorite.radiusMeters} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onArm, enabled = armEnabled) {
                Text(stringResource(R.string.home_arm_favorite))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_delete_favorite),
                )
            }
        }
    }
}
