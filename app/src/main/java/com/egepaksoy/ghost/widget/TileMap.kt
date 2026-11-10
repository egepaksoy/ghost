package com.egepaksoy.ghost.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.egepaksoy.ghost.R
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TimelineItem
import com.egepaksoy.ghost.core.TripItem
import com.egepaksoy.ghost.ui.EndGray
import com.egepaksoy.ghost.ui.RouteOrange
import com.egepaksoy.ghost.ui.StartGreen
import com.egepaksoy.ghost.ui.placeColor
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Widgets cannot host a live map, so the map is drawn into a picture: OpenStreetMap tiles
 * (cached on the phone) with the day's route on top, in the same Strava style as the app.
 */
object TileMap {
    private fun x(lon: Double, z: Int, t: Double) = (lon + 180) / 360 * (1 shl z) * t
    private fun y(lat: Double, z: Int, t: Double): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl z) * t
    }

    fun render(ctx: Context, items: List<TimelineItem>, w: Int, h: Int, pxPerDp: Float): Bitmap? {
        if (w <= 0 || h <= 0) return null
        val trips = items.filterIsInstance<TripItem>()
        val stays = items.filterIsInstance<StayItem>()
        val pts = trips.flatMap { t -> t.trip.path.map { it.lat to it.lon } } + stays.map { it.spot.lat to it.spot.lon }
        if (pts.isEmpty()) return null

        // Tiles are 256 px; scale them up a little on dense screens so street names stay readable.
        var tile = 256.0 * (pxPerDp / 1.6).coerceIn(1.0, 2.0)
        val pad = 20 * pxPerDp
        var z = 17
        val spread = pts.any { it.first != pts[0].first || it.second != pts[0].second }
        if (!spread) z = 15
        else while (z > 2) {
            val xs = pts.map { x(it.second, z, tile) }
            val ys = pts.map { y(it.first, z, tile) }
            if (xs.max() - xs.min() <= w - 2 * pad && ys.max() - ys.min() <= h - 2 * pad) break
            z--
        }
        if (spread) {
            // Zoom levels come in steps of 2x; stretch the tiles a bit so the route fills the picture.
            val sx = pts.map { x(it.second, z, tile) }.let { it.max() - it.min() }.coerceAtLeast(1.0)
            val sy = pts.map { y(it.first, z, tile) }.let { it.max() - it.min() }.coerceAtLeast(1.0)
            tile *= minOf((w - 2 * pad) / sx, (h - 2 * pad) / sy, 1.7).coerceAtLeast(1.0)
        }
        val xs = pts.map { x(it.second, z, tile) }
        val ys = pts.map { y(it.first, z, tile) }
        val left = (xs.max() + xs.min()) / 2 - w / 2.0
        val top = (ys.max() + ys.min()) / 2 - h / 2.0

        // RGB_565 keeps the picture small enough to send to the home screen.
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(ContextCompat.getColor(ctx, R.color.widget_map_bg))
        val n = 1 shl z
        val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (tx in floor(left / tile).toInt()..floor((left + w) / tile).toInt()) {
            for (ty in floor(top / tile).toInt()..floor((top + h) / tile).toInt()) {
                if (ty !in 0 until n) continue
                val img = tile(ctx, z, Math.floorMod(tx, n), ty) ?: continue
                val dst = RectF((tx * tile - left).toFloat(), (ty * tile - top).toFloat(), ((tx + 1) * tile - left).toFloat(), ((ty + 1) * tile - top).toFloat())
                c.drawBitmap(img, null, dst, tilePaint)
            }
        }

        fun px(lat: Double, lon: Double) = (x(lon, z, tile) - left).toFloat() to (y(lat, z, tile) - top).toFloat()
        val d = pxPerDp
        val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 6.5f * d
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val line = Paint(casing).apply { color = RouteOrange.toArgb(); strokeWidth = 3.8f * d }
        for (t in trips) {
            if (t.trip.path.size < 2) continue
            val p = Path()
            t.trip.path.forEachIndexed { i, f ->
                val (a, b) = px(f.lat, f.lon)
                if (i == 0) p.moveTo(a, b) else p.lineTo(a, b)
            }
            c.drawPath(p, casing)
            c.drawPath(p, line)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun dot(lat: Double, lon: Double, color: Int, r: Float) {
            val (a, b) = px(lat, lon)
            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(a, b, r + 2f * d, fill)
            fill.color = color; c.drawCircle(a, b, r, fill)
        }
        for (s in stays.distinctBy { it.spot.key }) dot(s.spot.lat, s.spot.lon, placeColor(s.spot.key).toArgb(), 4f * d)
        val first = trips.firstOrNull()?.trip?.path?.firstOrNull()
        val last = trips.lastOrNull()?.trip?.path?.lastOrNull()
        if (first != null && last != null) {
            dot(first.lat, first.lon, StartGreen.toArgb(), 4.5f * d)
            dot(last.lat, last.lon, EndGray.toArgb(), 4.5f * d)
        }
        return bmp
    }

    /** 24-hour bar: a block in each place's colour, orange while travelling. */
    fun strip(ctx: Context, items: List<TimelineItem>, dayStart: Long, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = h / 2f
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
        c.clipPath(clip)
        c.drawColor(ContextCompat.getColor(ctx, R.color.widget_track))
        val span = 24 * 3_600_000f
        for (it in items) {
            val (s, e, col) = when (it) {
                is StayItem -> Triple(it.start, it.end, placeColor(it.spot.key).toArgb())
                is TripItem -> Triple(it.trip.start, it.trip.end, RouteOrange.toArgb())
            }
            val x0 = ((s - dayStart) / span).coerceIn(0f, 1f) * w
            val x1 = ((e - dayStart) / span).coerceIn(0f, 1f) * w
            p.color = col
            c.drawRect(x0, 0f, maxOf(x1, x0 + 2f), h.toFloat(), p)
        }
        return bmp
    }

    private fun tile(ctx: Context, z: Int, x: Int, y: Int): Bitmap? {
        val dir = File(ctx.cacheDir, "widget_tiles").apply { mkdirs() }
        val f = File(dir, "$z-$x-$y.png")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 14 * 24 * 3_600_000L) {
            BitmapFactory.decodeFile(f.path)?.let { return it }
        }
        return try {
            val con = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
            con.setRequestProperty("User-Agent", ctx.packageName)
            con.connectTimeout = 8_000
            con.readTimeout = 8_000
            if (con.responseCode != 200) throw IllegalStateException("HTTP ${con.responseCode}")
            val bytes = con.inputStream.use { it.readBytes() }
            f.writeBytes(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        }
    }
}
