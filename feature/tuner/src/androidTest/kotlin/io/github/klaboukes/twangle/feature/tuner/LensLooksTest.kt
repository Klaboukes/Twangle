package io.github.klaboukes.twangle.feature.tuner

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.components.LENS_LEAST
import io.github.klaboukes.twangle.feature.tuner.components.LENS_TALL
import io.github.klaboukes.twangle.feature.tuner.components.LensQuiet
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The meter and its look (2.0, Phase 71; the Light arc and the strobe join in Phase 72; Classic, the default, and no
 * frame in Phase 78; v2-plan.md, *The meter* and *Tests*), light and dark, upright and on its side: the box's size
 * (as wide as the screen less 8 dp each side, 0.6 times as high where there is room, down to 0.45 on a short screen; on its
 * side 24 dp in and as high as the room left, Phase 73); Classic's needle (left of the middle for a flat reading,
 * right for a sharp one, where its plain scale puts it; ink off the note and green in tune) and its in-tune zone on
 * the arc (the tuner's window, Relaxed and Precise); the Light arc (a flat reading's wedge lit left, a sharp one's
 * right, growing further off; the centre green in tune) and the strobe (drifting left when flat and right when
 * sharp, slower nearer, still in tune; its stripes in the tone); all three looks in the quiet state (nothing
 * coloured, nothing moving) and nothing red; and the meter as one silent item, the word speaking the reading.
 */
@RunWith(AndroidJUnit4::class)
class LensLooksTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val shownState = mutableStateOf<TunerUiState>(listening)
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownDark = mutableStateOf(false)
    private val shownPrecision = mutableStateOf(InTunePrecision.RELAXED)
    private val shownCompact = mutableStateOf(false)
    private val shownLook = mutableStateOf(LensLook.CLASSIC)

    /** The theme's colours, as the meter draws them. */
    private var colours = Colours(Color.Unspecified, Color.Unspecified, Color.Unspecified)

    private fun show(settle: Boolean = true) {
        compose.setContent {
            PhoneScreen(shownSize.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    colours = Colours(
                        ink = MaterialTheme.colorScheme.onSurface,
                        farOff = MaterialTheme.styleTokens.farOff,
                        green = MaterialTheme.styleTokens.inTune,
                    )
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerContent(
                        shownState.value,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        inTunePrecision = shownPrecision.value,
                        focusMode = shownCompact.value,
                        onFocusModeChange = {},
                        lensLook = shownLook.value,
                    )
                }
            }
        }
        if (settle) compose.waitForIdle()
    }

    private val lens get() = compose.onNodeWithTag(TunerTestTags.GAUGE)

    /** Every case the looks are checked in: light and dark, upright, on its side in two panes and in focus mode. */
    private fun forEachCase(check: (String) -> Unit) {
        show()
        for (dark in listOf(false, true)) {
            for ((size, compact) in listOf(PHONE_UPRIGHT to false, PHONE_LANDSCAPE to false, PHONE_LANDSCAPE to true)) {
                shownDark.value = dark
                shownSize.value = size
                shownCompact.value = compact
                compose.waitForIdle()
                check("${if (dark) "dark" else "light"} ${size.width} x ${size.height}${if (compact) " focus" else ""}:")
            }
        }
    }

    // ---- The box ----

    @Test
    fun uprightTheMeterSpansTheColumnAndGivesWayOnAShortScreen() {
        show()
        for (state in listOf(listening, detected(-12f))) {
            shownState.value = state
            for ((size, tall) in listOf(TALL_PHONE to true, PHONE_UPRIGHT to false)) {
                shownSize.value = size
                compose.waitForIdle()
                val box = lens.assertIsDisplayed().getBoundsInRoot()
                val case = "$state ${size.width} x ${size.height}:"
                assertEquals("$case as wide as the screen less 8 dp each side", (size.width - 16.dp).value, box.width.value, 1f)
                val shape = box.height / box.width
                assertTrue("$case its shape $shape", shape >= LENS_LEAST - 0.01f && shape <= LENS_TALL + 0.01f)
                if (tall) assertEquals("$case 0.6 as high as wide", LENS_TALL, shape, 0.01f)
                // The strings stay in view under it.
                val strings = compose.onAllNodesWithText("E2").fetchSemanticsNodes()
                assertTrue("$case the strings show", strings.isNotEmpty())
            }
        }
    }

    /**
     * On its side the meter is wide (2.0, Phase 73, *The meter*, Size wide): the window less 24 dp each side, as high as
     * the ruler and the word's line leave, so about 3.4 : 1 in focus mode on a phone, flatter with the chip's row and
     * the pegs.
     */
    @Test
    fun onItsSideTheMeterIsWideAndTakesTheHeightLeft() {
        shownState.value = detected(-12f)
        shownSize.value = PHONE_LANDSCAPE
        show()
        val shapes = listOf(false, true).associateWith { compact ->
            shownCompact.value = compact
            compose.waitForIdle()
            val box = lens.assertIsDisplayed().getBoundsInRoot()
            assertEquals("focus $compact: as wide as the window less 24 dp each side", (PHONE_LANDSCAPE.width - 48.dp).value, box.width.value, 2f)
            box.width / box.height
        }
        val focus = shapes.getValue(true)
        assertTrue("focus mode's meter $focus : 1", focus in 3f..4.2f)
        assertTrue("with the pegs ${shapes.getValue(false)} : 1, flatter than focus mode's", shapes.getValue(false) > focus)
    }

    // ---- The look: Classic ----

    @Test
    fun classicsNeedlePointsLeftWhenFlatAndRightWhenSharp() {
        forEachCase { case ->
            for (cents in listOf(-30f, -8f, 8f, 30f)) {
                shownState.value = detected(cents)
                compose.waitForIdle()
                val picture = capture()
                assertTrue("$case $cents cents: the needle where its scale puts it", picture.needleAt(cents, colours.ink))
                assertTrue("$case $cents cents: no needle on the other side", !picture.needleAt(-cents, colours.ink))
            }
        }
    }

    @Test
    fun classicsNeedleIsInkOffTheNoteAndGreenInTune() {
        forEachCase { case ->
            shownState.value = detected(-20f)
            compose.waitForIdle()
            assertTrue("$case off the note: an ink needle", capture().needleAt(-20f, colours.ink))
            shownState.value = detected(1f, inTune = true)
            compose.waitForIdle()
            val tuned = capture()
            assertTrue("$case in tune: a green needle", tuned.needleAt(1f, mix(colours.ink, colours.green, 0.9f)))
        }
    }

    @Test
    fun classicsInTuneZoneIsTheTunersWindow() {
        forEachCase { case ->
            for (precision in InTunePrecision.entries) {
                shownPrecision.value = precision
                // In tune, the zone on the arc is the full green.
                shownState.value = detected(0.3f, inTune = true)
                compose.waitForIdle()
                val window = if (precision == InTunePrecision.RELAXED) 5f else 2f
                val zone = capture().zoneHalfCents(colours.green)
                assertEquals("$case $precision: the zone reaches", window, zone, 1.2f)
            }
            shownPrecision.value = InTunePrecision.RELAXED
        }
    }

    @Test
    fun theQuietMeterRestsAndDrawsNothingNew() {
        forEachCase { case ->
            for (look in LensLook.entries) {
                shownLook.value = look
                shownState.value = listening
                compose.waitForIdle()
                lens.assert(SemanticsMatcher.expectValue(LensQuiet, true))
                val first = capture()
                // Nothing coloured: Classic's zone and centre muted, the Light arc dark but its muted centre ray, the strobe's stripes muted.
                assertNull("$case $look: colour at rest", first.firstColoured(AMBER_HUES) ?: first.firstColoured(GREEN_HUES))
                compose.waitForIdle()
                val second = capture()
                assertTrue("$case $look: the quiet meter changed", first.bitmap.sameAs(second.bitmap))
                shownState.value = detected(-30f)
                compose.waitForIdle()
                lens.assert(SemanticsMatcher.expectValue(LensQuiet, false))
            }
            shownLook.value = LensLook.CLASSIC
        }
    }

    @Test
    fun nothingIsRed() {
        forEachCase { case ->
            for (look in LensLook.entries) {
                shownLook.value = look
                for (state in listOf(detected(-45f), detected(45f), detected(0f, inTune = true), listening)) {
                    shownState.value = state
                    compose.waitForIdle()
                    val red = capture().firstRed()
                    assertNull("$case $look $state: red at $red", red)
                }
            }
            shownLook.value = LensLook.CLASSIC
        }
    }

    // ---- The look: the Light arc ----

    @Test
    fun theLightArcLightsAFlatReadingLeftAndASharpOneRight() {
        shownLook.value = LensLook.LIGHT_ARC
        forEachCase { case ->
            for (cents in listOf(-30f, -12f, 12f, 30f)) {
                shownState.value = detected(cents)
                compose.waitForIdle()
                val picture = capture()
                // The lit wedge in the reading's tone (amber far off, toward green nearer): on the reading's side only.
                val (left, right) = picture.countColoured(TONE_HUES)
                assertTrue("$case $cents cents: lit $left left, $right right", left + right > 20)
                if (cents < 0f) assertTrue("$case $cents: flat lights the left ($left, $right; first right ${picture.firstColoured(TONE_HUES)})", right == 0 && left > 0) else assertTrue("$case $cents: sharp lights the right ($left, $right)", left == 0 && right > 0)
            }
            // Further off, more of the wedge is lit.
            val near = run { shownState.value = detected(-12f); compose.waitForIdle(); capture().countColoured(TONE_HUES).first }
            val far = run { shownState.value = detected(-30f); compose.waitForIdle(); capture().countColoured(TONE_HUES).first }
            assertTrue("$case the wedge grows: $near at 12 cents, $far at 30", far > near)
        }
    }

    @Test
    fun theLightArcsCentreTurnsGreenInTune() {
        shownLook.value = LensLook.LIGHT_ARC
        forEachCase { case ->
            shownState.value = detected(0.5f, inTune = true)
            compose.waitForIdle()
            val picture = capture()
            val (left, right) = picture.countColoured(GREEN_HUES)
            assertTrue("$case in tune: green lights $left left, $right right", left + right > 20)
            // The middle ray and its neighbours, the same either side of the middle; no amber wedge.
            assertTrue("$case in tune: green round the middle ($left, $right)", abs(left - right) <= (left + right) / 4 + 4)
            assertEquals("$case in tune: no amber", 0, picture.countColoured(AMBER_HUES).let { it.first + it.second })
        }
    }

    // ---- The look: the strobe ----

    @Test
    fun theStrobeDriftsLeftWhenFlatAndRightWhenSharpAndStandsStillInTune() {
        shownLook.value = LensLook.STROBE
        shownState.value = detected(-30f)
        // The test drives the frames itself: with the clock on manual, the strobe's "infinite" frame loop runs.
        compose.mainClock.autoAdvance = false
        try {
            show(settle = false)
            for (dark in listOf(false, true)) {
                for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                    shownDark.value = dark
                    shownSize.value = size
                    val case = "${if (dark) "dark" else "light"} ${size.width} x ${size.height}:"
                    for ((cents, inTune) in listOf(-30f to false, 30f to false, -3f to false, 0.3f to true)) {
                        shownState.value = detected(cents, inTune)
                        // The marker's spring, the tone and the strobe's own easing settle; the stripes then drift steadily.
                        compose.mainClock.advanceTimeBy(SETTLE_MS)
                        val before = capture()
                        compose.mainClock.advanceTimeBy(DRIFT_MS)
                        val after = capture()
                        val shift = before.stripeShift(after)
                        when {
                            inTune -> assertTrue("$case $cents cents: the stripes stood still", before.bitmap.sameAs(after.bitmap))
                            cents < 0f -> assertTrue("$case $cents cents: drifted ${shift / before.pxPerDp} dp, expected left", shift < 0f)
                            else -> assertTrue("$case $cents cents: drifted ${shift / before.pxPerDp} dp, expected right", shift > 0f)
                        }
                        // Further off, faster: 30 cents moves the stripes further than 3 in the same time.
                        if (cents == -3f) assertTrue("$case 3 cents drifts slower than 30", abs(shift) < abs(lastFarShift) && shift != 0f)
                        if (cents == 30f) lastFarShift = shift
                    }
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** The 30-cents shift of the case just measured, to compare 3 cents against. */
    private var lastFarShift = 0f

    @Test
    fun theStrobesStripesTakeTheTone() {
        shownLook.value = LensLook.STROBE
        forEachCase { case ->
            shownState.value = detected(-30f)
            compose.waitForIdle()
            val far = capture().countColoured(AMBER_HUES)
            assertTrue("$case far off: amber stripes on both halves $far", far.first > 20 && far.second > 20)
            shownState.value = detected(0.3f, inTune = true)
            compose.waitForIdle()
            val tuned = capture()
            val green = tuned.countColoured(GREEN_HUES)
            assertTrue("$case in tune: green stripes $green", green.first > 20 && green.second > 20)
            assertEquals("$case in tune: no amber", 0, tuned.countColoured(AMBER_HUES).let { it.first + it.second })
        }
    }

    // ---- TalkBack ----

    @Test
    fun theMeterIsOneSilentItemAndTheWordSpeaksTheReading() {
        shownState.value = detected(-12f)
        show()
        val node = lens.assertIsDisplayed().fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(node.config.getOrNull(SemanticsProperties.Text))
        assertTrue("nothing inside the meter for TalkBack", node.children.isEmpty())
        // The word speaks the reading (Phase 71): "E 2, 12 cents flat, Tighten".
        val reading = "E 2, ${context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)}, ${context.getString(R.string.tuner_word_tighten)}"
        compose.waitUntil(WORDS_WAIT_MS) { compose.onAllNodes(hasContentDescription(reading)).fetchSemanticsNodes().size == 1 }
    }

    // ---- Pictures ----

    /** The meter as drawn, with its size in px and dp. */
    private inner class Picture(val bitmap: Bitmap, bounds: DpRect) {
        val width = bitmap.width
        val height = bitmap.height
        val pxPerDp = width / bounds.width.value
        val centreX = width / 2f

        fun at(x: Int, y: Int): Color = Color(bitmap.getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1)))

        /** The first pixel whose colour is red (hue within 20° of red, clearly coloured), or null. */
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

        /** The inside, [INSIDE] dp in from the box's edges. */
        private val inside = (INSIDE * pxPerDp).toInt()

        /** Whether the pixel at ([x], [y]) is clearly coloured (not a grey, nor a faint wash) with a hue in [hues]. */
        private fun coloured(x: Int, y: Int, hues: ClosedFloatingPointRange<Float>, hsv: FloatArray): Boolean {
            android.graphics.Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            return hsv[0] in hues && hsv[1] > 0.45f && hsv[2] > 0.35f
        }

        /** How many pixels inside (every other one) are coloured with a hue in [hues], left of the middle and right of it. */
        fun countColoured(hues: ClosedFloatingPointRange<Float>): Pair<Int, Int> {
            val hsv = FloatArray(3)
            var left = 0
            var right = 0
            for (y in inside until height - inside step 2) {
                for (x in inside until width - inside step 2) {
                    if (coloured(x, y, hues, hsv)) if (x < centreX) left++ else if (x > centreX) right++
                }
            }
            return left to right
        }

        /** The first pixel inside coloured with a hue in [hues], or null. */
        fun firstColoured(hues: ClosedFloatingPointRange<Float>): Pair<Int, Int>? {
            val hsv = FloatArray(3)
            for (y in inside until height - inside step 2) {
                for (x in inside until width - inside step 2) if (coloured(x, y, hues, hsv)) return x to y
            }
            return null
        }

        // Classic's dial, as `ClassicDial` lays it out: 340 × 170 units, centred, the pivot 17 units over its bottom.
        private val u = min(width / 340f, height / 170f)
        private val cx = width / 2f
        private val cy = (height + 170f * u) / 2f - 17f * u

        /** The point [distance] units from the pivot towards [cents] (a plain scale: ±90° at ±50). */
        private fun dialPoint(distance: Float, cents: Float): Pair<Float, Float> {
            val angle = cents / 50f * (PI.toFloat() / 2f)
            return (cx + distance * u * sin(angle)) to (cy - distance * u * cos(angle))
        }

        /**
         * Whether the needle points at [cents] in [colour]: along it, half way out (clear of the hub and inside the
         * numbers), a pixel within a unit or so across the needle near that colour.
         */
        fun needleAt(cents: Float, colour: Color): Boolean {
            for (distance in listOf(60f, 75f, 90f)) {
                val (x, y) = dialPoint(distance, cents)
                val angle = cents / 50f * (PI.toFloat() / 2f)
                var found = false
                for (step in -3..3) {
                    val across = step * 0.5f * u
                    val px = (x + across * cos(angle)).toInt()
                    val py = (y + across * sin(angle)).toInt()
                    if (distance(at(px, py), colour) < NEEDLE_NEAR) found = true
                }
                if (!found) return false
            }
            return true
        }

        /** How many cents each way the in-tune zone on the arc reaches: the green run along the arc from its top. */
        fun zoneHalfCents(green: Color): Float {
            var cents = 0f
            while (cents < 20f) {
                val (x, y) = dialPoint(146f, cents + 0.1f)
                if (distance(at(x.toInt(), y.toInt()), green) >= ZONE_NEAR) break
                cents += 0.1f
            }
            return cents
        }

        /**
         * How far the strobe's top band moved from this picture to [after], in px (negative left): the shift that
         * best lines up the band's middle row, over its middle half (where its stripes don't fade), within less
         * than half a stripe each way.
         */
        fun stripeShift(after: Picture): Float {
            val heightDp = height / pxPerDp
            val widthDp = width / pxPerDp
            val bandHeight = (heightDp - 28f - 20f - 20f) / 3f
            val y = ((14f + bandHeight / 2f) * pxPerDp).toInt()
            val period = (widthDp / 5f).coerceIn(24f, 64f) * pxPerDp
            val maxShift = (period / 2f).toInt() - 1
            val from = width / 4 + maxShift
            val to = width * 3 / 4 - maxShift
            var best = 0
            var bestCost = Float.MAX_VALUE
            for (shift in -maxShift..maxShift) {
                var cost = 0f
                for (x in from until to) cost += distance(at(x, y), after.at(x + shift, y))
                if (cost < bestCost) {
                    bestCost = cost
                    best = shift
                }
            }
            return best.toFloat()
        }
    }

    private fun capture(): Picture {
        val node = lens.assertIsDisplayed()
        return Picture(node.captureToImage().asAndroidBitmap(), node.getBoundsInRoot())
    }

    private fun distance(a: Color, b: Color): Float = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))

    private fun mix(a: Color, b: Color, t: Float): Color =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)

    private data class Colours(val ink: Color, val farOff: Color, val green: Color)

    companion object {
        val listening = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)

        /** Low E, [cents] off; in tune within 5 cents unless said. */
        fun detected(cents: Float, inTune: Boolean = abs(cents) < 5f) = TunerUiState.Detected(
            mode = TunerMode.AUTO,
            tuning = Tunings.STANDARD,
            noteName = "E",
            octave = 2,
            frequencyHz = 82.41f * Math.pow(2.0, cents / 1200.0).toFloat(),
            cents = cents,
            targetString = 0,
            inTune = inTune,
        )

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 180L
        private const val WORDS_WAIT_MS = 5_000L

        /** How near a pixel must be to the needle's colour, and to the zone's green, channel by channel. */
        private const val NEEDLE_NEAR = 0.12f
        private const val ZONE_NEAR = 0.15f

        /** Hues: the amber far off, the green in tune, and the tone anywhere between. */
        private val AMBER_HUES = 20f..58f
        private val GREEN_HUES = 100f..175f
        private val TONE_HUES = 20f..175f

        /** The pictures' inside, this many dp in from the box's edges. */
        private const val INSIDE = 5f

        /** The strobe test lets the reading settle this long, then lets the stripes drift this long between pictures. */
        private const val SETTLE_MS = 2_000L
        private const val DRIFT_MS = 250L

        private val PHONE_UPRIGHT = DpSize(360.dp, 640.dp)
        private val TALL_PHONE = DpSize(412.dp, 900.dp)
        private val PHONE_LANDSCAPE = DpSize(760.dp, 360.dp)
    }
}
