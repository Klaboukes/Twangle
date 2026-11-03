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
import androidx.compose.ui.semantics.hideFromAccessibility
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
import io.github.klaboukes.twangle.core.designsystem.musicText
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
import io.github.klaboukes.twangle.feature.tuner.components.LiveFloat
import io.github.klaboukes.twangle.feature.tuner.components.TUNER_BUTTON_FACE
import io.github.klaboukes.twangle.feature.tuner.components.shortStringGapFor
import io.github.klaboukes.twangle.feature.tuner.components.stringFaceInsetFor
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
import io.github.klaboukes.twangle.feature.tuner.components.MARK_GAP
import io.github.klaboukes.twangle.feature.tuner.components.MARK_SIZE
import io.github.klaboukes.twangle.feature.tuner.components.StrumMark
import io.github.klaboukes.twangle.feature.tuner.components.StrumCheckButton
import io.github.klaboukes.twangle.feature.tuner.components.StrumPanel
import io.github.klaboukes.twangle.feature.tuner.components.strumCountText
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.feature.tuner.components.IntonationPanel
import io.github.klaboukes.twangle.feature.tuner.components.IntonationStatus
import io.github.klaboukes.twangle.feature.tuner.components.StringMark
import io.github.klaboukes.twangle.feature.tuner.components.markOf
import io.github.klaboukes.twangle.feature.tuner.components.instrumentLabel
import io.github.klaboukes.twangle.feature.tuner.components.tuningLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible. Settings and the metronome are
 * reached from the app's navigation bar or rail, which stays in every layout, the compact one too.
 * [saved] are the settings the app already read at its start, so the first frame shows the
 * player's mode, looks, layout and instrument instead of switching from the defaults a moment later
 * ([TunerViewModel]). The picker sheet, "What are you tuning?", shows while [pickerOpen] (the picker
 * chip at the top opens it, calling [onPickerOpenChange] with true) and calls [onPickerOpenChange] with
 * false once it has closed. [focusMode] is whether focus mode is on (2.0, Phase 73: the app holds it, not saved;
 * one button turns it on, [onFocusModeChange] with true, and its exit button and Back off). The sheet's "New tuning" calls [onOpenTuningEditor] with the instrument and no tuning,
 * and Edit on one of the player's own tunings with that tuning's id; the tuner rests (and lets go of the microphone) while the editor is shown in its place.
 * A long-press on a string plays its note through [tonePlayer]; the tone stops when the tuner leaves
 * the screen (another screen, or the app in the background).
 * Without the microphone the permission prompt shows
 * instead, with the picker chip over it, and the sheet opens over it all the same. The saved
 * [AppSettings.micRefused] tells a dismissed dialog from one Android no longer shows ([MicPermissionRules]).
 * [tools] says which music tools show (Phase 28, decided in `:app`): a mode whose switch is off
 * leaves no trace, so with [ToolSwitches.ALL_OFF] this is the tuner without them. Each new
 * [closeToolsRequest] (a tap on the navigation bar's Tuner tab) turns the tool that is on off.
 * [onFullScreenChange] says when focus mode's tuner shows without its controls, so the app
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
    focusMode: Boolean = false,
    onFocusModeChange: ((Boolean) -> Unit)? = null,
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
    val soundingString by viewModel.soundingString.collectAsStateWithLifecycle()
    val tunedStrings by viewModel.tunedStrings.collectAsStateWithLifecycle()
    val intonationCheck by viewModel.intonationCheck.collectAsStateWithLifecycle()
    val strumCheck by viewModel.strumCheck.collectAsStateWithLifecycle()
    val heard by viewModel.heardStrum.collectAsStateWithLifecycle()
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
                    focusMode = focusMode,
                    heard = heard,
                    tunerTools = tunerTools,
                    intonationCheck = intonationCheck,
                    onIntonationCheckChange = viewModel::setIntonationCheck,
                    strumCheck = strumCheck,
                    onStrumCheckChange = viewModel::setStrumCheck,
                    onStrumCheckClose = viewModel::closeStrumCheck,
                    onCheckAgain = viewModel::checkIntonationAgain,
                    onNextString = viewModel::checkNextString,
                    lensLook = lensLook,
                    pitchHistory = viewModel.pitchHistory,
                    onFullScreenChange = onFullScreenChange,
                    onFocusModeChange = onFocusModeChange,
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
 * instrument, a tuning or a "Lower by" picked in chromatic mode switches to it. With [onIntonationCheck]
 * its last row starts the 12th-fret check.
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
    onIntonationCheck: (() -> Unit)? = null,
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
        onIntonationCheck = onIntonationCheck,
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

    /** The optional signal bars by the ruler's heard note (in the chip row while a tool's panel takes the lens's place), and the figures panel a tap on them opens. */
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

    /** Focus mode's full-screen tuner (the wide layout without its controls, or the 12th-fret check's panel), which shows the controls when tapped. */
    const val COMPACT = "tuner_compact"

    /** The wide layout (2.0, Phase 73): a window wider than tall, the ruler, the lens and the word, with the pegs or in focus mode without. */
    const val WIDE = "tuner_wide"

    /** In focus mode's wide layout, the string heard (or locked), small at the end of the word's line (TalkBack hears it in the word). */
    const val FOCUS_STRING = "tuner_focus_string"

    /** Focus mode's exit button, in the top end corner of the full-screen tuner. */
    const val EXIT_FOCUS = "tuner_exit_focus"

    /** The button that turns focus mode on, at the end of the picker chip's row (top right, upright and on its side). */
    const val ENTER_FOCUS = "tuner_enter_focus"

    /** In its place among focus mode's controls a tap brings up: back to the full-screen tuner. */
    const val HIDE_CONTROLS = "tuner_hide_controls"

    /** After a strum of the open strings (2.0, Phase 74): string [index]'s mark above its peg (↑, ↓, ✓ or –). */
    fun strumMark(index: Int): String = "tuner_strum_mark_$index"

    /** The strum check's button above the pegs, at their end (2.0, Phase 74, after the owner's guitar check). */
    const val STRUM_BUTTON = "tuner_strum_button"

    /** The strum check's panel in the lens's place, its × and string [index]'s column (1.1's panel, the owner's pick of 2026-11-03). */
    const val STRUM_PANEL = "tuner_strum_panel"
    const val STRUM_CLOSE = "tuner_strum_close"
    fun strumString(index: Int): String = "tuner_strum_string_$index"

    /** The 12th-fret check's way in (2.0, Phase 71): the picker sheet's last row in guitar mode. */
    const val INTONATION_ROW = "tuner_intonation_row"

    /** The 12th-fret check's way out: the small × in its panel's corner. */
    const val INTONATION_CLOSE = "tuner_intonation_close"

    /** The 12th-fret check: its panel in the meter's place, the line under it, and the verdict's two buttons. */
    const val INTONATION_PANEL = "tuner_intonation_panel"
    const val INTONATION_STATUS = "tuner_intonation_status"
    const val INTONATION_AGAIN = "tuner_intonation_again"
    const val INTONATION_NEXT = "tuner_intonation_next"

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
 * in-tune colour and glow switch without easing). The ruler at the top names the note (2.0, Phase 71: the big note
 * and the hertz went), the lens under it says how far, and under the lens the word ([TuneWordLine]: Tighten ·
 * Loosen · In tune, Too low · Too high in Any note, "Play a string" while nothing is heard) with the cents small
 * under it, always there: the tuner's one item TalkBack speaks. The
 * reading's tone ([rememberLiveTone]) is the lens's border, the word's colour and, while the app gives a
 * [io.github.klaboukes.twangle.core.designsystem.ScreenTint], the whole screen's tint ([ScreenTintFrom]). [showSignalBars]
 * adds small signal bars beside the ruler's heard note (at the start of the top row, [ChipRow], while a tool's panel
 * stands in the ruler's and the lens's place), which open the live [signalFigures] when tapped; focus mode, with its
 * controls hidden, leaves them out. The app's [settingsButton] (the gear, Phase 69) stands at the very start of the
 * top row, across from focus mode's button, which [onFocusModeChange] adds at its end. In the
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
 * Play." while nothing is heard, in the in-tune colour. Where [tunerTools] holds the strum check and
 * [onStrumCheckChange] is given, its button stands above the pegs at their end ([StrumCheckButton], [strumCheck]
 * whether it is on; until its strum the word says "Strum all the open strings"). What the tuner [heard] besides one
 * string ([TunerViewModel.heardStrum]), a strum of the open strings, shows in the strum check's panel in the lens's
 * place ([StrumPanel]) and, while no string is heard, "4 of 6 in tune" in the word's place ([strumCountText]). Where
 * [tunerTools] holds the 12th-fret check (an instrument with frets, in a build with the check) and
 * [onIntonationCheckChange] is given, the picker sheet ends in its row in guitar mode while it is off
 * (`IntonationCheckRow`, 2.0 Phase 71: the owner's pick B, 2026-11-03), which closes the sheet and turns it on
 * ([intonationCheck] is whether it is on). A [TunerUiState.IntonationCheck] shows its panel in the lens's place and
 * its lines in the word's ([IntonationPanel], [IntonationStatus]), the verdict's buttons calling [onCheckAgain] and
 * [onNextString]; the pegs pick the string ([onStringClick]) and carry each string's result. The panel's × and Back
 * turn it off again. In the plain
 * tuner the lens ([Lens], 2.0 Phase 71) stands under the ruler, drawing [lensLook] (the
 * trail for every look until Phase 72) from [pitchHistory], the last seconds of readings (an empty one when
 * none is given); while it is given, the trail's motion ("wavering", "bending up") joins the word's spoken
 * reading ([trailMotionWords]).
 *
 * It picks its arrangement from the space it gets ([tunerLayoutFor]): one centred column when held upright; any
 * window wider than tall the wide layout (2.0, Phase 73, [WideLayout]): the ruler across the full width, the lens
 * wide, the word under it and the pegs, or in [focusMode] nothing but the ruler, the lens and the word with the
 * string small beside it, until a tap brings the pegs and the tabs back for a few seconds. The 12th-fret check's panel
 * keeps 1.1's arrangements on its side (two panes, in focus mode the compact row). All state lives in the
 * ViewModel, so rotating keeps the mode, the tuning and a locked string.
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
    focusMode: Boolean = false,
    instrument: Instrument = instrumentOf(state),
    onInstrumentChange: (Instrument) -> Unit = {},
    customTunings: List<CustomTuning> = emptyList(),
    onNewTuning: ((Instrument) -> Unit)? = null,
    onEditTuning: ((CustomTuning) -> Unit)? = null,
    onDeleteTuning: ((CustomTuning) -> Unit)? = null,
    onLoweredByChange: ((Int) -> Unit)? = null,
    pickerOpen: Boolean = false,
    onPickerOpenChange: (Boolean) -> Unit = {},
    heard: HeardStrum = HeardStrum.None,
    tunerTools: List<TunerTool> = emptyList(),
    intonationCheck: Boolean = false,
    onIntonationCheckChange: ((Boolean) -> Unit)? = null,
    strumCheck: Boolean = false,
    onStrumCheckChange: ((Boolean) -> Unit)? = null,
    onStrumCheckClose: (() -> Unit)? = null,
    onCheckAgain: () -> Unit = {},
    onNextString: () -> Unit = {},
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
    // While focus mode's controls are up, its button takes them away again.
    var hideControls by remember { mutableStateOf<(() -> Unit)?>(null) }
    val shownTuning = when (state) {
        is TunerUiState.Detected -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.Listening -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.IntonationCheck -> PanelTuning(state.tuning, state.notation)
        else -> null
    }
    // barsInRow: the signal bars in the chip's row, where no ruler stands beside them (the 12th-fret check's panel).
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
        // the exit button there looked as if a tap had already left focus mode); its exit is in the same
        // corner of the full-screen tuner. On, upright (a large screen keeping its rotation lock), it exits.
        val focusButton = onFocusModeChange?.let { onFocus ->
            @Composable {
                val hide = hideControls
                when {
                    hide != null -> FocusModeButton(FocusAction.HIDE_CONTROLS, onClick = hide)
                    focusMode -> FocusModeButton(FocusAction.EXIT, onClick = { onFocus(false) })
                    else -> FocusModeButton(FocusAction.ENTER, onClick = { onFocus(true) })
                }
            }
        }
        ChipRow(
            chip = chip,
            // Beside the ruler's heard note; in the row only while a tool's panel stands in the ruler's and the lens's place.
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
    // What the tuner heard besides one string (Phase 74): a strum of the open strings.
    val strumShown = (heard as? HeardStrum.OpenStrings)?.check?.takeIf { it.strings.isNotEmpty() && it.tuning == shownTuning?.tuning && mode == TunerMode.AUTO }
    // The strum check's panel in the lens's place (1.1's columns, the owner's pick of 2026-11-03): from the button's tap,
    // empty until the strum, then its strings, the ones played on their own after it following the tuner.
    val strumPanel = shownTuning?.takeIf { mode == TunerMode.AUTO && (state is TunerUiState.Detected || state is TunerUiState.Listening) }?.let { shown ->
        strumShown ?: TunerUiState.StrumCheck(
            tuning = shown.tuning,
            notation = shown.notation,
            phase = StrumPhase.WAITING,
            strings = emptyList(),
            toleranceCents = StrumCheckInterpreter.toleranceFor(inTunePrecision),
        ).takeIf { strumCheck }
    }
    // wide: the pegs in the wide layout's one row (Phase 73).
    val guitarSlotWith = @Composable { wide: Boolean ->
        // No marks over the pegs: the panel's columns show the strum (the marks stay for the other layouts' tests).
        val strings = StringActions(onStringClick, onStringLongClick, soundingString, ticks, tone, null)
        // The strum check's button above the pegs at their end (beside them, after the last, on its side).
        // Not in Any note, where the pegs are measured but not shown (the row keeps its height without it).
        val strumButton = onStrumCheckChange?.takeIf { TunerTool.STRUM_CHECK in tunerTools && mode == TunerMode.AUTO }?.let { onStrum ->
            @Composable { StrumCheckButton(on = strumCheck, onChange = onStrum) }
        }
        GuitarSlotFor(state, mode, strings, strumButton, wide = wide)
    }
    val guitarSlot = @Composable { guitarSlotWith(false) }
    // The lens's last seconds: the tuner's, or an empty one where none is given (its look then draws no line).
    val emptyHistory = remember { PitchHistory() }
    val history = pitchHistory ?: emptyHistory
    val gauge = @Composable {
        // The 12th-fret check takes the lens's place, at the old meter's shape.
        if (state is TunerUiState.IntonationCheck) {
            // The pegs below pick the string; the panel's one control is its ×, which ends the check.
            IntonationPanel(state, Modifier.zIndex(-1f), onClose = onIntonationCheckChange?.let { { it(false) } })
        } else if (strumPanel != null) {
            // The strum check takes the lens's place; its × closes it, as the 12th-fret check's does.
            StrumPanel(strumPanel, Modifier.zIndex(-1f), onClose = onStrumCheckClose)
        } else {
            Lens(
                look = lensLook,
                cents = needleCents,
                history = history,
                precision = inTunePrecision,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                glow = meterGlow,
                animationsOff = animationsOff,
                // A window wider than tall gets the lens's wide shape and its looks drawn wide.
                wide = LocalLensWide.current,
                // Drawn under its neighbours, with room at its sides for its glow, which reaches past its box.
                modifier = Modifier.zIndex(-1f).padding(horizontal = LENS_INSET).testTag(TunerTestTags.GAUGE),
                reception = reception,
                tone = tone,
            )
        }
    }
    // Kept here, as the bars are: TalkBack's pacing carries over when the word is built afresh (another layout),
    // so a note fading to "Play a string" (or a short drop-out) isn't said at once.
    val speech = rememberPacedSpeech()
    // The trail's motion, said after the reading while the lens shows the trail (the lens itself is silent).
    val motion = if (pitchHistory != null) trailMotionWords(pitchHistory) else null
    val detected = state as? TunerUiState.Detected
    val readout = detected?.let { NoteReadout(it.noteName, it.octave, it.cents, it.inTune, it.hint) }
    // The word under the lens (Phase 71), the tuner's one speaking item: what to do, the cents under it, or what to play.
    val word = @Composable { compact: Boolean, start: Boolean ->
        val listening = state as? TunerUiState.Listening
        // Every string ticked off (the string tick-off on): "In tune. Play." while nothing is heard.
        val allTuned = mode != TunerMode.CHROMATIC && listening != null && ticks.coversAll(listening.tuning)
        // While no string is heard (and no reference tone says which plays) the strum's count stands in the word's
        // place (Phase 74).
        val noteSpeech = rememberNoteNameSpeech()
        // The strum check's button on and no strum read yet: what to do.
        val strumWaiting = stringResource(R.string.tuner_strum_waiting)
        val heardQuiet = listening?.takeIf { soundingString == null }?.let {
            strumShown?.let { check -> strumCountText(check)?.let { count -> HeardWords(count, count) } }
                ?: HeardWords(strumWaiting, strumWaiting).takeIf { strumCheck && mode == TunerMode.AUTO }
        }
        // Every string of the strum in tune: its line in the in-tune colour, as the tick-off's "In tune. Play." is.
        val strumAllInTune = heardQuiet != null && strumShown != null && strumShown.strings.all { it.verdict == StrumVerdict.IN_TUNE }
        TuneWordLine(
            readout = readout,
            hint = detected?.hint,
            chromatic = mode == TunerMode.CHROMATIC,
            quiet = heardQuiet?.shown ?: listening?.let { listeningHint(mode, it, soundingString, allTuned = allTuned) }.orEmpty(),
            spokenQuiet = heardQuiet?.spoken ?: listening?.let { listeningHint(mode, it, soundingString, noteSpeech, allTuned) }.orEmpty(),
            quietInTune = (allTuned && soundingString == null && heardQuiet == null) || strumAllInTune,
            // While a reference tone sounds its line says which, not a tip about the mic.
            // Nor while a strum's count stands there: a strum's many notes read as noise to the tuner.
            tip = detected?.tip ?: listening?.tip?.takeIf { soundingString == null && heardQuiet == null },
            motion = motion,
            tone = tone,
            compact = compact,
            start = start,
            speech = speech,
        )
    }
    // Under the lens the word; under a tool's panel the tool's own lines.
    val status = @Composable { compact: Boolean ->
        when (state) {
            is TunerUiState.IntonationCheck -> IntonationStatus(state, onCheckAgain = onCheckAgain, onNextString = onNextString, compact = compact)
            // One call for a note, the wait for one and the start, so the word keeps what it showed as a note comes.
            else -> word(compact, false)
        }
    }
    // The ruler over the lens (2.0, Phase 71) while the tuner reads a note or waits for one: it names the note
    // (the big note went in the phase's last step). A tool's panel in the lens's place has none.
    val lastHeard = remember { LastHeardNote() }
    // None over the strum check's panel either: it names its strings itself.
    val rulerShown = rulerFor(state, lastHeard)?.takeIf { strumPanel == null }
    val rulerWith = @Composable { bars: Boolean, edgeClear: Dp ->
        if (rulerShown != null) {
            Ruler(
                pitch = rulerPitch,
                names = rulerShown.names,
                rest = rulerShown.rest,
                heard = rulerShown.heard,
                cents = needleCents,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                precision = inTunePrecision,
                animationsOff = animationsOff,
                bars = if (showSignalBars && bars) ({ movableSignalBars() }) else null,
                edgeClear = edgeClear,
            )
        }
    }
    val ruler = rulerShown?.let { @Composable { rulerWith(true, 0.dp) } }
    val rulerAndGauge = @Composable {
        if (ruler == null) {
            gauge()
        } else {
            // Under its neighbours, as the lens is on its own: the lens's glow reaches past its box, behind them.
            Column(Modifier.zIndex(-1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ruler()
                Spacer(Modifier.height(RULER_METER_GAP))
                gauge()
            }
        }
    }
    val error = (state as? TunerUiState.Error)?.reason
    // The plain tuner's lens; with a tool on, its panel takes the lens's place.
    val plainMeter = (state is TunerUiState.Detected || state is TunerUiState.Listening) && strumPanel == null

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val twoPane = @Composable {
            TwoPaneLayout(
                error = error,
                onRetry = onRetry,
                // The signal bars beside the ruler's heard note; in the chip's row only while a tool's panel stands in their place.
                header = { header(ruler == null) },
                guitarSlot = guitarSlot,
                gauge = gauge,
                status = status,
                ruler = ruler,
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
                    status = { status(false) },
                    lensGivesWay = plainMeter,
                    guitarSlot = guitarSlot,
                    overLens = if (ruler != null) RULER_HEIGHT + RULER_METER_GAP else 0.dp,
                )
            }
        }
        val windowHeight = maxHeight
        // The wide layout (Phase 73) with the pegs, or in focus mode without: the ruler, the lens and the word, the
        // string small beside the word (no signal bars there: they open a panel, a control).
        val wide = @Composable { focus: Boolean ->
            WideLayout(
                height = windowHeight,
                error = error,
                onRetry = onRetry,
                header = if (focus) null else ({ header(ruler == null) }),
                ruler = rulerShown?.let { if (focus) ({ rulerWith(false, WIDE_RULER_CLEAR) }) else ruler },
                gauge = gauge,
                word = { word(true, true) },
                pegs = if (focus) null else ({ guitarSlotWith(true) }),
                string = if (focus) ({ FocusString(state) }) else null,
            )
        }
        // The 12th-fret check's and the strum check's panels are laid out as in 1.1 on its side.
        val toolPanel = state is TunerUiState.IntonationCheck || strumPanel != null
        val controls = @Composable {
            when (tunerLayoutFor(maxWidth.value, maxHeight.value, focusMode = false, toolPanel = toolPanel)) {
                TunerLayout.WIDE -> wide(false)
                TunerLayout.TWO_PANE -> twoPane()
                else -> singleColumn()
            }
        }
        val layout = tunerLayoutFor(maxWidth.value, maxHeight.value, focusMode, toolPanel)
        CompositionLocalProvider(LocalLensWide provides (maxWidth > maxHeight)) {
            when {
                !focusMode || layout == TunerLayout.SINGLE_COLUMN -> controls()
                else -> {
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
                            // The same layout with its controls: the pegs and the picker chip, the rail beside it.
                            Box(Modifier.fillMaxSize().onEveryTouch { touches++ }) { controls() }
                        } else {
                            CompactLayout(
                                onShowControls = { controlsShown = true },
                                onExitFocus = onFocusModeChange?.let { { it(false) } },
                            ) {
                                if (layout == TunerLayout.WIDE) {
                                    wide(true)
                                } else {
                                    // The 12th-fret check keeps its panel beside its own lines and buttons.
                                    CompactRow(
                                        error = error,
                                        onRetry = onRetry,
                                        gauge = gauge,
                                        status = { status(false) },
                                        below = { CompactPegsFor(state) },
                                        gaugeMinWidth = GAUGE_MIN_WIDTH,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Back ends the 12th-fret check, as its × does, before it leaves the app (composed last, so it is asked first).
    if (intonationCheck && onIntonationCheckChange != null) BackHandler { onIntonationCheckChange(false) }
    // Back closes the strum check's panel too, as its × does.
    if (strumPanel != null && onStrumCheckClose != null) BackHandler { onStrumCheckClose() }
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
            // Its row only in guitar mode, while it is off, for an instrument it is offered for.
            onIntonationCheck = onIntonationCheckChange
                ?.takeIf { TunerTool.INTONATION_CHECK in tunerTools && mode == TunerMode.AUTO && !intonationCheck }
                ?.let { start -> { start(true) } },
        )
    }
}

/** A chord's words in the word's place: as [shown], and as TalkBack says them ([spoken], "B flat major"). */
private class HeardWords(val shown: String, val spoken: String)

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
 * The upright layout (2.0, Phase 71, the tuner mock-up's combination, docs/design/v2-tuner-screens-preview.html):
 * the header row (the gear, the picker chip and focus mode's button) at the top, the ruler and the lens under it
 * ([gauge]: the lens as wide as the screen less 20 dp each side), the word under the lens ([status]) and the pegs
 * (with the tools' buttons above them) at the bottom. The free [height] is shared out one to two above the lens and
 * above the pegs, so the lens sits a little high and the gaps look balanced. Everything but the lens keeps the
 * [COLUMN_PADDING] text margin.
 *
 * With [lensGivesWay] (the plain tuner) the ruler and the lens take what the rest leave ([LensOverWord]): the lens
 * 0.85 times as high as wide where there is room, its height giving way on a short screen down to half its width;
 * its width never does (owner: shrink the note, never the meter; the note went, so the lens keeps its width). With a
 * tool on, its panel in the lens's place shrinks instead (keeping its 2:1 shape, down to [GAUGE_MIN_WIDTH]), so the
 * tool's lines under it keep their size. Only with a very large system font is it still taller than the screen, and
 * then it scrolls. The scroll area fills the screen, so the lens's glow fades out on the screen rather than at the
 * column's edge. [overLens] is the ruler's height and the gap under it.
 */
@Composable
private fun SingleColumnLayout(
    height: Dp,
    error: TunerError?,
    onRetry: () -> Unit,
    header: @Composable () -> Unit,
    gauge: @Composable () -> Unit,
    status: @Composable () -> Unit,
    lensGivesWay: Boolean,
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
            // On a short screen (a phone with navigation buttons) the lens's height, or a tool's panel, gives way, so the pegs stay in view.
            if (lensGivesWay) {
                Shrinking { room -> LensOverWord(room, gauge, overLens) { Box(textMargin) { status() } } }
            } else {
                Shrinking { room -> FitGauge(gauge, maxWidth = COLUMN_MAX_WIDTH, maxHeight = room) }
                Spacer(Modifier.height(SECTION_SPACING))
                Box(textMargin) { status() }
            }
            Spacer(Modifier.height(SECTION_SPACING))
            Spacer(Modifier.gap(1f))
            Box(textMargin) { guitarSlot() }
        }
        Spacer(Modifier.height(EDGE_INSET))
    }
}

/**
 * The ruler and the lens ([gauge], [overLens] of it above the lens) over the [word], [SECTION_SPACING] apart, in a
 * [room] that high (2.0, Phase 71; docs/architecture/v2-plan.md, *The lens*, Size). The word keeps its height; the
 * lens takes the rest, from 0.85 down to half its width ([Lens]), and at half it stays, the column then scrolling.
 */
@Composable
private fun LensOverWord(room: Dp, gauge: @Composable () -> Unit, overLens: Dp, word: @Composable () -> Unit) {
    Layout(contents = listOf(gauge, word)) { (gaugeMeasurables, wordMeasurables), constraints ->
        val across = Constraints(maxWidth = min(constraints.maxWidth, COLUMN_MAX_WIDTH.roundToPx()))
        val wordPlaceable = wordMeasurables.single().measure(across)
        val spacing = SECTION_SPACING.roundToPx()
        val lensWidth = (across.maxWidth - (LENS_INSET * 2).roundToPx()).coerceAtLeast(0)
        val leastGauge = overLens.roundToPx() + (lensWidth * LENS_SHORT).roundToInt()
        val gaugeHeight = (room.roundToPx() - wordPlaceable.height - spacing).coerceAtLeast(leastGauge)
        val gaugePlaceable = gaugeMeasurables.single().measure(across.copy(maxHeight = gaugeHeight))
        val width = maxOf(gaugePlaceable.width, wordPlaceable.width)
        layout(width, gaugePlaceable.height + spacing + wordPlaceable.height) {
            gaugePlaceable.placeRelative((width - gaugePlaceable.width) / 2, 0)
            wordPlaceable.placeRelative((width - wordPlaceable.width) / 2, gaugePlaceable.height + spacing)
        }
    }
}

/**
 * The readout pane beside the controls pane: a window wider than tall (a phone or a tablet on its side), and the
 * [TunerLayout.COMPACT] layout while its controls show. The readout pane holds the [ruler] (the plain tuner's), the
 * [gauge] (the lens, or a tool's panel) and the [status] under it (the word, or the tool's lines; set a little
 * smaller in a pane shorter than [COMPACT_PANE_HEIGHT]); the gauge shrinks, keeping its 2:1 shape, so they fit the
 * pane's height. The controls pane holds the [header] over the [guitarSlot]. Phase 73 lays this out wide.
 */
@Composable
private fun TwoPaneLayout(
    error: TunerError?,
    onRetry: () -> Unit,
    header: @Composable () -> Unit,
    guitarSlot: @Composable () -> Unit,
    gauge: @Composable () -> Unit,
    status: @Composable (compact: Boolean) -> Unit,
    ruler: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        // Readout side. The gauge shrinks to fit the height, so the word stays in view.
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val paneHeight = maxHeight
            Column(
                // At least the pane's height, so the lens's glow fades out on the screen, not at the scroll area's edge.
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
                    val compact = paneHeight < COMPACT_PANE_HEIGHT
                    // The ruler across the whole pane, not only as wide as the lens that shrinks under it.
                    if (ruler != null) Box(Modifier.zIndex(1f)) { ruler() }
                    val rulerRoom = if (ruler != null) RULER_HEIGHT + SECTION_SPACING else 0.dp
                    // Room above and below for the lens's glow, which the scrolling pane would cut off flat.
                    GaugeAboveStatus(gauge = gauge, status = { status(compact) }, targetHeight = paneHeight - rulerRoom - LENS_GLOW_ROOM * 2)
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

/** Whether the tuner's window is wider than tall: the lens then has its wide shape and draws its look wide (Phase 72). */
private val LocalLensWide = staticCompositionLocalOf { false }

/**
 * Focus mode's full-screen tuner without its controls, around its [content]: the wide layout with nothing but the
 * ruler, the lens and the word (2.0, Phase 73), or the 12th-fret check's panel beside its lines ([CompactRow]).
 * No picker chip, tools' buttons or pegs. A tap anywhere, or TalkBack's double tap on the screen's "Show controls"
 * button, calls [onShowControls]. Focus mode's exit button ([onExitFocus]) sits in the top end corner, where the
 * button that turned it on is (owner, 2026-10-19: in place of a "Tap to show the controls" line, as the way out).
 */
@Composable
private fun CompactLayout(
    onShowControls: () -> Unit,
    onExitFocus: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
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
        content()
        if (onExitFocus != null) {
            // In a box of its own: the button's tooltip wrapper doesn't pass the alignment on. Its outline in the same
            // place as the button's in the picker chip's row ([WideLayout], upright too).
            Box(Modifier.align(Alignment.TopEnd).padding(top = EDGE_INSET, end = WIDE_EDGE - GEAR_OUTLINE_INSET)) {
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
 * grow to [GEAR_AND_BARS_SLOT], so the chip stays centred. The bars stand here only while a tool's panel
 * takes the ruler's place; in the plain tuner they are beside the ruler's heard note (Phase 71).
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
                val outlineInset = (it.width - TUNER_BUTTON_FACE.roundToPx()).coerceAtLeast(0) / 2
                it.placeRelative(-outlineInset, (height - it.height) / 2)
                it.width - outlineInset
            } ?: 0
            // The bars in the middle of their 48 dp touch area, which starts at the row's start, or after the gear's.
            barsPlaceable?.placeRelative(gearEnd + (SIGNAL_BARS_SLOT.roundToPx() - barsPlaceable.width) / 2, (height - barsPlaceable.height) / 2)
            // The touch area is wider than the outline; the outline ends at the row's end.
            buttonPlaceable?.let {
                val outlineInset = (it.width - TUNER_BUTTON_FACE.roundToPx()).coerceAtLeast(0) / 2
                it.placeRelative(width - it.width + outlineInset, (height - it.height) / 2)
            }
        }
    }
}

/**
 * The wide layout (2.0, Phase 73; the tuner mock-up's "On its side", docs/design/v2-tuner-screens-preview.html): any
 * window wider than tall. The [header] (the picker chip, with focus mode's button at its end: top right, as upright)
 * at the top, the [ruler] across the full width under it, showing more of the scale than upright, the [gauge] (the
 * lens) wide under that, as wide as the window less [WIDE_SIDE] each side and as high as the rest leave (the [Lens]
 * wide: at most half its width, about 3.4 : 1 on a phone on its side), and one line under it: the [word] at its start
 * and the [pegs] in its middle, with the tools' buttons beside them. Focus mode gives no header and no pegs, the
 * [string] small at the line's end instead, and its ruler keeps clear of the exit button in the top end corner. An
 * [error] shows its message and Try again in the lens's place. With a large font on a short screen the lens gives
 * way down to [WIDE_LENS_LEAST] and then the whole scrolls; the lens's glow reaches past its box, under its neighbours.
 * [height] is the window's.
 */
@Composable
private fun WideLayout(
    height: Dp,
    error: TunerError?,
    onRetry: () -> Unit,
    header: (@Composable () -> Unit)?,
    ruler: (@Composable () -> Unit)?,
    gauge: @Composable () -> Unit,
    word: @Composable () -> Unit,
    pegs: (@Composable () -> Unit)? = null,
    string: (@Composable () -> Unit)? = null,
) {
    val top = @Composable {
        Column(Modifier.fillMaxWidth()) {
            if (header != null) {
                Box(Modifier.padding(start = WIDE_EDGE, end = WIDE_EDGE, top = EDGE_INSET)) { header() }
            } else {
                Spacer(Modifier.height(EDGE_INSET))
            }
            if (ruler != null && error == null) {
                ruler()
                Spacer(Modifier.height(RULER_METER_GAP))
            }
        }
    }
    val middle = @Composable {
        if (error != null) {
            Column(
                Modifier.padding(horizontal = WIDE_EDGE),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                TunerErrorMessage(error, onRetry)
            }
        } else {
            gauge()
        }
    }
    val bottom = @Composable {
        if (error == null) {
            Box(Modifier.padding(start = WIDE_SIDE, end = WIDE_SIDE, top = WIDE_WORD_GAP, bottom = EDGE_INSET)) {
                WideBottomLine(word, pegs, string)
            }
        }
    }
    Layout(
        contents = listOf(top, middle, bottom),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(TunerTestTags.WIDE),
    ) { (topMeasurables, middleMeasurables, bottomMeasurables), constraints ->
        val width = constraints.maxWidth
        val across = Constraints(maxWidth = width)
        val topPlaceable = topMeasurables.single().measure(across)
        val bottomPlaceable = bottomMeasurables.singleOrNull()?.measure(across)
        val bottomHeight = bottomPlaceable?.height ?: 0
        val windowHeight = height.roundToPx()
        val room = maxOf(windowHeight - topPlaceable.height - bottomHeight, WIDE_LENS_LEAST.roundToPx())
        // The lens's own inset (room for its glow at its sides) makes up the rest of its side margin.
        val middleWidth = (width - 2 * (WIDE_SIDE - LENS_INSET).roundToPx()).coerceAtLeast(0)
        val middlePlaceable = middleMeasurables.single().measure(
            Constraints(maxWidth = middleWidth, maxHeight = if (error == null) room else Constraints.Infinity),
        )
        val middleHeight = maxOf(room, middlePlaceable.height)
        val total = maxOf(windowHeight, topPlaceable.height + middleHeight + bottomHeight)
        layout(width, total) {
            topPlaceable.placeRelative(0, 0)
            middlePlaceable.placeRelative((width - middlePlaceable.width) / 2, topPlaceable.height + (middleHeight - middlePlaceable.height) / 2)
            bottomPlaceable?.placeRelative(0, total - bottomHeight)
        }
    }
}

/**
 * The wide layout's line under the lens: the [word] at its start, and the [pegs] in its middle (at its end where the
 * middle leaves the word too little room: a narrow phone beside the rail and its navigation buttons) or the [string]
 * at its end. The word keeps to the room beside them (set smaller where it doesn't fit, [TuneWordLine]); where even
 * that is less than [WIDE_WORD_LEAST] (a large font on a phone on its side) the word stands over them instead. Where
 * the pegs go depends on the window alone, never on what the word says, so nothing moves while tuning.
 */
@Composable
private fun WideBottomLine(word: @Composable () -> Unit, pegs: (@Composable () -> Unit)?, string: (@Composable () -> Unit)?) {
    val pegsSlot = @Composable { if (pegs != null) pegs() }
    val stringSlot = @Composable { if (string != null) string() }
    Layout(contents = listOf(word, pegsSlot, stringSlot)) { (wordMeasurables, pegsMeasurables, stringMeasurables), constraints ->
        val width = constraints.maxWidth
        val gap = SECTION_SPACING.roundToPx()
        val least = WIDE_WORD_LEAST.roundToPx()
        val pegsPlaceable = pegsMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        val stringPlaceable = stringMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width / 3))
        // What stands beside the word, and where it starts: the pegs in the middle where the word keeps its room,
        // else at the end, as the string is.
        val other = pegsPlaceable ?: stringPlaceable
        val otherWidth = other?.width ?: 0
        val centred = pegsPlaceable != null && (width - otherWidth) / 2 - gap >= least
        val otherX = if (centred) (width - otherWidth) / 2 else width - otherWidth
        val beside = if (other == null) width else otherX - gap
        val fits = beside >= least
        val wordPlaceable = wordMeasurables.single().measure(Constraints(maxWidth = if (fits) beside else width))
        if (fits) {
            val lineHeight = maxOf(wordPlaceable.height, other?.height ?: 0)
            layout(width, lineHeight) {
                wordPlaceable.placeRelative(0, (lineHeight - wordPlaceable.height) / 2)
                other?.placeRelative(otherX, (lineHeight - other.height) / 2)
            }
        } else {
            val below = other?.height ?: 0
            val rowGap = if (below > 0) COMPACT_NOTE_SPACING.roundToPx() else 0
            layout(width, wordPlaceable.height + rowGap + below) {
                wordPlaceable.placeRelative(0, 0)
                other?.placeRelative(if (pegsPlaceable != null) (width - otherWidth) / 2 else otherX, wordPlaceable.height + rowGap)
            }
        }
    }
}

/**
 * The string in focus mode's wide layout (Phase 73), small at the end of the word's line, as its peg names it ("A2"):
 * the string heard in guitar mode, or the locked one while waiting for it. Nothing in Any note, nor without a string.
 * TalkBack skips it: the word says the note, and the hint the locked string.
 */
@Composable
private fun FocusString(state: TunerUiState) {
    val shown = when (state) {
        is TunerUiState.Detected -> state.targetString?.takeIf { state.mode == TunerMode.AUTO }?.let { Triple(state.tuning, state.notation, it) }
        is TunerUiState.Listening -> state.lockedString?.takeIf { state.mode == TunerMode.AUTO }?.let { Triple(state.tuning, state.notation, it) }
        else -> null
    } ?: return
    val (tuning, notation, index) = shown
    val note = tuning.strings.getOrNull(index) ?: return
    Text(
        text = musicText(notation.nameIn(tuning, note)),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .testTag(TunerTestTags.FOCUS_STRING)
            .semantics { hideFromAccessibility() },
    )
}

/**
 * The [count] pegs ([strings]) with the strum check's button ([button]) beside them, in the wide layout
 * ([GuitarControls] with `beside`): the pegs [WIDE_PEG_PITCH] apart (closer where the width is short), the button
 * after the last string in a slot of at least [WIDE_TOOL_SLOT], and as wide a slot kept empty before the first, so the
 * strings stay centred and never move when the button comes or goes. Above the pegs [MARKS_ROOM] is kept for a
 * strum's marks ([StrumMark]), shown or not. Until the 12th-fret check moved to the picker sheet (Phase 71,
 * 2026-11-03) its button stood before the first peg. [shortGap] is the room for the gap after a banjo's short string
 * ([shortStringGapFor]).
 */
@Composable
private fun WidePegs(count: Int, strings: @Composable () -> Unit, button: @Composable () -> Unit, shortGap: Dp = 0.dp) {
    Layout(contents = listOf(strings, button)) { (stringMeasurables, buttonMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttonPlaceable = buttonMeasurables.firstOrNull()?.measure(loose)
        val gap = TOOL_STRINGS_GAP.roundToPx()
        val slot = maxOf(WIDE_TOOL_SLOT.roundToPx(), buttonPlaceable?.width ?: 0)
        val stringsRoom = if (constraints.hasBoundedWidth) (constraints.maxWidth - 2 * (slot + gap)).coerceAtLeast(0) else Int.MAX_VALUE
        // A dp to spare, so rounding to pixels never leaves the row a hair short of full-size buttons (it would squeeze them).
        val stringsWidth = minOf(stringsRoom, (WIDE_PEG_PITCH * count + shortGap + 1.dp).roundToPx())
        val stringsPlaceable = stringMeasurables.single().measure(loose.copy(maxWidth = stringsWidth))
        val marks = MARKS_ROOM.roundToPx()
        val row = maxOf(stringsPlaceable.height, buttonPlaceable?.height ?: 0)
        val width = (stringsPlaceable.width + 2 * (slot + gap)).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (marks + row).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val stringsX = (width - stringsPlaceable.width) / 2
            stringsPlaceable.placeRelative(stringsX, marks + (row - stringsPlaceable.height) / 2)
            buttonPlaceable?.placeRelative(stringsX + stringsPlaceable.width + gap, marks + (row - buttonPlaceable.height) / 2)
        }
    }
}

/** The strum check's button beside the wide layout's pegs: its 48 dp touch area, kept whether it shows or not (and as much before the first peg). */
private val WIDE_TOOL_SLOT = 48.dp

/** Between the wide layout's pegs and the strum check's button beside them. */
private val TOOL_STRINGS_GAP = 8.dp

/** The wide layout's header and focus mode's exit button keep their outlines this far from the window's sides, as upright. */
private val WIDE_EDGE = 16.dp

/** The wide layout's lens and the line under it keep this far from the window's sides (*The lens*, Size wide). */
private val WIDE_SIDE = 24.dp

/**
 * Focus mode's ruler draws no name this near either end: the exit button's outline (44 dp, 16 dp in) and half a small
 * name, so the nearest name starts clear of it (checked on the Xiaomi: at 64 dp a faint name still touched it).
 */
private val WIDE_RULER_CLEAR = 76.dp

/** The wide lens gives way in height down to this (a large font on a phone on its side), then the layout scrolls. */
private val WIDE_LENS_LEAST = 72.dp

/** Between the wide lens and the line under it: room for the lens's glow. */
private val WIDE_WORD_GAP = 12.dp

/** The wide layout's pegs: a 48 dp button and 8 dp between, as upright on a phone (the six-string's spread row). */
private val WIDE_PEG_PITCH = 56.dp

/** Beside the pegs the word needs at least this much room, else it stands over them. */
private val WIDE_WORD_LEAST = 96.dp

/** The compact layout's gauge, at least [gaugeMinWidth] wide, beside the tool's lines ([status]), or the [error]. */
@Composable
private fun CompactRow(
    error: TunerError?,
    onRetry: () -> Unit,
    gauge: @Composable () -> Unit,
    status: @Composable () -> Unit,
    below: @Composable () -> Unit,
    gaugeMinWidth: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // The lines' column never takes the gauge's last [gaugeMinWidth]: long lines at 2× text (the 12th-fret check
        // in Dutch) would otherwise leave the panel no width at all, and the strum check's columns, which are buttons,
        // too little.
        val statusMaxWidth = (maxWidth - SECTION_SPACING - gaugeMinWidth).coerceAtLeast(0.dp)
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
                // The lines' column is measured first; the gauge takes all the room that is left.
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    FitGauge(gauge, maxWidth = COMPACT_GAUGE_MAX_WIDTH)
                }
                Column(
                    // With a very large system font the column can be taller than the screen; then it scrolls,
                    // clear of focus mode's exit button in the top end corner (and as far from the bottom).
                    modifier = Modifier
                        .widthIn(max = statusMaxWidth)
                        .padding(vertical = COMPACT_CORNER_CLEARANCE)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(COMPACT_NOTE_SPACING),
                ) {
                    status()
                    below()
                }
            }
        }
    }
}

/**
 * Under a tool's lines in the compact layout: the 12th-fret check's small pegs, the string being checked ringed;
 * nothing under the strum check's and the chord name's. (The plain tuner's focus mode shows no pegs.)
 */
@Composable
private fun CompactPegsFor(state: TunerUiState) {
    if (state is TunerUiState.IntonationCheck) {
        StringSelector(state.tuning, state.string, notation = state.notation, small = true)
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
 * [gauge] above [status] (the word, or a tool's lines), for the two-pane layout. The status keeps its natural size
 * and the gauge shrinks (keeping its 2:1 shape, down to [GAUGE_MIN_WIDTH]) so both fit in [targetHeight]: a phone on
 * its side is too short for the full-width gauge. Taller than [targetHeight] only with a very large font, where the
 * pane scrolls.
 */
@Composable
private fun GaugeAboveStatus(gauge: @Composable () -> Unit, status: @Composable () -> Unit, targetHeight: Dp) {
    Layout(contents = listOf(gauge, status)) { (gaugeMeasurables, statusMeasurables), constraints ->
        val maxWidth = constraints.maxWidth
        val statusPlaceable = statusMeasurables.single().measure(Constraints(maxWidth = maxWidth))
        val gaugeMeasurable = gaugeMeasurables.single()
        val spacing = GAUGE_NOTE_SPACING.roundToPx()

        val available = targetHeight.roundToPx() - statusPlaceable.height - spacing
        val fullHeight = gaugeMeasurable.minIntrinsicHeight(maxWidth)
        val gaugeWidth = if (fullHeight <= available) {
            maxWidth
        } else {
            // The gauge is twice as wide as it is tall (anything it adds under it keeps its height).
            val labels = fullHeight - maxWidth / 2
            ((available - labels) * 2).coerceIn(min(GAUGE_MIN_WIDTH.roundToPx(), maxWidth), maxWidth)
        }
        val gaugePlaceable = gaugeMeasurable.measure(Constraints.fixedWidth(gaugeWidth))

        val width = max(gaugePlaceable.width, statusPlaceable.width)
        layout(width, gaugePlaceable.height + spacing + statusPlaceable.height) {
            gaugePlaceable.placeRelative((width - gaugePlaceable.width) / 2, 0)
            statusPlaceable.placeRelative((width - statusPlaceable.width) / 2, gaugePlaceable.height + spacing)
        }
    }
}

/**
 * The [PegsSlot] for each [TunerUiState]: the pegs (with the strum check's button [strumButton] and a strum's marks
 * above them in the plain tuner) in guitar mode; chromatic mode (Any note) leaves the slot empty, at the same height.
 */
@Composable
private fun GuitarSlotFor(
    state: TunerUiState,
    mode: TunerMode,
    strings: StringActions,
    strumButton: (@Composable () -> Unit)? = null,
    wide: Boolean = false,
) {
    // The strum check's button with the plain tuner's pegs only (the 12th-fret check's pegs pick its string).
    val button = strumButton.takeIf { state is TunerUiState.Detected || state is TunerUiState.Listening }
    val controls = when (state) {
        // The strings ticked off only while they belong to the tuning shown (a new one starts afresh).
        is TunerUiState.Detected -> GuitarControlsState(
            state.tuning, state.notation, state.targetString, state.lockedString, state.inTune,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
            tone = strings.tone,
            strumMarks = strings.strumMarks,
        )
        // A locked string is the target before anything is played, so it stays ringed (in the resting tone).
        is TunerUiState.Listening -> GuitarControlsState(
            state.tuning, state.notation, state.lockedString, state.lockedString, inTune = false,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
            tone = strings.tone,
            strumMarks = strings.strumMarks,
        )
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
                marks = controls.marks,
                checking = controls.checking,
                tuned = controls.tuned,
                tone = controls.tone,
                strumMarks = controls.strumMarks,
                strumButton = button,
                beside = wide,
            )
        }
    } else {
        null
    }
    PegsSlot(shown = mode == TunerMode.AUTO, pegs = guitar, minHeight = if (wide) WIDE_PEGS_HEIGHT + MARKS_ROOM else GUITAR_SLOT_HEIGHT)
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
    val strumMarks: List<StrumString>? = null,
)

/**
 * What the pegs do: a tap ([onClick]: lock or unlock, or stop a sounding tone), a long-press
 * ([onLongClick]: play the string's note, none without it), which string's tone is [sounding],
 * which strings are ticked off as tuned ([tuned], only with the string tick-off on), the reading's [tone] the
 * heard peg is ringed in, and a strum's verdicts to mark above the pegs ([strumMarks], Phase 74).
 */
private data class StringActions(
    val onClick: (Int) -> Unit,
    val onLongClick: ((Int) -> Unit)?,
    val sounding: Int?,
    val tuned: TunedStrings? = null,
    val tone: LiveTone? = null,
    val strumMarks: List<StrumString>? = null,
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
 * The string row: what guitar mode shows in the [PegsSlot]. The strum check's button ([strumButton], 2.0 Phase 74,
 * the owner's pick after the guitar check of 2026-11-03) sits in a row of its own above the strings at its end, its
 * outline ending in line with the last string's button ([stringFaceInsetFor]); between that row and the pegs
 * [MARKS_ROOM] is kept for a strum's marks above each peg ([strumMarks]: ↑ ↓ ✓), so a mark never meets the button and
 * nothing moves when either comes or goes. The row's height is kept without the button too (the 12-string, the
 * 12th-fret check, a build without the check). [beside] (the wide layout, Phase 73, where height is scarce) puts the
 * button beside the strings instead, after the last, in a slot kept whether it shows or not and as wide a one before
 * the first, so the strings stay centred and never move. The instrument button and the tuning's name that started
 * this row until Phase 70 went (the picker chip at the top says and opens the same), and the 12th-fret check's
 * button, at this row's start, when the check moved to the picker sheet (Phase 71, 2026-11-03).
 */
@Composable
private fun GuitarControls(
    tuning: Tuning,
    notation: Notation,
    stringActions: StringActions,
    targetString: Int?,
    lockedString: Int?,
    inTune: Boolean = false,
    marks: List<StringMark?>? = null,
    checking: Boolean = false,
    tuned: TunedStrings? = null,
    tone: LiveTone? = null,
    strumMarks: List<StrumString>? = null,
    strumButton: (@Composable () -> Unit)? = null,
    beside: Boolean = false,
) {
    val buttonSlot = @Composable { if (strumButton != null) strumButton() }
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
            strumMarks = strumMarks,
        )
    }
    if (beside) {
        WidePegs(tuning.strings.size, strings, buttonSlot, shortGap = shortStringGapFor(Dp.Infinity, tuning, small = false))
        return
    }
    val facesSpan = LocalStringFacesSpan.current
    // Without the strings, the header over them takes its own width again.
    if (facesSpan != null) DisposableEffect(facesSpan) { onDispose { facesSpan.width = 0 } }
    Layout(contents = listOf(strings, buttonSlot)) { (stringMeasurables, buttonMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val stringsPlaceable = stringMeasurables.single().measure(partConstraints)
        val buttonPlaceable = buttonMeasurables.firstOrNull()?.measure(partConstraints)
        val width = stringsPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val stringsX = (width - stringsPlaceable.width) / 2
        // The first and last buttons' faces: the row spreads them evenly, the same inset at both ends.
        val faceInset = stringFaceInsetFor(stringsPlaceable.width.toDp(), tuning).dp.roundToPx().coerceAtLeast(0)
        val facesStart = stringsX + faceInset
        val facesEnd = stringsX + stringsPlaceable.width - faceInset
        // The strum check's outline ends where the last string's face does.
        val buttonX = buttonPlaceable?.let {
            val outlineInset = (it.width - TUNER_BUTTON_FACE.roundToPx()).coerceAtLeast(0) / 2
            (facesEnd + outlineInset - it.width).coerceIn(0, (width - it.width).coerceAtLeast(0))
        }
        // The button's row, kept without it too, then room for a strum's marks above the pegs (Phase 74).
        val rowHeight = maxOf(STRUM_ROW_HEIGHT.roundToPx(), buttonPlaceable?.height ?: 0)
        val stringsY = rowHeight + MARKS_ROOM.roundToPx()
        val height = (stringsY + stringsPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        // For the header over the strings on its side: as wide as the faces (they are centred, as it is).
        facesSpan?.width = (facesEnd - facesStart).coerceAtLeast(0)
        layout(width, height) {
            if (buttonPlaceable != null && buttonX != null) buttonPlaceable.placeRelative(buttonX, (rowHeight - buttonPlaceable.height) / 2)
            stringsPlaceable.placeRelative(stringsX, stringsY)
        }
    }
}

/**
 * The space under the word for the [pegs]: placed while [shown] (guitar mode), at the bottom of the slot, and still
 * measured in Any note, where they are not placed, so the slot keeps their height (at least [minHeight]) and
 * switching modes never moves anything, also with a large system font, where the pegs grow. While not shown they are
 * neither drawn nor tappable, and their semantics are cleared, so TalkBack and UI tests don't find them either.
 * (Until Phase 71's last step this was the mode slot, which could also hold something for chromatic mode.)
 */
@Composable
private fun PegsSlot(shown: Boolean, pegs: (@Composable () -> Unit)?, minHeight: Dp = GUITAR_SLOT_HEIGHT) {
    val part = @Composable { if (pegs != null) Box(if (shown) Modifier else Modifier.clearAndSetSemantics {}) { pegs() } }
    Layout(content = part, modifier = Modifier.heightIn(min = minHeight)) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = (placeable?.width ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable?.height ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        // Measured in either mode, so the pegs tell the header over the slot on its side the faces' width in Any note
        // too ([StringFacesSpan]), and its edges stay put.
        layout(width, height) {
            if (shown) placeable?.placeRelative((width - placeable.width) / 2, height - placeable.height)
        }
    }
}

private val SECTION_SPACING = 24.dp

/** Between the ruler and the lens under it. */
private val RULER_METER_GAP = 12.dp

/**
 * Above the pegs, room kept for a strum's marks ([StrumMark], Phase 74): the mark, its gap to the face and a little
 * clear of what is above (upright the strum check's button, whose outline sits 2 dp inside its row).
 */
private val MARKS_ROOM = MARK_SIZE + MARK_GAP + 2.dp

/** The signal bars as drawn, and their touch area's width at the picker chip row's start. */
private val SIGNAL_BARS_SIZE = 24.dp
private val SIGNAL_BARS_SLOT = 48.dp

/** The single column's text margin at each side. */
private val COLUMN_PADDING = 16.dp

/** The upright gauge's margin at each side of the screen (the approved 1.1 mock-up's 8 dp; a tool's panel spans it). */
private val METER_MARGIN = 8.dp

/** The lens's sides, this far in from the gauge's margin: 20 dp from the screen's edges (*The lens*, Size). */
private val LENS_INSET = 12.dp

/** On its side the readout pane leaves this much above and below the lens and the word, for the lens's glow. */
private val LENS_GLOW_ROOM = 24.dp

/** The free height above the lens, against 1 above the pegs (the mock-up's `.grow.top`). */
private const val TOP_GAP_WEIGHT = 0.5f

/** The single column, and the controls pane, never get wider than this. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On a wide screen the gauge stops growing here. */
private val READOUT_PANE_MAX_WIDTH = 560.dp

/** A phone on its side shrinks the gauge to fit, but not below this. */
private val GAUGE_MIN_WIDTH = 160.dp

/** Readout panes shorter than this (a phone on its side) set the word, or a tool's lines, a little smaller. */
private val COMPACT_PANE_HEIGHT = 420.dp

/** In the compact layout a tool's panel grows with the screen, up to this. */
private val COMPACT_GAUGE_MAX_WIDTH = 840.dp


/** The room at each end of the picker chip's row: focus mode's 44 dp button outline and 8 dp clear of the chip; the signal bars' 48 dp touch area fits it too. */
private val FOCUS_BUTTON_SLOT = 52.dp

/** The room at each end of that row with the app's gear and the signal bars both at its start: the gear's 46 dp past the margin, the bars' 48 dp touch area less what clears it. */
private val GEAR_AND_BARS_SLOT = 92.dp

/** The gear's 48 dp touch area reaches this far past its 44 dp outline at each side. */
private val GEAR_OUTLINE_INSET = 2.dp

/** Above and below the compact layout's column of a tool's lines: focus mode's exit button (48 dp at 8 dp from the top) stays clear. */
private val COMPACT_CORNER_CLEARANCE = 56.dp

/** Between a tool's lines and what's under them in the compact layout. */
private val COMPACT_NOTE_SPACING = 12.dp

/** Between the gauge and the word in the two-pane layout, where height is scarce. */
private val GAUGE_NOTE_SPACING = 8.dp

/** The upright column keeps this far from the top and bottom of its space. */
private val EDGE_INSET = 8.dp

/** Above and below the controls pane's column, so it keeps a margin when it scrolls (a large font on a phone on its side). */
private val CONTROLS_PANE_CLEARANCE = 56.dp

/** The strum check's button's row above the pegs: its 48 dp touch target, kept whether it shows or not. */
private val STRUM_ROW_HEIGHT = 48.dp

/**
 * The strum check's button's row, the room for a strum's marks and the string row (48 dp). Until the 12th-fret check
 * moved to the picker sheet (Phase 71, 2026-11-03) its button stood in that row, at the other end.
 */
private val GUITAR_SLOT_HEIGHT = STRUM_ROW_HEIGHT + MARKS_ROOM + 48.dp

/** The wide layout's pegs: one row of 48 dp buttons (a strum's marks' room above, [MARKS_ROOM]). */
private val WIDE_PEGS_HEIGHT = 48.dp

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
