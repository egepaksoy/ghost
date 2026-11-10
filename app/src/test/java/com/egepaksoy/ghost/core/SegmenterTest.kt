package com.egepaksoy.ghost.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmenterTest {
    private val min = 60_000L
    private val homeLat = 41.0000
    private val homeLon = 29.0000
    private val workLat = 41.0450 // ~5 km north
    private val workLon = 29.0000

    private fun stay(from: Long, to: Long, lat: Double, lon: Double, step: Long = 5 * min): List<Fix> =
        (from..to step step).mapIndexed { i, t -> Fix(t, lat + (i % 3 - 1) * 0.00005, lon, 15f) }

    private fun drive(from: Long, to: Long): List<Fix> {
        val steps = ((to - from) / min).toInt()
        return (1 until steps).map { i ->
            Fix(from + i * min, homeLat + (workLat - homeLat) * i / steps, homeLon, 10f)
        }
    }

    /** 60 min home, 20 min drive, 60 min work. */
    private fun commute(): List<Fix> =
        stay(0, 60 * min, homeLat, homeLon) + drive(60 * min, 80 * min) + stay(80 * min, 140 * min, workLat, workLon)

    @Test
    fun stayTripStay() {
        val segs = Segmenter.segment(commute())
        assertEquals(3, segs.size)
        val (a, t, b) = segs
        assertTrue(a is Stay && t is Trip && b is Stay)
        assertEquals(0L, a.start)
        assertEquals(60 * min, a.end)
        assertEquals(20 * min, t.end - t.start)
        assertEquals(5000.0, (t as Trip).distanceM, 300.0)
        assertFalse(t.uncertain)
        assertEquals(140 * min, b.end)
    }

    @Test
    fun singleGpsJumpDoesNotSplitStay() {
        val pts = stay(0, 60 * min, homeLat, homeLon).toMutableList()
        pts[5] = pts[5].copy(lat = homeLat + 0.01) // 1.1 km jump
        val segs = Segmenter.segment(pts)
        assertEquals(1, segs.size)
        assertEquals(60 * min, segs[0].end - segs[0].start)
    }

    @Test
    fun inaccurateFixesAreIgnored() {
        val pts = stay(0, 60 * min, homeLat, homeLon) + Fix(62 * min, homeLat + 0.05, homeLon, 900f)
        val segs = Segmenter.segment(pts)
        assertEquals(1, segs.size)
        assertTrue(segs[0] is Stay)
    }

    @Test
    fun holeInDataAtSamePlaceIsOneStay() {
        val pts = stay(0, 30 * min, homeLat, homeLon) + stay(8 * 60 * min, 9 * 60 * min, homeLat, homeLon)
        val segs = Segmenter.segment(pts)
        assertEquals(1, segs.size)
        assertEquals(9 * 60 * min, segs[0].end)
    }

    @Test
    fun holeBetweenPlacesIsUncertainTrip() {
        val pts = stay(0, 30 * min, homeLat, homeLon) + stay(120 * min, 180 * min, workLat, workLon)
        val segs = Segmenter.segment(pts)
        assertEquals(3, segs.size)
        assertTrue((segs[1] as Trip).uncertain)
    }

    @Test
    fun lastStayIsExtendedToNow() {
        val segs = Segmenter.segment(commute(), now = 143 * min)
        val last = segs.last() as Stay
        assertTrue(last.ongoing)
        assertEquals(143 * min, last.end)
        // Old data must not be stretched.
        assertFalse(Segmenter.segment(commute(), now = 500 * min).last().ongoing)
    }

    @Test
    fun drivingRightNowIsAnOngoingTrip() {
        val pts = stay(0, 60 * min, homeLat, homeLon) + drive(60 * min, 80 * min).take(10)
        val segs = Segmenter.segment(pts, now = 71 * min)
        assertEquals(2, segs.size)
        assertTrue(segs[1] is Trip && segs[1].ongoing)
    }

    @Test
    fun timelineCutsStaysToTheDayAndNamesPlaces() {
        val segs = Segmenter.segment(commute())
        val resolver = SpotResolver(listOf(PlaceDef(1, "Ev", homeLat, homeLon, 100.0)))
        val items = Analyzer.timeline(segs, resolver, 30 * min, 100 * min)
        assertEquals(3, items.size)
        val home = items[0] as StayItem
        assertEquals("Ev", home.spot.name)
        assertEquals(30 * min, home.durationMs)
        val trip = items[1] as TripItem
        assertEquals("Ev", trip.from?.name)
        assertEquals(null, trip.to?.name)
        assertEquals(20 * min, (items[2] as StayItem).durationMs)
    }

    @Test
    fun routesAverageRepeatedTrips() {
        val day = 24 * 60 * min
        // Day 1: 20 min drive, day 2: 30 min drive (same places).
        val d2 = stay(day, day + 60 * min, homeLat, homeLon) +
            (1 until 30).map { Fix(day + 60 * min + it * min, homeLat + (workLat - homeLat) * it / 30, homeLon, 10f) } +
            stay(day + 90 * min, day + 150 * min, workLat, workLon)
        val segs = Segmenter.segment(commute() + d2)
        val items = Analyzer.timeline(segs, SpotResolver(emptyList()), 0, 3 * day)
        val out = Analyzer.routes(items).first { it.from.lat < 41.02 && it.to.lat > 41.02 }
        assertEquals(2, out.count)
        assertEquals(25 * min, out.avgMs)
        assertEquals(20 * min, out.minMs)
        assertEquals(30 * min, out.maxMs)
        val totals = Analyzer.placeTotals(items)
        assertEquals(2, totals.size)
    }

    @Test
    fun adaptivePolicySwitchesModes() {
        val p = AdaptivePolicy()
        assertTrue(p.moving)
        // Standing still for 5 minutes -> stationary.
        for (i in 0..5) p.onFix(Fix(i * min, homeLat, homeLon, 10f))
        assertFalse(p.moving)
        // Small jitter keeps it stationary.
        assertFalse(p.onFix(Fix(10 * min, homeLat + 0.0003, homeLon, 20f)))
        // A far but very inaccurate fix is not trusted.
        assertFalse(p.onFix(Fix(11 * min, homeLat + 0.003, homeLon, 500f)))
        // Really leaving -> moving.
        assertTrue(p.onFix(Fix(15 * min, homeLat + 0.003, homeLon, 10f)))
        // Still driving -> stays in moving mode.
        for (i in 1..6) assertTrue(p.onFix(Fix((15 + i) * min, homeLat + 0.003 + i * 0.005, homeLon, 10f)))
    }
}
