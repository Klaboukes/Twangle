package com.intune.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.intune.app.ui.theme.InTuneTheme
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
            InTuneTheme {
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
    }
}
