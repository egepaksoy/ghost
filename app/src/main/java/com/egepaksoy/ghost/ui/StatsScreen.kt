package com.egepaksoy.ghost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.RouteStat

@Composable
fun StatsScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val days by vm.statsDays.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenTitle("İstatistik", "Zamanını nerede ve yolda nasıl geçirdin")
            val options = listOf(1 to "Bugün", 7 to "7 gün", 30 to "30 gün")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                options.forEachIndexed { i, (n, label) ->
                    SegmentedButton(
                        selected = days == n, onClick = { vm.statsDays.value = n },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
        }
        val s = stats
        if (s == null || s.days != days) return@LazyColumn
        if (s.places.isEmpty() && s.routes.isEmpty()) {
            item {
                Text(
                    "Bu dönem için kayıt yok.", Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(GIcons.Clock, "Yolda geçen", Fmt.duration(s.travelMs), Modifier.weight(1f))
                    Tile(GIcons.Route, "Mesafe", Fmt.distance(s.distanceM), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(GIcons.Car, "Yolculuk", "${s.trips}", Modifier.weight(1f))
                    Tile(Icons.Default.Place, "Farklı yer", "${s.places.size}", Modifier.weight(1f))
                }
            }
        }
        if (s.places.isNotEmpty()) {
            item { SectionLabel("Nerede ne kadar kaldın") }
            item {
                val total = s.places.sumOf { it.totalMs }.coerceAtLeast(1)
                val max = s.places.first().totalMs.coerceAtLeast(1)
                GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        for (p in s.places.take(12)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpotAvatar(p.spot, null, 36.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            Fmt.spot(p.spot, addresses), Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(Fmt.duration(p.totalMs), style = MaterialTheme.typography.titleSmall)
                                    }
                                    Box(
                                        Modifier.padding(vertical = 6.dp).fillMaxWidth().height(6.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(p.totalMs.toFloat() / max).fillMaxHeight().clip(CircleShape)
                                                .background(placeColor(p.spot.key))
                                        )
                                    }
                                    Text(
                                        "%${p.totalMs * 100 / total} · ${p.visits} ziyaret",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (s.routes.isNotEmpty()) {
            item { SectionLabel("Yolculuk süreleri") }
            items(s.routes.take(20)) { r -> RouteCard(r, addresses) }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    GCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            IconCircle(icon, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 36.dp)
            Text(value, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RouteCard(r: RouteStat, addresses: Map<String, String>) {
    GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpotAvatar(r.from, null, 24.dp)
                    Text(Fmt.spot(r.from, addresses), Modifier.padding(horizontal = 8.dp).weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    SpotAvatar(r.to, null, 24.dp, Modifier.padding(start = 8.dp))
                    Text(Fmt.spot(r.to, addresses), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (r.count == 1) "1 yolculuk · ${Fmt.distance(r.avgDistanceM)}"
                    else "${r.count} yolculuk · en kısa ${Fmt.duration(r.minMs)} · en uzun ${Fmt.duration(r.maxMs)} · ${Fmt.distance(r.avgDistanceM)}",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                Text(Fmt.duration(r.avgMs), style = MaterialTheme.typography.titleLarge, color = RouteOrange)
                Text(if (r.count == 1) "süre" else "ortalama", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
