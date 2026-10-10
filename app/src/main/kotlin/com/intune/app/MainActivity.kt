package com.intune.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intune.app.ui.theme.InTuneTheme
import com.intune.app.ui.theme.useDarkTheme
import com.intune.core.settings.AppSettings
import com.intune.feature.settings.SettingsScreen
import com.intune.feature.tuner.TunerScreen

/** The two screens. Navigation is just this one value; the back button leads from settings to the tuner. */
private enum class Screen { TUNER, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as InTuneApplication).container
        setContent {
            // Nothing is drawn for the few milliseconds until the settings are read, so the app
            // never flashes the wrong theme.
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { InTuneApp(container, it) }
        }
    }

    /** The app in the user's theme, showing the tuner or the settings screen. */
    @Composable
    private fun InTuneApp(container: AppContainer, settings: AppSettings) {
        val darkTheme = settings.theme.useDarkTheme(isSystemInDarkTheme())

        // Status and navigation bar icons follow the app's theme, not just the phone's.
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
            )
            onDispose {}
        }

        InTuneTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
            var screen by rememberSaveable { mutableStateOf(Screen.TUNER) }
            // Keeps each screen's saved UI state (e.g. the mic permission status) while the other one is shown.
            val saveableState = rememberSaveableStateHolder()
            saveableState.SaveableStateProvider(screen.name) {
                when (screen) {
                    Screen.TUNER -> TunerScreen(
                        audioRecorder = container.audioRecorder,
                        settingsRepository = container.settingsRepository,
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.TUNER }
                        SettingsScreen(
                            settingsRepository = container.settingsRepository,
                            onBack = { screen = Screen.TUNER },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        // The scrims enableEdgeToEdge() uses by default behind 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
