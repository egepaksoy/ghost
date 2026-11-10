package com.egepaksoy.ghost.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.egepaksoy.ghost.R
import com.egepaksoy.ghost.core.Analyzer
import com.egepaksoy.ghost.core.Segmenter
import com.egepaksoy.ghost.core.SpotResolver
import com.egepaksoy.ghost.core.StayItem
import com.egepaksoy.ghost.core.TimelineItem
import com.egepaksoy.ghost.core.TripItem
import com.egepaksoy.ghost.data.Db
import com.egepaksoy.ghost.data.Prefs
import com.egepaksoy.ghost.ui.Fmt
import com.egepaksoy.ghost.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Large widget: today's route on the map, the day's totals and what is happening right now. */
class DetailWidget : GhostWidget()

/** Small widget: how long you have been waiting at the current place, or how long you have been on the road. */
class StatusWidget : GhostWidget()

/** Slim widget: today's totals and a 24-hour strip. */
class SummaryWidget : GhostWidget()

open class GhostWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.requestUpdate(context, 0)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        Widgets.requestUpdate(context, 0)
}

object Widgets {
    sealed interface Now
    /** Staying at a place since [since]. */
    data class Staying(val label: String, val since: Long) : Now
    /** On the road since [since]. */
    data class Moving(val since: Long, val distanceM: Double) : Now
    /** Nothing recent; the last known thing happened at [time]. */
    data class Idle(val label: String?, val time: Long) : Now

    class State(val tracking: Boolean, val dayStart: Long, val items: List<TimelineItem>, val now: Now?)

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pending: ScheduledFuture<*>? = null

    /** Refreshes every widget soon. Many calls in a short time are merged into one refresh. */
    fun requestUpdate(ctx: Context, delayMs: Long = 3_000) {
        val app = ctx.applicationContext
        synchronized(this) {
            pending?.cancel(false)
            pending = executor.schedule({
                try { updateNow(app) } catch (e: Exception) { }
            }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun ids(ctx: Context, m: AppWidgetManager, c: Class<*>) = m.getAppWidgetIds(ComponentName(ctx, c))

    fun updateNow(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx) ?: return
        val detail = ids(ctx, m, DetailWidget::class.java)
        val status = ids(ctx, m, StatusWidget::class.java)
        val summary = ids(ctx, m, SummaryWidget::class.java)
        if (detail.isEmpty() && status.isEmpty() && summary.isEmpty()) return
        val s = load(ctx)
        for (id in status) m.updateAppWidget(id, statusViews(ctx, s))
        for (id in summary) {
            val o = m.getAppWidgetOptions(id)
            m.updateAppWidget(id, summaryViews(ctx, s, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300)))
        }
        for (id in detail) {
            val o = m.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 300
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 260
            m.updateAppWidget(id, detailViews(ctx, s, w, h))
        }
    }

    fun load(ctx: Context, nowMs: Long = System.currentTimeMillis()): State {
        val db = Db.get(ctx)
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val day = 24 * 3_600_000L
        val segs = Segmenter.segment(db.pointsBetween(start - day, nowMs + 60_000), now = nowMs)
        val resolver = SpotResolver(db.places())
        val all = Analyzer.timeline(segs, resolver, 0, Long.MAX_VALUE)
        val today = Analyzer.timeline(segs, resolver, start, start + day)
        val addresses = all.filterIsInstance<StayItem>().map { it.spot }.filter { it.name == null }.distinctBy { it.key }
            .mapNotNull { sp -> db.cachedAddress(sp.key)?.let { sp.key to it } }.toMap()
        val now = when (val last = all.lastOrNull()) {
            is StayItem -> if (last.ongoing) Staying(Fmt.spot(last.spot, addresses), last.start) else Idle(Fmt.spot(last.spot, addresses), last.end)
            is TripItem -> if (last.trip.ongoing) Moving(last.trip.start, last.trip.distanceM) else Idle(null, last.trip.end)
            null -> null
        }
        return State(Prefs(ctx).tracking, start, today, now)
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A Chronometer counts up on its own, so the widget stays live without being redrawn every second. */
    private fun RemoteViews.counter(chrono: Int, text: Int, since: Long?) {
        if (since != null) {
            setChronometer(chrono, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - since), null, true)
            setViewVisibility(chrono, View.VISIBLE)
            setViewVisibility(text, View.GONE)
        } else {
            setChronometer(chrono, SystemClock.elapsedRealtime(), null, false)
            setViewVisibility(chrono, View.GONE)
            setViewVisibility(text, View.VISIBLE)
        }
    }

    fun statusViews(ctx: Context, s: State): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_status)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
        val n = s.now
        when {
            s.tracking && n is Staying -> {
                v.setImageViewResource(R.id.icon, R.drawable.widget_icon_place)
                v.setTextViewText(R.id.title, n.label)
                v.counter(R.id.chrono, R.id.big, n.since)
                v.setTextViewText(R.id.sub, "Bekleme süresi · geliş ${Fmt.time(n.since)}")
            }
            s.tracking && n is Moving -> {
                v.setImageViewResource(R.id.icon, R.drawable.widget_icon_route)
                v.setTextViewText(R.id.title, "Yolda")
                v.counter(R.id.chrono, R.id.big, n.since)
                v.setTextViewText(R.id.sub, "Yolculuk süresi · ${Fmt.distance(n.distanceM)}")
            }
            else -> {
                v.setImageViewResource(R.id.icon, R.drawable.widget_icon_idle)
                v.counter(R.id.chrono, R.id.big, null)
                if (!s.tracking) {
                    v.setTextViewText(R.id.title, "Kayıt kapalı")
                    v.setTextViewText(R.id.big, "—")
                    v.setTextViewText(R.id.sub, "Başlatmak için dokun")
                } else {
                    v.setTextViewText(R.id.title, (n as? Idle)?.label ?: "Konum bekleniyor")
                    v.setTextViewText(R.id.big, (n as? Idle)?.let { Fmt.time(it.time) } ?: "—")
                    v.setTextViewText(R.id.sub, if (n != null) "Son kayıt" else "İlk konum birkaç dakika içinde gelir")
                }
            }
        }
        return v
    }

    private fun RemoteViews.totals(s: State) {
        val trips = s.items.filterIsInstance<TripItem>()
        setTextViewText(R.id.stat_places, "${s.items.filterIsInstance<StayItem>().map { it.spot.key }.distinct().size}")
        setTextViewText(R.id.stat_travel, Fmt.duration(trips.sumOf { it.durationMs }).takeIf { trips.isNotEmpty() } ?: "0 dk")
        setTextViewText(R.id.stat_dist, Fmt.distance(trips.sumOf { it.trip.distanceM }))
        setTextViewText(R.id.status, if (s.tracking) "● Kaydediliyor" else "Kayıt kapalı")
    }

    fun summaryViews(ctx: Context, s: State, widthDp: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_summary)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
        v.totals(s)
        val d = ctx.resources.displayMetrics.density
        val w = (widthDp.coerceIn(120, 600) * d).toInt().coerceAtMost(900)
        v.setImageViewBitmap(R.id.strip, TileMap.strip(ctx, s.items, s.dayStart, w, (8 * d).toInt()))
        return v
    }

    fun detailViews(ctx: Context, s: State, widthDp: Int, heightDp: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_detail)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
        v.totals(s)
        // Size of the map area: the widget minus padding, header, totals and the "now" line.
        val mapW = (widthDp - 24).coerceAtLeast(120)
        val mapH = (heightDp - 132).coerceAtLeast(60)
        val pxPerDp = minOf(ctx.resources.displayMetrics.density, 640f / mapW)
        val bmp = TileMap.render(ctx, s.items, (mapW * pxPerDp).toInt(), (mapH * pxPerDp).toInt(), pxPerDp)
        if (bmp != null) {
            v.setImageViewBitmap(R.id.map, bmp)
            v.setViewVisibility(R.id.map_empty, View.GONE)
        } else {
            v.setImageViewResource(R.id.map, android.R.color.transparent)
            v.setViewVisibility(R.id.map_empty, View.VISIBLE)
        }
        val n = s.now
        when {
            s.tracking && n is Staying -> {
                v.setImageViewResource(R.id.now_icon, R.drawable.widget_icon_place)
                v.setTextViewText(R.id.now_label, "${n.label} · bekleme")
                v.counter(R.id.now_chrono, R.id.now_text, n.since)
            }
            s.tracking && n is Moving -> {
                v.setImageViewResource(R.id.now_icon, R.drawable.widget_icon_route)
                v.setTextViewText(R.id.now_label, "Yolda · ${Fmt.distance(n.distanceM)}")
                v.counter(R.id.now_chrono, R.id.now_text, n.since)
            }
            else -> {
                v.setImageViewResource(R.id.now_icon, R.drawable.widget_icon_idle)
                v.counter(R.id.now_chrono, R.id.now_text, null)
                v.setTextViewText(R.id.now_label, if (!s.tracking) "Kayıt kapalı" else (n as? Idle)?.label ?: "Konum bekleniyor")
                v.setTextViewText(R.id.now_text, (n as? Idle)?.let { Fmt.time(it.time) } ?: "")
            }
        }
        return v
    }
}
