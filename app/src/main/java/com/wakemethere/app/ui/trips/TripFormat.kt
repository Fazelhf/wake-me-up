package com.wakemethere.app.ui.trips

import android.content.Context
import com.wakemethere.app.R
import com.wakemethere.app.domain.model.Trip
import com.wakemethere.app.ui.theme.BrtAccent
import com.wakemethere.app.ui.theme.MetroAccent
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared formatting/helpers for trip screens. */
object TripFormat {

    private val dateTime = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    private val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun dateTime(epochMillis: Long): String = dateTime.format(Date(epochMillis))
    fun time(epochMillis: Long): String = timeOnly.format(Date(epochMillis))

    /** Accent color for a transit type. */
    fun accent(transitType: String): Color = when (transitType) {
        "METRO" -> MetroAccent
        "BRT" -> BrtAccent
        else -> MetroAccent
    }

    /** Localized short label for a transit type. */
    fun label(context: Context, transitType: String): String = when (transitType) {
        "METRO" -> context.getString(R.string.map_mode_metro)
        "BRT" -> context.getString(R.string.map_mode_brt)
        else -> context.getString(R.string.map_mode_free)
    }

    fun distanceKm(trip: Trip): String =
        String.format(Locale.getDefault(), "%.1f", trip.distanceMeters / 1000f)
}
