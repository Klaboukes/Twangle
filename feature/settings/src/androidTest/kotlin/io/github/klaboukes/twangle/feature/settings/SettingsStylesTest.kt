package io.github.klaboukes.twangle.feature.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.settings.components.creditTag
import io.github.klaboukes.twangle.feature.settings.components.credits
import io.github.klaboukes.twangle.feature.settings.components.meterLookCardTag
import io.github.klaboukes.twangle.feature.settings.components.styleCardTag
import io.github.klaboukes.twangle.feature.settings.components.waveLookCardTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

/**
 * Settings and About in each of the six styles, with that style picked: each style brings its own
 * fonts (Michroma and Orbitron are wide, Caesar Dressing tall), so rows differ in size. At 2× text,
 * upright (light) and on its side (dark), every title, choice and card name can be scrolled to and
 * is then wholly shown; About's titles, version and credits too. TalkBack, light and dark, hears every
 * style, meter and wave card and every light-or-dark choice as a radio button with its name and which
 * one is picked, and the back arrow by name. [SettingsContentTest] covers each behaviour in one style.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class SettingsStylesTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Set these to change what the window shows without composing it again.
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)

    /** Settings (with the wave display, so its rows show too) or, with [about], the About page. */
    private fun show(about: Boolean = false) {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value)
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    if (about) {
                        AboutScreen(appVersion = VERSION, onBack = {}, modifier = Modifier.testTag(WINDOW))
                    } else {
                        SettingsContent(
                            AppSettings(style = style, display = TunerDisplay.WAVE, meterLook = MeterLook.COMET, waveLook = WaveLook.LOOP),
                            appVersion = VERSION,
                            onBack = {},
                            onOpenAbout = {},
                            onA4Change = {},
                            onNotationChange = {},
                            onInTunePrecisionChange = {},
                            onStyleChange = {},
                            onThemeChange = {},
                            onDynamicColorChange = {},
                            onKeepScreenOnChange = {},
                            onVibrateInTuneChange = {},
                            onShowTuneHintChange = {},
                            onShowSignalBarsChange = {},
                            onDisplayChange = {},
                            onMeterLookChange = {},
                            onInTuneEffectChange = {},
                            onWaveLookChange = {},
                            onWaveBreathesInTuneChange = {},
                            onKeepTwoPaneLandscapeChange = {},
                            modifier = Modifier.testTag(WINDOW),
                            dynamicColorAvailable = true,
                        )
                    }
                }
            }
        }
    }

    /** Shows the page at 2× text on a phone of [size] in the [dark] or light theme, then runs [check]. */
    private fun atTwiceTheTextSize(size: DpSize, dark: Boolean, about: Boolean = false, check: (case: String) -> Unit) {
        shownFontScale.value = 2f
        shownSize.value = size
        shownDark.value = dark
        show(about)
        check("$style ${if (dark) "dark" else "light"} ${size.width} x ${size.height} at 2x:")
    }

    /** Upright in the light theme; on its side in the dark one (a style's fonts are the same in both). */
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
            R.string.settings_in_tune_precision_title,
            R.string.settings_in_tune_precision_relaxed,
            R.string.settings_in_tune_precision_precise,
            R.string.settings_group_look,
            R.string.settings_style_title,
            R.string.settings_theme_title,
            R.string.settings_theme_system,
            R.string.settings_theme_light,
            R.string.settings_theme_dark,
            R.string.settings_meter_look_title,
            R.string.settings_in_tune_effect_title,
            R.string.settings_in_tune_effect_calm,
            R.string.settings_in_tune_effect_bold,
            R.string.settings_display_title,
            R.string.settings_display_needle,
            R.string.settings_display_wave,
            R.string.settings_wave_look_title,
            R.string.settings_wave_in_tune_title,
            R.string.settings_wave_in_tune_breathe,
            R.string.settings_wave_in_tune_hold_still,
            R.string.settings_group_while_tuning,
            R.string.settings_keep_screen_on_title,
            R.string.settings_vibrate_in_tune_title,
            R.string.settings_show_tune_hint_title,
            R.string.settings_show_signal_bars_title,
            R.string.settings_keep_two_pane_landscape_title,
            R.string.about_title,
        ).map { context.getString(it) } +
            context.getString(R.string.settings_a4_value, 440) +
            context.getString(R.string.settings_about_link_summary, VERSION) +
            // Wallpaper colours are offered only in Contemporary.
            if (style == VisualStyle.CONTEMPORARY) listOf(context.getString(R.string.settings_dynamic_color_title)) else emptyList()
        // The page title sits in the top bar, which doesn't scroll.
        assertWhollyShown(compose.onNodeWithText(context.getString(R.string.settings_title), useUnmergedTree = true), "$case title", scroll = false)
        for (text in texts) assertWhollyShown(compose.onNodeWithText(text, useUnmergedTree = true), "$case $text")
        for ((tag, name) in cardNames()) {
            assertWhollyShown(compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true), "$case $name card")
        }
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
    fun talkBackHearsEveryCardAndChoiceByName() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            compose.waitForIdle()
            val case = "$style ${if (dark) "dark" else "light"}:"
            compose.onNodeWithContentDescription(context.getString(R.string.settings_back)).assertHasClickAction()
            val picked = mapOf(styleCardTag(style) to true, meterLookCardTag(MeterLook.COMET) to true, waveLookCardTag(WaveLook.LOOP) to true)
            for ((tag, name) in cardNames()) {
                val card = compose.onNodeWithTag(tag).performScrollTo()
                card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                if (picked[tag] == true) card.assertIsSelected() else card.assertIsNotSelected()
                // The card says its name and nothing else: the picture inside is silent.
                assertEquals("$case $tag", listOf(name), card.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
            }
            for ((id, selected) in listOf(R.string.settings_theme_system to true, R.string.settings_theme_light to false, R.string.settings_theme_dark to false)) {
                val row = compose.onNodeWithText(context.getString(id)).performScrollTo()
                row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                if (selected) row.assertIsSelected() else row.assertIsNotSelected()
            }
        }
    }

    /** Each card's test tag with the name it shows. */
    private fun cardNames(): List<Pair<String, String>> = buildList {
        styleNames.forEach { (it, id) -> add(styleCardTag(it) to context.getString(id)) }
        meterLookNames.forEach { (it, id) -> add(meterLookCardTag(it) to context.getString(id)) }
        waveLookNames.forEach { (it, id) -> add(waveLookCardTag(it) to context.getString(id)) }
    }

    private val styleNames = mapOf(
        VisualStyle.ANCIENT to DesignR.string.style_ancient,
        VisualStyle.CLASSIC to DesignR.string.style_classic,
        VisualStyle.RETRO to DesignR.string.style_retro,
        VisualStyle.CONTEMPORARY to DesignR.string.style_contemporary,
        VisualStyle.FUTURISTIC to DesignR.string.style_futuristic,
        VisualStyle.SPACE_AGE to DesignR.string.style_space_age,
    )

    private val meterLookNames = mapOf(
        MeterLook.CLASSIC to R.string.settings_meter_look_classic,
        MeterLook.LIGHT_ARC to R.string.settings_meter_look_light_arc,
        MeterLook.SPIRIT_LEVEL to R.string.settings_meter_look_spirit_level,
        MeterLook.ROLLING_SCALE to R.string.settings_meter_look_rolling_scale,
        MeterLook.COMET to R.string.settings_meter_look_comet,
        MeterLook.STUDIO_METER to R.string.settings_meter_look_studio_meter,
    )

    private val waveLookNames = mapOf(
        WaveLook.STROBE to R.string.settings_wave_look_strobe,
        WaveLook.RIBBON to R.string.settings_wave_look_ribbon,
        WaveLook.DOTS to R.string.settings_wave_look_dots,
        WaveLook.FIREFLIES to R.string.settings_wave_look_fireflies,
        WaveLook.LOOP to R.string.settings_wave_look_loop,
        WaveLook.RIPPLES to R.string.settings_wave_look_ripples,
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val VERSION = "1.2.3-test"

        /** A test scrolls to some sixty rows, in about 6 s; 20 s means it hangs. */
        private const val TEST_TIMEOUT_SECONDS = 20L

        /** A narrow phone (a Galaxy S26) upright and on its side, without the system bars. */
        private val PHONE_UPRIGHT = DpSize(360.dp, 730.dp)
        private val PHONE_ON_ITS_SIDE = DpSize(800.dp, 360.dp)
    }
}
