package io.github.klaboukes.twangle.feature.tuner

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The string row from a 4-string bass to an 8-string guitar and the 12-string's six courses, in
 * Ink: upright, in
 * the full layout on its side, at normal and 2× text (focus mode's full-screen tuner shows no string row). Every button is in the
 * window, wholly shown with its whole name, clear of its neighbours; every one can be tapped (also
 * just beside it) and TalkBack names it.
 * [TunerStylesTest] covers the six-string guitar.
 */
@RunWith(AndroidJUnit4::class)
class StringRowStylesTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    // Set these to change what the window shows without composing it again.
    private val shownTuning = mutableStateOf(Instruments.GUITAR_8.defaultTuning)
    private val shownNotation = mutableStateOf(Notation.SHARPS)
    private val shownLayout = mutableStateOf(Layout.UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val shownTuned = mutableStateOf<TunedStrings?>(null)
    private val shownTickOff = mutableStateOf(false)
    private val clicked = mutableListOf<Int>()

    private enum class Layout(val size: DpSize) {
        UPRIGHT(DpSize(360.dp, 730.dp)),
        FULL_ON_ITS_SIDE(DpSize(780.dp, 330.dp)),
    }

    /** [focusButton]: focus mode's button at the end of the header, as in the app. */
    private fun show(listening: Boolean = false, focusButton: Boolean = false) {
        compose.setContent {
            val layout = shownLayout.value
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(layout.size, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = false, animate = false) {
                    val tuning = shownTuning.value
                    val notation = shownNotation.value
                    val first = tuning.strings.first()
                    val state = if (listening) {
                        TunerUiState.Listening(TunerMode.AUTO, tuning, notation = notation)
                    } else {
                        TunerUiState.Detected(
                            mode = TunerMode.AUTO,
                            tuning = tuning,
                            noteName = notation.nameIn(tuning, first.pitchClass),
                            octave = first.octave,
                            frequencyHz = 100f,
                            cents = -12.3f,
                            targetString = 0,
                            inTune = false,
                            notation = notation,
                        )
                    }
                    TunerContent(
                        state,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = { clicked += it },
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        focusMode = false,
                        // As in the app: the strum check's switch above the strings.
                        onStrumCheckChange = {},
                        tunedStrings = shownTuned.value,
                        // The ticks show only with the string tick-off on (off by default in the app).
                        stringTickOff = shownTickOff.value,
                        onFocusModeChange = if (focusButton) ({}) else null,
                    )
                }
            }
        }
    }

    /** The names on [tuning]'s buttons, as the row shows them ("F♯1"). */
    private fun labels(tuning: Tuning, notation: Notation) = tuning.strings.map { notation.nameIn(tuning, it) }

    /** Every button's name is in the window, wholly shown, and no two buttons overlap. */
    @Test
    fun everyRowFitsAtNormalText() {
        show()
        for (layout in Layout.entries) {
            shownLayout.value = layout
            for (tuning in TUNINGS) {
                shownTuning.value = tuning
                compose.waitForIdle()
                assertRowFits("$layout ${tuning.id} at 1x", tuning, Notation.SHARPS, scroll = false)
            }
        }
    }

    /** The longest names (solfège) on the most strings, and a 5-string bass, at 2× text: scrolled to, every name is whole. */
    @Test
    fun theLongestNamesFitAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownNotation.value = Notation.SOLFEGE
        show()
        for (layout in Layout.entries) {
            shownLayout.value = layout
            for (tuning in listOf(Instruments.GUITAR_8.defaultTuning, Instruments.BASS_5.defaultTuning)) {
                shownTuning.value = tuning
                compose.waitForIdle()
                assertRowFits("$layout ${tuning.id} solfège at 2x", tuning, Notation.SOLFEGE, scroll = true)
            }
        }
    }

    /**
     * On the most strings, upright and in the full layout on its side: every button can be tapped,
     * a tap just beside a button (between two) still reaches one of them where the row is squeezed (upright; on its side
     * the wide layout has room for the eight spread out, 8 dp apart like the upright six-string's), and TalkBack names each.
     */
    @Test
    fun everyStringCanBeTappedAndHeard() {
        val tuning = Instruments.GUITAR_8.defaultTuning
        val spoken = listOf("F sharp 1", "B 1", "E 2", "A 2", "D 3", "G 3", "B 3", "E 4")
        show(listening = true)
        val labels = labels(tuning, Notation.SHARPS)
        for (layout in listOf(Layout.UPRIGHT, Layout.FULL_ON_ITS_SIDE)) {
            shownLayout.value = layout
            compose.waitForIdle()
            labels.forEachIndexed { index, label ->
                val chip = compose.onNodeWithText(label).assertHasClickAction()
                val description = chip.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
                assertEquals("$layout: TalkBack's name for $label", listOf(spoken[index]), description)
                clicked.clear()
                chip.performClick()
                assertEquals("$layout: tapping $label", listOf(index), clicked)
            }
            // On its side (the wide layout, Phase 73) the eight have room: spread out, not squeezed.
            if (layout == Layout.FULL_ON_ITS_SIDE) continue
            for (index in 0 until labels.size - 1) {
                // In the root's pixels (PhoneScreen's window, at its top left): a forced size larger than the phone gets a smaller density.
                val left = compose.onNodeWithText(labels[index]).fetchSemanticsNode().boundsInRoot
                val right = compose.onNodeWithText(labels[index + 1]).fetchSemanticsNode().boundsInRoot
                val between = Offset((left.right + right.left) / 2f, left.center.y)
                clicked.clear()
                compose.onPhoneScreen().performTouchInput { click(between) }
                compose.waitForIdle()
                if (clicked.size != 1 || clicked.single() !in index..index + 1) {
                    fail("$layout: a tap between ${labels[index]} and ${labels[index + 1]} reached $clicked")
                }
            }
        }
    }

    /**
     * The 12-string's lower four courses carry a small "8", wholly shown inside their buttons, and
     * TalkBack says "with octave string" after the name; the top two, in unison, carry none. Upright
     * and on its side, at normal and 2× text.
     */
    @Test
    fun theTwelveStringMarksItsOctavePairs() {
        val tuning = Instruments.GUITAR_12.defaultTuning
        shownTuning.value = tuning
        show(listening = true)
        val spoken = listOf("E 2, with octave string", "A 2, with octave string", "D 3, with octave string", "G 3, with octave string", "B 3", "E 4")
        val labels = labels(tuning, Notation.SHARPS)
        for (scale in listOf(1f, 2f)) {
            shownFontScale.value = scale
            for (layout in Layout.entries) {
                shownLayout.value = layout
                compose.waitForIdle()
                val case = "$layout at ${scale}x"
                val marks = compose.onAllNodesWithTag(TunerTestTags.OCTAVE_MARK, useUnmergedTree = true).assertCountEquals(4)
                labels.forEachIndexed { index, label ->
                    val chip = compose.onNodeWithText(label)
                    val description = chip.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
                    assertEquals("$case: TalkBack's name for $label", listOf(spoken[index]), description)
                    if (index < 4) {
                        val mark = marks[index]
                        assertWhollyShown(mark, "$case: the 8 on $label", scroll = true)
                        val face = chip.getBoundsInRoot()
                        val eight = mark.getBoundsInRoot()
                        val inside = eight.left >= face.left - SLACK && eight.right <= face.right + SLACK &&
                            eight.top >= face.top - SLACK && eight.bottom <= face.bottom + SLACK
                        assertTrue("$case: the 8 $eight is inside $label's button $face", inside)
                    }
                }
            }
        }
    }

    /**
     * On a 12-string with every course ticked off as tuned, each "8" stays clear of its button's tick
     * (the badge at the button's top right, drawn, not a node: its square is worked out from the
     * button as `stringMarkBadge` places it), so both show; the buttons keep their size. Upright and
     * on its side, at normal and 2× text.
     */
    @Test
    fun theTwelveStringsEightShowsBesideItsTick() {
        val tuning = Instruments.GUITAR_12.defaultTuning
        shownTuning.value = tuning
        shownTickOff.value = true
        show(listening = true)
        val labels = labels(tuning, Notation.SHARPS)
        val tuned = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.tuner_string_tuned)
        for (scale in listOf(1f, 2f)) {
            shownFontScale.value = scale
            for (layout in Layout.entries) {
                shownLayout.value = layout
                shownTuned.value = null
                compose.waitForIdle()
                val before = labels.map { compose.onNodeWithText(it).getBoundsInRoot() }
                shownTuned.value = TunedStrings(tuning, (1L shl tuning.strings.size) - 1)
                compose.waitForIdle()
                val case = "$layout at ${scale}x"
                val marks = compose.onAllNodesWithTag(TunerTestTags.OCTAVE_MARK, useUnmergedTree = true).assertCountEquals(4)
                labels.forEachIndexed { index, label ->
                    val chip = compose.onNodeWithText(label)
                    val state = chip.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
                    assertEquals("$case: TalkBack's state for $label", tuned, state)
                    val face = chip.getBoundsInRoot()
                    assertEquals("$case: $label's button keeps its place and size", before[index], face)
                    if (index < 4) {
                        val eight = marks[index].getBoundsInRoot()
                        assertWhollyShown(marks[index], "$case: the 8 on $label", scroll = true)
                        val badge = DpRect(
                            left = face.right - BADGE_FROM_CORNER - BADGE_REACH,
                            top = face.top + BADGE_FROM_CORNER - BADGE_REACH,
                            right = face.right - BADGE_FROM_CORNER + BADGE_REACH,
                            bottom = face.top + BADGE_FROM_CORNER + BADGE_REACH,
                        )
                        val clear = eight.right <= badge.left || eight.left >= badge.right ||
                            eight.bottom <= badge.top || eight.top >= badge.bottom
                        assertTrue("$case: the 8 $eight on $label is clear of its tick $badge", clear)
                    }
                }
            }
        }
        shownTuned.value = null
    }

    /**
     * The 12-string in every language at 2× text, upright and on its side: every string's name and
     * every "8" wholly shown, and the picker chip says the 12-string's name in that language.
     * The name beside it may end in "…" there, as every long name does at that size (the buttons
     * keep their place; TalkBack hears it whole).
     */
    @EveryLanguage
    @Test
    fun theTwelveStringFitsInEveryLanguageAtTwiceTheTextSize() {
        val tuning = Instruments.GUITAR_12.defaultTuning
        shownTuning.value = tuning
        shownFontScale.value = 2f
        show(listening = true)
        for (language in checkedLanguages) {
            shownLocale.value = language
            val name = localized(language, R.string.instrument_guitar_12)
            for (layout in Layout.entries) {
                shownLayout.value = layout
                compose.waitForIdle()
                val case = "$language $layout at 2x"
                for (label in labels(tuning, Notation.SHARPS)) {
                    assertWhollyShown(compose.onNode(hasText(label), useUnmergedTree = true), "$case: $label", scroll = true)
                }
                val marks = compose.onAllNodesWithTag(TunerTestTags.OCTAVE_MARK, useUnmergedTree = true).assertCountEquals(4)
                for (index in 0 until 4) assertWhollyShown(marks[index], "$case: the 8 on course $index", scroll = true)
                val spoken = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                assertTrue("$case: the picker chip says $spoken", spoken != null && name in spoken)
            }
        }
    }

    /**
     * The banjo's short fifth string, first in the row, stands a little apart: the gap after its button
     * is about [SHORT_STRING_GAP] wider than the others, which are all alike. In each of its tunings,
     * upright and on its side, at normal and 2× text; every button still fits.
     */
    @Test
    fun theBanjosShortStringStandsALittleApart() {
        show(listening = true)
        for (scale in listOf(1f, 2f)) {
            shownFontScale.value = scale
            for (layout in Layout.entries) {
                shownLayout.value = layout
                for (tuning in Instruments.BANJO_5.tunings) {
                    shownTuning.value = tuning
                    compose.waitForIdle()
                    val case = "$layout ${tuning.id} at ${scale}x"
                    assertRowFits(case, tuning, Notation.SHARPS, scroll = true)
                    assertShortStringGap(case, tuning)
                }
            }
        }
    }

    /**
     * The banjo in every language at 2× text, upright and on its side: every string's name wholly
     * shown, the short string still set apart, and the picker chip says the banjo's name in that
     * language.
     */
    @EveryLanguage
    @Test
    fun theBanjoFitsInEveryLanguageAtTwiceTheTextSize() {
        val tuning = Instruments.BANJO_5.defaultTuning
        shownTuning.value = tuning
        shownFontScale.value = 2f
        show(listening = true)
        for (language in checkedLanguages) {
            shownLocale.value = language
            val name = localized(language, R.string.instrument_banjo_5)
            for (layout in Layout.entries) {
                shownLayout.value = layout
                compose.waitForIdle()
                val case = "$language $layout at 2x"
                for (label in labels(tuning, Notation.SHARPS)) {
                    assertWhollyShown(compose.onNode(hasText(label), useUnmergedTree = true), "$case: $label", scroll = true)
                }
                assertShortStringGap(case, tuning)
                val spoken = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                assertTrue("$case: the picker chip says $spoken", spoken != null && name in spoken)
            }
        }
    }

    /** The gap after [tuning]'s first (short) button is [SHORT_STRING_GAP] wider than the even gaps between the rest. */
    private fun assertShortStringGap(case: String, tuning: Tuning) {
        val chips = labels(tuning, Notation.SHARPS).map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        // In the forced window's own dp: a window wider than the phone (on its side, the phone held upright) gets a smaller density.
        val windowPx = compose.onNodeWithTag(WINDOW).fetchSemanticsNode().boundsInRoot.width
        val dpPerPx = shownLayout.value.size.width.value / windowPx
        val gaps = (0 until chips.size - 1).map { ((chips[it + 1].left - chips[it].right) * dpPerPx).dp }
        val even = gaps.drop(1)
        for (gap in even) assertEquals("$case: the even gaps $gaps", even.first().value, gap.value, 1f)
        val extra = gaps.first() - even.first()
        assertTrue("$case: the short string's gap is ${gaps.first()}, the others ${even.first()}", (extra - SHORT_STRING_GAP).value in -2f..2f)
    }

    private fun localized(language: String, id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(configuration).getString(id)
    }

    /**
     * The strum check's switch sits above the strings at their end upright, and beside them after the last one on its
     * side (the wide layout, Phase 73), in the window, for every instrument (4 to 8 strings), at normal and at 2x text.
     */
    @Test
    fun theStrumSwitchSitsAtTheStringsEnd() {
        show(listening = true, focusButton = true)
        for (scale in listOf(1f, 2f)) {
            shownFontScale.value = scale
            for (layout in listOf(Layout.UPRIGHT, Layout.FULL_ON_ITS_SIDE)) {
                shownLayout.value = layout
                for (tuning in Instruments.all.map { it.defaultTuning }) {
                    shownTuning.value = tuning
                    compose.waitForIdle()
                    val case = "$layout ${tuning.id} at ${scale}x"
                    val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
                    val toggle = compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE)
                    assertWhollyShown(toggle, "$case: strum check switch", scroll = true)
                    val strum = toggle.getBoundsInRoot()
                    if (strum.left < window.left - SLACK || strum.right > window.right + SLACK) fail("$case: the switch $strum is outside the window $window")
                    // Where they are, also when scrolled out of view (bounds in the root are cut to the window).
                    val switchNode = toggle.fetchSemanticsNode()
                    val labels = labels(tuning, Notation.SHARPS)
                    val first = compose.onNodeWithText(labels.first()).fetchSemanticsNode()
                    val last = compose.onNodeWithText(labels.last()).fetchSemanticsNode()
                    val switchBottom = switchNode.positionInRoot.y + switchNode.size.height
                    val switchEnd = switchNode.positionInRoot.x + switchNode.size.width
                    val stringsEnd = last.positionInRoot.x + last.size.width
                    if (layout == Layout.FULL_ON_ITS_SIDE) {
                        // Beside the last string, level with the strings.
                        assertTrue("$case: the switch starts at ${switchNode.positionInRoot.x} px, the strings end at $stringsEnd", switchNode.positionInRoot.x >= stringsEnd - 1f)
                        val switchMiddle = switchNode.positionInRoot.y + switchNode.size.height / 2f
                        val stringsMiddle = last.positionInRoot.y + last.size.height / 2f
                        assertEquals("$case: the switch level with the strings", stringsMiddle, switchMiddle, 2f)
                    } else {
                        if (switchBottom > first.positionInRoot.y + 1f) fail("$case: the switch ends at $switchBottom px, the strings start at ${first.positionInRoot.y}")
                        assertTrue("$case: the switch ends at $switchEnd px, the strings at $stringsEnd", switchEnd <= stringsEnd + 1f)
                    }
                }
            }
        }
    }

    /** Each of [tuning]'s buttons in the window, wholly shown, clear of the others. */
    private fun assertRowFits(case: String, tuning: Tuning, notation: Notation, scroll: Boolean) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val chips = labels(tuning, notation).map { label ->
            // The name in its button, unmerged: its own text box, not the button's.
            assertWhollyShown(compose.onNode(hasText(label), useUnmergedTree = true), "$case: $label", scroll = scroll)
            label to compose.onNodeWithText(label).assertIsDisplayed().getBoundsInRoot()
        }
        for ((label, chip) in chips) {
            if (chip.left < window.left - SLACK || chip.right > window.right + SLACK) fail("$case: $label $chip is outside the window $window")
        }
        for (i in 0 until chips.size - 1) {
            val (a, first) = chips[i]
            val (b, second) = chips[i + 1]
            if (first.overlaps(second)) fail("$case: $a $first overlaps $b $second")
            if (second.left < first.left) fail("$case: $b is left of $a")
        }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 10L
        private val SLACK = 0.5.dp

        /**
         * The tick's badge as `stringMarkBadge` (IntonationView.kt) draws it: its centre 0.35 of its
         * 8 dp radius in from the button's top right corner, and its reach, the radius and the 1.5 dp
         * ring of background round it.
         */
        private val BADGE_FROM_CORNER = 8.dp * 0.35f
        private val BADGE_REACH = 8.dp + 1.5.dp

        /** The extra gap after a banjo's short string, as StringSelector sets it. */
        private val SHORT_STRING_GAP = 12.dp

        /** 4, 5, 7 and 8 strings, the ukulele's re-entrant four, the 12-string's six courses and the banjo's five. */
        private val TUNINGS = listOf(
            Instruments.GUITAR_12.defaultTuning,
            Instruments.BANJO_5.defaultTuning,
            Instruments.BASS_4.defaultTuning,
            Instruments.BASS_5.defaultTuning,
            Instruments.UKULELE.defaultTuning,
            Instruments.GUITAR_7.defaultTuning,
            Instruments.GUITAR_8.defaultTuning,
        )
    }
}
