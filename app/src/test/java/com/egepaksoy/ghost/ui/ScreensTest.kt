package com.egepaksoy.ghost.ui

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.egepaksoy.ghost.core.Fix
import com.egepaksoy.ghost.data.Db
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * Opens the real app with two made-up days of data, walks through every screen and saves a
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
        val home = 41.0220 to 29.0150
        val work = 41.0480 to 29.0060
        val cafe = 41.0440 to 29.0110
        fun stay(from: Long, to: Long, p: Pair<Double, Double>) {
            var t = from
            while (t <= to) { db.insertPoint(Fix(t, p.first, p.second, 12f)); t += 5 * min }
        }
        // A wiggly path, one fix per minute, like a real road.
        fun move(from: Long, to: Long, a: Pair<Double, Double>, b: Pair<Double, Double>, bend: Double) {
            val n = ((to - from) / min).toInt()
            for (i in 1 until n) {
                val f = i.toDouble() / n
                val wobble = sin(f * PI) * bend + sin(i * 1.3) * 0.00035
                db.insertPoint(Fix(from + i * min, a.first + (b.first - a.first) * f + wobble * 0.4, a.second + (b.second - a.second) * f - wobble, 8f))
            }
        }
        for (dayBack in listOf(1, 0)) {
            val end = now - dayBack * 24 * 60 * min
            val arrive = if (dayBack == 0) 392 else 385
            stay(end - 600 * min, end - 420 * min, home)
            move(end - 420 * min, end - arrive * min, home, work, 0.006)
            stay(end - arrive * min, end - 150 * min, work)
            move(end - 150 * min, end - 135 * min, work, cafe, 0.0008)
            stay(end - 135 * min, end - 70 * min, cafe)
            move(end - 70 * min, end - 45 * min, cafe, home, -0.004)
            stay(end - 45 * min, end, home)
        }
        db.addPlace("Ev", home.first, home.second, 100.0)
        db.addPlace("İş", work.first, work.second, 100.0)
    }

    /** Gives the map a few real seconds to download tiles, if the network allows it. */
    private fun settleMap(seconds: Int = 8) {
        repeat(seconds * 2) {
            Thread.sleep(500)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        captureScreenRoboImage("build/outputs/screens/$name.png")
    }

    @Test
    fun everyScreenOpens() {
        seed(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Yolda").fetchSemanticsNodes().isNotEmpty() }
            settleMap()
            shot("1-gunluk")
            rule.onNode(hasScrollAction()).performScrollToIndex(5)
            shot("1a-gunluk-zaman-cizelgesi")

            rule.onAllNodesWithText("haritada gör", substring = true).onFirst().performClick()
            settleMap()
            shot("2a-harita-yolculuk")
            rule.onNodeWithContentDescription("Tüm günü göster").performClick()
            settleMap(15)
            shot("2b-harita-gun")

            rule.onNodeWithText("İstatistik").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Yolculuk süreleri").fetchSemanticsNodes().isNotEmpty() }
            shot("3-istatistik")
            rule.onNodeWithText("Yerler").performClick()
            rule.waitForIdle()
            shot("4-yerler")

            rule.onNodeWithText("Günlük").performClick()
            rule.onNodeWithContentDescription("Ayarlar").performClick()
            shot("5-ayarlar")
            // Dark theme from the settings: check the main screens in it too.
            rule.onNodeWithText("Koyu").performScrollTo().performClick()
            shot("5b-ayarlar-koyu")
            rule.onNodeWithContentDescription("Geri").performClick()
            rule.waitForIdle()
            shot("1-gunluk-koyu")
            rule.onNodeWithText("İstatistik").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Yolculuk süreleri").fetchSemanticsNodes().isNotEmpty() }
            shot("3-istatistik-koyu")
            rule.onNodeWithText("Günlük").performClick()
            rule.onNodeWithContentDescription("Ayarlar").performClick()
            rule.onNodeWithText("Açık").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Geri").performClick()

            rule.onNodeWithText("Bugün").performClick()
            shot("6-tarih")
            rule.onNodeWithText("İptal").performClick()

            // Last step: once the text field has focus this environment never reports "idle"
            // (blinking cursor), so take the picture without waiting.
            rule.onNode(hasScrollAction()).performScrollToIndex(6)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.onAllNodesWithText("İsim vermek için dokun").onFirst().performClick()
            rule.mainClock.advanceTimeBy(600)
            captureScreenRoboImage("build/outputs/screens/7-isim-ver.png")
        }
    }
}
