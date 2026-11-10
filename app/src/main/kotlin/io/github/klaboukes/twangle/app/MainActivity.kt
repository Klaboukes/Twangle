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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import io.github.klaboukes.twangle.app.navigation.destinationsFor
import io.github.klaboukes.twangle.app.navigation.practiceTool
import io.github.klaboukes.twangle.app.navigation.reselected
import io.github.klaboukes.twangle.app.navigation.screen
import io.github.klaboukes.twangle.app.navigation.screenFrom
import io.github.klaboukes.twangle.app.navigation.shownScreen
import io.github.klaboukes.twangle.app.widget.TuningWidget
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.LocalScreenTint
import io.github.klaboukes.twangle.core.designsystem.ScreenTint
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.phoneHighContrastFlow
import io.github.klaboukes.twangle.core.designsystem.phoneWantsHighContrast
import io.github.klaboukes.twangle.core.designsystem.useDarkTheme
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordInstruments
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.feature.eartraining.EarTrainingScreen
import io.github.klaboukes.twangle.feature.tuner.trail.PitchTrailScreen
import io.github.klaboukes.twangle.feature.metronome.MetronomeScreen
import io.github.klaboukes.twangle.feature.practice.CapoScreen
import io.github.klaboukes.twangle.feature.practice.ChordsScreen
import io.github.klaboukes.twangle.feature.practice.DroneChoice
import io.github.klaboukes.twangle.feature.practice.BackAndTuningRow
import io.github.klaboukes.twangle.feature.practice.PracticeCards
import io.github.klaboukes.twangle.feature.practice.lastTimeTool
import io.github.klaboukes.twangle.feature.practice.practiceToolsFor
import io.github.klaboukes.twangle.feature.settings.AboutScreen
import io.github.klaboukes.twangle.feature.settings.SettingsScreen
import io.github.klaboukes.twangle.feature.settings.openPlayPage
import io.github.klaboukes.twangle.feature.settings.openWebsite
import io.github.klaboukes.twangle.feature.tuner.R as TunerR
import io.github.klaboukes.twangle.feature.tuner.TunerScreen
import io.github.klaboukes.twangle.feature.tuner.components.InstrumentTuningPanel
import io.github.klaboukes.twangle.feature.tuner.components.instrumentLabel
import io.github.klaboukes.twangle.feature.tuner.components.tuningLabel
import io.github.klaboukes.twangle.feature.tuner.editor.TuningEditorScreen
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
        // The opening screen (Theme.Twangle.Starting): the icon on Ink light, or on Ink dark in dark mode, its
        // harmonics swinging into place on Android 12 and up.
        installSplashScreen().apply {
            setKeepOnScreenCondition { !ready }
            setOnExitAnimationListener { opening -> opening.fadeOutOnceStill() }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TwangleApplication).container
        // The first use is noted once: Google's rating window never comes on the first day.
        container.save { noteFirstUseOnce(System.currentTimeMillis()) }
        // 1.1's focus mode switch, read once (2.0, Phase 73): on, the first start of 2.0 opens in focus mode.
        if (savedInstanceState == null) {
            lifecycleScope.launch { if (container.settingsRepository.takeOldFocusMode()) container.focusMode.value = true }
        }
        // What a 2.0 test build saved for a setting that went before release (the 12th-fret check's switch).
        container.save { forgetDroppedSettings() }
        takeLensLook(intent)
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
        takeLensLook(intent)
    }

    /**
     * In a debug build and the frame-time build (`scripts/meter-frames.sh`) only: saves the lens's look named by
     * the launch's [EXTRA_LENS_LOOK] (`adb shell am start -S --activity-clear-task -n …/.app.MainActivity --es
     * lensLook STROBE`; without a new task the old task's first launch comes back, with its look), for
     * checks on the phone and the frame-time runs. Every other build ignores it (both flags false, so R8 drops it).
     */
    private fun takeLensLook(intent: Intent?) {
        if (!BuildConfig.DEBUG && !BuildConfig.SYNTHETIC_AUDIO) return
        val name = intent?.getStringExtra(EXTRA_LENS_LOOK) ?: return
        val look = runCatching { LensLook.valueOf(name) }.getOrNull() ?: return
        (application as TwangleApplication).container.save { setLensLook(look) }
    }

    /**
     * The app in the user's theme, showing the tuner, the metronome, Practice, settings, About or the
     * tuning editor over the app's background, with the navigation bar (Tune · Metronome · Practice) at
     * the bottom, or the rail at the start edge on its side. The gear in the top start corner of every
     * tab (upright in the tab's own top row, on its side at the rail's top) opens Settings over the whole
     * window, without the bar; Settings' back arrow and Back return to the screen that opened it, About's to
     * Settings. The Practice tab shows its cards ([PracticeCards]), one per tool, and a card opens its tool
     * under the bar ([PracticeToolScreen]); the tool's back arrow, Back and a tap on the Practice tab return
     * to the cards, and the tab, opened again from another, comes back to the tool it was left on. The
     * instrument and tuning panel opens
     * over the tuner, or over the Practice tools, from their instrument button. Back leads from the
     * metronome, Practice's cards and the tuning editor to the tuner, and from the tuner out of the app. Each new [openTunerRequest]
     * (the home-screen widget) shows the tuner, as Back does; the tuning editor handles both itself,
     * asking before it drops edits. A tap on the Tuner tab, also while the tuner shows, closes the
     * tuner's tools (strum check and the others), so the tab always leads to the plain tuner.
     */
    @Composable
    private fun TwangleApp(container: AppContainer, settings: AppSettings, openTunerRequest: Int) {
        val darkTheme = settings.theme.useDarkTheme(isSystemInDarkTheme())
        // The high-contrast look (Phase 86): the player's switch, else the phone's own contrast (high, or high-contrast
        // text). Read at once for the first frame, so the look never flashes, then followed while the app shows.
        val phoneHighContrast by remember { phoneHighContrastFlow(applicationContext) }
            .collectAsStateWithLifecycle(initialValue = remember { phoneWantsHighContrast(applicationContext) })
        val highContrast = settings.highContrastOn(phoneHighContrast)

        // Status and navigation bar icons follow the app's theme, not just the phone's. Both bars are
        // see-through, so the app's background runs to every edge, under the gesture handle and under
        // the three navigation buttons too, upright and on its side (Phase 65; until then the buttons
        // stood on the system's white or dark strip). Their icons stay readable: dark on a light style,
        // light on a dark one, and the navigation bar's calm field lies under them upright.
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            )
            // enableEdgeToEdge asks the system to keep the navigation bar readable, which lays a strip
            // behind the buttons (and, on MIUI, behind the gesture bar too): never asked.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            onDispose {}
        }

        // Focus mode keeps the whole app on its side, every tab (Phase 36); off, the screen follows the
        // phone again, turned back upright first where auto-rotate is off (see FocusTurn), except on a
        // large screen, which goes back to its rotation lock. Since 2.0 (Phase 73) the tuner's button alone
        // turns it on, and nothing saves it: the app holds it ([AppContainer.focusMode]).
        val focusMode by container.focusMode.collectAsStateWithLifecycle()
        val configuration = LocalConfiguration.current
        val upright = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val largeScreen = configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP
        var focusTurn by rememberSaveable(stateSaver = FocusTurn.Saver) {
            mutableStateOf(FocusTurn(focusMode = focusMode, uprightBefore = upright))
        }
        val nextTurn = focusTurn.next(focusMode, upright, largeScreen, autoRotate = ::autoRotateOn)
        SideEffect { if (nextTurn != focusTurn) focusTurn = nextTurn }
        val orientation = nextTurn.orientation
        DisposableEffect(orientation) {
            requestedOrientation = orientation
            onDispose {}
        }

        // Which music tools show (Phase 28): every one since they went live (Phase 34).
        val tools = container.toolSwitches
        // Practice's tools; with none (its switch off) there is no Practice tab, and the metronome keeps its own.
        val practiceTools = practiceToolsFor(tools)
        val screenTint = remember { ScreenTint() }
        TwangleTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor, highContrast = highContrast) {
            // Lets UI Automator (the Baseline Profile generator, the meter frame-time benchmark) find test tags.
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                // The tuner tints the whole screen toward its reading (Phase 71): the backdrop and the bar's and rail's fields read it.
                // Not in the high-contrast look, whose page stays pure black or white.
                CompositionLocalProvider(LocalScreenTint provides screenTint.takeUnless { highContrast }) {
                    var picked by rememberSaveable { mutableStateOf(Screen.TUNER) }
                    // Practice without a tool is no tab (its switch off): the metronome then, never an empty screen;
                    // a tool whose switch is off shows the cards.
                    val screen = shownScreen(picked, practiceTools)
                    // Whether the capo helper opened Chords (a row of its table), so Back from Chords returns to it.
                    var chordsFromCapo by rememberSaveable { mutableStateOf(false) }
                    // The chord the capo helper opened in Chords, until the saved settings have caught up with it.
                    var openedChord by remember { mutableStateOf<Chord?>(null) }
                    // What the tuning editor works on: an instrument, and a custom tuning's id or null for a new one.
                    var editorInstrumentId by rememberSaveable { mutableStateOf(Instruments.DEFAULT.id) }
                    var editorTuningId by rememberSaveable { mutableStateOf<String?>(null) }
                    // Where the tuning editor goes back to: the tuner, or Practice when its panel opened it.
                    var editorReturn by rememberSaveable { mutableStateOf(Screen.TUNER) }
                    // The screen whose gear opened Settings, where Settings goes back to: a tab, or on its side a
                    // Practice tool (the rail's gear).
                    var settingsOpener by rememberSaveable { mutableStateOf(Screen.TUNER) }
                    val openSettings = {
                        settingsOpener = if (screen.destination != null) screen else Screen.TUNER
                        picked = Screen.SETTINGS
                    }
                    // Where the Practice tab was left, the cards or a tool, so the tab comes back to it.
                    var practiceLeftOn by rememberSaveable { mutableStateOf(Screen.PRACTICE) }
                    SideEffect { if (screen.destination == Destination.PRACTICE) practiceLeftOn = screen }
                    // Whether the instrument and tuning panel shows over the tuner (its instrument button opens it).
                    var tuningPanelOpen by rememberSaveable { mutableStateOf(false) }
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
                    // Taps on the bar's Tuner tab (also while the tuner shows) close the tuner's tools, so
                    // the tab leads to the plain tuner.
                    var closeTunerToolsRequests by rememberSaveable { mutableIntStateOf(0) }
                    // Ink's plain background behind every screen, tinted by the tuner's reading while it hears a note.
                    StyleBackdrop(Modifier.fillMaxSize())
                    // Keeps each screen's saved UI state (e.g. the mic permission status) while the other one is shown.
                    val saveableState = rememberSaveableStateHolder()
                    val closeEditor = {
                        // Each opening starts afresh, not from the last draft.
                        saveableState.removeState(Screen.TUNING_EDITOR.name)
                        picked = editorReturn
                    }
                    val openTuningEditor: (Instrument, String?, Screen) -> Unit = { instrument, customTuningId, from ->
                        editorInstrumentId = instrument.id
                        editorTuningId = customTuningId
                        editorReturn = from
                        picked = Screen.TUNING_EDITOR
                    }
                    // A tap on the home-screen widget: back to the tuner. The tuning editor handles it itself,
                    // as it does Back: it asks before dropping a draft with edits.
                    LaunchedEffect(openTunerRequest) {
                        if (openTunerRequest == 0 || screen == Screen.TUNING_EDITOR) return@LaunchedEffect
                        picked = Screen.TUNER
                    }
                    // From the tuner, Back is Android's own: it leaves the app.
                    if (screen != Screen.TUNING_EDITOR) {
                        screen.back(settingsOpener, chordsFromCapo)?.let { target -> BackHandler { picked = target } }
                    }
                    // A card or the "last time" row opens its tool full screen, and it is the tool used last from then on.
                    val openTool: (PracticeTool) -> Unit = { tool ->
                        chordsFromCapo = false
                        openedChord = null
                        picked = tool.screen
                        container.save { setPracticeTool(tool) }
                    }
                    // A row of the capo helper's table opens its chord in Chords: Chords' saved state is dropped, so it
                    // opens afresh on that chord (saved too, as if picked there), and Back leads back to the capo helper.
                    val openChordFromCapo: (Chord) -> Unit = { chord ->
                        openedChord = chord
                        saveableState.removeState(CHORDS_STATE)
                        container.save { openInChords(chord.root, chord.quality) }
                        chordsFromCapo = true
                        picked = Screen.PRACTICE_CHORDS
                    }
                    val backFromTool: () -> Unit = { screen.back(settingsOpener, chordsFromCapo)?.let { picked = it } }
                    AppNavigationFrame(
                        selected = screen.destination,
                        onSelect = {
                            if (it == Destination.TUNER) closeTunerToolsRequests++
                            picked = it.screenFrom(practiceLeftOn)
                        },
                        onReselect = {
                            if (it == Destination.TUNER) closeTunerToolsRequests++
                            screen.reselected()?.let { target -> picked = target }
                        },
                        destinations = destinationsFor(practice = practiceTools.isNotEmpty()),
                        navigationHidden = fullScreen,
                        onOpenSettings = openSettings,
                    ) { gear ->
                        // Chords and Scales are one screen ([ChordsScreen]) that keeps its saved state across them.
                        saveableState.SaveableStateProvider(if (screen == Screen.PRACTICE_SCALES) CHORDS_STATE else screen.name) {
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
                                        saved = settings,
                                        onOpenTuningEditor = { instrument, customTuningId ->
                                            openTuningEditor(instrument, customTuningId, Screen.TUNER)
                                        },
                                        pickerOpen = tuningPanelOpen,
                                        onPickerOpenChange = { tuningPanelOpen = it },
                                        closeToolsRequest = closeTunerToolsRequests,
                                        onFullScreenChange = { tunerFullScreen = it },
                                        ratingWindow = container.ratingWindow,
                                        settingsButton = gear,
                                        // Its top row names the tab, as the metronome's and Practice's do (Phase 77).
                                        title = stringResource(R.string.nav_tuner),
                                        focusMode = focusMode,
                                        onFocusModeChange = { container.focusMode.value = it },
                                        screenReaderSpeech = container.screenReaderSpeech,
                                    )
                                }
                                Screen.METRONOME -> MetronomeTab(container, settings, tools, gear)
                                Screen.PRACTICE -> PracticeCards(
                                    title = stringResource(R.string.nav_practice),
                                    tools = practiceTools,
                                    lastTime = lastTimeTool(settings.practiceTool, practiceTools),
                                    notation = settings.notationFor(appLanguage()),
                                    savedRoot = settings.practiceRoot,
                                    savedQuality = settings.practiceChordQuality,
                                    savedScaleType = settings.practiceScaleType,
                                    onOpen = openTool,
                                    settingsButton = gear,
                                )
                                Screen.PRACTICE_CHORDS, Screen.PRACTICE_SCALES, Screen.PRACTICE_CAPO, Screen.PRACTICE_EAR_TRAINING, Screen.PRACTICE_PITCH_TRAIL ->
                                    PracticeToolScreen(
                                        container = container,
                                        settings = settings,
                                        tool = checkNotNull(screen.practiceTool),
                                        openedChord = openedChord,
                                        onOpenChord = openChordFromCapo,
                                        onBack = backFromTool,
                                        onOpenTuningEditor = { instrument, customTuningId -> openTuningEditor(instrument, customTuningId, screen) },
                                    )
                                Screen.SETTINGS -> SettingsScreen(
                                    settingsRepository = container.settingsRepository,
                                    appVersion = BuildConfig.VERSION_NAME,
                                    onOpenAbout = { picked = Screen.ABOUT },
                                    languages = BuildConfig.LANGUAGES.split(","),
                                    // Android 13+ redraws the widget itself on the new configuration.
                                    onLanguageChange = { (application as TwangleApplication).redrawWidgets() },
                                    onBack = { picked = settingsOpener },
                                    screenReaderOn = container.screenReaderSpeech.screenReaderOn,
                                    phoneHighContrast = phoneHighContrast,
                                )
                                Screen.ABOUT -> AboutScreen(
                                    appVersion = BuildConfig.VERSION_NAME,
                                    onOpenWebsite = { openWebsite(this@MainActivity, it) },
                                    onRate = { openPlayPage(this@MainActivity, BuildConfig.APPLICATION_ID) },
                                    onBack = { picked = Screen.SETTINGS },
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
     * The bar's Metronome tab (2.0, Phase 69: a tab of its own, no longer a Practice tool): the metronome
     * alone, with no tool button over it; upright the [gear] that opens Settings at its top start.
     * Leaving the tab stops it.
     */
    @Composable
    private fun MetronomeTab(container: AppContainer, settings: AppSettings, tools: ToolSwitches, gear: (@Composable () -> Unit)?) {
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
            savedSubdivision = settings.metronomeSubdivision,
            savedMicRefused = settings.micRefused,
            savedTrainer = settings.metronomeTrainer,
            savedTrainerOn = settings.metronomeTrainerOn,
            header = gear,
            title = stringResource(R.string.nav_metronome),
            screenReaderSpeech = container.screenReaderSpeech,
        )
    }

    /**
     * A Practice tool under the bar (2.0, Phase 69; full screen until 2026-11-03), opened from its card on the Practice tab or the "last
     * time" row: chords, scales, the capo helper or Ear training, each with its own switch (Phase 33, 55).
     * Its back arrow in the top start corner ([onBack]) and Back lead back to the cards (Chords opened from
     * the capo helper back to it). Each tool keeps its own saved UI state while another shows, but for Chords
     * and Scales, one screen that shares it (one root for both). A chord tapped in the capo helper opens in
     * Chords ([onOpenChord]), which then opens on [openedChord]. The picker chip in each tool opens the
     * "What are you tuning?" sheet over it ([PracticeTuningPanel]); its New tuning and Edit open the tuning
     * editor ([onOpenTuningEditor]). Scales gets the drone ([AppContainer.dronePlayer], Phase 48) and its
     * saved choices; it stops the drone itself when it goes. Ear training (Phase 55) gets the reference tones
     * and its own microphone through the tuner's pipeline ([AppContainer.earTrainingListener]), and the same
     * top row as Chords and Capo.
     */
    @Composable
    private fun PracticeToolScreen(
        container: AppContainer,
        settings: AppSettings,
        tool: PracticeTool,
        openedChord: Chord?,
        onOpenChord: (Chord) -> Unit,
        onBack: () -> Unit,
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
        when (tool) {
            PracticeTool.CHORDS, PracticeTool.SCALES -> {
                // On Scales the volume keys set the drone's loudness (the media volume).
                if (tool == PracticeTool.SCALES) MediaVolumeKeys()
                ChordsScreen(
                    tuning = settings.tuning,
                    tuningName = practiceTuningName(settings),
                    spokenTuningName = spokenTuning(settings),
                    notation = settings.notationFor(appLanguage()),
                    savedRoot = openedChord?.root ?: settings.practiceRoot,
                    savedQuality = openedChord?.quality ?: settings.practiceChordQuality,
                    onRootChange = { container.save { setPracticeRoot(it) } },
                    onQualityChange = { container.save { setPracticeChordQuality(it) } },
                    onBack = onBack,
                    scales = tool == PracticeTool.SCALES,
                    savedScaleType = settings.practiceScaleType,
                    onScaleTypeChange = { container.save { setPracticeScaleType(it) } },
                    onPickInstrument = openPanel,
                    drone = container.dronePlayer,
                    a4Hz = settings.a4Hz,
                    savedDrone = DroneChoice(settings.droneSound, settings.droneVoicing, settings.droneLower),
                    onDroneChange = { picked -> container.save { setDrone(picked.sound, picked.voicing, picked.lower) } },
                    leftHanded = settings.leftHanded,
                    // Listen (2.0, Phase 74): the chord name, behind its switch, for the instruments it reads.
                    chordListener = container.chordListener.takeIf { container.toolSwitches.chordNaming && ChordInstruments.offers(settings.instrument) },
                    listenRange = PitchRange.forLowestNote(NoteMapper(settings.a4Hz).frequencyOf(settings.tuning.lowestNote)),
                    micRefused = settings.micRefused,
                    onMicRefused = { if (!settings.micRefused) container.save { setMicRefused(true) } },
                )
            }
            PracticeTool.CAPO -> CapoScreen(
                tuningName = practiceTuningName(settings),
                spokenTuningName = spokenTuning(settings),
                notation = settings.notationFor(appLanguage()),
                savedShapes = settings.practiceCapoShapes,
                savedFret = settings.practiceCapoFret,
                onShapesChange = { container.save { setPracticeCapoShapes(it) } },
                onFretChange = { container.save { setPracticeCapoFret(it) } },
                onOpenChord = onOpenChord,
                onBack = onBack,
                onPickInstrument = openPanel,
                // The banjo's short fifth string needs a capo of its own, so the capo table's sums don't hold for it.
                shortString = settings.tuning.hasShortString,
            )
            PracticeTool.EAR_TRAINING -> {
                // The volume keys set the questions' loudness (the media volume).
                MediaVolumeKeys()
                EarTrainingScreen(
                    listener = container.earTrainingListener,
                    tonePlayer = container.tonePlayer,
                    settingsRepository = container.settingsRepository,
                    notation = settings.notationFor(appLanguage()),
                    header = { end ->
                        BackAndTuningRow(
                            tool = PracticeTool.EAR_TRAINING,
                            onBack = onBack,
                            tuningName = practiceTuningName(settings),
                            spokenTuningName = spokenTuning(settings),
                            onPickInstrument = openPanel,
                            end = end,
                        )
                    },
                    savedSettings = settings,
                    screenReaderSpeech = container.screenReaderSpeech,
                )
            }
            PracticeTool.PITCH_TRAIL -> PitchTrailScreen(
                audioRecorder = container.pitchTrailRecorder,
                settingsRepository = container.settingsRepository,
                // It listens for any note: no instrument under the top row.
                header = {
                    BackAndTuningRow(tool = PracticeTool.PITCH_TRAIL, onBack = onBack, tuningName = null, spokenTuningName = "", onPickInstrument = null)
                },
                saved = settings,
                screenReaderSpeech = container.screenReaderSpeech,
            )
        }
    }

    /**
     * The "What are you tuning?" sheet over a Practice tool (Phase 70): the same sheet as the tuner's, saving
     * what is picked as the tuner does, but without its Any note switch, as the charts need an instrument.
     * A pick also switches the tuner to guitar mode, where the strings show, as a pick in the tuner's sheet does.
     */
    @Composable
    private fun PracticeTuningPanel(
        container: AppContainer,
        settings: AppSettings,
        onDismiss: () -> Unit,
        onOpenTuningEditor: (Instrument, String?) -> Unit,
    ) {
        // Each pick and the switch to guitar mode are one write, so the tuner and the widget never see half of it.
        InstrumentTuningPanel(
            instrument = settings.instrument,
            tuning = settings.tuning,
            onInstrumentChange = { container.save { setInstrument(it, guitarMode = true) } },
            onTuningChange = { container.save { setTuning(it, guitarMode = true) } },
            onDismiss = onDismiss,
            notation = settings.notationFor(appLanguage()),
            customTunings = settings.customTunings,
            onNewTuning = { onOpenTuningEditor(it, null) },
            onEditTuning = { onOpenTuningEditor(it.instrument, it.id) },
            onDeleteTuning = { container.save { deleteCustomTuning(it.id) } },
            onLoweredByChange = { container.save { setLoweredBy(it, guitarMode = true) } },
        )
    }

    /** The picked instrument and tuning as the Practice tools show them, e.g. "Guitar · Standard". */
    @Composable
    private fun practiceTuningName(settings: AppSettings): String =
        stringResource(TunerR.string.tuner_tuning_name, instrumentLabel(settings.instrument), tuningName(settings))

    /** The picked instrument and tuning as TalkBack says them on the picker chip, e.g. "Guitar, Standard" or a custom tuning's name. */
    @Composable
    private fun spokenTuning(settings: AppSettings): String =
        stringResource(TunerR.string.tuner_spoken_picker, instrumentLabel(settings.instrument), rememberNoteNameSpeech().text(tuningName(settings)))

    /** The picked tuning's name: a custom tuning's own, else the preset's ("Standard", "Drop D"); lowered, what it turns into ("Drop C"). */
    @Composable
    private fun tuningName(settings: AppSettings): String =
        tuningLabel(settings.tuning, settings.pickedCustomTuning?.name)

    /** Whether the phone's auto-rotate is on (Android's own setting, readable without a permission). */
    private fun autoRotateOn(): Boolean =
        Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /** While this is in the composition, the volume keys set the media volume (the tone's, the clicks' and the drone's loudness). */
    @Composable
    private fun MediaVolumeKeys() {
        DisposableEffect(Unit) {
            volumeControlStream = AudioManager.STREAM_MUSIC
            onDispose { volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE }
        }
    }

    private companion object {
        // From this short side on, a screen may letterbox an orientation request instead of turning (FocusTurn).
        const val LARGE_SCREEN_DP = 600

        // The saved UI state Chords and Scales share (one screen, one root for both).
        val CHORDS_STATE = Screen.PRACTICE_CHORDS.name

        /** The launch extra a debug or frame-time build takes the lens's look from ([takeLensLook]). */
        const val EXTRA_LENS_LOOK = "lensLook"
    }
}
