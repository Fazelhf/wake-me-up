package com.wakemethere.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakemethere.app.R
import com.wakemethere.app.data.datastore.AppSettings
import com.wakemethere.app.domain.model.TransitNetwork
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * Destination-picking screen. Three bold modes:
 *  - METRO: all Tehran Metro lines and stations drawn on the map,
 *  - BRT: the BRT corridors and stations,
 *  - FREE: tap anywhere / Nominatim search.
 * Tapping a station (or the map in FREE mode) selects the wake-up
 * destination; the bottom panel arms the alarm.
 */
@Composable
fun MapScreen(
    onTrackingStarted: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ModeSwitcher(mode = state.mode, onModeChanged = viewModel::onModeChanged)

        if (state.mode == PickerMode.FREE) {
            SearchBar(state = state, viewModel = viewModel)
        }

        Box(modifier = Modifier.weight(1f)) {
            OsmMap(
                state = state,
                onMapTapped = viewModel::onMapTapped,
                onStationTapped = viewModel::onStationTapped,
            )
            if (!state.hasPin) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (state.mode == PickerMode.FREE) R.string.map_tap_hint
                            else R.string.map_tap_station_hint
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        BottomPanel(
            state = state,
            viewModel = viewModel,
            onTrackingStarted = onTrackingStarted,
        )
    }
}

/** Prominent Metro / BRT / free-pick switcher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitcher(
    mode: PickerMode,
    onModeChanged: (PickerMode) -> Unit,
) {
    val options = listOf(
        Triple(PickerMode.METRO, Icons.Default.Subway, R.string.map_mode_metro),
        Triple(PickerMode.BRT, Icons.Default.DirectionsBus, R.string.map_mode_brt),
        Triple(PickerMode.FREE, Icons.Default.Place, R.string.map_mode_free),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(48.dp),
    ) {
        options.forEachIndexed { index, (itemMode, icon, labelRes) ->
            SegmentedButton(
                selected = mode == itemMode,
                onClick = { onModeChanged(itemMode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                },
            ) {
                Text(
                    text = stringResource(labelRes),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Search field with a dropdown of Nominatim results (FREE mode only). */
@Composable
private fun SearchBar(state: MapUiState, viewModel: MapViewModel) {
    Column {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onQueryChanged,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.map_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (state.searchError) {
            Text(
                text = stringResource(R.string.map_search_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.searchResults.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.searchResults) { result ->
                    ListItem(
                        headlineContent = { Text(result.displayName, maxLines = 2) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onSearchResultPicked(result) }
                            .padding(horizontal = 4.dp),
                        leadingContent = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        tonalElevation = 2.dp,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Selected destination info, radius slider, favorite and start button. */
@Composable
private fun BottomPanel(
    state: MapUiState,
    viewModel: MapViewModel,
    onTrackingStarted: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Selected station header (transit modes).
            if (state.selectedStationId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(state.selectedLineColor ?: 0xFF888888.toInt())),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.pickedName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    state.selectedLineName?.let { lineName ->
                        Text(
                            text = lineName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = stringResource(R.string.map_radius_label, state.radiusMeters),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = state.radiusMeters.toFloat(),
                onValueChange = { viewModel.onRadiusChanged(it.toInt()) },
                valueRange = AppSettings.MIN_RADIUS_METERS.toFloat()..AppSettings.MAX_RADIUS_METERS.toFloat(),
                steps = 18,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.saveAsFavorite,
                    onCheckedChange = viewModel::onSaveAsFavoriteChanged,
                )
                Text(stringResource(R.string.map_save_favorite))
            }
            if (state.saveAsFavorite) {
                OutlinedTextField(
                    value = state.favoriteName,
                    onValueChange = viewModel::onFavoriteNameChanged,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.map_favorite_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { viewModel.onStartTracking(onTrackingStarted) },
                enabled = state.hasPin,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(52.dp),
            ) {
                Text(
                    text = stringResource(R.string.map_start_tracking),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * The OSMdroid map wrapped for Compose. Renders the transit network of the
 * active mode (colored line polylines + circular station markers), the
 * picked destination with its radius circle, and handles taps. Overlays are
 * rebuilt only when the relevant state actually changes.
 */
@Composable
private fun OsmMap(
    state: MapUiState,
    onMapTapped: (Double, Double) -> Unit,
    onStationTapped: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(INITIAL_ZOOM)
            controller.setCenter(GeoPoint(state.startLatitude, state.startLongitude))
        }
    }
    // Mutable render bookkeeping that must not trigger recomposition.
    val renderKey = remember { arrayOf("") }
    val fittedNetwork = remember { arrayOf<TransitNetwork?>(null) }

    // In FREE mode, center on the user's position once it becomes known
    // (transit modes fit the whole network instead).
    LaunchedEffect(state.startLatitude, state.startLongitude, state.mode) {
        if (state.mode == PickerMode.FREE && !state.hasPin) {
            mapView.controller.setCenter(GeoPoint(state.startLatitude, state.startLongitude))
        }
    }

    // Forward pause/resume so OSMdroid manages its tile cache correctly.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            val key = listOf(
                state.mode,
                state.network?.system,
                state.network?.lines?.size ?: 0,
                state.selectedStationId,
                state.pickedLatitude,
                state.pickedLongitude,
                state.radiusMeters,
            ).joinToString("|")
            if (key == renderKey[0]) return@AndroidView
            renderKey[0] = key

            view.overlays.clear()

            // Tap handler: drops/moves the pin in FREE mode.
            view.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        onMapTapped(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                })
            )

            // Transit network: one folder per line with polyline + stations.
            val network = state.network
            if (state.mode != PickerMode.FREE && network != null) {
                view.overlays.add(buildNetworkOverlay(view, network, state, onStationTapped))

                // Fit the camera to the network once per loaded network.
                if (fittedNetwork[0] !== network) {
                    fittedNetwork[0] = network
                    networkBoundingBox(network)?.let { box ->
                        view.post { view.zoomToBoundingBox(box, false, 64) }
                    }
                }
            }

            // Picked destination: radius circle + pin marker.
            val lat = state.pickedLatitude
            val lon = state.pickedLongitude
            if (lat != null && lon != null) {
                val point = GeoPoint(lat, lon)

                val circle = Polygon(view).apply {
                    points = Polygon.pointsAsCircle(point, state.radiusMeters.toDouble())
                    fillPaint.color = 0x301565C0
                    outlinePaint.color = 0xFF1565C0.toInt()
                    outlinePaint.strokeWidth = 3f
                    setOnClickListener { _, _, _ -> false }
                }
                view.overlays.add(circle)

                // In FREE mode show a classic pin; in transit modes the
                // selected station's enlarged icon already marks the spot.
                if (state.selectedStationId == null) {
                    val marker = Marker(view).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    view.overlays.add(marker)
                }

                view.controller.animateTo(point)
            }
            view.invalidate()
        },
    )
}

/** Builds the overlay tree for a whole transit network. */
private fun buildNetworkOverlay(
    view: MapView,
    network: TransitNetwork,
    state: MapUiState,
    onStationTapped: (String) -> Unit,
): FolderOverlay {
    val folder = FolderOverlay()
    for (line in network.lines) {
        // Line path connecting the stations in order.
        val polyline = Polyline(view).apply {
            setPoints(line.stations.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.color = line.color
            outlinePaint.strokeWidth = 9f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            infoWindow = null
        }
        folder.add(polyline)

        for (station in line.stations) {
            val selected = station.id == state.selectedStationId
            val marker = Marker(view).apply {
                position = GeoPoint(station.latitude, station.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = StationMarkerIcons.get(view.context, line.color, selected)
                title = station.name
                infoWindow = null
                setOnMarkerClickListener { _, _ ->
                    onStationTapped(station.id)
                    true
                }
            }
            folder.add(marker)
        }
    }
    return folder
}

/** Bounding box containing every station of the network. */
private fun networkBoundingBox(network: TransitNetwork): BoundingBox? {
    val points = network.lines.flatMap { line ->
        line.stations.map { GeoPoint(it.latitude, it.longitude) }
    }
    return if (points.isEmpty()) null else BoundingBox.fromGeoPoints(points)
}

private const val INITIAL_ZOOM = 15.0
