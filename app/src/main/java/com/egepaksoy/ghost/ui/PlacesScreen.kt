package com.egepaksoy.ghost.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.core.PlaceDef
import com.egepaksoy.ghost.core.Spot

@Composable
fun PlacesScreen(vm: AppViewModel) {
    val places by vm.places.collectAsStateWithLifecycle()
    val summary by vm.placeSummary.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlaceDef?>(null) }
    var deleting by remember { mutableStateOf<PlaceDef?>(null) }
    var naming by remember { mutableStateOf<Spot?>(null) }
    val frequent = summary.filter { it.spot.placeId == null && it.totalMs >= 20 * 60_000L }.take(10)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Yerler", "Son 30 gün") }
        item { SectionLabel("Kayıtlı yerler") }
        if (places.isEmpty()) item {
            GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Default.Place, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Henüz isim verdiğin bir yer yok. Günlük ekranında bir durağa dokunup \"Ev\", \"İş\" gibi bir isim ver.",
                        Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(places, key = { it.id }) { p ->
            val spot = Spot("p:${p.id}", p.id, p.name, p.lat, p.lon)
            val total = summary.firstOrNull { it.spot.placeId == p.id }
            GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), onClick = { editing = p }) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpotAvatar(spot, null, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (total?.let { "${Fmt.duration(it.totalMs)} · ${it.visits} ziyaret" } ?: "Son 30 günde ziyaret yok") +
                                " · ${p.radius.toInt()} m",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { deleting = p }) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (frequent.isNotEmpty()) {
            item { SectionLabel("Sık gittiğin, adı olmayan yerler") }
            items(frequent, key = { it.spot.key }) { t ->
                GCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpotAvatar(t.spot, null, 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(Fmt.spot(t.spot, addresses), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Fmt.duration(t.totalMs)} · ${t.visits} ziyaret",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { naming = t.spot }) { Text("İsim ver") }
                    }
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
                    Text("Yarıçap: ${radius.toInt()} m", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                    Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..500f, steps = 8)
                    Text(
                        "Bu mesafe içindeki duraklar bu yer sayılır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        ConfirmDialog(
            "\"${p.name}\" silinsin mi?", "Konum kayıtların silinmez; yalnızca bu isim kalkar.", "Sil",
            onConfirm = { vm.deletePlace(p.id); deleting = null }, onDismiss = { deleting = null },
        )
    }
    naming?.let { s ->
        NameDialog("Bu yere isim ver", "", onSave = { vm.namePlace(s, it); naming = null }, onDismiss = { naming = null })
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, button: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(button, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
