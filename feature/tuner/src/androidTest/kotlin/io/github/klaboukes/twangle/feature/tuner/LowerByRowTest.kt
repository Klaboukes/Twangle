package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.components.LowerByRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The instrument and tuning panel's "Lower by" row on its own, as wide as on a 360 dp phone: the
 * − and + step a half step at a time and stop at the ends, − on the left in every language, keep
 * their place whatever the amount, and
 * at 2× text every word fits: in Dutch by day, every language overnight. (The row in the panel, a
 * window of its own that takes the phone's text size, is checked in [TunerContentTest].)
 */
@RunWith(AndroidJUnit4::class)
class LowerByRowTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownLocale = mutableStateOf("en")
    private val loweredBy = mutableIntStateOf(0)

    private fun show(fontScale: Float = 1f, lowest: Int = Tuning.MAX_LOWERED_BY) {
        compose.setContent {
            PhoneScreen(PHONE, fontScale = fontScale, locale = shownLocale.value) {
                TwangleTheme(style = VisualStyle.DEFAULT, darkTheme = false, animate = false) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        LowerByRow(loweredBy.intValue, lowest, onChange = { loweredBy.intValue = it })
                    }
                }
            }
        }
    }

    /** − (back up a half step) and + (a half step lower): the shared stepper. */
    private val up get() = compose.onNodeWithTag(TunerTestTags.LOWER_LESS)
    private val down get() = compose.onNodeWithTag(TunerTestTags.LOWER_MORE)

    @Test
    fun minusIsOnTheLeftAndPlusOnTheRightInEveryDirectionOnTheSteppersFace() {
        for (language in listOf("en", "ar")) {
            shownLocale.value = language
            if (language == "en") show() else compose.waitForIdle()
            val less = up.getBoundsInRoot()
            val more = down.getBoundsInRoot()
            assertTrue("$language: − left of +", less.right <= more.left)
            for (bounds in listOf(less, more)) {
                // The face; minimumInteractiveComponentSize keeps 48 dp to touch round it.
                assertEquals("$language: the stepper's 36 dp face", 36f, bounds.width.value, 0.5f)
                assertEquals(36f, bounds.height.value, 0.5f)
            }
        }
    }

    @Test
    fun theButtonsStepAHalfStepAndStopAtTheEnds() {
        show(lowest = 2)
        up.assertIsNotEnabled()
        down.performClick()
        compose.waitForIdle()
        assertEquals(1, loweredBy.intValue)
        up.assertIsEnabled()
        down.performClick()
        compose.waitForIdle()
        assertEquals(2, loweredBy.intValue)
        down.assertIsNotEnabled()
        up.performClick()
        compose.waitForIdle()
        assertEquals(1, loweredBy.intValue)
    }

    @Test
    fun theButtonsKeepTheirPlaceWhateverTheAmountAtTwiceTheTextSizeInDutch() {
        shownLocale.value = "nl"
        show(fontScale = 2f)
        val upAt = up.getBoundsInRoot()
        val downAt = down.getBoundsInRoot()
        for (semitones in 0..Tuning.MAX_LOWERED_BY) {
            compose.runOnIdle { loweredBy.intValue = semitones }
            compose.waitForIdle()
            assertEquals("up at $semitones", upAt, up.getBoundsInRoot())
            assertEquals("down at $semitones", downAt, down.getBoundsInRoot())
            compose.assertEveryTextWhollyShown("nl at 2x, lowered $semitones:")
        }
    }

    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        loweredBy.intValue = Tuning.MAX_LOWERED_BY
        show(fontScale = 2f)
        for (language in checkedLanguages) {
            shownLocale.value = language
            compose.waitForIdle()
            compose.assertEveryTextWhollyShown("$language at 2x:")
        }
    }

    private companion object {
        val PHONE = DpSize(360.dp, 640.dp)

        /** Every language at 2x takes a few seconds; one alone well under one. */
        const val TEST_TIMEOUT_SECONDS = 30L
    }
}
