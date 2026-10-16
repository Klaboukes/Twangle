package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.shippedLanguages
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The Practice tab's tool switch over the Chords tool (Phase 32), in each style, light and dark:
 * TalkBack hears its segments as radio buttons by name, the picked one selected; a tap picks a
 * tool; it stands at the top, centred upright and in the start half on its side; at 2× text in
 * Dutch, on a narrow phone and on its side, the switch and the Chords placeholder are wholly shown.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class PracticeToolSwitchTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownTool = mutableStateOf(PracticeTool.CHORDS)
    private val tools = listOf(PracticeTool.METRONOME, PracticeTool.CHORDS)

    private fun show() {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value) then
                DeviceConfigurationOverride.Locales(LocaleList(shownLocale.value))
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    ChordsScreen(
                        header = { PracticeToolSwitch(shownTool.value, { shownTool.value = it }, tools) },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                    )
                }
            }
        }
    }

    private fun segment(tool: PracticeTool) = compose.onNodeWithTag(PracticeTestTags.tool(tool))

    @Test
    fun talkBackHearsTheToolsByNameAndATapPicksOne() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            for (picked in tools) {
                shownTool.value = picked
                compose.waitForIdle()
                for (tool in tools) {
                    val node = segment(tool)
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                        .assert(hasText(if (tool == PracticeTool.METRONOME) "Metronome" else "Chords"))
                    if (tool == picked) node.assertIsSelected() else node.assertIsNotSelected()
                }
            }
        }
        segment(PracticeTool.METRONOME).performClick()
        assertEquals(PracticeTool.METRONOME, shownTool.value)
        segment(PracticeTool.CHORDS).performClick()
        assertEquals(PracticeTool.CHORDS, shownTool.value)
    }

    @Test
    fun theSwitchStandsAtTheTopCentredUprightAndInTheStartHalfOnItsSide() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            val switch = compose.onNodeWithTag(PracticeTestTags.SWITCH).getBoundsInRoot()
            val case = "$style $size:"
            assertTrue("$case at the top $switch", switch.top - window.top in 4.dp..16.dp)
            val middle = (switch.left + switch.right) / 2
            if (size == PHONE_LANDSCAPE) {
                assertTrue("$case in the start half $switch", switch.right <= (window.left + window.right) / 2)
            } else {
                assertEquals("$case centred $switch", ((window.left + window.right) / 2).value, middle.value, 1f)
            }
        }
    }

    @Test
    fun dutchAtTwiceTheTextSizeIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            for (picked in tools) {
                shownTool.value = picked
                compose.waitForIdle()
                for (tool in tools) assertWhollyShown(segment(tool), "$style $size $tool", scroll = false)
                compose.assertEveryTextWhollyShown("$style nl $size $picked picked at 2x:")
            }
        }
    }

    /**
     * Every language at 2× text, either tool picked, upright on a 360 dp phone (as the tuner's and the
     * metronome's checks: on a 320 dp phone a picked Japanese "メトロノーム" beside its check mark needs
     * less than the labels' smallest size, 8 sp, as the tuner's "クロマチック" would) and on its side.
     * Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in shippedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) for (tool in tools) {
                shownSize.value = size
                shownTool.value = tool
                compose.waitForIdle()
                compose.assertEveryTextWhollyShown("$style $language $size $tool at 2x:")
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 20L

        /** A narrow phone less the app's navigation bar (as MetronomeStylesTest), on its side less the rail, and a 320 dp phone. */
        private val PHONE_UPRIGHT = DpSize(360.dp, 650.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
    }
}
