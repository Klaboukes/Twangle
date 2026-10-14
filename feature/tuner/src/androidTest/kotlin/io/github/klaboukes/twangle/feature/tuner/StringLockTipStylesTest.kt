package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The string-lock tip on an upright Xiaomi-12X-sized phone at the normal text size in each of the six styles, in English and Dutch: each
 * style brings its own fonts, so the tip's text and the instrument-and-tuning button differ in size. The tip sits in
 * the corner left of the button, above the strings, and covers nothing. [TunerContentTest] covers
 * the tip itself in one style.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class StringLockTipStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tipSitsInTheCornerInEnglish() = assertTipInTheCorner("en")

    @Test
    fun tipSitsInTheCornerInDutch() = assertTipInTheCorner("nl")

    private fun assertTipInTheCorner(locale: String) {
        compose.setContent {
            // The normal text size, whatever the phone's own: a larger one may leave no room in the corner.
            val override = DeviceConfigurationOverride.ForcedSize(PHONE_UPRIGHT) then
                DeviceConfigurationOverride.FontScale(1f) then
                DeviceConfigurationOverride.Locales(LocaleList(locale))
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = false, animate = false) {
                    TunerContent(
                        TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD),
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        showStringLockTip = true,
                    )
                }
            }
        }
        val resources = context.createConfigurationContext(
            context.resources.configuration.apply { setLocale(Locale.forLanguageTag(locale)) },
        ).resources
        fun text(id: Int) = compose.onNodeWithText(resources.getString(id), useUnmergedTree = true)
        val tip = compose.onNodeWithText(resources.getString(R.string.tuner_string_lock_tip))
        val pickerText = "${resources.getString(R.string.instrument_guitar)} · ${resources.getString(R.string.tuning_standard)}"
        val picker = compose.onNodeWithText(pickerText, useUnmergedTree = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val tipBounds = tip.assertIsDisplayed().getBoundsInRoot()
        val button = compose.onNodeWithTag(TunerTestTags.PICKER).getBoundsInRoot()
        val e2 = compose.onNodeWithText("E2").getBoundsInRoot()
        if (style in WIDE_FACES && locale == "nl") {
            // The wide faces' longer button leaves too narrow a corner for the Dutch tip: it sits
            // above the button, its body ending where the button's outline starts (2 dp inside its touch target).
            assertTrue("$style $locale: tip ends at ${tipBounds.bottom}", tipBounds.bottom - POINTER <= button.top + 2.5.dp)
        } else {
            assertTrue("$style $locale: tip ends at ${tipBounds.right}, the button starts at ${button.left}", tipBounds.right <= button.left)
            assertTrue("$style $locale: tip ends at ${tipBounds.bottom}, strings start at ${e2.top}", tipBounds.bottom <= e2.top)
        }
        assertTrue("$style $locale: tip starts at ${tipBounds.left}", tipBounds.left >= window.left)
        assertNoOverlap("tip" to tip, "picker" to picker, "play a string" to text(R.string.tuner_hint_play_string))
    }

    private fun assertNoOverlap(vararg nodes: Pair<String, SemanticsNodeInteraction>) {
        val bounds = nodes.map { (name, node) -> name to node.getBoundsInRoot() }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                val overlap = first.left < second.right && second.left < first.right &&
                    first.top < second.bottom && second.top < first.bottom
                assertFalse("$style: $a $first overlaps $b $second", overlap)
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"

        /** Each test takes well under a second; 10 s means it hangs. */
        private const val TEST_TIMEOUT_SECONDS = 10L

        /** The Xiaomi 12X held upright, without the status and navigation bars. */
        private val PHONE_UPRIGHT = DpSize(393.dp, 790.dp)

        /** Styles whose wide faces make the instrument-and-tuning button too long for the Dutch tip beside it. */
        private val WIDE_FACES = setOf(VisualStyle.FUTURISTIC, VisualStyle.SPACE_AGE)

        /** The height of the tip balloon's pointer. */
        private val POINTER = 8.dp
    }
}
