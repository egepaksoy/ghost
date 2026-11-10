package com.egepaksoy.ghost.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(style, style)
                onDispose {}
            }
            GhostTheme(dark) { App(vm) }
        }
    }
}

/** What the phone currently allows the app to do. */
data class Perms(val fine: Boolean, val background: Boolean, val notifications: Boolean, val batteryExempt: Boolean)

fun readPerms(ctx: Context): Perms {
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return Perms(
        fine = has(Manifest.permission.ACCESS_FINE_LOCATION),
        background = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        notifications = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS),
        batteryExempt = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName),
    )
}

class Actions(
    val startTracking: () -> Unit,
    val stopTracking: () -> Unit,
    val requestBackground: () -> Unit,
    val requestBattery: () -> Unit,
    val openAppSettings: () -> Unit,
    val openNotificationSettings: () -> Unit,
)

@SuppressLint("BatteryLife")
@Composable
fun App(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(readPerms(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = readPerms(ctx) }

    fun openAppSettings() {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }

    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        perms = readPerms(ctx)
        if (perms.fine) vm.setTracking(true)
        else if (!(ctx as Activity).shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) openAppSettings()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        perms = readPerms(ctx)
        if (!granted && !(ctx as Activity).shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) openAppSettings()
    }

    val actions = remember {
        Actions(
            startTracking = {
                if (readPerms(ctx).fine) vm.setTracking(true)
                else fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            stopTracking = { vm.setTracking(false) },
            requestBackground = {
                if (Build.VERSION.SDK_INT >= 29) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            requestBattery = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                } catch (e: Exception) {
                    try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e2: Exception) { openAppSettings() }
                }
            },
            openAppSettings = ::openAppSettings,
            openNotificationSettings = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                } catch (e: Exception) { openAppSettings() }
            },
        )
    }

    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.message.value = null }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settings) { settings = false }
    val tabs = listOf(
        "Günlük" to GIcons.Timeline,
        "Harita" to GIcons.Map,
        "İstatistik" to GIcons.Bars,
        "Yerler" to Icons.Default.Place,
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!settings) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (settings) SettingsScreen(vm, perms, actions, onBack = { settings = false })
            else when (tab) {
                0 -> TimelineScreen(vm, perms, actions, openSettings = { settings = true }, openMap = { trip ->
                    vm.focusTrip.value = trip
                    tab = 1
                })
                1 -> MapScreen(vm)
                2 -> StatsScreen(vm)
                else -> PlacesScreen(vm)
            }
        }
    }
}
