package com.egepaksoy.ghost.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TripItem
import org.osmdroid.util.GeoPoint

@Composable
fun MapScreen(vm: AppViewModel) {
    val data by vm.dayData.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val lastFix by vm.lastFix.collectAsStateWithLifecycle()
    val focus by vm.focusTrip.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(focus) {
        if (focus != null) { selected = focus; vm.focusTrip.value = null }
    }
    val items = data?.items.orEmpty()
    val trips = items.filterIsInstance<TripItem>()
    val sel = trips.firstOrNull { it.trip.start == selected }
    val badges = badges(items)
    val map = rememberMapView(interactive = true)
    val sheet = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = 196.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetShadowElevation = 8.dp,
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                if (sel != null) TripDetail(sel, addresses, badges) { selected = null }
                else DaySheet(items, addresses, badges) { selected = it }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().clipToBounds(), update = { m ->
                val pts = MapDraw.render(
                    m, items,
                    MapDraw.Options(selected = sel?.trip?.start, badges = badges, addresses = addresses, onSelect = { selected = it }),
                )
                MapDraw.frame(m, "${data?.date}/${sel?.trip?.start}/${pts.size}", pts, lastFix?.let { GeoPoint(it.lat, it.lon) }, 40, topDp = 80, bottomDp = 196)
            })
            DayPill(vm, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            Text(
                "© OpenStreetMap katkıcıları", Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF555555),
            )
        }
    }
}

@Composable
private fun DaySheet(items: List<com.egepaksoy.ghost.core.TimelineItem>, addresses: Map<String, String>, badges: Map<String, String>, onSelect: (Long) -> Unit) {
    val trips = items.filterIsInstance<TripItem>()
    val stays = items.filterIsInstance<StayItem>()
    if (items.isEmpty()) {
        Text("Bu gün için kayıt yok", style = MaterialTheme.typography.titleMedium)
        Text("Önceki günlere üstteki oklarla geçebilirsin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("${stays.map { it.spot.key }.distinct().size} yer · ${trips.size} yolculuk", style = MaterialTheme.typography.titleLarge)
    Text(
        "Yolda ${Fmt.duration(trips.sumOf { it.durationMs })} · ${Fmt.distance(trips.sumOf { it.trip.distanceM })} · bir yolculuğa dokun",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    for (it in items) {
        when (it) {
            is StayItem -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SpotAvatar(it.spot, badges[it.spot.key], 30.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(Fmt.spot(it.spot, addresses), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Fmt.time(it.start) + " – " + (if (it.ongoing) "şimdi" else Fmt.time(it.end)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Fmt.duration(it.durationMs), style = MaterialTheme.typography.labelLarge)
            }
            is TripItem -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSelect(it.trip.start) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteThumb(it.trip.path, Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("${Fmt.tripMode(it.trip)} · ${Fmt.duration(it.durationMs)}", style = MaterialTheme.typography.titleSmall, color = RouteOrange)
                    Text("${Fmt.distance(it.trip.distanceM)} · ${Fmt.waypoints(it.trip)} waypoint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Gör", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TripDetail(t: TripItem, addresses: Map<String, String>, badges: Map<String, String>, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircle(if (Fmt.isWalk(t.trip)) GIcons.Walk else GIcons.Car, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(Fmt.tripMode(t.trip), style = MaterialTheme.typography.labelLarge, color = RouteOrange)
            Text(
                (if (t.trip.uncertain) "≈ " else "") + Fmt.duration(t.durationMs),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Tüm günü göster") }
    }
    Spacer(Modifier.height(12.dp))
    Endpoint(StartGreen, Fmt.spot(t.from, addresses), Fmt.time(t.trip.start))
    Endpoint(EndGray, Fmt.spot(t.to, addresses), if (t.trip.ongoing) "sürüyor" else Fmt.time(t.trip.end))
    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("Mesafe", Fmt.distance(t.trip.distanceM))
        Metric("Ort. hız", "${Fmt.speedKmh(t.trip).toInt()} km/sa")
        Metric("Waypoint", "${Fmt.waypoints(t.trip)}")
    }
    if (t.trip.uncertain) Text(
        "Bu yolculuk sırasında kayıtlarda boşluk var; süre ve rota yaklaşık.", Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun Endpoint(color: Color, name: String, time: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).then(Modifier.padding(0.dp))) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(color) }
        }
        Text(name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
