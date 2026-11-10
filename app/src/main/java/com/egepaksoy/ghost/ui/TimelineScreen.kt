package com.egepaksoy.ghost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.Spot
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TripItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DayBar(vm: AppViewModel) {
    val date by vm.day.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.day.value = date.minusDays(1) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Önceki gün")
        }
        TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Text(Fmt.day(date), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { vm.day.value = date.plusDays(1) }, enabled = date < LocalDate.now()) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Sonraki gün")
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.day.value = minOf(d, LocalDate.now())
                    }
                    picking = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("İptal") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun TimelineScreen(vm: AppViewModel, perms: Perms, actions: Actions) {
    val data by vm.dayData.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf<Spot?>(null) }

    Column(Modifier.fillMaxSize()) {
        DayBar(vm)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SetupCards(tracking, perms, actions) }
            val d = data
            if (d != null && d.items.isEmpty()) {
                item {
                    Text(
                        if (tracking && d.date == LocalDate.now())
                            "Henüz kayıt yok. İlk konumlar birkaç dakika içinde burada görünecek."
                        else "Bu gün için kayıt yok.",
                        Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (d != null) {
                item {
                    val trips = d.items.filterIsInstance<TripItem>()
                    val stays = d.items.filterIsInstance<StayItem>()
                    Text(
                        "${stays.map { it.spot.key }.distinct().size} yer · ${trips.size} yolculuk" +
                            if (trips.isNotEmpty()) " · yolda ${Fmt.duration(trips.sumOf { it.durationMs })}, ${Fmt.distance(trips.sumOf { it.trip.distanceM })}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(d.items) { item ->
                    when (item) {
                        is StayItem -> StayRow(item, addresses) { naming = item.spot }
                        is TripItem -> TripRow(item)
                    }
                }
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

@Composable
private fun SetupCards(tracking: Boolean, perms: Perms, actions: Actions) {
    @Composable
    fun card(title: String, text: String, button: String, onClick: () -> Unit) {
        Card(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, Modifier.padding(top = 4.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onClick) { Text(button) }
            }
        }
    }
    Column {
        when {
            !tracking -> card(
                "Kayıt kapalı",
                "Başlatınca konumun dururken 5 dakikada, hareket halindeyken 1 dakikada bir kaydedilir. Veriler yalnızca bu telefonda kalır.",
                "Kaydı başlat", actions.startTracking,
            )
            !perms.fine -> card(
                "Konum izni gerekli", "Konum izni olmadan kayıt yapılamıyor.", "İzin ver", actions.startTracking,
            )
            !perms.background -> card(
                "Arka planda kayıt için bir adım daha",
                "Uygulama kapalıyken ve telefon yeniden başladığında da kaydın sürmesi için açılan ekranda \"Her zaman izin ver\"i seç.",
                "Her zaman izin ver", actions.requestBackground,
            )
            !perms.batteryExempt -> card(
                "Pil kısıtlamasını kaldır",
                "Telefon pil tasarrufu için uygulamayı uyutursa kayıtlar kesilir. Kesintisiz kayıt için kısıtlamayı kaldır.",
                "Kısıtlamayı kaldır", actions.requestBattery,
            )
        }
    }
}

@Composable
private fun StayRow(item: StayItem, addresses: Map<String, String>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    Fmt.spot(item.spot, addresses), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Fmt.time(item.start) + " – " + (if (item.ongoing) "şimdi" else Fmt.time(item.end)) +
                        if (item.spot.name == null) " · isim vermek için dokun" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Fmt.duration(item.durationMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TripRow(item: TripItem) {
    val t = item.trip
    Row(Modifier.padding(start = 26.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.width(22.dp))
        Column {
            Text(
                (if (t.uncertain) "≈ " else "") + Fmt.duration(item.durationMs) + " yolculuk" + (if (t.ongoing) " · sürüyor" else ""),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            )
            Text(
                Fmt.time(t.start) + " – " + Fmt.time(t.end) + " · " + Fmt.distance(t.distanceM) +
                    if (t.uncertain) " · arada kayıt eksik, süre yaklaşık" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("İsim") }, placeholder = { Text("Ev, İş, Spor salonu…") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
