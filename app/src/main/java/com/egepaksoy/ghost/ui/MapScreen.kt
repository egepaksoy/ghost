package com.egepaksoy.ghost.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.core.content.ContextCompat
import com.egepaksoy.ghost.R
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TripItem
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(vm: AppViewModel) {
    val data by vm.dayData.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val lastFix by vm.lastFix.collectAsStateWithLifecycle()
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val ctx = LocalContext.current

    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(39.0, 35.0))
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume() else if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    Column(Modifier.fillMaxSize()) {
        DayBar(vm)
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().clipToBounds(), update = { m ->
                val items = data?.items.orEmpty()
                m.overlays.clear()
                val all = ArrayList<GeoPoint>()
                for (t in items.filterIsInstance<TripItem>()) {
                    val pts = t.trip.path.map { GeoPoint(it.lat, it.lon) }
                    all += pts
                    m.overlays += Polyline(m).apply {
                        setPoints(pts)
                        outlinePaint.color = lineColor
                        outlinePaint.strokeWidth = 9f
                        infoWindow = null
                    }
                }
                for (s in items.filterIsInstance<StayItem>().distinctBy { it.spot.key }) {
                    val p = GeoPoint(s.spot.lat, s.spot.lon)
                    all += p
                    val visits = items.filterIsInstance<StayItem>().filter { it.spot.key == s.spot.key }
                    m.overlays += Marker(m).apply {
                        position = p
                        icon = ContextCompat.getDrawable(m.context, R.drawable.ic_marker)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = Fmt.spot(s.spot, addresses)
                        snippet = visits.joinToString("<br>") {
                            Fmt.time(it.start) + " – " + (if (it.ongoing) "şimdi" else Fmt.time(it.end)) + " (" + Fmt.duration(it.durationMs) + ")"
                        }
                    }
                }
                // Re-frame only when the day or the set of places changes, so panning is not undone every minute.
                val frame = "${data?.date}/${all.size}"
                if (m.tag != frame && (all.isNotEmpty() || lastFix != null)) {
                    m.tag = frame
                    m.post {
                        if (all.size >= 2 && all.any { it.latitude != all[0].latitude || it.longitude != all[0].longitude }) {
                            m.zoomToBoundingBox(BoundingBox.fromGeoPoints(all).increaseByScale(1.4f), false, 48)
                            if (m.zoomLevelDouble > 17.0) m.controller.setZoom(17.0)
                        } else {
                            m.controller.setZoom(16.0)
                            m.controller.setCenter(all.firstOrNull() ?: GeoPoint(lastFix!!.lat, lastFix!!.lon))
                        }
                    }
                }
                m.invalidate()
            })
            if (data?.items?.isEmpty() == true) {
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp, shadowElevation = 2.dp,
                ) { Text("Bu gün için kayıt yok", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            Text(
                "© OpenStreetMap katkıcıları",
                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.DarkGray,
            )
        }
    }
}
