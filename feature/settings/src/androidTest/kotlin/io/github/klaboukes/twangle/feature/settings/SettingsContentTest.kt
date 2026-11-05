package io.github.klaboukes.twangle.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_FACE
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.bundledFontCredits
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.isCutOffAtTheBottom
import io.github.klaboukes.twangle.core.testing.Thorough
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.settings.components.LANGUAGE_SETTING_TAG
import io.github.klaboukes.twangle.feature.settings.components.LICENCE_TEXT_TAG
import io.github.klaboukes.twangle.feature.settings.components.RATE_TAG
import io.github.klaboukes.twangle.feature.settings.components.WEBSITE_TAG
import io.github.klaboukes.twangle.feature.settings.components.languageOptionTag
import io.github.klaboukes.twangle.feature.settings.components.lensLookCardTag
import io.github.klaboukes.twangle.feature.settings.components.creditTag
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
    private val themeChanges = mutableListOf<ThemeMode>()
    private val dynamicColorChanges = mutableListOf<Boolean>()
    private val keepScreenOnChanges = mutableListOf<Boolean>()
    private val vibrateChanges = mutableListOf<Boolean>()
    private val tickOffChanges = mutableListOf<Boolean>()
    private val underMeterChanges = mutableListOf<UnderMeter>()
    private val signalBarsChanges = mutableListOf<Boolean>()
    private val lensLookChanges = mutableListOf<LensLook>()
    private val leftHandedChanges = mutableListOf<Boolean>()
    private var backs = 0
    private var aboutOpened = 0
    private var rates = 0
    private val websiteLinks = mutableListOf<String>()
    private val languageChanges = mutableListOf<String?>()

    /** The window [show] shows, inside the phone's bars ([shownBars]). */
    private var shownArea = PHONE
    private var shownBars = SystemBars.NONE

    /** Where the window's content may lie: inside the bars. */
    private fun safeArea() = shownBars.safeArea(shownArea)

    /**
     * Shows [settings] (or, with [about], the About page), as if the window inside the phone's bars were
     * [size] ([PHONE] unless given, whatever the phone's own screen; [PhoneScreen] draws a larger one
     * smaller to fit), the phone's font were [fontScale] times its normal size, or the app were in
     * another language ([locale], e.g. "nl"). With [settingsBack] Settings has its back arrow, as from the gear.
     */
    private fun show(
        settings: AppSettings,
        dynamicColorAvailable: Boolean = true,
        size: DpSize? = null,
        fontScale: Float? = null,
        locale: String? = null,
        about: Boolean = false,
        ink: Boolean = false,
        languages: List<String> = emptyList(),
        appLanguage: String? = null,
        settingsBack: Boolean = false,
    ) {
        shownArea = size ?: PHONE
        shownBars = SystemBars.tab(onItsSide = shownArea.width > shownArea.height, Navigation.BUTTONS)
        compose.setContent {
            val content = @Composable {
                Themed(ink) {
                    if (about) {
                        AboutScreen(
                            appVersion = VERSION,
                            onOpenWebsite = { websiteLinks += it },
                            onRate = { rates++ },
                            onBack = { backs++ },
                            modifier = Modifier.testTag(WINDOW),
                        )
                    } else SettingsContent(
                        settings,
                        appVersion = VERSION,
                        onOpenAbout = { aboutOpened++ },
                        onA4Change = { a4Changes += it },
                        onNotationChange = { notationChanges += it },
                        onInTunePrecisionChange = { precisionChanges += it },
                        onThemeChange = { themeChanges += it },
                        onDynamicColorChange = { dynamicColorChanges += it },
                        onKeepScreenOnChange = { keepScreenOnChanges += it },
                        onVibrateInTuneChange = { vibrateChanges += it },
                        onStringTickOffChange = { tickOffChanges += it },
                        onUnderMeterChange = { underMeterChanges += it },
                        onShowSignalBarsChange = { signalBarsChanges += it },
                        onLensLookChange = { lensLookChanges += it },
                        onLeftHandedChange = { leftHandedChanges += it },
                        modifier = Modifier.testTag(WINDOW),
                        dynamicColorAvailable = dynamicColorAvailable,
                        languages = languages,
                        appLanguage = appLanguage,
                        onAppLanguageChange = { languageChanges += it },
                        onBack = if (settingsBack) ({ backs++ }) else null,
                    )
                }
            }
            PhoneScreen(shownArea, shownBars, fontScale ?: 1f, locale, content)
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
        compose.onNodeWithText(context.getString(R.string.settings_a4_value, 442)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun plusAndMinusStepByOneHertz() {
        show(AppSettings(a4Hz = 442f))
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_raise)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_lower)).performClick()
        assertEquals(listOf(443f, 441f), a4Changes)
    }

    @Test
    fun resetGoesBackTo440() {
        show(AppSettings(a4Hz = 432f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_reset, 440)).performScrollTo().performClick()
        assertEquals(listOf(440f), a4Changes)
    }

    @Test
    fun resetStaysAButtonAt440AndChangesNothing() {
        // Never greyed out: too faint on a dimmed screen (owner, 2026-11-05).
        show(AppSettings(a4Hz = 440f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_reset, 440)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(emptyList<Float>(), a4Changes)
    }

    @Test
    fun buttonsStopAtTheEndsOfTheRange() {
        show(AppSettings(a4Hz = 466f))
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_raise)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_lower)).assertIsEnabled()
    }

    /**
     * Settings is behind the gear (Phase 69), no tab: its back arrow stands at the top and leads back (to
     * the tab that opened it).
     */
    @Test
    fun settingsBackArrowGoesBack() {
        show(AppSettings(), settingsBack = true)
        compose.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
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

    /** "Show me around" went with the tour (Phase 68): its row (tagged so in 1.1) is gone, About stays. */
    @Test
    fun showMeAroundIsGone() {
        show(AppSettings())
        compose.onNodeWithTag("settings_show_tour").assertDoesNotExist()
        compose.onNodeWithTag(ABOUT_LINK_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutBackArrowGoesBack() {
        show(AppSettings(), about = true)
        compose.onNodeWithText(context.getString(R.string.about_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_back)).performClick()
        assertEquals(1, backs)
    }

    /**
     * Note names is one row (since 2026-11-05; five choice rows until then): the picked one under its title, the list
     * in a dialog that a tap opens, Cancel closing it with nothing changed.
     */
    @Test
    fun noteNamesIsOneRowThatOpensTheList() {
        show(AppSettings(notationChoice = Notation.FLATS))
        compose.onNodeWithTag(notationOptionTag(Notation.SHARPS)).assertDoesNotExist()
        compose.onNodeWithTag(NOTATION_SETTING_TAG)
            .performScrollTo()
            .assert(hasText(context.getString(R.string.settings_notation_title)))
            .assert(hasText(context.getString(R.string.settings_notation_flats)))
            .assertHasClickAction()
        openNoteNames()
        (listOf(null) + Notation.entries).forEach { compose.onNodeWithTag(notationOptionTag(it)).assertIsDisplayed() }
        compose.onNodeWithText(context.getString(R.string.settings_language_cancel)).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(emptyList<Notation?>(), notationChanges)
    }

    private fun openNoteNames() {
        compose.onNodeWithTag(NOTATION_SETTING_TAG).performScrollTo().performClick()
        compose.onNode(isDialog()).assertExists()
    }

    @Test
    fun talkBackSaysTheNotationChoicesSignsAsWords() {
        show(AppSettings())
        // Never picked: Automatic, which in English is sharps; said in words under the row's title too.
        compose.onNodeWithTag(NOTATION_SETTING_TAG).performScrollTo().assert(hasContentDescription("Automatic: C, C sharp, D … B", substring = true))
        openNoteNames()
        compose.onNodeWithTag(notationOptionTag(Notation.SHARPS)).assert(hasContentDescription("Sharps: C, C sharp, D …"))
        compose.onNodeWithTag(notationOptionTag(Notation.FLATS)).assert(hasContentDescription("Flats: C, D flat, D …"))
        compose.onNodeWithTag(notationOptionTag(Notation.SOLFEGE)).assert(hasContentDescription("Do Re Mi: Do, Do sharp, Re …"))
        compose.onNodeWithTag(notationOptionTag(Notation.GERMAN)).assert(hasContentDescription("German: C, C sharp, D … H"))
        compose.onNodeWithTag(notationOptionTag(null)).assert(hasContentDescription("Automatic: C, C sharp, D … B")).assertIsSelected()
    }

    @Test
    fun germanAutomaticIsTheGermanNotationAndTalkBackSaysItTheGermanWay() {
        show(AppSettings(), locale = "de")
        compose.onNodeWithText("Automatisch: C, C♯, D … H").performScrollTo().assertIsDisplayed()
        openNoteNames()
        compose.onNodeWithTag(notationOptionTag(null)).assert(hasContentDescription("Automatisch: C, Cis, D … H")).assertIsSelected()
        compose.onNodeWithTag(notationOptionTag(Notation.GERMAN)).assert(hasContentDescription("Deutsch: C, Cis, D … H")).assertIsNotSelected()
    }

    @Test
    fun frenchAutomaticIsDoReMi() {
        show(AppSettings(), locale = "fr")
        // French sets a no-break space before the colon.
        val automatic = "Automatique : Do, Do♯, Re … Si"
        compose.onNodeWithTag(NOTATION_SETTING_TAG).performScrollTo().assert(hasText(automatic))
        openNoteNames()
        compose.onNodeWithTag(notationOptionTag(null)).assert(hasText(automatic)).assertIsSelected()
    }

    @Test
    fun dutchTalkBackSaysTheNotationChoicesTheDutchWay() {
        show(AppSettings(notationChoice = Notation.FLATS), locale = "nl")
        openNoteNames()
        compose.onNodeWithTag(notationOptionTag(Notation.SHARPS)).assert(hasContentDescription("Kruisen: c, cis, d …"))
        compose.onNodeWithTag(notationOptionTag(Notation.FLATS)).assert(hasContentDescription("Mollen: c, des, d …")).assertIsSelected()
        compose.onNodeWithTag(notationOptionTag(Notation.SOLFEGE)).assert(hasContentDescription("Do Re Mi: Do, Do kruis, Re …"))
        // What's on screen doesn't change.
        compose.onNodeWithTag(notationOptionTag(Notation.SHARPS)).assert(hasText("Kruisen: C, C♯, D …"))
    }

    @Test
    fun notationShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(notationChoice = Notation.FLATS))
        openNoteNames()
        compose.onNodeWithTag(notationOptionTag(Notation.FLATS)).assertIsSelected()
        compose.onNodeWithTag(notationOptionTag(Notation.SHARPS)).assertIsNotSelected()
        // A pick closes the list; the one already picked changes nothing.
        compose.onNodeWithTag(notationOptionTag(Notation.FLATS)).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        for (notation in listOf(Notation.SOLFEGE, Notation.GERMAN, null)) {
            openNoteNames()
            compose.onNodeWithTag(notationOptionTag(notation)).performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
        }
        assertEquals(listOf(Notation.SOLFEGE, Notation.GERMAN, null), notationChanges)
    }

    /**
     * What goes under the meter (2.0, Phase 78), in Look under the Meter cards since 2026-11-05: Words by default, Cents reported when picked;
     * the words named as the tuner says them, in Dutch too.
     */
    @Test
    fun underTheMeterIsWordsByDefaultAndReportsCents() = assertUnderMeter("en", "Under the meter", "Words: Tighten or Loosen", "Cents: −12")

    @Test
    fun dutchUnderTheMeterIsWordsByDefaultAndReportsCents() = assertUnderMeter("nl", "Onder de meter", "Woorden: Strakker of Losser", "Cent: −12")

    private fun assertUnderMeter(locale: String, title: String, words: String, cents: String) {
        show(AppSettings(), locale = locale)
        val res = resourcesIn(locale)
        assertEquals(listOf(title, words, cents), listOf(R.string.settings_under_meter_title, R.string.settings_under_meter_words, R.string.settings_under_meter_cents).map(res::getString))
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(words).performScrollTo().assertIsSelected()
        compose.onNodeWithText(cents).performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf(UnderMeter.CENTS), underMeterChanges)
    }

    @Test
    fun underTheMeterShowsCentsWhenPicked() {
        show(AppSettings(underMeter = UnderMeter.CENTS))
        compose.onNodeWithText(context.getString(R.string.settings_under_meter_cents)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_under_meter_words)).performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf(UnderMeter.WORDS), underMeterChanges)
    }

    /**
     * In-tune precision is one switch, "Precise tuning" (since 2026-11-05; two choice rows, then a segmented switch,
     * until then): off is Relaxed, on is Precise, and its line says what it does, in Dutch too.
     */
    @Test
    fun preciseTuningIsASwitchOffByDefault() = assertPreciseSwitch("en", "Precise tuning", "In tune within 2 cents instead of 5")

    @Test
    fun dutchPreciseTuningIsASwitchOffByDefault() = assertPreciseSwitch("nl", "Precies stemmen", "Zuiver binnen 2 cent in plaats van 5")

    private fun assertPreciseSwitch(locale: String, title: String, summary: String) {
        show(AppSettings(), locale = locale)
        val res = resourcesIn(locale)
        assertEquals(listOf(title, summary), listOf(R.string.settings_precise_title, R.string.settings_precise_summary).map(res::getString))
        val switch = compose.onNodeWithText(title)
        switch.performScrollTo().assertIsOff()
        compose.onNodeWithText(summary).assertIsDisplayed()
        switch.performClick()
        assertEquals(listOf(InTunePrecision.PRECISE), precisionChanges)
    }

    @Test
    fun preciseTuningShowsItIsOnAndTurnsBackToRelaxed() {
        show(AppSettings(inTunePrecision = InTunePrecision.PRECISE))
        val switch = compose.onNodeWithText(context.getString(R.string.settings_precise_title))
        switch.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(InTunePrecision.RELAXED), precisionChanges)
    }

    /**
     * Light or dark is one row that opens its list (since 2026-11-05; three choice rows, then a segmented switch, until
     * then), as Note names and Language: the picked one under its title, Same as the phone · Light · Dark in the list.
     */
    @Test
    fun lightOrDarkIsOneRowThatOpensTheList() {
        show(AppSettings(theme = ThemeMode.SYSTEM))
        assertEquals(
            listOf("Same as the phone", "Light", "Dark"),
            listOf(R.string.settings_theme_system, R.string.settings_theme_light, R.string.settings_theme_dark).map(context::getString),
        )
        compose.onNodeWithTag(themeOptionTag(ThemeMode.DARK)).assertDoesNotExist()
        compose.onNodeWithTag(THEME_SETTING_TAG)
            .performScrollTo()
            .assert(hasText(context.getString(R.string.settings_theme_title)))
            .assert(hasText(context.getString(R.string.settings_theme_system)))
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithTag(themeOptionTag(ThemeMode.SYSTEM)).assertIsSelected()
        compose.onNodeWithTag(themeOptionTag(ThemeMode.LIGHT)).assertIsNotSelected()
        compose.onNodeWithTag(themeOptionTag(ThemeMode.DARK)).assertIsNotSelected().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf(ThemeMode.DARK), themeChanges)
    }

    @Test
    fun switchesShowTheirStateAndToggle() {
        show(AppSettings(dynamicColor = true, keepScreenOn = false, vibrateInTune = true, stringTickOff = true))
        val dynamic = compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title))
        dynamic.performScrollTo().assertIsOn().performClick()
        val keepOn = compose.onNodeWithText(context.getString(R.string.settings_keep_screen_on_title))
        keepOn.performScrollTo().assertIsOff().performClick()
        val vibrate = compose.onNodeWithText(context.getString(R.string.settings_vibrate_in_tune_title))
        vibrate.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), dynamicColorChanges)
        assertEquals(listOf(true), keepScreenOnChanges)
        assertEquals(listOf(false), vibrateChanges)
        val tickOff = compose.onNodeWithText(context.getString(R.string.settings_string_tick_off_title))
        tickOff.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), tickOffChanges)
    }

    /**
     * The string tick-off's switch (2.0, Phase 71), off by default, in While tuning: its words name the ticks and
     * "In tune. Play.", in Dutch too. 1.1's "Show tuning help" is gone (the word under the lens always shows).
     */
    @Test
    fun theStringTickOffSwitchIsOffByDefaultAndNamesTheTicks() =
        assertStringTickOffSwitch("en", "Tick off tuned strings", "In tune. Play.")

    @Test
    fun dutchStringTickOffSwitchIsOffByDefaultAndNamesTheTicks() =
        assertStringTickOffSwitch("nl", "Gestemde snaren afvinken", "Zuiver. Speel maar.")

    private fun assertStringTickOffSwitch(locale: String, title: String, allInTune: String) {
        show(AppSettings(), locale = locale)
        val res = resourcesIn(locale)
        assertEquals(title, res.getString(R.string.settings_string_tick_off_title))
        val summary = res.getString(R.string.settings_string_tick_off_summary)
        assertTrue("the line names the tuner's own words: $summary", allInTune in summary)
        val switch = compose.onNodeWithText(title)
        switch.performScrollTo().assertIsOff()
        compose.onNodeWithText(summary).assertIsDisplayed()
        compose.onAllNodesWithText("Show tuning help").assertCountEquals(0)
        compose.onAllNodesWithText("Stemhulp tonen").assertCountEquals(0)
        switch.performClick()
        assertEquals(listOf(true), tickOffChanges)
    }

    /**
     * While tuning has no switch for the 12th-fret check (2.0, Phase 71): the check is opened from the tuner's picker
     * sheet since the owner picked that way in (2026-11-03). Its name is no Settings text, in English or Dutch.
     */
    @Test
    fun whileTuningHasNoSwitchForTheTwelfthFretCheck() = assertNoIntonationCheckSwitch("en", "12th-fret")

    @Test
    fun dutchWhileTuningHasNoSwitchForTheTwelfthFretCheck() = assertNoIntonationCheckSwitch("nl", "12e-fret")

    private fun assertNoIntonationCheckSwitch(locale: String, name: String) {
        show(AppSettings(), locale = locale)
        compose.onNodeWithText(resourcesIn(locale).getString(R.string.settings_string_tick_off_title)).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText(name, substring = true)).assertCountEquals(0)
    }

    @Test
    fun signalBarsSwitchIsOffByDefaultAndToggles() {
        show(AppSettings())
        val bars = compose.onNodeWithText(context.getString(R.string.settings_show_signal_bars_title))
        bars.performScrollTo().assertIsOff().performClick()
        assertEquals(listOf(true), signalBarsChanges)
    }

    @Test
    fun leftHandedSwitchIsOffByDefaultAndToggles() {
        show(AppSettings())
        val leftHanded = compose.onNodeWithText(context.getString(R.string.settings_left_handed_title))
        leftHanded.performScrollTo().assertIsOff().performClick()
        assertEquals(listOf(true), leftHandedChanges)
    }

    @Test
    fun leftHandedSwitchShowsItIsOnAtTheEndOfLook() {
        show(AppSettings(leftHanded = true))
        val leftHanded = compose.onNodeWithText(context.getString(R.string.settings_left_handed_title))
        leftHanded.performScrollTo().assertIsOn()
        // At the end of "Look" (Phase 75; "Pitch and notes" until then), under Meter, before the "Twangle" group.
        val top = { text: Int -> compose.onNodeWithText(context.getString(text)).getUnclippedBoundsInRoot().top }
        assertTrue("under Look", top(R.string.settings_left_handed_title) > top(R.string.settings_group_look))
        assertTrue("under Meter", top(R.string.settings_left_handed_title) > top(R.string.settings_meter_title))
        assertTrue("before Twangle", top(R.string.settings_left_handed_title) < top(R.string.settings_group_twangle))
        leftHanded.performClick()
        assertEquals(listOf(false), leftHandedChanges)
    }

    /**
     * 1.1's meter look cards, Calm or Bold, "Below the note" and the wave look cards went in Phase 67 (one meter and
     * one glow until the lens; its Meter cards come in Phase 72): no card of theirs is left. Meter stands in the Look
     * group (Phase 75; the Tuner screen group's only row until then), first, over light or dark and Left-handed charts
     * (the owner's review, 2026-11-03), and Look is the page's first group (2026-11-05).
     */
    @Test
    fun meterOffersTheThreeLooksAsCardsWithTheSavedOnePicked() {
        show(AppSettings(lensLook = LensLook.STROBE))
        val lookCards = SemanticsMatcher("a 1.1 look card") {
            val tag = it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }.orEmpty()
            tag.startsWith("meter_look_card_") || tag.startsWith("wave_look_card_")
        }
        compose.onAllNodes(lookCards, useUnmergedTree = true).assertCountEquals(0)
        val top = { text: Int -> compose.onNodeWithText(context.getString(text)).fetchSemanticsNode().positionInRoot.y }
        val theme = top(R.string.settings_theme_title)
        val meter = top(R.string.settings_meter_title)
        val leftHanded = top(R.string.settings_left_handed_title)
        assertTrue("Meter over Light or dark, Left-handed charts under both", theme > meter && leftHanded > theme)
        val names = mapOf(
            LensLook.CLASSIC to R.string.settings_meter_classic,
            LensLook.LIGHT_ARC to R.string.settings_meter_light_arc,
            LensLook.STROBE to R.string.settings_meter_strobe,
        )
        val lefts = LensLook.entries.map { look ->
            // Not scrolled to, so every place is measured on the same page.
            val card = compose.onNodeWithTag(lensLookCardTag(look))
            // TalkBack: a radio button named after its look, the saved one picked.
            card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            card.assert(hasText(context.getString(names.getValue(look))))
            if (look == LensLook.STROBE) card.assertIsSelected() else card.assertIsNotSelected()
            // Unclipped: a card below the fold has no bounds on screen.
            val node = card.fetchSemanticsNode()
            val top = node.positionInRoot.y
            val bottom = top + node.size.height
            assertTrue("$look's card ($top to $bottom) between Meter ($meter) and Light or dark ($theme)", top > meter && bottom < theme)
            node.positionInRoot.x
        }
        assertEquals("Classic · Light arc · Strobe, in one row", lefts.sorted(), lefts)
    }

    @Test
    fun pickingAMeterCardSavesItsLook() {
        show(AppSettings())
        compose.onNodeWithTag(lensLookCardTag(LensLook.CLASSIC)).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(lensLookCardTag(LensLook.LIGHT_ARC)).performClick()
        compose.onNodeWithTag(lensLookCardTag(LensLook.STROBE)).performClick()
        assertEquals(listOf(LensLook.LIGHT_ARC, LensLook.STROBE), lensLookChanges)
    }

    @Test
    fun wallpaperColoursAreHiddenWithoutDynamicColor() {
        show(AppSettings(), dynamicColorAvailable = false)
        compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title)).assertDoesNotExist()
    }

    @Test
    fun settingsComeInFourHeadedGroupsTheLastEndingInTheAboutLink() {
        show(AppSettings(), languages = listOf("de", "nl"))
        val headings = listOf(
            R.string.settings_group_look,
            R.string.settings_group_while_tuning,
            R.string.settings_group_tuning,
            R.string.settings_group_twangle,
        )
        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        headings.forEach { compose.onNodeWithText(context.getString(it)).assert(isHeading) }
        // No other heading but the page's title (every tab's top row, 2026-11-04): the four groups are all there is
        // (Phase 75; five until then).
        compose.onNodeWithText(context.getString(R.string.settings_title)).assert(isHeading)
        compose.onAllNodes(isHeading).assertCountEquals(headings.size + 1)

        // Top to bottom: each heading, then the settings of its group (2.0, Phase 75, the first mock-up's four groups;
        // Look first and the reference pitch last since the owner's look of 2026-11-05: what most players change first).
        // Unclipped positions: most of these rows are below the fold.
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
        val order = listOf(
            top(context.getString(R.string.settings_group_look)),
            // The lens's cards first (the owner's review, 2026-11-03), then what goes under the meter, light or dark with
            // the wallpaper colours, then the charts' hand.
            top(context.getString(R.string.settings_meter_title)),
            top(context.getString(R.string.settings_under_meter_title)),
            top(context.getString(R.string.settings_theme_title)),
            top(context.getString(R.string.settings_dynamic_color_title)),
            top(context.getString(R.string.settings_left_handed_title)),
            top(context.getString(R.string.settings_group_while_tuning)),
            top(context.getString(R.string.settings_keep_screen_on_title)),
            top(context.getString(R.string.settings_vibrate_in_tune_title)),
            top(context.getString(R.string.settings_string_tick_off_title)),
            top(context.getString(R.string.settings_show_signal_bars_title)),
            top(context.getString(R.string.settings_group_tuning)),
            top(context.getString(R.string.settings_notation_title)),
            top(context.getString(R.string.settings_precise_title)),
            top(context.getString(R.string.settings_a4_title)),
            top(context.getString(R.string.settings_group_twangle)),
            top(context.getString(R.string.settings_language_title)),
            top(context.getString(R.string.about_title)),
        )
        assertEquals("tops $order", order.sorted(), order)
    }

    @Test
    fun onAWideScreenTheSettingsStayInAReadableColumn() {
        show(AppSettings(), size = DpSize(1200.dp, 800.dp))
        val window = safeArea()
        val row = compose.onNodeWithText(context.getString(R.string.settings_under_meter_words)).performScrollTo().getBoundsInRoot()
        // 600 of the 1200 dp, in the middle.
        val share = (row.right - row.left) / (window.right - window.left)
        assertTrue("row takes $share of the width", share in 0.4f..0.51f)
        val leftGap = row.left - window.left
        val rightGap = window.right - row.right
        assertTrue("gaps $leftGap and $rightGap", abs((leftGap - rightGap).value) < 2f)
    }

    /**
     * The page's top row stands over the rows' column (Phase 52): on a tablet the title stayed at the window's
     * edge while the rows stood in the centred 600 dp column, 100 dp apart upright and 300 dp on its side. Since
     * 2026-11-04 it is every tab's top row, the title in the middle of the column.
     */
    @Test
    fun onATabletUprightTheTitleStandsOverTheRows() = assertSettingsTitleLinesUp(TABLET_UPRIGHT)

    @Test
    fun onATabletOnItsSideTheTitleStandsOverTheRows() = assertSettingsTitleLinesUp(TABLET_ON_ITS_SIDE)

    @Test
    fun onAPhoneTheTitleStandsOverTheRows() = assertSettingsTitleLinesUp(PHONE)

    private fun assertSettingsTitleLinesUp(size: DpSize) {
        show(AppSettings(), size = size, fontScale = TABLET_FONT_SCALE)
        val title = compose.onNodeWithText(context.getString(R.string.settings_title)).getBoundsInRoot()
        val heading = compose.onNodeWithText(context.getString(R.string.settings_group_look)).getBoundsInRoot()
        assertEquals("$size: the first heading at the column's start", (columnStart() + ROW_INSET).value, heading.left.value, tolerance())
        assertEquals("$size: the title in the column's middle", columnMiddle().value, ((title.left + title.right) / 2).value, tolerance())
    }

    /** On About the back arrow's outline stands at the column's start, in line with the text, and the title in the middle. */
    @Test
    fun onATabletUprightAboutsBackArrowLinesUpWithTheText() = assertAboutTitleLinesUp(TABLET_UPRIGHT)

    @Test
    fun onATabletOnItsSideAboutsBackArrowLinesUpWithTheText() = assertAboutTitleLinesUp(TABLET_ON_ITS_SIDE)

    @Test
    fun onAPhoneAboutsBackArrowLinesUpWithTheText() = assertAboutTitleLinesUp(PHONE)

    private fun assertAboutTitleLinesUp(size: DpSize) {
        show(AppSettings(), size = size, fontScale = TABLET_FONT_SCALE, about = true)
        val back = compose.onNodeWithTag(SETTINGS_BACK_TAG).getBoundsInRoot()
        val title = compose.onNodeWithText(context.getString(R.string.about_title)).getBoundsInRoot()
        val text = compose.onNodeWithText(context.getString(R.string.settings_about_free_title)).getBoundsInRoot()
        assertEquals("$size: the text at the column's start", (columnStart() + ROW_INSET).value, text.left.value, tolerance())
        assertEquals("$size: the back arrow's outline in line with the text", text.left.value, back.left.value, tolerance())
        assertEquals("$size: the arrow on the top row's face", TOP_BAR_FACE.value, (back.right - back.left).value, tolerance())
        assertEquals("$size: the title in the column's middle", columnMiddle().value, ((title.left + title.right) / 2).value, tolerance())
        assertTrue("$size: the title after the arrow", title.left >= back.right)
    }

    /**
     * How far apart two edges may be: [EDGE_TOLERANCE], or a pixel and a half where the window is drawn smaller to
     * fit the phone's screen (a tablet's window on a phone, or on its side on the tablet upright: under 1 px a dp).
     */
    private fun tolerance(): Float =
        maxOf(EDGE_TOLERANCE, 1.5f / compose.onNodeWithTag(WINDOW).fetchSemanticsNode().layoutInfo.density.density)

    /** Where the rows' centred column starts in the window: at most 600 dp wide, in the middle of the safe area. */
    private fun columnStart(): Dp {
        val safe = safeArea()
        return safe.left + maxOf(0.dp, (safe.right - safe.left - CONTENT_WIDTH) / 2)
    }

    private fun columnMiddle(): Dp = safeArea().let { (it.left + it.right) / 2 }

    @Test
    fun aboutShowsTheVersionTheNamesMeaningThePromiseAndThePrivacyStatement() {
        show(AppSettings(), about = true)
        listOf(
            context.getString(R.string.settings_about_version, VERSION),
            context.getString(R.string.settings_about_name_title),
            context.getString(R.string.settings_about_name_meaning),
            context.getString(R.string.settings_about_free_title),
            context.getString(R.string.settings_about_free_summary),
            context.getString(R.string.settings_about_privacy_title),
            context.getString(R.string.settings_about_privacy_summary),
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    /** A quiet "Rate Twangle" row: a button that says where it leads, and one tap asks to open the Play page once. */
    @Test
    fun rateTwangleIsAQuietRowThatOpensThePlayPage() {
        show(AppSettings(), about = true)
        compose.onNodeWithTag(RATE_TAG).performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText(context.getString(R.string.settings_about_rate_title)))
            .assert(hasText(context.getString(R.string.settings_about_rate_summary)))
            .assertHeightIsAtLeast(48.dp)
        assertEquals(0, rates)
        compose.onNodeWithTag(RATE_TAG).performClick()
        assertEquals(1, rates)
    }

    /**
     * "Help and website": a quiet row like "Rate Twangle", and one tap asks to open the website's home page
     * once, in the app's language where the site has it (Dutch), else in English.
     */
    @Test
    fun helpAndWebsiteIsAQuietRowThatOpensTheWebsite() {
        show(AppSettings(), about = true)
        compose.onNodeWithTag(WEBSITE_TAG).performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText(context.getString(R.string.settings_about_website_title)))
            .assert(hasText(context.getString(R.string.settings_about_website_summary)))
            .assertHeightIsAtLeast(48.dp)
        assertEquals(emptyList<String>(), websiteLinks)
        compose.onNodeWithTag(WEBSITE_TAG).performClick()
        assertEquals(listOf("https://klaboukes.github.io/"), websiteLinks)
        assertEquals(0, rates)
    }

    @Test
    fun helpAndWebsiteOpensTheDutchSiteInDutch() {
        show(AppSettings(), about = true, locale = "nl")
        compose.onNodeWithTag(WEBSITE_TAG).performScrollTo().performClick()
        assertEquals(listOf("https://klaboukes.github.io/nl/"), websiteLinks)
    }

    /** The site has only English and Dutch so far: every other language gets the English site. */
    @Test
    fun helpAndWebsiteOpensTheEnglishSiteInGerman() {
        show(AppSettings(), about = true, locale = "de")
        compose.onNodeWithTag(WEBSITE_TAG).performScrollTo().performClick()
        assertEquals(listOf("https://klaboukes.github.io/"), websiteLinks)
    }

    @Test
    fun everyFontAndLibraryIsCredited() {
        show(AppSettings(), about = true)
        assertEquals(bundledFontCredits.size + 8, credits.size)
        bundledFontCredits.forEach { font ->
            compose.onNodeWithTag(creditTag(font.name)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(font.name).assertIsDisplayed()
            compose.onNodeWithText("${font.designer} · SIL Open Font License 1.1").assertIsDisplayed()
        }
        compose.onNodeWithTag(creditTag("Jetpack Compose & AndroidX")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(creditTag("Kotlin & kotlinx.coroutines")).performScrollTo().assertIsDisplayed()
        // Compose & AndroidX and Material Symbols.
        compose.onAllNodesWithText("Google · Apache License 2.0").assertCountEquals(2)
        listOf("Okio", "Protocol Buffers", "JSpecify", "Guava: ListenableFuture", "kotlinx.serialization", "Material Symbols").forEach {
            compose.onNodeWithTag(creditTag(it)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Google Inc. · BSD 3-Clause License").assertIsDisplayed()
    }

    @Test
    fun creditsAreButtonsThatSayWhatTheyDo() {
        show(AppSettings(), about = true)
        compose.onNodeWithTag(creditTag("Noto Sans Symbols")).performScrollTo()
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
        compose.onNodeWithTag(creditTag("Noto Sans Symbols")).performScrollTo().performClick()
        // The licence is read from the app's own files, so it is there without internet.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Google Inc. All Rights Reserved", substring = true).fetchSemanticsNodes().isNotEmpty() }
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
        val window = safeArea()
        credits.forEach {
            val row = compose.onNodeWithTag(creditTag(it.name)).performScrollTo().assertIsDisplayed().getBoundsInRoot()
            assertTrue("${it.name} fits across", row.left >= window.left && row.right <= window.right)
        }
    }

    @Test
    fun theLicenceCanBeClosedAtTwiceTheFontSize() {
        show(AppSettings(), fontScale = 2f, about = true)
        compose.onNodeWithTag(creditTag("Noto Sans Symbols")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Google Inc. All Rights Reserved", substring = true).fetchSemanticsNodes().isNotEmpty() }
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

    /** [MaterialTheme], or with [ink] the app's own theme (Ink, light). */
    @Composable
    private fun Themed(ink: Boolean, content: @Composable () -> Unit) {
        if (!ink) MaterialTheme { content() } else TwangleTheme(darkTheme = false, animate = false) { content() }
    }

    /** Resources in [locale], or the test's own when it is null. */
    private fun resourcesIn(locale: String?): android.content.res.Resources {
        if (locale == null) return context.resources
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(locale))
        return context.createConfigurationContext(config).resources
    }

    /**
     * The rows phase 8 added to settings, with Phase 75's four group headings: the headings, the note-name choices and the
     * About link. Each can be scrolled to and is then wholly on screen, inside the window, and its
     * text isn't cut short.
     */
    private fun assertNewRowsFit(size: DpSize? = null, fontScale: Float? = null, locale: String? = null) {
        show(AppSettings(), size = size, fontScale = fontScale, locale = locale)
        val res = resourcesIn(locale)
        val texts = listOf(
            R.string.settings_group_tuning,
            // Note names and Light or dark are rows that open their lists, Precise tuning a switch (2026-11-05).
            R.string.settings_notation_title,
            R.string.settings_theme_title,
            R.string.settings_theme_system,
            R.string.settings_precise_title,
            R.string.settings_precise_summary,
            R.string.settings_group_while_tuning,
            R.string.settings_group_look,
            R.string.settings_group_twangle,
            R.string.about_title,
        ).map { res.getString(it) } + res.getString(R.string.settings_about_link_summary, VERSION)
        texts.forEach { assertWhollyShown(compose.onNodeWithText(it, useUnmergedTree = true), it) }
        assertWhollyShown(compose.onNodeWithTag(NOTATION_SETTING_TAG), "the note names row")
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
            R.string.settings_about_name_title,
            R.string.settings_about_free_title,
            R.string.settings_about_privacy_title,
            R.string.settings_about_credits_title,
        ).map { res.getString(it) } + res.getString(R.string.settings_about_version, VERSION) + credits.map { it.name }
        texts.forEach { assertWhollyShown(compose.onNodeWithText(it, useUnmergedTree = true), it) }
        // The long About texts are taller than a phone on its side at 2×, but they wrap inside the window.
        val window = safeArea()
        listOf(
            R.string.settings_about_name_meaning,
            R.string.settings_about_free_summary,
            R.string.settings_about_privacy_summary,
            R.string.settings_about_credits_summary,
        )
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
            assertTrue("\"$what\" is cut off at the bottom", !layout.isCutOffAtTheBottom())
            lines.forEach { line ->
                assertTrue("\"$what\" line $line runs out of its box", layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("\"$what\" line $line is ellipsized", !layout.isLineEllipsized(line))
            }
        }
    }

    private companion object {
        /** The window unless a test gives one: a Galaxy S26 upright inside its bars, the same on every phone. */
        val PHONE = DpSize(360.dp, 730.dp)

        const val WINDOW = "window"
        const val VERSION = "1.2.3-test"
        const val TEST_TIMEOUT_SECONDS = 10L

        /** A phone on its side, as the S26 (780 × 360 dp) with a little to spare. */
        val PHONE_ON_ITS_SIDE = DpSize(800.dp, 360.dp)

        /** A tablet (the Lenovo Tab M11) upright and on its side. */
        val TABLET_UPRIGHT = DpSize(800.dp, 1280.dp)
        val TABLET_ON_ITS_SIDE = DpSize(1280.dp, 800.dp)

        /** The Lenovo Tab M11's own text size. */
        const val TABLET_FONT_SCALE = 1.3f

        /** The rows' column at most and its rows' inset, as SettingsScreen sets them. */
        val CONTENT_WIDTH = 600.dp
        val ROW_INSET = 16.dp

        /** Positions compared in dp: at the tablet's 1.5 px a dp, a pixel's rounding is 0.67 dp ([tolerance]). */
        const val EDGE_TOLERANCE = 0.75f
    }
}
