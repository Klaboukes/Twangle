package io.github.klaboukes.twangle.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
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
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.bundledFontCredits
import io.github.klaboukes.twangle.core.designsystem.styleCardTag
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.settings.components.LICENCE_TEXT_TAG
import io.github.klaboukes.twangle.feature.settings.components.creditTag
import io.github.klaboukes.twangle.feature.settings.components.meterLookCardTag
import io.github.klaboukes.twangle.feature.settings.components.credits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

    private val compose = createComposeRule()

    // Outermost, so a test that hangs (scrolling, waiting for a licence) fails in seconds.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

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
    private val signalBarsChanges = mutableListOf<Boolean>()
    private val displayChanges = mutableListOf<TunerDisplay>()
    private val meterLookChanges = mutableListOf<MeterLook>()
    private val waveLookChanges = mutableListOf<WaveLook>()
    private val breatheChanges = mutableListOf<Boolean>()
    private val twoPaneChanges = mutableListOf<Boolean>()
    private var backs = 0
    private var aboutOpened = 0

    /**
     * Shows [settings] (or, with [about], the About page), as if the window were [size] ([ForcedSize]
     * scales it down to fit), the phone's font were [fontScale] times its normal size, or the app were
     * in another language ([locale], e.g. "nl").
     */
    private fun show(
        settings: AppSettings,
        dynamicColorAvailable: Boolean = true,
        size: DpSize? = null,
        fontScale: Float? = null,
        locale: String? = null,
        about: Boolean = false,
        style: VisualStyle? = null,
    ) {
        compose.setContent {
            val content = @Composable {
                Themed(style) {
                    if (about) {
                        AboutScreen(appVersion = VERSION, onBack = { backs++ }, modifier = Modifier.testTag(WINDOW))
                    } else SettingsContent(
                        settings,
                        appVersion = VERSION,
                        onBack = { backs++ },
                        onOpenAbout = { aboutOpened++ },
                        onA4Change = { a4Changes += it },
                        onNotationChange = { notationChanges += it },
                        onInTunePrecisionChange = { precisionChanges += it },
                        onStyleChange = { styleChanges += it },
                        onThemeChange = { themeChanges += it },
                        onDynamicColorChange = { dynamicColorChanges += it },
                        onKeepScreenOnChange = { keepScreenOnChanges += it },
                        onVibrateInTuneChange = { vibrateChanges += it },
                        onShowTuneHintChange = { tuneHintChanges += it },
                        onShowSignalBarsChange = { signalBarsChanges += it },
                        onDisplayChange = { displayChanges += it },
                        onMeterLookChange = { meterLookChanges += it },
                        onWaveLookChange = { waveLookChanges += it },
                        onWaveBreathesInTuneChange = { breatheChanges += it },
                        onKeepTwoPaneLandscapeChange = { twoPaneChanges += it },
                        modifier = Modifier.testTag(WINDOW),
                        dynamicColorAvailable = dynamicColorAvailable,
                    )
                }
            }
            val overrides = listOfNotNull(
                size?.let { DeviceConfigurationOverride.ForcedSize(it) },
                fontScale?.let { DeviceConfigurationOverride.FontScale(it) },
                locale?.let { DeviceConfigurationOverride.Locales(LocaleList(it)) },
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
    fun theLastRowOpensAboutAndShowsTheVersion() {
        show(AppSettings())
        compose.onNodeWithText(context.getString(R.string.settings_about_link_summary, VERSION)).performScrollTo().assertIsDisplayed()
        // The long About texts live on their own page, not in settings.
        compose.onNodeWithText(context.getString(R.string.settings_about_privacy_summary)).assertDoesNotExist()
        compose.onNode(hasText(context.getString(R.string.about_title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button), useUnmergedTree = false)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, aboutOpened)
    }

    @Test
    fun aboutBackArrowGoesBack() {
        show(AppSettings(), about = true)
        compose.onNodeWithText(context.getString(R.string.about_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_back)).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun talkBackSaysTheNotationChoicesSignsAsWords() {
        show(AppSettings())
        compose.onNode(hasContentDescription("Sharps: C, C sharp, D …")).assertExists()
        compose.onNode(hasContentDescription("Flats: C, D flat, D …")).assertExists()
        compose.onNode(hasContentDescription("Do Re Mi: Do, Do sharp, Re …")).assertExists()
    }

    @Test
    fun dutchTalkBackSaysTheNotationChoicesTheDutchWay() {
        show(AppSettings(notation = Notation.FLATS), locale = "nl")
        compose.onNode(hasContentDescription("Kruisen: c, cis, d …")).assertExists()
        compose.onNode(hasContentDescription("Mollen: c, des, d …")).assertExists().assertIsSelected()
        compose.onNode(hasContentDescription("Do Re Mi: Do, Do kruis, Re …")).assertExists()
        // What's on screen doesn't change.
        compose.onNodeWithText("Kruisen: C, C♯, D …").assertIsDisplayed()
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
        show(AppSettings(dynamicColor = true, keepScreenOn = false, vibrateInTune = true, showTuneHint = true))
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
    }

    @Test
    fun signalBarsSwitchIsOffByDefaultAndToggles() {
        show(AppSettings())
        val bars = compose.onNodeWithText(context.getString(R.string.settings_show_signal_bars_title))
        bars.performScrollTo().assertIsOff().performClick()
        assertEquals(listOf(true), signalBarsChanges)
    }

    @Test
    fun twoPaneLandscapeSwitchIsOffByDefaultAndToggles() {
        show(AppSettings())
        val twoPane = compose.onNodeWithText(context.getString(R.string.settings_keep_two_pane_landscape_title))
        twoPane.performScrollTo().assertIsOff().performClick()
        assertEquals(listOf(true), twoPaneChanges)
    }

    @Test
    fun twoPaneLandscapeSwitchShowsItIsOn() {
        show(AppSettings(keepTwoPaneLandscape = true))
        val twoPane = compose.onNodeWithText(context.getString(R.string.settings_keep_two_pane_landscape_title))
        twoPane.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), twoPaneChanges)
    }

    @Test
    fun displayPickerShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(display = TunerDisplay.WAVE))
        compose.onNodeWithText(context.getString(R.string.settings_display_wave)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_display_needle)).assertIsNotSelected().performClick()
        assertEquals(listOf(TunerDisplay.NEEDLE), displayChanges)
    }

    /** Six meter cards, in the owner's order, with the needle display as well as the wave. */
    @Test
    fun theMeterPickerShowsSixCardsWithTheNeedle() = assertSixMeterCards(TunerDisplay.NEEDLE)

    @Test
    fun theMeterPickerShowsSixCardsWithTheWave() = assertSixMeterCards(TunerDisplay.WAVE)

    private fun assertSixMeterCards(display: TunerDisplay) {
        show(AppSettings(display = display))
        compose.onNodeWithText(context.getString(R.string.settings_meter_look_title)).performScrollTo().assertIsDisplayed()
        val cards = SemanticsMatcher("a meter look card") {
            it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("meter_look_card_") == true
        }
        compose.onAllNodes(cards).assertCountEquals(6)
        val places = MeterLook.entries.map { compose.onNodeWithTag(meterLookCardTag(it)).fetchSemanticsNode().positionInRoot }
        // Read in rows: each card is beside the one before it, to the right, or in the row below.
        places.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", b.y > a.y || (b.y == a.y && b.x > a.x)) }
    }

    @Test
    fun meterLooksShowTheChoiceAndReportEachOne() {
        show(AppSettings(meterLook = MeterLook.SPIRIT_LEVEL))
        compose.onNodeWithTag(meterLookCardTag(MeterLook.SPIRIT_LEVEL)).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(meterLookCardTag(MeterLook.CLASSIC)).assertIsNotSelected()
        for (look in MeterLook.entries) compose.onNodeWithTag(meterLookCardTag(look)).performScrollTo().performClick()
        assertEquals(MeterLook.entries, meterLookChanges)
    }

    /**
     * TalkBack hears every meter card as a radio button with its name, and which one is picked:
     * one group, exactly one card selected. In English and in Dutch.
     */
    @Test
    fun talkBackReadsEveryMeterLookAsARadioButton() = assertMeterLooksSpoken(locale = null)

    @Test
    fun dutchTalkBackReadsEveryMeterLookAsARadioButton() = assertMeterLooksSpoken(locale = "nl")

    private fun assertMeterLooksSpoken(locale: String?) {
        show(AppSettings(meterLook = MeterLook.COMET), locale = locale)
        val res = resourcesIn(locale)
        for ((look, id) in meterLookNames) {
            val name = res.getString(id)
            val card = compose.onNodeWithTag(meterLookCardTag(look)).performScrollTo()
            card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (look == MeterLook.COMET) card.assertIsSelected() else card.assertIsNotSelected()
            // Its name, said once: the moving meter inside says nothing.
            assertEquals("$look", listOf(name), card.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
            card.assert(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }
    }

    /**
     * At 2× font every meter look's name still fits its card on one line (two cards to a row then,
     * and the name gets smaller when needed), in English and Dutch ("Studiometer"), on a phone and
     * on a phone on its side.
     */
    @Test
    fun meterLookNamesFitAtTwiceTheFontSize() = assertMeterLookNamesFit(fontScale = 2f)

    @Test
    fun meterLookNamesFitInDutchAtTwiceTheFontSize() = assertMeterLookNamesFit(fontScale = 2f, locale = "nl")

    /** Classic sets its names in a wide serif face. */
    @Test
    fun meterLookNamesFitAtTwiceTheFontSizeInTheClassicStyle() =
        assertMeterLookNamesFit(fontScale = 2f, style = VisualStyle.CLASSIC)

    @Test
    fun meterLookNamesFitInDutchAtTwiceTheFontSizeInTheClassicStyle() =
        assertMeterLookNamesFit(fontScale = 2f, locale = "nl", style = VisualStyle.CLASSIC)

    @Test
    fun meterLookNamesFitAtTwiceTheFontSizeOnAPhoneOnItsSide() =
        assertMeterLookNamesFit(size = PHONE_ON_ITS_SIDE, fontScale = 2f)

    /** Just under the size where the grid goes to two cards a row: three a row, the tightest fit. */
    @Test
    fun meterLookNamesFitInALargerFontSizeInTheClassicStyle() =
        assertMeterLookNamesFit(fontScale = 1.29f, style = VisualStyle.CLASSIC)

    private fun assertMeterLookNamesFit(
        size: DpSize? = null,
        fontScale: Float? = null,
        locale: String? = null,
        style: VisualStyle? = null,
    ) {
        show(AppSettings(), size = size, fontScale = fontScale, locale = locale, style = style)
        val res = resourcesIn(locale)
        for ((look, id) in meterLookNames) {
            val name = res.getString(id)
            val label = compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(meterLookCardTag(look))), useUnmergedTree = true)
            assertWhollyShown(label, name)
            val layouts = mutableListOf<TextLayoutResult>()
            label.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals("\"$name\" lines", 1, layouts.single().lineCount)
        }
    }

    private val meterLookNames = mapOf(
        MeterLook.CLASSIC to R.string.settings_meter_look_classic,
        MeterLook.LIGHT_ARC to R.string.settings_meter_look_light_arc,
        MeterLook.SPIRIT_LEVEL to R.string.settings_meter_look_spirit_level,
        MeterLook.ROLLING_SCALE to R.string.settings_meter_look_rolling_scale,
        MeterLook.COMET to R.string.settings_meter_look_comet,
        MeterLook.STUDIO_METER to R.string.settings_meter_look_studio_meter,
    )

    @Test
    fun waveLooksShowTheChoiceAndReportANewOne() {
        show(AppSettings(display = TunerDisplay.WAVE, waveLook = WaveLook.LOOP))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_loop)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_ribbon)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_fireflies)).performScrollTo().performClick()
        assertEquals(listOf(WaveLook.FIREFLIES), waveLookChanges)
    }

    /**
     * TalkBack hears every look card as a radio button with its name, and which one is picked:
     * one group, exactly one card selected. In English and in Dutch.
     */
    @Test
    fun talkBackReadsEveryWaveLookAsARadioButton() = assertWaveLooksSpoken(locale = null)

    @Test
    fun dutchTalkBackReadsEveryWaveLookAsARadioButton() = assertWaveLooksSpoken(locale = "nl")

    private fun assertWaveLooksSpoken(locale: String?) {
        show(AppSettings(display = TunerDisplay.WAVE, waveLook = WaveLook.DOTS), locale = locale)
        val res = resourcesIn(locale)
        val names = waveLookNames.mapValues { res.getString(it.value) }
        for ((look, name) in names) {
            val card = compose.onNodeWithText(name).performScrollTo()
            card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (look == WaveLook.DOTS) card.assertIsSelected() else card.assertIsNotSelected()
            // Its name, said once: nothing else inside the card for TalkBack to read.
            assertEquals("$look", listOf(name), card.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
            card.assert(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }
    }

    /**
     * At 2× font every look's name still fits its card on one line (two cards to a row then, and
     * the name gets smaller when needed) instead of breaking in the middle of a word, in English
     * and Dutch ("Vuurvliegjes"), on a phone and on a phone on its side.
     */
    @Test
    fun waveLookNamesFitAtTwiceTheFontSize() = assertWaveLookNamesFit(fontScale = 2f)

    @Test
    fun waveLookNamesFitInDutchAtTwiceTheFontSize() = assertWaveLookNamesFit(fontScale = 2f, locale = "nl")

    /** Classic sets its names in a wide serif face. */
    @Test
    fun waveLookNamesFitInDutchAtTwiceTheFontSizeInTheClassicStyle() =
        assertWaveLookNamesFit(fontScale = 2f, locale = "nl", style = VisualStyle.CLASSIC)

    @Test
    fun waveLookNamesFitInDutchAtTwiceTheFontSizeOnAPhoneOnItsSide() =
        assertWaveLookNamesFit(size = PHONE_ON_ITS_SIDE, fontScale = 2f, locale = "nl")

    @Test
    fun waveLookNamesFitInDutchAtALargerFontSize() =
        assertWaveLookNamesFit(fontScale = 1.3f, locale = "nl", style = VisualStyle.CLASSIC)

    private fun assertWaveLookNamesFit(
        size: DpSize? = null,
        fontScale: Float? = null,
        locale: String? = null,
        style: VisualStyle? = null,
    ) {
        show(AppSettings(display = TunerDisplay.WAVE), size = size, fontScale = fontScale, locale = locale, style = style)
        val res = resourcesIn(locale)
        for (id in waveLookNames.values) {
            val name = res.getString(id)
            val label = compose.onNodeWithText(name, useUnmergedTree = true)
            assertWhollyShown(label, name)
            val layouts = mutableListOf<TextLayoutResult>()
            label.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals("\"$name\" lines", 1, layouts.single().lineCount)
        }
    }

    private val waveLookNames = mapOf(
        WaveLook.STROBE to R.string.settings_wave_look_strobe,
        WaveLook.RIBBON to R.string.settings_wave_look_ribbon,
        WaveLook.DOTS to R.string.settings_wave_look_dots,
        WaveLook.FIREFLIES to R.string.settings_wave_look_fireflies,
        WaveLook.LOOP to R.string.settings_wave_look_loop,
        WaveLook.RIPPLES to R.string.settings_wave_look_ripples,
    )

    @Test
    fun waveInTuneShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(display = TunerDisplay.WAVE, waveBreathesInTune = true))
        compose.onNodeWithText(context.getString(R.string.settings_wave_in_tune_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_wave_in_tune_breathe)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_in_tune_hold_still)).performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf(false), breatheChanges)
    }

    @Test
    fun waveLooksAreHiddenWithTheNeedle() {
        show(AppSettings(display = TunerDisplay.NEEDLE))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_wave_in_tune_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_ribbon)).assertDoesNotExist()
    }

    @Test
    fun theStrobeIsOneOfTheWaveLooks() {
        show(AppSettings(display = TunerDisplay.WAVE, waveLook = WaveLook.STROBE))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_strobe)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_ribbon)).assertIsNotSelected().performClick()
        assertEquals(listOf(WaveLook.RIBBON), waveLookChanges)
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
    fun settingsComeInThreeHeadedGroupsThenTheAboutLink() {
        show(AppSettings(display = TunerDisplay.WAVE))
        val headings = listOf(
            R.string.settings_group_tuning,
            R.string.settings_group_look,
            R.string.settings_group_while_tuning,
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
            top(context.getString(R.string.settings_meter_look_title)),
            top(context.getString(R.string.settings_display_title)),
            top(context.getString(R.string.settings_wave_look_title)),
            top(context.getString(R.string.settings_group_while_tuning)),
            top(context.getString(R.string.settings_keep_screen_on_title)),
            top(context.getString(R.string.settings_vibrate_in_tune_title)),
            top(context.getString(R.string.settings_show_tune_hint_title)),
            top(context.getString(R.string.settings_show_signal_bars_title)),
            top(context.getString(R.string.about_title)),
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
        show(AppSettings(), about = true)
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
        show(AppSettings(), about = true)
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
        show(AppSettings(), about = true)
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
        show(AppSettings(), about = true)
        compose.onNodeWithTag(creditTag("Orbitron")).performScrollTo().performClick()
        // The licence is read from the app's own files, so it is there without internet.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Orbitron Project Authors", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("licensed under the SIL Open Font License, Version 1.1", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_about_licence_close)).assertIsDisplayed().performClick()
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingALibraryShowsTheApacheLicence() {
        show(AppSettings(), about = true)
        compose.onNodeWithTag(creditTag("Kotlin & kotlinx.coroutines")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("TERMS AND CONDITIONS FOR USE", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Reflowed: a paragraph's lines are joined, so it wraps to the screen.
        compose.onNodeWithText("Apache License Version 2.0, January 2004", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_about_licence_close)).performClick()
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assertDoesNotExist()
    }

    @Test
    fun aboutIsReachableAtTwiceTheFontSizeOnAPhoneOnItsSide() {
        show(AppSettings(), size = PHONE_ON_ITS_SIDE, fontScale = 2f, about = true)
        compose.onNodeWithText(context.getString(R.string.about_title)).assertIsDisplayed()
        listOf(
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
        show(AppSettings(), fontScale = 2f, about = true)
        compose.onNodeWithTag(creditTag("Michroma")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Michroma Project Authors", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // A long licence scrolls inside the dialog; the button stays on screen.
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        compose.onNodeWithText(context.getString(R.string.settings_about_licence_close)).assertIsDisplayed().performClick()
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assertDoesNotExist()
    }

    @Test
    fun newRowsFitAtTwiceTheFontSize() = assertNewRowsFit(fontScale = 2f)

    @Test
    fun newRowsFitOnAPhoneOnItsSide() = assertNewRowsFit(size = PHONE_ON_ITS_SIDE)

    @Test
    fun newRowsFitAtTwiceTheFontSizeOnAPhoneOnItsSide() = assertNewRowsFit(size = PHONE_ON_ITS_SIDE, fontScale = 2f)

    @Test
    fun newRowsFitInDutchAtTwiceTheFontSizeOnAPhoneOnItsSide() =
        assertNewRowsFit(size = PHONE_ON_ITS_SIDE, fontScale = 2f, locale = "nl")

    @Test
    fun aboutFitsAtTwiceTheFontSize() = assertAboutFits(fontScale = 2f)

    @Test
    fun aboutFitsOnAPhoneOnItsSide() = assertAboutFits(size = PHONE_ON_ITS_SIDE)

    @Test
    fun aboutFitsAtTwiceTheFontSizeOnAPhoneOnItsSide() = assertAboutFits(size = PHONE_ON_ITS_SIDE, fontScale = 2f)

    @Test
    fun aboutFitsInDutchAtTwiceTheFontSizeOnAPhoneOnItsSide() =
        assertAboutFits(size = PHONE_ON_ITS_SIDE, fontScale = 2f, locale = "nl")

    @Test
    fun theLicenceShowsItsNameAndCloseButtonAtTwiceTheFontSize() {
        show(AppSettings(), fontScale = 2f, about = true)
        compose.onNodeWithTag(creditTag("Jetpack Compose & AndroidX")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("TERMS AND CONDITIONS FOR USE", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // The dialog is a window of its own: its name, the licence's name and the button are all
        // wholly on screen, and only the licence text scrolls.
        listOf("Jetpack Compose & AndroidX", "Apache License 2.0", context.getString(R.string.settings_about_licence_close))
            .forEach { assertWhollyShown(compose.onNode(hasText(it) and hasAnyAncestor(isDialog()), useUnmergedTree = true), it, scroll = false) }
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    }

    /** Resources in [locale], or the test's own when it is null. */
    /** [MaterialTheme], or with a [style] that style's lettering and colours (light). */
    @Composable
    private fun Themed(style: VisualStyle?, content: @Composable () -> Unit) {
        if (style == null) MaterialTheme { content() } else TwangleTheme(style = style, darkTheme = false, animate = false) { content() }
    }

    private fun resourcesIn(locale: String?): android.content.res.Resources {
        if (locale == null) return context.resources
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(locale))
        return context.createConfigurationContext(config).resources
    }

    /**
     * The rows phase 8 added to settings: the three group headings, the note-name choices and the
     * About link. Each can be scrolled to and is then wholly on screen, inside the window, and its
     * text isn't cut short.
     */
    private fun assertNewRowsFit(size: DpSize? = null, fontScale: Float? = null, locale: String? = null) {
        show(AppSettings(), size = size, fontScale = fontScale, locale = locale)
        val res = resourcesIn(locale)
        val texts = listOf(
            R.string.settings_group_tuning,
            R.string.settings_notation_title,
            R.string.settings_notation_sharps,
            R.string.settings_notation_flats,
            R.string.settings_notation_solfege,
            R.string.settings_group_look,
            R.string.settings_group_while_tuning,
            R.string.about_title,
        ).map { res.getString(it) } + res.getString(R.string.settings_about_link_summary, VERSION)
        texts.forEach { assertWhollyShown(compose.onNodeWithText(it, useUnmergedTree = true), it) }
    }

    /**
     * The About page: its titles, the version and every credit can be scrolled to and are then wholly
     * on screen; the long texts wrap inside the window; every credit is a big enough touch target.
     */
    private fun assertAboutFits(size: DpSize? = null, fontScale: Float? = null, locale: String? = null) {
        show(AppSettings(), size = size, fontScale = fontScale, locale = locale, about = true)
        val res = resourcesIn(locale)
        // The page title sits in the top bar, which doesn't scroll.
        assertWhollyShown(compose.onNodeWithText(res.getString(R.string.about_title), useUnmergedTree = true), "title", scroll = false)
        val texts = listOf(
            R.string.settings_about_free_title,
            R.string.settings_about_privacy_title,
            R.string.settings_about_credits_title,
        ).map { res.getString(it) } + res.getString(R.string.settings_about_version, VERSION) + credits.map { it.name }
        texts.forEach { assertWhollyShown(compose.onNodeWithText(it, useUnmergedTree = true), it) }
        // The long About texts are taller than a phone on its side at 2×, but they wrap inside the window.
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        listOf(R.string.settings_about_free_summary, R.string.settings_about_privacy_summary, R.string.settings_about_credits_summary)
            .map { res.getString(it) }
            .forEach {
                val bounds = compose.onNodeWithText(it).performScrollTo().assertIsDisplayed().getBoundsInRoot()
                assertTrue("\"$it\" fits across", bounds.left >= window.left && bounds.right <= window.right)
            }
        // Every credit row is a touch target of at least 48 dp.
        credits.forEach {
            val row = compose.onNodeWithTag(creditTag(it.name)).performScrollTo().getBoundsInRoot()
            assertTrue("${it.name} row is ${row.bottom - row.top} high", row.bottom - row.top >= 47.5.dp)
        }
    }

    /**
     * Scrolls [node] into view (unless [scroll] is false) and checks all of it shows: its visible
     * part is as big as the node itself, and its text has no lines or letters cut off.
     */
    private fun assertWhollyShown(node: SemanticsNodeInteraction, what: String, scroll: Boolean = true) {
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        val semantics = node.fetchSemanticsNode()
        val visible = semantics.boundsInRoot
        assertEquals("\"$what\" visible width", semantics.size.width.toFloat(), visible.width, 1f)
        assertEquals("\"$what\" visible height", semantics.size.height.toFloat(), visible.height, 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        semantics.config.getOrElseNullable(SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layouts)
        // (TextLayoutResult.didOverflowWidth can't be used: it compares with the widest the text
        // may be, not what it needs.) Every line ends inside the text's box, no line is cut off at
        // the bottom, and none ends in an ellipsis.
        layouts.forEach { layout ->
            val lines = 0 until layout.lineCount
            assertTrue("\"$what\" is cut off at the bottom", !layout.didOverflowHeight)
            lines.forEach { line ->
                assertTrue("\"$what\" line $line runs out of its box", layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("\"$what\" line $line is ellipsized", !layout.isLineEllipsized(line))
            }
        }
    }

    private companion object {
        const val WINDOW = "window"
        const val VERSION = "1.2.3-test"
        const val TEST_TIMEOUT_SECONDS = 10L

        /** A phone on its side, as the S26 (780 × 360 dp) with a little to spare. */
        val PHONE_ON_ITS_SIDE = DpSize(800.dp, 360.dp)
    }
}
