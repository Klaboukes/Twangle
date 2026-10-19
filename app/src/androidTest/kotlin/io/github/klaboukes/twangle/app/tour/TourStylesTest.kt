package io.github.klaboukes.twangle.app.tour

import android.os.LocaleList
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.app.navigation.AppNavigationFrame
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.Thorough
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.shippedLanguages
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerContent
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import io.github.klaboukes.twangle.feature.tuner.tour.BalloonSide
import io.github.klaboukes.twangle.feature.tuner.tour.TourHost
import io.github.klaboukes.twangle.feature.tuner.tour.TourMeasures
import io.github.klaboukes.twangle.feature.tuner.tour.TourPart
import io.github.klaboukes.twangle.feature.tuner.tour.TourShown
import io.github.klaboukes.twangle.feature.tuner.tour.TourTargets
import io.github.klaboukes.twangle.feature.tuner.tour.TourTestTags
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The first-start tour over the tuner and its navigation bar (or rail), as the app puts them
 * together: in every style (only the default one unless the run is thorough), each step's balloon
 * and both its buttons wholly shown inside the window, in Dutch at 2× text on a 360 dp phone, on a
 * phone on its side (the compact tuner shows its controls and rail) and in the two-pane layout; Next
 * steps through every part in the layout's reading order, each balloon's pointer at its own part;
 * Skip tour and Back end it at once; TalkBack hears only the balloon; taps
 * outside it do nothing; nothing in the tuner moves when it comes.
 */
@RunWith(Parameterized::class)
class TourStylesTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shownSize = mutableStateOf(XIAOMI)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val shownMode = mutableStateOf(TunerMode.AUTO)
    private val shownOpen = mutableStateOf(true)
    private val focusMode = mutableStateOf(true)
    private val ends = mutableIntStateOf(0)
    private val targets = TourTargets()

    // The content's own density: a forced size scales it to fit the real window.
    private var contentDensity = 1f
    private var modeChanges = 0

    private fun show() {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value) then
                DeviceConfigurationOverride.Locales(androidx.compose.ui.text.intl.LocaleList(shownLocale.value))
            DeviceConfigurationOverride(override) {
                contentDensity = LocalDensity.current.density
                // As on a phone: focus mode turns it on its side, so upright it is off.
                val focus = focusMode.value && shownSize.value.width > shownSize.value.height
                TwangleTheme(style = style, darkTheme = false, animate = false) {
                    TourHost(
                        open = shownOpen.value,
                        steps = TourPart.stepsFor(shownMode.value, focusMode = focus),
                        onEnd = {
                            ends.intValue++
                            shownOpen.value = false
                        },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        targets = targets,
                    ) {
                        AppNavigationFrame(selected = Destination.TUNER, onSelect = {}) {
                            TunerContent(
                                state = TunerUiState.Listening(shownMode.value, Tunings.STANDARD),
                                mode = shownMode.value,
                                onModeChange = { modeChanges++ },
                                onTuningChange = {},
                                onStringClick = {},
                                onRetry = {},
                                onStrumCheckChange = {},
                                compactLandscape = focus,
                                onFocusModeChange = {},
                                controlsAlwaysShown = shownOpen.value,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** In the window's language ([shownLocale]), whatever language the phone gives the app. */
    private fun string(id: Int, vararg args: Any): String {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(shownLocale.value)))
        return context.createConfigurationContext(configuration).getString(id, *args)
    }

    /**
     * Steps through the whole tour with Next (Done at the end), in [order]: at each step the balloon,
     * its text and both buttons are wholly shown inside the window, TalkBack hears "Step i of n. …",
     * and the balloon points at its own part (the pointer's tip over the part, 2 dp from it) without
     * lying over it.
     */
    private fun stepThroughWhollyShown(case: String, layoutOrder: List<TourPart>) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val density = contentDensity
        val order = layoutOrder
        order.forEachIndexed { index, part ->
            val at = "$case step ${index + 1} ($part)"
            val shown = targets.shown ?: error("$at: nothing shown")
            assertEquals("$at: the step in reading order", part, shown.part)
            assertEquals(at, index, shown.index)
            assertEquals(at, order.size, shown.count)
            val text = string(part.text)
            compose.onNodeWithTag(TourTestTags.TEXT)
                .assert(hasText(text))
                .assertContentDescriptionEquals(string(R.string.tour_step_spoken, index + 1, order.size, text))
            val last = index == order.lastIndex
            val next = string(if (last) R.string.tour_done else R.string.tour_next)
            compose.onNodeWithTag(TourTestTags.NEXT).assert(hasText(next))
            val buttons = if (last) listOf(TourTestTags.NEXT to "Next") else listOf(TourTestTags.SKIP to "Skip", TourTestTags.NEXT to "Next")
            for ((tag, what) in listOf(TourTestTags.BALLOON to "balloon", TourTestTags.TEXT to "text") + buttons) {
                val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
                assertWhollyShown(node, "$at $what", scroll = false)
                val b = node.getBoundsInRoot()
                if (b.left < window.left - SLACK.dp || b.right > window.right + SLACK.dp || b.top < window.top - SLACK.dp || b.bottom > window.bottom + SLACK.dp) {
                    fail("$at: $what at $b outside the window $window")
                }
            }
            if (last) {
                // Nothing left to skip on the last step: Done alone.
                compose.onNodeWithTag(TourTestTags.SKIP).assertDoesNotExist()
            } else {
                assertWhollyShown(compose.onNode(hasText(string(R.string.tour_skip)), useUnmergedTree = true), "$at Skip's text", scroll = false)
            }
            assertWhollyShown(compose.onNode(hasText(next), useUnmergedTree = true), "$at Next's text", scroll = false)
            assertPointsAtItsPart(at, part, shown, density)
            compose.onNodeWithTag(TourTestTags.NEXT).performClick()
            compose.waitForIdle()
        }
        assertEquals("$case: Done ends it once", 1, ends.intValue)
        compose.onNodeWithTag(TourTestTags.BALLOON).assertDoesNotExist()
    }

    /** The pointer's tip over [part] (across it), [TourMeasures.POINTER_GAP] from its edge; the balloon not over it. */
    private fun assertPointsAtItsPart(at: String, part: TourPart, shown: TourShown, density: Float) {
        val element = targets.boundsOf(part) ?: error("$at: the part isn't laid out")
        val tip = shown.tip ?: error("$at: no pointer (${shown.side})")
        val gap = when (shown.side) {
            BalloonSide.ABOVE -> element.top - tip.y
            BalloonSide.BELOW -> tip.y - element.bottom
            BalloonSide.RIGHT -> tip.x - element.right
            BalloonSide.LEFT -> element.left - tip.x
            BalloonSide.OVER -> error("$at: over the part")
        }
        assertEquals("$at: the tip ${TourMeasures.POINTER_GAP} dp from the part", TourMeasures.POINTER_GAP * density, gap, 1f)
        val across = if (shown.side == BalloonSide.ABOVE || shown.side == BalloonSide.BELOW) {
            tip.x in element.left..element.right
        } else {
            tip.y in element.top..element.bottom
        }
        assertTrue("$at: the tip $tip points at the part $element", across)
        val b = shown.balloon
        val overlaps = b.left < element.right && element.left < b.right && b.top < element.bottom && element.top < b.bottom
        assertFalse("$at: the balloon $b lies over its part $element", overlaps)
        // The balloon on screen is where the placement says.
        val node = compose.onNodeWithTag(TourTestTags.BALLOON).getBoundsInRoot()
        assertEquals("$at: balloon left", b.left, node.left.value * density, 1.5f)
        assertEquals("$at: balloon top", b.top, node.top.value * density, 1.5f)
    }

    private fun restart() {
        ends.intValue = 0
        shownOpen.value = true
        compose.waitForIdle()
    }

    /** The hardest upright case: Dutch at 2× text on a 360 dp phone. */
    @Test
    fun dutchAtTwiceTheTextOnANarrowPhoneShowsEveryBalloonWhole() {
        shownSize.value = NARROW
        shownFontScale.value = 2f
        shownLocale.value = "nl"
        show()
        stepThroughWhollyShown("$style nl 2x 360 dp", UPRIGHT)
    }

    /**
     * Every language at 2× text on a 360 dp phone: each balloon, its text and buttons wholly shown,
     * pointing at its part (every style in a thorough run). Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageAtTwiceTheTextOnANarrowPhoneShowsEveryBalloonWhole() {
        shownSize.value = NARROW
        shownFontScale.value = 2f
        show()
        for (language in shippedLanguages) {
            shownLocale.value = language
            restart()
            stepThroughWhollyShown("$style $language 2x 360 dp", UPRIGHT)
        }
    }

    /** On its side the compact tuner shows its controls and rail for the tour; Dutch at 2× text. */
    @Test
    fun onItsSideTheCompactTunerShowsItsControlsAndEveryBalloonWhole() {
        shownSize.value = LANDSCAPE
        shownFontScale.value = 2f
        shownLocale.value = "nl"
        show()
        compose.onNodeWithTag(TunerTestTags.COMPACT).assertDoesNotExist()
        assertNotNull("the rail is lit in the last step", targets.boundsOf(TourPart.NAVIGATION))
        assertNotNull("the strum check's switch shows", targets.boundsOf(TourPart.STRUM))
        stepThroughWhollyShown("$style nl 2x on its side", SIDEWAYS)
    }

    /** The full two-pane layout on its side (focus mode off), in English at 1.25×. */
    @Test
    fun theTwoPaneLayoutShowsEveryBalloonWhole() {
        shownSize.value = LANDSCAPE
        shownFontScale.value = OWNER_FONT_SCALE
        focusMode.value = false
        show()
        stepThroughWhollyShown("$style two-pane", TWO_PANE)
    }

    @Thorough
    @Test
    fun theOwnersPhoneShowsEveryBalloonWhole() {
        shownFontScale.value = OWNER_FONT_SCALE
        show()
        stepThroughWhollyShown("$style Xiaomi 1.25x", UPRIGHT)
    }

    @Thorough
    @Test
    fun aTabletOnItsSideShowsEveryBalloonWhole() {
        shownSize.value = TABLET
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        stepThroughWhollyShown("$style tablet nl 2x", SIDEWAYS)
    }

    /** Chromatic mode (the tour started again from Settings) leaves out the instrument button, strum check and strings. */
    @Test
    fun chromaticModeHasFourSteps() {
        shownMode.value = TunerMode.CHROMATIC
        show()
        stepThroughWhollyShown("$style chromatic", UPRIGHT.filter { it in TourPart.stepsFor(TunerMode.CHROMATIC) })
    }

    /** Chromatic mode on its side, Dutch at 2× text: the readout pane first, the rail last. */
    @Test
    fun chromaticModeOnItsSideReadsThePanes() {
        shownMode.value = TunerMode.CHROMATIC
        shownSize.value = LANDSCAPE
        shownFontScale.value = 2f
        shownLocale.value = "nl"
        show()
        stepThroughWhollyShown("$style chromatic on its side", SIDEWAYS.filter { it in TourPart.stepsFor(TunerMode.CHROMATIC) })
    }

    @Test
    fun skipTourEndsItAtOnce() {
        show()
        compose.onNodeWithTag(TourTestTags.NEXT).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TourTestTags.SKIP).performClick()
        compose.waitForIdle()
        assertEquals(1, ends.intValue)
        compose.onNodeWithTag(TourTestTags.BALLOON).assertDoesNotExist()
        compose.onNodeWithTag(TourTestTags.OVERLAY).assertDoesNotExist()
    }

    @Test
    fun backEndsItAtOnce() {
        show()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(1, ends.intValue)
        compose.onNodeWithTag(TourTestTags.BALLOON).assertDoesNotExist()
    }

    /** Started again after it ended (Settings → Show me around), it begins at the first step. */
    @Test
    fun startedAgainItBeginsAtTheFirstStep() {
        show()
        compose.onNodeWithTag(TourTestTags.NEXT).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TourTestTags.SKIP).performClick()
        compose.waitForIdle()
        restart()
        compose.onNodeWithTag(TourTestTags.TEXT).assert(hasText(string(TourPart.MODE.text)))
    }

    /** TalkBack hears a pane "Show me around" with the step's text and two buttons, and nothing of the screen below. */
    @Test
    fun talkBackHearsOnlyTheBalloon() {
        show()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, string(R.string.tour_title))).assertIsDisplayed()
        compose.onNodeWithTag(TourTestTags.TEXT).assertContentDescriptionEquals(string(R.string.tour_step_spoken, 1, UPRIGHT.size, string(R.string.tour_mode)))
        for (tag in listOf(TourTestTags.SKIP, TourTestTags.NEXT)) {
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        for (tag in listOf(TunerTestTags.INSTRUMENT_BUTTON, TunerTestTags.mode(TunerMode.CHROMATIC), NavigationTestTags.tab(Destination.SETTINGS))) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        compose.onNodeWithTag(TourTestTags.SKIP).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.INSTRUMENT_BUTTON).assertExists()
        compose.onNodeWithTag(NavigationTestTags.tab(Destination.SETTINGS)).assertExists()
    }

    /** A tap on the lit part (or anywhere outside the balloon) does nothing: the tour never changes a setting. */
    @Test
    fun tapsOutsideTheBalloonDoNothing() {
        show()
        repeat(3) {
            compose.onNodeWithTag(TourTestTags.NEXT).performClick()
            compose.waitForIdle()
        }
        val mode = targets.boundsOf(TourPart.MODE) ?: error("no mode switch")
        val window = compose.onNodeWithTag(WINDOW).fetchSemanticsNode().positionInRoot
        compose.onNodeWithTag(WINDOW).performTouchInput {
            click(Offset(mode.left + mode.width * 0.75f, mode.centerY) - window)
        }
        compose.waitForIdle()
        assertEquals("the mode switch wasn't reached", 0, modeChanges)
        compose.onNodeWithTag(TourTestTags.BALLOON).assertExists()
    }

    /** The tour lies over the screen: nothing in the tuner or the bar moves when it comes. */
    @Test
    fun nothingMovesWhenTheTourComes() {
        shownOpen.value = false
        focusMode.value = false
        show()
        for (size in listOf(XIAOMI, LANDSCAPE)) {
            shownSize.value = size
            shownOpen.value = false
            compose.waitForIdle()
            val before = TourPart.entries.associateWith { targets.boundsOf(it) }
            shownOpen.value = true
            compose.waitForIdle()
            compose.onNodeWithTag(TourTestTags.BALLOON).assertExists()
            for (part in TourPart.entries) {
                assertNotNull("$style $size: $part laid out", before[part])
                assertEquals("$style $size: $part moved", before[part], targets.boundsOf(part))
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val WINDOW = "tour_test_window"
        private const val SLACK = 1f
        private const val OWNER_FONT_SCALE = 1.25f

        /** Whole windows, the system bars included, as the tour's layer covers them. */
        private val XIAOMI = DpSize(393.dp, 873.dp)
        private val NARROW = DpSize(360.dp, 800.dp)
        private val LANDSCAPE = DpSize(800.dp, 360.dp)
        private val TABLET = DpSize(1280.dp, 800.dp)

        /**
         * The tour's order upright (rows top to bottom, left to right) and in two panes (pane by pane),
         * the bar's or rail's tabs last.
         */
        private val UPRIGHT = listOf(TourPart.MODE, TourPart.METER, TourPart.NOTE, TourPart.FOCUS, TourPart.PICKER, TourPart.STRUM, TourPart.STRINGS, TourPart.NAVIGATION)
        // In focus mode on its side (the compact tuner's controls shown) there is no focus mode step.
        private val SIDEWAYS = listOf(TourPart.METER, TourPart.NOTE, TourPart.MODE, TourPart.PICKER, TourPart.STRUM, TourPart.STRINGS, TourPart.NAVIGATION)
        private val TWO_PANE = listOf(TourPart.METER, TourPart.NOTE, TourPart.MODE, TourPart.FOCUS, TourPart.PICKER, TourPart.STRUM, TourPart.STRINGS, TourPart.NAVIGATION)
    }
}
