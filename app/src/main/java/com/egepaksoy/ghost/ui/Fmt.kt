package com.egepaksoy.ghost.ui

import com.egepaksoy.ghost.core.Spot
import com.egepaksoy.ghost.core.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    val tr: Locale = Locale.forLanguageTag("tr")
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMMM EEEE", tr)

    fun time(ms: Long): String = hm.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun duration(ms: Long): String {
        val m = (ms + 30_000) / 60_000
        return when {
            m < 1 -> "1 dk'dan az"
            m < 60 -> "$m dk"
            m % 60 == 0L -> "${m / 60} sa"
            else -> "${m / 60} sa ${m % 60} dk"
        }
    }

    fun distance(m: Double): String =
        if (m < 1000) "${(m / 10).toInt() * 10} m" else String.format(tr, "%.1f km", m / 1000)

    fun day(d: LocalDate): String = when (d) {
        LocalDate.now() -> "Bugün"
        LocalDate.now().minusDays(1) -> "Dün"
        else -> dayFmt.format(d)
    }

    fun dayTitle(d: LocalDate): String = when (d) {
        LocalDate.now() -> "Bugün"
        LocalDate.now().minusDays(1) -> "Dün"
        else -> DateTimeFormatter.ofPattern("d MMMM", tr).format(d)
    }

    fun dayFull(d: LocalDate): String = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", tr).format(d)

    fun speedKmh(t: Trip): Double {
        val h = (t.end - t.start) / 3_600_000.0
        return if (h > 0) t.distanceM / 1000 / h else 0.0
    }

    fun isWalk(t: Trip) = speedKmh(t) < 6.5
    fun tripMode(t: Trip) = if (isWalk(t)) "Yürüyüş" else "Araçla"
    fun waypoints(t: Trip) = (t.path.size - 2).coerceAtLeast(0)

    fun spot(s: Spot?, addresses: Map<String, String>): String =
        if (s == null) "?" else s.name ?: addresses[s.key] ?: "Adsız yer"
}
