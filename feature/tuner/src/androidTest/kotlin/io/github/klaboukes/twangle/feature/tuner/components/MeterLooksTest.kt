package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * All six meter looks (Classic, Light arc, Pointers, Eclipse, Arrows and Light bars) in
 * each of the six styles, light and dark: clearly drawn in their 2:1 box, straight on the background
 * (no plate), with a calm field and an in-tune glow that reach past the box and fade out there,
 * never cut off at its edge; skipped by TalkBack, moved by a new reading without recomposing, and
 * lit by the soft in-tune glow (Calm and the stronger Bold; at once with "Remove animations").
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class MeterLooksTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val cents = mutableFloatStateOf(-20f)

    /** Which [PAGES] entry is on screen. */
    private val page = mutableIntStateOf(0)

    private val inTune = mutableStateOf(false)
    private val glow = mutableStateOf(false)
    private val effect = mutableStateOf(InTuneEffect.CALM)
    private val animationsOff = mutableStateOf(false)

    /** The style's "in tune" colour, light (false) and dark (true). */
    private val inTuneColours = mutableMapOf<Boolean, Color>()

    private fun show() {
        compose.setContent {
            // A page of looks, light and dark side by side, small enough to fit on the screen at once
            // whichever way the phone is turned (a case must be on screen to be captured).
            Column {
                for (look in PAGES[page.intValue]) {
                    Row {
                        for (dark in listOf(false, true)) {
                            TwangleTheme(style = style, darkTheme = dark, animate = false) {
                                inTuneColours[dark] = MaterialTheme.styleTokens.inTune
                                // Clipped, so a case's soft field and glow can't reach its neighbour: they must fade out within the padding.
                                Box(modifier = Modifier.testTag(caseTag(look, dark)).clipToBounds().background(BACKGROUND).padding(PADDING.dp)) {
                                    MeterFrame(
                                        look = look,
                                        cents = { cents.floatValue },
                                        inTune = inTune.value,
                                        glow = glow.value,
                                        effect = effect.value,
                                        animationsOff = animationsOff.value,
                                        modifier = Modifier.width(FRAME_WIDTH.dp).testTag(frameTag(look, dark)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Shows each page of looks in turn and runs [check] on its looks. */
    private fun eachPage(check: (List<MeterLook>) -> Unit) {
        show()
        for (i in PAGES.indices) {
            page.intValue = i
            compose.waitForIdle()
            check(PAGES[i])
        }
    }

    @Test
    fun everyLookIsClearlyDrawnInItsBoxAndFadesOutAroundIt() = eachPage { looks ->
        for ((case, pixels) in captureCases(looks)) {
            val name = "$style $case"
            val box = boxOf(pixels)
            assertFadesOutAroundTheBox(name, pixels, box)
            // In the box: clearly drawn marks on the calm field.
            val inBox = buildList {
                for (x in box.left until box.right step 2) for (y in box.top until box.bottom step 2) add(pixels[x, y])
            }
            val face = inBox.groupingBy { it }.eachCount().maxBy { it.value }.key
            val details = inBox.count { it.distanceTo(face) > 0.25f } / inBox.size.toFloat()
            assertTrue("$name details ${(details * 100).toInt()} %", details > 0.01f)
        }
    }

    @Test
    fun talkBackSkipsEveryLook() = eachPage { looks ->
        for (look in looks) {
            for (dark in listOf(false, true)) {
                val node = compose.onNodeWithTag(frameTag(look, dark)).fetchSemanticsNode()
                assertNull("$look", node.config.getOrNull(SemanticsProperties.ContentDescription))
                assertNull("$look", node.config.getOrNull(SemanticsProperties.Text))
                assertTrue("nothing inside the $look meter for TalkBack", node.children.isEmpty())
            }
        }
    }

    @Test
    fun aNewReadingMovesTheMarkerWithoutRecomposing() = eachPage { looks ->
        cents.floatValue = -20f
        compose.waitForIdle()
        val before = captureCases(looks)
        val changes = changeCount()
        cents.floatValue = 25f
        // The spring and the light arc's trail settle, then everything is idle again.
        compose.waitForIdle()
        val after = captureCases(looks)
        for (look in looks) {
            val light = Case(look, dark = false)
            assertTrue("$style $look moved", after.getValue(light).differsFrom(before.getValue(light)))
        }
        assertEquals("recompositions while the markers moved", changes, changeCount())
    }

    /**
     * Every look glows in tune in Calm and in Bold, and Bold lights more of each look, and more
     * strongly, than Calm: the same glow, only stronger and larger. One test, so each glow is
     * measured once.
     */
    @Test
    fun everyLookGlowsInTuneAndBoldGlowsClearlyMoreThanCalm() = eachPage { looks ->
        val calm = glowsOf(looks, InTuneEffect.CALM)
        val bold = glowsOf(looks, InTuneEffect.BOLD)
        for (case in calm.keys) {
            val c = calm.getValue(case)
            val b = bold.getValue(case)
            assertGlows("$style $case ${InTuneEffect.CALM}", c)
            assertGlows("$style $case ${InTuneEffect.BOLD}", b)
            // Not cut off at the box: the glow fades out past it.
            assertTrue("$style $case: bold glow reaches past the box", b.litOutside > 0)
            assertTrue("$style $case: bold lights ${b.lit} pixels, calm ${c.lit}", b.lit > c.lit)
            assertTrue("$style $case: bold glows ${b.strength}, calm ${c.strength}", b.strength > c.strength * BOLD_MIN_GAIN)
        }
    }

    /** How much of a case the glow lights (and how much of that is outside the box), and how strongly (the summed move towards "in tune"). */
    private data class Glow(val lit: Int, val litOutside: Int, val stray: Int, val all: Int, val strength: Float)

    /** The glow of every case of [looks] in [effect], measured against the same case unlit. */
    private fun glowsOf(looks: List<MeterLook>, effect: InTuneEffect): Map<Case, Glow> {
        this.effect.value = effect
        cents.floatValue = 0f
        inTune.value = true
        glow.value = false
        compose.waitForIdle()
        val unlit = captureCases(looks)
        glow.value = true
        compose.waitForIdle()
        val result = captureCases(looks).mapValues { (case, after) ->
            val box = boxOf(after)
            assertFadesOutAroundTheBox("$style $case $effect", after, box)
            val target = inTuneColours.getValue(case.dark)
            val before = unlit.getValue(case)
            var lit = 0
            var litOutside = 0
            var stray = 0
            var all = 0
            var strength = 0f
            for (x in 0 until after.width step 2) {
                for (y in 0 until after.height step 2) {
                    val change = before[x, y].distanceTo(target) - after[x, y].distanceTo(target)
                    if (change > GLOW_STEP) {
                        lit++
                        if (x < box.left || x >= box.right || y < box.top || y >= box.bottom) litOutside++
                        strength += change
                    }
                    if (change < -GLOW_STEP) stray++
                    all++
                }
            }
            Glow(lit, litOutside, stray, all, strength)
        }
        inTune.value = false
        glow.value = false
        this.effect.value = InTuneEffect.CALM
        compose.waitForIdle()
        return result
    }

    /** [case] glows: a soft patch moves towards "in tune", and nothing moves away. */
    private fun assertGlows(case: String, glow: Glow) {
        assertTrue("$case glows on ${glow.lit * 100 / glow.all} % of its case", glow.lit > glow.all * GLOW_MIN_SHARE)
        assertTrue("$case: ${glow.stray} pixels moved away from the in-tune colour", glow.stray <= glow.all * GLOW_STRAY_SHARE)
    }

    @Test
    fun withAnimationsOffTheInTuneLookShowsAtOnce() = eachPage { looks ->
        cents.floatValue = 0f
        for (off in listOf(true, false)) {
            inTune.value = false
            glow.value = false
            animationsOff.value = off
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            inTune.value = true
            glow.value = true
            repeat(AT_ONCE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
            val first = captureCases(looks)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            val settled = captureCases(looks)
            for (look in looks) {
                val same = listOf(false, true).none { dark ->
                    val case = Case(look, dark)
                    first.getValue(case).differsFrom(settled.getValue(case), AT_ONCE_STEP)
                }
                // With animations the colour and the glow are still easing in; without, they are there.
                if (off) {
                    assertTrue("$style $look shows the in-tune look at once", same)
                } else {
                    assertTrue("$style $look eases into the in-tune look", !same)
                }
            }
        }
        animationsOff.value = false
        inTune.value = false
        glow.value = false
    }

    /** One look in the light or the dark theme: one box on the page. */
    private data class Case(val look: MeterLook, val dark: Boolean) {
        override fun toString() = "$look ${if (dark) "dark" else "light"}"
    }

    /**
     * Every case of [looks] as it is on screen now. One capture of the window, cut up per case: a
     * capture costs a frame however much it covers, and these are the pixels each case's own
     * `captureToImage()` would give (its bounds rounded the same way).
     */
    private fun captureCases(looks: List<MeterLook>): Map<Case, PixelMap> {
        val window = compose.onRoot().captureToImage().toPixelMap()
        return looks.flatMap { look -> listOf(Case(look, false), Case(look, true)) }.associateWith { case ->
            val bounds = compose.onNodeWithTag(caseTag(case.look, case.dark)).fetchSemanticsNode().boundsInRoot
            val left = bounds.left.roundToInt()
            val top = bounds.top.roundToInt()
            PixelMap(
                buffer = window.buffer,
                width = bounds.right.roundToInt() - left,
                height = bounds.bottom.roundToInt() - top,
                bufferOffset = window.bufferOffset + top * window.stride + left,
                stride = window.stride,
            )
        }
    }

    /** Where the meter's 2:1 box lies in a case's [pixels] (its padding all round, the flat and sharp labels under it). */
    private fun boxOf(pixels: PixelMap): IntRect {
        val pad = pixels.width * PADDING / (FRAME_WIDTH + 2 * PADDING)
        val right = pixels.width - pad
        return IntRect(pad, pad, right, pad + (right - pad) / 2)
    }

    /**
     * Asserts [case] reaches past its [box] only softly: at the edge of its padding nothing is drawn
     * (the field and the glow have faded out), and across the box's top and sides there is no edge
     * where something stops (a clip), only a gentle change. A clip runs along the edge; a mark drawn
     * just inside it (Classic's in-tune tick, 0.8 dp under the top) is a few px wide, and where its
     * soft edge falls depends on the phone's density: only a step along [EDGE_RUN_DP] or more of the
     * edge counts. The two sides compared are [EDGE_STEP_DP] from the edge, not a fixed number of px:
     * the calm field still changes a little by the box's top, and on a screen of fewer px per dp
     * (the Lenovo tablet's 1.5 against the phones' 2.75 and 3) 2 px either side spanned nearly twice
     * as much of that smooth fade, which read as a stop. (The messages are built only on a failure:
     * per pixel they cost more than the check.)
     */
    private fun assertFadesOutAroundTheBox(case: String, pixels: PixelMap, box: IntRect) {
        for (x in 0 until pixels.width) {
            for (y in listOf(0, pixels.height - 1)) if (!pixels[x, y].near(BACKGROUND)) fail("$case drew at the edge of its padding at $x, $y")
        }
        for (y in 0 until pixels.height) {
            for (x in listOf(0, pixels.width - 1)) if (!pixels[x, y].near(BACKGROUND)) fail("$case drew at the edge of its padding at $x, $y")
        }
        // The case's px per dp: its width is the frame's and the padding's.
        val pxPerDp = pixels.width.toFloat() / (FRAME_WIDTH + 2 * PADDING)
        val across = (EDGE_STEP_DP * pxPerDp).roundToInt().coerceAtLeast(1)
        val run = ceil(EDGE_RUN_DP * pxPerDp).toInt()
        var steps = 0
        for (x in box.left + across until box.right - across) {
            val step = pixels[x, box.top - across].distanceTo(pixels[x, box.top + across])
            steps = if (step > EDGE_JUMP) steps + 1 else 0
            if (steps >= run) fail("$case stops at the box's top at ${x - run + 1}..$x: $step")
        }
        for ((outside, inside) in listOf(box.left - across to box.left + across, box.right + across to box.right - across)) {
            steps = 0
            for (y in box.top + across until box.bottom - across) {
                val step = pixels[outside, y].distanceTo(pixels[inside, y])
                steps = if (step > EDGE_JUMP) steps + 1 else 0
                if (steps >= run) fail("$case stops at the box's side at $outside, ${y - run + 1}..$y: $step")
            }
        }
    }

    private fun changeCount(): Long = compose.runOnIdle { Recomposer.runningRecomposers.value.sumOf { it.changeCount } }

    private fun PixelMap.differsFrom(other: PixelMap, step: Float = 0.1f): Boolean =
        (0 until width step 2).any { x -> (0 until height step 2).any { y -> this[x, y].distanceTo(other[x, y]) > step } }

    private fun Color.distanceTo(other: Color): Float =
        maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))

    private fun Color.near(other: Color): Boolean = distanceTo(other) < 0.02f

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        /** Every look, two to a page. */
        val PAGES = MeterLook.entries.chunked(2)

        /** Nothing in the styles is this colour. */
        val BACKGROUND = Color(0xFF7F00FF)

        /**
         * Room round each box for the field and the glow to fade out in: the dial's field reaches
         * about 100 units below its box and Bold's glow nearly 90 above, 0.3 box widths.
         */
        const val PADDING = 32

        /** How far either side of a box's edge is compared for an abrupt stop: 2 px on the phones, 1 on the tablet. */
        const val EDGE_STEP_DP = 0.75f

        /** More change than this across a box's edge would be a stop, not a fade. */
        const val EDGE_JUMP = 0.06f

        /** A stop as long as this along the edge is a clip; a mark by the edge is shorter. */
        const val EDGE_RUN_DP = 3f

        /** A pixel this much nearer the "in tune" colour (in its most changed channel) is lit by the glow. */
        const val GLOW_STEP = 0.01f

        /** The glow lights at least this share of a case (its box is about a fifth of it). */
        const val GLOW_MIN_SHARE = 0.02f

        /** Antialiased edges may shift a hair; nothing more moves away from "in tune". */
        const val GLOW_STRAY_SHARE = 0.002f

        /** Bold's glow is at least this many times Calm's, summed over the case. */
        const val BOLD_MIN_GAIN = 1.4f

        /** "At once": within this many frames of the note being in tune. */
        const val AT_ONCE_FRAMES = 3

        /** Two captures of the same look differ by less than this anywhere. */
        const val AT_ONCE_STEP = 0.02f
        const val FRAME_WIDTH = 100

        /** Each test takes a few seconds at most; 20 s means it hangs. */
        const val TEST_TIMEOUT_SECONDS = 20L

        fun caseTag(look: MeterLook, dark: Boolean) = "case_${look.name}_$dark"
        fun frameTag(look: MeterLook, dark: Boolean) = "frame_${look.name}_$dark"
    }
}
