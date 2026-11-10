package com.egepaksoy.ghost

import android.app.Application
import com.egepaksoy.ghost.data.Db
import com.egepaksoy.ghost.data.Prefs
import com.egepaksoy.ghost.widget.Widgets
import com.egepaksoy.ghost.tracking.TrackingService
import com.egepaksoy.ghost.tracking.WatchdogWorker
import org.osmdroid.config.Configuration
import java.io.File

class GhostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "tiles")
        }
        Db.get(this).onChange = { Widgets.requestUpdate(this) }
        Widgets.requestUpdate(this)
        // The watchdog is only a safety net; never let it stop the app from opening.
        try { WatchdogWorker.schedule(this) } catch (e: Exception) {}
        // If the process was restarted while recording was on, pick it up again.
        if (Prefs(this).tracking && TrackingService.hasLocationPermission(this)) TrackingService.start(this)
    }
}
