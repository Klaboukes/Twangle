package io.github.klaboukes.twangle.feature.tuner

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * The tuner upright and in its full layout on its side, in Ink, light and dark. Upright and
 * on its side everything fits without scrolling (in the room the navigation bar or rail leaves) and
 * nothing overlaps; at the owner's 1.25× text on the Xiaomi 12X too. At 2× text every part can be scrolled to
 * and is then wholly shown, and the string buttons' names (solfège flats, the longest) fit. TalkBack
 * hears the reading once, skips the meter, and hears the mode switch and every string
 * by name with which one is picked. [TunerContentTest] covers each behaviour, and
 * [CompactLandscapeStylesTest] the compact layout on its side.
 */
@RunWith(AndroidJUnit4::class)
class TunerStylesTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownState = mutableStateOf<TunerUiState>(detected())
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf<String?>(null)
    private val shownMode = mutableStateOf(TunerMode.AUTO)

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    TunerContent(
                        shownState.value,
                        shownMode.value,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        showTuneHint = true,
                        showSignalBars = true,
                        compactLandscape = false,
                        onStrumCheckChange = {},
                    )
                }
            }
        }
    }

    private fun detected() = TunerUiState.Detected(
        mode = TunerMode.AUTO,
        tuning = Tunings.STANDARD,
        noteName = "A",
        octave = 2,
        frequencyHz = 108.47f,
        cents = -12.3f,
        targetString = 1,
        inTune = false,
    )

    private val twelveCentsFlat get() = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
    private val gauge get() = compose.onNodeWithTag(TunerTestTags.GAUGE)
    private val pickerChip get() = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP)
    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")

    /** Every part of the screen, by name. (1.1's wave view, which took the cents' and frequency's place, went in Phase 67.) */
    private fun parts(): List<Pair<String, SemanticsNodeInteraction>> = buildList {
        add("picker chip" to pickerChip)
        // On a phone on its side there is no ruler yet (Phase 73 lays the tuner out wide).
        if (compose.onAllNodesWithTag(TunerTestTags.RULER).fetchSemanticsNodes().isNotEmpty()) add("ruler" to compose.onNodeWithTag(TunerTestTags.RULER))
        add("meter" to gauge)
        // On a short phone the big note makes room for the ruler above, which names it (Phase 71).
        if (compose.onAllNodesWithText("A").fetchSemanticsNodes().isNotEmpty()) add("note" to compose.onNodeWithText("A"))
        add("cents" to compose.onNodeWithText(twelveCentsFlat))
        add("frequency" to compose.onNodeWithText(context.getString(R.string.tuner_frequency, 108.47f)))
        add("tune hint" to compose.onNodeWithText(context.getString(R.string.tuner_tune_up)))
        add("signal bars" to compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON))
        add("strum check switch" to compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE))
        strings.forEach { add("string $it" to compose.onNodeWithText(it)) }
    }

    /** Upright on a narrow phone at normal text, light and dark. */
    @Test
    fun uprightEverythingFitsWithoutScrolling() {
        show()
        forEachCase(PHONE_UPRIGHT, 1f) { case -> assertAllInView(case) }
    }

    /**
     * On a short phone the note shrinks, never the meter (owner, 2026-10-21: on the Galaxy S26 it was
     * at its 160 dp minimum): the meter keeps the screen's width less 8 dp at each side, light and
     * dark, and everything stays in view.
     */
    @Test
    fun uprightOnAShortPhoneTheMeterKeepsItsFullWidth() {
        show()
        forEachCase(PHONE_UPRIGHT, 1f) { case ->
            assertAllInView(case)
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            val scale = (window.right - window.left) / PHONE_UPRIGHT.width
            val meter = gauge.assertIsDisplayed().getBoundsInRoot()
            // The lens: the screen less 20 dp each side (Phase 71).
            assertEquals("$case meter width", (PHONE_UPRIGHT.width - 40.dp).value * scale, (meter.right - meter.left).value, 1f)
        }
    }

    /** The owner's phone and text size: the Xiaomi 12X upright at 1.25× text. */
    @Test
    fun uprightAtTheOwnersTextSizeEverythingFitsWithoutScrolling() {
        shownFontScale.value = OWNER_FONT_SCALE
        show()
        forEachCase(XIAOMI_UPRIGHT, OWNER_FONT_SCALE) { case -> assertAllInView(case) }
    }

    /** The full layout on a phone on its side: the readout beside the controls, all in view. */
    @Test
    fun onItsSideEverythingFitsWithoutScrolling() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        forEachCase(PHONE_LANDSCAPE, 1f) { case ->
            assertAllInView(case)
            val note = compose.onNodeWithText("A").getBoundsInRoot()
            for (label in strings) {
                val chip = compose.onNodeWithText(label).getBoundsInRoot()
                if (chip.left < note.right) fail("$case: string $label should be right of the note")
            }
        }
    }

    @Test
    fun uprightAtTwiceTheTextSizeEveryPartIsWhollyShown() {
        shownFontScale.value = 2f
        show()
        forEachCase(PHONE_UPRIGHT, 2f) { case -> assertAllWhollyShown(case) }
    }

    @Test
    fun onItsSideAtTwiceTheTextSizeEveryPartIsWhollyShown() {
        shownSize.value = PHONE_LANDSCAPE
        shownFontScale.value = 2f
        show()
        forEachCase(PHONE_LANDSCAPE, 2f) { case -> assertAllWhollyShown(case) }
    }

    /**
     * The string buttons have a fixed size and let their names grow only to 1.3×: the longest names
     * (solfège flats, "Sol♭3") still fit their buttons at 2× text, upright and on its side.
     */
    @Test
    fun theLongestStringNamesFitTheirButtonsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.HALF_STEP_DOWN, notation = Notation.SOLFEGE)
        show()
        val labels = listOf("Mi♭2", "La♭2", "Re♭3", "Sol♭3", "Si♭3", "Mi♭4")
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            for (label in labels) {
                // The name in its button, unmerged: its own text box, not the button's.
                assertWhollyShown(compose.onNode(hasText(label), useUnmergedTree = true), "$size: $label")
            }
        }
    }

    /**
     * TalkBack, light and dark: the reading is one sentence, the meter says nothing, the
     * mode switch and strings have their names, a click action and which one is picked.
     */
    @Test
    fun talkBackHearsEveryPartByName() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            val case = "${if (dark) "dark" else "light"}"
            val spoken = "A 2, $twelveCentsFlat, ${context.getString(R.string.tuner_spoken_tune_up)}"
            compose.onNode(hasContentDescription(spoken)).assertExists()
            val node = gauge.fetchSemanticsNode()
            assertNull("$case: TalkBack reads the meter", node.config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull("$case: TalkBack reads the meter", node.config.getOrNull(SemanticsProperties.Text))
            assertTrue("$case: something inside the meter for TalkBack", node.children.isEmpty())
            pickerChip.assertHasClickAction().assert(hasContentDescription(context.getString(R.string.tuner_spoken_picker, context.getString(R.string.instrument_guitar), context.getString(R.string.tuning_standard))))
            listOf("E 2", "A 2", "D 3", "G 3", "B 3", "E 4").forEachIndexed { index, name ->
                val chip = compose.onNode(hasContentDescription(name)).assertHasClickAction()
                if (index == 1) chip.assertIsSelected() else chip.assertIsNotSelected()
            }
            compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertHasClickAction()
        }
    }

    /**
     * While nothing is heard the hint ("Play a string", "Play a note") stands in the note's slot, in
     * the note's face, where the note then comes, so nothing moves: upright and on its side, at 1× and,
     * in Dutch, at 2× text, in both modes. It is
     * wholly shown (wrapped or set smaller where it must) and never reaches past the note's slot.
     */
    @Test
    fun theIdleHintFitsTheNotesSlot() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            for ((fontScale, locale) in listOf(1f to null, 2f to "nl")) {
                shownSize.value = size
                shownFontScale.value = fontScale
                shownLocale.value = locale
                for (mode in TunerMode.entries) {
                    shownMode.value = mode
                    shownState.value = TunerUiState.Listening(mode, Tunings.STANDARD)
                    compose.waitForIdle()
                    val case = "$mode ${size.width} x ${size.height} at ${fontScale}x in ${locale ?: "en"}:"
                    val resources = locale?.let { localised(it) } ?: context.resources
                    val words = resources.getString(if (mode == TunerMode.AUTO) R.string.tuner_hint_play_string else R.string.tuner_hint_play_note)
                    val hint = compose.onNodeWithText(words)
                    assertWhollyShown(hint, "$case hint")
                    val hintBounds = hint.getBoundsInRoot()
                    shownState.value = detected().copy(mode = mode, targetString = if (mode == TunerMode.AUTO) 1 else null)
                    compose.waitForIdle()
                    if (compose.onAllNodesWithText("A").fetchSemanticsNodes().isEmpty()) {
                        // A short screen leaves the big note out for the ruler (Phase 71): the hint takes the pill's line.
                        val cents = compose.onNodeWithText(resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).getBoundsInRoot()
                        if (hintBounds.top < cents.top - PILL_PADDING - SLACK || hintBounds.bottom > cents.bottom + PILL_PADDING + SLACK) {
                            fail("$case hint $hintBounds is not in the pill's line $cents")
                        }
                        continue
                    }
                    val note = compose.onNodeWithText("A").getBoundsInRoot()
                    if (hintBounds.top < note.top - SLACK || hintBounds.bottom > note.bottom + SLACK) {
                        fail("$case hint $hintBounds reaches past the note's slot $note")
                    }
                }
            }
        }
    }

    private fun localised(language: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) },
    ).resources

    /** Runs [check] in the light and the dark theme, at [size] and [fontScale]. */
    private fun forEachCase(size: DpSize, fontScale: Float, check: (case: String) -> Unit) {
        shownSize.value = size
        shownFontScale.value = fontScale
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            check("${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${fontScale}x:")
        }
    }

    /** Every part is on screen without scrolling, inside the window, and no two overlap. */
    private fun assertAllInView(case: String) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val bounds = parts().map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        for ((name, rect) in bounds) {
            if (rect.left < window.left - SLACK || rect.top < window.top - SLACK || rect.right > window.right + SLACK || rect.bottom > window.bottom + SLACK) {
                fail("$case $name $rect is outside the window $window")
            }
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                // The mode switch's two segments share their border.
                if (a.startsWith("mode switch") && b.startsWith("mode switch")) continue
                // The signal bars stand inside the ruler, beside its heard note.
                if (setOf(a, b) == setOf("ruler", "signal bars")) continue
                if (first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
    }

    /** Every part can be scrolled to and is then wholly shown. */
    private fun assertAllWhollyShown(case: String) {
        for ((name, node) in parts()) {
            // The meter is a picture: scrolled to, it must be wholly in view too.
            assertWhollyShown(node, "$case $name")
        }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    /**
     * Every language at 2× text, upright and on its side, a note and listening: every text wholly shown. Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                for ((name, state) in listOf("note" to detected(), "listening" to TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))) {
                    shownState.value = state
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$language ${size.width} x ${size.height} $name at 2x:")
                }
            }
        }
    }

    companion object {
        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 10L

        /**
         * A short narrow phone (360 x 720 dp with navigation buttons: 650 dp without the status bar and
         * the buttons; a Galaxy S26 has more): upright less the app's navigation bar (at most 80 dp up to 1.25× text, `NavigationBarTest` in :app),
         * on its side less the 80 dp rail.
         */
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)

        /** The owner's Xiaomi 12X upright (393 x 873 dp) without the status bar, the button bar and the app's navigation bar. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp

        /** The cents pill's padding above and below its text. */
        private val PILL_PADDING = 6.dp
    }
}
