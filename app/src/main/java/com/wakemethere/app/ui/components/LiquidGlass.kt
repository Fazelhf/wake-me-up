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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
    ) {
        // Top-left blue orb.
        Box(
            modifier = Modifier
                .offset(x = (-80).dp, y = (-100).dp)
                .size(320.dp)
                .blur(90.dp)
                .background(primary.copy(alpha = 0.18f), RoundedCornerShape(50))
        )
        // Bottom-right warm orb.
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .offset(x = 70.dp, y = 90.dp)
                .size(280.dp)
                .blur(90.dp)
                .background(secondary.copy(alpha = 0.14f), RoundedCornerShape(50))
        )
    }
}

/**
 * A translucent glass surface. Use for cards, panels and pills.
 *
 * @param color base tint of the glass (defaults to a bright surface).
 * @param alpha translucency of the tint.
 */
@Composable
fun glassModifier(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surfaceBright,
    alpha: Float = 0.72f,
    borderColor: Color = Color.White.copy(alpha = 0.6f),
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
    color: Color = MaterialTheme.colorScheme.surfaceBright,
    alpha: Float = 0.72f,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.then(glassModifier(shape, color, alpha))) {
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
