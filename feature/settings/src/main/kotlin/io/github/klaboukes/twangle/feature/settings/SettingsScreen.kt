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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.OneLineAutoSize
import io.github.klaboukes.twangle.core.designsystem.TwangleIconButton
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.feature.settings.components.A4Calibration
import io.github.klaboukes.twangle.feature.settings.components.LanguageSetting
import io.github.klaboukes.twangle.feature.settings.components.LinkSetting
import io.github.klaboukes.twangle.feature.settings.components.LocalSettingsWindow
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
 *
 * [intonationCheck] is the build's switch for the 12th-fret check (`ToolSwitches.intonationCheck`): off, the
 * While tuning group has no switch for its way in on the tuner screen.
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
    intonationCheck: Boolean = true,
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
        onShowSignalBarsChange = viewModel::setShowSignalBars,
        onFocusModeChange = viewModel::setFocusMode,
        onLeftHandedChange = viewModel::setLeftHanded,
        onShowIntonationCheckChange = if (intonationCheck) viewModel::setShowIntonationCheck else null,
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
 * load. The settings come in five headed groups, the last ending in a link to the About page (see [SettingsGroups]);
 * the wallpaper-colours switch only shows when [dynamicColorAvailable] (Android 12+). The Language row shows when there are [languages]
 * (language tags, in the order shown); [appLanguage] is the picked one, null for the phone's. With
 * [onBack] the top bar has a back arrow, as About's. The 12th-fret check's switch (its way in on the tuner screen,
 * 2.0 Phase 71) shows only with [onShowIntonationCheckChange], which a build without the check leaves out.
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
    onShowSignalBarsChange: (Boolean) -> Unit,
    onFocusModeChange: (Boolean) -> Unit,
    onLeftHandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onShowIntonationCheckChange: ((Boolean) -> Unit)? = null,
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
                onShowSignalBarsChange = onShowSignalBarsChange,
                onFocusModeChange = onFocusModeChange,
                onLeftHandedChange = onLeftHandedChange,
                onShowIntonationCheckChange = onShowIntonationCheckChange,
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

@OptIn(ExperimentalMaterial3Api::class)
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
            // The bar stands in the same centred column as the rows, so the title (and the back arrow)
            // line up with the headings where the window is wider than the column (Phase 52: a tablet's
            // title sat at the window's edge, 100 to 300 dp from the rows). The bar is see-through, so
            // narrowing it shows nothing new; where the column is the whole width it is as before.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter,
            ) {
                TopAppBar(
                    title = {
                        // Set smaller where it doesn't fit the bar: in 1.1 Bengali's About title in Futuristic,
                        // Orbitron's "Twangle" beside Bengali letters, made a line taller than the bar at 2× text
                        // (the overnight run of 2026-10-30). It still wraps where it is too wide, as French "À propos
                        // de Twangle" does at 2× text, in two lines that fit; one line never wrapped ran off the bar.
                        val style = LocalTextStyle.current
                        Text(title, autoSize = remember(style.fontSize) { OneLineAutoSize(style.fontSize, TITLE_SMALLEST) })
                    },
                    modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        if (onBack != null) {
                            TwangleIconButton(onClick = onBack) {
                                Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
                            }
                        }
                    },
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
 * Every setting, in five headed groups: Pitch and notes (which note it is and what counts as in
 * tune; not "Tuning", which the navigation bar's Instrument item was called until 2026-10-16), Look (the
 * wallpaper colours, then light or dark; the style picker went with 1.1's six styles in Phase 67), Tuner
 * screen (the meter's look and in-tune effect, what shows below the note and the layout on its side), While tuning (what the tuner does while
 * it listens) and App (the app's language and one row that opens the About page: version,
 * promise, privacy and licences, so those long texts don't crowd the settings). A line divides two
 * subjects; a setting that belongs to the one above stays with it. Rows, including the card grids, keep
 * 16 dp from the column's edges.
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
    onShowSignalBarsChange: (Boolean) -> Unit,
    onFocusModeChange: (Boolean) -> Unit,
    onLeftHandedChange: (Boolean) -> Unit,
    onShowIntonationCheckChange: ((Boolean) -> Unit)?,
    dynamicColorAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
        SettingsGroup(title = stringResource(R.string.settings_group_pitch)) {
            Rows {
                A4Calibration(a4Hz = settings.a4Hz, onA4Change = onA4Change)
                HorizontalDivider()
                // Automatic (null) follows the app's language and shows what that gives.
                val languageNotation = Notation.forLanguage(appLanguage())
                RadioGroupSetting(
                    title = stringResource(R.string.settings_notation_title),
                    options = listOf(null) + Notation.entries,
                    selected = settings.notationChoice,
                    onSelect = onNotationChange,
                ) {
                    if (it == null) {
                        stringResource(R.string.settings_notation_automatic, notationSample(languageNotation))
                    } else {
                        stringResource(notationLabel(it))
                    }
                }
                HorizontalDivider()
                RadioGroupSetting(
                    title = stringResource(R.string.settings_in_tune_precision_title),
                    options = InTunePrecision.entries,
                    selected = settings.inTunePrecision,
                    onSelect = onInTunePrecisionChange,
                ) { stringResource(inTunePrecisionLabel(it)) }
                HorizontalDivider()
                // With Note names, the other setting that changes what the chord charts and the scale neck show.
                SwitchSetting(
                    title = stringResource(R.string.settings_left_handed_title),
                    summary = stringResource(R.string.settings_left_handed_summary),
                    checked = settings.leftHanded,
                    onCheckedChange = onLeftHandedChange,
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_group_look)) {
            Rows {
                // Android 12 and later only: older phones have no wallpaper colours to take.
                if (dynamicColorAvailable) {
                    SwitchSetting(
                        title = stringResource(R.string.settings_dynamic_color_title),
                        summary = stringResource(R.string.settings_dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                    HorizontalDivider()
                }
                RadioGroupSetting(
                    title = stringResource(R.string.settings_theme_title),
                    options = ThemeMode.entries,
                    selected = settings.theme,
                    onSelect = onThemeChange,
                ) { stringResource(themeLabel(it)) }
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_group_tuner_screen)) {
            Rows {
                // 1.1's meter look, Calm or Bold glow, display and wave look went in Phase 67; the lens's Meter comes in 72.
                SwitchSetting(
                    title = stringResource(R.string.settings_focus_mode_title),
                    summary = stringResource(R.string.settings_focus_mode_summary),
                    checked = settings.focusMode,
                    onCheckedChange = onFocusModeChange,
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
                // The 12th-fret check's way in on the tuner screen (2.0, Phase 71), off by default; none in a build without the check.
                if (onShowIntonationCheckChange != null) {
                    SwitchSetting(
                        title = stringResource(R.string.settings_intonation_check_title),
                        summary = stringResource(R.string.settings_intonation_check_summary),
                        checked = settings.showIntonationCheck,
                        onCheckedChange = onShowIntonationCheckChange,
                    )
                }
                SwitchSetting(
                    title = stringResource(R.string.settings_show_signal_bars_title),
                    summary = stringResource(R.string.settings_show_signal_bars_summary),
                    checked = settings.showSignalBars,
                    onCheckedChange = onShowSignalBarsChange,
                )
            }
        }
        // The app itself: its language and About (until Phase 63 the only rows without a heading; "Show me around" went with the tour in Phase 68).
        SettingsGroup(title = stringResource(R.string.settings_group_app)) {
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

/** The top bar's title is set no smaller than this where it doesn't fit. */
private val TITLE_SMALLEST = 14.sp

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

private fun inTunePrecisionLabel(precision: InTunePrecision): Int = when (precision) {
    InTunePrecision.RELAXED -> R.string.settings_in_tune_precision_relaxed
    InTunePrecision.PRECISE -> R.string.settings_in_tune_precision_precise
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
            onShowSignalBarsChange = {},
            onFocusModeChange = {},
            onLeftHandedChange = {},
        )
    }
}
