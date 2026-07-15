package com.wakemethere.app.util

import android.content.Context
import com.wakemethere.app.R
import kotlin.math.roundToInt

/**
 * Formats a distance in meters for display: "850 m" below 1 km,
 * "2.3 km" above.
 */
fun formatDistance(context: Context, meters: Float): String =
    if (meters < 1000f) {
        context.getString(R.string.distance_meters, meters.roundToInt())
    } else {
        context.getString(R.string.distance_kilometers, meters / 1000f)
    }
