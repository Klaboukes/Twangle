package io.github.klaboukes.twangle.feature.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.settings.components.A4Calibration
import io.github.klaboukes.twangle.feature.settings.components.LanguageSetting
import io.github.klaboukes.twangle.feature.settings.components.LinkSetting
import io.github.klaboukes.twangle.feature.settings.components.MeterLookPicker
import io.github.klaboukes.twangle.feature.settings.components.RadioGroupSetting
import io.github.klaboukes.twangle.feature.settings.components.StylePicker
import io.github.klaboukes.twangle.feature.settings.components.SwitchSetting
import io.github.klaboukes.twangle.feature.settings.components.WaveLookPicker

/**
 * Settings entry point, one of the app's three tabs (no back arrow: the navigation bar and Back
 * lead away). Every change is saved straight away; [onOpenAbout] opens the About page. [appVersion] is the app's version name, shown on the About link.
 * With [onShowTour], a "Show me around" row starts the first-start tour again (on the tuner).
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
    onShowTour: (() -> Unit)? = null,
    languages: List<String> = emptyList(),
    onLanguageChange: () -> Unit = {},
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
        onStyleChange = viewModel::setStyle,
        onThemeChange = viewModel::setTheme,
        onDynamicColorChange = viewModel::setDynamicColor,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onVibrateInTuneChange = viewModel::setVibrateInTune,
        onShowTuneHintChange = viewModel::setShowTuneHint,
        onShowSignalBarsChange = viewModel::setShowSignalBars,
        onDisplayChange = viewModel::setDisplay,
        onMeterLookChange = viewModel::setMeterLook,
        onInTuneEffectChange = viewModel::setInTuneEffect,
        onWaveLookChange = viewModel::setWaveLook,
        onKeepTwoPaneLandscapeChange = viewModel::setKeepTwoPaneLandscape,
        modifier = modifier,
        onShowTour = onShowTour,
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
 * load. The settings come in four headed groups, then a link to the About page (see [SettingsGroups]);
 * the wallpaper-colours switch only shows in the contemporary style when [dynamicColorAvailable]
 * (Android 12+); the meter looks always show, and the wave looks only while the wave is the
 * display. The Language row shows when there are [languages]
 * (language tags, in the order shown); [appLanguage] is the picked one, null for the phone's.
 */
@Composable
fun SettingsContent(
    settings: AppSettings?,
    appVersion: String,
    onOpenAbout: () -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation?) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onStyleChange: (VisualStyle) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onShowTuneHintChange: (Boolean) -> Unit,
    onShowSignalBarsChange: (Boolean) -> Unit,
    onDisplayChange: (TunerDisplay) -> Unit,
    onMeterLookChange: (MeterLook) -> Unit,
    onInTuneEffectChange: (InTuneEffect) -> Unit,
    onWaveLookChange: (WaveLook) -> Unit,
    onKeepTwoPaneLandscapeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    onShowTour: (() -> Unit)? = null,
    languages: List<String> = emptyList(),
    appLanguage: String? = null,
    onAppLanguageChange: (String?) -> Unit = {},
) {
    SettingsPage(title = stringResource(R.string.settings_title), onBack = null, modifier = modifier) {
        if (settings != null) {
            SettingsGroups(
                settings = settings,
                appVersion = appVersion,
                onOpenAbout = onOpenAbout,
                onShowTour = onShowTour,
                languages = languages,
                appLanguage = appLanguage,
                onAppLanguageChange = onAppLanguageChange,
                onA4Change = onA4Change,
                onNotationChange = onNotationChange,
                onInTunePrecisionChange = onInTunePrecisionChange,
                onStyleChange = onStyleChange,
                onThemeChange = onThemeChange,
                onDynamicColorChange = onDynamicColorChange,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onVibrateInTuneChange = onVibrateInTuneChange,
                onShowTuneHintChange = onShowTuneHintChange,
                onShowSignalBarsChange = onShowSignalBarsChange,
                onDisplayChange = onDisplayChange,
                onMeterLookChange = onMeterLookChange,
                onInTuneEffectChange = onInTuneEffectChange,
                onWaveLookChange = onWaveLookChange,
                onKeepTwoPaneLandscapeChange = onKeepTwoPaneLandscapeChange,
                dynamicColorAvailable = dynamicColorAvailable,
            )
        }
    }
}

/**
 * A page of the settings: a top bar with [title] and, with [onBack], a back arrow, then [content] in a
 * scrolling column. The page is see-through, so the style's background shows behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // A see-through container gives no content colour of its own.
        contentColor = MaterialTheme.colorScheme.onBackground,
        // safeDrawing also keeps clear of the camera cutout, which is at the side when the phone is turned.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // The whole width scrolls, but the content stays in a centred column no wider than
        // CONTENT_MAX_WIDTH, so rows don't stretch across a tablet or a phone on its side.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
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
 * Every setting, in four headed groups: Pitch and notes (which note it is and what counts as in
 * tune; not "Tuning", which the navigation bar's item for picking a tuning is called), Look (the
 * style, light or dark and the wallpaper colours), Tuner screen (the meter's look and in-tune effect,
 * what shows below the note and the layout on its side) and While tuning (what the tuner does while
 * it listens), followed by the app's language,
 * the tour and one row that opens the About page (version, promise, privacy and licences), so those
 * long texts don't crowd the settings. Rows, including the card grids, keep 16 dp from the column's edges.
 */
@Composable
private fun SettingsGroups(
    settings: AppSettings,
    appVersion: String,
    onOpenAbout: () -> Unit,
    onShowTour: (() -> Unit)?,
    languages: List<String>,
    appLanguage: String?,
    onAppLanguageChange: (String?) -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation?) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onStyleChange: (VisualStyle) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onShowTuneHintChange: (Boolean) -> Unit,
    onShowSignalBarsChange: (Boolean) -> Unit,
    onDisplayChange: (TunerDisplay) -> Unit,
    onMeterLookChange: (MeterLook) -> Unit,
    onInTuneEffectChange: (InTuneEffect) -> Unit,
    onWaveLookChange: (WaveLook) -> Unit,
    onKeepTwoPaneLandscapeChange: (Boolean) -> Unit,
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
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_group_look)) {
            Rows {
                StylePicker(
                    title = stringResource(R.string.settings_style_title),
                    selected = settings.style,
                    onSelect = onStyleChange,
                )
                HorizontalDivider()
                RadioGroupSetting(
                    title = stringResource(R.string.settings_theme_title),
                    options = ThemeMode.entries,
                    selected = settings.theme,
                    onSelect = onThemeChange,
                ) { stringResource(themeLabel(it)) }
                if (dynamicColorAvailable && settings.style == VisualStyle.CONTEMPORARY) {
                    SwitchSetting(
                        title = stringResource(R.string.settings_dynamic_color_title),
                        summary = stringResource(R.string.settings_dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                }
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_group_tuner_screen)) {
            Rows {
                // The meter sits above the note with both displays, so its looks come first.
                MeterLookPicker(
                    title = stringResource(R.string.settings_meter_look_title),
                    selected = settings.meterLook,
                    onSelect = onMeterLookChange,
                    effect = settings.inTuneEffect,
                ) { stringResource(meterLookLabel(it)) }
                RadioGroupSetting(
                    title = stringResource(R.string.settings_in_tune_effect_title),
                    summary = stringResource(R.string.settings_in_tune_effect_summary),
                    options = InTuneEffect.entries,
                    selected = settings.inTuneEffect,
                    onSelect = onInTuneEffectChange,
                ) { stringResource(inTuneEffectLabel(it)) }
                HorizontalDivider()
                RadioGroupSetting(
                    title = stringResource(R.string.settings_display_title),
                    options = TunerDisplay.entries,
                    selected = settings.display,
                    onSelect = onDisplayChange,
                ) { stringResource(displayLabel(it)) }
                if (settings.display == TunerDisplay.WAVE) {
                    WaveLookPicker(
                        title = stringResource(R.string.settings_wave_look_title),
                        selected = settings.waveLook,
                        onSelect = onWaveLookChange,
                    ) { stringResource(waveLookLabel(it)) }
                }
                HorizontalDivider()
                SwitchSetting(
                    title = stringResource(R.string.settings_keep_two_pane_landscape_title),
                    summary = stringResource(R.string.settings_keep_two_pane_landscape_summary),
                    checked = settings.keepTwoPaneLandscape,
                    onCheckedChange = onKeepTwoPaneLandscapeChange,
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
                SwitchSetting(
                    title = stringResource(R.string.settings_show_tune_hint_title),
                    summary = stringResource(R.string.settings_show_tune_hint_summary),
                    checked = settings.showTuneHint,
                    onCheckedChange = onShowTuneHintChange,
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_show_signal_bars_title),
                    summary = stringResource(R.string.settings_show_signal_bars_summary),
                    checked = settings.showSignalBars,
                    onCheckedChange = onShowSignalBarsChange,
                )
            }
        }
        Rows {
            HorizontalDivider()
            if (languages.isNotEmpty()) {
                val names = remember(languages) { languages.associateWith(AppLanguages::nameOf) }
                LanguageSetting(
                    languages = languages,
                    selected = appLanguage,
                    nameOf = { names.getValue(it) },
                    onSelect = onAppLanguageChange,
                )
            }
            if (onShowTour != null) {
                LinkSetting(
                    title = stringResource(R.string.settings_show_tour_title),
                    summary = stringResource(R.string.settings_show_tour_summary),
                    onClick = onShowTour,
                    modifier = Modifier.testTag(SHOW_TOUR_TAG),
                )
            }
            LinkSetting(
                title = stringResource(R.string.about_title),
                summary = stringResource(R.string.settings_about_link_summary, appVersion),
                onClick = onOpenAbout,
            )
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
            style = MaterialTheme.typography.titleLarge,
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

/** Test tag of the "Show me around" row, which starts the first-start tour again. */
const val SHOW_TOUR_TAG = "settings_show_tour"

/** Rows and headings keep this far from the column's edges. */
private val ROW_INSET = 16.dp

/** Wide enough for comfortable rows; on wider screens the settings stay centred at this width. */
private val CONTENT_MAX_WIDTH = 600.dp

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

private fun displayLabel(display: TunerDisplay): Int = when (display) {
    TunerDisplay.NEEDLE -> R.string.settings_display_numbers
    TunerDisplay.WAVE -> R.string.settings_display_wave
}

private fun meterLookLabel(look: MeterLook): Int = when (look) {
    MeterLook.CLASSIC -> R.string.settings_meter_look_classic
    MeterLook.LIGHT_ARC -> R.string.settings_meter_look_light_arc
    MeterLook.SPIRIT_LEVEL -> R.string.settings_meter_look_spirit_level
    MeterLook.ECLIPSE -> R.string.settings_meter_look_eclipse
    MeterLook.ARROWS -> R.string.settings_meter_look_arrows
    MeterLook.LIGHT_BARS -> R.string.settings_meter_look_light_bars
}

private fun inTuneEffectLabel(effect: InTuneEffect): Int = when (effect) {
    InTuneEffect.CALM -> R.string.settings_in_tune_effect_calm
    InTuneEffect.BOLD -> R.string.settings_in_tune_effect_bold
}

private fun waveLookLabel(look: WaveLook): Int = when (look) {
    WaveLook.STROBE -> R.string.settings_wave_look_strobe
    WaveLook.RIBBON -> R.string.settings_wave_look_ribbon
    WaveLook.DOTS -> R.string.settings_wave_look_dots
    WaveLook.FIREFLIES -> R.string.settings_wave_look_fireflies
    WaveLook.LOOP -> R.string.settings_wave_look_loop
    WaveLook.RIPPLES -> R.string.settings_wave_look_ripples
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
            onKeepTwoPaneLandscapeChange = {},
        )
    }
}
