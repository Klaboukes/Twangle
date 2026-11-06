package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Keep screen on's warning ("Screen turns off soon", "Tap to keep it on") shows both lines, a tap
 * keeps the screen on, and at 2× text on a small phone every word fits: in Dutch by day, every
 * language in the full check.
 */
@RunWith(AndroidJUnit4::class)
class ScreenOffWarningTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shownLocale = mutableStateOf("en")
    private var keptOn = 0

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            PhoneScreen(PHONE_UPRIGHT, fontScale = fontScale, locale = shownLocale.value) {
                TwangleTheme(darkTheme = false, animate = false) {
                    Box(Modifier.fillMaxSize()) {
                        ScreenOffWarning(
                            left = { 0.5f },
                            onKeepOn = { keptOn++ },
                            animationsOff = true,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun showsBothLinesAndATapKeepsTheScreenOn() {
        show()
        compose.onNodeWithText(context.getString(R.string.tuner_screen_off_soon)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_screen_off_keep_on)).assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.SCREEN_OFF_WARNING).performClick()
        assertEquals(1, keptOn)
    }

    @Test
    fun dutchFitsAtTwiceTheTextSize() {
        shownLocale.value = "nl"
        show(fontScale = 2f)
        compose.assertEveryTextWhollyShown("nl at 2x:")
    }

    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        show(fontScale = 2f)
        for (language in checkedLanguages) {
            shownLocale.value = language
            compose.waitForIdle()
            compose.assertEveryTextWhollyShown("$language at 2x:")
        }
    }

    private companion object {
        val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)

        /** Every language at 2x takes a few seconds; one alone well under one. */
        const val TEST_TIMEOUT_SECONDS = 30L
    }
}
