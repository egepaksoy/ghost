package com.egepaksoy.ghost.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Where fixes come from. Google's fused provider when the phone has it, plain Android otherwise. */
interface LocationSource {
    fun start(intervalMs: Long, minIntervalMs: Long, onFix: (Location) -> Unit)
    fun stop()
    /** Ask for one fix right now (used when regular updates went quiet). */
    fun requestSingle()

    companion object {
        fun create(ctx: Context): LocationSource {
            val gms = try {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
            } catch (e: Exception) { false }
            return if (gms) FusedSource(ctx) else FrameworkSource(ctx)
        }
    }
}

@SuppressLint("MissingPermission")
private class FusedSource(ctx: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(ctx)
    private var callback: LocationCallback? = null
    private var onFix: ((Location) -> Unit)? = null

    override fun start(intervalMs: Long, minIntervalMs: Long, onFix: (Location) -> Unit) {
        stop()
        this.onFix = onFix
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) { r.lastLocation?.let(onFix) }
        }
        callback = cb
        client.requestLocationUpdates(req, cb, Looper.getMainLooper())
    }

    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    override fun requestSingle() {
        val f = onFix ?: return
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc != null && callback != null) f(loc) }
    }
}

@SuppressLint("MissingPermission")
private class FrameworkSource(ctx: Context) : LocationSource {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    override fun start(intervalMs: Long, minIntervalMs: Long, onFix: (Location) -> Unit) {
        stop()
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        listener = l
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (p in lm.allProviders) {
                try { lm.requestLocationUpdates(p, intervalMs, 0f, l, Looper.getMainLooper()) } catch (e: Exception) {}
            }
        }
    }

    override fun stop() {
        listener?.let { lm.removeUpdates(it) }
        listener = null
    }

    override fun requestSingle() {}
}
