package io.github.klaboukes.twangle.feature.tuner

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.designsystem.FitColumn
import io.github.klaboukes.twangle.core.designsystem.PickerChip
import io.github.klaboukes.twangle.core.designsystem.ScaledUp
import io.github.klaboukes.twangle.core.designsystem.TwangleButton
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberPacedSpeech
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.FocusAction
import io.github.klaboukes.twangle.feature.tuner.components.FocusModeButton
import io.github.klaboukes.twangle.feature.tuner.components.FocusNote
import io.github.klaboukes.twangle.feature.tuner.components.LiveFloat
import io.github.klaboukes.twangle.feature.tuner.components.STRUM_TOGGLE_SIZE
import io.github.klaboukes.twangle.feature.tuner.components.stringFaceInsetFor
import io.github.klaboukes.twangle.feature.tuner.components.strumPanelMinWidth
import io.github.klaboukes.twangle.feature.tuner.components.InTuneHaptic
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import io.github.klaboukes.twangle.feature.tuner.components.LENS_SHORT
import io.github.klaboukes.twangle.feature.tuner.components.trailMotionWords
import io.github.klaboukes.twangle.feature.tuner.components.ScreenTintFrom
import io.github.klaboukes.twangle.feature.tuner.components.TuneWordLine
import io.github.klaboukes.twangle.feature.tuner.components.LiveTone
import io.github.klaboukes.twangle.feature.tuner.components.rememberLiveTone
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.feature.tuner.components.rememberInTuneGlow
import io.github.klaboukes.twangle.feature.tuner.components.NoteDisplay
import io.github.klaboukes.twangle.feature.tuner.components.NoteReadout
import io.github.klaboukes.twangle.feature.tuner.components.PermissionPrompt
import io.github.klaboukes.twangle.feature.tuner.components.RULER_HEIGHT
import io.github.klaboukes.twangle.feature.tuner.components.Ruler
import io.github.klaboukes.twangle.feature.tuner.components.rulerHeardMidi
import io.github.klaboukes.twangle.feature.tuner.components.rulerNames
import io.github.klaboukes.twangle.feature.tuner.components.rulerPosition
import io.github.klaboukes.twangle.feature.tuner.components.rulerRestMidi
import io.github.klaboukes.twangle.feature.tuner.components.SignalBarsButton
import io.github.klaboukes.twangle.feature.tuner.components.InstrumentTuningPanel
import io.github.klaboukes.twangle.feature.tuner.components.StringSelector
import io.github.klaboukes.twangle.feature.tuner.components.StrumCheckToggle
import io.github.klaboukes.twangle.feature.tuner.components.StrumPanel
import io.github.klaboukes.twangle.feature.tuner.components.StrumStatus
import io.github.klaboukes.twangle.feature.tuner.components.ChordPanel
import io.github.klaboukes.twangle.feature.tuner.components.ChordStatus
import io.github.klaboukes.twangle.feature.tuner.components.IntonationPanel
import io.github.klaboukes.twangle.feature.tuner.components.IntonationStatus
import io.github.klaboukes.twangle.feature.tuner.components.MoreToolsButton
import io.github.klaboukes.twangle.feature.tuner.components.StringMark
import io.github.klaboukes.twangle.feature.tuner.components.markOf
import io.github.klaboukes.twangle.feature.tuner.components.instrumentLabel
import io.github.klaboukes.twangle.feature.tuner.components.tuningLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible. Settings and the metronome are
 * reached from the app's navigation bar or rail, which stays in every layout, the compact one too.
 * [saved] are the settings the app already read at its start, so the first frame shows the
 * player's mode, looks, layout and instrument instead of switching from the defaults a moment later
 * ([TunerViewModel]). The picker sheet, "What are you tuning?", shows while [pickerOpen] (the picker
 * chip at the top opens it, calling [onPickerOpenChange] with true) and calls [onPickerOpenChange] with
 * false once it has closed. The compact layout on its side is used only in focus mode
 * (the "Focus mode" setting). The sheet's "New tuning" calls [onOpenTuningEditor] with the instrument and no tuning,
 * and Edit on one of the player's own tunings with that tuning's id; the tuner rests (and lets go of the microphone) while the editor is shown in its place.
 * A long-press on a string plays its note through [tonePlayer]; the tone stops when the tuner leaves
 * the screen (another screen, or the app in the background).
 * Without the microphone the permission prompt shows
 * instead, with the picker chip over it, and the sheet opens over it all the same. The saved
 * [AppSettings.micRefused] tells a dismissed dialog from one Android no longer shows ([MicPermissionRules]).
 * [tools] says which music tools show (Phase 28, decided in `:app`): a mode whose switch is off
 * leaves no trace, so with [ToolSwitches.ALL_OFF] this is the tuner without them. Each new
 * [closeToolsRequest] (a tap on the navigation bar's Tuner tab) turns the tool that is on off.
 * [onFullScreenChange] says when focus mode's compact layout shows without its controls, so the app
 * can hide the rail and the system bars around it.
 * [ratingWindow] is Google Play's own rating window (Phase 50), asked for at most once when
 * [TunerViewModel.ratingDue] says the moment has come, never while the panel is open.
 * [settingsButton] is the app's gear that opens Settings (2.0, Phase 69), given where the screen holds it
 * (upright; on its side it is at the rail's top): at the start of the header row, across from focus
 * mode's button, and in the same top start corner over the permission prompt.
 */
@Composable
fun TunerScreen(
    audioRecorder: AudioRecorder,
    settingsRepository: SettingsRepository,
    tonePlayer: TonePlayer,
    tools: ToolSwitches,
    modifier: Modifier = Modifier,
    saved: AppSettings = AppSettings(),
    onOpenTuningEditor: (instrument: Instrument, customTuningId: String?) -> Unit = { _, _ -> },
    pickerOpen: Boolean = false,
    onPickerOpenChange: (Boolean) -> Unit = {},
    closeToolsRequest: Int = 0,
    onFullScreenChange: (Boolean) -> Unit = {},
    ratingWindow: RatingWindow = RatingWindow.NONE,
    settingsButton: (@Composable () -> Unit)? = null,
) {
    val language = appLanguage()
    val viewModel: TunerViewModel = viewModel {
        TunerViewModel(
            audioRecorder,
            settingsRepository,
            tonePlayer,
            saved = saved,
            language = language,
            tools = tools,
        )
    }
    LaunchedEffect(closeToolsRequest) { viewModel.closeToolsOnRequest(closeToolsRequest) }
    val micRefused by viewModel.micRefused.collectAsStateWithLifecycle()
    val mic = rememberMicPermissionState(refusedBefore = micRefused, onRefused = viewModel::onMicRefused)
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val ratingDue by viewModel.ratingDue.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(ratingDue, pickerOpen) {
        if (ratingDue && !pickerOpen && activity != null) {
            viewModel.onRatingAsked()
            ratingWindow.show(activity)
        }
    }
    val readings = viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel only sends readings that look different, but most of them just move the needle.
    // Composing from the state as its text shows it recomposes only when the text changes; the
    // lens follows the exact cents without recomposing (see Lens).
    val state by remember(readings) { derivedStateOf(structuralEqualityPolicy()) { readings.value.asShown() } }
    // The exact cents and the reception, read while drawing (NaN without a note).
    val exactCents = remember(readings) { LiveFloat { readings.value.let { if (it is TunerUiState.Detected) it.cents else Float.NaN } } }
    val reception = remember(readings) { LiveFloat { readings.value.let { if (it is TunerUiState.Detected) it.reception else Float.NaN } } }
    // Where the exact reading sits on the ruler's scale, read while drawing too, from the same reading as the cents.
    val rulerPitch = remember(readings) {
        LiveFloat { readings.value.let { if (it is TunerUiState.Detected) rulerPosition(it.targetMidi, it.cents) else Float.NaN } }
    }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val instrument by viewModel.instrument.collectAsStateWithLifecycle()
    val customTunings by viewModel.customTunings.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val vibrateInTune by viewModel.vibrateInTune.collectAsStateWithLifecycle()
    val inTunePrecision by viewModel.inTunePrecision.collectAsStateWithLifecycle()
    val stringTickOff by viewModel.stringTickOff.collectAsStateWithLifecycle()
    val showSignalBars by viewModel.showSignalBars.collectAsStateWithLifecycle()
    val focusMode by viewModel.focusMode.collectAsStateWithLifecycle()
    val soundingString by viewModel.soundingString.collectAsStateWithLifecycle()
    val tunedStrings by viewModel.tunedStrings.collectAsStateWithLifecycle()
    val strumCheck by viewModel.strumCheck.collectAsStateWithLifecycle()
    val intonationCheck by viewModel.intonationCheck.collectAsStateWithLifecycle()
    val chordName by viewModel.chordName.collectAsStateWithLifecycle()
    val lensLook by viewModel.lensLook.collectAsStateWithLifecycle()
    val tunerTools by viewModel.tunerTools.collectAsStateWithLifecycle()
    StopToneWhenHidden(viewModel::stopTone)
    val granted = mic.status == MicPermissionStatus.GRANTED
    LaunchedEffect(granted) { viewModel.onPermissionChanged(granted) }
    // Keep screen on lets go after a quiet spell, with a warning first (ScreenAwakeRule).
    val awake = rememberScreenAwake(readings, enabled = granted && keepScreenOn)
    if (granted && keepScreenOn && awake.phase != ScreenAwakePhase.OFF) KeepScreenOn()
    // The microphone refused to open for want of the permission (taken back while the app was open):
    // the prompt comes back, unless Android still says it is granted (then the error stays, with Try again).
    val permissionLost = (state as? TunerUiState.Error)?.reason == TunerError.MIC_PERMISSION_MISSING
    if (granted && permissionLost) LaunchedEffect(Unit) { mic.recheck() }
    val detected = state as? TunerUiState.Detected
    val inTuneTarget = detected?.let { it.noteName + it.octave }
    InTuneHaptic(
        inTune = detected?.inTune == true,
        target = inTuneTarget,
        enabled = vibrateInTune,
    )
    // The meter's in-tune glow lights with the tick and outlasts a brief dropout of the same note,
    // but never while the hint asks to tune it.
    val glowing = rememberInTuneGlow(
        inTune = detected?.inTune == true,
        target = inTuneTarget,
        retuning = detected?.hint?.isDirection == true,
    )
    val animationsOff = rememberAnimationsOff()

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
                .padding(innerPadding)
                .touchesKeepAwake(awake),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                TunerContent(
                    state = state,
                    needleCents = exactCents,
                    reception = reception,
                    rulerPitch = rulerPitch,
                    mode = mode,
                    onModeChange = viewModel::setMode,
                    onTuningChange = viewModel::setTuning,
                    instrument = instrument,
                    onInstrumentChange = viewModel::setInstrument,
                    customTunings = customTunings,
                    onNewTuning = { onOpenTuningEditor(it, null) },
                    onEditTuning = { onOpenTuningEditor(it.instrument, it.id) },
                    onDeleteTuning = { viewModel.deleteCustomTuning(it.id) },
                    onLoweredByChange = viewModel::setLoweredBy,
                    pickerOpen = pickerOpen,
                    onPickerOpenChange = onPickerOpenChange,
                    onStringClick = viewModel::toggleString,
                    onStringLongClick = viewModel::playString,
                    soundingString = soundingString,
                    tunedStrings = tunedStrings,
                    onRetry = viewModel::retry,
                    inTunePrecision = inTunePrecision,
                    meterGlow = glowing,
                    animationsOff = animationsOff,
                    stringTickOff = stringTickOff,
                    showSignalBars = showSignalBars,
                    signalFigures = viewModel.signalFigures,
                    compactLandscape = focusMode,
                    strumCheck = strumCheck,
                    onStrumCheckChange = viewModel::setStrumCheck,
                    tunerTools = tunerTools,
                    intonationCheck = intonationCheck,
                    onIntonationCheckChange = viewModel::setIntonationCheck,
                    onCheckAgain = viewModel::checkIntonationAgain,
                    onNextString = viewModel::checkNextString,
                    chordName = chordName,
                    onChordNameChange = viewModel::setChordName,
                    lensLook = lensLook,
                    pitchHistory = viewModel.pitchHistory,
                    onFullScreenChange = onFullScreenChange,
                    onFocusModeChange = viewModel::setFocusMode,
                    settingsButton = settingsButton,
                )
                val fade = if (animationsOff) 0 else WARNING_FADE_MILLIS
                AnimatedVisibility(
                    visible = keepScreenOn && awake.phase == ScreenAwakePhase.WARNING,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    enter = fadeIn(tween(fade)),
                    exit = fadeOut(tween(fade)),
                ) {
                    ScreenOffWarning(left = awake::warningLeft, onKeepOn = awake::touched, animationsOff = animationsOff)
                }
            } else {
                PermissionPrompt(
                    status = mic.status,
                    onRequest = mic::request,
                    onOpenSettings = mic::openSettings,
                    practiceTab = tools.practiceTab,
                )
                // Settings stays one tap away while the microphone is asked for: the gear in the header's corner.
                if (settingsButton != null) {
                    Box(Modifier.align(Alignment.TopStart).padding(start = COLUMN_PADDING - GEAR_OUTLINE_INSET, top = EDGE_INSET)) { settingsButton() }
                }
                // The picker chip works without the microphone too: the sheet opens over the prompt, and
                // the tuning picked is the one the tuner listens for once allowed.
                val panelTuning by viewModel.panelTuning.collectAsStateWithLifecycle()
                val shown = panelTuning
                if (shown != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = EDGE_INSET)
                            .padding(horizontal = FOCUS_BUTTON_SLOT + COLUMN_PADDING),
                    ) {
                        TunerPickerChip(mode, shown, TuningNames(instrument, customTunings), onClick = { onPickerOpenChange(true) })
                    }
                }
                if (pickerOpen && shown != null) {
                    TuningPanel(
                        instrument = instrument,
                        tuning = shown,
                        mode = mode,
                        onModeChange = viewModel::setMode,
                        onInstrumentChange = viewModel::setInstrument,
                        onTuningChange = viewModel::setTuning,
                        onDismiss = { onPickerOpenChange(false) },
                        customTunings = customTunings,
                        onNewTuning = { onOpenTuningEditor(it, null) },
                        onEditTuning = { onOpenTuningEditor(it.instrument, it.id) },
                        onDeleteTuning = { viewModel.deleteCustomTuning(it.id) },
                        onLoweredByChange = viewModel::setLoweredBy,
                    )
                }
            }
        }
    }
}

/**
 * The picker sheet, "What are you tuning?" ([InstrumentTuningPanel]), for [tuning]. Its Any note
 * switch sets the [mode] (on: [TunerMode.CHROMATIC]); the strings show in guitar mode only, so an
 * instrument, a tuning or a "Lower by" picked in chromatic mode switches to it.
 */
@Composable
private fun TuningPanel(
    instrument: Instrument,
    tuning: PanelTuning,
    mode: TunerMode,
    onModeChange: (TunerMode) -> Unit,
    onInstrumentChange: (Instrument) -> Unit,
    onTuningChange: (Tuning) -> Unit,
    onDismiss: () -> Unit,
    customTunings: List<CustomTuning>,
    onNewTuning: ((Instrument) -> Unit)?,
    onEditTuning: ((CustomTuning) -> Unit)?,
    onDeleteTuning: ((CustomTuning) -> Unit)?,
    onLoweredByChange: ((Int) -> Unit)?,
) {
    val toGuitar = { if (mode != TunerMode.AUTO) onModeChange(TunerMode.AUTO) }
    InstrumentTuningPanel(
        instrument = instrument,
        tuning = tuning.tuning,
        onInstrumentChange = {
            toGuitar()
            onInstrumentChange(it)
        },
        onTuningChange = {
            toGuitar()
            onTuningChange(it)
        },
        onDismiss = onDismiss,
        notation = tuning.notation,
        customTunings = customTunings,
        onNewTuning = onNewTuning,
        onEditTuning = onEditTuning,
        onDeleteTuning = onDeleteTuning,
        onLoweredByChange = onLoweredByChange?.let { lower ->
            { semitones: Int ->
                toGuitar()
                lower(semitones)
            }
        },
        anyNote = mode == TunerMode.CHROMATIC,
        onAnyNoteChange = { on -> onModeChange(if (on) TunerMode.CHROMATIC else TunerMode.AUTO) },
    )
}

/**
 * The picker chip at the top of the tuner ([PickerChip]): in guitar mode the instrument and [tuning]
 * ("Guitar · Drop D", or the player's own tuning's name after the instrument's, [names]), in chromatic
 * [mode] "Any note"; a tap ([onClick]) opens the picker sheet.
 */
@Composable
private fun TunerPickerChip(mode: TunerMode, tuning: PanelTuning, names: TuningNames, onClick: () -> Unit) {
    val anyNote = stringResource(R.string.tuner_any_note)
    val chromatic = mode == TunerMode.CHROMATIC
    PickerChip(
        label = if (chromatic) anyNote else names.labelFor(tuning.tuning, spoken = false),
        spoken = if (chromatic) anyNote else names.labelFor(tuning.tuning, spoken = true),
        onClick = onClick,
        modifier = Modifier.testTag(TunerTestTags.PICKER_CHIP),
    )
}

/**
 * Test tags for UI tests and the Baseline Profile generator (`:baselineprofile`), which finds them as
 * resource ids because `MainActivity` turns on `testTagsAsResourceId`. Tags don't change with the
 * app's language, unlike the visible text.
 */
object TunerTestTags {
    /** A microphone error's message in the tuner's place (a polite live region, so TalkBack says it as it comes). */
    const val ERROR = "tuner_error"

    /**
     * The lens (2.0, Phase 71): its box, inside the room for its glow. TalkBack skips it; the tag lets UI tests
     * find where it sits, and whether it is quiet (`LensQuiet`).
     */
    const val GAUGE = "tuner_gauge"

    /**
     * The ruler at the top (2.0, Phase 71): the chromatic scale under its pointer. TalkBack skips it; UI tests read
     * the note it shows and whether it is quiet from its semantics (`RulerNote`, `RulerQuiet`).
     */
    const val RULER = "tuner_ruler"

    /**
     * The word under the lens (2.0, Phase 71: Tighten, Loosen, In tune, Too low, Too high, Play a string) with the
     * cents under it: [WORD] is the block TalkBack speaks (its description is the spoken reading), [WORD_TEXT] the
     * word as drawn and [WORD_CENTS] the small line under it (the cents, or a tip about the sound).
     */
    const val WORD = "tuner_word"
    const val WORD_TEXT = "tuner_word_text"
    const val WORD_CENTS = "tuner_word_cents"

    /** The optional signal bars by the ruler's heard note (in the chip row while a tool's panel takes the meter's place), and the figures panel a tap on them opens. */
    const val SIGNAL_BARS = "tuner_signal_bars"
    const val SIGNAL_FIGURES = "tuner_signal_figures"

    /** The signal bars as drawn, in their small slot; [SIGNAL_BARS] is the 48 dp button around them. */
    const val SIGNAL_BARS_ICON = "tuner_signal_bars_icon"

    /** The picker sheet, "What are you tuning?", opened from the picker chip. */
    const val PICKER_PANEL = "tuner_picker_panel"

    /** The picker chip at the top of the tuner ("Guitar · Standard ▾", "Any note ▾"), which opens the sheet. */
    const val PICKER_CHIP = "tuner_picker_chip"

    /** The sheet's Any note switch at its bottom (the whole row toggles it). */
    const val ANY_NOTE = "tuner_any_note"

    /** A 12-string's small "8" on a string button with an octave string (TalkBack has it in the button's name). */
    const val OCTAVE_MARK = "tuner_octave_mark"

    /** The quiet line under an instrument's tunings in the panel saying what the tuner can't do for it (the 12-string's). */
    const val PICKER_NOTE = "tuner_picker_note"

    /** The panel's "New tuning" row, which opens the tuning editor. */
    const val NEW_TUNING = "tuner_new_tuning"

    /** The panel's "Lower by" row at its bottom, its amount, and its down and up buttons. */
    const val LOWER_BY = "tuner_lower_by"
    const val LOWERED_AMOUNT = "tuner_lowered_amount"
    const val LOWER_MORE = "tuner_lower_more"
    const val LOWER_LESS = "tuner_lower_less"

    /** The dialog that asks before one of the player's own tunings is deleted. */
    const val DELETE_TUNING_DIALOG = "tuner_delete_tuning_dialog"

    /** One of the player's own tunings in the panel, by its id, and its ⋮ button (Edit, Delete). */
    fun customTuning(id: String): String = "tuner_custom_tuning_$id"
    fun customTuningMenu(id: String): String = "tuner_custom_tuning_menu_$id"

    /** The compact landscape layout, which shows the controls when tapped. */
    const val COMPACT = "tuner_compact"

    /** Focus mode's exit button, in the top end corner of the full-screen tuner. */
    const val EXIT_FOCUS = "tuner_exit_focus"

    /** The button that turns focus mode on, at the end of the picker chip's row. */
    const val ENTER_FOCUS = "tuner_enter_focus"

    /** In its place among focus mode's controls a tap brings up: back to the full-screen tuner. */
    const val HIDE_CONTROLS = "tuner_hide_controls"

    /** Focus mode's small note in the corner, beside the full-screen meter. */
    const val FOCUS_NOTE = "tuner_focus_note"

    /** Strum check: its switch above the strings, its panel in the meter's place, and the line under it. */
    const val STRUM_TOGGLE = "tuner_strum_toggle"
    const val STRUM_PANEL = "tuner_strum_panel"
    const val STRUM_STATUS = "tuner_strum_status"

    /** The switch's name in a tooltip, shown on a long-press. */
    const val STRUM_TOOLTIP = "tuner_strum_tooltip"

    /** String [index]'s column in the strum check's panel. */
    fun strumString(index: Int): String = "tuner_strum_string_$index"

    /** The "more tools" button (tiles and a plus) above the strings, the menu it opens, and each tool's item in it. */
    const val MORE_TOOLS = "tuner_more_tools"
    const val MORE_TOOLS_MENU = "tuner_more_tools_menu"
    fun tool(tool: TunerTool): String = "tuner_tool_" + tool.name.lowercase()

    /** The 12th-fret check: its panel in the meter's place, the line under it, and the verdict's two buttons. */
    const val INTONATION_PANEL = "tuner_intonation_panel"
    const val INTONATION_STATUS = "tuner_intonation_status"
    const val INTONATION_AGAIN = "tuner_intonation_again"
    const val INTONATION_NEXT = "tuner_intonation_next"

    /** The chord name: its panel in the meter's place and the line under it. */
    const val CHORD_PANEL = "tuner_chord_panel"
    const val CHORD_STATUS = "tuner_chord_status"

    /** String [index]'s column in the 12th-fret check's summary. */
    fun intonationString(index: Int): String = "tuner_intonation_string_$index"

    /** Keep screen on's warning before the screen is let go ("Screen turns off soon"). */
    const val SCREEN_OFF_WARNING = "tuner_screen_off_warning"
}

/**
 * Calls [stopTone] when the tuner leaves the screen: the app goes to the background, or another
 * screen (settings, the tuning editor) takes the tuner's place. A tone never outlives the tuner.
 */
@Composable
private fun StopToneWhenHidden(stopTone: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentStopTone by rememberUpdatedState(stopTone)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) currentStopTone() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentStopTone()
        }
    }
}

/** How long Keep screen on's warning takes to fade in and out. */
private const val WARNING_FADE_MILLIS = 400

/**
 * Stops the screen from dimming and locking while this is in the composition: while the tuner is
 * shown and hears playing (see [rememberScreenAwake]). Leaving the screen (or the app) lets it time
 * out normally again.
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
 * [mode] comes straight from the ViewModel, so the chip reacts before the next reading arrives.
 * [needleCents] gives the needle's position, by default the cents of [state] (NaN
 * without a note). [TunerScreen] passes the exact cents there and a [state] rounded to what the text
 * shows ([asShown]). [reception] likewise gives how well the note is heard (NaN without one), which
 * the lens reads while drawing. Both are [LiveFloat]s: read on every frame, boxing nothing. The lens's in-tune glow
 * is lit by [meterGlow] (from [rememberInTuneGlow]; by default while the note is in tune; with [animationsOff] its
 * in-tune colour and glow switch without easing). The frequency is under the note, and under it the word (2.0,
 * Phase 71, [TuneWordLine]: Tighten · Loosen · In tune, Too low · Too high in Any note, "Play a string" while
 * nothing is heard) with the cents small under it, always there: the tuner's one item TalkBack speaks. The
 * reading's tone ([rememberLiveTone]) is the lens's border, the word's colour and, while the app gives a
 * [io.github.klaboukes.twangle.core.designsystem.ScreenTint], the whole screen's tint ([ScreenTintFrom]). [showSignalBars]
 * adds small signal bars at the start of the top row ([ChipRow], across from focus mode's button, which [onFocusModeChange] adds at its end), which open the
 * live [signalFigures] when tapped; focus mode, with its controls hidden, leaves them out. The app's
 * [settingsButton] (the gear, Phase 69) stands at the very start of that row, before the bars. In the
 * row's middle the picker chip (2.0, Phase 70: "Guitar · Drop D ▾", the player's own tuning by its
 * name, "Any note ▾" in chromatic mode) calls [onPickerOpenChange] with true. While [pickerOpen] the
 * picker sheet, "What are you tuning?", shows over the tuner ([InstrumentTuningPanel]), and
 * [onPickerOpenChange] hears false once it has closed. [instrument] is the one picked there (by
 * default the one [state]'s tuning belongs to); picking another calls [onInstrumentChange]. Its Any
 * note switch calls [onModeChange] (the mode switch went in Phase 70); picking an instrument or a
 * tuning in chromatic mode switches to guitar mode, where the strings show. [customTunings] are the player's own tunings (the panel lists the
 * instrument's under its presets, each with Edit, [onEditTuning], and Delete, [onDeleteTuning], after
 * asking), and [onNewTuning], when given, adds "New tuning" to the panel's list; [onLoweredByChange],
 * when given, adds its "Lower by" row, which lowers the picked tuning. A long-press on a string calls [onStringLongClick] (play its
 * note); [soundingString] is the string whose tone sounds now, marked on its button, and while it
 * sounds a tap on any string calls [onStringClick], which stops the tone. The strings are the pegs ([StringSelector]:
 * the one heard ringed in the reading's tone, the locked one with a padlock). [tunedStrings] are the strings
 * tuned since the tuner opened and not played clearly off since, ticked off on their pegs in the plain tuner (not
 * while a tool is on, nor in the compact layout's small row), and only with [stringTickOff] on (the string tick-off,
 * 2.0's beginner's help, off by default); once every string of the tuning shown is ticked, the word says "In tune.
 * Play." while nothing is heard, in the in-tune colour. With [onStrumCheckChange] a
 * switch at the end of the row above the strings turns strum check on and off ([strumCheck]
 * is whether it is on). A [TunerUiState.StrumCheck] state shows its panel in the meter's place and its
 * line in the note's ([StrumPanel], [StrumStatus]); a tap on a string's column there calls [onStringClick] too.
 * Where [tunerTools] holds anything but the strum check alone, one "more tools" button takes the switch's place
 * and opens a menu of them ([MoreToolsButton]); the 12th-fret check is turned on and off with
 * [onIntonationCheckChange] ([intonationCheck] is whether it is on). A [TunerUiState.IntonationCheck]
 * shows its panel and line in the same places ([IntonationPanel], [IntonationStatus]), the verdict's
 * buttons calling [onCheckAgain] and [onNextString], and each string's result on its button. The chord
 * name is turned on and off with [onChordNameChange] ([chordName] is whether it is on); a
 * [TunerUiState.ChordName] shows its panel and line there too ([ChordPanel], [ChordStatus]). In the plain
 * tuner the lens ([Lens], 2.0 Phase 71) stands under the ruler in the meter's place, drawing [lensLook] (the
 * trail for every look until Phase 72) from [pitchHistory], the last seconds of readings (an empty one when
 * none is given); while it is given, the trail's motion ("wavering", "bending up") joins the word's spoken
 * reading ([trailMotionWords]).
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
    onStringLongClick: ((Int) -> Unit)? = null,
    soundingString: Int? = null,
    tunedStrings: TunedStrings? = null,
    needleCents: LiveFloat = LiveFloat { if (state is TunerUiState.Detected) state.cents else Float.NaN },
    reception: LiveFloat = LiveFloat { if (state is TunerUiState.Detected) state.reception else Float.NaN },
    rulerPitch: LiveFloat = LiveFloat { if (state is TunerUiState.Detected) rulerPosition(state.targetMidi, state.cents) else Float.NaN },
    inTunePrecision: InTunePrecision = InTunePrecision.DEFAULT,
    meterGlow: Boolean = (state as? TunerUiState.Detected)?.inTune == true,
    animationsOff: Boolean = false,
    stringTickOff: Boolean = false,
    showSignalBars: Boolean = false,
    signalFigures: Flow<SignalFigures?> = emptyFlow(),
    compactLandscape: Boolean = true,
    instrument: Instrument = instrumentOf(state),
    onInstrumentChange: (Instrument) -> Unit = {},
    customTunings: List<CustomTuning> = emptyList(),
    onNewTuning: ((Instrument) -> Unit)? = null,
    onEditTuning: ((CustomTuning) -> Unit)? = null,
    onDeleteTuning: ((CustomTuning) -> Unit)? = null,
    onLoweredByChange: ((Int) -> Unit)? = null,
    pickerOpen: Boolean = false,
    onPickerOpenChange: (Boolean) -> Unit = {},
    strumCheck: Boolean = false,
    onStrumCheckChange: ((Boolean) -> Unit)? = null,
    tunerTools: List<TunerTool> = listOf(TunerTool.STRUM_CHECK),
    intonationCheck: Boolean = false,
    onIntonationCheckChange: ((Boolean) -> Unit)? = null,
    onCheckAgain: () -> Unit = {},
    onNextString: () -> Unit = {},
    chordName: Boolean = false,
    onChordNameChange: ((Boolean) -> Unit)? = null,
    lensLook: LensLook = LensLook.DEFAULT,
    pitchHistory: PitchHistory? = null,
    onFullScreenChange: (Boolean) -> Unit = {},
    onFocusModeChange: ((Boolean) -> Unit)? = null,
    settingsButton: (@Composable () -> Unit)? = null,
) {
    // Movable, so the bars keep their count (and an open figures panel stays open) when the layout
    // changes and the header is built afresh around them.
    val currentReception by rememberUpdatedState(reception)
    val currentFigures by rememberUpdatedState(signalFigures)
    val movableSignalBars = remember {
        movableContentOf {
            SignalBarsButton({ currentReception() }, currentFigures, MaterialTheme.colorScheme.onSurface, Modifier.fillMaxSize())
        }
    }
    // While focus mode's controls are up over the compact layout, its button takes them away again.
    var hideControls by remember { mutableStateOf<(() -> Unit)?>(null) }
    val shownTuning = when (state) {
        is TunerUiState.Detected -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.Listening -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.StrumCheck -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.IntonationCheck -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.ChordName -> PanelTuning(state.tuning, state.notation)
        else -> null
    }
    // barsInRow: the signal bars in the chip's row, where no ruler stands beside them (a tool's panel, a phone on its side).
    val header = @Composable { barsInRow: Boolean ->
        // The picker chip, top centre (2.0, Phase 70); none over an error, where the sheet can't open.
        val chip = @Composable {
            if (shownTuning != null) {
                TunerPickerChip(mode, shownTuning, TuningNames(instrument, customTunings), onClick = { onPickerOpenChange(true) })
            }
        }
        // Focus mode's button at the end of the chip's row, in both modes in the same place (owner,
        // 2026-10-25, idea A1 of the Phase 43 mock-up: chromatic mode has no strings or tools to stand it on).
        // Among the controls a tap brings up in focus mode it goes back to full screen (owner, 2026-10-19:
        // the exit button there looked as if a tap had already left focus mode); its exit is in the
        // full-screen tuner's corner. On, without the compact layout (a large screen upright), it exits.
        val focusButton = onFocusModeChange?.let { onFocus ->
            @Composable {
                val hide = hideControls
                when {
                    hide != null -> FocusModeButton(FocusAction.HIDE_CONTROLS, onClick = hide)
                    compactLandscape -> FocusModeButton(FocusAction.EXIT, onClick = { onFocus(false) })
                    else -> FocusModeButton(FocusAction.ENTER, onClick = { onFocus(true) })
                }
            }
        }
        ChipRow(
            chip = chip,
            // Beside the ruler's heard note; in the row only while a tool's panel stands in the ruler's and the meter's place.
            bars = if (showSignalBars && barsInRow) ({ movableSignalBars() }) else null,
            button = focusButton,
            gear = settingsButton,
        )
    }
    // The reading's tone, one for the lens's border, the word, the heard peg's ring and the whole screen's tint
    // (Phase 71), so they agree.
    val tone = rememberLiveTone(needleCents, (state as? TunerUiState.Detected)?.inTune == true, inTunePrecision, animationsOff)
    ScreenTintFrom(tone)
    val ticks = tunedStrings.takeIf { stringTickOff }
    val guitarSlot = @Composable {
        val strings = StringActions(onStringClick, onStringLongClick, soundingString, ticks, tone)
        val strum = onStrumCheckChange?.let { onStrum ->
            // Only tools whose switch the screen was given; the strum check's alone is its own switch, as in v1.0.
            val offered = tunerTools.filter {
                when (it) {
                    TunerTool.STRUM_CHECK -> true
                    TunerTool.INTONATION_CHECK -> onIntonationCheckChange != null
                    TunerTool.CHORD_NAME -> onChordNameChange != null
                }
            }
            ToolsState(
                tools = offered,
                active = when {
                    strumCheck -> TunerTool.STRUM_CHECK
                    intonationCheck -> TunerTool.INTONATION_CHECK
                    chordName -> TunerTool.CHORD_NAME
                    else -> null
                },
                onChange = { tool, on ->
                    when (tool) {
                        TunerTool.STRUM_CHECK -> onStrum(on)
                        TunerTool.INTONATION_CHECK -> onIntonationCheckChange?.invoke(on)
                        TunerTool.CHORD_NAME -> onChordNameChange?.invoke(on)
                    }
                },
            )
        }
        GuitarSlotFor(state, mode, strings, strum)
    }
    // The lens's last seconds: the tuner's, or an empty one where none is given (its look then draws no line).
    val emptyHistory = remember { PitchHistory() }
    val history = pitchHistory ?: emptyHistory
    val gauge = @Composable {
        // Strum check, the 12th-fret check and the chord name take the lens's place, at the old meter's shape.
        if (state is TunerUiState.ChordName) {
            ChordPanel(state, Modifier.zIndex(-1f))
        } else if (state is TunerUiState.StrumCheck) {
            StrumPanel(
                state,
                Modifier.zIndex(-1f).testTag(TunerTestTags.STRUM_PANEL),
                onStringClick = onStringClick,
                toneSounding = soundingString != null,
            )
        } else if (state is TunerUiState.IntonationCheck) {
            IntonationPanel(
                state,
                Modifier.zIndex(-1f),
                onStringClick = onStringClick,
                toneSounding = soundingString != null,
            )
        } else {
            Lens(
                look = lensLook,
                cents = needleCents,
                history = history,
                precision = inTunePrecision,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                glow = meterGlow,
                animationsOff = animationsOff,
                // Drawn under its neighbours, with room at its sides for its glow, which reaches past its box.
                modifier = Modifier.zIndex(-1f).padding(horizontal = LENS_INSET).testTag(TunerTestTags.GAUGE),
                reception = reception,
                tone = tone,
            )
        }
    }
    // Kept here, as the bars are: TalkBack's pacing carries over when the note's display is built
    // afresh, so a note fading to "Play a string" (or a short drop-out) isn't said at once.
    val speech = rememberPacedSpeech()
    // The trail's motion, said after the reading while the lens shows the trail (the lens itself is silent).
    val motion = if (pitchHistory != null) trailMotionWords(pitchHistory) else null
    val detected = state as? TunerUiState.Detected
    val readout = detected?.let { NoteReadout(it.noteName, it.octave, it.frequencyHz, it.cents, it.inTune, it.hint) }
    // The word under the lens (Phase 71), the tuner's one speaking item: what to do, the cents under it, or what to play.
    val word = @Composable { compact: Boolean ->
        val listening = state as? TunerUiState.Listening
        // Every string ticked off (the string tick-off on): "In tune. Play." while nothing is heard.
        val allTuned = mode != TunerMode.CHROMATIC && listening != null && ticks.coversAll(listening.tuning)
        TuneWordLine(
            readout = readout,
            hint = detected?.hint,
            chromatic = mode == TunerMode.CHROMATIC,
            quiet = listening?.let { listeningHint(mode, it, soundingString, allTuned = allTuned) }.orEmpty(),
            spokenQuiet = listening?.let { listeningHint(mode, it, soundingString, rememberNoteNameSpeech(), allTuned) }.orEmpty(),
            quietInTune = allTuned && soundingString == null,
            // While a reference tone sounds its line says which, not a tip about the mic.
            tip = detected?.tip ?: listening?.tip?.takeIf { soundingString == null },
            motion = motion,
            tone = tone,
            compact = compact,
            speech = speech,
        )
    }
    val noteFor = @Composable { compact: Boolean, maxHeight: Dp ->
        when (state) {
            is TunerUiState.StrumCheck -> StrumStatus(state, compact = compact)
            is TunerUiState.IntonationCheck -> IntonationStatus(state, onCheckAgain = onCheckAgain, onNextString = onNextString, compact = compact)
            is TunerUiState.ChordName -> ChordStatus(state, compact = compact)
            // One call for a note, the wait for one and the start, so the word keeps what it showed as a note comes.
            else -> NoteDisplay(
                readout = readout,
                compact = compact,
                maxHeight = maxHeight,
                // The ruler above names the note: on a short screen the big one makes room for it (Phase 71).
                noteMayGo = state is TunerUiState.Detected || state is TunerUiState.Listening,
                word = { word(compact) },
            )
        }
    }
    // Focus mode's note: small in the corner, above the full-screen lens; the word under the lens.
    val focusNote = @Composable { FocusNote(readout) }
    val focusWord = @Composable { word(true) }
    val note = @Composable { compact: Boolean, maxHeight: Dp -> noteFor(compact, maxHeight) }
    // The ruler over the meter (2.0, Phase 71) while the tuner reads a note or waits for one; a tool's
    // panel in the meter's place has none. Focus mode's full-screen meter has none until Phase 73 lays it out wide.
    val lastHeard = remember { LastHeardNote() }
    val ruler = rulerFor(state, lastHeard)?.let { shown ->
        @Composable {
            Ruler(
                pitch = rulerPitch,
                names = shown.names,
                rest = shown.rest,
                heard = shown.heard,
                cents = needleCents,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                precision = inTunePrecision,
                animationsOff = animationsOff,
                bars = if (showSignalBars) ({ movableSignalBars() }) else null,
            )
        }
    }
    val rulerAndGauge = @Composable {
        if (ruler == null) {
            gauge()
        } else {
            // Under its neighbours, as the meter is on its own: the meter's glow reaches past its box, behind them.
            Column(Modifier.zIndex(-1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ruler()
                Spacer(Modifier.height(RULER_METER_GAP))
                gauge()
            }
        }
    }
    val error = (state as? TunerUiState.Error)?.reason
    // The plain tuner's lens; with a tool on, its panel takes the lens's place.
    val plainMeter = state is TunerUiState.Detected || state is TunerUiState.Listening

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // On its side the readout pane holds the ruler only where it is tall enough for the ruler, the meter
        // and the note (a tablet; not a phone, whose ruler waits for Phase 73's wide layout without the big note).
        val rulerBesideControls = ruler.takeIf { maxHeight >= TWO_PANE_RULER_MIN_HEIGHT }
        val twoPane = @Composable {
            TwoPaneLayout(
                error = error,
                onRetry = onRetry,
                header = { header(rulerBesideControls == null) },
                guitarSlot = guitarSlot,
                gauge = gauge,
                note = { compact -> note(compact, Dp.Unspecified) },
                // The word's two lines make the readout taller: on a phone on its side it fits with a smaller note.
                compactNote = true,
                ruler = rulerBesideControls,
            )
        }
        val singleColumn = @Composable {
            // On a tablet held upright the whole column is drawn bigger, as on a phone (1 on every phone).
            val scale = columnScaleFor(maxWidth.value, maxHeight.value)
            ScaledUp(scale) {
                SingleColumnLayout(
                    height = maxHeight / scale,
                    error = error,
                    onRetry = onRetry,
                    header = { header(ruler == null) },
                    gauge = rulerAndGauge,
                    note = { maxHeight -> note(false, maxHeight) },
                    noteShrinks = plainMeter,
                    guitarSlot = guitarSlot,
                    overLens = if (ruler != null) RULER_HEIGHT + RULER_METER_GAP else 0.dp,
                )
            }
        }
        val layout = tunerLayoutFor(maxWidth.value, maxHeight.value, compactLandscape)
        val twoPanesFit = maxWidth.value >= TWO_PANE_MIN_WIDTH_DP
        when (layout) {
            TunerLayout.SINGLE_COLUMN -> singleColumn()
            TunerLayout.TWO_PANE -> twoPane()
            TunerLayout.COMPACT -> {
                var controlsShown by remember { mutableStateOf(false) }
                // Every touch while the controls show, and an open instrument and tuning panel, keep them a while longer.
                var touches by remember { mutableIntStateOf(0) }
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
                val showing = controlsShown
                DisposableEffect(Unit) {
                    hideControls = { controlsShown = false }
                    onDispose { hideControls = null }
                }
                LaunchedEffect(controlsShown, touches, pickerOpen) {
                    if (controlsShown && !pickerOpen) {
                        delay(shownMillis)
                        controlsShown = false
                    }
                }
                // Without the controls the tuner takes the whole screen: the app hides the rail and the system bars.
                val currentOnFullScreenChange by rememberUpdatedState(onFullScreenChange)
                LaunchedEffect(showing) { currentOnFullScreenChange(!showing) }
                DisposableEffect(Unit) { onDispose { currentOnFullScreenChange(false) } }
                // Back leaves focus mode, as it leaves a video player's full screen, before it leaves the app.
                if (onFocusModeChange != null) BackHandler { onFocusModeChange(false) }
                Crossfade(targetState = showing, label = "compactControls") { shown ->
                    if (shown) {
                        Box(Modifier.fillMaxSize().onEveryTouch { touches++ }) {
                            // Beside the rail and the phone's navigation buttons a phone on its side may be
                            // too narrow for two panes (578 dp on a Galaxy S26): the controls in one column then.
                            if (twoPanesFit) twoPane() else singleColumn()
                        }
                    } else {
                        CompactLayout(
                            error = error,
                            onRetry = onRetry,
                            gauge = gauge,
                            note = { note(false, Dp.Unspecified) },
                            below = { CompactExtraFor(state, mode, tone) },
                            // The plain tuner gets the meter over the whole screen; a tool keeps its panel beside its own text and buttons.
                            focusNote = if (plainMeter) focusNote else null,
                            focusWord = focusWord,
                            // The strum check's columns are buttons: each keeps a 48 dp touch width.
                            gaugeMinWidth = if (state is TunerUiState.StrumCheck) strumPanelMinWidth(state.tuning.strings.size) else GAUGE_MIN_WIDTH,
                            onShowControls = { controlsShown = true },
                            onExitFocus = onFocusModeChange?.let { { it(false) } },
                        )
                    }
                }
            }
        }
    }
    // Only over the tuner itself, not over an error (it closes then) or the moment before the microphone is heard.
    if (pickerOpen && state is TunerUiState.Error) LaunchedEffect(Unit) { onPickerOpenChange(false) }
    if (pickerOpen && shownTuning != null) {
        TuningPanel(
            instrument = instrument,
            tuning = shownTuning,
            mode = mode,
            onModeChange = onModeChange,
            onInstrumentChange = onInstrumentChange,
            onTuningChange = onTuningChange,
            onDismiss = { onPickerOpenChange(false) },
            customTunings = customTunings,
            onNewTuning = onNewTuning,
            onEditTuning = onEditTuning,
            onDeleteTuning = onDeleteTuning,
            onLoweredByChange = onLoweredByChange,
        )
    }
}

/** What the ruler shows: its 12 [names], where it rests while quiet ([rest]) and the [heard] note (null while quiet). */
private class RulerShown(val names: List<String>, val rest: Int, val heard: Int?)

/** The note the ruler last showed as heard, so it rests there once the string dies away (not state: nothing redraws for it). */
private class LastHeardNote {
    var midi: Int? = null
}

/**
 * What the ruler shows for [state] ([RulerShown]): with a note, the heard note as the screen's text has the
 * reading; waiting for one, where it rests ([rulerRestMidi]: a locked string, the note [lastHeard], the
 * tuning's first string or A4). Null for every other state: the ruler shows only in the plain tuner.
 */
@Composable
private fun rulerFor(state: TunerUiState, lastHeard: LastHeardNote): RulerShown? {
    val mode: TunerMode
    val tuning: Tuning
    val notation: Notation
    val locked: Int?
    when (state) {
        is TunerUiState.Detected -> {
            mode = state.mode
            tuning = state.tuning
            notation = state.notation
            locked = state.lockedString
        }
        is TunerUiState.Listening -> {
            mode = state.mode
            tuning = state.tuning
            notation = state.notation
            locked = state.lockedString
        }
        else -> return null
    }
    val guitar = mode == TunerMode.AUTO
    val names = remember(notation, tuning.spelling, guitar) { rulerNames(notation, tuning, guitar) }
    val heard = (state as? TunerUiState.Detected)?.let { rulerHeardMidi(rulerPosition(it.targetMidi, it.cents)) }
    if (heard != null) SideEffect { lastHeard.midi = heard }
    return RulerShown(names, rest = heard ?: rulerRestMidi(mode, tuning, locked, lastHeard.midi), heard = heard)
}

/**
 * The upright layout, as on the approved mock-up (docs/design/theme-styles-preview.html): the mode
 * switch centred at the top, the meter as wide
 * as the screen less [METER_MARGIN] at each side, the note under it, and the strings (with the strum
 * check's switch above them) at the bottom. The free [height] is shared out one to two above the meter and above the
 * strings, so the meter sits a little high and the gaps look balanced. Everything but the meter keeps
 * the [COLUMN_PADDING] text margin. Where the screen is too short for all that (a narrow phone with
 * navigation buttons), the gaps close up to [SECTION_SPACING] and, with [noteShrinks] (the plain
 * tuner), the note gets smaller (given its room as [note]'s maxHeight) while the meter keeps its full
 * width (owner, 2026-10-21: shrink the note, never the meter). With a tool on, its panel in the
 * meter's place shrinks instead (keeping its shape, down to [GAUGE_MIN_WIDTH]), so the tool's text
 * under it keeps its size. Only with a very large system font is it still taller than the screen,
 * and then it scrolls. The scroll area fills the screen, so
 * the meter's glow fades out on the screen rather than at the column's edge.
 *
 * The plain tuner's [gauge] is the ruler over the lens (2.0, Phase 71; [overLens] is the ruler's height and the gap
 * under it): the lens and the note share what the rest leave ([LensOverNote]), the lens's height giving way first,
 * down to half its width, and only then the note.
 */
@Composable
private fun SingleColumnLayout(
    height: Dp,
    error: TunerError?,
    onRetry: () -> Unit,
    header: @Composable () -> Unit,
    gauge: @Composable () -> Unit,
    note: @Composable (maxHeight: Dp) -> Unit,
    noteShrinks: Boolean,
    guitarSlot: @Composable () -> Unit,
    overLens: Dp = 0.dp,
) {
    val textMargin = Modifier.padding(horizontal = COLUMN_PADDING - METER_MARGIN)
    FitColumn(
        height = height,
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .widthIn(max = COLUMN_MAX_WIDTH)
            .padding(horizontal = METER_MARGIN),
    ) {
        Box(textMargin.padding(top = EDGE_INSET)) { header() }
        Spacer(Modifier.gap(TOP_GAP_WEIGHT))
        Spacer(Modifier.height(SECTION_SPACING))
        if (error != null) {
            Column(textMargin, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)) {
                TunerErrorMessage(error, onRetry)
            }
            Spacer(Modifier.gap(1f))
        } else {
            // On a short screen (a phone with navigation buttons) the note, or a tool's panel, shrinks, so the strings stay in view.
            if (noteShrinks) {
                Shrinking { room -> LensOverNote(room, gauge, overLens) { noteRoom -> Box(textMargin) { note(noteRoom) } } }
            } else {
                Shrinking { room -> FitGauge(gauge, maxWidth = COLUMN_MAX_WIDTH, maxHeight = room) }
                Spacer(Modifier.height(SECTION_SPACING))
                Box(textMargin) { note(Dp.Unspecified) }
            }
            Spacer(Modifier.height(SECTION_SPACING))
            Spacer(Modifier.gap(1f))
            Box(textMargin) { guitarSlot() }
        }
        Spacer(Modifier.height(EDGE_INSET))
    }
}

/**
 * The ruler and the lens ([gauge], [overLens] of it above the lens) over the [note], [SECTION_SPACING] apart, in a
 * [room] that high (2.0, Phase 71; docs/architecture/v2-plan.md, *The lens*, Size). The lens takes the height the
 * note leaves it, from 0.85 down to half its width ([Lens]); only where even half doesn't leave the note its full
 * size is the note told to fit in what remains (it shrinks, and may go: the ruler names the note). The lens's
 * width never gives way (owner: shrink the note, never the meter).
 */
@Composable
private fun LensOverNote(room: Dp, gauge: @Composable () -> Unit, overLens: Dp, note: @Composable (maxHeight: Dp) -> Unit) {
    BoxWithConstraints {
        val lensWidth = (min(maxWidth, COLUMN_MAX_WIDTH) - LENS_INSET * 2).coerceAtLeast(0.dp)
        val noteRoom = (room - overLens - lensWidth * LENS_SHORT - SECTION_SPACING).coerceAtLeast(0.dp)
        Layout(contents = listOf(gauge, { note(noteRoom) })) { (gaugeMeasurables, noteMeasurables), constraints ->
            val across = Constraints(maxWidth = min(constraints.maxWidth, COLUMN_MAX_WIDTH.roundToPx()))
            val notePlaceable = noteMeasurables.single().measure(across)
            val spacing = SECTION_SPACING.roundToPx()
            val leastGauge = (overLens + lensWidth * LENS_SHORT).roundToPx()
            val gaugeHeight = (room.roundToPx() - notePlaceable.height - spacing).coerceAtLeast(leastGauge)
            val gaugePlaceable = gaugeMeasurables.single().measure(across.copy(maxHeight = gaugeHeight))
            val width = maxOf(gaugePlaceable.width, notePlaceable.width)
            layout(width, gaugePlaceable.height + spacing + notePlaceable.height) {
                gaugePlaceable.placeRelative((width - gaugePlaceable.width) / 2, 0)
                notePlaceable.placeRelative((width - notePlaceable.width) / 2, gaugePlaceable.height + spacing)
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
    ruler: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        // Readout side. The gauge shrinks to fit the height, so the note stays in view.
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val paneHeight = maxHeight
            Column(
                // At least the pane's height, so the meter's glow fades out on the screen, not at the scroll area's edge.
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = paneHeight)
                    .widthIn(max = READOUT_PANE_MAX_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING, Alignment.CenterVertically),
            ) {
                if (error != null) {
                    TunerErrorMessage(error, onRetry)
                } else {
                    val compact = compactNote && paneHeight < COMPACT_PANE_HEIGHT
                    // The ruler across the whole pane, not only as wide as the meter that shrinks under it.
                    if (ruler != null) Box(Modifier.zIndex(1f)) { ruler() }
                    val rulerRoom = if (ruler != null) RULER_HEIGHT + SECTION_SPACING else 0.dp
                    // Room above and below for the lens's glow, which the scrolling pane would cut off flat.
                    GaugeAboveNote(gauge = gauge, note = { note(compact) }, targetHeight = paneHeight - rulerRoom - LENS_GLOW_ROOM * 2)
                }
            }
        }
        // Controls side, at the end. The vertical padding keeps a margin when they scroll.
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .padding(vertical = CONTROLS_PANE_CLEARANCE),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderOverSlot(header, guitarSlot)
            }
        }
    }
}

/**
 * The controls pane's [header] (the picker chip, focus mode's button and the signal bars) over the
 * [guitarSlot], [SECTION_SPACING] apart, the header as wide as the string buttons' faces
 * ([StringFacesSpan]), so on its side focus mode's button ends where the last string's button and the
 * tools button do, and the signal bars start where the first string's button does (Phase 65: they stood a
 * few dp further out, the header taking the pane's whole width). Without strings it takes the width.
 */
@Composable
private fun HeaderOverSlot(header: @Composable () -> Unit, guitarSlot: @Composable () -> Unit) {
    val faces = remember { StringFacesSpan() }
    val slotContent = @Composable { CompositionLocalProvider(LocalStringFacesSpan provides faces, guitarSlot) }
    Layout(contents = listOf(header, slotContent)) { (headerMeasurables, slotMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val slot = slotMeasurables.single().measure(loose)
        // Written by the guitar controls as the slot measured them; read here, so a change measures this again.
        val span = faces.width
        val headerConstraints = if (span <= 0 || span > loose.maxWidth) loose else loose.copy(minWidth = span, maxWidth = span)
        val headerPlaceable = headerMeasurables.single().measure(headerConstraints)
        val gap = SECTION_SPACING.roundToPx()
        val width = maxOf(slot.width, headerPlaceable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (headerPlaceable.height + gap + slot.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            headerPlaceable.placeRelative((width - headerPlaceable.width) / 2, 0)
            slot.placeRelative((width - slot.width) / 2, headerPlaceable.height + gap)
        }
    }
}

/**
 * How wide the string buttons' faces span, first face's start to last one's end, in px: the guitar
 * controls write it as they measure, and the header over them on its side ([HeaderOverSlot]) takes
 * that width; 0 without strings. Every part between them is centred, so the width alone places it.
 * Snapshot state, so the header follows a change. (Until Phase 65's last step a vertical alignment
 * line carried it; reading it during measuring left the string buttons' place on screen and their
 * place for touch and TalkBack apart, StringRowStylesTest found it.)
 */
@Stable
private class StringFacesSpan {
    var width by mutableIntStateOf(0)
}

/** The [StringFacesSpan] of the header over the strings on its side; none where nothing reads it. */
private val LocalStringFacesSpan = staticCompositionLocalOf<StringFacesSpan?> { null }

/**
 * The compact landscape layout (focus mode). With a [focusNote] (the plain tuner) the [gauge] fills
 * the screen, the small note in its top start corner ([FocusMeter]). Otherwise (a tool on) the gauge
 * is as big as the space allows beside the [note] with [below] under it (the small string row or the
 * chromatic numbers). No picker chip, strum check switch or string buttons. A tap anywhere, or
 * TalkBack's double tap on the screen's "Show controls" button, calls [onShowControls]. Focus
 * mode's exit button ([onExitFocus]) sits in the top end corner, where every meter look leaves room
 * (owner, 2026-10-19: in place of a "Tap to show the controls" line, as the way out). An [error]
 * shows its message and retry instead.
 */
@Composable
private fun CompactLayout(
    error: TunerError?,
    onRetry: () -> Unit,
    gauge: @Composable () -> Unit,
    note: @Composable () -> Unit,
    below: @Composable () -> Unit,
    gaugeMinWidth: Dp,
    onShowControls: () -> Unit,
    onExitFocus: (() -> Unit)?,
    focusNote: (@Composable () -> Unit)? = null,
    focusWord: @Composable () -> Unit = {},
) {
    val fullMeter = focusNote != null && error == null
    val showControls = stringResource(R.string.tuner_show_controls)
    val currentOnShowControls by rememberUpdatedState(onShowControls)
    Box(
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
            },
    ) {
        if (fullMeter && focusNote != null) {
            FocusMeter(gauge, focusNote, focusWord)
        } else {
            CompactRow(error, onRetry, gauge, note, below, gaugeMinWidth)
        }
        if (onExitFocus != null) {
            // In a box of its own: the button's tooltip wrapper doesn't pass the alignment on.
            Box(Modifier.align(Alignment.TopEnd).padding(top = FOCUS_EDGE_TOP, end = FOCUS_EDGE_SIDE)) {
                FocusModeButton(FocusAction.EXIT, onClick = onExitFocus)
            }
        }
    }
}

/**
 * The picker [chip] centred (2.0, Phase 70, in the mode switch's place), with focus mode's [button] at
 * the row's end, its outline in line with the column's text margin (owner, 2026-10-25: the same place
 * in both modes, never moving), and the signal [bars] at its start, across from the button. The row
 * takes the whole width. The chip keeps clear of two equal slots ([FOCUS_BUTTON_SLOT]) at the row's
 * ends, whether the bars show or not, so it stays centred and never reaches either: a long name at a
 * large font ends in "…" instead. None of them moves with anything the tuner hears, nor when the bars
 * are turned on; the chip alone grows and shrinks with its name, about its centre.
 *
 * The app's [gear] (Settings, Phase 69) stands at the very start, its outline in line with the text
 * margin as the button's is at the end, and the bars right after it; with both, the two end slots
 * grow to [GEAR_AND_BARS_SLOT], so the chip stays centred (until Phase 71 puts the bars beside the
 * ruler's heard note).
 */
@Composable
private fun ChipRow(
    chip: @Composable () -> Unit,
    bars: (@Composable () -> Unit)?,
    button: (@Composable () -> Unit)?,
    gear: (@Composable () -> Unit)? = null,
) {
    val barsSlot = @Composable { if (bars != null) Box(Modifier.size(SIGNAL_BARS_SIZE)) { bars() } }
    val buttonSlot = @Composable { if (button != null) button() }
    val gearSlot = @Composable { if (gear != null) gear() }
    Layout(contents = listOf(chip, barsSlot, buttonSlot, gearSlot)) { (chipMeasurables, barsMeasurables, buttonMeasurables, gearMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttonPlaceable = buttonMeasurables.singleOrNull()?.measure(loose)
        val barsPlaceable = barsMeasurables.singleOrNull()?.measure(loose)
        val gearPlaceable = gearMeasurables.singleOrNull()?.measure(loose)
        val slot = when {
            gearPlaceable != null && barsPlaceable != null -> GEAR_AND_BARS_SLOT.roundToPx()
            buttonPlaceable != null || barsPlaceable != null || gearPlaceable != null -> FOCUS_BUTTON_SLOT.roundToPx()
            else -> 0
        }
        val bounded = constraints.hasBoundedWidth
        val chipRoom = if (bounded) (loose.maxWidth - 2 * slot).coerceAtLeast(0) else Constraints.Infinity
        val chipPlaceable = chipMeasurables.singleOrNull()?.measure(loose.copy(maxWidth = chipRoom))
        val width = if (bounded) constraints.maxWidth else (chipPlaceable?.width ?: 0) + 2 * slot
        val height = maxOf(chipPlaceable?.height ?: 0, barsPlaceable?.height ?: 0, buttonPlaceable?.height ?: 0, gearPlaceable?.height ?: 0)
        layout(width, height) {
            chipPlaceable?.placeRelative((width - chipPlaceable.width) / 2, (height - chipPlaceable.height) / 2)
            // The gear's touch area is wider than its outline; the outline starts at the row's start.
            val gearEnd = gearPlaceable?.let {
                val outlineInset = (it.width - STRUM_TOGGLE_SIZE.roundToPx()).coerceAtLeast(0) / 2
                it.placeRelative(-outlineInset, (height - it.height) / 2)
                it.width - outlineInset
            } ?: 0
            // The bars in the middle of their 48 dp touch area, which starts at the row's start, or after the gear's.
            barsPlaceable?.placeRelative(gearEnd + (SIGNAL_BARS_SLOT.roundToPx() - barsPlaceable.width) / 2, (height - barsPlaceable.height) / 2)
            // The touch area is wider than the outline; the outline ends at the row's end.
            buttonPlaceable?.let {
                val outlineInset = (it.width - STRUM_TOGGLE_SIZE.roundToPx()).coerceAtLeast(0) / 2
                it.placeRelative(width - it.width + outlineInset, (height - it.height) / 2)
            }
        }
    }
}

/**
 * Focus mode's plain tuner: the [gauge] as big as the screen under its top row allows (keeping its 2:1
 * shape, so on a phone on its side its height decides), with [note] small in the top start corner of
 * that row, which is as high as focus mode's exit button in the other corner at least, and the [word] (Phase 71)
 * in the row's middle, so it costs the lens no height. The lens's box fills its corners, so nothing stands over
 * it (until Phase 73 lays focus mode out wide, the word under the lens).
 */
@Composable
private fun FocusMeter(gauge: @Composable () -> Unit, note: @Composable () -> Unit, word: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FOCUS_EDGE_TOP + FOCUS_BUTTON_SLOT)
                .padding(top = FOCUS_EDGE_TOP),
        ) {
            Box(Modifier.align(Alignment.TopStart).padding(start = FOCUS_EDGE_SIDE)) { note() }
            // Clear of the note's corner and the exit button's.
            Box(Modifier.align(Alignment.TopCenter).padding(horizontal = FOCUS_WORD_CLEARANCE)) { word() }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(FOCUS_METER_MARGIN), contentAlignment = Alignment.Center) {
            FitGauge(gauge, maxWidth = FOCUS_GAUGE_MAX_WIDTH)
        }
    }
}

/** Focus mode's word keeps this far from the screen's sides, where the small note and the exit button stand. */
private val FOCUS_WORD_CLEARANCE = 160.dp

/** The compact layout's meter, at least [gaugeMinWidth] wide, beside the note column, or the [error]. */
@Composable
private fun CompactRow(
    error: TunerError?,
    onRetry: () -> Unit,
    gauge: @Composable () -> Unit,
    note: @Composable () -> Unit,
    below: @Composable () -> Unit,
    gaugeMinWidth: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // The note column never takes the meter's last [gaugeMinWidth]: long lines in a wide face at 2× text
        // (Space Age's 12th-fret check in Dutch) would otherwise leave the meter no width at all, and the
        // strum check's columns, which are buttons, too little (Futuristic and Space Age beside the rail).
        val noteMaxWidth = (maxWidth - SECTION_SPACING - gaugeMinWidth).coerceAtLeast(0.dp)
        Row(
            modifier = Modifier.fillMaxSize(),
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
                    // With a very large system font the note column can be taller than the screen; then it scrolls,
                    // clear of focus mode's exit button in the top end corner (and as far from the bottom).
                    modifier = Modifier
                        .widthIn(max = noteMaxWidth)
                        .padding(vertical = COMPACT_CORNER_CLEARANCE)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(COMPACT_NOTE_SPACING),
                ) {
                    note()
                    below()
                }
            }
        }
    }
}

/**
 * Under the note in the compact layout: in guitar mode a small row of pegs with the target (or
 * locked) string ringed in the reading's [tone] and the locked one's padlock; nothing in chromatic mode, where the
 * cents and frequency are under the note.
 */
@Composable
private fun CompactExtraFor(state: TunerUiState, mode: TunerMode, tone: LiveTone) {
    when {
        mode == TunerMode.CHROMATIC -> Unit
        state is TunerUiState.Detected -> StringSelector(
            state.tuning,
            state.targetString,
            notation = state.notation,
            inTune = state.inTune,
            lockedString = state.lockedString,
            small = true,
            tone = tone,
        )
        // A locked string is the target before anything is played, so it stays ringed.
        state is TunerUiState.Listening -> StringSelector(
            state.tuning,
            state.lockedString,
            notation = state.notation,
            lockedString = state.lockedString,
            small = true,
            tone = tone,
        )
        state is TunerUiState.IntonationCheck -> StringSelector(state.tuning, state.string, notation = state.notation, small = true)
    }
}

/**
 * The gauge as wide as the space allows (at most [maxWidth]), shrinking (keeping its shape, down to
 * [GAUGE_MIN_WIDTH]) when it would be taller than the space, or than [maxHeight] where that is given.
 */
@Composable
private fun FitGauge(gauge: @Composable () -> Unit, maxWidth: Dp, maxHeight: Dp = Dp.Unspecified) {
    Layout(content = gauge) { measurables, constraints ->
        val measurable = measurables.single()
        val width = min(constraints.maxWidth, maxWidth.roundToPx())
        val fullHeight = measurable.minIntrinsicHeight(width)
        val height = if (maxHeight.isSpecified) min(maxHeight.roundToPx(), constraints.maxHeight) else constraints.maxHeight
        val gaugeWidth = if (height == Constraints.Infinity || fullHeight <= height) {
            width
        } else {
            // The dial is twice as wide as it is tall (anything the gauge adds under it keeps its height).
            val labels = fullHeight - width / 2
            ((height - labels) * 2).coerceIn(min(GAUGE_MIN_WIDTH.roundToPx(), width), width)
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
            // The dial is twice as wide as it is tall (anything the gauge adds under it keeps its height).
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
 * The [ModeSlot] for each [TunerUiState]: the string row (with the strum check's switch) for guitar mode. The cents
 * and frequency are under the note, so chromatic mode leaves the slot empty.
 */
@Composable
private fun GuitarSlotFor(
    state: TunerUiState,
    mode: TunerMode,
    strings: StringActions,
    strum: ToolsState? = null,
) {
    val controls = when (state) {
        // The strings ticked off only while they belong to the tuning shown (a new one starts afresh).
        is TunerUiState.Detected -> GuitarControlsState(
            state.tuning, state.notation, state.targetString, state.lockedString, state.inTune,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
            tone = strings.tone,
        )
        // A locked string is the target before anything is played, so it stays ringed (in the resting tone).
        is TunerUiState.Listening -> GuitarControlsState(
            state.tuning, state.notation, state.lockedString, state.lockedString, inTune = false,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
            tone = strings.tone,
        )
        // Strum check reads every string at once, the chord name every note: none is the target.
        is TunerUiState.StrumCheck ->
            GuitarControlsState(state.tuning, state.notation, targetString = null, lockedString = null, inTune = false)
        is TunerUiState.ChordName ->
            GuitarControlsState(state.tuning, state.notation, targetString = null, lockedString = null, inTune = false)
        // The 12th-fret check: the string being checked, and every string's result on its button.
        is TunerUiState.IntonationCheck ->
            GuitarControlsState(
                state.tuning, state.notation, targetString = state.string, lockedString = null, inTune = false,
                marks = state.results.map(::markOf), checking = true,
            )
        // Errors, and PermissionRequired (only for a moment, until the ViewModel hears that the
        // permission is granted): empty space of the same height.
        else -> null
    }
    // One call site for both Detected and Listening, so the controls stay in the composition when a
    // note starts or stops.
    val guitar: (@Composable () -> Unit)? = if (controls != null) {
        {
            GuitarControls(
                controls.tuning, controls.notation, strings,
                controls.targetString, controls.lockedString, controls.inTune,
                strum,
                marks = controls.marks,
                checking = controls.checking,
                tuned = controls.tuned,
                tone = controls.tone,
            )
        }
    } else {
        null
    }
    ModeSlot(mode, guitar = guitar, chromatic = null)
}

/** What [GuitarControls] shows for the current [TunerUiState]. */
private data class GuitarControlsState(
    val tuning: Tuning,
    val notation: Notation,
    val targetString: Int?,
    val lockedString: Int?,
    val inTune: Boolean,
    val marks: List<StringMark?>? = null,
    val checking: Boolean = false,
    val tuned: TunedStrings? = null,
    val tone: LiveTone? = null,
)

/**
 * What the pegs do: a tap ([onClick]: lock or unlock, or stop a sounding tone), a long-press
 * ([onLongClick]: play the string's note, none without it), which string's tone is [sounding],
 * which strings are ticked off as tuned ([tuned], only with the string tick-off on), and the reading's [tone] the
 * heard peg is ringed in.
 */
private data class StringActions(
    val onClick: (Int) -> Unit,
    val onLongClick: ((Int) -> Unit)?,
    val sounding: Int?,
    val tuned: TunedStrings? = null,
    val tone: LiveTone? = null,
)

/** Whether these ticks are of [tuning] and tick off every one of its strings. */
private fun TunedStrings?.coversAll(tuning: Tuning): Boolean =
    this != null && this.tuning == tuning && tuning.strings.indices.all { it in this }

/** The instrument [state]'s tuning belongs to; the default guitar without a tuning. */
private fun instrumentOf(state: TunerUiState): Instrument {
    val tuning = when (state) {
        is TunerUiState.Listening -> state.tuning
        is TunerUiState.Detected -> state.tuning
        else -> null
    }
    return tuning?.let { Instruments.ofTuning(it.id) } ?: Instruments.DEFAULT
}

/**
 * What the picker chip's name is made of: the [instrument] picked and the player's own
 * [customTunings], whose names are their own (the presets' come from the string resources).
 */
private data class TuningNames(val instrument: Instrument, val customTunings: List<CustomTuning>)

/** "Guitar · Drop D" for [tuning], or the player's own tuning's name after the instrument's. */
@Composable
private fun TuningNames.labelFor(tuning: Tuning, spoken: Boolean): String {
    val tuningName = tuningLabel(tuning, customTunings.firstOrNull { it.id == tuning.id }?.name)
    return if (spoken) {
        stringResource(R.string.tuner_spoken_picker, instrumentLabel(instrument), rememberNoteNameSpeech().text(tuningName))
    } else {
        stringResource(R.string.tuner_tuning_name, instrumentLabel(instrument), tuningName)
    }
}

/**
 * The tools the tuner offers ([tools]: the strum check, and in builds with its switch the 12th-fret
 * check), the one that is [active] (null: none), and what turning one on or off calls ([onChange]).
 */
private data class ToolsState(val tools: List<TunerTool>, val active: TunerTool?, val onChange: (TunerTool, Boolean) -> Unit)

private val STRUM_CHECK_ALONE = listOf(TunerTool.STRUM_CHECK)

/**
 * The string row: what guitar mode shows in the [ModeSlot]. With [strum] the strum check's switch (or
 * the "more tools" button) sits in a row of its own above the strings, at its end, its outline ending
 * in line with the last string's button ([stringFaceInsetFor]). The instrument button and the
 * tuning's name that started this row until Phase 70 went: the picker chip at the top says and opens
 * the same.
 */
@Composable
private fun GuitarControls(
    tuning: Tuning,
    notation: Notation,
    stringActions: StringActions,
    targetString: Int?,
    lockedString: Int?,
    inTune: Boolean = false,
    strum: ToolsState? = null,
    marks: List<StringMark?>? = null,
    checking: Boolean = false,
    tuned: TunedStrings? = null,
    tone: LiveTone? = null,
) {
    val strings = @Composable {
        StringSelector(
            tuning,
            targetString,
            notation = notation,
            inTune = inTune,
            lockedString = lockedString,
            onStringClick = stringActions.onClick,
            onStringLongClick = stringActions.onLongClick,
            soundingString = stringActions.sounding,
            marks = marks,
            checking = checking,
            tuned = tuned,
            tone = tone,
        )
    }
    val toggle = @Composable {
        when {
            strum == null || strum.tools.isEmpty() -> Unit
            // The strum check alone keeps its own switch, exactly as in v1.0.
            strum.tools == STRUM_CHECK_ALONE -> StrumCheckToggle(strum.active == TunerTool.STRUM_CHECK, { strum.onChange(TunerTool.STRUM_CHECK, it) })
            // Any other tool, or more than one (the 12-string has no strum check): one "more tools"
            // button gathers them, in the switch's place.
            else -> MoreToolsButton(strum.tools, strum.active, strum.onChange)
        }
    }
    val facesSpan = LocalStringFacesSpan.current
    // Without the strings, the header over them takes its own width again.
    if (facesSpan != null) DisposableEffect(facesSpan) { onDispose { facesSpan.width = 0 } }
    Layout(contents = listOf(strings, toggle)) { (stringMeasurables, toggleMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val stringsPlaceable = stringMeasurables.single().measure(partConstraints)
        val togglePlaceable = toggleMeasurables.firstOrNull()?.measure(partConstraints)
        val width = stringsPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val stringsX = (width - stringsPlaceable.width) / 2
        // The first and last buttons' faces: the row spreads them evenly, the same inset at both ends.
        val faceInset = stringFaceInsetFor(stringsPlaceable.width.toDp(), tuning).dp.roundToPx().coerceAtLeast(0)
        val facesStart = stringsX + faceInset
        val facesEnd = stringsX + stringsPlaceable.width - faceInset
        // The switch's outline sits inside its 48 dp touch target and ends where the last string's face does.
        val toggleX = togglePlaceable?.let {
            val outlineInset = (it.width - STRUM_TOGGLE_SIZE.roundToPx()).coerceAtLeast(0) / 2
            (facesEnd - it.width + outlineInset).coerceIn(0, (width - it.width).coerceAtLeast(0))
        }
        val rowHeight = togglePlaceable?.height ?: 0
        val stringsY = if (rowHeight > 0) rowHeight + TOGGLE_STRINGS_GAP.roundToPx() else 0
        val height = (stringsY + stringsPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        // For the header over the strings on its side: as wide as the faces (they are centred, as it is).
        facesSpan?.width = (facesEnd - facesStart).coerceAtLeast(0)
        layout(width, height) {
            if (togglePlaceable != null && toggleX != null) togglePlaceable.placeRelative(toggleX, 0)
            stringsPlaceable.placeRelative(stringsX, stringsY)
        }
    }
}

/**
 * The space under the readout that changes with the mode: [guitar] in guitar mode, [chromatic] (or
 * nothing) in chromatic mode, at the bottom of the slot. Both are measured all the time and the slot
 * takes the taller one's height (at least [GUITAR_SLOT_HEIGHT]), so switching modes never moves
 * anything, also with a large system font, where the strings grow. The other mode's content
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
        // Both parts are measured in either mode, so the guitar controls tell the header over the slot on
        // its side the faces' width in chromatic mode too ([StringFacesSpan]), and its edges stay put.
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

/** The least height of the two-pane layout that shows the ruler over the meter and the note: a tablet on its side, not a phone. */
private val TWO_PANE_RULER_MIN_HEIGHT = 480.dp

/** Between the ruler and the meter under it. */
private val RULER_METER_GAP = 12.dp

/** Between the strum check's switch and the string row under it. */
private val TOGGLE_STRINGS_GAP = 8.dp

/** The signal bars as drawn, and their touch area's width at the picker chip row's start. */
private val SIGNAL_BARS_SIZE = 24.dp
private val SIGNAL_BARS_SLOT = 48.dp

/** The single column's text margin at each side. */
private val COLUMN_PADDING = 16.dp

/** The upright meter's margin at each side of the screen (the approved mock-up's 8 dp). */
private val METER_MARGIN = 8.dp

/** The lens's sides, this far in from the meter's margin: 20 dp from the screen's edges (*The lens*, Size). */
private val LENS_INSET = 12.dp

/** On its side the readout pane leaves this much above and below the lens and the note, for the lens's glow. */
private val LENS_GLOW_ROOM = 24.dp

/** The free height above the meter, against 1 above the strings (the mock-up's `.grow.top`). */
private const val TOP_GAP_WEIGHT = 0.5f

/** The single column, and the controls pane, never get wider than this. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On a wide screen the gauge stops growing here. */
private val READOUT_PANE_MAX_WIDTH = 560.dp

/** A phone on its side shrinks the gauge to fit, but not below this. */
private val GAUGE_MIN_WIDTH = 160.dp

/** Readout panes shorter than this (a phone on its side) get the compact note with the wave view or the hint. */
private val COMPACT_PANE_HEIGHT = 420.dp

/** The compact layout's "Tap to show the controls" fades in and out this gently. */

/** In the compact layout the meter grows with the screen, up to this. */
private val COMPACT_GAUGE_MAX_WIDTH = 840.dp

/** Focus mode's full-screen meter: the margin round it, and how wide it may grow (a tablet on its side). */
private val FOCUS_METER_MARGIN = 8.dp
private val FOCUS_GAUGE_MAX_WIDTH = 1200.dp

/** The room at each end of the picker chip's row: focus mode's 44 dp button outline and 8 dp clear of the chip; the signal bars' 48 dp touch area fits it too. */
private val FOCUS_BUTTON_SLOT = 52.dp

/** The room at each end of that row with the app's gear and the signal bars both at its start: the gear's 46 dp past the margin, the bars' 48 dp touch area less what clears it. */
private val GEAR_AND_BARS_SLOT = 92.dp

/** The gear's 48 dp touch area reaches this far past its 44 dp outline at each side. */
private val GEAR_OUTLINE_INSET = 2.dp

/** What sits in the compact layout's top corners (the note, focus mode's exit button) keeps this far from the screen's edges. */
private val FOCUS_EDGE_SIDE = 16.dp
private val FOCUS_EDGE_TOP = 8.dp

/** Above and below the compact layout's note column: focus mode's exit button (48 dp at 8 dp from the top) stays clear. */
private val COMPACT_CORNER_CLEARANCE = 56.dp

/** Between the note and what's under it in the compact layout. */
private val COMPACT_NOTE_SPACING = 12.dp

/** Between gauge and note in the two-pane layout, where height is scarce. */
private val GAUGE_NOTE_SPACING = 8.dp

/** The upright column keeps this far from the top and bottom of its space. */
private val EDGE_INSET = 8.dp

/** Above and below the controls pane's column, so it keeps a margin when it scrolls (a large font on a phone on its side). */
private val CONTROLS_PANE_CLEARANCE = 56.dp

/** The tools button (a 48 dp touch target) + 8 dp + string row (48 dp). */
private val GUITAR_SLOT_HEIGHT = 104.dp

/** The hint while listening; with [speech], as TalkBack says it ("Play the E flat 2 string"). */
@Composable
private fun listeningHint(
    mode: TunerMode,
    state: TunerUiState.Listening,
    sounding: Int? = null,
    speech: NoteNameSpeech? = null,
    allTuned: Boolean = false,
): String {
    val locked = state.lockedString
    // The string whose reference tone sounds now: "Playing D3", in the player's note names.
    val playing = sounding?.let { state.tuning.strings.getOrNull(it) }
    return when {
        playing != null -> stringResource(R.string.tuner_hint_playing, stringName(state, playing, speech))
        mode == TunerMode.CHROMATIC -> stringResource(R.string.tuner_hint_play_note)
        // Every string ticked off (the string tick-off): the guitar is tuned, a locked string too.
        allTuned -> stringResource(R.string.tuner_hint_all_in_tune)
        locked != null -> stringResource(R.string.tuner_hint_play_locked_string, stringName(state, state.tuning.strings[locked], speech))
        else -> stringResource(R.string.tuner_hint_play_string)
    }
}

/** [string] of [state]'s tuning named in the player's notation: "D3", or with [speech] as TalkBack says it ("D 3"). */
@Composable
private fun stringName(state: TunerUiState.Listening, string: Note, speech: NoteNameSpeech?): String =
    if (speech == null) {
        state.notation.nameIn(state.tuning, string)
    } else {
        val spokenName = speech.name(state.notation.nameIn(state.tuning, string.pitchClass))
        stringResource(R.string.tuner_spoken_string, spokenName, string.octave)
    }

/**
 * Why the tuner stopped listening and what to do, with Try again under it. A polite live region, so
 * TalkBack says it as it comes: a call or another app taking the microphone used to stop the readings
 * without a word (Phase 65).
 */
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
        modifier = Modifier
            .testTag(TunerTestTags.ERROR)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    TwangleButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
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
