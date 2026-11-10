package com.egepaksoy.ghost.ui

import android.app.Application
import android.location.Geocoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.egepaksoy.ghost.core.Analyzer
import com.egepaksoy.ghost.core.PlaceTotal
import com.egepaksoy.ghost.core.RouteStat
import com.egepaksoy.ghost.core.Segmenter
import com.egepaksoy.ghost.core.Spot
import com.egepaksoy.ghost.core.SpotResolver
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TimelineItem
import com.egepaksoy.ghost.core.TripItem
import com.egepaksoy.ghost.data.Db
import com.egepaksoy.ghost.data.Exporter
import com.egepaksoy.ghost.data.Prefs
import com.egepaksoy.ghost.tracking.TrackingService
import com.egepaksoy.ghost.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

data class DayData(val date: LocalDate, val items: List<TimelineItem>)
data class StatsData(
    val days: Int, val places: List<PlaceTotal>, val routes: List<RouteStat>,
    val travelMs: Long, val distanceM: Double, val trips: Int,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val prefs = Prefs(app)
    private val dayMs = 24 * 3600_000L

    val day = MutableStateFlow(LocalDate.now())
    val statsDays = MutableStateFlow(7)
    val tracking = MutableStateFlow(prefs.tracking)
    val themeMode = MutableStateFlow(prefs.theme)
    val movingSec = MutableStateFlow(prefs.movingSec)
    val stationarySec = MutableStateFlow(prefs.stationarySec)
    /** Street names looked up for unnamed spots, by spot key. */
    val addresses = MutableStateFlow<Map<String, String>>(emptyMap())
    val message = MutableStateFlow<String?>(null)
    /** A trip (by its start time) the map should zoom to when it opens. */
    val focusTrip = MutableStateFlow<Long?>(null)

    private val minute = flow { while (true) { emit(Unit); delay(60_000) } }
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val places = db.version.map { db.places() }.flowOn(Dispatchers.IO).state(emptyList())
    val pointCount = db.version.map { db.pointCount() }.flowOn(Dispatchers.IO).state(0L)
    val lastFix = db.version.map { db.lastPoint() }.flowOn(Dispatchers.IO).state(null)

    val dayData = combine(day, db.version, minute) { d, _, _ -> d }
        .map { loadDay(it) }.flowOn(Dispatchers.Default).state(null)

    val stats = combine(statsDays, db.version, minute) { n, _, _ -> n }
        .map { loadStats(it) }.flowOn(Dispatchers.Default).state(null)

    /** Time spent at each spot over the last 30 days, for the places screen. */
    val placeSummary = combine(db.version, minute) { _, _ -> }
        .map { loadStats(30).places }.flowOn(Dispatchers.Default).state(emptyList())

    private fun startOf(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun load(from: Long, to: Long): List<TimelineItem> {
        // Look a day beyond both ends so a stay that crosses midnight is still recognised.
        val segs = Segmenter.segment(db.pointsBetween(from - dayMs, to + dayMs), now = System.currentTimeMillis())
        val items = Analyzer.timeline(segs, SpotResolver(db.places()), from, to)
        lookupAddresses(items)
        return items
    }

    private fun loadDay(d: LocalDate) = DayData(d, load(startOf(d), startOf(d.plusDays(1))))

    private fun loadStats(days: Int): StatsData {
        val today = LocalDate.now()
        val items = load(startOf(today.minusDays(days - 1L)), startOf(today.plusDays(1)))
        val trips = items.filterIsInstance<TripItem>()
        return StatsData(
            days, Analyzer.placeTotals(items), Analyzer.routes(items),
            trips.sumOf { it.durationMs }, trips.sumOf { it.trip.distanceM }, trips.size,
        )
    }

    private val pending = ConcurrentHashMap.newKeySet<String>()

    private fun lookupAddresses(items: List<TimelineItem>) {
        val spots = items.filterIsInstance<StayItem>().map { it.spot }
            .filter { it.name == null && it.key !in addresses.value }.distinctBy { it.key }
        for (s in spots) {
            if (!pending.add(s.key)) continue
            viewModelScope.launch(Dispatchers.IO) {
                val label = db.cachedAddress(s.key) ?: geocode(s)?.also { db.cacheAddress(s.key, it) }
                if (label != null) addresses.update { it + (s.key to label) }
                pending.remove(s.key)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun geocode(s: Spot): String? = try {
        if (!Geocoder.isPresent()) null
        else Geocoder(getApplication(), Fmt.tr).getFromLocation(s.lat, s.lon, 1)?.firstOrNull()?.let { a ->
            listOfNotNull(a.thoroughfare ?: a.featureName, a.subLocality ?: a.subAdminArea ?: a.locality)
                .distinct().joinToString(", ").ifBlank { null }
        }
    } catch (e: Exception) { null }

    fun setTracking(on: Boolean) {
        prefs.tracking = on
        tracking.value = on
        val ctx = getApplication<Application>()
        if (on) TrackingService.start(ctx) else TrackingService.stop(ctx)
        Widgets.requestUpdate(ctx)
    }

    fun setTheme(mode: Int) {
        prefs.theme = mode
        themeMode.value = mode
    }

    fun setIntervals(moving: Int, stationary: Int) {
        prefs.movingSec = moving
        prefs.stationarySec = stationary
        movingSec.value = moving
        stationarySec.value = stationary
        if (prefs.tracking) TrackingService.start(getApplication())
    }

    fun namePlace(spot: Spot, name: String) = viewModelScope.launch(Dispatchers.IO) {
        if (spot.placeId != null) {
            val radius = db.places().firstOrNull { it.id == spot.placeId }?.radius ?: 100.0
            db.updatePlace(spot.placeId, name, radius)
        } else db.addPlace(name, spot.lat, spot.lon, 100.0)
    }

    fun updatePlace(id: Long, name: String, radius: Double) =
        viewModelScope.launch(Dispatchers.IO) { db.updatePlace(id, name, radius) }

    fun deletePlace(id: Long) = viewModelScope.launch(Dispatchers.IO) { db.deletePlace(id) }

    fun deleteAllPoints() = viewModelScope.launch(Dispatchers.IO) { db.deleteAllPoints() }

    fun export(uri: Uri, gpx: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        message.value = try {
            getApplication<Application>().contentResolver.openOutputStream(uri)!!.use {
                if (gpx) Exporter.gpx(db, it) else Exporter.csv(db, it)
            }
            "Dışa aktarıldı"
        } catch (e: Exception) { "Dışa aktarılamadı: ${e.message}" }
    }
}
