package com.egepaksoy.ghost.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.egepaksoy.ghost.core.Fix
import com.egepaksoy.ghost.data.Db
import com.egepaksoy.ghost.data.Prefs
import com.egepaksoy.ghost.widget.Widgets
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.sin

/** Builds every widget exactly as the home screen would and saves a picture of each. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class WidgetsTest {
    private val min = 60_000L
    private val home = 41.0220 to 29.0150
    private val work = 41.0480 to 29.0060

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    private fun seed(endWithTrip: Boolean) {
        val db = Db.get(ctx())
        // The database outlives a single test; start from empty.
        db.deleteAllPoints()
        db.writableDatabase.delete("places", null, null)
        val now = System.currentTimeMillis()
        fun stay(from: Long, to: Long, p: Pair<Double, Double>) {
            var t = from
            while (t <= to) { db.insertPoint(Fix(t, p.first, p.second, 12f)); t += 5 * min }
        }
        fun move(from: Long, to: Long, a: Pair<Double, Double>, b: Pair<Double, Double>, bend: Double) {
            val n = ((to - from) / min).toInt()
            for (i in 1 until n) {
                val f = i.toDouble() / n
                val w = sin(f * PI) * bend + sin(i * 1.3) * 0.00035
                db.insertPoint(Fix(from + i * min, a.first + (b.first - a.first) * f + w * 0.4, a.second + (b.second - a.second) * f - w, 8f))
            }
        }
        stay(now - 300 * min, now - 200 * min, home)
        move(now - 200 * min, now - 172 * min, home, work, 0.006)
        if (endWithTrip) {
            stay(now - 172 * min, now - 20 * min, work)
            move(now - 20 * min, now, work, home, -0.004)
        } else {
            stay(now - 172 * min, now, work)
        }
        db.addPlace("Ev", home.first, home.second, 100.0)
        db.addPlace("İş", work.first, work.second, 100.0)
        Prefs(ctx()).tracking = true
    }

    private fun shoot(views: RemoteViews, wDp: Int, hDp: Int, name: String) {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { a ->
                val d = a.resources.displayMetrics.density
                val v = views.apply(a, FrameLayout(a))
                val frame = FrameLayout(a).apply {
                    setBackgroundColor(Color.parseColor("#FF6F8F86"))
                    setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
                    addView(v, FrameLayout.LayoutParams((wDp * d).toInt(), (hDp * d).toInt()))
                }
                a.setContentView(LinearLayout(a).apply { addView(frame, LinearLayout.LayoutParams(-2, -2)) })
            }
            sc.onActivity { a ->
                val root = (a.findViewById<View>(android.R.id.content) as FrameLayout).getChildAt(0)
                (root as LinearLayout).getChildAt(0).captureRoboImage("build/outputs/screens/widget-$name.png")
            }
        }
    }

    @Test
    fun widgetsWhileStaying() {
        seed(endWithTrip = false)
        val s = Widgets.load(ctx())
        assertTrue(s.now is Widgets.Staying)
        shoot(Widgets.detailViews(ctx(), s, 340, 290), 340, 290, "1-detayli")
        shoot(Widgets.statusViews(ctx(), s), 160, 150, "2-su-an-bekleme")
        shoot(Widgets.summaryViews(ctx(), s, 340), 340, 100, "4-ozet")
    }

    @Test
    fun widgetWhileOnTheRoad() {
        seed(endWithTrip = true)
        val s = Widgets.load(ctx())
        assertTrue(s.now is Widgets.Moving)
        shoot(Widgets.statusViews(ctx(), s), 160, 150, "3-su-an-yolda")
    }
}
