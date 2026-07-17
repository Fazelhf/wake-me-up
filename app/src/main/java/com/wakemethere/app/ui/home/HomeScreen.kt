package com.wakemethere.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakemethere.app.R
import com.wakemethere.app.domain.model.Destination
import com.wakemethere.app.domain.model.TrackingStatus
import com.wakemethere.app.ui.components.AmbientBackground
import com.wakemethere.app.ui.components.GlassCard
import com.wakemethere.app.ui.components.ScreenMargin
import com.wakemethere.app.ui.components.glassModifier
import com.wakemethere.app.ui.components.rememberPulse
import com.wakemethere.app.util.formatDistance

/**
 * Home screen (Liquid Transit look): glass top bar, a live armed-alarm glass
 * card with a pulsing status and stat tiles, glass favorite cards, and a
 * floating "Set Alarm" capsule.
 */
@Composable
fun HomeScreen(
    onSetAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val status by viewModel.trackingStatus.collectAsStateWithLifecycle()
    val idle = status is TrackingStatus.Idle

    // One-shot entrance animation for the dashboard content.
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(onOpenSettings = onOpenSettings, onOpenHistory = onOpenHistory)

            AnimatedVisibility(
                visibleState = entered,
                enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 10 },
            ) {
            Column {
            if (idle) GreetingCard() else DisabledModeSwitcher()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScreenMargin),
            ) {
                if (!idle) Spacer(modifier = Modifier.weight(1f))
                when (val current = status) {
                    is TrackingStatus.Tracking -> ArmedCard(
                        destination = current.destination,
                        distanceMeters = current.distanceMeters,
                        startDistanceMeters = current.startDistanceMeters,
                        signalWeak = current.signalWeak,
                        onCancel = viewModel::cancelTracking,
                    )
                    is TrackingStatus.Alarming -> ArmedCard(
                        destination = current.destination,
                        distanceMeters = current.distanceMeters,
                        startDistanceMeters = null,
                        signalWeak = false,
                        onCancel = viewModel::cancelTracking,
                    )
                    TrackingStatus.Idle -> Unit
                }

                if (!idle) {
                    Spacer(modifier = Modifier.height(96.dp))
                }
                if (idle) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.home_favorites_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (favorites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_no_favorites),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
                    ) {
                        items(favorites, key = Destination::id) { favorite ->
                            FavoriteRow(
                                favorite = favorite,
                                armEnabled = idle,
                                onArm = { viewModel.armFavorite(favorite) },
                                onDelete = { viewModel.deleteFavorite(favorite) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
                }
            }
            }
            }
        }

        // Floating pulsing "Stop Tracking" capsule (prototype: fixed above
        // the bottom edge while tracking; bottom nav is hidden then).
        if (!idle) {
            val stopPulse = rememberPulse()
            Button(
                onClick = viewModel::cancelTracking,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .scale(1f + (1f - stopPulse) * 0.03f)
                    .height(56.dp),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.notif_stop_action),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Floating "Set Alarm" capsule (hidden while an alarm is armed).
        if (idle) {
            Button(
                onClick = onSetAlarm,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 104.dp)
                    .height(56.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.home_set_alarm),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The Metro/BRT/Anywhere switcher, shown disabled while tracking. */
@Composable
private fun DisabledModeSwitcher() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .graphicsLayer { alpha = 0.5f }
            .then(glassModifier(RoundedCornerShape(26.dp)))
            .padding(6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            Triple(true, Icons.Default.Navigation, R.string.map_mode_metro),
            Triple(false, Icons.Default.Navigation, R.string.map_mode_brt),
            Triple(false, Icons.Default.Navigation, R.string.map_mode_free),
        ).forEach { (selected, _, labelRes) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .padding(vertical = 11.dp),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Greeting header: avatar, salam + creator credit — always visible on top. */
@Composable
private fun GreetingCard() {
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_owner_initial),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.settings_credit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Floating glass top app bar (prototype: menu / title / avatar). */
@Composable
private fun GlassTopBar(onOpenSettings: () -> Unit, onOpenHistory: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .then(glassModifier(RoundedCornerShape(12.dp)))
            .height(64.dp),
    ) {
        IconButton(
            onClick = onOpenHistory,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.history_title),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center),
        )
        // Avatar (prototype trailing element) — opens settings.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_owner_initial),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Glass card shown while an alarm is armed: pulsing status + stat tiles. */
@Composable
private fun ArmedCard(
    destination: Destination,
    distanceMeters: Float?,
    startDistanceMeters: Float?,
    signalWeak: Boolean,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val pulse = rememberPulse()

    GlassCard(
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp).animateContentSize(tween(320))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_armed_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = destination.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Pulsing navigation badge.
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(0.8f + pulse * 0.5f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = (1f - pulse) * 0.4f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // Trip progress toward the destination (once both are known).
            if (startDistanceMeters != null && distanceMeters != null && startDistanceMeters > 0f) {
                val progress = (1f - distanceMeters / startDistanceMeters).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                )
            }
            if (distanceMeters != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_armed_distance,
                        formatDistance(context, distanceMeters),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.home_stat_eta),
                    value = when {
                        signalWeak -> stringResource(R.string.home_stat_weak)
                        distanceMeters != null -> stringResource(
                            R.string.home_minutes,
                            (distanceMeters / 500f).toInt().coerceAtLeast(1),
                        )
                        else -> "—"
                    },
                    animateValue = true,
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.home_stat_status),
                    value = stringResource(R.string.home_stat_live),
                    valueColor = MaterialTheme.colorScheme.tertiaryContainer,
                    showLiveDot = true,
                    pulse = pulse,
                )
            }

        }
    }
}

/** Small inner stat tile used inside the armed card. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    showLiveDot: Boolean = false,
    pulse: Float = 0f,
    animateValue: Boolean = false,
) {
    Box(
        modifier = modifier.then(
            glassModifier(
                RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                alpha = 0.5f,
            )
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLiveDot) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { alpha = 0.4f + (1f - pulse) * 0.6f }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                }
                if (animateValue) {
                    Crossfade(targetState = value, animationSpec = tween(350), label = "stat") { v ->
                        Text(
                            text = v,
                            style = MaterialTheme.typography.headlineSmall,
                            color = valueColor,
                        )
                    }
                } else {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = valueColor,
                    )
                }
            }
        }
    }
}

/** One favorite destination as a glass card with arm and delete actions. */
@Composable
private fun FavoriteRow(
    favorite: Destination,
    armEnabled: Boolean,
    onArm: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    favorite.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${favorite.radiusMeters} m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onArm, enabled = armEnabled, shape = CircleShape) {
                Text(stringResource(R.string.home_arm_favorite))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_delete_favorite),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
