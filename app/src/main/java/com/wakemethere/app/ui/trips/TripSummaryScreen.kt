package com.wakemethere.app.ui.trips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
                val outlineVar = MaterialTheme.colorScheme.outlineVariant
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
                        // Prototype timeline: blue departure dot, dashed line,
                        // orange arrival dot, with stacked labels.
                        Row {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 6.dp, end = 14.dp),
                            ) {
                                TimelineDot(MaterialTheme.colorScheme.primary)
                                Canvas(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(52.dp),
                                ) {
                                    drawLine(
                                        color = outlineVar,
                                        start = Offset(size.width / 2, 0f),
                                        end = Offset(size.width / 2, size.height),
                                        strokeWidth = size.width,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                                    )
                                }
                                TimelineDot(MaterialTheme.colorScheme.secondaryContainer)
                            }
                            Column {
                                TimeBlock(
                                    label = stringResource(R.string.summary_departure),
                                    value = TripFormat.time(current.startedAt),
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                TimeBlock(
                                    label = stringResource(R.string.summary_arrival),
                                    value = TripFormat.time(current.arrivedAt),
                                )
                            }
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
                        Spacer(modifier = Modifier.height(16.dp))
                        RouteSnippet(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDone,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    stringResource(R.string.summary_done),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** Small filled circle with a soft ring, used by the timeline. */
@Composable
private fun TimelineDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(color.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
    }
}

/**
 * Prototype map snippet: dotted grid over a tinted surface with a curved
 * route and endpoint rings — drawn locally, no images needed.
 */
@Composable
private fun RouteSnippet(modifier: Modifier = Modifier) {
    val dot = MaterialTheme.colorScheme.primary
    val route = MaterialTheme.colorScheme.primaryContainer
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        drawRect(base)
        // Dotted grid, 16px pitch (per prototype).
        val pitch = 16.dp.toPx()
        var y = pitch / 2
        while (y < size.height) {
            var x = pitch / 2
            while (x < size.width) {
                drawCircle(dot.copy(alpha = 0.2f), radius = 1.5f, center = Offset(x, y))
                x += pitch
            }
            y += pitch
        }
        // Curved route: M 10 80 Q 30 60, 50 70 T 90 20 (percent space).
        val p = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.80f)
            quadraticBezierTo(
                size.width * 0.30f, size.height * 0.60f,
                size.width * 0.50f, size.height * 0.70f,
            )
            quadraticBezierTo(
                size.width * 0.70f, size.height * 0.80f,
                size.width * 0.90f, size.height * 0.20f,
            )
        }
        drawPath(p, route, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (pt in listOf(
            Offset(size.width * 0.10f, size.height * 0.80f),
            Offset(size.width * 0.90f, size.height * 0.20f),
        )) {
            drawCircle(androidx.compose.ui.graphics.Color.White, radius = 8f, center = pt)
            drawCircle(route, radius = 8f, center = pt, style = Stroke(width = 4f))
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
