package io.github.klaboukes.twangle.app

import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.klaboukes.twangle.app.navigation.AppNavigationFrame
import io.github.klaboukes.twangle.app.navigation.Screen
import io.github.klaboukes.twangle.app.navigation.back
import io.github.klaboukes.twangle.app.navigation.destination
import io.github.klaboukes.twangle.app.navigation.screen
import io.github.klaboukes.twangle.app.widget.TuningWidget
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.useDarkTheme
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.feature.metronome.MetronomeScreen
import io.github.klaboukes.twangle.feature.settings.AboutScreen
import io.github.klaboukes.twangle.feature.settings.SettingsScreen
import io.github.klaboukes.twangle.feature.tuner.TunerScreen
import io.github.klaboukes.twangle.feature.tuner.editor.TuningEditorScreen
import io.github.klaboukes.twangle.feature.tuner.tour.TourHost
import io.github.klaboukes.twangle.feature.tuner.tour.TourPart
import kotlinx.coroutines.launch

/**
 * The app's one activity. An [AppCompatActivity] (rather than a plain ComponentActivity) so the
 * language picked in Settings applies on Android 8–12 too: AppCompat keeps it and sets it on the
 * activity; Android 13+ keeps it as the per-app language itself.
 */
class MainActivity : AppCompatActivity() {
    /** True once the first frame with the user's settings has been composed; until then the opening screen stays up. */
    private var ready = false

    /** Counts the home-screen widget's taps while the app was already open; each one shows the tuner. */
    private var openTunerRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The opening screen (Theme.Twangle.Starting): the icon on gold, or on dark brown in dark mode.
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TwangleApplication).container
        // A fresh install is due the first-start tour; an update from before the tour is not. Decided once.
        lifecycleScope.launch { container.settingsRepository.decideTourOnce() }
        setContent {
            // Nothing is drawn for the few milliseconds until the settings are read, so the app
            // never flashes the wrong theme. The opening screen covers that gap.
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let {
                SideEffect { ready = true }
                TwangleApp(container, it, openTunerRequests)
            }
        }
    }

    /** A tap on the home-screen widget while Twangle was open: show the tuner (on a fresh start it already does). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == TuningWidget.ACTION_OPEN_TUNER) openTunerRequests++
    }

    /**
     * The app in the user's style and theme, showing the tuner, the metronome, settings, About or the
     * tuning editor over the style's background, with the navigation bar (Tuner · Metronome · Settings)
     * at the bottom, or the rail at the start edge on its side. Back leads from About to Settings, from
     * every other screen to the tuner, and from the tuner out of the app. Each new [openTunerRequest]
     * (the home-screen widget) shows the tuner, as Back does.
     *
     * The first-start tour lies over everything (bar or rail included) while it is due ([tourDue]: a
     * fresh install, or "Show me around" in Settings) and the tuner shows ([tourShown]). Skip tour,
     * Back or Done end it for good. Its strings step ("tap one to lock it") is how people find the
     * string lock.
     */
    @Composable
    private fun TwangleApp(container: AppContainer, settings: AppSettings, openTunerRequest: Int) {
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
            // Lets UI Automator (the Baseline Profile generator, the meter frame-time benchmark) find test tags.
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                var screen by rememberSaveable { mutableStateOf(Screen.TUNER) }
                // What the tuning editor works on: an instrument, and a custom tuning's id or null for a new one.
                var editorInstrumentId by rememberSaveable { mutableStateOf(Instruments.DEFAULT.id) }
                var editorTuningId by rememberSaveable { mutableStateOf<String?>(null) }
                // Whether the tuner shows its compact layout on its side without the controls: the rail hides then.
                var tunerCompact by remember { mutableStateOf(false) }
                // The first-start tour: asked for again from Settings, ended in this session, and whether the tuner itself shows.
                var tourRequested by rememberSaveable { mutableStateOf(false) }
                var tourEnded by rememberSaveable { mutableStateOf(false) }
                var tunerShown by remember { mutableStateOf(false) }
                val tourDue = tourDue(settings.tourSeen, tourRequested, tourEnded)
                val tourOpen = tourShown(tourDue, screen == Screen.TUNER, tunerShown)
                // Every screen but the tuner gets the quiet version of the style's background.
                StyleBackdrop(Modifier.fillMaxSize(), quiet = screen != Screen.TUNER)
                // Keeps each screen's saved UI state (e.g. the mic permission status) while the other one is shown.
                val saveableState = rememberSaveableStateHolder()
                val closeEditor = {
                    // Each opening starts afresh, not from the last draft.
                    saveableState.removeState(Screen.TUNING_EDITOR.name)
                    screen = Screen.TUNER
                }
                // A tap on the home-screen widget: back to the tuner (an open editor's draft is dropped, as with Back).
                LaunchedEffect(openTunerRequest) {
                    if (openTunerRequest == 0) return@LaunchedEffect
                    if (screen == Screen.TUNING_EDITOR) closeEditor() else screen = Screen.TUNER
                }
                // From the tuner, Back is Android's own: it leaves the app.
                screen.back?.let { target ->
                    BackHandler { if (screen == Screen.TUNING_EDITOR) closeEditor() else screen = target }
                }
                TourHost(
                    open = tourOpen,
                    steps = TourPart.stepsFor(settings.mode),
                    onEnd = {
                        tourEnded = true
                        tourRequested = false
                        // For good: it doesn't come back by itself.
                        lifecycleScope.launch { container.settingsRepository.setTourSeen(true) }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AppNavigationFrame(
                        selected = screen.destination,
                        onSelect = { screen = it.screen },
                        hidden = screen == Screen.TUNER && tunerCompact,
                    ) {
                        saveableState.SaveableStateProvider(screen.name) {
                            when (screen) {
                                Screen.TUNER -> {
                                    // The volume keys set the reference tone's loudness (the media volume)
                                    // while the tuner is shown, also when no tone sounds.
                                    MediaVolumeKeys()
                                    TunerScreen(
                                        audioRecorder = container.audioRecorder,
                                        settingsRepository = container.settingsRepository,
                                        tonePlayer = container.tonePlayer,
                                        onCompactChange = { tunerCompact = it },
                                        savedMode = settings.mode,
                                        savedKeepTwoPaneLandscape = settings.keepTwoPaneLandscape,
                                        savedInstrument = settings.instrument,
                                        savedCustomTunings = settings.customTunings,
                                        onOpenTuningEditor = { instrument, customTuningId ->
                                            editorInstrumentId = instrument.id
                                            editorTuningId = customTuningId
                                            screen = Screen.TUNING_EDITOR
                                        },
                                        tourOpen = tourOpen,
                                        onMicGrantedChange = { tunerShown = it },
                                    )
                                }
                                Screen.METRONOME -> {
                                    // The volume keys set the clicks' loudness (the media volume).
                                    MediaVolumeKeys()
                                    MetronomeScreen(
                                        player = container.metronomePlayer,
                                        settingsRepository = container.settingsRepository,
                                        savedBpm = settings.metronomeBpm,
                                        savedMeter = settings.metronomeMeter,
                                        savedVibrate = settings.metronomeVibrate,
                                    )
                                }
                                Screen.SETTINGS -> SettingsScreen(
                                    settingsRepository = container.settingsRepository,
                                    appVersion = BuildConfig.VERSION_NAME,
                                    onOpenAbout = { screen = Screen.ABOUT },
                                    languages = BuildConfig.LANGUAGES.split(","),
                                    // Android 13+ redraws the widget itself on the new configuration.
                                    onLanguageChange = { (application as TwangleApplication).redrawWidgets() },
                                    onShowTour = {
                                        tourRequested = true
                                        screen = Screen.TUNER
                                    },
                                )
                                Screen.ABOUT -> AboutScreen(appVersion = BuildConfig.VERSION_NAME, onBack = { screen = Screen.SETTINGS })
                                Screen.TUNING_EDITOR -> TuningEditorScreen(
                                    settingsRepository = container.settingsRepository,
                                    settings = settings,
                                    instrument = Instruments.byId(editorInstrumentId) ?: Instruments.DEFAULT,
                                    editingId = editorTuningId,
                                    onClose = closeEditor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** While this is in the composition, the volume keys set the media volume (the tone's and the clicks' loudness). */
    @Composable
    private fun MediaVolumeKeys() {
        DisposableEffect(Unit) {
            volumeControlStream = AudioManager.STREAM_MUSIC
            onDispose { volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE }
        }
    }

    private companion object {
        // The scrims enableEdgeToEdge() uses by default behind 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
