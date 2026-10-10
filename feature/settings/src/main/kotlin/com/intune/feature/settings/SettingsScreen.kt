package com.intune.feature.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intune.core.dsp.music.Notation
import com.intune.core.settings.AppSettings
import com.intune.core.settings.SettingsRepository
import com.intune.core.settings.ThemeMode
import com.intune.feature.settings.components.A4Calibration
import com.intune.feature.settings.components.RadioGroupSetting
import com.intune.feature.settings.components.SwitchSetting

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
        onThemeChange = viewModel::setTheme,
        onDynamicColorChange = viewModel::setDynamicColor,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onVibrateInTuneChange = viewModel::setVibrateInTune,
        modifier = modifier,
    )
}

/**
 * The settings screen body. Stateless, so previews and UI tests can drive it. Empty while [settings]
 * load. The wallpaper-colours switch only shows when [dynamicColorAvailable] (Android 12+).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppSettings?,
    onBack: () -> Unit,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // safeDrawing also keeps clear of the camera cutout, which is at the side when the phone is turned.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
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
                SettingsList(
                    settings = settings,
                    onA4Change = onA4Change,
                    onNotationChange = onNotationChange,
                    onThemeChange = onThemeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    onVibrateInTuneChange = onVibrateInTuneChange,
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

/** Every setting, top to bottom. */
@Composable
private fun SettingsList(
    settings: AppSettings,
    onA4Change: (Float) -> Unit,
    onNotationChange: (Notation) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVibrateInTuneChange: (Boolean) -> Unit,
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
            title = stringResource(R.string.settings_theme_title),
            options = ThemeMode.entries,
            selected = settings.theme,
            onSelect = onThemeChange,
        ) { stringResource(themeLabel(it)) }
        if (dynamicColorAvailable) {
            SwitchSetting(
                title = stringResource(R.string.settings_dynamic_color_title),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )
        }
        HorizontalDivider()
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
    }
}

/** Wide enough for comfortable rows; on wider screens the settings stay centred at this width. */
private val CONTENT_MAX_WIDTH = 600.dp

private fun notationLabel(notation: Notation): Int = when (notation) {
    Notation.SHARPS -> R.string.settings_notation_sharps
    Notation.FLATS -> R.string.settings_notation_flats
    Notation.SOLFEGE -> R.string.settings_notation_solfege
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
            onThemeChange = {},
            onDynamicColorChange = {},
            onKeepScreenOnChange = {},
            onVibrateInTuneChange = {},
        )
    }
}
