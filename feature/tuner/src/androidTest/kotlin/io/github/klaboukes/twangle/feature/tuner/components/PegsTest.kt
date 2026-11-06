package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * The pegs (2.0, Phase 71) as drawn, light and dark, on the app's background: the same row pictured before and after
 * a change differs, clearly, round that one peg and nowhere else. The heard peg's ring is in the reading's tone
 * (amber far off, green in tune), the locked peg carries its padlock (in the ink), and a sounding string sends its
 * rings out (Phase 30; until Phase 71 this was SoundingRingsTest).
 */
@RunWith(JUnit4::class)
class PegsTest {

    val compose = createSharedActivityComposeRule()

    /** The test runs the clock by hand, so a mistake could spin: fail in 30 s. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val sounding = mutableStateOf<Int?>(null)
    private val target = mutableStateOf<Int?>(null)
    private val locked = mutableStateOf<Int?>(null)
    private val cents = mutableStateOf(Float.NaN)
    private val inTune = mutableStateOf(false)

    /** Each theme's colours, as the row is drawn in it: light first, then dark. */
    private val farOff = arrayOfNulls<Color>(2)
    private val green = arrayOfNulls<Color>(2)
    private val ink = arrayOfNulls<Color>(2)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column {
                for (dark in listOf(false, true)) {
                    TwangleTheme(darkTheme = dark, animate = false) {
                        val index = if (dark) 1 else 0
                        farOff[index] = MaterialTheme.styleTokens.farOff
                        green[index] = MaterialTheme.styleTokens.inTune
                        ink[index] = MaterialTheme.colorScheme.onSurface
                        val tone = rememberLiveTone(LiveFloat { cents.value }, inTune = inTune.value, animationsOff = true)
                        Box(Modifier.size(360.dp, 120.dp).testTag(tag(dark)), contentAlignment = Alignment.Center) {
                            StyleBackdrop(Modifier.matchParentSize())
                            StringSelector(
                                tuning = Tunings.STANDARD,
                                targetString = target.value,
                                lockedString = locked.value,
                                onStringClick = {},
                                onStringLongClick = {},
                                soundingString = sounding.value,
                                tone = tone,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theRingsShowLightAndDark() {
        show()
        val failures = mutableListOf<String>()
        sounding.value = null
        settle()
        val quiet = listOf(false, true).map { capture(it) }
        sounding.value = SOUNDING
        compose.mainClock.advanceTimeByFrame()
        // A quarter of the way through a wave: one ring just out of the button, one further out.
        compose.mainClock.advanceTimeBy(RING_PHASE_MS)
        for (dark in listOf(false, true)) {
            val (inBand, outside, strongest) = compare(quiet[if (dark) 1 else 0], capture(dark), band(dark))
            val case = case(dark)
            // The ring is 2 dp wide round a ~50 dp button: hundreds of pixels change.
            if (inBand.size < MIN_CHANGED_PIXELS) failures += "$case: only ${inBand.size} pixels changed round the string"
            if (strongest < MIN_CONTRAST) failures += "$case: the rings differ from the background by only $strongest"
            if (outside > 0) failures += "$case: $outside pixels changed away from the sounding string"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The peg heard is ringed in the reading's tone: amber far off, green in tune; nothing else changes. */
    @Test
    fun theHeardPegIsRingedInTheReadingsTone() {
        show()
        settle()
        val plain = listOf(false, true).map { capture(it) }
        val failures = mutableListOf<String>()
        for ((reading, tuned) in listOf(-30f to false, -1f to true)) {
            target.value = SOUNDING
            cents.value = reading
            inTune.value = tuned
            settle()
            for (dark in listOf(false, true)) {
                val index = if (dark) 1 else 0
                val (inBand, outside, _) = compare(plain[index], capture(dark), band(dark))
                val tone = if (tuned) green[index]!! else farOff[index]!!
                val case = "${case(dark)}, ${if (tuned) "in tune" else "far off"}"
                val inTone = inBand.count { it.near(tone) }
                if (inTone < MIN_TONE_PIXELS) failures += "$case: only $inTone of ${inBand.size} changed pixels in the tone $tone"
                if (outside > 0) failures += "$case: $outside pixels changed away from the heard peg"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The locked peg carries a padlock on an ink disc, drawn on it; nothing else changes. */
    @Test
    fun theLockShowsOnThePegLightAndDark() {
        show()
        settle()
        val free = listOf(false, true).map { capture(it) }
        locked.value = SOUNDING
        settle()
        val failures = mutableListOf<String>()
        for (dark in listOf(false, true)) {
            val index = if (dark) 1 else 0
            val (inBand, outside, strongest) = compare(free[index], capture(dark), band(dark))
            val case = case(dark)
            val inInk = inBand.count { it.near(ink[index]!!) }
            if (inInk < MIN_LOCK_PIXELS) failures += "$case: only $inInk of ${inBand.size} changed pixels in the ink"
            if (strongest < MIN_CONTRAST) failures += "$case: the lock differs from the peg by only $strongest"
            if (outside > 0) failures += "$case: $outside pixels changed away from the locked peg"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Lets the background's cross-fade and the pegs' rings and badges finish. */
    private fun settle() = compose.mainClock.advanceTimeBy(SETTLE_MS)

    private fun case(dark: Boolean) = if (dark) "dark" else "light"

    private fun capture(dark: Boolean): PixelMap = compose.onNodeWithTag(tag(dark)).captureToImage().toPixelMap()

    /** Round the A string's peg, in its box's pixels, [BAND] past the peg's bounds. */
    private fun band(dark: Boolean): Rect {
        val box = compose.onNodeWithTag(tag(dark)).fetchSemanticsNode().boundsInRoot
        val button = compose.onNode(hasText("A2") and hasAnyAncestor(hasTestTag(tag(dark)))).fetchSemanticsNode().boundsInRoot
        val reach = with(compose.density) { BAND.toPx() }
        val peg = button.translate(-box.left, -box.top)
        return Rect(peg.left - reach, peg.top - reach, peg.right + reach, peg.bottom + reach)
    }

    /**
     * The colours (after) of the pixels that differ clearly between [before] and [after] inside [band], how many differ
     * outside it, and the largest difference in one colour channel (0–1).
     */
    private fun compare(before: PixelMap, after: PixelMap, band: Rect): Triple<List<Color>, Int, Float> {
        val inBand = mutableListOf<Color>()
        var outside = 0
        var strongest = 0f
        for (x in 0 until before.width) {
            for (y in 0 until before.height) {
                val a = before[x, y]
                val b = after[x, y]
                val difference = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))
                if (difference < CHANGED) continue
                if (band.contains(Offset(x.toFloat(), y.toFloat()))) {
                    inBand += b
                    strongest = max(strongest, difference)
                } else {
                    outside++
                }
            }
        }
        return Triple(inBand, outside, strongest)
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < NEAR && abs(green - other.green) < NEAR && abs(blue - other.blue) < NEAR

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 30L

        /** The A string, second in the row: the peg sounding, heard or locked. */
        const val SOUNDING = 1
        const val SETTLE_MS = 1_000L
        const val RING_PHASE_MS = 300L

        /** Past the button's box: the rings reach 10 dp beyond the button's outline, inside the box. */
        val BAND = 16.dp

        /** A change smaller than this in every channel is rounding, not a ring. */
        const val CHANGED = 0.04f
        const val MIN_CHANGED_PIXELS = 200

        /** The heard ring (2 dp round a 40 dp face) has hundreds of pixels in the tone; the lock's disc dozens in the ink. */
        const val MIN_TONE_PIXELS = 150
        const val MIN_LOCK_PIXELS = 40

        /** A pixel this close in every channel is that colour (antialiasing aside). */
        const val NEAR = 0.08f

        /** The ring's strongest pixel stands this far from the background in at least one channel. */
        const val MIN_CONTRAST = 0.25f

        fun tag(dark: Boolean) = "pegs_$dark"
    }
}
