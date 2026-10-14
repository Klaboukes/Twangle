package io.github.klaboukes.twangle.app

import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.useDarkTheme
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.feature.settings.AboutScreen
import io.github.klaboukes.twangle.feature.settings.SettingsScreen
import io.github.klaboukes.twangle.feature.tuner.TunerScreen
import io.github.klaboukes.twangle.feature.tuner.editor.TuningEditorScreen

/**
 * The four screens. Navigation is just this one value; the back button leads from About to settings,
 * and from settings and the tuning editor to the tuner.
 */
private enum class Screen { TUNER, SETTINGS, ABOUT, TUNING_EDITOR }

class MainActivity : ComponentActivity() {
    /** True once the first frame with the user's settings has been composed; until then the opening screen stays up. */
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The opening screen (Theme.Twangle.Starting): the icon on gold, or on dark brown in dark mode.
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TwangleApplication).container
        setContent {
            // Nothing is drawn for the few milliseconds until the settings are read, so the app
            // never flashes the wrong theme. The opening screen covers that gap.
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let {
                SideEffect { ready = true }
                TwangleApp(container, it)
            }
        }
    }

    /** The app in the user's style and theme, showing the tuner, settings, About or the tuning editor over the style's background. */
    @Composable
    private fun TwangleApp(container: AppContainer, settings: AppSettings) {
        val darkTheme = settings.theme.useDarkTheme(isSystemInDarkTheme())

        // Status and navigation bar icons follow the app's theme, not just the phone's.
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
            )
            onDispose {}
        }

        TwangleTheme(style = settings.style, darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
            Box(Modifier.fillMaxSize()) {
                var screen by rememberSaveable { mutableStateOf(Screen.TUNER) }
                // What the tuning editor works on: an instrument, and a custom tuning's id or null for a new one.
                var editorInstrumentId by rememberSaveable { mutableStateOf(Instruments.DEFAULT.id) }
                var editorTuningId by rememberSaveable { mutableStateOf<String?>(null) }
                // Settings and About get the quiet version of the style's background.
                StyleBackdrop(Modifier.fillMaxSize(), quiet = screen != Screen.TUNER)
                // Keeps each screen's saved UI state (e.g. the mic permission status) while the other one is shown.
                val saveableState = rememberSaveableStateHolder()
                saveableState.SaveableStateProvider(screen.name) {
                    when (screen) {
                        Screen.TUNER -> {
                            // The volume keys set the reference tone's loudness (the media volume)
                            // while the tuner is shown, also when no tone sounds.
                            DisposableEffect(Unit) {
                                volumeControlStream = AudioManager.STREAM_MUSIC
                                onDispose { volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE }
                            }
                            TunerScreen(
                                audioRecorder = container.audioRecorder,
                                settingsRepository = container.settingsRepository,
                                tonePlayer = container.tonePlayer,
                                onOpenSettings = { screen = Screen.SETTINGS },
                                savedMode = settings.mode,
                                savedStringLockTipSeen = settings.stringLockTipSeen,
                                savedKeepTwoPaneLandscape = settings.keepTwoPaneLandscape,
                                savedInstrument = settings.instrument,
                                savedCustomTunings = settings.customTunings,
                                onOpenTuningEditor = { instrument, customTuningId ->
                                    editorInstrumentId = instrument.id
                                    editorTuningId = customTuningId
                                    screen = Screen.TUNING_EDITOR
                                },
                                // Lets UI Automator (the Baseline Profile generator) find the tuner's test tags.
                                modifier = Modifier.semantics { testTagsAsResourceId = true },
                            )
                        }
                        Screen.SETTINGS -> {
                            BackHandler { screen = Screen.TUNER }
                            SettingsScreen(
                                settingsRepository = container.settingsRepository,
                                appVersion = BuildConfig.VERSION_NAME,
                                onBack = { screen = Screen.TUNER },
                                onOpenAbout = { screen = Screen.ABOUT },
                                // Lets UI Automator (the meter frame-time benchmark) find the meter cards.
                                modifier = Modifier.semantics { testTagsAsResourceId = true },
                            )
                        }
                        Screen.ABOUT -> {
                            BackHandler { screen = Screen.SETTINGS }
                            AboutScreen(appVersion = BuildConfig.VERSION_NAME, onBack = { screen = Screen.SETTINGS })
                        }
                        Screen.TUNING_EDITOR -> {
                            val close = {
                                // Each opening starts afresh, not from the last draft.
                                saveableState.removeState(Screen.TUNING_EDITOR.name)
                                screen = Screen.TUNER
                            }
                            BackHandler(onBack = close)
                            TuningEditorScreen(
                                settingsRepository = container.settingsRepository,
                                settings = settings,
                                instrument = Instruments.byId(editorInstrumentId) ?: Instruments.DEFAULT,
                                editingId = editorTuningId,
                                onClose = close,
                            )
                        }
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
