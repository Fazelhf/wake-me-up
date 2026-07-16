package com.wakemethere.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wakemethere.app.R
import com.wakemethere.app.domain.model.Trip
import com.wakemethere.app.ui.components.AmbientBackground
import com.wakemethere.app.ui.components.GlassCard
import com.wakemethere.app.ui.components.ScreenMargin
import com.wakemethere.app.ui.components.glassModifier

/**
 * Trip Summary (Liquid Transit): the success state shown on arrival — a check,
 * a departure→arrival timeline, duration/distance/on-time stats, and Done.
 */
@Composable
fun TripSummaryScreen(
    tripId: Long,
    onDone: () -> Unit,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    var trip by remember { mutableStateOf<Trip?>(null) }
    LaunchedEffect(tripId) { trip = viewModel.tripById(tripId) }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Success indicator.
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.summary_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.summary_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            val current = trip
            if (current != null) {
                GlassCard(shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Destination + line.
                        Text(
                            text = current.destinationName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        current.lineName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // Departure / arrival times.
                        Row {
                            TimeBlock(
                                label = stringResource(R.string.summary_departure),
                                value = TripFormat.time(current.startedAt),
                                modifier = Modifier.weight(1f),
                            )
                            TimeBlock(
                                label = stringResource(R.string.summary_arrival),
                                value = TripFormat.time(current.arrivedAt),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // Stat tiles.
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                value = current.durationMinutes.toString(),
                                label = stringResource(R.string.summary_mins),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                value = TripFormat.distanceKm(current),
                                label = stringResource(R.string.summary_km),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                value = stringResource(R.string.summary_ontime),
                                label = stringResource(R.string.summary_status),
                                highlight = true,
                                modifier = Modifier.weight(1.2f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDone,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(bottom = 0.dp),
            ) {
                Text(stringResource(R.string.summary_done), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TimeBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier.then(
            glassModifier(
                RoundedCornerShape(18.dp),
                color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                alpha = if (highlight) 0.14f else 0.5f,
            )
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
