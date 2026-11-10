package com.egepaksoy.ghost.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.egepaksoy.ghost.R
import com.egepaksoy.ghost.core.AdaptivePolicy
import com.egepaksoy.ghost.core.Fix
import com.egepaksoy.ghost.data.Db
import com.egepaksoy.ghost.data.Prefs
import com.egepaksoy.ghost.ui.MainActivity
import com.egepaksoy.ghost.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Keeps recording while the app is closed. Android only allows that for a "foreground service",
 * which is why there is a permanent notification.
 */
class TrackingService : Service() {
    private lateinit var source: LocationSource
    private lateinit var db: Db
    private lateinit var prefs: Prefs
    private val policy = AdaptivePolicy()
    private val handler = Handler(Looper.getMainLooper())
    private var lastStored = 0L
    private var lastHeard = 0L
    private var started = false
    /** Fixes heard but not written yet, so the first minutes of a trip can be filled in once it starts. */
    private val unsaved = ArrayDeque<Fix>()

    private val heartbeat = object : Runnable {
        override fun run() {
            // If the system stopped sending fixes (it sometimes does when the phone lies still), ask for one.
            if (System.currentTimeMillis() - lastHeard > currentIntervalMs() * 2) source.requestSingle()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        db = Db.get(this)
        prefs = Prefs(this)
        source = LocationSource.create(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.tracking || !hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            // Android refused (e.g. started from the background without "allow all the time").
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            lastHeard = System.currentTimeMillis()
            handler.postDelayed(heartbeat, 60_000)
        }
        applyRequest()
        running.value = true
        Widgets.requestUpdate(this)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        source.stop()
        running.value = false
        Widgets.requestUpdate(this)
        super.onDestroy()
    }

    private fun currentIntervalMs(): Long =
        (if (policy.moving) prefs.movingSec else prefs.stationarySec) * 1000L

    private fun applyRequest() {
        // Even while stationary we listen at the faster rate when a fix is available for free,
        // so leaving a place is noticed quickly; only the slower rate is written to disk.
        source.start(currentIntervalMs(), prefs.movingSec * 1000L, ::onLocation)
        moving.value = policy.moving
    }

    private fun onLocation(loc: Location) {
        val now = System.currentTimeMillis()
        lastHeard = now
        // Some devices report a stale or bogus clock on the fix; never trust a time from the future.
        val time = loc.time.takeIf { it in 1..now } ?: now
        if (time <= lastStored) return
        val fix = Fix(time, loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 50f)

        val wasMoving = policy.moving
        val isMoving = if (fix.accuracy <= 150f) policy.onFix(fix) else wasMoving
        val due = time - lastStored >= currentIntervalMs() * 8 / 10
        if (due || isMoving != wasMoving) {
            if (isMoving && !wasMoving) {
                // Just left a place: keep the fixes heard on the way out as route waypoints too.
                val gap = prefs.movingSec * 1000L * 8 / 10
                for (u in unsaved) {
                    if (u.time > lastStored + gap && u.time < time - gap / 2) {
                        db.insertPoint(u)
                        lastStored = u.time
                    }
                }
            }
            unsaved.clear()
            db.insertPoint(fix)
            lastStored = time
        } else if (fix.accuracy <= 100f) {
            unsaved.addLast(fix)
            while (unsaved.isNotEmpty() && time - unsaved.first().time > 15 * 60_000L) unsaved.removeFirst()
        }
        if (isMoving != wasMoving) applyRequest()
    }

    /**
     * Android requires a notification while a background location service runs. It is kept as quiet as
     * Android allows: lowest importance (no status bar icon, collapsed, silent) and never updated.
     * The user can turn it off completely in the app's notification settings; recording keeps going.
     */
    private fun notification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.deleteNotificationChannel("tracking")
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Konum kaydı", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    description = "Kaydın sürdüğünü gösterir. Kapatabilirsin; kayıt etkilenmez."
                }
            )
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle("Ghost konum kaydı açık")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "tracking_quiet"
        private const val NOTIFICATION_ID = 1

        val running = MutableStateFlow(false)
        val moving = MutableStateFlow(true)

        fun hasLocationPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Starts the service, or re-reads the settings if it is already running. Returns false if Android refused. */
        fun start(ctx: Context): Boolean = try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
            true
        } catch (e: Exception) { false }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, TrackingService::class.java)) }
    }
}
