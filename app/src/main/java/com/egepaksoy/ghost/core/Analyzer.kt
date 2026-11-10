package com.egepaksoy.ghost.core

import java.util.Locale

/** A place the user gave a name to. */
data class PlaceDef(val id: Long, val name: String, val lat: Double, val lon: Double, val radius: Double)

/** A location a stay happened at: either a named place or an anonymous spot. */
data class Spot(val key: String, val placeId: Long?, val name: String?, val lat: Double, val lon: Double)

/** Maps coordinates to named places, and groups unnamed coordinates that are close together. */
class SpotResolver(private val places: List<PlaceDef>, private val groupRadiusM: Double = 150.0) {
    private val anon = ArrayList<Spot>()

    fun resolve(lat: Double, lon: Double): Spot {
        places.map { it to Geo.distance(it.lat, it.lon, lat, lon) }
            .filter { (p, d) -> d <= p.radius }
            .minByOrNull { it.second }
            ?.let { (p, _) -> return Spot("p:${p.id}", p.id, p.name, p.lat, p.lon) }
        anon.minByOrNull { Geo.distance(it.lat, it.lon, lat, lon) }
            ?.takeIf { Geo.distance(it.lat, it.lon, lat, lon) <= groupRadiusM }
            ?.let { return it }
        val s = Spot(String.format(Locale.US, "a:%.4f,%.4f", lat, lon), null, null, lat, lon)
        anon += s
        return s
    }
}

sealed interface TimelineItem
data class StayItem(val spot: Spot, val start: Long, val end: Long, val ongoing: Boolean) : TimelineItem {
    val durationMs get() = end - start
}
data class TripItem(val trip: Trip, val from: Spot?, val to: Spot?) : TimelineItem {
    val durationMs get() = trip.end - trip.start
}

data class PlaceTotal(val spot: Spot, val totalMs: Long, val visits: Int)
data class RouteStat(
    val from: Spot, val to: Spot, val count: Int,
    val avgMs: Long, val minMs: Long, val maxMs: Long, val avgDistanceM: Double,
)

object Analyzer {
    /** Segments that overlap [from, to); stays are cut to the range so durations add up per day. */
    fun timeline(segments: List<Segment>, resolver: SpotResolver, from: Long, to: Long): List<TimelineItem> {
        val spots = segments.map { if (it is Stay) resolver.resolve(it.lat, it.lon) else null }
        val out = ArrayList<TimelineItem>()
        segments.forEachIndexed { i, s ->
            if (s.end < from || s.start >= to || (s.end == from && s.start < from)) return@forEachIndexed
            when (s) {
                is Stay -> out += StayItem(spots[i]!!, maxOf(s.start, from), minOf(s.end, to), s.ongoing)
                is Trip -> out += TripItem(s, spots.getOrNull(i - 1), spots.getOrNull(i + 1))
            }
        }
        return out
    }

    fun placeTotals(items: List<TimelineItem>): List<PlaceTotal> =
        items.filterIsInstance<StayItem>().groupBy { it.spot.key }.map { (_, v) ->
            PlaceTotal(v.first().spot, v.sumOf { it.durationMs }, v.size)
        }.sortedByDescending { it.totalMs }

    /** Typical travel time for each from→to pair. Trips with holes in the data are left out. */
    fun routes(items: List<TimelineItem>): List<RouteStat> =
        items.filterIsInstance<TripItem>()
            .filter { it.from != null && it.to != null && !it.trip.uncertain && !it.trip.ongoing }
            .groupBy { it.from!!.key to it.to!!.key }
            .map { (_, v) ->
                val d = v.map { it.durationMs }
                RouteStat(
                    v.first().from!!, v.first().to!!, v.size,
                    d.average().toLong(), d.min(), d.max(), v.map { it.trip.distanceM }.average(),
                )
            }.sortedByDescending { it.count }
}
