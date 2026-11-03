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
import androidx.compose.ui.test.assertCountEquals
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
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.components.LENS_SHORT
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The lens and its look (2.0, Phase 71: the trail; the Light arc and the strobe join in Phase 72; v2-plan.md,
 * *The lens* and *Tests*), light and dark, upright and on its side: the box's size (as wide as the screen less
 * 20 dp each side, 0.85 times as high where there is room, half on a short screen, half on its side), the
 * direction (a flat reading's dot left of the middle, a sharp one's right, on the stretched scale), the in-tune
 * zone (the tuner's window, Relaxed and Precise), the quiet state (the border at rest, nothing moving), the
 * border in the reading's tone and green in tune, nothing red, and the lens as one silent item, the trail's
 * words joining the note's spoken reading. Took over the pitch trail tool's `PitchTrailStylesTest`. The Light arc
 * (a flat reading's wedge lit left, a sharp one's right, growing further off; the centre green in tune) and the
 * strobe (drifting left when flat and right when sharp, slower nearer, still in tune; its stripes in the tone)
 * in every case too, and all three looks in the quiet state (nothing coloured, nothing moving) and nothing red.
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
    private val shownHistory = mutableStateOf(PitchHistory())
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownDark = mutableStateOf(false)
    private val shownPrecision = mutableStateOf(InTunePrecision.RELAXED)
    private val shownCompact = mutableStateOf(false)
    private val shownLook = mutableStateOf(LensLook.TRAIL)

    /** The theme's colours, as the lens draws them. */
    private var colours = Colours(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)

    private fun show(settle: Boolean = true) {
        compose.setContent {
            PhoneScreen(shownSize.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    colours = Colours(
                        face = MaterialTheme.colorScheme.surface,
                        line = MaterialTheme.colorScheme.outlineVariant,
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
                        compactLandscape = shownCompact.value,
                        onStrumCheckChange = {},
                        onFocusModeChange = {},
                        lensLook = shownLook.value,
                        pitchHistory = shownHistory.value,
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
    fun uprightTheLensIsTallWhereThereIsRoomAndGivesWayOnAShortScreen() {
        show()
        for (state in listOf(listening, detected(-12f))) {
            shownState.value = state
            for ((size, tall) in listOf(TALL_PHONE to true, PHONE_UPRIGHT to false)) {
                shownSize.value = size
                compose.waitForIdle()
                val box = lens.assertIsDisplayed().getBoundsInRoot()
                val case = "$state ${size.width} x ${size.height}:"
                assertEquals("$case as wide as the screen less 20 dp each side", (size.width - 40.dp).value, box.width.value, 1f)
                val shape = box.height / box.width
                assertTrue("$case its shape $shape", shape >= LENS_SHORT - 0.01f && shape <= LENS_TALL + 0.01f)
                if (tall) assertEquals("$case 0.85 as high as wide", LENS_TALL, shape, 0.01f)
                // The strings stay in view under it.
                val strings = compose.onAllNodesWithText("E2").fetchSemanticsNodes()
                assertTrue("$case the strings show", strings.isNotEmpty())
            }
        }
        // A short phone: the lens gives way to half its width before the strings leave the screen.
        shownSize.value = PHONE_UPRIGHT
        compose.waitForIdle()
        assertTrue("short screen: ${lens.getBoundsInRoot()}", lens.getBoundsInRoot().height / lens.getBoundsInRoot().width < LENS_TALL - 0.05f)
    }

    @Test
    fun onItsSideTheLensIsHalfAsHighAsWide() {
        shownState.value = detected(-12f)
        shownSize.value = PHONE_LANDSCAPE
        show()
        for (compact in listOf(false, true)) {
            shownCompact.value = compact
            compose.waitForIdle()
            val box = lens.assertIsDisplayed().getBoundsInRoot()
            assertEquals("focus $compact: 2:1", LENS_SHORT, box.height / box.width, 0.01f)
        }
    }

    // ---- The look: the trail ----

    @Test
    fun aFlatReadingIsLeftAndASharpOneRight() {
        forEachCase { case ->
            for (cents in listOf(-30f, -8f, 8f, 30f)) {
                shownHistory.value = history { cents }
                shownState.value = detected(cents)
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
    fun theInTuneZoneIsTheTunersWindow() {
        shownState.value = listening
        shownHistory.value = PitchHistory()
        forEachCase { case ->
            for (precision in InTunePrecision.entries) {
                shownPrecision.value = precision
                compose.waitForIdle()
                val picture = capture()
                val window = if (precision == InTunePrecision.RELAXED) 5f else 2f
                val expected = 2f * stretched(window) / 50f * (picture.width / 2f - SCALE_INSET * picture.pxPerDp)
                val band = bandWidth(picture)
                assertEquals("$case $precision: the band", expected, band, 2.5f)
            }
            shownPrecision.value = InTunePrecision.RELAXED
        }
    }

    @Test
    fun theQuietLensRestsAndDrawsNothingNew() {
        forEachCase { case ->
            for (look in LensLook.entries) {
                shownLook.value = look
                // A note that died a moment ago, and nothing at all.
                for (history in listOf(history { t -> if (t < 4.95f) 10f else Float.NaN }, PitchHistory())) {
                    shownHistory.value = history
                    shownState.value = listening
                    compose.waitForIdle()
                    lens.assert(SemanticsMatcher.expectValue(LensQuiet, true))
                    val first = capture()
                    assertNear("$case $look: the border at rest", colours.line, first.at(first.edge, first.height / 2))
                    // Nothing coloured: the Light arc dark but its muted centre ray, the strobe's stripes muted.
                    assertNull("$case $look: colour at rest", first.firstColoured(AMBER_HUES) ?: first.firstColoured(GREEN_HUES))
                    compose.waitForIdle()
                    val second = capture()
                    assertTrue("$case $look: the quiet lens changed", first.bitmap.sameAs(second.bitmap))
                }
                shownState.value = detected(-30f)
                shownHistory.value = history { -30f }
                compose.waitForIdle()
                lens.assert(SemanticsMatcher.expectValue(LensQuiet, false))
            }
            shownLook.value = LensLook.TRAIL
        }
    }

    @Test
    fun theBorderTakesTheReadingsToneAndTurnsGreenInTune() {
        forEachCase { case ->
            shownState.value = detected(-30f)
            shownHistory.value = history { -30f }
            compose.waitForIdle()
            val far = capture()
            val farBorder = far.at(far.edge, far.height / 2)
            assertTrue("$case far off: the border $farBorder nearer amber than the line", distance(farBorder, colours.farOff) < distance(farBorder, colours.line))
            shownState.value = detected(1f, inTune = true)
            shownHistory.value = history { 1f }
            compose.waitForIdle()
            val tuned = capture()
            val tunedBorder = tuned.at(tuned.edge, tuned.height / 2)
            assertTrue("$case in tune: the border $tunedBorder nearer green than amber", distance(tunedBorder, colours.green) < distance(tunedBorder, colours.farOff))
            // 1 cent sharp: a green dot a little right of the middle (6.6 dp on a phone's lens: the scale is stretched there).
            val dot = dotX(tuned, colours.green)
            val expected = tuned.centreX + stretched(1f) / 50f * (tuned.width / 2f - SCALE_INSET * tuned.pxPerDp)
            assertTrue("$case in tune: a green dot at $dot, expected about $expected", abs(dot - expected) < 3f * tuned.pxPerDp)
        }
    }

    @Test
    fun nothingIsRed() {
        forEachCase { case ->
            for (look in LensLook.entries) {
                shownLook.value = look
                for ((state, shape) in listOf(
                    detected(-45f) to history { t -> -45f + 10f * sin(2f * PI.toFloat() * t) },
                    detected(45f) to history { 45f },
                    detected(0f, inTune = true) to history { 0f },
                    listening to PitchHistory(),
                )) {
                    shownState.value = state
                    shownHistory.value = shape
                    compose.waitForIdle()
                    val picture = capture()
                    val red = picture.firstRed()
                    assertNull("$case $look $state: red at $red", red)
                }
            }
            shownLook.value = LensLook.TRAIL
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
    fun theLensIsOneSilentItemAndTheTrailsWordsJoinTheReading() {
        shownState.value = detected(-12f)
        shownHistory.value = history { -12f }
        show()
        val node = lens.assertIsDisplayed().fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(node.config.getOrNull(SemanticsProperties.Text))
        assertTrue("nothing inside the lens for TalkBack", node.children.isEmpty())
        // The word speaks the reading (Phase 71): "E 2, 12 cents flat, Tighten".
        val reading = "E 2, ${context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)}, ${context.getString(R.string.tuner_word_tighten)}"
        // Steady: the reading alone.
        compose.waitUntil(WORDS_WAIT_MS) { compose.onAllNodes(hasContentDescription(reading)).fetchSemanticsNodes().size == 1 }
        // A bend and vibrato are said after it.
        shownHistory.value = history { t -> if (t < 3f) -12f else -12f + (t - 3f) * 25f }
        val bending = "$reading, ${context.getString(R.string.tuner_trail_bending_up)}"
        compose.waitUntil(WORDS_WAIT_MS) { compose.onAllNodes(hasContentDescription(bending)).fetchSemanticsNodes().size == 1 }
        shownHistory.value = history { t -> -12f + 15f * sin(2f * PI.toFloat() * 5f * t) }
        val wavering = "$reading, ${context.getString(R.string.tuner_spoken_wavering)}"
        compose.waitUntil(WORDS_WAIT_MS) { compose.onAllNodes(hasContentDescription(wavering)).fetchSemanticsNodes().size == 1 }
        compose.onAllNodes(hasContentDescription(wavering)).assertCountEquals(1)
    }

    // ---- Pictures ----

    /** The lens as drawn, with its size in px and dp. */
    private inner class Picture(val bitmap: Bitmap, bounds: DpRect) {
        val width = bitmap.width
        val height = bitmap.height
        val pxPerDp = width / bounds.width.value
        val centreX = width / 2f

        /** A column just inside the border's stroke (1.5 dp at rest, 2 with a reading). */
        val edge = (0.75f * pxPerDp).toInt().coerceAtLeast(1)

        fun at(x: Int, y: Int): Color = Color(bitmap.getPixel(x, y))

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

        /** The inside, [INSIDE] dp in from the box's edges: clear of the border, which takes the tone too. */
        private val inside = (INSIDE * pxPerDp).toInt()

        /** The rounded corners' radius, in px (the lens's 22 dp). */
        private val corner = CORNER * pxPerDp

        /** Whether ([x], [y]) is inside the box's rounded shape, [inside] px in from its border all round. */
        private fun withinShape(x: Int, y: Int): Boolean {
            val cx = x.toFloat().coerceIn(corner, width - corner)
            val cy = y.toFloat().coerceIn(corner, height - corner)
            val dx = x - cx
            val dy = y - cy
            return dx * dx + dy * dy <= (corner - inside) * (corner - inside)
        }

        /** Whether the pixel at ([x], [y]) is clearly coloured (not a grey, nor a faint wash) with a hue in [hues]. */
        private fun coloured(x: Int, y: Int, hues: ClosedFloatingPointRange<Float>, hsv: FloatArray): Boolean {
            if (!withinShape(x, y)) return false
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

    /**
     * Where the newest reading's dot is: the middle of the pixels near [colour] on the row the dot sits on
     * ([NOW_FROM_TOP] dp down), a stretch at least the dot's radius wide.
     */
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
        // Step past the hairline in the middle.
        var left = middle - (2f * picture.pxPerDp).toInt()
        while (left > 0 && greenish(left - 1)) left--
        var right = middle + (2f * picture.pxPerDp).toInt()
        while (right < picture.width - 1 && greenish(right + 1)) right++
        return (right - left + 1).toFloat()
    }

    private fun assertNear(what: String, expected: Color, actual: Color) {
        assertTrue("$what: $actual, expected about $expected", distance(expected, actual) < BORDER_NEAR)
    }

    /** The tone of a reading [cents] off, not in tune: amber eased toward green near the zone (`readingToneAmount`). */
    private fun tone(cents: Float): Color {
        val t = 0.7f * (1f - (abs(cents) - 5f) / 14f).coerceIn(0f, 1f)
        val a = colours.farOff
        val b = colours.green
        return Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
    }

    private fun distance(a: Color, b: Color): Float = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))

    /** The stretched scale's place of [cents] in plain cents (k = 1.5, as `meterStretch`). */
    private fun stretched(cents: Float): Float {
        val x = min(abs(cents), 50f) / 50f
        return Math.signum(cents) * 50f * 2.5f * x / (1f + 1.5f * x)
    }

    private data class Colours(val face: Color, val line: Color, val farOff: Color, val green: Color)

    companion object {
        private const val HOP = 1024f / 48_000f

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

        /** 5 s of readings of E2, one per hop: [cents] off at each time (NaN for a gap). */
        fun history(cents: (Float) -> Float): PitchHistory {
            val history = PitchHistory()
            var t = 0f
            while (t < 5f) {
                t += HOP
                val c = cents(t)
                if (c.isNaN()) history.pushGap(t) else history.push(t, 40, c, abs(c) < 5f)
            }
            return history
        }

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 180L
        private const val WORDS_WAIT_MS = 5_000L

        /** The trail's geometry, in dp, as `TrailGeometry` has it. */
        private const val SCALE_INSET = 24f
        private const val NOW_FROM_TOP = 28f

        /** How near a pixel must be to the dot's colour, and to the rest border's, channel by channel. */
        private const val DOT_NEAR = 0.06f
        private const val BORDER_NEAR = 0.08f

        /** Hues: the amber far off, the green in tune, and the tone anywhere between. */
        private val AMBER_HUES = 20f..58f
        private val GREEN_HUES = 100f..175f
        private val TONE_HUES = 20f..175f

        /** The pictures' inside, this many dp in from the box's edges (clear of the 2 dp border). */
        private const val INSIDE = 5f

        /** The lens's corners (`LENS_CORNER`). */
        private const val CORNER = 22f

        /** The strobe test lets the reading settle this long, then lets the stripes drift this long between pictures. */
        private const val SETTLE_MS = 2_000L
        private const val DRIFT_MS = 250L

        private val PHONE_UPRIGHT = DpSize(360.dp, 640.dp)
        private val TALL_PHONE = DpSize(412.dp, 900.dp)
        private val PHONE_LANDSCAPE = DpSize(760.dp, 360.dp)
    }
}
