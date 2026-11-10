package com.egepaksoy.ghost.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One recorded location. [time] is epoch millis, [accuracy] is the reported radius in metres. */
data class Fix(val time: Long, val lat: Double, val lon: Double, val accuracy: Float)

object Geo {
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

sealed interface Segment {
    val start: Long
    val end: Long
    val ongoing: Boolean
}

/** A period spent in one place. */
data class Stay(
    override val start: Long,
    override val end: Long,
    val lat: Double,
    val lon: Double,
    override val ongoing: Boolean = false,
) : Segment

/** Movement between two stays (or before the first / after the last one). */
data class Trip(
    override val start: Long,
    override val end: Long,
    val distanceM: Double,
    val path: List<Fix>,
    /** True when there is a long hole in the data, so the duration is only an upper bound. */
    val uncertain: Boolean,
    override val ongoing: Boolean = false,
) : Segment

/** Turns raw location fixes into an ordered list of stays and trips. */
object Segmenter {
    data class Config(
        val radiusM: Double = 100.0,
        val minStayMs: Long = 7 * MIN,
        /** A cluster that reaches the end of the data counts as a stay sooner ("you are here now"). */
        val tailMinStayMs: Long = 3 * MIN,
        val maxAccuracyM: Float = 100f,
        val mergeGapMs: Long = 15 * MIN,
        val uncertainGapMs: Long = 12 * MIN,
        val ongoingMs: Long = 15 * MIN,
    )

    private const val MIN = 60_000L

    private class Cluster(val first: Int, var last: Int, var lat: Double, var lon: Double, var count: Int)

    fun segment(input: List<Fix>, cfg: Config = Config(), now: Long? = null): List<Segment> {
        val pts = input.filter { it.accuracy <= cfg.maxAccuracyM }.sortedBy { it.time }
        val n = pts.size
        if (n == 0) return emptyList()

        val clusters = findClusters(pts, cfg)
        val out = ArrayList<Segment>()

        if (clusters.isEmpty()) {
            if (n == 1) out += Stay(pts[0].time, pts[0].time, pts[0].lat, pts[0].lon)
            else out += trip(pts[0].time, pts[n - 1].time, pts, cfg)
        } else {
            clusters.forEachIndexed { k, c ->
                val centre = Fix(pts[c.first].time, c.lat, c.lon, 0f)
                if (k == 0) {
                    if (c.first > 0) {
                        out += trip(pts[0].time, pts[c.first].time, pts.subList(0, c.first) + centre, cfg)
                    }
                } else {
                    val p = clusters[k - 1]
                    val from = Fix(pts[p.last].time, p.lat, p.lon, 0f)
                    val mids = pts.subList(p.last + 1, c.first)
                    out += trip(pts[p.last].time, pts[c.first].time, listOf(from) + mids + centre, cfg)
                }
                out += Stay(pts[c.first].time, pts[c.last].time, c.lat, c.lon)
            }
            val l = clusters.last()
            if (l.last < n - 1) {
                val from = Fix(pts[l.last].time, l.lat, l.lon, 0f)
                out += trip(pts[l.last].time, pts[n - 1].time, listOf(from) + pts.subList(l.last + 1, n), cfg)
            }
        }

        if (now != null && out.isNotEmpty()) {
            val last = out.last()
            if (now >= last.end && now - last.end <= cfg.ongoingMs) {
                out[out.size - 1] = when (last) {
                    is Stay -> last.copy(end = now, ongoing = true)
                    is Trip -> last.copy(ongoing = true)
                }
            }
        }
        return out
    }

    private fun findClusters(pts: List<Fix>, cfg: Config): List<Cluster> {
        val n = pts.size
        val raw = ArrayList<Cluster>()
        var i = 0
        while (i < n) {
            var sLat = pts[i].lat
            var sLon = pts[i].lon
            var cnt = 1
            var last = i
            var j = i + 1
            while (j < n) {
                val cLat = sLat / cnt
                val cLon = sLon / cnt
                if (Geo.distance(cLat, cLon, pts[j].lat, pts[j].lon) <= cfg.radiusM) {
                    sLat += pts[j].lat; sLon += pts[j].lon; cnt++
                    last = j
                    j++
                } else if (j + 1 < n &&
                    Geo.distance(cLat, cLon, pts[j + 1].lat, pts[j + 1].lon) <= cfg.radiusM &&
                    pts[j + 1].time - pts[last].time <= cfg.mergeGapMs
                ) {
                    j++ // a single stray fix (GPS jump) - ignore it
                } else break
            }
            val need = if (last == n - 1) cfg.tailMinStayMs else cfg.minStayMs
            if (pts[last].time - pts[i].time >= need) {
                raw += Cluster(i, last, sLat / cnt, sLon / cnt, cnt)
                i = last + 1
            } else i++
        }

        // Join neighbouring clusters that are really the same place.
        val merged = ArrayList<Cluster>()
        for (c in raw) {
            val p = merged.lastOrNull()
            if (p != null && Geo.distance(p.lat, p.lon, c.lat, c.lon) <= cfg.radiusM &&
                (c.first == p.last + 1 || pts[c.first].time - pts[p.last].time <= cfg.mergeGapMs)
            ) {
                val total = p.count + c.count
                p.lat = (p.lat * p.count + c.lat * c.count) / total
                p.lon = (p.lon * p.count + c.lon * c.count) / total
                p.count = total
                p.last = c.last
            } else merged += c
        }
        return merged
    }

    private fun trip(start: Long, end: Long, path: List<Fix>, cfg: Config): Trip {
        var dist = 0.0
        var maxGap = 0L
        for (k in 1 until path.size) {
            dist += Geo.distance(path[k - 1].lat, path[k - 1].lon, path[k].lat, path[k].lon)
            maxGap = maxOf(maxGap, path[k].time - path[k - 1].time)
        }
        return Trip(start, end, dist, path, uncertain = maxGap > cfg.uncertainGapMs)
    }
}

/**
 * Decides whether the phone is moving (record often) or staying put (record rarely).
 * Pure logic so it can be unit tested.
 */
class AdaptivePolicy(
    private val leaveRadiusM: Double = 120.0,
    private val settleRadiusM: Double = 70.0,
    private val settleMs: Long = 4 * 60_000L,
) {
    var moving: Boolean = true
        private set
    private var anchor: Fix? = null
    private val recent = ArrayDeque<Fix>()

    /** Feed a new fix; returns the resulting state. */
    fun onFix(fix: Fix): Boolean {
        if (moving) {
            recent.addLast(fix)
            while (recent.size > 1 && fix.time - recent[1].time >= settleMs) recent.removeFirst()
            val settled = fix.time - recent.first().time >= settleMs &&
                recent.all { Geo.distance(it.lat, it.lon, fix.lat, fix.lon) <= settleRadiusM }
            if (settled) {
                moving = false
                anchor = fix
                recent.clear()
            }
        } else {
            val a = anchor
            if (a == null) {
                anchor = fix
            } else if (Geo.distance(a.lat, a.lon, fix.lat, fix.lon) > maxOf(leaveRadiusM, fix.accuracy * 1.5)) {
                moving = true
                anchor = null
                recent.clear()
                recent.addLast(fix)
            }
        }
        return moving
    }
}
