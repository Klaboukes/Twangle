package io.github.klaboukes.twangle.feature.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.settings.components.creditTag
import io.github.klaboukes.twangle.feature.settings.components.credits
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Settings and About in Ink (1.1 checked them in each of its six styles, whose fonts made rows
 * differ in size). At 2× text,
 * upright (light) and on its side (dark), every title, choice and card name can be scrolled to and
 * is then wholly shown; About's titles, version and credits too. TalkBack, light and dark, hears every
 * light-or-dark choice as a radio button with its name and which one is picked, and the back arrow by
 * name. The lens's Meter cards (Phase 72) and their names are among the texts checked, every language's too
 * (`@EveryLanguage`, overnight). [SettingsContentTest] covers each behaviour.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStylesTest {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownNavigation = mutableStateOf(Navigation.BUTTONS)

    /** The phone's bars around the window: the status bar, and on its side the navigation bar too. */
    private fun bars() = SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, shownNavigation.value)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf<String?>(null)

    /** Settings or, with [about], the About page. */
    private fun show(about: Boolean = false) {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    if (about) {
                        AboutScreen(appVersion = VERSION, onOpenWebsite = {}, onRate = {}, onBack = {}, modifier = Modifier.testTag(WINDOW))
                    } else {
                        SettingsContent(
                            AppSettings(),
                            appVersion = VERSION,
                            onOpenAbout = {},
                            onA4Change = {},
                            onNotationChange = {},
                            onInTunePrecisionChange = {},
                            onThemeChange = {},
                            onDynamicColorChange = {},
                            onKeepScreenOnChange = {},
                            onVibrateInTuneChange = {},
                            onStringTickOffChange = {},
                            onShowSignalBarsChange = {},
                            onLensLookChange = {},
                            onLeftHandedChange = {},
                            modifier = Modifier.testTag(WINDOW),
                            dynamicColorAvailable = true,
                        )
                    }
                }
            }
        }
    }

    /** Shows the page at 2× text on a phone of [size] in the [dark] or light theme, then runs [check]. */
    private fun atTwiceTheTextSize(size: DpSize, dark: Boolean, about: Boolean = false, check: (case: String) -> Unit) =
        atTextSize(2f, size, dark, about, check)

    /** Shows the page at [fontScale] text in a window of [size] in the [dark] or light theme, then runs [check]. */
    private fun atTextSize(fontScale: Float, size: DpSize, dark: Boolean, about: Boolean = false, check: (case: String) -> Unit) {
        shownFontScale.value = fontScale
        shownSize.value = size
        shownDark.value = dark
        show(about)
        check("${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${fontScale}x:")
    }

    /**
     * On a tablet (Phase 52) at its own 1.3× text, upright (light) and on its side (dark), and at 2× upright, where
     * the cards come three to a row: every title, choice and card name wholly shown; About on its side too.
     */
    @Test
    fun onATabletUprightEverySettingIsWhollyShownAtItsTextSize() =
        atTextSize(TABLET_FONT_SCALE, TABLET_UPRIGHT, dark = false, check = ::assertEverySettingWhollyShown)

    @Test
    fun onATabletOnItsSideEverySettingIsWhollyShownAtItsTextSize() =
        atTextSize(TABLET_FONT_SCALE, TABLET_ON_ITS_SIDE, dark = true, check = ::assertEverySettingWhollyShown)

    @Test
    fun onATabletUprightEverySettingIsWhollyShownAtTwiceTheTextSize() =
        atTwiceTheTextSize(TABLET_UPRIGHT, dark = false, check = ::assertEverySettingWhollyShown)

    @Test
    fun onATabletOnItsSideAboutIsWhollyShownAtItsTextSize() =
        atTextSize(TABLET_FONT_SCALE, TABLET_ON_ITS_SIDE, dark = true, about = true, check = ::assertAboutWhollyShown)

    /** Upright in the light theme; on its side in the dark one (the fonts are the same in both). */
    @Test
    fun uprightEverySettingIsWhollyShownAtTwiceTheTextSize() = atTwiceTheTextSize(PHONE_UPRIGHT, dark = false, check = ::assertEverySettingWhollyShown)

    @Test
    fun onItsSideEverySettingIsWhollyShownAtTwiceTheTextSize() = atTwiceTheTextSize(PHONE_ON_ITS_SIDE, dark = true, check = ::assertEverySettingWhollyShown)

    private fun assertEverySettingWhollyShown(case: String) {
        val texts = listOf(
            R.string.settings_group_tuning,
            R.string.settings_a4_title,
            R.string.settings_notation_title,
            R.string.settings_notation_sharps,
            R.string.settings_notation_flats,
            R.string.settings_notation_solfege,
            R.string.settings_notation_german,
            R.string.settings_in_tune_precision_title,
            R.string.settings_in_tune_precision_relaxed,
            R.string.settings_in_tune_precision_precise,
            R.string.settings_group_while_tuning,
            R.string.settings_keep_screen_on_title,
            R.string.settings_vibrate_in_tune_title,
            R.string.settings_string_tick_off_title,
            R.string.settings_show_signal_bars_title,
            R.string.settings_group_look,
            R.string.settings_theme_title,
            R.string.settings_theme_system,
            R.string.settings_theme_light,
            R.string.settings_theme_dark,
            R.string.settings_meter_title,
            R.string.settings_meter_trail,
            R.string.settings_meter_light_arc,
            R.string.settings_meter_strobe,
            R.string.settings_left_handed_title,
            R.string.settings_group_twangle,
            R.string.about_title,
        ).map { context.getString(it) } +
            context.getString(R.string.settings_a4_value, 440) +
            context.getString(R.string.settings_about_link_summary, VERSION) +
            // Wallpaper colours (dynamicColorAvailable here).
            context.getString(R.string.settings_dynamic_color_title)
        // The page title sits in the top bar, which doesn't scroll.
        assertWhollyShown(compose.onNodeWithText(context.getString(R.string.settings_title), useUnmergedTree = true), "$case title", scroll = false)
        for (text in texts) assertWhollyShown(compose.onNodeWithText(text, useUnmergedTree = true), "$case $text")
    }

    @Test
    fun uprightAboutIsWhollyShownAtTwiceTheTextSize() = atTwiceTheTextSize(PHONE_UPRIGHT, dark = false, about = true, check = ::assertAboutWhollyShown)

    @Test
    fun onItsSideAboutIsWhollyShownAtTwiceTheTextSize() = atTwiceTheTextSize(PHONE_ON_ITS_SIDE, dark = true, about = true, check = ::assertAboutWhollyShown)

    private fun assertAboutWhollyShown(case: String) {
        val texts = listOf(
            context.getString(R.string.settings_about_version, VERSION),
            context.getString(R.string.settings_about_free_title),
            context.getString(R.string.settings_about_free_summary),
            context.getString(R.string.settings_about_privacy_title),
            context.getString(R.string.settings_about_privacy_summary),
            context.getString(R.string.settings_about_credits_title),
            context.getString(R.string.settings_about_credits_summary),
        ) + credits.map { it.name }
        assertWhollyShown(compose.onNodeWithText(context.getString(R.string.about_title), useUnmergedTree = true), "$case title", scroll = false)
        texts.forEach { assertWhollyShown(compose.onNodeWithText(it, useUnmergedTree = true), "$case $it") }
        credits.forEach { compose.onNodeWithTag(creditTag(it.name)).performScrollTo().assertHasClickAction() }
    }

    @Test
    fun talkBackHearsEveryChoiceByName() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            val case = "${if (dark) "dark" else "light"}:"
            for ((id, selected) in listOf(R.string.settings_theme_system to true, R.string.settings_theme_light to false, R.string.settings_theme_dark to false)) {
                val row = compose.onNodeWithText(context.getString(id)).performScrollTo()
                row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                if (selected) row.assertIsSelected() else row.assertIsNotSelected()
            }
            // The app's one choice row (ui.md, *Shared controls*): at least 48 dp tall, and every
            // name starts at the same place, picked or not (the ✓'s room is kept on each row).
            val starts = listOf(R.string.settings_theme_system, R.string.settings_theme_light, R.string.settings_theme_dark).map { id ->
                val height = compose.onNodeWithText(context.getString(id)).getBoundsInRoot().let { it.bottom - it.top }
                assertTrue("$case row $height", height >= 47.5.dp)
                compose.onNodeWithText(context.getString(id), useUnmergedTree = true).getBoundsInRoot().left.value
            }
            assertTrue("$case names line up $starts", starts.all { kotlin.math.abs(it - starts[0]) < 0.5f })
        }
    }

    /**
     * Every language at 2× text, upright and on its side: every text of Settings, and of About, wholly
     * shown. Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsInSettingsAtTwiceTheTextSize() = everyLanguageFits(about = false)

    @EveryLanguage
    @Test
    fun everyLanguageFitsInAboutAtTwiceTheTextSize() = everyLanguageFits(about = true)

    private fun everyLanguageFits(about: Boolean) {
        shownFontScale.value = 2f
        show(about)
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_ON_ITS_SIDE)) {
                shownSize.value = size
                compose.waitForIdle()
                compose.assertEveryTextWhollyShown("$language ${size.width} x ${size.height} at 2x:")
            }
        }
    }

    companion object {
        private const val WINDOW = "window"
        private const val VERSION = "1.2.3-test"

        /** A test scrolls to some sixty rows, in about 6 s; 20 s means it hangs. */
        private const val TEST_TIMEOUT_SECONDS = 20L

        /** A narrow phone (a Galaxy S26) upright and on its side, without the system bars. */
        private val PHONE_UPRIGHT = DpSize(360.dp, 730.dp)
        private val PHONE_ON_ITS_SIDE = DpSize(800.dp, 360.dp)

        /** A tablet (the Lenovo Tab M11) upright and on its side, and its own text size. */
        private val TABLET_UPRIGHT = DpSize(800.dp, 1280.dp)
        private val TABLET_ON_ITS_SIDE = DpSize(1280.dp, 800.dp)
        private const val TABLET_FONT_SCALE = 1.3f
    }
}
