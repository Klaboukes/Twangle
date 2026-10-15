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
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.bundledFontCredits
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.core.testing.Thorough
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.settings.components.LANGUAGE_SETTING_TAG
import io.github.klaboukes.twangle.feature.settings.components.LICENCE_TEXT_TAG
import io.github.klaboukes.twangle.feature.settings.components.languageOptionTag
import io.github.klaboukes.twangle.feature.settings.components.creditTag
import io.github.klaboukes.twangle.feature.settings.components.credits
import io.github.klaboukes.twangle.feature.settings.components.meterLookCardTag
import io.github.klaboukes.twangle.feature.settings.components.styleCardTag
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

    private val compose = createSharedActivityComposeRule()

    // Outermost, so a test that hangs (scrolling, waiting for a licence) fails in seconds.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val a4Changes = mutableListOf<Float>()
    private val notationChanges = mutableListOf<Notation?>()
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
    private val effectChanges = mutableListOf<InTuneEffect>()
    private val waveLookChanges = mutableListOf<WaveLook>()
    private val twoPaneChanges = mutableListOf<Boolean>()
    private var backs = 0
    private var aboutOpened = 0
    private var toursAsked = 0
    private val languageChanges = mutableListOf<String?>()

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
        languages: List<String> = emptyList(),
        appLanguage: String? = null,
    ) {
        compose.setContent {
            val content = @Composable {
                Themed(style) {
                    if (about) {
                        AboutScreen(appVersion = VERSION, onBack = { backs++ }, modifier = Modifier.testTag(WINDOW))
                    } else SettingsContent(
                        settings,
                        appVersion = VERSION,
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
                        onInTuneEffectChange = { effectChanges += it },
                        onWaveLookChange = { waveLookChanges += it },
                        onKeepTwoPaneLandscapeChange = { twoPaneChanges += it },
                        modifier = Modifier.testTag(WINDOW),
                        dynamicColorAvailable = dynamicColorAvailable,
                        onShowTour = { toursAsked++ },
                        languages = languages,
                        appLanguage = appLanguage,
                        onAppLanguageChange = { languageChanges += it },
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

    /**
     * The Language row shows the app's language in its own name; its dialog lists the phone's
     * language first and then each language in its own name, marked with its language for TalkBack.
     */
    @Test
    fun languageDialogListsThePhonesLanguageFirstThenEachInItsOwnName() {
        show(AppSettings(), languages = listOf("de", "pl", "zh-CN"), appLanguage = "pl")
        compose.onNodeWithTag(LANGUAGE_SETTING_TAG).performScrollTo().assert(hasText("Polski")).performClick()
        val options = listOf(null, "de", "pl", "zh-CN")
        val tops = options.map { tag ->
            val option = compose.onNodeWithTag(languageOptionTag(tag)).assert(hasAnyAncestor(isDialog()))
            option.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (tag == "pl") option.assertIsSelected() else option.assertIsNotSelected()
            val text = option.fetchSemanticsNode().config[SemanticsProperties.Text].single()
            if (tag == null) {
                assertEquals(context.getString(R.string.settings_language_phone), text.text)
            } else {
                assertEquals(AppLanguages.nameOf(tag), text.text)
                assertTrue(tag, text.spanStyles.any { it.item.localeList == LocaleList(tag) })
            }
            option.getUnclippedBoundsInRoot().top
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun pickingALanguageReportsItAndClosesTheDialog() {
        show(AppSettings(), languages = listOf("de", "pl"))
        compose.onNodeWithTag(LANGUAGE_SETTING_TAG).performScrollTo()
            .assert(hasText(context.getString(R.string.settings_language_phone))).performClick()
        compose.onNodeWithTag(languageOptionTag(null)).assertIsSelected()
        compose.onNodeWithTag(languageOptionTag("de")).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        // Cancel and the language already picked change nothing.
        compose.onNodeWithTag(LANGUAGE_SETTING_TAG).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_language_cancel)).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag(LANGUAGE_SETTING_TAG).performClick()
        compose.onNodeWithTag(languageOptionTag(null)).performClick()
        assertEquals(listOf("de"), languageChanges)
    }

    /** Without a language list (previews, the watch) there is no Language row. */
    @Test
    fun noLanguageRowWithoutLanguages() {
        show(AppSettings())
        compose.onNodeWithTag(LANGUAGE_SETTING_TAG).assertDoesNotExist()
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

    /** Settings is one of the app's tabs: the navigation bar and Back lead away, not an arrow. */
    @Test
    fun settingsHasNoBackArrow() {
        show(AppSettings())
        compose.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_back)).assertDoesNotExist()
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

    /** "Show me around", above About, starts the first-start tour again. */
    @Test
    fun showMeAroundStartsTheTourAgain() {
        show(AppSettings(tourSeen = true))
        compose.onNodeWithTag(SHOW_TOUR_TAG)
            .assert(hasText(context.getString(R.string.settings_show_tour_title)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, toursAsked)
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
        compose.onNode(hasContentDescription("German: C, C sharp, D … H")).assertExists()
        // Never picked: Automatic, which in English is sharps.
        compose.onNode(hasContentDescription("Automatic: C, C sharp, D … B")).assertExists().assertIsSelected()
    }

    @Test
    fun germanAutomaticIsTheGermanNotationAndTalkBackSaysItTheGermanWay() {
        show(AppSettings(), locale = "de")
        compose.onNode(hasContentDescription("Automatisch: C, Cis, D … H")).assertExists().assertIsSelected()
        compose.onNode(hasContentDescription("Deutsch: C, Cis, D … H")).assertExists().assertIsNotSelected()
        compose.onNodeWithText("Automatisch: C, C♯, D … H").assertIsDisplayed()
    }

    @Test
    fun frenchAutomaticIsDoReMi() {
        show(AppSettings(), locale = "fr")
        compose.onNodeWithText("Automatique : Do, Do♯, Re … Si").assertIsSelected()
    }

    @Test
    fun dutchTalkBackSaysTheNotationChoicesTheDutchWay() {
        show(AppSettings(notationChoice = Notation.FLATS), locale = "nl")
        compose.onNode(hasContentDescription("Kruisen: c, cis, d …")).assertExists()
        compose.onNode(hasContentDescription("Mollen: c, des, d …")).assertExists().assertIsSelected()
        compose.onNode(hasContentDescription("Do Re Mi: Do, Do kruis, Re …")).assertExists()
        // What's on screen doesn't change.
        compose.onNodeWithText("Kruisen: C, C♯, D …").assertIsDisplayed()
    }

    @Test
    fun notationShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(notationChoice = Notation.FLATS))
        compose.onNodeWithText(context.getString(R.string.settings_notation_flats)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_notation_sharps)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_notation_solfege)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_notation_german)).performClick()
        compose.onNodeWithText("Automatic", substring = true).performClick()
        assertEquals(listOf(Notation.SOLFEGE, Notation.GERMAN, null), notationChanges)
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
        compose.onNodeWithText(context.getString(R.string.settings_display_numbers)).assertIsNotSelected().performClick()
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

    /** The in-tune effect shows Calm picked on a fresh install, and reports Bold (and back) when tapped. */
    @Test
    fun inTuneEffectShowsTheChoiceAndReportsANewOne() {
        show(AppSettings())
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_effect_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_effect_calm)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_effect_bold)).performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf(InTuneEffect.BOLD), effectChanges)
    }

    @Test
    fun inTuneEffectShowsBoldWhenPicked() {
        show(AppSettings(inTuneEffect = InTuneEffect.BOLD, display = TunerDisplay.NEEDLE))
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_effect_bold)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_in_tune_effect_calm)).performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf(InTuneEffect.CALM), effectChanges)
    }

    /**
     * TalkBack hears Calm and Bold as two radio buttons in one group, by name, with the picked one
     * selected. In English and in Dutch ("Rustig", "Krachtig").
     */
    @Test
    fun talkBackReadsTheInTuneEffectsAsRadioButtons() = assertInTuneEffectsSpoken(locale = null)

    @Test
    fun dutchTalkBackReadsTheInTuneEffectsAsRadioButtons() = assertInTuneEffectsSpoken(locale = "nl")

    private fun assertInTuneEffectsSpoken(locale: String?) {
        show(AppSettings(inTuneEffect = InTuneEffect.BOLD), locale = locale)
        val res = resourcesIn(locale)
        for ((effect, id) in listOf(InTuneEffect.CALM to R.string.settings_in_tune_effect_calm, InTuneEffect.BOLD to R.string.settings_in_tune_effect_bold)) {
            val name = res.getString(id)
            val row = compose.onNodeWithText(name).performScrollTo()
            row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (effect == InTuneEffect.BOLD) row.assertIsSelected() else row.assertIsNotSelected()
            assertEquals("$effect", listOf(name), row.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
            row.assert(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }
    }

    /** At 2× font the in-tune effect's title and both choices are wholly shown, in English and Dutch, also on a phone on its side. */
    @Test
    @Thorough
    fun inTuneEffectFitsAtTwiceTheFontSize() = assertInTuneEffectFits(fontScale = 2f)

    @Test
    fun inTuneEffectFitsInDutchAtTwiceTheFontSizeOnAPhoneOnItsSide() =
        assertInTuneEffectFits(size = PHONE_ON_ITS_SIDE, fontScale = 2f, locale = "nl")

    private fun assertInTuneEffectFits(size: DpSize? = null, fontScale: Float? = null, locale: String? = null) {
        show(AppSettings(), size = size, fontScale = fontScale, locale = locale)
        val res = resourcesIn(locale)
        listOf(R.string.settings_in_tune_effect_title, R.string.settings_in_tune_effect_calm, R.string.settings_in_tune_effect_bold)
            .map { res.getString(it) }
            .forEach { assertWhollyShown(compose.onNodeWithText(it, useUnmergedTree = true), it) }
        listOf(R.string.settings_in_tune_effect_calm, R.string.settings_in_tune_effect_bold).forEach {
            val row = compose.onNodeWithText(res.getString(it)).performScrollTo().getBoundsInRoot()
            assertTrue("${res.getString(it)} row is ${row.bottom - row.top} high", row.bottom - row.top >= 47.5.dp)
        }
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
        show(AppSettings(meterLook = MeterLook.SPIRIT_LEVEL), locale = locale)
        val res = resourcesIn(locale)
        for ((look, id) in meterLookNames) {
            val name = res.getString(id)
            val card = compose.onNodeWithTag(meterLookCardTag(look)).performScrollTo()
            card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (look == MeterLook.SPIRIT_LEVEL) card.assertIsSelected() else card.assertIsNotSelected()
            // Its name, said once: the moving meter inside says nothing.
            assertEquals("$look", listOf(name), card.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
            card.assert(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }
    }

    /**
     * At 2× font every meter look's name still fits its card on one line (two cards to a row then,
     * and the name gets smaller when needed), in English and Dutch ("Waterpas"), on a phone and
     * on a phone on its side.
     */
    @Test
    @Thorough
    fun meterLookNamesFitAtTwiceTheFontSize() = assertMeterLookNamesFit(fontScale = 2f)

    @Test
    @Thorough
    fun meterLookNamesFitInDutchAtTwiceTheFontSize() = assertMeterLookNamesFit(fontScale = 2f, locale = "nl")

    /** Classic sets its names in a wide serif face. */
    @Test
    @Thorough
    fun meterLookNamesFitAtTwiceTheFontSizeInTheClassicStyle() =
        assertMeterLookNamesFit(fontScale = 2f, style = VisualStyle.CLASSIC)

    @Test
    fun meterLookNamesFitInDutchAtTwiceTheFontSizeInTheClassicStyle() =
        assertMeterLookNamesFit(fontScale = 2f, locale = "nl", style = VisualStyle.CLASSIC)

    @Test
    @Thorough
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
        MeterLook.ECLIPSE to R.string.settings_meter_look_eclipse,
        MeterLook.ARROWS to R.string.settings_meter_look_arrows,
        MeterLook.LIGHT_BARS to R.string.settings_meter_look_light_bars,
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
    @Thorough
    fun waveLookNamesFitAtTwiceTheFontSize() = assertWaveLookNamesFit(fontScale = 2f)

    @Test
    @Thorough
    fun waveLookNamesFitInDutchAtTwiceTheFontSize() = assertWaveLookNamesFit(fontScale = 2f, locale = "nl")

    /** Classic sets its names in a wide serif face. */
    @Test
    fun waveLookNamesFitInDutchAtTwiceTheFontSizeInTheClassicStyle() =
        assertWaveLookNamesFit(fontScale = 2f, locale = "nl", style = VisualStyle.CLASSIC)

    @Test
    @Thorough
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
    fun waveLooksAreHiddenWithTheNeedle() {
        show(AppSettings(display = TunerDisplay.NEEDLE))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_title)).assertDoesNotExist()
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
        compose.onNodeWithTag(styleCardTag(VisualStyle.CLASSIC)).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(styleCardTag(VisualStyle.ANCIENT)).performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf(VisualStyle.ANCIENT), styleChanges)
    }

    /** The six styles, in the owner's order, three to a row: Ancient, Classic, Retro, then Contemporary, Futuristic, Space Age. */
    @Test
    fun styleCardsComeInTheOwnersOrderThreeToARow() {
        show(AppSettings())
        // Unclipped: the second row may sit below the bottom of the screen.
        val bounds = VisualStyle.entries.map { compose.onNodeWithTag(styleCardTag(it)).getUnclippedBoundsInRoot() }
        assertEquals(
            listOf(
                VisualStyle.ANCIENT,
                VisualStyle.CLASSIC,
                VisualStyle.RETRO,
                VisualStyle.CONTEMPORARY,
                VisualStyle.FUTURISTIC,
                VisualStyle.SPACE_AGE,
            ),
            VisualStyle.entries,
        )
        for (row in bounds.chunked(3)) {
            row.zipWithNext { a, b ->
                assertEquals("same row", a.top.value, b.top.value, 0.5f)
                assertTrue("left to right", a.right <= b.left)
            }
        }
        assertTrue("second row starts at ${bounds[3].top}, the first ends at ${bounds[0].bottom}: $bounds", bounds[3].top >= bounds[0].bottom)
    }

    /**
     * TalkBack hears every style card as a radio button with the style's name, and which one is
     * picked; the mood picture inside (its big letter and string buttons) says nothing. In English
     * and in Dutch.
     */
    @Test
    fun talkBackReadsEveryStyleAsARadioButton() = assertStylesSpoken(locale = null)

    @Test
    fun dutchTalkBackReadsEveryStyleAsARadioButton() = assertStylesSpoken(locale = "nl")

    private fun assertStylesSpoken(locale: String?) {
        show(AppSettings(style = VisualStyle.FUTURISTIC), locale = locale)
        val res = resourcesIn(locale)
        for ((style, id) in styleNames) {
            val name = res.getString(id)
            val card = compose.onNodeWithTag(styleCardTag(style)).performScrollTo()
            card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            if (style == VisualStyle.FUTURISTIC) card.assertIsSelected() else card.assertIsNotSelected()
            assertEquals("$style", listOf(name), card.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
            card.assert(hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
        }
    }

    /** At 2× font every style's name still fits its card on one line, also Dutch "Ruimtetijdperk". */
    @Test
    fun styleNamesFitInDutchAtTwiceTheFontSize() = assertStyleNamesFit(fontScale = 2f, locale = "nl")

    @Test
    @Thorough
    fun styleNamesFitAtTwiceTheFontSize() = assertStyleNamesFit(fontScale = 2f)

    /** Just under the size where the grid goes to two cards a row: three a row, the tightest fit. */
    @Test
    fun styleNamesFitInDutchInALargerFontSize() = assertStyleNamesFit(fontScale = 1.29f, locale = "nl")

    @Test
    @Thorough
    fun styleNamesFitInDutchAtTwiceTheFontSizeOnAPhoneOnItsSide() =
        assertStyleNamesFit(size = PHONE_ON_ITS_SIDE, fontScale = 2f, locale = "nl")

    private fun assertStyleNamesFit(size: DpSize? = null, fontScale: Float? = null, locale: String? = null) {
        show(AppSettings(), size = size, fontScale = fontScale, locale = locale)
        val res = resourcesIn(locale)
        for ((style, id) in styleNames) {
            val name = res.getString(id)
            val label = compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(styleCardTag(style))), useUnmergedTree = true)
            assertWhollyShown(label, name)
            val layouts = mutableListOf<TextLayoutResult>()
            label.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals("\"$name\" lines", 1, layouts.single().lineCount)
        }
    }

    private val styleNames = mapOf(
        VisualStyle.ANCIENT to DesignR.string.style_ancient,
        VisualStyle.CLASSIC to DesignR.string.style_classic,
        VisualStyle.RETRO to DesignR.string.style_retro,
        VisualStyle.CONTEMPORARY to DesignR.string.style_contemporary,
        VisualStyle.FUTURISTIC to DesignR.string.style_futuristic,
        VisualStyle.SPACE_AGE to DesignR.string.style_space_age,
    )

    @Test
    fun settingsComeInFourHeadedGroupsThenTheAboutLink() {
        show(AppSettings(display = TunerDisplay.WAVE))
        val headings = listOf(
            R.string.settings_group_pitch,
            R.string.settings_group_look,
            R.string.settings_group_tuner_screen,
            R.string.settings_group_while_tuning,
        )
        headings.forEach { compose.onNodeWithText(context.getString(it)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)) }

        // Top to bottom: each heading, then the settings of its group.
        // Unclipped positions: most of these rows are below the fold.
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
        val order = listOf(
            top(context.getString(R.string.settings_group_pitch)),
            top(context.getString(R.string.settings_a4_title)),
            top(context.getString(R.string.settings_notation_title)),
            top(context.getString(R.string.settings_in_tune_precision_title)),
            top(context.getString(R.string.settings_group_look)),
            compose.onNodeWithTag(styleCardTag(VisualStyle.CONTEMPORARY)).fetchSemanticsNode().positionInRoot.y,
            top(context.getString(R.string.settings_theme_title)),
            top(context.getString(R.string.settings_dynamic_color_title)),
            top(context.getString(R.string.settings_group_tuner_screen)),
            top(context.getString(R.string.settings_meter_look_title)),
            top(context.getString(R.string.settings_in_tune_effect_title)),
            top(context.getString(R.string.settings_in_tune_effect_summary)),
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
        assertEquals(bundledFontCredits.size + 6, credits.size)
        bundledFontCredits.forEach { font ->
            compose.onNodeWithTag(creditTag(font.name)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(font.name).assertIsDisplayed()
            compose.onNodeWithText("${font.designer} · SIL Open Font License 1.1").assertIsDisplayed()
        }
        compose.onNodeWithTag(creditTag("Jetpack Compose & AndroidX")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(creditTag("Kotlin & kotlinx.coroutines")).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Google · Apache License 2.0").assertCountEquals(1)
        listOf("Okio", "Protocol Buffers", "JSpecify", "Guava: ListenableFuture").forEach {
            compose.onNodeWithTag(creditTag(it)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Google Inc. · BSD 3-Clause License").assertIsDisplayed()
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
    fun tappingProtocolBuffersShowsItsBsdLicence() {
        show(AppSettings(), about = true)
        compose.onNodeWithTag(creditTag("Protocol Buffers")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Copyright 2008 Google Inc.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Redistribution and use in source and binary forms", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_about_licence_close)).performClick()
        compose.onNodeWithTag(LICENCE_TEXT_TAG).assertDoesNotExist()
    }

    @Test
    @Thorough
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
    @Thorough
    fun newRowsFitOnAPhoneOnItsSide() = assertNewRowsFit(size = PHONE_ON_ITS_SIDE)

    @Test
    @Thorough
    fun newRowsFitAtTwiceTheFontSizeOnAPhoneOnItsSide() = assertNewRowsFit(size = PHONE_ON_ITS_SIDE, fontScale = 2f)

    @Test
    fun newRowsFitInDutchAtTwiceTheFontSizeOnAPhoneOnItsSide() =
        assertNewRowsFit(size = PHONE_ON_ITS_SIDE, fontScale = 2f, locale = "nl")

    @Test
    fun aboutFitsAtTwiceTheFontSize() = assertAboutFits(fontScale = 2f)

    @Test
    @Thorough
    fun aboutFitsOnAPhoneOnItsSide() = assertAboutFits(size = PHONE_ON_ITS_SIDE)

    @Test
    @Thorough
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

    /** [MaterialTheme], or with a [style] that style's lettering and colours (light). */
    @Composable
    private fun Themed(style: VisualStyle?, content: @Composable () -> Unit) {
        if (style == null) MaterialTheme { content() } else TwangleTheme(style = style, darkTheme = false, animate = false) { content() }
    }

    /** Resources in [locale], or the test's own when it is null. */
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
            R.string.settings_group_pitch,
            R.string.settings_notation_title,
            R.string.settings_notation_sharps,
            R.string.settings_notation_flats,
            R.string.settings_notation_solfege,
            R.string.settings_notation_german,
            R.string.settings_group_look,
            R.string.settings_group_tuner_screen,
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
