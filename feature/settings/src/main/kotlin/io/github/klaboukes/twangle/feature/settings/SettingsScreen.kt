package io.github.klaboukes.twangle.feature.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.designsystem.BackButton
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_MARGIN
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_TOP
import io.github.klaboukes.twangle.core.designsystem.TopBar
import io.github.klaboukes.twangle.core.designsystem.TopBarTitle
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.isMusicSign
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.feature.settings.components.A4Calibration
import io.github.klaboukes.twangle.feature.settings.components.ChoiceDialogSetting
import io.github.klaboukes.twangle.feature.settings.components.LanguageSetting
import io.github.klaboukes.twangle.feature.settings.components.LinkSetting
import io.github.klaboukes.twangle.feature.settings.components.LocalSettingsWindow
import io.github.klaboukes.twangle.feature.settings.components.MeterPicker
import io.github.klaboukes.twangle.feature.settings.components.RadioGroupSetting
import io.github.klaboukes.twangle.feature.settings.components.SwitchSetting

/**
 * Settings entry point, opened from the gear in the top corner of every tab (2.0, Phase 69; a tab of
 * its own until then): with [onBack] a back arrow at the top leads back to that tab, as Back does. Every change is saved straight away; [onOpenAbout] opens the About page. [appVersion] is the app's version name, shown on the About link.
 *
 * With [languages] (`twangle.languages`, as resource qualifiers), a Language row sets the app's
 * language through AppCompat: on Android 13+ that is the per-app language of phone Settings, on
 * Android 8–12 AppCompat keeps it. The app's screens are then recreated in it; [onLanguageChange]
 * follows, for what lives outside them (the home-screen widget).
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    appVersion: String,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    languages: List<String> = emptyList(),
    onLanguageChange: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val languageTags = remember(languages) { AppLanguages.tagsOf(languages) }
    var appLanguage by remember(languageTags) {
        mutableStateOf(AppLanguages.selectedOf(AppCompatDelegate.getApplicationLocales()[0], languageTags))
    }
    SettingsContent(
        settings = settings,
        appVersion = appVersion,
        onOpenAbout = onOpenAbout,
        onA4Change = viewModel::setA4,
        onNotationChange = viewModel::setNotation,
        onInTunePrecisionChange = viewModel::setInTunePrecision,
        onThemeChange = viewModel::setTheme,
        onDynamicColorChange = viewModel::setDynamicColor,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onVibrateInTuneChange = viewModel::setVibrateInTune,
        onStringTickOffChange = viewModel::setStringTickOff,
        onUnderMeterChange = viewModel::setUnderMeter,
        onShowSignalBarsChange = viewModel::setShowSignalBars,
        onLensLookChange = viewModel::setLensLook,
        onLeftHandedChange = viewModel::setLeftHanded,
        modifier = modifier,
        onBack = onBack,
        languages = languageTags,
        appLanguage = appLanguage,
        onAppLanguageChange = { tag ->
            appLanguage = tag
            AppCompatDelegate.setApplicationLocales(
                if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
            )
            onLanguageChange()
        },
    )
}

/**
 * The settings screen body. Stateless, so previews and UI tests can drive it. Empty while [settings]
 * load. The settings come in four headed groups, the last ending in a link to the About page (see [SettingsGroups]);
 * the wallpaper-colours switch only shows when [dynamicColorAvailable] (Android 12+). The Language row shows when there are [languages]
 * (language tags, in the order shown); [appLanguage] is the picked one, null for the phone's. With
 * [onBack] the top bar has a back arrow, as About's. Meter's
 * cards (2.0 Phases 72 and 78) are Classic, the Light arc and the strobe.
 */
@Composable
fun SettingsContent(
    settings: AppSettings?,
    appVersion: String,
    onOpenAbout: () -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation?) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onStringTickOffChange: (Boolean) -> Unit,
    onUnderMeterChange: (UnderMeter) -> Unit,
    onShowSignalBarsChange: (Boolean) -> Unit,
    onLensLookChange: (LensLook) -> Unit,
    onLeftHandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    languages: List<String> = emptyList(),
    appLanguage: String? = null,
    onAppLanguageChange: (String?) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    SettingsPage(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) {
        if (settings != null) {
            SettingsGroups(
                settings = settings,
                appVersion = appVersion,
                onOpenAbout = onOpenAbout,
                languages = languages,
                appLanguage = appLanguage,
                onAppLanguageChange = onAppLanguageChange,
                onA4Change = onA4Change,
                onNotationChange = onNotationChange,
                onInTunePrecisionChange = onInTunePrecisionChange,
                onThemeChange = onThemeChange,
                onDynamicColorChange = onDynamicColorChange,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onVibrateInTuneChange = onVibrateInTuneChange,
                onStringTickOffChange = onStringTickOffChange,
                onUnderMeterChange = onUnderMeterChange,
                onShowSignalBarsChange = onShowSignalBarsChange,
                onLensLookChange = onLensLookChange,
                onLeftHandedChange = onLeftHandedChange,
                dynamicColorAvailable = dynamicColorAvailable,
            )
        }
    }
}

/**
 * A page of the settings: a top bar with [title] and, with [onBack], a back arrow, then [content] in a
 * scrolling column. The page is see-through, so the app's background shows behind it.
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The page's size, for the look card grids' rule (three to a row on a large window at any font size).
        CompositionLocalProvider(LocalSettingsWindow provides DpSize(maxWidth, maxHeight)) {
            SettingsScaffold(title, onBack, content)
        }
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // A see-through container gives no content colour of its own.
        contentColor = MaterialTheme.colorScheme.onBackground,
        // safeDrawing also keeps clear of the camera cutout, which is at the side when the phone is turned.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // Every tab's top row (TopBar): the back arrow in the gear's place, the title in the middle, 8 dp down
            // and the arrow's outline 16 dp in, as the rows (Material's top bar until 2026-11-04, its title at the
            // start beside a bare arrow). It stands in the same centred column as the rows, so the arrow lines up
            // with them where the window is wider than the column (Phase 52: a tablet's title sat at the window's
            // edge, 100 to 300 dp from the rows). The bar is see-through.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
                contentAlignment = Alignment.TopCenter,
            ) {
                TopBar(
                    middle = { TopBarTitle(title) },
                    modifier = Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(start = TOP_BAR_MARGIN, end = TOP_BAR_MARGIN, top = TOP_BAR_TOP),
                    start = onBack?.let { back -> { BackButton(back, stringResource(R.string.settings_back), faceModifier = Modifier.testTag(SETTINGS_BACK_TAG)) } },
                )
            }
        },
    ) { innerPadding ->
        // The whole width scrolls, but the content stays in a centred column no wider than
        // CONTENT_MAX_WIDTH, so rows don't stretch across a tablet or a phone on its side.
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .fadeUnderTitle(scroll)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    // 24 dp at the bottom, so the last row ends above the frames some styles draw.
                    .padding(top = 8.dp, bottom = 24.dp),
                content = content,
            )
        }
    }
}

/**
 * The rows fade out as they pass under the page's fixed title (Phase 65): without it a row scrolled
 * half under the title was cut off in a hard line. The fade comes in over the first [TITLE_FADE] of
 * scrolling, so a page at its top shows every row whole, as before. Read while drawing, so scrolling
 * redraws only this; the page is drawn in a layer of its own for it.
 */
private fun Modifier.fadeUnderTitle(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val reach = TITLE_FADE.toPx()
            val amount = (scroll.value / reach).coerceIn(0f, 1f)
            if (amount > 0f) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - amount), 1f to Color.Black, endY = reach),
                    size = Size(size.width, reach),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/**
 * Every setting, in four short headed groups (2.0, Phase 75, as the first 2.0 mock-up draws them; Look first since
 * 2026-11-05): Look (the lens's look, what goes under the meter, light or dark with the wallpaper colours, and
 * left-handed charts), While tuning (what the tuner does while it listens), Tuning (which note it is, what counts as
 * in tune and the reference pitch) and Twangle (the app's language and one
 * row that opens the About page: version, promise, privacy and licences, so those long texts don't crowd the
 * settings). No lines inside or between the groups (the owner, 2026-11-05: they fell where a rule put them and looked
 * random; the headings and the room above them mark the groups, each setting's title the rest). Rows, including the
 * card grids, keep 16 dp from the column's edges. Four kinds of control and no more: the Meter cards, choice rows,
 * switches, and rows that open a list.
 */
@Composable
private fun SettingsGroups(
    settings: AppSettings,
    appVersion: String,
    onOpenAbout: () -> Unit,
    languages: List<String>,
    appLanguage: String?,
    onAppLanguageChange: (String?) -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation?) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onStringTickOffChange: (Boolean) -> Unit,
    onUnderMeterChange: (UnderMeter) -> Unit,
    onShowSignalBarsChange: (Boolean) -> Unit,
    onLensLookChange: (LensLook) -> Unit,
    onLeftHandedChange: (Boolean) -> Unit,
    dynamicColorAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
        // What the player sees first (the owner, 2026-11-05: the settings most players change come first, Tuning's big
        // slider last): the lens's cards, the one look a player picks (the owner's review, 2026-11-03), what goes under
        // the meter, then light or dark, then how the charts are drawn.
        SettingsGroup(title = stringResource(R.string.settings_group_look)) {
            Rows {
                // The lens's look (2.0, Phase 72), in place of 1.1's meter look, Calm or Bold glow, display and wave look.
                MeterPicker(
                    looks = LensLook.entries,
                    selected = settings.lensLook,
                    onSelect = onLensLookChange,
                    precision = settings.inTunePrecision,
                )
                // What the tuner shows under its meter (2.0, Phase 78): the word, or the cents big in its place.
                RadioGroupSetting(
                    title = stringResource(R.string.settings_under_meter_title),
                    options = UnderMeter.entries,
                    selected = settings.underMeter,
                    onSelect = onUnderMeterChange,
                ) { stringResource(underMeterLabel(it)) }
                // One row that opens the list, as Note names and Language: most players set it once, if ever (three
                // full rows until 2026-11-05).
                ChoiceDialogSetting(
                    title = stringResource(R.string.settings_theme_title),
                    options = ThemeMode.entries,
                    selected = settings.theme,
                    label = { AnnotatedString(stringResource(themeLabel(it))) },
                    onSelect = onThemeChange,
                    rowTag = THEME_SETTING_TAG,
                    optionTag = ::themeOptionTag,
                )
                // Android 12 and later only: older phones have no wallpaper colours to take.
                if (dynamicColorAvailable) {
                    SwitchSetting(
                        title = stringResource(R.string.settings_dynamic_color_title),
                        summary = stringResource(R.string.settings_dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                }
                // Mirrors the chord charts and the scale neck.
                SwitchSetting(
                    title = stringResource(R.string.settings_left_handed_title),
                    summary = stringResource(R.string.settings_left_handed_summary),
                    checked = settings.leftHanded,
                    onCheckedChange = onLeftHandedChange,
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_group_while_tuning)) {
            Rows {
                SwitchSetting(
                    title = stringResource(R.string.settings_keep_screen_on_title),
                    summary = stringResource(R.string.settings_keep_screen_on_summary),
                    checked = settings.keepScreenOn,
                    onCheckedChange = onKeepScreenOnChange,
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_vibrate_in_tune_title),
                    summary = stringResource(R.string.settings_vibrate_in_tune_summary),
                    checked = settings.vibrateInTune,
                    onCheckedChange = onVibrateInTuneChange,
                )
                // The beginner's help (2.0, Phase 71), off by default: 1.1's "Show tuning help" went, as the word always shows.
                SwitchSetting(
                    title = stringResource(R.string.settings_string_tick_off_title),
                    summary = stringResource(R.string.settings_string_tick_off_summary),
                    checked = settings.stringTickOff,
                    onCheckedChange = onStringTickOffChange,
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_show_signal_bars_title),
                    summary = stringResource(R.string.settings_show_signal_bars_summary),
                    checked = settings.showSignalBars,
                    onCheckedChange = onShowSignalBarsChange,
                )
            }
        }
        // Which note it is and what counts as in tune ("Pitch and notes" until Phase 75); the reference pitch last,
        // the setting most players never change.
        SettingsGroup(title = stringResource(R.string.settings_group_tuning)) {
            Rows {
                // Automatic (null) follows the app's language and shows what that gives. One row that opens the list
                // (five rows until 2026-11-05: hardly anyone changes it). Labels such as "Sharps: C, C♯, D …" set
                // their signs like the rest of the app, and TalkBack says their note names in words ("C sharp").
                val languageNotation = Notation.forLanguage(appLanguage())
                val speech = rememberNoteNameSpeech()
                val notationText: @Composable (Notation?) -> String = {
                    if (it == null) {
                        stringResource(R.string.settings_notation_automatic, notationSample(languageNotation))
                    } else {
                        stringResource(notationLabel(it))
                    }
                }
                ChoiceDialogSetting(
                    title = stringResource(R.string.settings_notation_title),
                    options = listOf(null) + Notation.entries,
                    selected = settings.notationChoice,
                    label = { musicText(notationText(it)) },
                    spoken = { notationText(it).let { text -> if (text.any(::isMusicSign)) speech.text(text) else null } },
                    onSelect = onNotationChange,
                    rowTag = NOTATION_SETTING_TAG,
                    optionTag = ::notationOptionTag,
                )
                // Relaxed or Precise is a yes or no, so a switch like the others (two choice rows, then a segmented
                // switch, until 2026-11-05): on means Precise.
                SwitchSetting(
                    title = stringResource(R.string.settings_precise_title),
                    summary = stringResource(R.string.settings_precise_summary),
                    checked = settings.inTunePrecision == InTunePrecision.PRECISE,
                    onCheckedChange = { onInTunePrecisionChange(if (it) InTunePrecision.PRECISE else InTunePrecision.RELAXED) },
                )
                A4Calibration(a4Hz = settings.a4Hz, onA4Change = onA4Change)
            }
        }
        // The app itself: its language and About ("App" until Phase 75).
        SettingsGroup(title = stringResource(R.string.settings_group_twangle)) {
            Rows {
                if (languages.isNotEmpty()) {
                    val names = remember(languages) { languages.associateWith(AppLanguages::nameOf) }
                    LanguageSetting(
                        languages = languages,
                        selected = appLanguage,
                        nameOf = { names.getValue(it) },
                        onSelect = onAppLanguageChange,
                    )
                }
                LinkSetting(
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.settings_about_link_summary, appVersion),
                    onClick = onOpenAbout,
                    modifier = Modifier.testTag(ABOUT_LINK_TAG),
                )
            }
        }
    }
}

/**
 * One group: its heading in the style's title face and main accent colour, marked as a heading so
 * TalkBack users can jump from group to group, then [content].
 */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = headingStyle(MaterialTheme.typography.titleLarge),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = ROW_INSET)
                .semantics { heading() },
        )
        content()
    }
}

/** Setting rows, 16 dp apart and 16 dp in from the column's edges. */
@Composable
private fun Rows(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_INSET),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Space between two groups, so each heading clearly starts a new one. */
private val GROUP_GAP = 32.dp

/** Test tag of the row that opens the About page (the Baseline Profile generator opens it). */
const val ABOUT_LINK_TAG = "settings_about"

/** Rows and headings keep this far from the column's edges. */
private val ROW_INSET = 16.dp

/** Wide enough for comfortable rows; on wider screens the settings stay centred at this width. */
private val CONTENT_MAX_WIDTH = 600.dp

/** How far under the fixed title the rows fade out while the page is scrolled. */
private val TITLE_FADE = 24.dp

/** The back arrow's face at the start of Settings' and About's top row, for UI tests (a resource id in the app). */
const val SETTINGS_BACK_TAG = "settingsBack"

private fun notationLabel(notation: Notation): Int = when (notation) {
    Notation.SHARPS -> R.string.settings_notation_sharps
    Notation.FLATS -> R.string.settings_notation_flats
    Notation.SOLFEGE -> R.string.settings_notation_solfege
    Notation.GERMAN -> R.string.settings_notation_german
}

/** A few names in [notation] to tell it apart, B's last: "C, C♯, D … B", "Do, Do♯, Re … Si", "C, C♯, D … H". */
@Composable
private fun notationSample(notation: Notation): String = stringResource(
    R.string.settings_notation_sample,
    notation.nameOf(PitchClass.C),
    notation.nameOf(PitchClass.C_SHARP),
    notation.nameOf(PitchClass.D),
    notation.nameOf(PitchClass.B),
)

/** Test tag of the Note names row in Settings. */
const val NOTATION_SETTING_TAG = "settings_notation"

/** Test tag of a choice in the Note names list: its notation's name, or "automatic". */
fun notationOptionTag(notation: Notation?): String = "notation:${notation?.name ?: "automatic"}"

/** Test tag of the Light or dark row in Settings. */
const val THEME_SETTING_TAG = "settings_theme"

/** Test tag of a choice in the Light or dark list: its mode's name. */
fun themeOptionTag(theme: ThemeMode): String = "theme:${theme.name}"

private fun underMeterLabel(choice: UnderMeter): Int = when (choice) {
    UnderMeter.WORDS -> R.string.settings_under_meter_words
    UnderMeter.CENTS -> R.string.settings_under_meter_cents
}

private fun themeLabel(theme: ThemeMode): Int = when (theme) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsContentPreview() {
    MaterialTheme {
        SettingsContent(
            AppSettings(a4Hz = 442f),
            appVersion = "0.9.0-beta",
            onOpenAbout = {},
            onA4Change = {},
            onNotationChange = {},
            onInTunePrecisionChange = {},
            onThemeChange = {},
            onDynamicColorChange = {},
            onKeepScreenOnChange = {},
            onVibrateInTuneChange = {},
            onStringTickOffChange = {},
            onUnderMeterChange = {},
            onShowSignalBarsChange = {},
            onLensLookChange = {},
            onLeftHandedChange = {},
        )
    }
}
