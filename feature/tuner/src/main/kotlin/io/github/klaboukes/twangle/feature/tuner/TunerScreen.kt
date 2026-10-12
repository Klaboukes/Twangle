package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.StylePicker
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.tuner.components.ChromaticReadout
import io.github.klaboukes.twangle.feature.tuner.components.InTuneHaptic
import io.github.klaboukes.twangle.feature.tuner.components.ModeSwitch
import io.github.klaboukes.twangle.feature.tuner.components.NeedleGauge
import io.github.klaboukes.twangle.feature.tuner.components.NoteDisplay
import io.github.klaboukes.twangle.feature.tuner.components.NoteReadout
import io.github.klaboukes.twangle.feature.tuner.components.PermissionPrompt
import io.github.klaboukes.twangle.feature.tuner.components.SignalBarsButton
import io.github.klaboukes.twangle.feature.tuner.components.StringLockTip
import io.github.klaboukes.twangle.feature.tuner.components.StringSelector
import io.github.klaboukes.twangle.feature.tuner.components.TipPointer
import io.github.klaboukes.twangle.feature.tuner.components.POINTER_HEIGHT
import io.github.klaboukes.twangle.feature.tuner.components.TuningPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible. The gear button in the corner
 * calls [onOpenSettings], also while the permission prompt is shown; the palette button in the
 * other corner opens the style cards. [savedMode] is the mode in the settings already read, so the
 * first frame shows the right mode instead of switching a moment later; [savedStringLockTipSeen]
 * likewise keeps a dismissed string-lock tip from flashing up at the start, and
 * [savedKeepTwoPaneLandscape] a turned phone from switching layout. The compact layout on its side is
 * used unless the "full layout on its side" setting is on.
 */
@Composable
fun TunerScreen(
    audioRecorder: AudioRecorder,
    settingsRepository: SettingsRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    savedMode: TunerMode = TunerMode.AUTO,
    savedStringLockTipSeen: Boolean = false,
    savedKeepTwoPaneLandscape: Boolean = false,
) {
    val mic = rememberMicPermissionState()
    val viewModel: TunerViewModel = viewModel {
        TunerViewModel(
            audioRecorder,
            settingsRepository,
            savedMode = savedMode,
            savedStringLockTipSeen = savedStringLockTipSeen,
            savedKeepTwoPaneLandscape = savedKeepTwoPaneLandscape,
        )
    }
    val readings = viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel only sends readings that look different, but most of them just move the needle.
    // Composing from the state as its text shows it recomposes only when the text changes; the
    // needle follows the exact cents without recomposing (see NeedleGauge).
    val state by remember(readings) { derivedStateOf(structuralEqualityPolicy()) { readings.value.asShown() } }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val vibrateInTune by viewModel.vibrateInTune.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
    val waveLook by viewModel.waveLook.collectAsStateWithLifecycle()
    val waveBreathesInTune by viewModel.waveBreathesInTune.collectAsStateWithLifecycle()
    val showTuneHint by viewModel.showTuneHint.collectAsStateWithLifecycle()
    val showSignalBars by viewModel.showSignalBars.collectAsStateWithLifecycle()
    val showStringLockTip by viewModel.showStringLockTip.collectAsStateWithLifecycle()
    val keepTwoPaneLandscape by viewModel.keepTwoPaneLandscape.collectAsStateWithLifecycle()
    var showStyles by rememberSaveable { mutableStateOf(false) }
    val granted = mic.status == MicPermissionStatus.GRANTED
    LaunchedEffect(granted) { viewModel.onPermissionChanged(granted) }
    if (granted && keepScreenOn) KeepScreenOn()
    val detected = state as? TunerUiState.Detected
    InTuneHaptic(
        inTune = detected?.inTune == true,
        target = detected?.let { it.noteName + it.octave },
        enabled = vibrateInTune,
    )

    // safeDrawing also keeps clear of the camera cutout, which is at the side when the phone is turned.
    // See-through, so the style's background (drawn behind every screen) shows.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // A see-through container gives no content colour of its own.
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                TunerContent(
                    state = state,
                    needleCents = { (readings.value as? TunerUiState.Detected)?.cents },
                    reception = { (readings.value as? TunerUiState.Detected)?.reception },
                    mode = mode,
                    onModeChange = viewModel::setMode,
                    onTuningChange = viewModel::setTuning,
                    onStringClick = viewModel::toggleString,
                    onRetry = viewModel::retry,
                    display = display,
                    waveLook = waveLook,
                    waveBreathesInTune = waveBreathesInTune,
                    showTuneHint = showTuneHint,
                    showSignalBars = showSignalBars,
                    signalFigures = viewModel.signalFigures,
                    showStringLockTip = showStringLockTip,
                    onStringLockTipDismiss = viewModel::dismissStringLockTip,
                    compactLandscape = !keepTwoPaneLandscape,
                )
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            }
            IconButton(onClick = { showStyles = true }, modifier = Modifier.align(Alignment.TopStart).padding(CORNER_BUTTON_INSET).testTag(TunerTestTags.STYLE)) {
                Icon(painterResource(R.drawable.ic_palette), contentDescription = stringResource(R.string.tuner_change_style))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).padding(CORNER_BUTTON_INSET).testTag(TunerTestTags.SETTINGS)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.tuner_open_settings))
            }
        }
    }
    if (showStyles) StyleSheet(onSelect = viewModel::setStyle, onDismiss = { showStyles = false })
}

/**
 * A bottom sheet with the style cards, so the look can be changed without leaving the tuner. The
 * app restyles behind it as soon as a card is tapped; the sheet stays open to try another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleSheet(onSelect: (VisualStyle) -> Unit, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.styleTokens
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.tuner_style_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            StylePicker(
                selected = tokens.style,
                onSelect = onSelect,
                darkTheme = tokens.dark,
                contentPadding = PaddingValues(horizontal = 24.dp),
            )
        }
    }
}

/**
 * Test tags for UI tests and the Baseline Profile generator (`:baselineprofile`), which finds them as
 * resource ids because `MainActivity` turns on `testTagsAsResourceId`. Tags don't change with the
 * app's language, unlike the visible text.
 */
object TunerTestTags {
    const val SETTINGS = "tuner_settings"
    const val STYLE = "tuner_style"

    /** The wave view. It has nothing for TalkBack to say; the tag lets UI tests find where it sits. */
    const val WAVE = "tuner_wave"

    /** The needle gauge. TalkBack skips it; the tag lets UI tests find where it sits. */
    const val GAUGE = "tuner_gauge"

    /** The optional signal bars by the note, and the figures panel a tap on them opens. */
    const val SIGNAL_BARS = "tuner_signal_bars"
    const val SIGNAL_FIGURES = "tuner_signal_figures"

    /** The compact landscape layout, which shows the controls when tapped. */
    const val COMPACT = "tuner_compact"

    /** The mode switch button for [mode], e.g. "tuner_mode_chromatic". */
    fun mode(mode: TunerMode): String = "tuner_mode_" + mode.name.lowercase()
}

/**
 * Stops the screen from dimming and locking while this is in the composition, i.e. while the tuner
 * is shown. Leaving the screen (or the app) lets it time out normally again.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * The tuner itself, for every [TunerUiState]. Stateless, so previews and UI tests can drive it.
 * [mode] comes straight from the ViewModel, so the switch reacts before the next reading arrives.
 * [needleCents] gives the needle's position, by default the cents of [state]. [TunerScreen] passes the
 * exact cents there and a [state] rounded to what the text shows ([asShown]). [reception] likewise
 * gives how well the note is heard (null without one), which the needle and the wave read while drawing. [display] picks how
 * far off a note is shown under it: in cents (the needle) or with the wave view in [waveLook] (breathing
 * on the note or, without [waveBreathesInTune], holding still there), and
 * [showTuneHint] adds "Tune up ↑" / "Tune down ↓" under an off-pitch note (saved settings). [showSignalBars]
 * adds small signal bars by the note, which open the live [signalFigures] when tapped. [showStringLockTip] shows the
 * one-time tip, a speech balloon pointing at the string row in guitar mode; tapping it calls
 * [onStringLockTipDismiss].
 *
 * It picks its arrangement from the space it gets ([tunerLayoutFor]): one centred column when held
 * upright; on a phone turned sideways and on wide screens, with [compactLandscape], the compact layout
 * (a big meter beside the note, controls hidden until the screen is tapped), else two panes side by
 * side. All state lives in the ViewModel, so rotating keeps the mode, the tuning and a locked string.
 */
@Composable
fun TunerContent(
    state: TunerUiState,
    mode: TunerMode,
    onModeChange: (TunerMode) -> Unit,
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    needleCents: () -> Float? = { (state as? TunerUiState.Detected)?.cents },
    reception: () -> Float? = { (state as? TunerUiState.Detected)?.reception },
    display: TunerDisplay = TunerDisplay.NEEDLE,
    waveLook: WaveLook = WaveLook.DEFAULT,
    waveBreathesInTune: Boolean = true,
    showTuneHint: Boolean = false,
    showSignalBars: Boolean = false,
    signalFigures: Flow<SignalFigures?> = emptyFlow(),
    showStringLockTip: Boolean = false,
    onStringLockTipDismiss: () -> Unit = {},
    compactLandscape: Boolean = true,
) {
    // The wave takes the cents' place, which changes the layout.
    val pictured = display != TunerDisplay.NEEDLE
    // Movable, so the bars keep their count (and an open figures panel stays open) when a note
    // starts or stops, or the layout changes, and the note's display is built afresh around them.
    val currentReception by rememberUpdatedState(reception)
    val currentFigures by rememberUpdatedState(signalFigures)
    val movableSignalBars = remember {
        movableContentOf { tint: Color ->
            SignalBarsButton({ currentReception() }, currentFigures, tint, Modifier.fillMaxSize())
        }
    }
    val signalBars: (@Composable (Color) -> Unit)? = if (showSignalBars) {
        { tint -> movableSignalBars(tint) }
    } else {
        null
    }
    val header = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeSwitch(mode = mode, onModeChange = onModeChange)
            A4Label(state)
        }
    }
    val guitarSlot = @Composable { tipPointer: TipPointer, onMenuOpenChange: (Boolean) -> Unit ->
        val tip = StringLockTipState(showStringLockTip, onStringLockTipDismiss, tipPointer)
        GuitarSlotFor(state, mode, pictured, onTuningChange, onStringClick, tip, onMenuOpenChange)
    }
    val gauge = @Composable {
        NeedleGauge(
            cents = needleCents,
            inTune = (state as? TunerUiState.Detected)?.inTune == true,
            modifier = Modifier.testTag(TunerTestTags.GAUGE),
            reception = reception,
        )
    }
    val note = @Composable { compact: Boolean ->
        when (state) {
            is TunerUiState.Detected -> NoteDisplay(
                readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune, state.hint),
                hint = "",
                display = display,
                compact = compact,
                waveLook = waveLook,
                waveBreathesInTune = waveBreathesInTune,
                showTuneHint = showTuneHint,
                reception = reception,
                tip = state.tip,
                signalBars = signalBars,
            )
            // showTuneHint here too: it keeps the hint's (empty) line, so nothing moves when a note starts or stops.
            is TunerUiState.Listening ->
                NoteDisplay(
                    readout = null,
                    hint = listeningHint(mode, state),
                    spokenHint = listeningHint(mode, state, rememberNoteNameSpeech()),
                    display = display,
                    compact = compact,
                    waveLook = waveLook,
                    waveBreathesInTune = waveBreathesInTune,
                    showTuneHint = showTuneHint,
                    tip = state.tip,
                signalBars = signalBars,
                )
            else -> NoteDisplay(
                readout = null,
                hint = "",
                display = display,
                compact = compact,
                waveLook = waveLook,
                waveBreathesInTune = waveBreathesInTune,
                showTuneHint = showTuneHint,
                signalBars = signalBars,
            )
        }
    }
    val error = (state as? TunerUiState.Error)?.reason

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val twoPane = @Composable { onMenuOpenChange: (Boolean) -> Unit ->
            TwoPaneLayout(
                error = error,
                onRetry = onRetry,
                header = header,
                guitarSlot = { guitarSlot(TipPointer.UP, onMenuOpenChange) },
                gauge = gauge,
                note = note,
                compactNote = pictured || showTuneHint,
            )
        }
        when (tunerLayoutFor(maxWidth.value, maxHeight.value, compactLandscape)) {
            TunerLayout.SINGLE_COLUMN -> Column(
                modifier = Modifier
                    // With a very large system font it can be taller than the screen; then it scrolls.
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                header()
                if (error != null) {
                    TunerErrorMessage(error, onRetry)
                } else {
                    gauge()
                    note(false)
                    // Under the strings there's no room on an upright phone; above the picker there is.
                    guitarSlot(TipPointer.DOWN) {}
                }
            }
            TunerLayout.TWO_PANE -> twoPane {}
            TunerLayout.COMPACT -> {
                var controlsShown by remember { mutableStateOf(false) }
                // Every touch while the controls show, and an open tuning menu, keep them a while longer.
                var touches by remember { mutableIntStateOf(0) }
                var menuOpen by remember { mutableStateOf(false) }
                val accessibility = LocalAccessibilityManager.current
                val shownMillis = remember(accessibility) {
                    // Longer when the user asked Android for more time to act ("Time to take action").
                    accessibility?.calculateRecommendedTimeoutMillis(
                        COMPACT_CONTROLS_SHOWN_MS,
                        containsIcons = true,
                        containsText = true,
                        containsControls = true,
                    ) ?: COMPACT_CONTROLS_SHOWN_MS
                }
                LaunchedEffect(controlsShown, touches, menuOpen) {
                    if (controlsShown && !menuOpen) {
                        delay(shownMillis)
                        controlsShown = false
                    }
                }
                Crossfade(targetState = controlsShown, label = "compactControls") { shown ->
                    if (shown) {
                        Box(Modifier.fillMaxSize().onEveryTouch { touches++ }) {
                            twoPane { menuOpen = it }
                        }
                    } else {
                        CompactLayout(
                            error = error,
                            onRetry = onRetry,
                            gauge = gauge,
                            note = { note(false) },
                            below = { CompactExtraFor(state, mode, pictured) },
                            onShowControls = { controlsShown = true },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Readout pane beside the controls pane: landscape and wide screens, and the [TunerLayout.COMPACT]
 * layout while its controls show. [compactNote] (the wave view, or the tune hint's line)
 * makes the readout taller; on a phone on its side it only fits with a smaller note.
 */
@Composable
private fun TwoPaneLayout(
    error: TunerError?,
    onRetry: () -> Unit,
    header: @Composable () -> Unit,
    guitarSlot: @Composable () -> Unit,
    gauge: @Composable () -> Unit,
    note: @Composable (compact: Boolean) -> Unit,
    compactNote: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        // Readout side. The gauge shrinks to fit the height, so the note stays in view.
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val paneHeight = maxHeight
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).widthIn(max = READOUT_PANE_MAX_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                if (error != null) {
                    TunerErrorMessage(error, onRetry)
                } else {
                    val compact = compactNote && paneHeight < COMPACT_PANE_HEIGHT
                    GaugeAboveNote(gauge = gauge, note = { note(compact) }, targetHeight = paneHeight)
                }
            }
        }
        // Controls side, at the end where the settings gear sits. The vertical padding keeps
        // them clear of the gear when they scroll.
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .padding(vertical = GEAR_CLEARANCE),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                header()
                // The bottom padding (clear of the gear) leaves room for the tip under the strings.
                guitarSlot()
            }
        }
    }
}

/**
 * The compact landscape layout: the [gauge] as big as the space allows, beside the [note] with
 * [below] under it (the small string row or the chromatic numbers). No mode switch, A4 label,
 * tuning picker or string buttons. A tap anywhere, or TalkBack's double tap on the screen's
 * "Show controls" button, calls [onShowControls]. An [error] shows its message and retry instead.
 */
@Composable
private fun CompactLayout(
    error: TunerError?,
    onRetry: () -> Unit,
    gauge: @Composable () -> Unit,
    note: @Composable () -> Unit,
    below: @Composable () -> Unit,
    onShowControls: () -> Unit,
) {
    val showControls = stringResource(R.string.tuner_show_controls)
    val currentOnShowControls by rememberUpdatedState(onShowControls)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TunerTestTags.COMPACT)
            .pointerInput(Unit) { detectTapGestures { currentOnShowControls() } }
            .semantics {
                contentDescription = showControls
                role = Role.Button
                onClick(label = showControls) {
                    currentOnShowControls()
                    true
                }
            }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING, Alignment.CenterHorizontally),
    ) {
        if (error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)) {
                TunerErrorMessage(error, onRetry)
            }
        } else {
            // The note column is measured first; the meter takes all the room that is left.
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                FitGauge(gauge, maxWidth = COMPACT_GAUGE_MAX_WIDTH)
            }
            Column(
                // With a very large system font the note column can be taller than the screen; then it scrolls.
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(COMPACT_NOTE_SPACING),
            ) {
                note()
                below()
            }
        }
    }
}

/**
 * Under the note in the compact layout: in guitar mode a small string row with the target (or
 * locked) string highlighted; in chromatic mode with the wave view the cents and
 * frequency ([ChromaticReadout]), which those views leave out under the note.
 */
@Composable
private fun CompactExtraFor(state: TunerUiState, mode: TunerMode, pictured: Boolean) {
    when {
        mode == TunerMode.CHROMATIC && pictured && state is TunerUiState.Detected ->
            ChromaticReadout(NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune))
        mode == TunerMode.CHROMATIC && pictured && state is TunerUiState.Listening -> ChromaticReadout(readout = null)
        mode == TunerMode.CHROMATIC -> Unit
        state is TunerUiState.Detected -> StringSelector(
            state.tuning,
            state.targetString,
            notation = state.notation,
            inTune = state.inTune,
            lockedString = state.lockedString,
            small = true,
        )
        // A locked string is the target before anything is played, so it stays highlighted.
        state is TunerUiState.Listening -> StringSelector(
            state.tuning,
            state.lockedString,
            notation = state.notation,
            lockedString = state.lockedString,
            small = true,
        )
    }
}

/**
 * The gauge as wide as the space allows (at most [maxWidth]), shrinking (keeping its shape, down to
 * [GAUGE_MIN_WIDTH]) when it would be taller than the space.
 */
@Composable
private fun FitGauge(gauge: @Composable () -> Unit, maxWidth: Dp) {
    Layout(content = gauge) { measurables, constraints ->
        val measurable = measurables.single()
        val width = min(constraints.maxWidth, maxWidth.roundToPx())
        val fullHeight = measurable.minIntrinsicHeight(width)
        val gaugeWidth = if (!constraints.hasBoundedHeight || fullHeight <= constraints.maxHeight) {
            width
        } else {
            // The dial is twice as wide as it is tall; the flat/sharp labels under it keep their height.
            val labels = fullHeight - width / 2
            ((constraints.maxHeight - labels) * 2).coerceIn(min(GAUGE_MIN_WIDTH.roundToPx(), width), width)
        }
        val placeable = measurable.measure(Constraints.fixedWidth(gaugeWidth))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}

/** Calls [onTouch] whenever a finger goes down here, before (and without taking) the touch from what's under it. */
private fun Modifier.onEveryTouch(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
    }
}

/**
 * [gauge] above [note], for the two-pane layout. The note keeps its natural size and the gauge
 * shrinks (keeping its shape, down to [GAUGE_MIN_WIDTH]) so both fit in [targetHeight]: a phone on
 * its side is too short for the full-width gauge. Taller than [targetHeight] only with a very large
 * font, where the pane scrolls.
 */
@Composable
private fun GaugeAboveNote(gauge: @Composable () -> Unit, note: @Composable () -> Unit, targetHeight: Dp) {
    Layout(contents = listOf(gauge, note)) { (gaugeMeasurables, noteMeasurables), constraints ->
        val maxWidth = constraints.maxWidth
        val notePlaceable = noteMeasurables.single().measure(Constraints(maxWidth = maxWidth))
        val gaugeMeasurable = gaugeMeasurables.single()
        val spacing = GAUGE_NOTE_SPACING.roundToPx()

        val available = targetHeight.roundToPx() - notePlaceable.height - spacing
        val fullHeight = gaugeMeasurable.minIntrinsicHeight(maxWidth)
        val gaugeWidth = if (fullHeight <= available) {
            maxWidth
        } else {
            // The dial is twice as wide as it is tall; the flat/sharp labels under it keep their height.
            val labels = fullHeight - maxWidth / 2
            ((available - labels) * 2).coerceIn(min(GAUGE_MIN_WIDTH.roundToPx(), maxWidth), maxWidth)
        }
        val gaugePlaceable = gaugeMeasurable.measure(Constraints.fixedWidth(gaugeWidth))

        val width = max(gaugePlaceable.width, notePlaceable.width)
        layout(width, gaugePlaceable.height + spacing + notePlaceable.height) {
            gaugePlaceable.placeRelative((width - gaugePlaceable.width) / 2, 0)
            notePlaceable.placeRelative((width - notePlaceable.width) / 2, gaugePlaceable.height + spacing)
        }
    }
}

/**
 * The [ModeSlot] for each [TunerUiState]: the tuning picker and string row for guitar mode and, with
 * [pictured] (the wave instead of cents), the [ChromaticReadout] for chromatic mode. With the needle the cents and frequency are
 * already under the note, so chromatic mode leaves the slot empty.
 */
@Composable
private fun GuitarSlotFor(
    state: TunerUiState,
    mode: TunerMode,
    pictured: Boolean,
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
    tip: StringLockTipState,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    val chromatic:(@Composable () -> Unit)? = if (pictured) {
        when (state) {
            is TunerUiState.Detected -> {
                { ChromaticReadout(NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune)) }
            }
            is TunerUiState.Listening -> {
                { ChromaticReadout(readout = null) }
            }
            else -> null
        }
    } else {
        null
    }
    val controls = when (state) {
        // Above the picker the tip would cover the numbers or the hint under the note, so there it
        // steps aside while a note sounds.
        is TunerUiState.Detected -> GuitarControlsState(
            state.tuning, state.notation, state.targetString, state.lockedString,
            tip.copy(shown = tip.shown && tip.pointer == TipPointer.UP), state.inTune,
        )
        // A locked string is the target before anything is played, so it stays highlighted.
        is TunerUiState.Listening ->
            GuitarControlsState(state.tuning, state.notation, state.lockedString, state.lockedString, tip, inTune = false)
        // Errors, and PermissionRequired (only for a moment, until the ViewModel hears that the
        // permission is granted): empty space of the same height.
        else -> null
    }
    // One call site for both Detected and Listening, so the controls stay in the composition when a
    // note starts or stops and an open tuning menu stays open.
    val guitar: (@Composable () -> Unit)? = if (controls != null) {
        {
            GuitarControls(
                controls.tuning, controls.notation, onTuningChange, onStringClick,
                controls.targetString, controls.lockedString, controls.tip, controls.inTune, onMenuOpenChange,
            )
        }
    } else {
        null
    }
    ModeSlot(mode, guitar = guitar, chromatic = chromatic)
}

/** What [GuitarControls] shows for the current [TunerUiState]. */
private data class GuitarControlsState(
    val tuning: Tuning,
    val notation: Notation,
    val targetString: Int?,
    val lockedString: Int?,
    val tip: StringLockTipState,
    val inTune: Boolean,
)

/** Whether the one-time string-lock tip is [shown], what tapping it does, and where it [pointer]s from. */
private data class StringLockTipState(val shown: Boolean, val onDismiss: () -> Unit, val pointer: TipPointer)

/**
 * The tuning picker above the string row: what guitar mode shows in the [ModeSlot]. While [tip] is
 * shown, the string-lock tip floats as a speech balloon over the gap above the picker (pointer down,
 * beside the picker, at the strings) or under the strings (pointer up). It takes no room of its
 * own, so nothing moves when it comes or goes, and it leaves the picker and the strings uncovered.
 */
@Composable
private fun GuitarControls(
    tuning: Tuning,
    notation: Notation,
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
    targetString: Int?,
    lockedString: Int?,
    tip: StringLockTipState,
    inTune: Boolean = false,
    onMenuOpenChange: (Boolean) -> Unit = {},
) {
    val picker = @Composable {
        TuningPicker(selected = tuning, notation = notation, onSelect = onTuningChange, onExpandedChange = onMenuOpenChange)
    }
    val strings = @Composable {
        StringSelector(
            tuning,
            targetString,
            notation = notation,
            inTune = inTune,
            lockedString = lockedString,
            onStringClick = onStringClick,
        )
    }
    val balloon = @Composable {
        AnimatedVisibility(visible = tip.shown, enter = fadeIn(), exit = fadeOut()) {
            StringLockTip(onDismiss = tip.onDismiss, pointer = tip.pointer)
        }
    }
    Layout(contents = listOf(picker, strings, balloon)) { (pickerMeasurables, stringMeasurables, tipMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val pickerPlaceable = pickerMeasurables.single().measure(partConstraints)
        val stringsPlaceable = stringMeasurables.single().measure(partConstraints)
        val gap = PICKER_STRINGS_GAP.roundToPx()
        val width = max(pickerPlaceable.width, stringsPlaceable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (pickerPlaceable.height + gap + stringsPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        // The balloon is no wider than the row it points at, and not counted in the height.
        val tipPlaceable = tipMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        layout(width, height) {
            pickerPlaceable.placeRelative((width - pickerPlaceable.width) / 2, 0)
            stringsPlaceable.placeRelative((width - stringsPlaceable.width) / 2, pickerPlaceable.height + gap)
            if (tipPlaceable != null) {
                val x = (width - tipPlaceable.width) / 2
                val y = when (tip.pointer) {
                    // The body ends where the picker's button starts; the pointer reaches past it.
                    TipPointer.DOWN -> PICKER_TOUCH_INSET.roundToPx() + POINTER_HEIGHT.roundToPx() - tipPlaceable.height
                    TipPointer.UP -> height + TIP_STRINGS_GAP.roundToPx()
                }
                tipPlaceable.placeRelative(x, y, zIndex = 1f)
            }
        }
    }
}

/**
 * The space under the readout that changes with the mode: [guitar] in guitar mode, [chromatic] (or
 * nothing) in chromatic mode, at the bottom of the slot. Both are measured all the time and the slot
 * takes the taller one's height (at least [GUITAR_SLOT_HEIGHT]), so switching modes never moves
 * anything, also with a large system font, where the tuning picker grows. The other mode's content
 * isn't placed, so it is neither drawn nor tappable, and its semantics are cleared, so TalkBack and
 * UI tests don't find it either.
 */
@Composable
private fun ModeSlot(
    mode: TunerMode,
    guitar: (@Composable () -> Unit)?,
    chromatic: (@Composable () -> Unit)?,
) {
    val guitarShown = mode == TunerMode.AUTO
    val guitarPart = @Composable { if (guitar != null) SlotPart(shown = guitarShown, content = guitar) }
    val chromaticPart = @Composable { if (chromatic != null) SlotPart(shown = !guitarShown, content = chromatic) }
    Layout(
        contents = listOf(guitarPart, chromaticPart),
        modifier = Modifier.heightIn(min = GUITAR_SLOT_HEIGHT),
    ) { (guitarMeasurables, chromaticMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val guitarPlaceable = guitarMeasurables.firstOrNull()?.measure(partConstraints)
        val chromaticPlaceable = chromaticMeasurables.firstOrNull()?.measure(partConstraints)
        val parts = listOfNotNull(guitarPlaceable, chromaticPlaceable)
        val width = (parts.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (parts.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val shown = if (guitarShown) guitarPlaceable else chromaticPlaceable
            shown?.placeRelative((width - shown.width) / 2, height - shown.height)
        }
    }
}

/** One mode's content in the [ModeSlot]; while not [shown] TalkBack and UI tests don't see it. */
@Composable
private fun SlotPart(shown: Boolean, content: @Composable () -> Unit) {
    Box(if (shown) Modifier else Modifier.clearAndSetSemantics {}) { content() }
}

private val SECTION_SPACING = 24.dp

/** Between the tuning picker and the string row. */
private val PICKER_STRINGS_GAP = 8.dp

/** The tuning picker's button sits this far inside its 48 dp touch target. */
private val PICKER_TOUCH_INSET = 4.dp

/** Between the string row and a tip under it. */
private val TIP_STRINGS_GAP = 4.dp

/** The single column, and the controls pane, never get wider than this. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On a wide screen the gauge stops growing here. */
private val READOUT_PANE_MAX_WIDTH = 560.dp

/** A phone on its side shrinks the gauge to fit, but not below this. */
private val GAUGE_MIN_WIDTH = 160.dp

/** Readout panes shorter than this (a phone on its side) get the compact note with the wave view or the hint. */
private val COMPACT_PANE_HEIGHT = 420.dp

/** In the compact layout the meter grows with the screen, up to this. */
private val COMPACT_GAUGE_MAX_WIDTH = 840.dp

/** Between the note and what's under it in the compact layout. */
private val COMPACT_NOTE_SPACING = 12.dp

/** Between gauge and note in the two-pane layout, where height is scarce. */
private val GAUGE_NOTE_SPACING = 8.dp

/** The style and settings buttons sit this far in, clear of the frames some styles draw. */
private val CORNER_BUTTON_INSET = 8.dp

/** Room for the settings gear's 48 dp touch target in the top corner. */
private val GEAR_CLEARANCE = 48.dp + CORNER_BUTTON_INSET

/** Tuning picker (a 48 dp touch target) + 8 dp + string row (48 dp). */
private val GUITAR_SLOT_HEIGHT = 104.dp

/** "A4 = 440 Hz", the reference the readings use. Blank (same height) before listening starts. */
@Composable
private fun A4Label(state: TunerUiState) {
    val a4Hz = when (state) {
        is TunerUiState.Listening -> state.a4Hz
        is TunerUiState.Detected -> state.a4Hz
        else -> null
    }
    Text(
        text = if (a4Hz != null) stringResource(R.string.tuner_a4, a4Hz.roundToInt()) else "",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The hint while listening; with [speech], as TalkBack says it ("Play the E flat 2 string"). */
@Composable
private fun listeningHint(mode: TunerMode, state: TunerUiState.Listening, speech: NoteNameSpeech? = null): String {
    val locked = state.lockedString
    return when {
        mode == TunerMode.CHROMATIC -> stringResource(R.string.tuner_hint_play_note)
        locked != null -> {
            val string = state.tuning.strings[locked]
            val name = if (speech == null) {
                state.notation.nameIn(state.tuning, string)
            } else {
                val spokenName = speech.name(state.notation.nameIn(state.tuning, string.pitchClass))
                stringResource(R.string.tuner_spoken_string, spokenName, string.octave)
            }
            stringResource(R.string.tuner_hint_play_locked_string, name)
        }
        else -> stringResource(R.string.tuner_hint_play_string)
    }
}

@Composable
private fun TunerErrorMessage(error: TunerError, onRetry: () -> Unit) {
    Text(
        text = stringResource(
            when (error) {
                TunerError.MIC_PERMISSION_MISSING -> R.string.error_mic_permission
                TunerError.MIC_BUSY -> R.string.error_mic_busy
                TunerError.MIC_UNAVAILABLE -> R.string.error_mic_unavailable
                TunerError.MIC_STOPPED -> R.string.error_mic_stopped
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TunerContentDetectedPreview() {
    MaterialTheme {
        TunerContent(
            state = TunerUiState.Detected(
                mode = TunerMode.AUTO,
                tuning = Tunings.STANDARD,
                noteName = "E",
                octave = 2,
                frequencyHz = 81.84f,
                cents = -12f,
                targetString = 0,
                inTune = false,
            ),
            mode = TunerMode.AUTO,
            onModeChange = {},
            onTuningChange = {},
            onStringClick = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TunerContentListeningPreview() {
    MaterialTheme { TunerContent(
            state = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD),
            mode = TunerMode.CHROMATIC,
            onModeChange = {},
            onTuningChange = {},
            onStringClick = {},
            onRetry = {},
        ) }
}

@Preview(showBackground = true, widthDp = 780, heightDp = 340)
@Composable
private fun TunerContentLandscapePreview() {
    MaterialTheme {
        TunerContent(
            state = TunerUiState.Detected(
                mode = TunerMode.AUTO,
                tuning = Tunings.STANDARD,
                noteName = "A",
                octave = 2,
                frequencyHz = 110.2f,
                cents = 3f,
                targetString = 1,
                inTune = true,
            ),
            mode = TunerMode.AUTO,
            onModeChange = {},
            onTuningChange = {},
            onStringClick = {},
            onRetry = {},
        )
    }
}
