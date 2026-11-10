package com.egepaksoy.ghost.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.Fix
import com.egepaksoy.ghost.core.Spot
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TimelineItem
import org.osmdroid.views.MapView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

@Composable
fun GCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = color)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, content = content)
    else Card(modifier = modifier, shape = shape, colors = colors, content = content)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(
        text, modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )

/** Short label drawn inside a place's circle: the first letter of its name, or its order number for the day. */
fun badges(items: List<TimelineItem>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    var n = 0
    for (s in items.filterIsInstance<StayItem>()) {
        if (s.spot.key in out) continue
        out[s.spot.key] = s.spot.name?.take(1)?.uppercase(Fmt.tr) ?: (++n).toString()
    }
    return out
}

@Composable
fun SpotAvatar(spot: Spot?, badge: String? = null, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val color = spot?.let { placeColor(it.key) } ?: MaterialTheme.colorScheme.outline
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        val text = badge ?: spot?.name?.take(1)?.uppercase(Fmt.tr)
        if (text != null) Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        else Icon(Icons.Default.Place, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun IconCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, fg: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** A route drawn without a map, Strava style: orange line, green start, grey end. */
@Composable
fun RouteThumb(path: List<Fix>, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(bg)) {
        if (path.size < 2) return@Canvas
        val k = cos(Math.toRadians(path.map { it.lat }.average()))
        val xs = path.map { it.lon * k }
        val ys = path.map { -it.lat }
        val spanX = max(xs.max() - xs.min(), 1e-7)
        val spanY = max(ys.max() - ys.min(), 1e-7)
        val pad = 9.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val scale = min(w / spanX, h / spanY)
        val ox = pad + (w - spanX * scale) / 2
        val oy = pad + (h - spanY * scale) / 2
        fun pt(i: Int) = Offset((ox + (xs[i] - xs.min()) * scale).toFloat(), (oy + (ys[i] - ys.min()) * scale).toFloat())
        val p = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until path.size) lineTo(pt(i).x, pt(i).y)
        }
        drawPath(p, RouteOrange, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, 4.5.dp.toPx(), pt(0))
        drawCircle(StartGreen, 3.dp.toPx(), pt(0))
        drawCircle(Color.White, 4.5.dp.toPx(), pt(path.size - 1))
        drawCircle(EndGray, 3.dp.toPx(), pt(path.size - 1))
    }
}

@Composable
fun rememberMapView(interactive: Boolean): MapView {
    val ctx = LocalContext.current
    val map = remember { MapDraw.newMap(ctx, interactive) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume() else if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }
    return map
}

@Composable
fun DayHeader(vm: AppViewModel, actions: @Composable RowScope.() -> Unit = {}) {
    val date by vm.day.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { picking = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayTitle(date), style = MaterialTheme.typography.headlineMedium)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Tarih seç", Modifier.padding(start = 2.dp))
            }
            Text(Fmt.dayFull(date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { vm.day.value = date.minusDays(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki gün")
        }
        IconButton(onClick = { vm.day.value = date.plusDays(1) }, enabled = date < LocalDate.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki gün")
        }
        actions()
    }
    if (picking) DayPicker(date, onPick = { vm.day.value = it }, onDismiss = { picking = false })
}

/** Compact floating day switcher used on top of the map. */
@Composable
fun DayPill(vm: AppViewModel, modifier: Modifier = Modifier) {
    val date by vm.day.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.day.value = date.minusDays(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki gün")
            }
            Text(
                Fmt.dayTitle(date), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clip(CircleShape).clickable { picking = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            IconButton(onClick = { vm.day.value = date.plusDays(1) }, enabled = date < LocalDate.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki gün")
            }
        }
    }
    if (picking) DayPicker(date, onPick = { vm.day.value = it }, onDismiss = { picking = false })
}

@Composable
private fun DayPicker(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(minOf(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now()))
                }
                onDismiss()
            }) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    ) { DatePicker(state) }
}
