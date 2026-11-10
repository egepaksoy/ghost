package com.egepaksoy.ghost.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.PlaceDef
import com.egepaksoy.ghost.tracking.TrackingService
import java.time.LocalDate

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))

@Composable
private fun Section(text: String) =
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ---------------------------------------------------------------- Places

@Composable
fun PlacesScreen(vm: AppViewModel) {
    val places by vm.places.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlaceDef?>(null) }
    var deleting by remember { mutableStateOf<PlaceDef?>(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title("Yerler") }
        if (places.isEmpty()) item {
            Muted("Henüz isim verdiğin bir yer yok. Günlük ekranında bir durağa dokunup \"Ev\", \"İş\" gibi bir isim verebilirsin; sonraki ziyaretler o isimle görünür.")
        }
        items(places, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Muted("Yarıçap ${p.radius.toInt()} m")
                    }
                    IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, contentDescription = "Düzenle") }
                    IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, contentDescription = "Sil") }
                }
            }
        }
    }

    editing?.let { p ->
        var name by remember(p) { mutableStateOf(p.name) }
        var radius by remember(p) { mutableFloatStateOf(p.radius.toFloat()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Yeri düzenle") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("İsim") })
                    Text("Yarıçap: ${radius.toInt()} m", Modifier.padding(top = 16.dp))
                    Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..500f, steps = 8)
                    Muted("Bu mesafe içindeki duraklar bu yer sayılır.")
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.updatePlace(p.id, name.trim(), radius.toInt().toDouble()); editing = null
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("İptal") } },
        )
    }
    deleting?.let { p ->
        Confirm("\"${p.name}\" silinsin mi?", "Konum kayıtların silinmez; yalnızca bu isim kalkar.", "Sil",
            onConfirm = { vm.deletePlace(p.id); deleting = null }, onDismiss = { deleting = null })
    }
}

@Composable
private fun Confirm(title: String, text: String, button: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(button) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}

// ---------------------------------------------------------------- Stats

@Composable
fun StatsScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val days by vm.statsDays.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Title("İstatistik")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((n, label) in listOf(1 to "Bugün", 7 to "Son 7 gün", 30 to "Son 30 gün")) {
                    FilterChip(selected = days == n, onClick = { vm.statsDays.value = n }, label = { Text(label) })
                }
            }
        }
        val s = stats
        if (s != null && s.days == days) {
            if (s.places.isEmpty() && s.routes.isEmpty()) item { Muted("Bu dönem için kayıt yok.", Modifier.padding(top = 24.dp)) }
            if (s.travelMs > 0) item {
                Muted("Yolda geçen toplam süre ${Fmt.duration(s.travelMs)} · ${Fmt.distance(s.distanceM)}", Modifier.padding(top = 12.dp))
            }
            if (s.places.isNotEmpty()) {
                item { Section("Nerede ne kadar zaman geçirdin") }
                val max = s.places.first().totalMs.coerceAtLeast(1)
                items(s.places.take(30)) { p ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row {
                            Text(Fmt.spot(p.spot, addresses), Modifier.weight(1f).padding(end = 8.dp), maxLines = 1)
                            Text(Fmt.duration(p.totalMs), fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(
                            progress = { p.totalMs.toFloat() / max },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        if (s.days > 1) Muted("${p.visits} ziyaret")
                    }
                }
            }
            if (s.routes.isNotEmpty()) {
                item { Section("Yolculuk süreleri") }
                items(s.routes.take(30)) { r ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(Fmt.spot(r.from, addresses) + " → " + Fmt.spot(r.to, addresses), fontWeight = FontWeight.Medium)
                        Muted(
                            if (r.count == 1) "${Fmt.duration(r.avgMs)} · ${Fmt.distance(r.avgDistanceM)}"
                            else "${r.count} kez · ortalama ${Fmt.duration(r.avgMs)} (en kısa ${Fmt.duration(r.minMs)}, en uzun ${Fmt.duration(r.maxMs)}) · ${Fmt.distance(r.avgDistanceM)}"
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: AppViewModel, perms: Perms, actions: Actions) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val running by TrackingService.running.collectAsStateWithLifecycle()
    val isMoving by TrackingService.moving.collectAsStateWithLifecycle()
    val movingSec by vm.movingSec.collectAsStateWithLifecycle()
    val stationarySec by vm.stationarySec.collectAsStateWithLifecycle()
    val count by vm.pointCount.collectAsStateWithLifecycle()
    val lastFix by vm.lastFix.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.export(u, gpx = false) } }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let { u -> vm.export(u, gpx = true) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Title("Ayarlar")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Konum kaydı", style = MaterialTheme.typography.titleMedium)
                Muted(
                    when {
                        !tracking -> "Kapalı"
                        !running -> "Açık, ancak servis çalışmıyor. Kapatıp yeniden aç."
                        isMoving -> "Çalışıyor · hareket halinde"
                        else -> "Çalışıyor · sabit"
                    } + (lastFix?.let { " · son kayıt ${Fmt.time(it.time)}" } ?: "")
                )
            }
            Switch(checked = tracking, onCheckedChange = { if (it) actions.startTracking() else actions.stopTracking() })
        }

        Section("Kayıt sıklığı")
        Text("Hareket halindeyken")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((sec, label) in listOf(30 to "30 sn", 60 to "1 dk", 120 to "2 dk", 300 to "5 dk")) {
                FilterChip(selected = movingSec == sec, onClick = { vm.setIntervals(sec, maxOf(sec, stationarySec)) }, label = { Text(label) })
            }
        }
        Text("Bir yerde dururken", Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((sec, label) in listOf(60 to "1 dk", 180 to "3 dk", 300 to "5 dk", 600 to "10 dk")) {
                FilterChip(selected = stationarySec == sec, onClick = { vm.setIntervals(minOf(sec, movingSec), sec) }, label = { Text(label) })
            }
        }
        Muted("Sık kayıt, yolculuk sürelerini daha hassas ölçer ama pili biraz daha fazla kullanır.")

        Section("İzinler")
        PermRow("Konum", perms.fine, "İzin ver", actions.startTracking)
        PermRow("Arka planda konum (\"Her zaman izin ver\")", perms.background, "Aç", actions.requestBackground)
        PermRow("Bildirim", perms.notifications, "Aç", actions.openAppSettings)
        PermRow("Pil kısıtlaması kaldırıldı", perms.batteryExempt, "Kaldır", actions.requestBattery)
        Muted(
            "Samsung: kayıtlar yine de kesiliyorsa Ayarlar → Pil → Arka plan kullanım sınırları → \"Hiç uyku moduna geçmeyen uygulamalar\" listesine Ghost'u ekle.",
            Modifier.padding(top = 8.dp),
        )

        Section("Veriler")
        Muted("$count konum kaydı bu telefonda saklanıyor. Hiçbir veri dışarı gönderilmez.")
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { csv.launch("ghost-${LocalDate.now()}.csv") }) { Text("CSV dışa aktar") }
            OutlinedButton(onClick = { gpx.launch("ghost-${LocalDate.now()}.gpx") }) { Text("GPX dışa aktar") }
        }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 4.dp)) {
            Text("Tüm konum kayıtlarını sil", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        Confirm("Tüm konum kayıtları silinsin mi?", "$count kayıt kalıcı olarak silinir. Bu işlem geri alınamaz.", "Sil",
            onConfirm = { vm.deleteAllPoints(); confirmDelete = false }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, button: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text((if (ok) "✓  " else "✕  ") + label, Modifier.weight(1f).padding(vertical = 10.dp),
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        if (!ok) TextButton(onClick = onClick) { Text(button) }
    }
}
