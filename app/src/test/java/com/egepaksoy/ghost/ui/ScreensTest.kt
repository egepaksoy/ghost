package com.egepaksoy.ghost.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.egepaksoy.ghost.core.Fix
import com.egepaksoy.ghost.data.Db
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opens the real app with a made-up day of data, walks through every screen and saves a
 * screenshot of each to app/build/outputs/screens. Fails if any screen crashes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val min = 60_000L

    private fun seed(ctx: Context) {
        val db = Db.get(ctx)
        val now = System.currentTimeMillis()
        val home = 41.0200 to 29.0100
        val work = 41.0650 to 28.9900
        val cafe = 41.0500 to 29.0300
        fun stay(from: Long, to: Long, p: Pair<Double, Double>) {
            var t = from
            while (t <= to) { db.insertPoint(Fix(t, p.first, p.second, 12f)); t += 5 * min }
        }
        fun drive(from: Long, to: Long, a: Pair<Double, Double>, b: Pair<Double, Double>) {
            val n = ((to - from) / min).toInt()
            for (i in 1 until n) db.insertPoint(
                Fix(from + i * min, a.first + (b.first - a.first) * i / n, a.second + (b.second - a.second) * i / n, 8f)
            )
        }
        // Yesterday and today share the same commute so the statistics have something to average.
        for (dayBack in listOf(1, 0)) {
            val end = now - dayBack * 24 * 60 * min
            stay(end - 600 * min, end - 420 * min, home)
            drive(end - 420 * min, end - (if (dayBack == 0) 392 else 385) * min, home, work)
            stay(end - (if (dayBack == 0) 392 else 385) * min, end - 150 * min, work)
            drive(end - 150 * min, end - 135 * min, work, cafe)
            stay(end - 135 * min, end - 70 * min, cafe)
            drive(end - 70 * min, end - 45 * min, cafe, home)
            stay(end - 45 * min, end, home)
        }
        db.addPlace("Ev", home.first, home.second, 100.0)
        db.addPlace("İş", work.first, work.second, 100.0)
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        captureScreenRoboImage("build/outputs/screens/$name.png")
    }

    @Test
    fun everyScreenOpens() {
        seed(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Ev").fetchSemanticsNodes().isNotEmpty() }
            shot("1-gunluk")
            rule.onNodeWithText("Harita").performClick()
            shot("2-harita")
            rule.onNodeWithText("Yerler").performClick()
            shot("3-yerler")
            rule.onNodeWithText("İstatistik").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Yolculuk süreleri").fetchSemanticsNodes().isNotEmpty() }
            shot("4-istatistik")
            rule.onNodeWithText("Ayarlar").performClick()
            shot("5-ayarlar")
            rule.onNodeWithText("Günlük").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Bugün").performClick()
            shot("1c-tarih")
            rule.onNodeWithText("İptal").performClick()
            // Last step: once the text field has focus this environment never reports "idle"
            // (blinking cursor), so take the picture without waiting.
            rule.mainClock.autoAdvance = false
            rule.onAllNodesWithText("Adsız yer").onFirst().performClick()
            rule.mainClock.advanceTimeBy(600)
            captureScreenRoboImage("build/outputs/screens/1b-isim-ver.png")
        }
    }
}
