package com.wakemethere.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid-glass building blocks shared by every screen: a soft ambient
 * background of blurred color orbs, and translucent "glass" surfaces with a
 * light border and gentle blue shadow.
 *
 * True backdrop blur is not portable across the min-SDK range, so the glass
 * effect is approximated with translucency + a bright hairline border +
 * a soft colored shadow — which reads as glass over the ambient background.
 */

/**
 * Fills the available space with the surface background plus two blurred,
 * low-opacity color orbs, giving depth for glass panels layered on top.
 * Place it as the first child of a Box and draw content above it.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val base = MaterialTheme.colorScheme.background

    // Radial gradients fade smoothly to transparent — unlike blurred boxes,
    // they can never show hard rectangular edges (visible in dark mode).
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.02f),
                        radius = size.width * 0.85f,
                    ),
                    center = Offset(size.width * 0.08f, size.height * 0.02f),
                    radius = size.width * 0.85f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.92f),
                        radius = size.width * 0.75f,
                    ),
                    center = Offset(size.width * 0.95f, size.height * 0.92f),
                    radius = size.width * 0.75f,
                )
            }
    )
}

/** True when the current color scheme is dark (by surface luminance). */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Green "success/on-time" accent that stays readable as a FOREGROUND color
 * in both themes. (tertiaryContainer is a dark green in the dark scheme —
 * fine as a background tint, invisible as text/icons on dark surfaces.)
 */
@Composable
fun successAccent(): Color =
    if (isDarkScheme()) Color(0xFF7ADF9B) else MaterialTheme.colorScheme.tertiaryContainer

/** Orange "arrival" accent for timeline dots, readable in both themes. */
@Composable
fun arrivalAccent(): Color =
    if (isDarkScheme()) Color(0xFFFFB77C) else MaterialTheme.colorScheme.secondaryContainer

/**
 * A translucent glass surface. Use for cards, panels and pills. The tint and
 * the bright hairline border adapt to light/dark so the glass stays legible.
 *
 * @param color base tint of the glass (defaults to a scheme surface).
 * @param alpha translucency of the tint.
 */
@Composable
fun glassModifier(
    shape: Shape,
    color: Color = if (isDarkScheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceBright
    },
    // Dark glass is more opaque than light: text needs the extra contrast
    // over the ambient gradients and (on the map screen) the tiles.
    alpha: Float = if (isDarkScheme()) 0.78f else 0.72f,
    borderColor: Color = Color.White.copy(alpha = if (isDarkScheme()) 0.16f else 0.6f),
): Modifier = Modifier
    .clip(shape)
    .background(color.copy(alpha = alpha), shape)
    .border(1.dp, borderColor, shape)

/**
 * Convenience glass card container.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.then(glassModifier(shape))) {
        content()
    }
}

/**
 * A pulsing scale+alpha animation value in [0,1], for "live"/active
 * indicators (tracking dot, navigation icon halo). Multiply into scale and
 * derive ring alpha from it.
 */
@Composable
fun rememberPulse(periodMillis: Int = 2000): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-value",
    )
    return value
}

/**
 * Vertical brand gradient used behind hero areas (e.g. onboarding top).
 */
@Composable
fun heroGradient(): Brush = Brush.linearGradient(
    colors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.background.copy(alpha = 0f),
    ),
    start = Offset(0f, 0f),
    end = Offset(0f, 900f),
)

/** Standard content edge padding used across screens (design margin-mobile). */
val ScreenMargin: Dp = 24.dp
