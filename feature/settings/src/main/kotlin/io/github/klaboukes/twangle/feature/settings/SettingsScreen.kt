package io.github.klaboukes.twangle.feature.settings

import android.os.Build
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.designsystem.StylePicker
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.settings.components.A4Calibration
import io.github.klaboukes.twangle.feature.settings.components.LinkSetting
import io.github.klaboukes.twangle.feature.settings.components.RadioGroupSetting
import io.github.klaboukes.twangle.feature.settings.components.SwitchSetting
import io.github.klaboukes.twangle.feature.settings.components.WaveLookPicker

/**
 * Settings entry point. Every change is saved straight away; [onBack] returns to the tuner and
 * [onOpenAbout] opens the About page. [appVersion] is the app's version name, shown on the About link.
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    appVersion: String,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        appVersion = appVersion,
        onBack = onBack,
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
        onWaveLookChange = viewModel::setWaveLook,
        onWaveBreathesInTuneChange = viewModel::setWaveBreathesInTune,
        onKeepTwoPaneLandscapeChange = viewModel::setKeepTwoPaneLandscape,
        modifier = modifier,
    )
}

/**
 * The settings screen body. Stateless, so previews and UI tests can drive it. Empty while [settings]
 * load. The settings come in three headed groups, then a link to the About page (see [SettingsGroups]);
 * the wallpaper-colours switch only shows in the contemporary style when [dynamicColorAvailable]
 * (Android 12+), and the wave looks (and whether the wave breathes in tune) only while the wave is the display.
 */
@Composable
fun SettingsContent(
    settings: AppSettings?,
    appVersion: String,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onStyleChange: (VisualStyle) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onShowTuneHintChange: (Boolean) -> Unit,
    onShowSignalBarsChange: (Boolean) -> Unit,
    onDisplayChange: (TunerDisplay) -> Unit,
    onWaveLookChange: (WaveLook) -> Unit,
    onWaveBreathesInTuneChange: (Boolean) -> Unit,
    onKeepTwoPaneLandscapeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    SettingsPage(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) {
        if (settings != null) {
            SettingsGroups(
                settings = settings,
                appVersion = appVersion,
                onOpenAbout = onOpenAbout,
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
                onWaveLookChange = onWaveLookChange,
                onWaveBreathesInTuneChange = onWaveBreathesInTuneChange,
                onKeepTwoPaneLandscapeChange = onKeepTwoPaneLandscapeChange,
                dynamicColorAvailable = dynamicColorAvailable,
            )
        }
    }
}

/**
 * A page of the settings: a top bar with [title] and a back arrow ([onBack]), then [content] in a
 * scrolling column. The page is see-through, so the style's background shows behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
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
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
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
 * Every setting, in three headed groups: Tuning (which note it is and what counts as in tune), Look
 * (how the app looks, how far off a note is shown and the layout on its side) and While tuning (what the tuner does while it listens),
 * followed by one row that opens the About page (version, promise, privacy and licences), so those
 * long texts don't crowd the settings.
 * Rows keep 16 dp from the column's edges; only the style cards run to the edges, so their row
 * visibly continues off-screen.
 */
@Composable
private fun SettingsGroups(
    settings: AppSettings,
    appVersion: String,
    onOpenAbout: () -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onStyleChange: (VisualStyle) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onShowTuneHintChange: (Boolean) -> Unit,
    onShowSignalBarsChange: (Boolean) -> Unit,
    onDisplayChange: (TunerDisplay) -> Unit,
    onWaveLookChange: (WaveLook) -> Unit,
    onWaveBreathesInTuneChange: (Boolean) -> Unit,
    onKeepTwoPaneLandscapeChange: (Boolean) -> Unit,
    dynamicColorAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
        SettingsGroup(title = stringResource(R.string.settings_group_tuning)) {
            Rows {
                A4Calibration(a4Hz = settings.a4Hz, onA4Change = onA4Change)
                HorizontalDivider()
                RadioGroupSetting(
                    title = stringResource(R.string.settings_notation_title),
                    options = Notation.entries,
                    selected = settings.notation,
                    onSelect = onNotationChange,
                ) { stringResource(notationLabel(it)) }
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
            Text(
                text = stringResource(R.string.settings_style_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = ROW_INSET),
            )
            StylePicker(selected = settings.style, onSelect = onStyleChange, darkTheme = MaterialTheme.styleTokens.dark)
            Rows {
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
                        breathesInTune = settings.waveBreathesInTune,
                    ) { stringResource(waveLookLabel(it)) }
                    // Temporary, to compare both on the phone: the one not picked goes, with this choice.
                    RadioGroupSetting(
                        title = stringResource(R.string.settings_wave_in_tune_title),
                        options = listOf(true, false),
                        selected = settings.waveBreathesInTune,
                        onSelect = onWaveBreathesInTuneChange,
                    ) { breathes ->
                        stringResource(if (breathes) R.string.settings_wave_in_tune_breathe else R.string.settings_wave_in_tune_hold_still)
                    }
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

/** Rows and headings keep this far from the column's edges. */
private val ROW_INSET = 16.dp

/** Wide enough for comfortable rows; on wider screens the settings stay centred at this width. */
private val CONTENT_MAX_WIDTH = 600.dp

private fun notationLabel(notation: Notation): Int = when (notation) {
    Notation.SHARPS -> R.string.settings_notation_sharps
    Notation.FLATS -> R.string.settings_notation_flats
    Notation.SOLFEGE -> R.string.settings_notation_solfege
}

private fun inTunePrecisionLabel(precision: InTunePrecision): Int = when (precision) {
    InTunePrecision.RELAXED -> R.string.settings_in_tune_precision_relaxed
    InTunePrecision.PRECISE -> R.string.settings_in_tune_precision_precise
}

private fun displayLabel(display: TunerDisplay): Int = when (display) {
    TunerDisplay.NEEDLE -> R.string.settings_display_needle
    TunerDisplay.WAVE -> R.string.settings_display_wave
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
            onWaveLookChange = {},
            onWaveBreathesInTuneChange = {},
            onKeepTwoPaneLandscapeChange = {},
        )
    }
}
