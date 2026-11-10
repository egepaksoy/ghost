package com.egepaksoy.ghost.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.ui.graphics.toArgb
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TimelineItem
import com.egepaksoy.ghost.core.TripItem
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow

/** Draws a day's routes on an OpenStreetMap view, Strava style, through the real recorded points. */
object MapDraw {
    class Options(
        val selected: Long? = null,
        val waypoints: Boolean = true,
        val pins: Boolean = true,
        val badges: Map<String, String> = emptyMap(),
        val addresses: Map<String, String> = emptyMap(),
        val onSelect: ((Long) -> Unit)? = null,
    )

    fun newMap(ctx: Context, interactive: Boolean): MapView = MapView(ctx).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(interactive)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 3.0
        maxZoomLevel = 19.0
        controller.setZoom(5.0)
        controller.setCenter(GeoPoint(39.0, 35.0))
        if (!interactive) {
            isClickable = false
            setOnTouchListener { _, _ -> true }
        }
    }

    /** Replaces everything on the map; returns the points the camera should fit. */
    fun render(m: MapView, items: List<TimelineItem>, o: Options): List<GeoPoint> {
        InfoWindow.closeAllInfoWindowsOn(m)
        m.overlays.clear()
        val ctx = m.context
        val d = ctx.resources.displayMetrics.density
        val trips = items.filterIsInstance<TripItem>()
        val sel = trips.firstOrNull { it.trip.start == o.selected }
        val frame = ArrayList<GeoPoint>()
        val orange = RouteOrange.toArgb()

        for (t in trips) {
            val pts = t.trip.path.map { GeoPoint(it.lat, it.lon) }
            if (pts.size < 2) continue
            val dim = sel != null && t !== sel
            val width = (if (t === sel) 6f else 4.5f) * d
            if (!dim) m.overlays += line(m, pts, 0xFFFFFFFF.toInt(), width + 3 * d, null)
            // Dimmed routes use a solid light orange: a transparent line looks blotchy where segments overlap.
            m.overlays += line(m, pts, if (dim) 0xFFF7B899.toInt() else orange, width) {
                o.onSelect?.invoke(t.trip.start)
            }
            if (!dim) frame += pts
        }

        if (o.waypoints) {
            val dot = dot(ctx, 0xFFFFFFFF.toInt(), orange, 9)
            for (t in trips) {
                if (sel != null && t !== sel) continue
                for (w in t.trip.path.drop(1).dropLast(1)) {
                    m.overlays += Marker(m).apply {
                        position = GeoPoint(w.lat, w.lon)
                        icon = dot
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = Fmt.time(w.time)
                        snippet = "Waypoint · ±${w.accuracy.toInt()} m"
                    }
                }
            }
        }

        val pinMarkers = ArrayList<Marker>()
        val stays = items.filterIsInstance<StayItem>()
        for (s in stays.distinctBy { it.spot.key }) {
            val p = GeoPoint(s.spot.lat, s.spot.lon)
            val related = sel == null || sel.from?.key == s.spot.key || sel.to?.key == s.spot.key
            if (sel == null || related) frame += p
            val visits = stays.filter { it.spot.key == s.spot.key }
            val marker = Marker(m).apply {
                position = p
                if (o.pins) {
                    icon = pin(ctx, placeColor(s.spot.key).toArgb(), o.badges[s.spot.key] ?: "")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    alpha = if (related) 1f else 0.45f
                } else {
                    icon = dot(ctx, placeColor(s.spot.key).toArgb(), 0xFFFFFFFF.toInt(), 12)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                title = Fmt.spot(s.spot, o.addresses)
                snippet = visits.joinToString("<br>") {
                    Fmt.time(it.start) + " – " + (if (it.ongoing) "şimdi" else Fmt.time(it.end)) + " (" + Fmt.duration(it.durationMs) + ")"
                }
            }
            if (o.pins) pinMarkers += marker else m.overlays += marker
        }
        // Start (green) and end (grey) of the day's movement, or of the selected trip.
        val first = (sel ?: trips.firstOrNull())?.trip?.path?.firstOrNull()
        val last = (sel ?: trips.lastOrNull())?.trip?.path?.lastOrNull()
        if (first != null && last != null) {
            m.overlays += endpoint(m, GeoPoint(first.lat, first.lon), dot(ctx, StartGreen.toArgb(), 0xFFFFFFFF.toInt(), 15), "Başlangıç · ${Fmt.time(first.time)}")
            m.overlays += endpoint(m, GeoPoint(last.lat, last.lon), dot(ctx, EndGray.toArgb(), 0xFFFFFFFF.toInt(), 15), "Bitiş · ${Fmt.time(last.time)}")
        }

        m.overlays.addAll(pinMarkers)
        m.invalidate()
        return frame
    }

    /** Moves the camera to [points], but only when [key] changed, so the user's own panning is kept. */
    fun frame(m: MapView, key: String, points: List<GeoPoint>, fallback: GeoPoint?, borderDp: Int, topDp: Int = 0, bottomDp: Int = 0) {
        if (m.tag == key || (points.isEmpty() && fallback == null)) return
        m.tag = key
        val density = m.context.resources.displayMetrics.density
        val border = (borderDp * density).toInt()
        m.post {
            if (points.size >= 2 && points.any { it.latitude != points[0].latitude || it.longitude != points[0].longitude }) {
                // Grow the box so the route lands in the part of the map not covered by the
                // floating day switcher (top) and the bottom sheet (bottom).
                val box = BoundingBox.fromGeoPoints(points)
                val top = topDp * density
                val bottom = bottomDp * density
                val usable = (m.height - top - bottom - 2 * border).coerceAtLeast(m.height * 0.3f)
                val span = (box.latNorth - box.latSouth).coerceAtLeast(1e-5)
                val north = (box.latNorth + span * top / usable).coerceAtMost(85.0)
                val south = (box.latSouth - span * bottom / usable).coerceAtLeast(-85.0)
                m.zoomToBoundingBox(BoundingBox(north, box.lonEast, south, box.lonWest), false, border)
                if (m.zoomLevelDouble > 17.0) m.controller.setZoom(17.0)
            } else {
                m.controller.setZoom(16.0)
                m.controller.setCenter(points.firstOrNull() ?: fallback)
            }
        }
    }

    private fun line(m: MapView, pts: List<GeoPoint>, color: Int, width: Float, onClick: (() -> Unit)?) =
        Polyline(m).apply {
            setPoints(pts)
            outlinePaint.color = color
            outlinePaint.strokeWidth = width
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
            infoWindow = null
            if (onClick != null) setOnClickListener { _, _, _ -> onClick(); true }
        }

    private fun endpoint(m: MapView, p: GeoPoint, icon: Drawable, label: String) = Marker(m).apply {
        position = p
        this.icon = icon
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = label
    }

    private fun dot(ctx: Context, fill: Int, stroke: Int, sizeDp: Int): Drawable {
        val d = ctx.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke((2.5f * d).toInt().coerceAtLeast(1), stroke)
            setSize((sizeDp * d).toInt(), (sizeDp * d).toInt())
        }
    }

    /** A map pin in the place's colour with its letter or number inside. */
    private fun pin(ctx: Context, color: Int, label: String): Drawable {
        val d = ctx.resources.displayMetrics.density
        val w = (34 * d).toInt()
        val h = (44 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun shape(inset: Float) = Path().apply {
            val r = w / 2f - inset
            val cx = w / 2f
            val cy = w / 2f
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * 0.62f, cy + r * 0.78f)
            lineTo(cx, h - inset * 1.4f)
            lineTo(cx + r * 0.62f, cy + r * 0.78f)
            close()
        }
        p.color = 0x33000000
        c.drawPath(shape(0f), p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(shape(1.2f * d), p)
        p.color = color
        c.drawPath(shape(3.5f * d), p)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 14 * d
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText(label, w / 2f, w / 2f - (p.descent() + p.ascent()) / 2, p)
        return BitmapDrawable(ctx.resources, bmp)
    }
}
