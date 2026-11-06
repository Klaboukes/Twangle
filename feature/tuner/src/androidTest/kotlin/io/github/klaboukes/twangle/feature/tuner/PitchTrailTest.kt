package io.github.klaboukes.twangle.feature.tuner

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.lens.readingToneAmount
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.trail.PitchTrailContent
import io.github.klaboukes.twangle.feature.tuner.trail.PitchTrailTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The pitch trail as a Practice tool (2.0, Phase 78; until then the tuner's meter look, whose checks these were), light
 * and dark, on a phone upright and on its side: the trail fills the height between the ruler and the word, a flat
 * reading's dot is left of the middle and a sharp one's right on the stretched scale, the in-tune band is the tuner's
 * window (Relaxed and Precise), nothing is red, and the trail's motion joins the word's spoken reading.
 */
@RunWith(AndroidJUnit4::class)
class PitchTrailTest {

    val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val shownState = mutableStateOf<TunerUiState>(LensLooksTest.listening)
    private val shownHistory = mutableStateOf(PitchHistory())
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownDark = mutableStateOf(false)
    private val shownPrecision = mutableStateOf(InTunePrecision.RELAXED)
    private var farOff = Color.Unspecified
    private var green = Color.Unspecified

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    farOff = MaterialTheme.styleTokens.farOff
                    green = MaterialTheme.styleTokens.inTune
                    StyleBackdrop(Modifier.fillMaxSize())
                    PitchTrailContent(shownState.value, shownHistory.value, shownPrecision.value)
                }
            }
        }
        compose.waitForIdle()
    }

    private val trail get() = compose.onNodeWithTag(PitchTrailTestTags.TRAIL)

    private fun forEachCase(check: (String) -> Unit) {
        show()
        for (dark in listOf(false, true)) {
            for (size in listOf(PHONE_UPRIGHT, PHONE_ON_ITS_SIDE)) {
                shownDark.value = dark
                shownSize.value = size
                compose.waitForIdle()
                check("${if (dark) "dark" else "light"} ${size.width} x ${size.height}:")
            }
        }
    }

    @Test
    fun theTrailFillsTheHeightBetweenTheRulerAndTheWord() {
        show()
        val box = trail.assertIsDisplayed().getBoundsInRoot()
        val word = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
        assertTrue("taller than wide upright: $box", box.height > box.width)
        assertTrue("the word under it", word.top >= box.bottom)
    }

    @Test
    fun aFlatReadingIsLeftAndASharpOneRight() {
        forEachCase { case ->
            for (cents in listOf(-30f, -8f, 8f, 30f)) {
                shownHistory.value = LensLooksTestHistory.of { cents }
                shownState.value = LensLooksTest.detected(cents)
                compose.waitForIdle()
                val picture = capture()
                val x = dotX(picture, tone(cents))
                val expected = picture.centreX + stretched(cents) / 50f * (picture.width / 2f - SCALE_INSET * picture.pxPerDp)
                assertTrue("$case $cents cents: the dot at $x, expected about $expected", abs(x - expected) < 4f * picture.pxPerDp)
                if (cents < 0f) assertTrue("$case flat is left", x < picture.centreX) else assertTrue("$case sharp is right", x > picture.centreX)
            }
        }
    }

    @Test
    fun theInTuneBandIsTheTunersWindow() {
        shownState.value = LensLooksTest.listening
        shownHistory.value = PitchHistory()
        forEachCase { case ->
            for (precision in InTunePrecision.entries) {
                shownPrecision.value = precision
                compose.waitForIdle()
                val picture = capture()
                val window = if (precision == InTunePrecision.RELAXED) 5f else 2f
                val expected = 2f * stretched(window) / 50f * (picture.width / 2f - SCALE_INSET * picture.pxPerDp)
                assertEquals("$case $precision: the band", expected, bandWidth(picture), 2.5f)
            }
            shownPrecision.value = InTunePrecision.RELAXED
        }
    }

    @Test
    fun nothingIsRed() {
        forEachCase { case ->
            for ((state, history) in listOf(
                LensLooksTest.detected(-45f) to LensLooksTestHistory.of { t -> -45f + 10f * sin(2f * PI.toFloat() * t) },
                LensLooksTest.detected(45f) to LensLooksTestHistory.of { 45f },
                LensLooksTest.detected(0f, inTune = true) to LensLooksTestHistory.of { 0f },
            )) {
                shownState.value = state
                shownHistory.value = history
                compose.waitForIdle()
                assertNull("$case $state: red", capture().firstRed())
            }
        }
    }

    @Test
    fun theTrailsWordsJoinTheReading() {
        shownState.value = LensLooksTest.detected(-12f)
        shownHistory.value = LensLooksTestHistory.of { -12f }
        show()
        // Any note: "E 2, 12 cents flat, Too low".
        val reading = "E 2, ${context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)}, ${context.getString(R.string.tuner_word_too_low)}"
        compose.waitUntil(WORDS_WAIT_MS) { compose.onAllNodes(hasContentDescription(reading)).fetchSemanticsNodes().size == 1 }
        shownHistory.value = LensLooksTestHistory.of { t -> if (t < 3f) -12f else -12f + (t - 3f) * 25f }
        val bending = "$reading, ${context.getString(R.string.tuner_trail_bending_up)}"
        compose.waitUntil(WORDS_WAIT_MS) { compose.onAllNodes(hasContentDescription(bending)).fetchSemanticsNodes().size == 1 }
        shownHistory.value = LensLooksTestHistory.of { t -> -12f + 15f * sin(2f * PI.toFloat() * 5f * t) }
        val wavering = "$reading, ${context.getString(R.string.tuner_spoken_wavering)}"
        compose.waitUntil(WORDS_WAIT_MS) { compose.onAllNodes(hasContentDescription(wavering)).fetchSemanticsNodes().size == 1 }
        compose.onAllNodes(hasContentDescription(wavering)).assertCountEquals(1)
    }

    private inner class Picture(val bitmap: Bitmap, bounds: DpRect) {
        val width = bitmap.width
        val height = bitmap.height
        val pxPerDp = width / bounds.width.value
        val centreX = width / 2f

        fun at(x: Int, y: Int): Color = Color(bitmap.getPixel(x, y))

        fun firstRed(): Pair<Int, Int>? {
            val hsv = FloatArray(3)
            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    android.graphics.Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                    if ((hsv[0] < 20f || hsv[0] > 340f) && hsv[1] > 0.45f && hsv[2] > 0.35f) return x to y
                }
            }
            return null
        }
    }

    private fun capture(): Picture {
        val node = trail.assertIsDisplayed()
        return Picture(node.captureToImage().asAndroidBitmap(), node.getBoundsInRoot())
    }

    /** The newest reading's dot: the middle of the pixels near [colour] on its row ([NOW_FROM_TOP] dp down). */
    private fun dotX(picture: Picture, colour: Color): Float {
        val y = (NOW_FROM_TOP * picture.pxPerDp).toInt()
        var first = -1
        var last = -1
        for (x in 0 until picture.width) {
            if (distance(picture.at(x, y), colour) < DOT_NEAR) {
                if (first < 0) first = x
                last = x
            }
        }
        assertTrue("no dot of $colour on row $y", first >= 0 && last - first >= 4f * picture.pxPerDp)
        return (first + last) / 2f
    }

    /** How wide the in-tune band is half way down: the run of greenish pixels round the middle. */
    private fun bandWidth(picture: Picture): Float {
        val y = picture.height / 2
        val middle = picture.width / 2
        fun greenish(x: Int): Boolean {
            val c = picture.at(x, y)
            return c.green - c.red > 0.025f && c.green >= c.blue - 0.005f
        }
        var left = middle - (2f * picture.pxPerDp).toInt()
        while (left > 0 && greenish(left - 1)) left--
        var right = middle + (2f * picture.pxPerDp).toInt()
        while (right < picture.width - 1 && greenish(right + 1)) right++
        return (right - left + 1).toFloat()
    }

    private fun tone(cents: Float): Color {
        val t = readingToneAmount(cents, 5f)
        return Color(farOff.red + (green.red - farOff.red) * t, farOff.green + (green.green - farOff.green) * t, farOff.blue + (green.blue - farOff.blue) * t)
    }

    private fun distance(a: Color, b: Color): Float = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))

    /** The stretched scale's place of [cents] in plain cents (k = 1.5, as `meterStretch`). */
    private fun stretched(cents: Float): Float {
        val x = min(abs(cents), 50f) / 50f
        return Math.signum(cents) * 50f * 2.5f * x / (1f + 1.5f * x)
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 120L
        const val WORDS_WAIT_MS = 5_000L

        /** The trail's geometry, in dp, as `TrailGeometry` has it. */
        const val SCALE_INSET = 24f
        const val NOW_FROM_TOP = 28f
        const val DOT_NEAR = 0.06f

        val PHONE_UPRIGHT = DpSize(360.dp, 640.dp)
        val PHONE_ON_ITS_SIDE = DpSize(760.dp, 360.dp)
    }
}

/** 5 s of readings of E2, one per hop: [cents] off at each time (NaN for a gap). */
internal object LensLooksTestHistory {
    fun of(cents: (Float) -> Float): PitchHistory {
        val history = PitchHistory()
        var t = 0f
        while (t < 5f) {
            t += 1024f / 48_000f
            val c = cents(t)
            if (c.isNaN()) history.pushGap(t) else history.push(t, 40, c, abs(c) < 5f)
        }
        return history
    }
}
