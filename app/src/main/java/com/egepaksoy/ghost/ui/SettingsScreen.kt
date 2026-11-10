package com.egepaksoy.ghost.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egepaksoy.ghost.tracking.TrackingService
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: AppViewModel, perms: Perms, actions: Actions, onBack: () -> Unit) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val running by TrackingService.running.collectAsStateWithLifecycle()
    val isMoving by TrackingService.moving.collectAsStateWithLifecycle()
    val movingSec by vm.movingSec.collectAsStateWithLifecycle()
    val stationarySec by vm.stationarySec.collectAsStateWithLifecycle()
    val count by vm.pointCount.collectAsStateWithLifecycle()
    val lastFix by vm.lastFix.collectAsStateWithLifecycle()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.export(u, gpx = false) } }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let { u -> vm.export(u, gpx = true) } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Ayarlar") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") } },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Group("Kayıt") {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(
                        Icons.Default.LocationOn,
                        if (tracking && running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        if (tracking && running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Konum kaydı", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !tracking -> "Kapalı"
                                !running -> "Açık, servis başlatılıyor"
                                isMoving -> "Çalışıyor · hareket halinde"
                                else -> "Çalışıyor · bir yerdesin"
                            } + (lastFix?.let { " · son kayıt ${Fmt.time(it.time)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = tracking, onCheckedChange = { if (it) actions.startTracking() else actions.stopTracking() })
                }
            }

            Group("Kayıt sıklığı") {
                Column(Modifier.padding(16.dp)) {
                    Text("Hareket halindeyken", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Haritadaki waypoint'ler bu aralıkla alınır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Choice(listOf(30 to "30 sn", 60 to "1 dk", 120 to "2 dk"), movingSec) { vm.setIntervals(it, maxOf(it, stationarySec)) }
                    Text("Bir yerde dururken", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Daha seyrek kayıt pili korur; yola çıkış en fazla bu kadar geç fark edilir.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Choice(listOf(60 to "1 dk", 180 to "3 dk", 300 to "5 dk", 600 to "10 dk"), stationarySec) { vm.setIntervals(minOf(it, movingSec), it) }
                }
            }

            Group("Görünüm") {
                Column(Modifier.padding(16.dp)) {
                    Text("Tema", style = MaterialTheme.typography.titleSmall)
                    Choice(listOf(0 to "Sistem", 1 to "Açık", 2 to "Koyu"), theme) { vm.setTheme(it) }
                }
            }

            Group("İzinler") {
                Perm(Icons.Default.LocationOn, "Konum", "Konumunu kaydetmek için", perms.fine, actions.startTracking)
                HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Perm(Icons.Default.LocationOn, "Her zaman izin ver", "Uygulama kapalıyken kayıt için", perms.background, actions.requestBackground)
                HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Perm(
                    Icons.Default.Notifications, "Kayıt bildirimini gizle",
                    if (perms.notifications) "Sessiz ve küçük. Kapatırsan kayıt sürer, bildirim görünmez." else "Bildirim gizli",
                    !perms.notifications, actions.openNotificationSettings, button = "Kapat",
                )
                HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Perm(GIcons.Battery, "Pil kısıtlaması yok", "Telefon uygulamayı uyutmasın", perms.batteryExempt, actions.requestBattery)
            }
            GCard(Modifier.fillMaxWidth().padding(top = 12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "Samsung'da kayıtlar yine de kesiliyorsa: Ayarlar → Pil → Arka plan kullanım sınırları → " +
                            "\"Hiç uyku moduna geçmeyen uygulamalar\" listesine Ghost'u ekle.",
                        Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Group("Veriler") {
                Text(
                    "$count konum kaydı bu telefonda saklanıyor. Hiçbir veri dışarı gönderilmez.",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Action(GIcons.Download, "CSV olarak dışa aktar") { csv.launch("ghost-${LocalDate.now()}.csv") }
                Action(GIcons.Download, "GPX olarak dışa aktar (Strava, Google Earth)") { gpx.launch("ghost-${LocalDate.now()}.gpx") }
                Action(Icons.Default.Delete, "Tüm konum kayıtlarını sil", danger = true) { confirmDelete = true }
            }
            Text(
                "Ghost $version", Modifier.fillMaxWidth().padding(top = 24.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Tüm konum kayıtları silinsin mi?", "$count kayıt kalıcı olarak silinir. Bu işlem geri alınamaz.", "Sil",
            onConfirm = { vm.deleteAllPoints(); confirmDelete = false }, onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title, Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
    GCard(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun Choice(options: List<Pair<Int, String>>, value: Int, onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(selected = value == v, onClick = { onPick(v) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Perm(icon: ImageVector, title: String, text: String, ok: Boolean, onClick: () -> Unit, button: String = "Aç") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ok) Icon(Icons.Default.CheckCircle, contentDescription = "Tamam", tint = MaterialTheme.colorScheme.primary)
        else FilledTonalButton(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
        Text(title, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
