package com.wakemethere.app.ui.planner

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakemethere.app.R
import com.wakemethere.app.domain.model.TransitSystem
import com.wakemethere.app.domain.route.RoutePlanner
import com.wakemethere.app.ui.components.AmbientBackground
import com.wakemethere.app.ui.components.GlassCard
import com.wakemethere.app.ui.components.glassModifier
import com.wakemethere.app.util.AppLocale
import com.wakemethere.app.util.formatDistance

/**
 * Origin→destination route planner (Liquid Transit): pick two stations (or
 * start from the current location), get the best route with suggested lines,
 * transfer stations, time and distance — plus an alternative — and arm the
 * wake-up alarm for the destination in one tap.
 */
@Composable
fun RoutePlannerScreen(
    onBack: () -> Unit,
    onArmed: () -> Unit,
    viewModel: RoutePlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val english = AppLocale.current(context) == "en"

    // Which endpoint the station picker is open for (null = closed).
    var pickerForOrigin by remember { mutableStateOf<Boolean?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Glass top bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .then(glassModifier(RoundedCornerShape(20.dp)))
                    .padding(vertical = 10.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(R.string.planner_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                EndpointsCard(
                    state = state,
                    english = english,
                    stationName = { id -> viewModel.station(id)?.displayName(english) },
                    onPickOrigin = { pickerForOrigin = true },
                    onPickDestination = { pickerForOrigin = false },
                    onSwap = viewModel::onSwap,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = viewModel::onPlan,
                    enabled = !state.loading && !state.planning &&
                        state.destinationId != null &&
                        (state.originIsMyLocation || state.originId != null),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    if (state.planning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.planner_find),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (state.locationError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.planner_location_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.planned && state.plans.isEmpty() && !state.locationError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.planner_no_route),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                state.plans.forEachIndexed { index, plan ->
                    Spacer(modifier = Modifier.height(16.dp))
                    PlanCard(
                        plan = plan,
                        best = index == 0,
                        english = english,
                        originWalk = if (index == 0) state.originWalk else null,
                        onArm = { viewModel.onArmDestination(onArmed) },
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        pickerForOrigin?.let { forOrigin ->
            StationPicker(
                stations = state.stations,
                english = english,
                allowMyLocation = forOrigin,
                onDismiss = { pickerForOrigin = null },
                onMyLocation = {
                    viewModel.onOriginPicked(null)
                    pickerForOrigin = null
                },
                onStation = { station ->
                    if (forOrigin) {
                        viewModel.onOriginPicked(station.id)
                    } else {
                        viewModel.onDestinationPicked(station.id)
                    }
                    pickerForOrigin = null
                },
            )
        }
    }
}

private fun RoutePlanner.StationOption.displayName(english: Boolean): String =
    if (english) nameEn ?: name else name

/** Origin/destination rows with the swap button, prototype-style dots. */
@Composable
private fun EndpointsCard(
    state: RoutePlannerViewModel.UiState,
    english: Boolean,
    stationName: (String?) -> String?,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onSwap: () -> Unit,
) {
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            EndpointRow(
                dotColor = MaterialTheme.colorScheme.primary,
                label = stringResource(R.string.planner_origin),
                value = if (state.originIsMyLocation) {
                    stringResource(R.string.planner_my_location)
                } else {
                    stationName(state.originId)
                        ?: stringResource(R.string.planner_choose_station)
                },
                showLocationIcon = state.originIsMyLocation,
                onClick = onPickOrigin,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                IconButton(onClick = onSwap) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.planner_swap),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            EndpointRow(
                dotColor = MaterialTheme.colorScheme.secondaryContainer,
                label = stringResource(R.string.planner_destination),
                value = stationName(state.destinationId)
                    ?: stringResource(R.string.planner_choose_station),
                showLocationIcon = false,
                onClick = onPickDestination,
            )
        }
    }
}

@Composable
private fun EndpointRow(
    dotColor: Color,
    label: String,
    value: String,
    showLocationIcon: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(dotColor.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showLocationIcon) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** One planned route: summary, leg timeline with transfers, arm button. */
@Composable
private fun PlanCard(
    plan: RoutePlanner.RoutePlan,
    best: Boolean,
    english: Boolean,
    originWalk: RoutePlannerViewModel.OriginWalk?,
    onArm: () -> Unit,
) {
    val context = LocalContext.current
    GlassCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(
                    if (best) R.string.planner_best_route else R.string.planner_alt_route
                ).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (best) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Summary row: minutes, distance, transfers.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.planner_summary_minutes, plan.totalMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Icon(
                    Icons.Default.Straighten,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatDistance(context, plan.totalDistanceMeters.toFloat()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (plan.transfers.isEmpty()) {
                        stringResource(R.string.planner_summary_no_transfer)
                    } else {
                        stringResource(R.string.planner_summary_transfers, plan.transfers.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            if (originWalk != null && originWalk.meters > 30) {
                TransferRow(
                    icon = Icons.Default.DirectionsWalk,
                    text = stringResource(
                        R.string.planner_walk_to_start,
                        formatDistance(context, originWalk.meters.toFloat()),
                        originWalk.station.displayName(english),
                    ),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Legs interleaved with their transfers, in travel order. The
            // mapping is computed once per plan (recomposition-safe).
            val transferBeforeLeg = remember(plan) {
                val used = mutableSetOf<Int>()
                plan.legs.mapIndexed { index, leg ->
                    if (index == 0) {
                        null
                    } else {
                        plan.transfers.withIndex()
                            .firstOrNull { (i, t) -> i !in used && t.toStation.id == leg.from.id }
                            ?.also { used.add(it.index) }
                            ?.value
                    }
                }
            }
            plan.legs.forEachIndexed { index, leg ->
                transferBeforeLeg[index]?.let { t ->
                    // Explicit change-line guidance: which station, from
                    // which line to which line (localized line names come
                    // from the adjacent legs).
                    val fromLine = plan.legs[index - 1].let {
                        if (english) it.lineNameEn ?: it.lineName else it.lineName
                    }
                    val toLine = if (english) leg.lineNameEn ?: leg.lineName else leg.lineName
                    Spacer(modifier = Modifier.height(10.dp))
                    TransferRow(
                        icon = if (t.isWalk) Icons.Default.DirectionsWalk
                        else Icons.Default.SwapHoriz,
                        text = if (t.isWalk) {
                            stringResource(
                                R.string.planner_walk_detail,
                                t.fromStation.displayName(english),
                                formatDistance(context, t.walkMeters.toFloat()),
                                t.toStation.displayName(english),
                                toLine,
                            )
                        } else {
                            stringResource(
                                R.string.planner_transfer_detail,
                                t.fromStation.displayName(english),
                                fromLine,
                                toLine,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                LegRow(leg = leg, english = english)
            }

            if (best) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onArm,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.planner_arm_alarm),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Selectable pill for the picker's Metro/Bus filter. */
@Composable
private fun SystemPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Highlighted guidance row for a transfer or a walking link. */
@Composable
private fun TransferRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** One ride: colored line chip, line name, endpoints and stop count. */
@Composable
private fun LegRow(leg: RoutePlanner.RouteLeg, english: Boolean) {
    val lineColor = Color(leg.color)
    Row {
        // Line indicator: dot + thick colored bar.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                imageVector = if (leg.system == TransitSystem.METRO) {
                    Icons.Default.Subway
                } else {
                    Icons.Default.DirectionsBus
                },
                contentDescription = null,
                tint = lineColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(lineColor.copy(alpha = 0.75f)),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = if (english) leg.lineNameEn ?: leg.lineName else leg.lineName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = lineColor,
            )
            Text(
                // Arrow follows the layout direction (RTL Persian, LTR English).
                text = if (english) {
                    "${leg.from.displayName(true)} → ${leg.to.displayName(true)}"
                } else {
                    "${leg.from.displayName(false)} ← ${leg.to.displayName(false)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.planner_leg_stops, leg.stops),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Full-screen glass station picker with search. */
@Composable
private fun StationPicker(
    stations: List<RoutePlanner.StationOption>,
    english: Boolean,
    allowMyLocation: Boolean,
    onDismiss: () -> Unit,
    onMyLocation: () -> Unit,
    onStation: (RoutePlanner.StationOption) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Which system to search in: null = both Metro and BRT buses.
    var systemFilter by remember { mutableStateOf<TransitSystem?>(null) }
    val visible = remember(query, stations, systemFilter) {
        val q = query.trim()
        stations.filter { station ->
            (systemFilter == null || station.system == systemFilter) &&
                (q.isEmpty() ||
                    station.name.contains(q, ignoreCase = true) ||
                    (station.nameEn?.contains(q, ignoreCase = true) == true))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: close + search field in a glass capsule.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .then(glassModifier(RoundedCornerShape(24.dp)))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.planner_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // What you're searching: everything, Metro, or BRT buses.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
            ) {
                SystemPill(
                    label = stringResource(R.string.planner_filter_all),
                    icon = null,
                    tint = MaterialTheme.colorScheme.primary,
                    selected = systemFilter == null,
                    onClick = { systemFilter = null },
                )
                SystemPill(
                    label = stringResource(R.string.map_mode_metro),
                    icon = Icons.Default.Subway,
                    tint = Color(0xFF007AFF),
                    selected = systemFilter == TransitSystem.METRO,
                    onClick = { systemFilter = TransitSystem.METRO },
                )
                SystemPill(
                    label = stringResource(R.string.planner_filter_bus),
                    icon = Icons.Default.DirectionsBus,
                    tint = Color(0xFFFF9500),
                    selected = systemFilter == TransitSystem.BRT,
                    onClick = { systemFilter = TransitSystem.BRT },
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (allowMyLocation) {
                    item(key = "my-location") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(glassModifier(RoundedCornerShape(16.dp)))
                                .clickable(onClick = onMyLocation)
                                .padding(14.dp),
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.planner_my_location),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                items(visible, key = { it.id }) { station ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(glassModifier(RoundedCornerShape(16.dp)))
                            .clickable { onStation(station) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = if (station.system == TransitSystem.METRO) {
                                Icons.Default.Subway
                            } else {
                                Icons.Default.DirectionsBus
                            },
                            contentDescription = null,
                            tint = if (station.system == TransitSystem.METRO) {
                                Color(0xFF007AFF)
                            } else {
                                Color(0xFFFF9500)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.displayName(english),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = station.lineNames.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Line color dots.
                        Row {
                            station.lineColors.take(4).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(10.dp)
                                        .background(Color(color), CircleShape),
                                )
                            }
                        }
                    }
                }
                item(key = "bottom-space") {
                    Spacer(
                        modifier = Modifier
                            .height(24.dp)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }
}
