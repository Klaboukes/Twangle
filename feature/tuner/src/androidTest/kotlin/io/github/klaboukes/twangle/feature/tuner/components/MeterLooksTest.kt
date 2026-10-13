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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * All six meter looks (Classic, Light arc, Spirit level, Rolling scale, Comet and Studio meter) in
 * each of the seven styles, light and dark: clearly drawn, inside their 2:1 box (nothing spills onto
 * the flat and sharp labels or around the frame), skipped by TalkBack, moved by a new reading
 * without recomposing, and lit by the soft in-tune glow (inside the box; at once with "Remove
 * animations").
 */
@RunWith(Parameterized::class)
class MeterLooksTest(private val style: VisualStyle) {

    val compose = createComposeRule()

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
                                Box(modifier = Modifier.testTag(caseTag(look, dark)).background(BACKGROUND).padding(PADDING.dp)) {
                                    MeterFrame(
                                        look = look,
                                        cents = { cents.floatValue },
                                        inTune = inTune.value,
                                        glow = glow.value,
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
    fun everyLookIsClearlyDrawnInsideItsBox() = eachPage { looks ->
        for (look in looks) {
            for (dark in listOf(false, true)) {
                val case = "$style $look ${if (dark) "dark" else "light"}"
                val pixels = compose.onNodeWithTag(caseTag(look, dark)).captureToImage().toPixelMap()
                val pad = pixels.width * PADDING / (FRAME_WIDTH + 2 * PADDING)
                val boxRight = pixels.width - pad
                val boxBottom = pad + (boxRight - pad) / 2
                assertNothingOutsideTheBox(case, pixels)
                // In the box: the plate covers most of it, with clearly drawn marks on it.
                val inBox = buildList {
                    for (x in pad until boxRight step 2) for (y in pad until boxBottom step 2) add(pixels[x, y])
                }
                val drawn = inBox.count { !it.near(BACKGROUND) } / inBox.size.toFloat()
                assertTrue("$case covers ${(drawn * 100).toInt()} % of its box", drawn > 0.75f)
                val face = inBox.groupingBy { it }.eachCount().maxBy { it.value }.key
                val details = inBox.count { it.distanceTo(face) > 0.25f } / inBox.size.toFloat()
                assertTrue("$case details ${(details * 100).toInt()} %", details > 0.01f)
            }
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
        val before = looks.map { compose.onNodeWithTag(caseTag(it, false)).captureToImage().toPixelMap() }
        val changes = changeCount()
        cents.floatValue = 25f
        // The spring and the light arc's and the comet's trails settle, then everything is idle again.
        compose.waitForIdle()
        looks.forEachIndexed { i, look ->
            val after = compose.onNodeWithTag(caseTag(look, false)).captureToImage().toPixelMap()
            assertTrue("$style $look moved", after.differsFrom(before[i]))
        }
        assertEquals("recompositions while the markers moved", changes, changeCount())
    }

    @Test
    fun everyLookGlowsInTuneInsideItsBox() = eachPage { looks ->
        cents.floatValue = 0f
        inTune.value = true
        glow.value = false
        compose.waitForIdle()
        val before = looks.associateWith { look -> listOf(capture(look, false), capture(look, true)) }
        glow.value = true
        compose.waitForIdle()
        for (look in looks) {
            for (dark in listOf(false, true)) {
                val case = "$style $look ${if (dark) "dark" else "light"}"
                val after = capture(look, dark)
                assertNothingOutsideTheBox(case, after)
                // A soft patch of the box moves towards the "in tune" colour; nothing moves away from it.
                val target = inTuneColours.getValue(dark)
                val unlit = before.getValue(look)[if (dark) 1 else 0]
                var warmer = 0
                var colder = 0
                var all = 0
                for (x in 0 until after.width step 2) {
                    for (y in 0 until after.height step 2) {
                        val change = unlit[x, y].distanceTo(target) - after[x, y].distanceTo(target)
                        if (change > GLOW_STEP) warmer++
                        if (change < -GLOW_STEP) colder++
                        all++
                    }
                }
                assertTrue("$case glows on ${warmer * 100 / all} % of its box", warmer > all * GLOW_MIN_SHARE)
                assertTrue("$case: $colder pixels moved away from the in-tune colour", colder <= all * GLOW_STRAY_SHARE)
            }
        }
        inTune.value = false
        glow.value = false
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
            val first = looks.map { capture(it, false) to capture(it, true) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            looks.forEachIndexed { i, look ->
                val (light, dark) = first[i]
                val same = !light.differsFrom(capture(look, false), AT_ONCE_STEP) &&
                    !dark.differsFrom(capture(look, true), AT_ONCE_STEP)
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

    private fun capture(look: MeterLook, dark: Boolean): PixelMap =
        compose.onNodeWithTag(caseTag(look, dark)).captureToImage().toPixelMap()

    /** Asserts nothing of [case] was drawn around its frame or between the flat and sharp labels. */
    private fun assertNothingOutsideTheBox(case: String, pixels: PixelMap) {
        val pad = pixels.width * PADDING / (FRAME_WIDTH + 2 * PADDING)
        val boxRight = pixels.width - pad
        val boxBottom = pad + (boxRight - pad) / 2
        // Around the frame: untouched.
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val around = x < pad - 1 || x > boxRight || y < pad - 1 || y > pixels.height - pad
                if (around) assertTrue("$case drew outside the frame at $x, $y", pixels[x, y].near(BACKGROUND))
            }
        }
        // Between the flat and sharp labels, under the box: untouched too.
        for (x in pixels.width / 4 until pixels.width * 3 / 4) {
            for (y in boxBottom + 2 until pixels.height - pad) {
                assertTrue("$case drew under its box at $x, $y", pixels[x, y].near(BACKGROUND))
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
        fun styles(): List<VisualStyle> = VisualStyle.entries

        /** Every look, two to a page. */
        val PAGES = MeterLook.entries.chunked(2)

        /** Nothing in the styles is this colour. */
        val BACKGROUND = Color(0xFF7F00FF)

        const val PADDING = 8

        /** A pixel this much nearer the "in tune" colour (in its most changed channel) is lit by the glow. */
        const val GLOW_STEP = 0.01f

        /** The glow lights at least this share of a case (its box is about three quarters of it). */
        const val GLOW_MIN_SHARE = 0.04f

        /** Antialiased edges may shift a hair; nothing more moves away from "in tune". */
        const val GLOW_STRAY_SHARE = 0.002f

        /** "At once": within this many frames of the note being in tune. */
        const val AT_ONCE_FRAMES = 3

        /** Two captures of the same look differ by less than this anywhere. */
        const val AT_ONCE_STEP = 0.02f
        const val FRAME_WIDTH = 130

        /** Each test takes a few seconds at most; 20 s means it hangs. */
        const val TEST_TIMEOUT_SECONDS = 20L

        fun caseTag(look: MeterLook, dark: Boolean) = "case_${look.name}_$dark"
        fun frameTag(look: MeterLook, dark: Boolean) = "frame_${look.name}_$dark"
    }
}
