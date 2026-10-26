package io.github.klaboukes.twangle.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.tappableElement
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.klaboukes.twangle.app.navigation.AppNavigationFrame
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.Screen
import io.github.klaboukes.twangle.app.navigation.back
import io.github.klaboukes.twangle.app.navigation.destination
import io.github.klaboukes.twangle.app.navigation.screen
import io.github.klaboukes.twangle.app.widget.TuningWidget
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.useDarkTheme
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.metronome.MetronomeScreen
import io.github.klaboukes.twangle.feature.practice.CapoScreen
import io.github.klaboukes.twangle.feature.practice.ChordsScreen
import io.github.klaboukes.twangle.feature.practice.PracticeToolButton
import io.github.klaboukes.twangle.feature.practice.practiceToolToOpen
import io.github.klaboukes.twangle.feature.practice.practiceToolsFor
import io.github.klaboukes.twangle.feature.settings.AboutScreen
import io.github.klaboukes.twangle.feature.settings.SettingsScreen
import io.github.klaboukes.twangle.feature.settings.openPlayPage
import io.github.klaboukes.twangle.feature.tuner.R as TunerR
import io.github.klaboukes.twangle.feature.tuner.TunerScreen
import io.github.klaboukes.twangle.feature.tuner.components.InstrumentTuningPanel
import io.github.klaboukes.twangle.feature.tuner.components.instrumentLabel
import io.github.klaboukes.twangle.feature.tuner.components.tuningLabel
import io.github.klaboukes.twangle.feature.tuner.editor.TuningEditorScreen
import io.github.klaboukes.twangle.feature.tuner.tour.TourHost
import io.github.klaboukes.twangle.feature.tuner.tour.TourPart
import io.github.klaboukes.twangle.feature.tuner.tour.tourTextOf
import io.github.klaboukes.twangle.feature.tuner.tunerToolsFor
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
     * at the bottom, or the rail at the start edge on its side. The instrument and tuning panel opens
     * over the tuner, or over the Practice tools, from their instrument button. Back leads from About to Settings, from
     * every other screen to the tuner, and from the tuner out of the app. Each new [openTunerRequest]
     * (the home-screen widget) shows the tuner, as Back does; the tuning editor handles both itself,
     * asking before it drops edits. A tap on the Tuner tab, also while the tuner shows, closes the
     * tuner's tools (strum check and the others), so the tab always leads to the plain tuner.
     *
     * The first-start tour lies over everything (bar or rail included) while it is due ([tourDue]: a
     * fresh install, or "Show me around" in Settings) and the tuner shows ([tourShown]). Skip tour,
     * Back or Done end it for good. Its strings step ("tap one to lock it") is how people find the
     * string lock.
     */
    @Composable
    private fun TwangleApp(container: AppContainer, settings: AppSettings, openTunerRequest: Int) {
        val darkTheme = settings.theme.useDarkTheme(isSystemInDarkTheme())
        // Navigation buttons or the gesture bar. Switching between them changes the insets, so the
        // phone's navigation mode is read again then.
        val tappable = WindowInsets.tappableElement
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val left = tappable.getLeft(density, layoutDirection)
        val right = tappable.getRight(density, layoutDirection)
        val bottom = tappable.getBottom(density)
        val buttonNavigation = remember(left, right, bottom) {
            navigationButtonsShown(contentResolver.navigationMode(), left, right, bottom)
        }

        // Status and navigation bar icons follow the app's theme, not just the phone's.
        DisposableEffect(darkTheme, buttonNavigation) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
            )
            // enableEdgeToEdge asks the system to keep the navigation bar readable, and MIUI then lays a
            // white (or dark) strip behind the gesture bar too. The bare handle needs none: only navigation
            // buttons keep the system's scrim, so the style's background reaches the bottom edge.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = buttonNavigation
            }
            onDispose {}
        }

        // Focus mode keeps the whole app on its side, every tab (Phase 36); off, the screen follows the
        // phone again, turned back upright first where auto-rotate is off (see FocusTurn).
        val upright = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
        var focusTurn by rememberSaveable(stateSaver = FocusTurn.Saver) {
            mutableStateOf(FocusTurn(focusMode = settings.focusMode, uprightBefore = upright))
        }
        val nextTurn = focusTurn.next(settings.focusMode, upright, autoRotate = ::autoRotateOn)
        SideEffect { if (nextTurn != focusTurn) focusTurn = nextTurn }
        val orientation = nextTurn.orientation
        DisposableEffect(orientation) {
            requestedOrientation = orientation
            onDispose {}
        }

        // Which music tools show (Phase 28): every one since they went live (Phase 34).
        val tools = container.toolSwitches
        TwangleTheme(style = settings.style, darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
            // Lets UI Automator (the Baseline Profile generator, the meter frame-time benchmark) find test tags.
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                var screen by rememberSaveable { mutableStateOf(Screen.TUNER) }
                // What the tuning editor works on: an instrument, and a custom tuning's id or null for a new one.
                var editorInstrumentId by rememberSaveable { mutableStateOf(Instruments.DEFAULT.id) }
                var editorTuningId by rememberSaveable { mutableStateOf<String?>(null) }
                // Where the tuning editor goes back to: the tuner, or Practice when its panel opened it.
                var editorReturn by rememberSaveable { mutableStateOf(Screen.TUNER) }
                // Whether the instrument and tuning panel shows over the tuner (its instrument button opens it).
                var tuningPanelOpen by rememberSaveable { mutableStateOf(false) }
                // The first-start tour: asked for again from Settings, ended in this session, and whether the tuner itself shows.
                var tourRequested by rememberSaveable { mutableStateOf(false) }
                var tourEnded by rememberSaveable { mutableStateOf(false) }
                var tunerShown by remember { mutableStateOf(false) }
                // Focus mode's tuner without its controls: the meter takes the whole screen, so the rail
                // and the system bars go (a swipe from the edge shows the system bars for a moment).
                var tunerFullScreen by remember { mutableStateOf(false) }
                val fullScreen = tunerFullScreen && screen == Screen.TUNER
                DisposableEffect(fullScreen) {
                    val bars = WindowCompat.getInsetsController(window, window.decorView)
                    if (fullScreen) {
                        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        bars.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        bars.show(WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose {}
                }
                // Taps on the bar's Tuner tab, also while the tuner shows: each closes the tuner's tools.
                var tunerTabTaps by rememberSaveable { mutableIntStateOf(0) }
                val tourDue = tourDue(settings.tourSeen, tourRequested, tourEnded)
                val tourOpen = tourShown(tourDue, screen == Screen.TUNER, tunerShown)
                // Every screen but the tuner gets the quiet version of the style's background.
                StyleBackdrop(Modifier.fillMaxSize(), quiet = screen != Screen.TUNER)
                // Keeps each screen's saved UI state (e.g. the mic permission status) while the other one is shown.
                val saveableState = rememberSaveableStateHolder()
                val closeEditor = {
                    // Each opening starts afresh, not from the last draft.
                    saveableState.removeState(Screen.TUNING_EDITOR.name)
                    screen = editorReturn
                }
                val openTuningEditor: (Instrument, String?, Screen) -> Unit = { instrument, customTuningId, from ->
                    editorInstrumentId = instrument.id
                    editorTuningId = customTuningId
                    editorReturn = from
                    screen = Screen.TUNING_EDITOR
                }
                // A tap on the home-screen widget: back to the tuner. The tuning editor handles it itself,
                // as it does Back: it asks before dropping a draft with edits.
                LaunchedEffect(openTunerRequest) {
                    if (openTunerRequest == 0 || screen == Screen.TUNING_EDITOR) return@LaunchedEffect
                    screen = Screen.TUNER
                }
                // From the tuner, Back is Android's own: it leaves the app.
                if (screen != Screen.TUNING_EDITOR) {
                    screen.back?.let { target -> BackHandler { screen = target } }
                }
                TourHost(
                    open = tourOpen,
                    steps = TourPart.stepsFor(settings.mode),
                    // Where the tuner offers more than the strum check, its "more tools" button stands in the switch's place.
                    textOf = { tourTextOf(it, moreTools = tunerToolsFor(tools, settings.instrument).size > 1) },
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
                        onSelect = {
                            if (it == Destination.TUNER) tunerTabTaps++
                            screen = it.screen
                        },
                        onReselect = { if (it == Destination.TUNER) tunerTabTaps++ },
                        practice = tools.practiceTab,
                        navigationHidden = fullScreen,
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
                                        tools = tools,
                                        savedMode = settings.mode,
                                        savedFocusMode = settings.focusMode,
                                        savedInstrument = settings.instrument,
                                        savedCustomTunings = settings.customTunings,
                                        onOpenTuningEditor = { instrument, customTuningId ->
                                            openTuningEditor(instrument, customTuningId, Screen.TUNER)
                                        },
                                        pickerOpen = tuningPanelOpen,
                                        onPickerOpenChange = { tuningPanelOpen = it },
                                        tourOpen = tourOpen,
                                        onMicGrantedChange = { tunerShown = it },
                                        savedMicRefused = settings.micRefused,
                                        closeToolsRequest = tunerTabTaps,
                                        onFullScreenChange = { tunerFullScreen = it },
                                    )
                                }
                                Screen.METRONOME -> PracticeTab(container, settings, tools) { instrument, customTuningId ->
                                    openTuningEditor(instrument, customTuningId, Screen.METRONOME)
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
                                Screen.ABOUT -> AboutScreen(
                                    appVersion = BuildConfig.VERSION_NAME,
                                    onRate = { openPlayPage(this@MainActivity, BuildConfig.APPLICATION_ID) },
                                    onBack = { screen = Screen.SETTINGS },
                                )
                                Screen.TUNING_EDITOR -> TuningEditorScreen(
                                    settingsRepository = container.settingsRepository,
                                    settings = settings,
                                    instrument = Instruments.byId(editorInstrumentId) ?: Instruments.DEFAULT,
                                    editingId = editorTuningId,
                                    onClose = closeEditor,
                                    closeRequest = openTunerRequest,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The bar's Metronome tab. With the Practice tab's switch off ([ToolSwitches.practiceTab]) it
     * is the metronome alone. With it on it is Practice
     * (Phase 32): the tool button at the top of each tool (Phase 47) picks the metronome, chords, scales
     * or, with its own switch ([ToolSwitches.capoHelper]), the capo helper (Phase 33), and the tab
     * opens on the tool used last ([AppSettings.practiceTool], saved as soon as it is picked). Each
     * tool keeps its own saved UI state while another shows, but for Chords and Scales, one screen that
     * shares it (one root for both); leaving the metronome stops it, as
     * leaving the tab does. A chord tapped in the capo helper opens in Chords: its saved state is
     * dropped, so Chords opens afresh on that chord (saved too, as if picked there). The instrument
     * button in Chords and the capo helper opens the instrument and tuning panel over the tool
     * ([PracticeTuningPanel]); its New tuning and Edit open the tuning editor ([onOpenTuningEditor]).
     */
    @Composable
    private fun PracticeTab(
        container: AppContainer,
        settings: AppSettings,
        tools: ToolSwitches,
        onOpenTuningEditor: (Instrument, String?) -> Unit,
    ) {
        var panelOpen by rememberSaveable { mutableStateOf(false) }
        val openPanel = { panelOpen = true }
        if (panelOpen) {
            PracticeTuningPanel(container, settings, onDismiss = { panelOpen = false }, onOpenTuningEditor = { instrument, id ->
                panelOpen = false
                onOpenTuningEditor(instrument, id)
            })
        }
        val offered = practiceToolsFor(tools)
        var tool by rememberSaveable { mutableStateOf(practiceToolToOpen(settings.practiceTool, offered)) }
        // The chord the capo helper opened in Chords, until the saved settings have caught up with it.
        var opened by remember { mutableStateOf<Chord?>(null) }
        val pickTool: (PracticeTool) -> Unit = { picked ->
            if (picked != tool) {
                tool = picked
                lifecycleScope.launch { container.settingsRepository.setPracticeTool(picked) }
            }
        }
        val header: (@Composable () -> Unit)? = if (offered.isEmpty()) {
            null
        } else {
            { PracticeToolButton(tool = tool, onToolChange = pickTool, tools = offered) }
        }
        val toolState = rememberSaveableStateHolder()
        // Chords and Scales are one screen ([ChordsScreen]) that keeps its saved state across them.
        toolState.SaveableStateProvider(if (tool == PracticeTool.SCALES) PracticeTool.CHORDS.name else tool.name) {
            when (if (header == null) PracticeTool.METRONOME else tool) {
                PracticeTool.METRONOME -> {
                    // The volume keys set the clicks' loudness (the media volume).
                    MediaVolumeKeys()
                    MetronomeScreen(
                        player = container.metronomePlayer,
                        settingsRepository = container.settingsRepository,
                        recorder = container.listenRecorder,
                        tools = tools,
                        savedBpm = settings.metronomeBpm,
                        savedMeter = settings.metronomeMeter,
                        savedVibrate = settings.metronomeVibrate,
                        savedSound = settings.metronomeClickSound,
                        savedMicRefused = settings.micRefused,
                        header = header,
                    )
                }
                PracticeTool.CHORDS, PracticeTool.SCALES -> ChordsScreen(
                    tuning = settings.tuning,
                    tuningName = practiceTuningName(settings),
                    spokenTuningName = spokenTuning(settings),
                    notation = settings.notationFor(appLanguage()),
                    savedRoot = opened?.root ?: settings.practiceRoot,
                    savedQuality = opened?.quality ?: settings.practiceChordQuality,
                    onRootChange = { lifecycleScope.launch { container.settingsRepository.setPracticeRoot(it) } },
                    onQualityChange = { lifecycleScope.launch { container.settingsRepository.setPracticeChordQuality(it) } },
                    header = header ?: {},
                    scales = tool == PracticeTool.SCALES,
                    savedScaleType = settings.practiceScaleType,
                    onScaleTypeChange = { lifecycleScope.launch { container.settingsRepository.setPracticeScaleType(it) } },
                    onPickInstrument = openPanel,
                )
                PracticeTool.CAPO -> CapoScreen(
                    tuningName = practiceTuningName(settings),
                    spokenTuningName = spokenTuning(settings),
                    notation = settings.notationFor(appLanguage()),
                    savedShapes = settings.practiceCapoShapes,
                    savedFret = settings.practiceCapoFret,
                    onShapesChange = { lifecycleScope.launch { container.settingsRepository.setPracticeCapoShapes(it) } },
                    onFretChange = { lifecycleScope.launch { container.settingsRepository.setPracticeCapoFret(it) } },
                    onOpenChord = { chord ->
                        opened = chord
                        toolState.removeState(PracticeTool.CHORDS.name)
                        lifecycleScope.launch {
                            container.settingsRepository.setPracticeRoot(chord.root)
                            container.settingsRepository.setPracticeChordQuality(chord.quality)
                        }
                        pickTool(PracticeTool.CHORDS)
                    },
                    header = header ?: {},
                    onPickInstrument = openPanel,
                )
            }
        }
    }

    /**
     * The instrument and tuning panel over a Practice tool: the same panel as the tuner's, saving what is
     * picked as the tuner does. A pick also switches the tuner to guitar mode, where the strings show,
     * as a pick in the tuner's panel does.
     */
    @Composable
    private fun PracticeTuningPanel(
        container: AppContainer,
        settings: AppSettings,
        onDismiss: () -> Unit,
        onOpenTuningEditor: (Instrument, String?) -> Unit,
    ) {
        val repository = container.settingsRepository
        val toGuitar = suspend { if (settings.mode != TunerMode.AUTO) repository.setMode(TunerMode.AUTO) }
        InstrumentTuningPanel(
            instrument = settings.instrument,
            tuning = settings.tuning,
            onInstrumentChange = {
                lifecycleScope.launch {
                    toGuitar()
                    repository.setInstrument(it)
                }
            },
            onTuningChange = {
                lifecycleScope.launch {
                    toGuitar()
                    repository.setTuning(it)
                }
            },
            onDismiss = onDismiss,
            notation = settings.notationFor(appLanguage()),
            customTunings = settings.customTunings,
            onNewTuning = { onOpenTuningEditor(it, null) },
            onEditTuning = { onOpenTuningEditor(it.instrument, it.id) },
            onDeleteTuning = { lifecycleScope.launch { repository.deleteCustomTuning(it.id) } },
        )
    }

    /** The picked instrument and tuning as the Practice tools show them, e.g. "Guitar · Standard". */
    @Composable
    private fun practiceTuningName(settings: AppSettings): String =
        stringResource(TunerR.string.tuner_tuning_name, instrumentLabel(settings.instrument), tuningName(settings))

    /** The picked instrument and tuning as TalkBack says them on the instrument button, e.g. "Guitar, Standard" or a custom tuning's name. */
    @Composable
    private fun spokenTuning(settings: AppSettings): String =
        stringResource(TunerR.string.tuner_spoken_picker, instrumentLabel(settings.instrument), tuningName(settings))

    /** The picked tuning's name: a custom tuning's own, else the preset's ("Standard", "Drop D"). */
    @Composable
    private fun tuningName(settings: AppSettings): String =
        settings.pickedCustomTuning?.name ?: tuningLabel(settings.tuning)

    /** Whether the phone's auto-rotate is on (Android's own setting, readable without a permission). */
    private fun autoRotateOn(): Boolean =
        Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

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
