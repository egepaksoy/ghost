package com.egepaksoy.ghost.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.Spot
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TimelineItem
import com.egepaksoy.ghost.core.TripItem
import com.egepaksoy.ghost.tracking.TrackingService
import org.osmdroid.util.GeoPoint
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TimelineScreen(vm: AppViewModel, perms: Perms, actions: Actions, openSettings: () -> Unit, openMap: (Long?) -> Unit) {
    val data by vm.dayData.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val running by TrackingService.running.collectAsStateWithLifecycle()
    val moving by TrackingService.moving.collectAsStateWithLifecycle()
    val lastFix by vm.lastFix.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf<Spot?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            DayHeader(vm) {
                IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") }
            }
        }
        item {
            val text = when {
                !tracking -> "Kayıt kapalı · başlatmak için dokun"
                !running -> "Kayıt başlatılıyor…"
                else -> "Kaydediliyor · " + (if (moving) "hareket halinde" else "bir yerdesin") +
                    (lastFix?.let { " · son ${Fmt.time(it.time)}" } ?: "")
            }
            val on = tracking && running
            Surface(
                onClick = { if (!tracking) actions.startTracking() else openSettings() },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (on) StartGreen else MaterialTheme.colorScheme.outline)
                    )
                    Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item { SetupCard(tracking, perms, actions) }

        val d = data ?: return@LazyColumn
        if (d.items.isEmpty()) {
            item { EmptyDay(tracking && d.date == LocalDate.now()) }
            return@LazyColumn
        }
        val badges = badges(d.items)
        item { DayCard(d, badges, onOpenMap = { openMap(null) }) }
        item { SectionLabel("Zaman çizelgesi") }
        itemsIndexed(d.items) { i, item ->
            val first = i == 0
            val last = i == d.items.lastIndex
            when (item) {
                is StayItem -> StayEntry(item, badges[item.spot.key], first, last, Fmt.spot(item.spot, addresses)) { naming = item.spot }
                is TripItem -> TripEntry(item, first, last) { openMap(item.trip.start) }
            }
        }
    }

    naming?.let { spot ->
        NameDialog(
            title = if (spot.placeId == null) "Bu yere isim ver" else "Yeri yeniden adlandır",
            initial = spot.name ?: "",
            onSave = { vm.namePlace(spot, it); naming = null },
            onDismiss = { naming = null },
        )
    }
}

/** Map of the day's route on real map tiles, plus the day's totals. */
@Composable
private fun DayCard(d: DayData, badges: Map<String, String>, onOpenMap: () -> Unit) {
    val trips = d.items.filterIsInstance<TripItem>()
    val stays = d.items.filterIsInstance<StayItem>()
    val map = rememberMapView(interactive = false)
    GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().clipToBounds(), update = { m ->
                val pts = MapDraw.render(m, d.items, MapDraw.Options(waypoints = false, pins = false, badges = badges))
                val fallback = stays.firstOrNull()?.let { GeoPoint(it.spot.lat, it.spot.lon) }
                MapDraw.frame(m, "${d.date}/${pts.size}", pts, fallback, 28)
            })
            Box(Modifier.fillMaxSize().clickable(onClick = onOpenMap))
            Surface(
                Modifier.align(Alignment.BottomEnd).padding(10.dp), shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(GIcons.Map, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Haritada aç", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                "© OpenStreetMap", Modifier.align(Alignment.BottomStart).padding(6.dp),
                style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF555555),
            )
        }
        Column(Modifier.padding(20.dp)) {
            Row {
                Stat("Yer", "${stays.map { it.spot.key }.distinct().size}", Modifier.weight(0.7f))
                Stat("Yolda", Fmt.duration(trips.sumOf { it.durationMs }), Modifier.weight(1.2f))
                Stat("Mesafe", Fmt.distance(trips.sumOf { it.trip.distanceM }), Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            DayStrip(d, Modifier.fillMaxWidth().height(12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (h in listOf("00", "06", "12", "18", "24")) {
                    Text(h, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 24-hour bar: coloured blocks for places, orange for travel. */
@Composable
private fun DayStrip(d: DayData, modifier: Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val dayStart = d.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val span = 24 * 3_600_000f
    Canvas(modifier.clip(CircleShape)) {
        drawRect(bg)
        for (it in d.items) {
            val (s, e, c) = when (it) {
                is StayItem -> Triple(it.start, it.end, placeColor(it.spot.key))
                is TripItem -> Triple(it.trip.start, it.trip.end, RouteOrange)
            }
            val x0 = ((s - dayStart) / span).coerceIn(0f, 1f) * size.width
            val x1 = ((e - dayStart) / span).coerceIn(0f, 1f) * size.width
            drawRect(c, Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(2f), size.height))
        }
    }
}

@Composable
private fun SetupCard(tracking: Boolean, perms: Perms, actions: Actions) {
    if (!tracking) {
        GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(20.dp)) {
                IconCircle(Icons.Default.LocationOn, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, 48.dp)
                Text("Gününü kaydetmeye başla", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLarge)
                Text(
                    "Ghost gittiğin yerleri, orada ne kadar kaldığını ve yolda geçen süreyi kendiliğinden kaydeder. " +
                        "Hareket halindeyken her dakika, dururken 5 dakikada bir konum alır. Veriler yalnızca bu telefonda kalır.",
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = actions.startTracking, Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Text("Kaydı başlat", Modifier.padding(start = 6.dp))
                }
            }
        }
        return
    }
    class Step(val title: String, val text: String, val done: Boolean, val action: () -> Unit)
    val steps = listOf(
        Step("Konum izni", "Konumunu kaydedebilmek için", perms.fine, actions.startTracking),
        Step("Her zaman izin ver", "Uygulama kapalıyken de kayıt sürsün", perms.background, actions.requestBackground),
        Step("Pil kısıtlamasını kaldır", "Telefon uygulamayı uyutmasın", perms.batteryExempt, actions.requestBattery),
    )
    val done = steps.count { it.done }
    if (done == steps.size) return
    val next = steps.indexOfFirst { !it.done }
    GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(20.dp)) {
            Text("Kurulumu tamamla", style = MaterialTheme.typography.titleMedium)
            Text("$done / ${steps.size} adım tamam · kesintisiz kayıt için gerekli", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            steps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val c = MaterialTheme.colorScheme.onTertiaryContainer
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .then(if (s.done) Modifier.background(MaterialTheme.colorScheme.tertiary) else Modifier.border(2.dp, c.copy(alpha = 0.4f), CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (s.done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiary)
                        else Text("${i + 1}", style = MaterialTheme.typography.labelLarge)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall)
                        Text(s.text, style = MaterialTheme.typography.bodySmall, color = c.copy(alpha = 0.75f))
                    }
                    if (i == next) Button(
                        onClick = s.action,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) { Text("Aç") }
                }
            }
        }
    }
}

@Composable
private fun EmptyDay(recordingToday: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconCircle(Icons.Default.Place, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, 72.dp)
        Text(
            if (recordingToday) "Kayıt sürüyor" else "Bu gün için kayıt yok",
            Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (recordingToday) "İlk durakların ve yolculukların birkaç dakika içinde burada görünecek."
            else "Kayıt açıkken gittiğin yerler burada zaman çizelgesi olarak görünür.",
            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

private val TimeCol = 46.dp
private val LineCol = 44.dp

@Composable
private fun StayEntry(item: StayItem, badge: String?, first: Boolean, last: Boolean, label: String, onClick: () -> Unit) {
    val color = placeColor(item.spot.key)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 12.dp)) {
        Text(
            Fmt.time(item.start), Modifier.width(TimeCol).padding(top = 26.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
        )
        Box(Modifier.width(LineCol).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val node = 36.dp.toPx()
                if (!first) drawLine(lineColor, Offset(x, 0f), Offset(x, node), 3.dp.toPx())
                if (!last) drawLine(lineColor, Offset(x, node), Offset(x, size.height), 3.dp.toPx())
            }
            SpotAvatar(item.spot, badge, 32.dp, Modifier.padding(top = 20.dp))
        }
        GCard(Modifier.weight(1f).padding(vertical = 6.dp), onClick = onClick) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        Fmt.time(item.start) + " – " + (if (item.ongoing) "şimdi" else Fmt.time(item.end)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.spot.name == null) Text(
                        "İsim vermek için dokun", Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
                    Text(
                        Fmt.duration(item.durationMs), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun TripEntry(item: TripItem, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val t = item.trip
    val dash = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Spacer(Modifier.width(TimeCol))
        Box(Modifier.width(LineCol).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                drawLine(
                    dash, Offset(x, if (first) size.height / 2 else 0f), Offset(x, if (last) size.height / 2 else size.height),
                    2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
            IconCircle(
                GIcons.Route,
                MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, 28.dp,
            )
        }
        Column(Modifier.weight(1f).align(Alignment.CenterVertically).padding(start = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Text(
                (if (t.uncertain) "≈ " else "") + "Yolculuk · " + Fmt.duration(item.durationMs) +
                    if (t.ongoing) " · sürüyor" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                Fmt.time(t.start) + " – " + Fmt.time(t.end) + " · " + Fmt.distance(t.distanceM),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (t.uncertain) "Arada kayıt eksik, süre yaklaşık" else "${Fmt.waypoints(t)} waypoint · haritada gör",
                style = MaterialTheme.typography.labelMedium,
                color = if (t.uncertain) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
        RouteThumb(t.path, Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).size(68.dp).align(Alignment.CenterVertically))
    }
}

@Composable
fun NameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("İsim") }, placeholder = { Text("Ev, İş, Spor salonu…") },
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    "Bu yerin çevresindeki tüm duraklar bu isimle görünür.", Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
