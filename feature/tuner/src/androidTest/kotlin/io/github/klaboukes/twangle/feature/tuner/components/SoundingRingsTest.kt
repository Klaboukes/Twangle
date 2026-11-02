package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
 * The rings round a sounding string show light and dark, on the app's background: the same row
 * pictured with and without a sounding string differs, clearly, in a band round that string's
 * button, and nowhere else.
 */
@RunWith(JUnit4::class)
class SoundingRingsTest {

    val compose = createSharedActivityComposeRule()

    /** The test runs the clock by hand, so a mistake could spin: fail in 20 s. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val sounding = mutableStateOf<Int?>(null)

    @Test
    fun theRingsShowLightAndDark() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column {
                for (dark in listOf(false, true)) {
                    TwangleTheme(darkTheme = dark, animate = false) {
                        Box(Modifier.size(360.dp, 120.dp).testTag(tag(dark)), contentAlignment = Alignment.Center) {
                            StyleBackdrop(Modifier.matchParentSize())
                            StringSelector(
                                tuning = Tunings.STANDARD,
                                targetString = null,
                                onStringClick = {},
                                onStringLongClick = {},
                                soundingString = sounding.value,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        sounding.value = null
        settle()
        val quiet = listOf(false, true).map { capture(it) }
        sounding.value = SOUNDING
        compose.mainClock.advanceTimeByFrame()
        // A quarter of the way through a wave: one ring just out of the button, one further out.
        compose.mainClock.advanceTimeBy(RING_PHASE_MS)
        for (dark in listOf(false, true)) {
            val case = if (dark) "dark" else "light"
            val ringing = capture(dark)
            val button = buttonBounds(dark)
            val reach = with(compose.density) { BAND.toPx() }
            val band = Rect(button.left - reach, button.top - reach, button.right + reach, button.bottom + reach)
            val (inBand, outside, strongest) = compare(quiet[if (dark) 1 else 0], ringing, band)
            // The ring is 2 dp wide round a ~50 dp button: hundreds of pixels change.
            if (inBand < MIN_CHANGED_PIXELS) failures += "$case: only $inBand pixels changed round the string"
            if (strongest < MIN_CONTRAST) failures += "$case: the rings differ from the background by only $strongest"
            if (outside > 0) failures += "$case: $outside pixels changed away from the sounding string"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Lets the background's cross-fade finish. */
    private fun settle() = compose.mainClock.advanceTimeBy(SETTLE_MS)

    private fun capture(dark: Boolean): PixelMap = compose.onNodeWithTag(tag(dark)).captureToImage().toPixelMap()

    /** The sounding string's button within its box, in the box's pixels. */
    private fun buttonBounds(dark: Boolean): Rect {
        val box = compose.onNodeWithTag(tag(dark)).fetchSemanticsNode().boundsInRoot
        val button = compose.onNode(hasText("A2") and hasAnyAncestor(hasTestTag(tag(dark)))).fetchSemanticsNode().boundsInRoot
        return button.translate(-box.left, -box.top)
    }

    /**
     * Pixels that differ clearly between [before] and [after] inside [band] and outside it, and the
     * largest difference in one colour channel (0–1).
     */
    private fun compare(before: PixelMap, after: PixelMap, band: Rect): Triple<Int, Int, Float> {
        var inBand = 0
        var outside = 0
        var strongest = 0f
        for (x in 0 until before.width) {
            for (y in 0 until before.height) {
                val a = before[x, y]
                val b = after[x, y]
                val difference = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))
                if (difference < CHANGED) continue
                if (band.contains(Offset(x.toFloat(), y.toFloat()))) {
                    inBand++
                    strongest = max(strongest, difference)
                } else {
                    outside++
                }
            }
        }
        return Triple(inBand, outside, strongest)
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 20L
        const val SOUNDING = 1
        const val SETTLE_MS = 1_000L
        const val RING_PHASE_MS = 300L

        /** Past the button's box: the rings reach 10 dp beyond the button's outline, inside the box. */
        val BAND = 16.dp

        /** A change smaller than this in every channel is rounding, not a ring. */
        const val CHANGED = 0.04f
        const val MIN_CHANGED_PIXELS = 200

        /** The ring's strongest pixel stands this far from the background in at least one channel. */
        const val MIN_CONTRAST = 0.25f

        fun tag(dark: Boolean) = "rings_$dark"
    }
}
