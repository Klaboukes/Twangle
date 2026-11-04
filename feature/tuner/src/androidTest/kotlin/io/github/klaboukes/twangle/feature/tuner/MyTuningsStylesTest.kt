package io.github.klaboukes.twangle.feature.tuner

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.tuner.components.MyTunings
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The player's own tunings in the instrument-and-tuning panel, in Ink, in Dutch at
 * 2× text (the longest words at the largest size): the heading, the longest name a tuning may have
 * and its ⋯ button are wholly shown, the button beside the name and inside its row.
 */
@RunWith(AndroidJUnit4::class)
class MyTuningsStylesTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val dutch: Resources = InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("nl")) },
        ).resources
    }

    private val longest = CustomTuning(
        "custom_1",
        CustomTuning.normalizeName("Mijn allerlangste open stemming voor slide gitaar"),
        Instruments.GUITAR_8,
        Instruments.GUITAR_8.defaultTuning.strings,
    )
    private val other = CustomTuning("custom_2", "Laag C", Instruments.GUITAR_8, Instruments.GUITAR_8.defaultTuning.strings.map { Note(it.midi - 2) })

    /**
     * The list's own-tunings part on the panel's surface, as wide as on a 360 dp phone. Not in the
     * panel itself: the panel, the menu and the dialog are windows of their own, which take the
     * phone's text size and language rather than the test's (the phone check covers those).
     */
    private fun show(lowered: Int = 0) {
        compose.setContent {
            PhoneScreen(DpSize(360.dp, 730.dp), SystemBars.NONE, fontScale = 2f, locale = "nl") {
                TwangleTheme(darkTheme = false, animate = false) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        MyTunings(listOf(longest, other), longest.tuning.lowered(lowered), Notation.DEFAULT, onSelect = {}, onEdit = {}, onDelete = {})
                    }
                }
            }
        }
    }

    @Test
    fun ownTuningsAndTheirMenuButtonsFitAtTwiceTheTextSizeInDutch() {
        show()
        val heading = dutch.getString(R.string.tuner_my_tunings)
        assertWhollyShown(compose.onNode(hasContentDescription(heading), useUnmergedTree = true), "$heading")
        for (mine in listOf(longest, other)) {
            val name = compose.onNode(hasText(mine.name) and hasAnyAncestor(hasTestTag(TunerTestTags.customTuning(mine.id))), useUnmergedTree = true)
            assertWhollyShown(name, "${mine.name}")
            val menu = compose.onNodeWithTag(TunerTestTags.customTuningMenu(mine.id))
            assertWhollyShown(menu, "⋯ of ${mine.name}")
            val row = compose.onNodeWithTag(TunerTestTags.customTuning(mine.id)).getBoundsInRoot()
            val button = menu.getBoundsInRoot()
            if (button.left < row.left - SLACK || button.right > row.right + SLACK) fail("⋯ $button is outside its row $row")
            if (name.getBoundsInRoot().right > button.left + SLACK) fail("${mine.name} runs under its ⋯ button")
        }
    }

    @Test
    fun theLongestOwnTuningLoweredFitsWithItsShiftAtTwiceTheTextSizeInDutch() {
        show(lowered = 5)
        val name = dutch.getString(R.string.tuning_lowered_5, longest.name)
        val shown = compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(TunerTestTags.customTuning(longest.id))), useUnmergedTree = true)
        assertWhollyShown(shown, "$name")
        val button = compose.onNodeWithTag(TunerTestTags.customTuningMenu(longest.id)).getBoundsInRoot()
        if (shown.getBoundsInRoot().right > button.left + SLACK) fail("$name runs under its ⋯ button")
    }

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 10L
        private val SLACK = 0.5.dp
    }
}
