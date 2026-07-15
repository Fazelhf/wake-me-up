package com.wakemethere.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakemethere.app.R
import com.wakemethere.app.data.datastore.AppSettings
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Destination-picking screen: OSMdroid map with tap-to-drop pin, radius
 * circle, Nominatim search and "start tracking" controls.
 */
@Composable
fun MapScreen(
    onTrackingStarted: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(state = state, viewModel = viewModel)

        Box(modifier = Modifier.weight(1f)) {
            OsmMap(
                state = state,
                onMapTapped = viewModel::onMapTapped,
            )
            if (!state.hasPin) {
                Card(modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.map_tap_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        BottomControls(
            state = state,
            viewModel = viewModel,
            onTrackingStarted = onTrackingStarted,
        )
    }
}

/** Search field with a dropdown of Nominatim results. */
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

/** Radius slider, favorite controls and the start-tracking button. */
@Composable
private fun BottomControls(
    state: MapUiState,
    viewModel: MapViewModel,
    onTrackingStarted: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
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
            Spacer(modifier = Modifier.width(8.dp))
        }
        Button(
            onClick = { viewModel.onStartTracking(onTrackingStarted) },
            enabled = state.hasPin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.map_start_tracking))
        }
    }
}

/**
 * The OSMdroid map wrapped for Compose. Keeps a single MapView instance,
 * forwards lifecycle events, and re-renders the pin + radius circle
 * whenever the picked point or radius changes.
 */
@Composable
private fun OsmMap(
    state: MapUiState,
    onMapTapped: (Double, Double) -> Unit,
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

    // Center on the user's position once it becomes known (it loads
    // asynchronously after the map is created), unless a pin is set.
    LaunchedEffect(state.startLatitude, state.startLongitude) {
        if (!state.hasPin) {
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
            view.overlays.clear()

            // Tap handler drops/moves the destination pin.
            view.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        onMapTapped(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                })
            )

            val lat = state.pickedLatitude
            val lon = state.pickedLongitude
            if (lat != null && lon != null) {
                val point = GeoPoint(lat, lon)

                // Translucent trigger-radius circle around the pin.
                val circle = Polygon(view).apply {
                    points = Polygon.pointsAsCircle(point, state.radiusMeters.toDouble())
                    fillPaint.color = 0x301565C0
                    outlinePaint.color = 0xFF1565C0.toInt()
                    outlinePaint.strokeWidth = 3f
                    setOnClickListener { _, _, _ -> false }
                }
                view.overlays.add(circle)

                val marker = Marker(view).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                view.overlays.add(marker)

                view.controller.animateTo(point)
            }
            view.invalidate()
        },
    )
}

private const val INITIAL_ZOOM = 15.0
