package io.github.klaboukes.twangle.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.bundledFontCredits
import io.github.klaboukes.twangle.core.designsystem.styleCardTag
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.settings.components.LICENCE_TEXT_TAG
import io.github.klaboukes.twangle.feature.settings.components.creditTag
import io.github.klaboukes.twangle.feature.settings.components.credits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val a4Changes = mutableListOf<Float>()
    private val notationChanges = mutableListOf<Notation>()
    private val precisionChanges = mutableListOf<InTunePrecision>()
    private val styleChanges = mutableListOf<VisualStyle>()
    private val themeChanges = mutableListOf<ThemeMode>()
    private val dynamicColorChanges = mutableListOf<Boolean>()
    private val keepScreenOnChanges = mutableListOf<Boolean>()
    private val vibrateChanges = mutableListOf<Boolean>()
    private val tuneHintChanges = mutableListOf<Boolean>()
    private val showWavesChanges = mutableListOf<Boolean>()
    private val waveLookChanges = mutableListOf<WaveLook>()
    private var backs = 0

    /**
     * Shows [settings], or as if the window were [size] ([ForcedSize] scales it down to fit) and the
     * phone's font were [fontScale] times its normal size.
     */
    private fun show(settings: AppSettings, dynamicColorAvailable: Boolean = true, size: DpSize? = null, fontScale: Float? = null) {
        compose.setContent {
            val content = @Composable {
                MaterialTheme {
                    SettingsContent(
                        settings,
                        appVersion = VERSION,
                        onBack = { backs++ },
                        onA4Change = { a4Changes += it },
                        onNotationChange = { notationChanges += it },
                        onInTunePrecisionChange = { precisionChanges += it },
                        onStyleChange = { styleChanges += it },
                        onThemeChange = { themeChanges += it },
                        onDynamicColorChange = { dynamicColorChanges += it },
                        onKeepScreenOnChange = { keepScreenOnChanges += it },
                        onVibrateInTuneChange = { vibrateChanges += it },
                        onShowTuneHintChange = { tuneHintChanges += it },
                        onShowWavesChange = { showWavesChanges += it },
                        onWaveLookChange = { waveLookChanges += it },
                        modifier = Modifier.testTag(WINDOW),
                        dynamicColorAvailable = dynamicColorAvailable,
                    )
                }
            }
            val overrides = listOfNotNull(
                size?.let { DeviceConfigurationOverride.ForcedSize(it) },
                fontScale?.let { DeviceConfigurationOverride.FontScale(it) },
            ).reduceOrNull { a, b -> a then b }
            if (overrides != null) DeviceConfigurationOverride(overrides) { content() } else content()
        }
    }

    @Test
    fun showsTheCurrentA4() {
        show(AppSettings(a4Hz = 442f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_value, 442)).assertIsDisplayed()
    }

    @Test
    fun plusAndMinusStepByOneHertz() {
        show(AppSettings(a4Hz = 442f))
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_raise)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_lower)).performClick()
        assertEquals(listOf(443f, 441f), a4Changes)
    }

    @Test
    fun resetGoesBackTo440() {
        show(AppSettings(a4Hz = 432f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_reset, 440)).performClick()
        assertEquals(listOf(440f), a4Changes)
    }

    @Test
    fun resetIsDisabledAt440() {
        show(AppSettings(a4Hz = 440f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_reset, 440)).assertIsNotEnabled()
    }

    @Test
    fun buttonsStopAtTheEndsOfTheRange() {
        show(AppSettings(a4Hz = 466f))
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_raise)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_lower)).assertIsEnabled()
    }

    @Test
    fun backArrowGoesBack() {
        show(AppSettings())
        compose.onNodeWithContentDescription(context.getString(R.string.settings_back)).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun notationShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(notation = Notation.FLATS))
        compose.onNodeWithText(context.getString(R.string.settings_notation_flats)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_notation_sharps)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_notation_solfege)).performClick()
        assertEquals(listOf(Notation.SOLFEGE), notationChanges)
    }

    @Test
    fun inTunePrecisionShowsTheChoiceAndReportsANewOne() {
        show(AppSettings())
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_precision_relaxed)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_precision_precise)).performScrollTo().assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_precision_precise)).performClick()
        assertEquals(listOf(InTunePrecision.PRECISE), precisionChanges)
    }

    @Test
    fun themeShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(theme = ThemeMode.SYSTEM))
        compose.onNodeWithText(context.getString(R.string.settings_theme_system)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_theme_dark)).performScrollTo().performClick()
        assertEquals(listOf(ThemeMode.DARK), themeChanges)
    }

    @Test
    fun switchesShowTheirStateAndToggle() {
        show(AppSettings(dynamicColor = true, keepScreenOn = false, vibrateInTune = true, showTuneHint = true, showWaves = true))
        val dynamic = compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title))
        dynamic.performScrollTo().assertIsOn().performClick()
        val keepOn = compose.onNodeWithText(context.getString(R.string.settings_keep_screen_on_title))
        keepOn.performScrollTo().assertIsOff().performClick()
        val vibrate = compose.onNodeWithText(context.getString(R.string.settings_vibrate_in_tune_title))
        vibrate.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), dynamicColorChanges)
        assertEquals(listOf(true), keepScreenOnChanges)
        assertEquals(listOf(false), vibrateChanges)
        val tuneHint = compose.onNodeWithText(context.getString(R.string.settings_show_tune_hint_title))
        tuneHint.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), tuneHintChanges)
        val waves = compose.onNodeWithText(context.getString(R.string.settings_show_waves_title))
        waves.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), showWavesChanges)
    }

    @Test
    fun waveLooksShowTheChoiceAndReportANewOne() {
        show(AppSettings(showWaves = true, waveLook = WaveLook.LOOP))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_loop)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_ribbon)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_fireflies)).performScrollTo().performClick()
        assertEquals(listOf(WaveLook.FIREFLIES), waveLookChanges)
    }

    @Test
    fun waveLooksAreHiddenWhileTheWaveViewIsOff() {
        show(AppSettings(showWaves = false))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_ribbon)).assertDoesNotExist()
    }

    @Test
    fun wallpaperColoursAreHiddenWithoutDynamicColor() {
        show(AppSettings(), dynamicColorAvailable = false)
        compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title)).assertDoesNotExist()
    }

    @Test
    fun wallpaperColoursOnlyApplyToTheContemporaryStyle() {
        show(AppSettings(style = VisualStyle.RETRO))
        compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title)).assertDoesNotExist()
    }

    @Test
    fun styleCardsShowTheChoiceAndReportANewOne() {
        show(AppSettings(style = VisualStyle.CLASSIC))
        // The cards sit in the Look group, below the fold: bring the row on screen first.
        compose.onNodeWithText(context.getString(R.string.settings_theme_title)).performScrollTo()
        compose.onNodeWithTag(styleCardTag(VisualStyle.CLASSIC)).assertIsSelected()
        compose.onNodeWithTag(styleCardTag(VisualStyle.MIDDLE_AGES)).assertIsNotSelected().performClick()
        assertEquals(listOf(VisualStyle.MIDDLE_AGES), styleChanges)
    }

    @Test
    fun settingsComeInThreeHeadedGroupsThenAbout() {
        show(AppSettings(showWaves = true))
        val headings = listOf(
            R.string.settings_group_tuning,
            R.string.settings_group_look,
            R.string.settings_group_while_tuning,
            R.string.settings_group_about,
        )
        headings.forEach { compose.onNodeWithText(context.getString(it)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)) }

        // Top to bottom: each heading, then the settings of its group.
        // Unclipped positions: most of these rows are below the fold.
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
        val order = listOf(
            top(context.getString(R.string.settings_group_tuning)),
            top(context.getString(R.string.settings_a4_title)),
            top(context.getString(R.string.settings_notation_title)),
            top(context.getString(R.string.settings_in_tune_precision_title)),
            top(context.getString(R.string.settings_group_look)),
            compose.onNodeWithTag(styleCardTag(VisualStyle.CONTEMPORARY)).fetchSemanticsNode().positionInRoot.y,
            top(context.getString(R.string.settings_theme_title)),
            top(context.getString(R.string.settings_dynamic_color_title)),
            top(context.getString(R.string.settings_show_waves_title)),
            top(context.getString(R.string.settings_wave_look_title)),
            top(context.getString(R.string.settings_group_while_tuning)),
            top(context.getString(R.string.settings_keep_screen_on_title)),
            top(context.getString(R.string.settings_vibrate_in_tune_title)),
            top(context.getString(R.string.settings_show_tune_hint_title)),
            top(context.getString(R.string.settings_group_about)),
            top(context.getString(R.string.settings_about_version, VERSION)),
            top(context.getString(R.string.settings_about_free_title)),
            top(context.getString(R.string.settings_about_privacy_title)),
            top(context.getString(R.string.settings_about_credits_title)),
        )
        assertEquals("tops $order", order.sorted(), order)
    }

    @Test
    fun onAWideScreenTheSettingsStayInAReadableColumn() {
        show(AppSettings(), size = DpSize(1200.dp, 800.dp))
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val row = compose.onNodeWithText(context.getString(R.string.settings_theme_dark)).performScrollTo().getBoundsInRoot()
        // 600 of the 1200 dp, in the middle.
        val share = (row.right - row.left) / (window.right - window.left)
        assertTrue("row takes $share of the width", share in 0.4f..0.51f)
        val leftGap = row.left - window.left
        val rightGap = window.right - row.right
        assertTrue("gaps $leftGap and $rightGap", abs((leftGap - rightGap).value) < 2f)
    }

    @Test
    fun aboutShowsTheVersionThePromiseAndThePrivacyStatement() {
        show(AppSettings())
        listOf(
            context.getString(R.string.settings_about_version, VERSION),
            context.getString(R.string.settings_about_free_title),
            context.getString(R.string.settings_about_free_summary),
            context.getString(R.string.settings_about_privacy_title),
            context.getString(R.string.settings_about_privacy_summary),
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun everyFontAndLibraryIsCredited() {
        show(AppSettings())
        assertEquals(bundledFontCredits.size + 2, credits.size)
        bundledFontCredits.forEach { font ->
            compose.onNodeWithTag(creditTag(font.name)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(font.name).assertIsDisplayed()
            compose.onNodeWithText("${font.designer} · SIL Open Font License 1.1").assertIsDisplayed()
        }
        compose.onNodeWithTag(creditTag("Jetpack Compose & AndroidX")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(creditTag("Kotlin & kotlinx.coroutines")).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Google · Apache License 2.0").assertCountEquals(1)
    }

    @Test
    fun creditsAreButtonsThatSayWhatTheyDo() {
        show(AppSettings())
        compose.onNodeWithTag(creditTag("Orbitron")).performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click label") {
                    it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == context.getString(R.string.settings_about_licence_open)
                },
            )
    }

    @Test
    fun tappingAFontShowsItsLicenceFromTheApp() {
        show(AppSettings())
        compose.onNodeWithTag(creditTag("Orbitron")).performScrollTo().performClick()
        // The licence is read from the app's own files, so it is there without internet.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Orbitron Project Authors", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("licensed under the SIL Open Font License, Version 1.1", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_about_licence_close)).assertIsDisplayed().performClick()
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingALibraryShowsTheApacheLicence() {
        show(AppSettings())
        compose.onNodeWithTag(creditTag("Kotlin & kotlinx.coroutines")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("TERMS AND CONDITIONS FOR USE", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Reflowed: a paragraph's lines are joined, so it wraps to the screen.
        compose.onNodeWithText("Apache License Version 2.0, January 2004", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_about_licence_close)).performClick()
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assertDoesNotExist()
    }

    @Test
    fun aboutIsReachableAtTwiceTheFontSizeOnAPhoneOnItsSide() {
        show(AppSettings(), size = DpSize(800.dp, 360.dp), fontScale = 2f)
        listOf(
            context.getString(R.string.settings_group_about),
            context.getString(R.string.settings_about_version, VERSION),
            context.getString(R.string.settings_about_free_summary),
            context.getString(R.string.settings_about_privacy_summary),
            context.getString(R.string.settings_about_credits_summary),
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        credits.forEach {
            val row = compose.onNodeWithTag(creditTag(it.name)).performScrollTo().assertIsDisplayed().getBoundsInRoot()
            assertTrue("${it.name} fits across", row.left >= window.left && row.right <= window.right)
        }
    }

    @Test
    fun theLicenceCanBeClosedAtTwiceTheFontSize() {
        show(AppSettings(), fontScale = 2f)
        compose.onNodeWithTag(creditTag("Michroma")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Michroma Project Authors", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // A long licence scrolls inside the dialog; the button stays on screen.
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        compose.onNodeWithText(context.getString(R.string.settings_about_licence_close)).assertIsDisplayed().performClick()
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assertDoesNotExist()
    }

    private companion object {
        const val WINDOW = "window"
        const val VERSION = "1.2.3-test"
    }
}
