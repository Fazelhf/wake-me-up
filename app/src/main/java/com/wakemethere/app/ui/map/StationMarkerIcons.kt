package com.wakemethere.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable

/**
 * Generates and caches the circular station icons drawn on the map:
 * a ring in the line's color with a white core (classic transit-map look).
 * The selected station gets a larger icon with a colored center dot.
 */
object StationMarkerIcons {

    private val cache = HashMap<String, BitmapDrawable>()

    fun get(context: Context, lineColor: Int, selected: Boolean): BitmapDrawable {
        val key = "$lineColor-$selected"
        return cache.getOrPut(key) { create(context, lineColor, selected) }
    }

    /** Drops all cached bitmaps (e.g. on low memory). */
    fun clear() = cache.clear()

    private fun create(context: Context, lineColor: Int, selected: Boolean): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val diameterDp = if (selected) 26f else 15f
        val size = (diameterDp * density).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Subtle dark outline so light rings stay visible on light tiles.
        paint.color = Color.argb(90, 0, 0, 0)
        canvas.drawCircle(center, center, center, paint)

        // Line-colored ring.
        paint.color = lineColor
        canvas.drawCircle(center, center, center * 0.92f, paint)

        // White core.
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center * 0.55f, paint)

        if (selected) {
            // Colored center dot marks the chosen station.
            paint.color = lineColor
            canvas.drawCircle(center, center, center * 0.28f, paint)
        }

        return BitmapDrawable(context.resources, bitmap)
    }
}
