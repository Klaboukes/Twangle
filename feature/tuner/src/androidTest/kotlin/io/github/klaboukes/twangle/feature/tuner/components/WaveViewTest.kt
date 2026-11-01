package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WaveViewTest {

    val compose = createSharedActivityComposeRule()

    /** One test runs the wave's frame loop by hand, so a mistake could spin: fail in 10 s. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    @Test
    fun everyStyleDrawsItsRibbon() {
        compose.setContent {
            // Light and dark side by side, small enough that all fit on the screen at once.
            Column {
                for (style in VisualStyle.entries) {
                    Row {
                        for (dark in listOf(false, true)) {
                            TwangleTheme(style = style, darkTheme = dark, animate = false) {
                                WaveView(cents = -12f, inTune = false, modifier = Modifier.size(170.dp, 40.dp).testTag(tag(style, dark)))
                            }
                        }
                    }
                }
            }
        }
        for (style in VisualStyle.entries) {
            for (dark in listOf(false, true)) {
                val pixels = compose.onNodeWithTag(tag(style, dark)).captureToImage().toPixelMap()
                val empty = pixels[0, 0]
                var marked = 0
                for (x in 0 until pixels.width step 2) for (y in 0 until pixels.height step 2) if (pixels[x, y] != empty) marked++
                // The ribbon covers a good part of the box; an empty box has nothing on it.
                assertTrue("$style dark=$dark marked $marked pixels", marked > 60)
            }
        }
    }

    @Test
    fun everyStyleDrawsItsStrobe() {
        compose.setContent {
            // Light and dark side by side, small enough that all fit on the screen at once.
            Column {
                for (style in VisualStyle.entries) {
                    Row {
                        for (dark in listOf(false, true)) {
                            TwangleTheme(style = style, darkTheme = dark, animate = false) {
                                WaveView(
                                    cents = -3f,
                                    inTune = false,
                                    look = WaveLook.STROBE,
                                    modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(tag(style, dark)),
                                )
                            }
                        }
                    }
                }
            }
        }
        for (style in VisualStyle.entries) {
            for (dark in listOf(false, true)) {
                val marked = markedShare(compose.onNodeWithTag(tag(style, dark)).captureToImage().toPixelMap())
                // Clearly drawn, and clearly not a solid block.
                assertTrue("$style dark=$dark marked ${(marked * 100).toInt()} %", marked > 0.15f && marked < 0.85f)
            }
        }
    }

    /**
     * The wave moves on every frame without recomposing anything: the frame clock and the motion
     * are read only while drawing. The test drives the frames itself (with the clock on manual, the
     * wave's "infinite" animation runs) and counts how often the Recomposer applied changes.
     */
    @Test
    fun theStrobeMovesWithoutRecomposing() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false, animate = false) {
                WaveView(
                    cents = -3f,
                    inTune = false,
                    look = WaveLook.STROBE,
                    modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(STROBE_TAG),
                )
            }
        }
        // Let the first frames through: the frame loop starts and the stripes grow to full height.
        compose.mainClock.advanceTimeBy(STROBE_SETTLE_MS)
        val before = compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()
        val changes = changeCount()
        // A quarter of a second at 3 cents flat moves the stripes about a quarter of a period.
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val after = compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()
        assertEquals("recompositions while the stripes moved", changes, changeCount())
        assertTrue("the stripes moved", differentShare(before, after) > 0.05f)
    }

    /**
     * The wave reads the exact cents while drawing (Phase 65; before, the whole cents the text
     * shows): on the note it holds still, and a reading moving off it sets the stripes drifting
     * without recomposing anything.
     */
    @Test
    fun theStrobeFollowsTheLiveCentsWithoutRecomposing() {
        compose.mainClock.autoAdvance = false
        val cents = mutableFloatStateOf(0f)
        compose.setContent {
            TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false, animate = false) {
                WaveView(
                    cents = { cents.floatValue },
                    inTune = false,
                    look = WaveLook.STROBE,
                    modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(STROBE_TAG),
                )
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val still = compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()
        val changes = changeCount()
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("on the note it held still", differentShare(still, compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()) < 0.01f)
        cents.floatValue = -3f
        compose.mainClock.advanceTimeBy(STROBE_SETTLE_MS)
        val before = compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val after = compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()
        assertEquals("recompositions while the reading changed", changes, changeCount())
        assertTrue("the stripes moved", differentShare(before, after) > 0.05f)
    }

    /** How often the Recomposers applied changes to a composition, all together. */
    private fun changeCount(): Long = compose.runOnIdle {
        Recomposer.runningRecomposers.value.sumOf { it.changeCount }
    }

    /** Share of [pixels] (every other one) that isn't [BACKGROUND]. */
    private fun markedShare(pixels: PixelMap): Float {
        var marked = 0
        var total = 0
        for (x in 0 until pixels.width step 2) {
            for (y in 0 until pixels.height step 2) {
                total++
                if (!pixels[x, y].near(BACKGROUND)) marked++
            }
        }
        return marked / total.toFloat()
    }

    /**
     * Every look gives the strobe's cue: a few cents off it visibly moves, and on the note not a
     * pixel changes. The test drives the frames itself (with the clock on manual,
     * the wave's "infinite" animation runs).
     */
    @Test
    fun everyLookMovesOffPitchAndHoldsStillOnTheNote() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false, animate = false) {
                Column {
                    for (look in WaveLook.entries) {
                        Row {
                            for (onNote in listOf(false, true)) {
                                WaveView(
                                    cents = if (onNote) 0.3f else -3f,
                                    inTune = onNote,
                                    look = look,
                                    modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(lookTag(look, onNote)),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Long enough for the breathing to fade out on the note and the colours to settle.
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val before = WaveLook.entries.associateWith { look -> listOf(false, true).map { capture(look, it) } }
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        for (look in WaveLook.entries) {
            val moved = differentShare(before.getValue(look)[0], capture(look, onNote = false))
            assertTrue("$look moved off pitch: $moved", moved > 0.01f)
            assertEquals("$look pixels that changed on the note", 0f, differentShare(before.getValue(look)[1], capture(look, onNote = true)))
        }
    }

    /**
     * Every look, in every style, light and dark, is clearly drawn and stays inside its own box (a
     * little glow aside), so none of them spills over the note above it or the hint and strings
     * below, far off pitch (the ripples swell, the fireflies scatter) or in tune (with the halo).
     * Only the default style unless the run is thorough.
     */
    @Test
    fun everyLookStaysInItsBoxInEveryStyle() {
        compose.mainClock.autoAdvance = false
        val shownStyle = mutableStateOf(VisualStyle.entries.first())
        compose.setContent {
            TwangleTheme(style = shownStyle.value, darkTheme = false, animate = false) {
                Column {
                    for (look in WaveLook.entries) {
                        Row {
                            for (onNote in listOf(false, true)) {
                                TwangleTheme(style = shownStyle.value, darkTheme = onNote, animate = false) {
                                    Box(Modifier.testTag(lookTag(look, onNote)).background(BACKGROUND).padding(MARGIN)) {
                                        WaveView(
                                            cents = if (onNote) 0.2f else -30f,
                                            inTune = onNote,
                                            look = look,
                                            modifier = Modifier.size(150.dp, 40.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (style in allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)) {
            shownStyle.value = style
            compose.mainClock.advanceTimeBy(STYLE_SETTLE_MS)
            for (look in WaveLook.entries) {
                for (onNote in listOf(false, true)) {
                    val pixels = capture(look, onNote)
                    // The margin around the wave's box, less the little a glow may reach into it.
                    val edge = with(compose.density) { (MARGIN - GLOW_ROOM).toPx() }.toInt()
                    var outside = 0
                    var firstOut = ""
                    var inside = 0
                    var total = 0
                    for (x in 0 until pixels.width step 3) {
                        for (y in 0 until pixels.height step 3) {
                            val inBox = x >= edge && y >= edge && x < pixels.width - edge && y < pixels.height - edge
                            // A little looser than near(): the screen's colours can be off by a step or two.
                            val marked = !pixels[x, y].near(BACKGROUND, BOX_TOLERANCE)
                            if (inBox) {
                                total++
                                if (marked) inside++
                            } else if (marked) {
                                if (outside == 0) firstOut = "(first at $x, $y px)"
                                outside++
                            }
                        }
                    }
                    val what = "$style $look ${if (onNote) "in tune, dark" else "off pitch, light"}"
                    assertEquals("$what: pixels drawn outside its box $firstOut", 0, outside)
                    assertTrue("$what: drawn on ${inside * 100 / total} % of its box", inside > total / 50)
                }
            }
        }
    }

    /**
     * Poor reception draws every look fainter and softer, in every style, light and dark, on the
     * style's own background, yet still plainly visible: at the worst reception it keeps at least
     * 40 % of its ink (colour difference from the background, summed) and still marks its box.
     * Only the default style unless the run is thorough.
     */
    @Test
    fun everyLookIsFaintButClearWithPoorReception() {
        compose.mainClock.autoAdvance = false
        val shownStyle = mutableStateOf(VisualStyle.entries.first())
        val shownDark = mutableStateOf(false)
        compose.setContent {
            TwangleTheme(style = shownStyle.value, darkTheme = shownDark.value, animate = false) {
                Column(Modifier.testTag(RECEPTION_TAG)) {
                    for (look in WaveLook.entries) {
                        Row {
                            for (heard in listOf(1f, 0f)) {
                                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(MARGIN)) {
                                    WaveView(cents = -8f, inTune = false, look = look, modifier = Modifier.size(150.dp, 40.dp), reception = { heard })
                                }
                            }
                        }
                    }
                }
            }
        }
        for (style in allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)) {
            for (dark in listOf(false, true)) {
                shownStyle.value = style
                shownDark.value = dark
                compose.mainClock.advanceTimeBy(STYLE_SETTLE_MS)
                // One capture of the whole grid; each cell is a look at full (left) and worst (right) reception.
                val grid = compose.onNodeWithTag(RECEPTION_TAG).captureToImage().toPixelMap()
                val cellWidth = grid.width / 2
                val cellHeight = grid.height / WaveLook.entries.size
                for ((row, look) in WaveLook.entries.withIndex()) {
                    val what = "$style $look dark=$dark"
                    val crisp = ink(grid, 0, row * cellHeight, cellWidth, cellHeight)
                    val faint = ink(grid, cellWidth, row * cellHeight, cellWidth, cellHeight)
                    assertTrue("$what: fainter with poor reception ($faint vs $crisp)", faint.total < crisp.total * 0.95f)
                    assertTrue("$what: still clear with poor reception ($faint vs $crisp)", faint.total > crisp.total * 0.4f)
                    assertTrue("$what: marks ${faint.markedShare} of its box", faint.markedShare > 0.02f)
                }
            }
        }
    }

    /** A new reception changes the picture while drawing only, without recomposing. */
    @Test
    fun receptionChangesWithoutRecomposing() {
        compose.mainClock.autoAdvance = false
        val heard = mutableFloatStateOf(1f)
        compose.setContent {
            TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = true, animate = false) {
                WaveView(
                    cents = 0f,
                    inTune = false,
                    modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(STROBE_TAG),
                    reception = { heard.floatValue },
                )
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val before = compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()
        val changes = changeCount()
        heard.floatValue = 0f
        compose.mainClock.advanceTimeByFrame()
        val after = compose.onNodeWithTag(STROBE_TAG).captureToImage().toPixelMap()
        assertEquals("recompositions for a new reception", changes, changeCount())
        assertTrue("the wave turned faint", differentShare(before, after) > 0.05f)
    }

    /** Colour difference from the background (the top-left pixel), summed, and the share of pixels clearly marked. */
    private class Ink(val total: Float, val markedShare: Float) {
        override fun toString() = "ink $total, marked $markedShare"
    }

    private fun ink(pixels: PixelMap, left: Int, top: Int, width: Int, height: Int): Ink {
        val background = pixels[left, top]
        var total = 0f
        var marked = 0
        var count = 0
        for (x in left until left + width step 2) {
            for (y in top until top + height step 2) {
                val c = pixels[x, y]
                val difference = abs(c.red - background.red) + abs(c.green - background.green) + abs(c.blue - background.blue)
                total += difference
                count++
                if (difference > 0.15f) marked++
            }
        }
        return Ink(total, marked / count.toFloat())
    }

    private fun capture(look: WaveLook, onNote: Boolean): PixelMap =
        compose.onNodeWithTag(lookTag(look, onNote)).captureToImage().toPixelMap()

    /** Share of pixels (every other one) that differ between [a] and [b]. */
    private fun differentShare(a: PixelMap, b: PixelMap): Float {
        var different = 0
        var total = 0
        for (x in 0 until a.width step 2) {
            for (y in 0 until a.height step 2) {
                total++
                if (!a[x, y].near(b[x, y])) different++
            }
        }
        return different / total.toFloat()
    }

    private fun Color.near(other: Color, tolerance: Float = 0.02f): Boolean =
        abs(red - other.red) < tolerance && abs(green - other.green) < tolerance && abs(blue - other.blue) < tolerance

    private fun tag(style: VisualStyle, dark: Boolean) = "wave_${style.name}_$dark"

    private fun lookTag(look: WaveLook, onNote: Boolean) = "look_${look.name}_$onNote"

    private companion object {
        /** Room around the wave in [everyLookStaysInItsBoxInEveryStyle], and how far into it a glow may reach. */
        val MARGIN = 12.dp
        val GLOW_ROOM = 4.dp
        const val BOX_TOLERANCE = 0.06f

        /** Behind the wave, a colour none of the styles uses. */
        val BACKGROUND = Color(0xFF7F00FF)

        /** Each test takes well under a second on the S26 (see TunerContentTest). */
        const val TEST_TIMEOUT_SECONDS = 10L

        /** Long enough for the breathing to fade out on the note (about a second) and the colours to settle. */
        const val SETTLE_MS = 2500L

        /** Long enough for the colours, the halo and the shape to settle after a change of style. */
        const val STYLE_SETTLE_MS = 800L

        const val STROBE_TAG = "strobe"
        const val RECEPTION_TAG = "reception"

        /** Long enough for the strobe to grow to full height and catch up with the reading. */
        const val STROBE_SETTLE_MS = 500L

        /** A quarter of a second at 60 frames a second. */
        const val FRAMES = 15
    }
}
