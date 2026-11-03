package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
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
import kotlin.math.max

/**
 * The wide layout (2.0, Phase 73; v2-plan.md, *The lens*, Size wide), any window wider than tall, in Ink: the ruler
 * across the full width, the lens wide under it (the window less 24 dp each side, as high as the rest leave), the
 * word under it at the start of its line; with its controls the picker chip on top and the pegs centred under the
 * lens with the tools' buttons beside them; in focus mode no pegs and no chip until a tap, the string small at the
 * end of the word's line; and the Light arc and the strobe drawn in the wide box, light and dark (the trail's line wide
 * in `LensLooksTest`, which drives its clock). [TunerContentTest] has
 * the taps, Back and the timings; [CompactLandscapeStylesTest] focus mode's corners and font sizes.
 */
@RunWith(AndroidJUnit4::class)
class WideLayoutTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    // Set these to change what the window shows without composing it again.
    private val shownState = mutableStateOf<TunerUiState>(LensLooksTest.detected(-12f))
    private val shownMode = mutableStateOf(TunerMode.AUTO)
    private val shownSize = mutableStateOf(PHONE_SIDE)
    private val shownDark = mutableStateOf(false)
    private val shownFocus = mutableStateOf(false)
    private val shownLook = mutableStateOf(LensLook.TRAIL)
    private val fullScreenReports = mutableListOf<Boolean>()

    /** The lens's face in the theme shown. */
    private var face = Color.Unspecified

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    face = MaterialTheme.colorScheme.surface
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerContent(
                        shownState.value,
                        shownMode.value,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        focusMode = shownFocus.value,
                        onStrumCheckChange = {},
                        onFocusModeChange = {},
                        onFullScreenChange = { fullScreenReports += it },
                        lensLook = shownLook.value,
                        pitchHistory = HISTORY,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private val window get() = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
    private val ruler get() = compose.onNodeWithTag(TunerTestTags.RULER)
    private val lens get() = compose.onNodeWithTag(TunerTestTags.GAUGE)
    private val word get() = compose.onNodeWithTag(TunerTestTags.WORD)
    private val pickerChip get() = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP)

    /** The peg named [label] ("E2"): a button with that text (focus mode's small string name has the text, no click). */
    private fun peg(label: String) = compose.onNode(hasText(label) and hasClickAction())

    /** The pegs, low to high. */
    private fun pegs(): List<DpRect> = listOf("E2", "A2", "D3", "G3", "B3", "E4").map { peg(it).assertIsDisplayed().getBoundsInRoot() }

    @Test
    fun theRulerSpansTheWidthOverTheWideLensAndTheWordUnderIt() {
        show()
        for (size in listOf(PHONE_SIDE, PHONE_FULL_SCREEN, TABLET_SIDE)) {
            for (focus in listOf(false, true)) {
                shownSize.value = size
                shownFocus.value = focus
                compose.waitForIdle()
                val case = "${size.width} x ${size.height}${if (focus) " focus" else ""}:"
                val window = window
                val rulerAt = ruler.assertIsDisplayed().getBoundsInRoot()
                val lensAt = lens.assertIsDisplayed().getBoundsInRoot()
                val wordAt = word.assertIsDisplayed().getBoundsInRoot()
                assertEquals("$case the ruler across the window", window.width.value, rulerAt.width.value, 2f)
                assertEquals("$case the lens 24 dp in from each side", (window.width - 48.dp).value, lensAt.width.value, 2f)
                assertEquals("$case the lens centred", (window.left + 24.dp).value, lensAt.left.value, 2f)
                assertTrue("$case lens ${lensAt.width} x ${lensAt.height}: at most half as high as wide", lensAt.height <= lensAt.width / 2 + 1.dp)
                assertTrue("$case lens ${lensAt.width} x ${lensAt.height}: wide", lensAt.width / lensAt.height >= 1.99f)
                assertTrue("$case ruler $rulerAt over the lens $lensAt", rulerAt.bottom <= lensAt.top + 0.5.dp)
                assertTrue("$case word $wordAt under the lens $lensAt", wordAt.top >= lensAt.bottom)
                assertEquals("$case the word at the start", (window.left + 24.dp).value, wordAt.left.value, 2f)
                assertTrue("$case word $wordAt in the window $window", wordAt.bottom <= window.bottom + 0.5.dp)
            }
        }
    }

    /**
     * With its controls: the picker chip on top, the pegs under the lens, centred where the word keeps its room beside
     * them (else at the end of the line, a narrow phone beside the rail), the strum check's switch after them.
     */
    @Test
    fun withItsControlsThePegsAreUnderTheLensAndTheChipOnTop() {
        show()
        for ((size, centred) in listOf(PHONE_SIDE_WIDE to true, PHONE_SIDE to false, NARROW_SIDE to false, TABLET_SIDE to true)) {
            shownSize.value = size
            compose.waitForIdle()
            val case = "${size.width} x ${size.height}:"
            val window = window
            val chip = pickerChip.assertIsDisplayed().getBoundsInRoot()
            val rulerAt = ruler.getBoundsInRoot()
            val lensAt = lens.getBoundsInRoot()
            assertTrue("$case chip $chip over the ruler $rulerAt", chip.bottom <= rulerAt.top + 0.5.dp)
            val pegs = pegs()
            for (peg in pegs) assertTrue("$case peg $peg under the lens $lensAt, in the window", peg.top >= lensAt.bottom && peg.bottom <= window.bottom)
            val toggle = compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertIsDisplayed().getBoundsInRoot()
            if (centred) {
                assertEquals("$case the pegs centred", ((window.left + window.right) / 2).value, ((pegs.first().left + pegs.last().right) / 2).value, 1.5f)
            } else {
                // The switch's 44 dp outline in its 48 dp slot, which ends 24 dp from the side.
                assertEquals("$case the pegs at the end", (window.right - 26.dp).value, toggle.right.value, 1.5f)
            }
            assertTrue("$case the switch $toggle after the last peg ${pegs.last()}", toggle.left >= pegs.last().right)
            assertTrue("$case the switch $toggle level with the pegs", abs(((toggle.top + toggle.bottom) - (pegs.last().top + pegs.last().bottom)).value) < 2f)
            val wordAt = word.getBoundsInRoot()
            assertTrue("$case the word $wordAt before the pegs", wordAt.right <= pegs.first().left)
            val focusButton = compose.onNodeWithTag(TunerTestTags.ENTER_FOCUS).assertIsDisplayed().getBoundsInRoot()
            assertEquals("$case focus mode's button top right", (window.right - 16.dp).value, focusButton.right.value, 1f)
        }
    }

    /** Any note has no pegs; the word stays where it was. */
    @Test
    fun inAnyNoteTheWordKeepsItsPlace() {
        show()
        val guitar = word.getBoundsInRoot()
        shownState.value = LensLooksTest.detected(-12f).copy(mode = TunerMode.CHROMATIC, targetString = null)
        shownMode.value = TunerMode.CHROMATIC
        compose.waitForIdle()
        assertEquals(guitar.left.value, word.getBoundsInRoot().left.value, 0.5f)
        assertEquals(guitar.top.value, word.getBoundsInRoot().top.value, 0.5f)
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertDoesNotExist()
    }

    /** Focus mode: no pegs, no chip, the whole screen, until a tap brings them back for a few seconds. */
    @Test
    fun inFocusModeNoPegsAndNoChipUntilATap() {
        shownFocus.value = true
        shownSize.value = PHONE_FULL_SCREEN
        show()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_MS)
        pickerChip.assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertDoesNotExist()
        peg("E2").assertDoesNotExist()
        assertEquals("the app hides the rail and the bars", true, fullScreenReports.last())
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(FADE_MS)
        pickerChip.assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertIsDisplayed()
        assertEquals("the rail and the bars back", false, fullScreenReports.last())
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        pickerChip.assertDoesNotExist()
        assertEquals(true, fullScreenReports.last())
    }

    /**
     * Each look drawn in the wide box (Phase 72's `wide`), light and dark, with and without focus mode: the box is
     * covered, the look draws clearly inside it, and a flat reading leans left (the Light arc's lit wedge, the trail's
     * line, the strobe's toned bands all start from the left half or fill both). `LensLooksTest` has each look's motion.
     */
    @Test
    fun eachLookIsDrawnWide() {
        show()
        for (look in LensLook.entries) {
            for (dark in listOf(false, true)) {
                for (focus in listOf(false, true)) {
                    shownLook.value = look
                    shownDark.value = dark
                    shownFocus.value = focus
                    compose.waitForIdle()
                    val case = "$look ${if (dark) "dark" else "light"}${if (focus) " focus" else ""}:"
                    val pixels = lens.captureToImage().toPixelMap()
                    var drawn = 0
                    var left = 0
                    var right = 0
                    var all = 0
                    // Inside the box, clear of the rounded corners and the ♭ ♯ in the bottom corners.
                    val inset = pixels.height / 5
                    for (y in inset until pixels.height - inset step 2) {
                        for (x in inset until pixels.width - inset step 2) {
                            all++
                            val p = pixels[x, y]
                            if (max(abs(p.red - face.red), max(abs(p.green - face.green), abs(p.blue - face.blue))) > 0.12f) {
                                drawn++
                                if (x < pixels.width / 2) left++ else right++
                            }
                        }
                    }
                    // The trail draws its line from the readings' times, which this fixed history has left behind;
                    // LensLooksTest drives the trail's clock and checks its line wide.
                    if (look != LensLook.TRAIL) assertTrue("$case draws ${drawn * 100 / all} % of its box", drawn > all / 100)
                    if (look == LensLook.LIGHT_ARC) assertTrue("$case flat lights the left: $left left, $right right", left > right)
                }
            }
        }
    }

    private companion object {
        const val WINDOW = "window"
        const val TEST_TIMEOUT_SECONDS = 60L

        /** A Galaxy S26 on its side beside the rail, without the status bar; and full screen, as focus mode shows it. */
        val PHONE_SIDE = DpSize(700.dp, 330.dp)

        /** A narrow phone on its side beside the rail: the pegs at the line's end. */
        val NARROW_SIDE = DpSize(640.dp, 330.dp)

        /** A Xiaomi 12X on its side beside the rail, without the status bar and the camera's cutout. */
        val PHONE_SIDE_WIDE = DpSize(740.dp, 360.dp)
        val PHONE_FULL_SCREEN = DpSize(780.dp, 360.dp)

        /** A tablet on its side. */
        val TABLET_SIDE = DpSize(1280.dp, 800.dp)

        /** A frame or two, for effects to run on a stopped clock; and the cross-fade of the controls. */
        const val FRAME_MS = 32L
        const val FADE_MS = 1_000L

        /** 5 s of a string 12 cents flat, for the trail. */
        val HISTORY: PitchHistory = LensLooksTest.history { -12f }
    }
}
