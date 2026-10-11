package io.github.klaboukes.twangle.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.settings.components.A4Calibration
import io.github.klaboukes.twangle.feature.settings.components.RadioGroupSetting
import io.github.klaboukes.twangle.feature.settings.components.SwitchSetting
import io.github.klaboukes.twangle.feature.settings.components.WaveLookPicker

/** Settings entry point. Every change is saved straight away; [onBack] returns to the tuner. */
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onA4Change = viewModel::setA4,
        onNotationChange = viewModel::setNotation,
        onInTunePrecisionChange = viewModel::setInTunePrecision,
        onStyleChange = viewModel::setStyle,
        onThemeChange = viewModel::setTheme,
        onDynamicColorChange = viewModel::setDynamicColor,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onVibrateInTuneChange = viewModel::setVibrateInTune,
        onShowTuneHintChange = viewModel::setShowTuneHint,
        onShowWavesChange = viewModel::setShowWaves,
        onWaveLookChange = viewModel::setWaveLook,
        modifier = modifier,
    )
}

/**
 * The settings screen body. Stateless, so previews and UI tests can drive it. Empty while [settings]
 * load. It opens with the style cards; the wallpaper-colours switch only shows in the contemporary
 * style when [dynamicColorAvailable] (Android 12+), and the wave looks only while the wave view is on. The screen is see-through, so the style's
 * background shows behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppSettings?,
    onBack: () -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onStyleChange: (VisualStyle) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onShowTuneHintChange: (Boolean) -> Unit,
    onShowWavesChange: (Boolean) -> Unit,
    onWaveLookChange: (WaveLook) -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
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
                title = { Text(stringResource(R.string.settings_title)) },
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
        if (settings != null) {
            // The whole width scrolls, but the settings stay in a centred column no wider than
            // CONTENT_MAX_WIDTH, so rows don't stretch across a tablet or a phone on its side.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StyleSection(
                    selected = settings.style,
                    onSelect = onStyleChange,
                    modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(top = 8.dp),
                )
                SettingsList(
                    settings = settings,
                    onA4Change = onA4Change,
                    onNotationChange = onNotationChange,
                    onInTunePrecisionChange = onInTunePrecisionChange,
                    onThemeChange = onThemeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    onVibrateInTuneChange = onVibrateInTuneChange,
                    onShowTuneHintChange = onShowTuneHintChange,
                    onShowWavesChange = onShowWavesChange,
                    onWaveLookChange = onWaveLookChange,
                    dynamicColorAvailable = dynamicColorAvailable,
                    modifier = Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * "Style" and the row of style cards. The cards run to the column's edges, so the row visibly
 * continues off-screen.
 */
@Composable
private fun StyleSection(selected: VisualStyle, onSelect: (VisualStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.settings_style_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        StylePicker(selected = selected, onSelect = onSelect, darkTheme = MaterialTheme.styleTokens.dark)
    }
}

/** Every setting under the styles, top to bottom. */
@Composable
private fun SettingsList(
    settings: AppSettings,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation) -> Unit,
    onInTunePrecisionChange: (InTunePrecision) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    onShowTuneHintChange: (Boolean) -> Unit,
    onShowWavesChange: (Boolean) -> Unit,
    onWaveLookChange: (WaveLook) -> Unit,
    dynamicColorAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
        SwitchSetting(
            title = stringResource(R.string.settings_show_waves_title),
            summary = stringResource(R.string.settings_show_waves_summary),
            checked = settings.showWaves,
            onCheckedChange = onShowWavesChange,
        )
        if (settings.showWaves) {
            WaveLookPicker(
                title = stringResource(R.string.settings_wave_look_title),
                selected = settings.waveLook,
                onSelect = onWaveLookChange,
            ) { stringResource(waveLookLabel(it)) }
        }
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
    }
}

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

private fun waveLookLabel(look: WaveLook): Int = when (look) {
    WaveLook.RIBBON -> R.string.settings_wave_look_ribbon
    WaveLook.STRIPES -> R.string.settings_wave_look_stripes
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
            onBack = {},
            onA4Change = {},
            onNotationChange = {},
            onInTunePrecisionChange = {},
            onStyleChange = {},
            onThemeChange = {},
            onDynamicColorChange = {},
            onKeepScreenOnChange = {},
            onVibrateInTuneChange = {},
            onShowTuneHintChange = {},
            onShowWavesChange = {},
            onWaveLookChange = {},
        )
    }
}
