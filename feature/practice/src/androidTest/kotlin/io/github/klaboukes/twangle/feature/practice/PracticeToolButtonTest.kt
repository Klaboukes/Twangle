package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The Practice tab's tool button (Phase 47) over the Chords and Capo tools, in Ink, light
 * and dark: TalkBack hears "Tool, Chords" as a drop-down list; a tap opens the list of tools, heard
 * as a group of radio buttons by name, the picked one selected; a pick changes the tool and closes
 * it. It stands at the top start, upright and on its side, one steady width whatever the tool and
 * the language, with the instrument and tuning beside it; at 2× text in Dutch, on a 320 dp phone and
 * on its side, the button, the tuning line and the list are wholly shown.
 */
@RunWith(AndroidJUnit4::class)
class PracticeToolButtonTest {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownNavigation = mutableStateOf(Navigation.BUTTONS)

    /** The phone's bars around the window: the status bar, and on its side the navigation bar too. */
    private fun bars() = SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, shownNavigation.value)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownTool = mutableStateOf(PracticeTool.CHORDS)
    private val tools = listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO, PracticeTool.EAR_TRAINING)

    /**
     * Chords or Scales, or the capo helper with Capo picked; the metronome and Ear training live in their
     * own modules, so Chords stands in for them.
     */
    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    val header = @androidx.compose.runtime.Composable { PracticeToolButton(shownTool.value, { shownTool.value = it }, tools) }
                    if (shownTool.value == PracticeTool.CAPO) {
                        CapoScreen(
                            tuningName = "Guitar · Standard",
                            spokenTuningName = "Guitar, Standard",
                            notation = Notation.SHARPS,
                            savedShapes = SongKey(PitchClass.C),
                            savedFret = 0,
                            onShapesChange = {},
                            onFretChange = {},
                            onOpenChord = {},
                            header = header,
                            modifier = Modifier.fillMaxSize().testTag(WINDOW),
                            onPickInstrument = {},
                        )
                    } else {
                        ChordsScreen(
                            tuning = Tunings.STANDARD,
                            tuningName = "Guitar · Standard",
                            spokenTuningName = "Guitar, Standard",
                            notation = Notation.SHARPS,
                            savedRoot = PitchClass.C,
                            savedQuality = ChordQuality.MAJOR,
                            onRootChange = {},
                            onQualityChange = {},
                            header = header,
                            modifier = Modifier.fillMaxSize().testTag(WINDOW),
                            scales = shownTool.value == PracticeTool.SCALES,
                            onPickInstrument = {},
                        )
                    }
                }
            }
        }
    }

    private fun button() = compose.onNodeWithTag(PracticeTestTags.TOOL_BUTTON)
    private fun row(tool: PracticeTool) = compose.onNodeWithTag(PracticeTestTags.tool(tool))

    @Test
    fun talkBackHearsTheButtonAndTheListAndAPickChangesTheTool() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            for (picked in tools) {
                shownTool.value = picked
                compose.waitForIdle()
                button()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Tool")))
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, LABELS.getValue(picked)))
                for (tool in tools) row(tool).assertDoesNotExist()
                button().performClick()
                compose.waitForIdle()
                compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Tools"))).assertExists()
                for (tool in tools) {
                    val node = row(tool)
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                        .assert(hasText(LABELS.getValue(tool)))
                    if (tool == picked) node.assertIsSelected() else node.assertIsNotSelected()
                }
                // The tool already picked closes the list and changes nothing.
                row(picked).performClick()
                compose.waitForIdle()
                assertEquals(picked, shownTool.value)
                row(picked).assertDoesNotExist()
            }
        }
        for (tool in listOf(PracticeTool.METRONOME, PracticeTool.SCALES, PracticeTool.CAPO, PracticeTool.CHORDS)) {
            button().performClick()
            compose.waitForIdle()
            row(tool).performClick()
            compose.waitForIdle()
            assertEquals(tool, shownTool.value)
            row(tool).assertDoesNotExist()
        }
    }

    @Test
    fun itStandsAtTheTopStartOneSteadyWidthWithTheTuningBesideIt() {
        show()
        for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE)) for (tool in listOf(PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO)) {
            shownSize.value = size
            shownTool.value = tool
            compose.waitForIdle()
            val window = bars().safeArea(size)
            val bounds = button().getBoundsInRoot()
            val case = "$size $tool:"
            assertTrue("$case at the top $bounds", bounds.top - window.top in 4.dp..16.dp)
            // The column's margin (16 dp), or on its side the start half's (24 dp), before it.
            assertTrue("$case at the start $bounds", bounds.left - window.left in 12.dp..28.dp)
            assertEquals("$case its width", 160f, bounds.width.value, 0.5f)
            assertEquals("$case its touch target", 48f, bounds.height.value, 0.5f)
            val instrument = compose.onNodeWithTag(PracticeTestTags.INSTRUMENT_BUTTON).getBoundsInRoot()
            assertTrue(
                "$case the instrument and tuning beside it $instrument",
                instrument.left >= bounds.right && instrument.top < bounds.bottom && instrument.bottom > bounds.top,
            )
            if (size == PHONE_LANDSCAPE) assertTrue("$case in the start half", bounds.right <= (window.left + window.right) / 2)
        }
    }

    @Test
    fun theListOpensUnderTheButtonWithEveryToolInOrder() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            button().performClick()
            compose.waitForIdle()
            val bounds = button().getBoundsInRoot()
            val rows = tools.map { row(it).getBoundsInRoot() }
            for ((tool, rect) in tools.zip(rows)) assertTrue("$size $tool a 48 dp row $rect", rect.height >= 47.5.dp)
            assertTrue("$size in the tools' order $rows", rows.zipWithNext().all { (a, b) -> a.bottom <= b.top + 0.5.dp })
            // The list is a window of its own, so where each stands on the screen.
            val buttonNode = button().fetchSemanticsNode()
            val buttonBottom = buttonNode.positionOnScreen.y + buttonNode.size.height
            val listTop = row(tools.first()).fetchSemanticsNode().positionOnScreen.y
            assertTrue("$size under the button $bounds: $buttonBottom px, the list at $listTop px", listTop >= buttonBottom - 8 * compose.density.density)
            Espresso.pressBack()
            compose.waitForIdle()
            row(PracticeTool.METRONOME).assertDoesNotExist()
        }
    }

    @Test
    fun dutchAtTwiceTheTextSizeIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for (picked in listOf(PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO)) {
                shownTool.value = picked
                compose.waitForIdle()
                assertEquals("$size its width at 2x", 160f, button().getBoundsInRoot().width.value, 0.5f)
                assertWhollyShown(button(), "$size $picked tool button")
                assertWhollyShown(compose.onNodeWithTag(PracticeTestTags.TUNING), "$size $picked tuning")
                compose.assertEveryTextWhollyShown("nl $size $picked picked at 2x:")
            }
            shownTool.value = PracticeTool.CHORDS
            compose.waitForIdle()
            assertWhollyShown(button(), "$size tool button")
            button().performClick()
            compose.waitForIdle()
            for (tool in tools) assertWhollyShown(row(tool), "$size $tool in the list", scroll = false)
            row(PracticeTool.CHORDS).performClick()
            compose.waitForIdle()
        }
    }

    /**
     * Every language at 2× text on a 320 dp phone and on its side, Chords, Scales and Capo picked, and the
     * list opened: every text wholly shown. Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                for (tool in listOf(PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO)) {
                    shownTool.value = tool
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$language $size $tool at 2x:")
                }
                assertWhollyShown(button(), "$language $size tool button")
                button().performClick()
                compose.waitForIdle()
                for (tool in tools) assertWhollyShown(row(tool), "$language $size $tool in the list", scroll = false)
                row(PracticeTool.CAPO).performClick()
                compose.waitForIdle()
            }
        }
    }

    companion object {
        private val LABELS = mapOf(
            PracticeTool.METRONOME to "Metronome", PracticeTool.CHORDS to "Chords", PracticeTool.SCALES to "Scales", PracticeTool.CAPO to "Capo",
            PracticeTool.EAR_TRAINING to "Ear training",
        )

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L

        /**
         * The Xiaomi and a short narrow phone less their bars and the app's navigation bar (as
         * MetronomeStylesTest), on its side less the rail, and a 320 dp phone.
         */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
    }
}
