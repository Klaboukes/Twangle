package io.github.klaboukes.twangle.feature.tuner

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.AlignmentLine
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
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
import io.github.klaboukes.twangle.core.designsystem.INSTRUMENT_BUTTON_SIZE
import io.github.klaboukes.twangle.core.designsystem.InstrumentButton
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.ScaledUp
import io.github.klaboukes.twangle.core.designsystem.TwangleButton
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink
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
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.feature.tuner.components.ChromaticReadout
import io.github.klaboukes.twangle.feature.tuner.components.FocusAction
import io.github.klaboukes.twangle.feature.tuner.components.FocusModeButton
import io.github.klaboukes.twangle.feature.tuner.components.FocusNote
import io.github.klaboukes.twangle.feature.tuner.components.STRUM_TOGGLE_SIZE
import io.github.klaboukes.twangle.feature.tuner.components.stringFaceInsetFor
import io.github.klaboukes.twangle.feature.tuner.components.strumPanelMinWidth
import io.github.klaboukes.twangle.feature.tuner.components.InTuneHaptic
import io.github.klaboukes.twangle.feature.tuner.components.ModeSwitch
import io.github.klaboukes.twangle.feature.tuner.components.MeterFrame
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.feature.tuner.components.rememberInTuneGlow
import io.github.klaboukes.twangle.feature.tuner.components.NoteDisplay
import io.github.klaboukes.twangle.feature.tuner.components.NoteReadout
import io.github.klaboukes.twangle.feature.tuner.components.PermissionPrompt
import io.github.klaboukes.twangle.feature.tuner.components.PitchTrailPanel
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
import io.github.klaboukes.twangle.feature.tuner.tour.TourPart
import io.github.klaboukes.twangle.feature.tuner.tour.tourControl
import io.github.klaboukes.twangle.feature.tuner.tour.tourTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible. Settings and the metronome are
 * reached from the app's navigation bar or rail, which stays in every layout, the compact one too.
 * [savedMode] is the mode in the settings already read, so the
 * first frame shows the right mode instead of switching a moment later; [savedFocusMode]
 * likewise keeps a turned phone from switching layout, and [savedInstrument] the instrument
 * and tuning panel from showing the guitar a moment first ([savedCustomTunings] likewise lists the
 * player's own tunings from the start). The panel shows while [pickerOpen] (the navigation bar's
 * Instrument item opens it) and calls [onPickerOpenChange] with false once it has closed. The compact layout on its side is used only in focus mode
 * (the "Focus mode" setting). The panel's "New tuning" calls [onOpenTuningEditor] with the instrument and no tuning,
 * and Edit on one of the player's own tunings with that tuning's id; the tuner rests (and lets go of the microphone) while the editor is shown in its place.
 * A long-press on a string plays its note through [tonePlayer]; the tone stops when the tuner leaves
 * the screen (another screen, or the app in the background).
 * While the first-start tour runs ([tourOpen]) the compact layout on its side shows its controls, so
 * the tour can point at them. [onMicGrantedChange] says whether the tuner itself shows (the
 * microphone is allowed), and false when it leaves. Without the microphone the permission prompt shows
 * instead, and [pickerOpen] opens the panel over it all the same. [savedMicRefused] is the saved
 * [io.github.klaboukes.twangle.core.settings.AppSettings.micRefused], which tells a dismissed dialog
 * from one Android no longer shows ([MicPermissionRules]).
 * [tools] says which music tools show (Phase 28, decided in `:app`): a mode whose switch is off
 * leaves no trace, so with [ToolSwitches.ALL_OFF] this is the tuner without them. Each new
 * [closeToolsRequest] (a tap on the navigation bar's Tuner tab, or "Show me around") turns the tool that is on off.
 * [onFullScreenChange] says when focus mode's compact layout shows without its controls, so the app
 * can hide the rail and the system bars around it.
 * [ratingWindow] is Google Play's own rating window (Phase 50), asked for at most once when
 * [TunerViewModel.ratingDue] says the moment has come, never while the tour or the panel is open.
 */
@Composable
fun TunerScreen(
    audioRecorder: AudioRecorder,
    settingsRepository: SettingsRepository,
    tonePlayer: TonePlayer,
    tools: ToolSwitches,
    modifier: Modifier = Modifier,
    savedMode: TunerMode = TunerMode.AUTO,
    savedFocusMode: Boolean = false,
    savedInstrument: Instrument = Instruments.DEFAULT,
    savedCustomTunings: List<CustomTuning> = emptyList(),
    onOpenTuningEditor: (instrument: Instrument, customTuningId: String?) -> Unit = { _, _ -> },
    pickerOpen: Boolean = false,
    onPickerOpenChange: (Boolean) -> Unit = {},
    tourOpen: Boolean = false,
    onMicGrantedChange: (Boolean) -> Unit = {},
    savedMicRefused: Boolean = false,
    closeToolsRequest: Int = 0,
    onFullScreenChange: (Boolean) -> Unit = {},
    ratingWindow: RatingWindow = RatingWindow.NONE,
) {
    val language = appLanguage()
    val viewModel: TunerViewModel = viewModel {
        TunerViewModel(
            audioRecorder,
            settingsRepository,
            tonePlayer,
            savedMode = savedMode,
            savedFocusMode = savedFocusMode,
            savedInstrument = savedInstrument,
            savedCustomTunings = savedCustomTunings,
            language = language,
            savedMicRefused = savedMicRefused,
            tools = tools,
        )
    }
    LaunchedEffect(closeToolsRequest) { viewModel.closeToolsOnRequest(closeToolsRequest) }
    val micRefused by viewModel.micRefused.collectAsStateWithLifecycle()
    val mic = rememberMicPermissionState(refusedBefore = micRefused, onRefused = viewModel::onMicRefused)
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val ratingDue by viewModel.ratingDue.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(ratingDue, tourOpen, pickerOpen) {
        if (ratingDue && !tourOpen && !pickerOpen && activity != null) {
            viewModel.onRatingAsked()
            ratingWindow.show(activity)
        }
    }
    val readings = viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel only sends readings that look different, but most of them just move the needle.
    // Composing from the state as its text shows it recomposes only when the text changes; the
    // needle follows the exact cents without recomposing (see MeterFrame).
    val state by remember(readings) { derivedStateOf(structuralEqualityPolicy()) { readings.value.asShown() } }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val instrument by viewModel.instrument.collectAsStateWithLifecycle()
    val customTunings by viewModel.customTunings.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val vibrateInTune by viewModel.vibrateInTune.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
    val waveLook by viewModel.waveLook.collectAsStateWithLifecycle()
    val meterLook by viewModel.meterLook.collectAsStateWithLifecycle()
    val inTuneEffect by viewModel.inTuneEffect.collectAsStateWithLifecycle()
    val inTunePrecision by viewModel.inTunePrecision.collectAsStateWithLifecycle()
    val showTuneHint by viewModel.showTuneHint.collectAsStateWithLifecycle()
    val showSignalBars by viewModel.showSignalBars.collectAsStateWithLifecycle()
    val focusMode by viewModel.focusMode.collectAsStateWithLifecycle()
    val soundingString by viewModel.soundingString.collectAsStateWithLifecycle()
    val tunedStrings by viewModel.tunedStrings.collectAsStateWithLifecycle()
    val strumCheck by viewModel.strumCheck.collectAsStateWithLifecycle()
    val intonationCheck by viewModel.intonationCheck.collectAsStateWithLifecycle()
    val chordName by viewModel.chordName.collectAsStateWithLifecycle()
    val pitchTrail by viewModel.pitchTrail.collectAsStateWithLifecycle()
    val tunerTools by viewModel.tunerTools.collectAsStateWithLifecycle()
    StopToneWhenHidden(viewModel::stopTone)
    val granted = mic.status == MicPermissionStatus.GRANTED
    LaunchedEffect(granted) { viewModel.onPermissionChanged(granted) }
    // The tour starts once the tuner shows (after the microphone question), and not while it's away.
    val currentOnMicGrantedChange by rememberUpdatedState(onMicGrantedChange)
    LaunchedEffect(granted) { currentOnMicGrantedChange(granted) }
    DisposableEffect(Unit) { onDispose { currentOnMicGrantedChange(false) } }
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
                    needleCents = { (readings.value as? TunerUiState.Detected)?.cents },
                    reception = { (readings.value as? TunerUiState.Detected)?.reception },
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
                    display = display,
                    waveLook = waveLook,
                    meterLook = meterLook,
                    inTuneEffect = inTuneEffect,
                    inTunePrecision = inTunePrecision,
                    meterGlow = glowing,
                    animationsOff = animationsOff,
                    showTuneHint = showTuneHint,
                    showSignalBars = showSignalBars,
                    signalFigures = viewModel.signalFigures,
                    compactLandscape = focusMode,
                    controlsAlwaysShown = tourOpen,
                    strumCheck = strumCheck,
                    onStrumCheckChange = viewModel::setStrumCheck,
                    tunerTools = tunerTools,
                    intonationCheck = intonationCheck,
                    onIntonationCheckChange = viewModel::setIntonationCheck,
                    onCheckAgain = viewModel::checkIntonationAgain,
                    onNextString = viewModel::checkNextString,
                    chordName = chordName,
                    onChordNameChange = viewModel::setChordName,
                    pitchTrail = pitchTrail,
                    onPitchTrailChange = viewModel::setPitchTrail,
                    pitchHistory = viewModel.pitchHistory,
                    onFullScreenChange = onFullScreenChange,
                    onFocusModeChange = viewModel::setFocusMode,
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
                // The navigation bar's Instrument item works without the microphone too: the panel opens
                // over the prompt, and the tuning picked is the one the tuner listens for once allowed.
                val panelTuning by viewModel.panelTuning.collectAsStateWithLifecycle()
                val shown = panelTuning
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
 * The instrument and tuning panel ([InstrumentTuningPanel]) for [tuning]. The strings show in guitar
 * mode only, so a pick made in chromatic [mode] switches to it.
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

    /** The wave view. It has nothing for TalkBack to say; the tag lets UI tests find where it sits. */
    const val WAVE = "tuner_wave"

    /** The needle gauge. TalkBack skips it; the tag lets UI tests find where it sits. */
    const val GAUGE = "tuner_gauge"

    /** The optional signal bars by the note, and the figures panel a tap on them opens. */
    const val SIGNAL_BARS = "tuner_signal_bars"
    const val SIGNAL_FIGURES = "tuner_signal_figures"

    /** The signal bars as drawn, in their small slot; [SIGNAL_BARS] is the 48 dp button around them. */
    const val SIGNAL_BARS_ICON = "tuner_signal_bars_icon"

    /** The instrument and tuning panel, opened from the navigation bar's Instrument item. */
    const val PICKER_PANEL = "tuner_picker_panel"

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

    /** The instrument and tuning button (a headstock) at the start of the row above the strings, which opens the panel. */
    const val INSTRUMENT_BUTTON = "tuner_instrument_button"

    /** The instrument and tuning's name beside it ("Guitar · Drop D"), which opens the panel too. */
    const val TUNING_NAME = "tuner_tuning_name"

    /** The compact landscape layout, which shows the controls when tapped. */
    const val COMPACT = "tuner_compact"

    /** Focus mode's exit button, in the top end corner of the full-screen tuner. */
    const val EXIT_FOCUS = "tuner_exit_focus"

    /** The button that turns focus mode on, at the end of the mode switch's row. */
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

    /** The pitch trail's panel in the meter's place. */
    const val TRAIL_PANEL = "tuner_trail_panel"

    /** String [index]'s column in the 12th-fret check's summary. */
    fun intonationString(index: Int): String = "tuner_intonation_string_$index"

    /** Keep screen on's warning before the screen is let go ("Screen turns off soon"). */
    const val SCREEN_OFF_WARNING = "tuner_screen_off_warning"

    /** The mode switch button for [mode], e.g. "tuner_mode_chromatic". */
    fun mode(mode: TunerMode): String = "tuner_mode_" + mode.name.lowercase()
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
 * [mode] comes straight from the ViewModel, so the switch reacts before the next reading arrives.
 * [needleCents] gives the needle's position, by default the cents of [state]. [TunerScreen] passes the
 * exact cents there and a [state] rounded to what the text shows ([asShown]). [reception] likewise
 * gives how well the note is heard (null without one), which the needle and the wave read while drawing. [display] picks how
 * far off a note is shown under it: in cents (the needle) or with the wave view in [waveLook] (holding
 * perfectly still on the note); the meter above the note is drawn in
 * [meterLook] (its in-tune glow lit by [meterGlow], from [rememberInTuneGlow]; by default while the note is in
 * tune; as strong as [inTuneEffect]; showing the in-tune window of [inTunePrecision] where the look has one; with
 * [animationsOff] its in-tune colour and glow switch without easing), and
 * [showTuneHint] adds "Tune up ↑" / "Tune down ↓" under an off-pitch note (saved settings). [showSignalBars]
 * adds small signal bars at the start of the mode switch's row ([ModeSwitchRow], across from focus mode's button, which [onFocusModeChange] adds at its end), which open the
 * live [signalFigures] when tapped; focus mode, with its controls hidden, leaves them out. While
 * [pickerOpen] the instrument and tuning panel shows over the tuner ([InstrumentTuningPanel], opened
 * from the navigation bar), and [onPickerOpenChange] hears false once it has closed. [instrument] is
 * the one picked there (by default the one [state]'s tuning belongs to); picking another calls
 * [onInstrumentChange]. Picking an instrument or a tuning in chromatic mode switches to guitar mode,
 * where the strings show. [customTunings] are the player's own tunings (the panel lists the
 * instrument's under its presets, each with Edit, [onEditTuning], and Delete, [onDeleteTuning], after
 * asking), and [onNewTuning], when given, adds "New tuning" to the panel's list; [onLoweredByChange],
 * when given, adds its "Lower by" row, which lowers the picked tuning. A long-press on a string calls [onStringLongClick] (play its
 * note); [soundingString] is the string whose tone sounds now, marked on its button, and while it
 * sounds a tap on any string calls [onStringClick], which stops the tone. [tunedStrings] are the strings
 * that have been in tune since the tuner opened, ticked off on their buttons in the plain tuner (not
 * while a tool is on, nor in the compact layout's small row). With [onStrumCheckChange] a
 * switch at the end of the row above the strings turns strum check on and off ([strumCheck]
 * is whether it is on); the start of that row names the instrument and tuning ("Guitar · Drop D", or
 * the player's own tuning's name), and a tap on it calls [onPickerOpenChange] with true. A [TunerUiState.StrumCheck] state shows its panel in the meter's place and its
 * line in the note's ([StrumPanel], [StrumStatus]); a tap on a string's column there calls [onStringClick] too.
 * Where [tunerTools] holds anything but the strum check alone, one "more tools" button takes the switch's place
 * and opens a menu of them ([MoreToolsButton]); the 12th-fret check is turned on and off with
 * [onIntonationCheckChange] ([intonationCheck] is whether it is on). A [TunerUiState.IntonationCheck]
 * shows its panel and line in the same places ([IntonationPanel], [IntonationStatus]), the verdict's
 * buttons calling [onCheckAgain] and [onNextString], and each string's result on its button. The chord
 * name is turned on and off with [onChordNameChange] ([chordName] is whether it is on); a
 * [TunerUiState.ChordName] shows its panel and line there too ([ChordPanel], [ChordStatus]). The pitch
 * trail is turned on and off with [onPitchTrailChange] ([pitchTrail] is whether it is on): while it is
 * on, [pitchHistory] is drawn as a line in the meter's place ([PitchTrailPanel]); the note and the
 * strings stay the tuner's.
 *
 * It picks its arrangement from the space it gets ([tunerLayoutFor]): one centred column when held
 * upright; on a phone turned sideways and on wide screens, with [compactLandscape], the compact layout
 * (a big meter beside the note, controls hidden until the screen is tapped), else two panes side by
 * side. All state lives in the ViewModel, so rotating keeps the mode, the tuning and a locked string.
 * [controlsAlwaysShown] (while the first-start tour runs) keeps the compact layout's controls shown.
 * The parts the tour lights up mark themselves with [tourTarget]; the mode switch and focus mode's
 * button, which have no step, with [tourControl], so balloons keep clear of them.
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
    needleCents: () -> Float? = { (state as? TunerUiState.Detected)?.cents },
    reception: () -> Float? = { (state as? TunerUiState.Detected)?.reception },
    display: TunerDisplay = TunerDisplay.NEEDLE,
    waveLook: WaveLook = WaveLook.DEFAULT,
    meterLook: MeterLook = MeterLook.DEFAULT,
    inTuneEffect: InTuneEffect = InTuneEffect.DEFAULT,
    inTunePrecision: InTunePrecision = InTunePrecision.DEFAULT,
    meterGlow: Boolean = (state as? TunerUiState.Detected)?.inTune == true,
    animationsOff: Boolean = false,
    showTuneHint: Boolean = false,
    showSignalBars: Boolean = false,
    signalFigures: Flow<SignalFigures?> = emptyFlow(),
    compactLandscape: Boolean = true,
    controlsAlwaysShown: Boolean = false,
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
    pitchTrail: Boolean = false,
    onPitchTrailChange: ((Boolean) -> Unit)? = null,
    pitchHistory: PitchHistory? = null,
    onFullScreenChange: (Boolean) -> Unit = {},
    onFocusModeChange: ((Boolean) -> Unit)? = null,
) {
    // The wave takes the cents' place, which changes the layout.
    val pictured = display != TunerDisplay.NEEDLE
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
    val header = @Composable {
        val switch = @Composable { ModeSwitch(mode = mode, onModeChange = onModeChange, modifier = Modifier.tourControl()) }
        // Focus mode's button at the end of the mode switch's row, in both modes in the same place (owner,
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
        ModeSwitchRow(
            switch = switch,
            bars = if (showSignalBars) ({ movableSignalBars() }) else null,
            button = focusButton,
        )
    }
    val guitarSlot = @Composable {
        val strings = StringActions(onStringClick, onStringLongClick, soundingString, tunedStrings)
        val strum = onStrumCheckChange?.let { onStrum ->
            // Only tools whose switch the screen was given; the strum check's alone is its own switch, as in v1.0.
            val offered = tunerTools.filter {
                when (it) {
                    TunerTool.STRUM_CHECK -> true
                    TunerTool.INTONATION_CHECK -> onIntonationCheckChange != null
                    TunerTool.CHORD_NAME -> onChordNameChange != null
                    TunerTool.PITCH_TRAIL -> onPitchTrailChange != null && pitchHistory != null
                }
            }
            ToolsState(
                tools = offered,
                active = when {
                    strumCheck -> TunerTool.STRUM_CHECK
                    intonationCheck -> TunerTool.INTONATION_CHECK
                    chordName -> TunerTool.CHORD_NAME
                    pitchTrail -> TunerTool.PITCH_TRAIL
                    else -> null
                },
                onChange = { tool, on ->
                    when (tool) {
                        TunerTool.STRUM_CHECK -> onStrum(on)
                        TunerTool.INTONATION_CHECK -> onIntonationCheckChange?.invoke(on)
                        TunerTool.CHORD_NAME -> onChordNameChange?.invoke(on)
                        TunerTool.PITCH_TRAIL -> onPitchTrailChange?.invoke(on)
                    }
                },
            )
        }
        GuitarSlotFor(state, mode, pictured, strings, strum, TuningNames(instrument, customTunings) { onPickerOpenChange(true) })
    }
    val gauge = @Composable {
        // Strum check, the 12th-fret check, the chord name and the pitch trail take the meter's place, at its shape.
        if (state is TunerUiState.ChordName) {
            ChordPanel(state, Modifier.zIndex(-1f).tourTarget(TourPart.READOUT))
        } else if (state is TunerUiState.StrumCheck) {
            StrumPanel(
                state,
                Modifier.zIndex(-1f).testTag(TunerTestTags.STRUM_PANEL).tourTarget(TourPart.READOUT),
                onStringClick = onStringClick,
                toneSounding = soundingString != null,
            )
        } else if (state is TunerUiState.IntonationCheck) {
            IntonationPanel(
                state,
                Modifier.zIndex(-1f).tourTarget(TourPart.READOUT),
                onStringClick = onStringClick,
                toneSounding = soundingString != null,
            )
        } else if (pitchTrail && pitchHistory != null) {
            // The pitch trail takes the needle's place; the note under it stays the tuner's.
            PitchTrailPanel(
                pitchHistory,
                Modifier.zIndex(-1f).tourTarget(TourPart.READOUT),
                animationsOff = animationsOff,
            )
        } else {
            MeterFrame(
                look = meterLook,
                effect = inTuneEffect,
                precision = inTunePrecision,
                cents = needleCents,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                glow = meterGlow,
                animationsOff = animationsOff,
                // Drawn under its neighbours: its calm field and in-tune glow reach past its box, behind them.
                modifier = Modifier.zIndex(-1f).testTag(TunerTestTags.GAUGE).tourTarget(TourPart.READOUT),
                reception = reception,
            )
        }
    }
    // Kept here, as the bars are: TalkBack's pacing carries over when the note's display is built
    // afresh, so a note fading to "Play a string" (or a short drop-out) isn't said at once.
    val speech = rememberPacedSpeech()
    val noteFor = @Composable { compact: Boolean, maxHeight: Dp ->
        when (state) {
            is TunerUiState.StrumCheck -> StrumStatus(state, compact = compact)
            is TunerUiState.IntonationCheck -> IntonationStatus(state, onCheckAgain = onCheckAgain, onNextString = onNextString, compact = compact)
            is TunerUiState.ChordName -> ChordStatus(state, compact = compact)
            is TunerUiState.Detected -> NoteDisplay(
                readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune, state.hint),
                hint = "",
                display = display,
                compact = compact,
                maxHeight = maxHeight,
                waveLook = waveLook,
                showTuneHint = showTuneHint,
                reception = reception,
                tip = state.tip,
                speech = speech,
            )
            // showTuneHint here too: it keeps the hint's (empty) line, so nothing moves when a note starts or stops.
            is TunerUiState.Listening ->
                NoteDisplay(
                    readout = null,
                    hint = listeningHint(mode, state, soundingString),
                    spokenHint = listeningHint(mode, state, soundingString, rememberNoteNameSpeech()),
                    display = display,
                    compact = compact,
                    maxHeight = maxHeight,
                    waveLook = waveLook,
                    showTuneHint = showTuneHint,
                    // While a reference tone sounds its line says which, not a tip about the mic.
                    tip = state.tip.takeIf { soundingString == null },
                    speech = speech,
                )
            else -> NoteDisplay(
                readout = null,
                hint = "",
                display = display,
                compact = compact,
                maxHeight = maxHeight,
                waveLook = waveLook,
                showTuneHint = showTuneHint,
                speech = speech,
            )
        }
    }
    // Focus mode's note: small in the corner, beside the full-screen meter.
    val focusNote = @Composable {
        when (state) {
            is TunerUiState.Detected -> FocusNote(
                readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune, state.hint),
                hint = "",
                showTuneHint = showTuneHint,
                tip = state.tip,
                speech = speech,
            )
            is TunerUiState.Listening -> FocusNote(
                readout = null,
                hint = listeningHint(mode, state, soundingString),
                spokenHint = listeningHint(mode, state, soundingString, rememberNoteNameSpeech()),
                tip = state.tip.takeIf { soundingString == null },
                speech = speech,
            )
            else -> FocusNote(readout = null, hint = "", speech = speech)
        }
    }
    // The note (with its pill and frequency, or the wave) is lit with the meter in the tour's first step.
    val note = @Composable { compact: Boolean, maxHeight: Dp -> Box(Modifier.tourTarget(TourPart.READOUT)) { noteFor(compact, maxHeight) } }
    val error = (state as? TunerUiState.Error)?.reason
    // The plain tuner's meter; with a tool on, its panel takes the meter's place.
    val plainMeter = (state is TunerUiState.Detected || state is TunerUiState.Listening) &&
        !(pitchTrail && pitchHistory != null)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val twoPane = @Composable {
            TwoPaneLayout(
                error = error,
                onRetry = onRetry,
                header = header,
                guitarSlot = guitarSlot,
                gauge = gauge,
                note = { compact -> note(compact, Dp.Unspecified) },
                compactNote = pictured || showTuneHint,
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
                    header = header,
                    gauge = gauge,
                    note = { maxHeight -> note(false, maxHeight) },
                    noteShrinks = plainMeter,
                    guitarSlot = guitarSlot,
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
                // While the tour runs the controls stay, so it can point at them.
                val showing = controlsShown || controlsAlwaysShown
                DisposableEffect(controlsAlwaysShown) {
                    hideControls = if (controlsAlwaysShown) null else ({ controlsShown = false })
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
                // Back leaves focus mode, as it leaves a video player's full screen, before it leaves the app
                // (not while the tour runs: there Back ends the tour).
                if (onFocusModeChange != null) BackHandler(enabled = !controlsAlwaysShown) { onFocusModeChange(false) }
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
                            below = { CompactExtraFor(state, mode, pictured) },
                            // The plain tuner gets the meter over the whole screen; a tool keeps its panel beside its own text and buttons.
                            focusNote = if (plainMeter) focusNote else null,
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
    val shownTuning = when (state) {
        is TunerUiState.Detected -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.Listening -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.StrumCheck -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.IntonationCheck -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.ChordName -> PanelTuning(state.tuning, state.notation)
        else -> null
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
                FitGauge(gauge, maxWidth = COLUMN_MAX_WIDTH)
                Spacer(Modifier.height(SECTION_SPACING))
                Shrinking { room -> Box(textMargin) { note(room) } }
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
                    GaugeAboveNote(gauge = gauge, note = { note(compact) }, targetHeight = paneHeight)
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
 * The controls pane's [header] (the mode switch, focus mode's button and the signal bars) over the
 * [guitarSlot], [SECTION_SPACING] apart, the header as wide as the string buttons' faces
 * ([StringFacesLeft]), so on its side focus mode's button ends where the last string's button and the
 * tools button do, and the signal bars start where the instrument button does (Phase 65: they stood a
 * few dp further out, the header taking the pane's whole width). Without strings it takes the width.
 */
@Composable
private fun HeaderOverSlot(header: @Composable () -> Unit, guitarSlot: @Composable () -> Unit) {
    Layout(contents = listOf(header, guitarSlot)) { (headerMeasurables, slotMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val slot = slotMeasurables.single().measure(loose)
        val faces = slot[StringFacesLeft]
        val span = slot.width - 2 * faces
        val headerConstraints = if (faces == AlignmentLine.Unspecified || span <= 0) loose else loose.copy(minWidth = span, maxWidth = span)
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
 * Where the first string button's face starts in the guitar slot, from its left (the inset is the same
 * at the right end), for the header over it on its side ([HeaderOverSlot]).
 */
private val StringFacesLeft = VerticalAlignmentLine(::minOf)

/**
 * The compact landscape layout (focus mode). With a [focusNote] (the plain tuner) the [gauge] fills
 * the screen, the small note in its top start corner ([FocusMeter]). Otherwise (a tool on) the gauge
 * is as big as the space allows beside the [note] with [below] under it (the small string row or the
 * chromatic numbers). No mode switch, strum check switch or string buttons. A tap anywhere, or
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
            FocusMeter(gauge, focusNote)
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
 * The mode [switch] centred, with focus mode's [button] at the row's end, its outline in line with
 * the column's text margin (owner, 2026-10-25: the same place in both modes, never moving), and the
 * signal [bars] at its start, across from the button. The row takes the whole width. The switch keeps
 * clear of two equal slots ([FOCUS_BUTTON_SLOT]) at the row's ends, whether the bars show or not, so
 * it stays centred and never reaches either: a long label at a large font shrinks to fit instead
 * (Dutch "Chromatisch" at 2× text on a 360 dp phone). None of them moves with anything the tuner
 * hears, nor when the mode changes or the bars are turned on.
 */
@Composable
private fun ModeSwitchRow(switch: @Composable () -> Unit, bars: (@Composable () -> Unit)?, button: (@Composable () -> Unit)?) {
    val barsSlot = @Composable { if (bars != null) Box(Modifier.size(SIGNAL_BARS_SIZE)) { bars() } }
    val buttonSlot = @Composable { if (button != null) button() }
    Layout(contents = listOf(switch, barsSlot, buttonSlot)) { (switchMeasurables, barsMeasurables, buttonMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttonPlaceable = buttonMeasurables.singleOrNull()?.measure(loose)
        val barsPlaceable = barsMeasurables.singleOrNull()?.measure(loose)
        val slot = if (buttonPlaceable != null || barsPlaceable != null) FOCUS_BUTTON_SLOT.roundToPx() else 0
        val bounded = constraints.hasBoundedWidth
        val switchRoom = if (bounded) (loose.maxWidth - 2 * slot).coerceAtLeast(0) else Constraints.Infinity
        val switchPlaceable = switchMeasurables.single().measure(loose.copy(maxWidth = switchRoom))
        val width = if (bounded) constraints.maxWidth else switchPlaceable.width + 2 * slot
        val height = maxOf(switchPlaceable.height, barsPlaceable?.height ?: 0, buttonPlaceable?.height ?: 0)
        layout(width, height) {
            switchPlaceable.placeRelative((width - switchPlaceable.width) / 2, (height - switchPlaceable.height) / 2)
            // The bars in the middle of their 48 dp touch area, which starts at the row's start.
            barsPlaceable?.placeRelative((SIGNAL_BARS_SLOT.roundToPx() - barsPlaceable.width) / 2, (height - barsPlaceable.height) / 2)
            // The touch area is wider than the outline; the outline ends at the row's end.
            buttonPlaceable?.let {
                val outlineInset = (it.width - STRUM_TOGGLE_SIZE.roundToPx()).coerceAtLeast(0) / 2
                it.placeRelative(width - it.width + outlineInset, (height - it.height) / 2)
            }
        }
    }
}

/**
 * Focus mode's plain tuner: the [gauge] as big as the whole screen allows (keeping its 2:1 shape, so
 * on a phone on its side its height decides), with [note] small in the top start corner, where every
 * meter look leaves room above its scale.
 */
@Composable
private fun FocusMeter(gauge: @Composable () -> Unit, note: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(FOCUS_METER_MARGIN), contentAlignment = Alignment.Center) {
            FitGauge(gauge, maxWidth = FOCUS_GAUGE_MAX_WIDTH)
        }
        Box(Modifier.align(Alignment.TopStart).padding(start = FOCUS_EDGE_SIDE, top = FOCUS_EDGE_TOP)) { note() }
    }
}

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
 * The [ModeSlot] for each [TunerUiState]: the string row (with the strum check's switch) for guitar mode and, with
 * [pictured] (the wave instead of cents), the [ChromaticReadout] for chromatic mode. With the needle the cents and frequency are
 * already under the note, so chromatic mode leaves the slot empty.
 */
@Composable
private fun GuitarSlotFor(
    state: TunerUiState,
    mode: TunerMode,
    pictured: Boolean,
    strings: StringActions,
    strum: ToolsState? = null,
    names: TuningNames? = null,
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
        // The strings ticked off only while they belong to the tuning shown (a new one starts afresh).
        is TunerUiState.Detected -> GuitarControlsState(
            state.tuning, state.notation, state.targetString, state.lockedString, state.inTune,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
        )
        // A locked string is the target before anything is played, so it stays highlighted.
        is TunerUiState.Listening -> GuitarControlsState(
            state.tuning, state.notation, state.lockedString, state.lockedString, inTune = false,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
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
                names = names,
                marks = controls.marks,
                checking = controls.checking,
                tuned = controls.tuned,
                // Only where it shows: hidden in chromatic mode, it isn't laid out, so its old place mustn't count.
                inTour = mode == TunerMode.AUTO,
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
    val inTune: Boolean,
    val marks: List<StringMark?>? = null,
    val checking: Boolean = false,
    val tuned: TunedStrings? = null,
)

/**
 * What the string buttons do: a tap ([onClick]: lock or unlock, or stop a sounding tone), a long-press
 * ([onLongClick]: play the string's note, none without it), which string's tone is [sounding], and
 * which strings have been in tune since the tuner opened ([tuned], ticked off).
 */
private data class StringActions(
    val onClick: (Int) -> Unit,
    val onLongClick: ((Int) -> Unit)?,
    val sounding: Int?,
    val tuned: TunedStrings? = null,
)

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
 * What the instrument button and the name over the string row are made of: the [instrument] picked
 * and the player's own [customTunings], whose names are their own (the presets' come from the string
 * resources). A tap on either calls [onClick] (opens the instrument and tuning panel).
 */
private data class TuningNames(val instrument: Instrument, val customTunings: List<CustomTuning>, val onClick: () -> Unit)

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
 * in line with the last string's button ([stringFaceInsetFor]). With [names] that row starts with the
 * instrument button ([InstrumentButton], a headstock in the same outline), its outline starting in
 * line with the first string's button, so a button of one kind stands at each end; between them the
 * instrument and tuning ("Guitar · Drop D"), quiet in the muted text colour. A long name (a large font,
 * a long language, an own tuning) takes up to two lines and ends in "…" rather than move either
 * button. A tap on the button or the name opens the instrument and tuning panel; TalkBack hears the
 * button ("Instrument, Guitar, Drop D") and skips the name, which says the same.
 * [inTour]: the instrument button, the switch and the strings are the tour's steps.
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
    inTour: Boolean = true,
    names: TuningNames? = null,
    marks: List<StringMark?>? = null,
    checking: Boolean = false,
    tuned: TunedStrings? = null,
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
            modifier = if (inTour) Modifier.tourTarget(TourPart.STRINGS) else Modifier,
            marks = marks,
            checking = checking,
            tuned = tuned,
        )
    }
    val toggle = @Composable {
        when {
            strum == null || strum.tools.isEmpty() -> Unit
            // The strum check alone keeps its own switch, exactly as in v1.0.
            strum.tools == STRUM_CHECK_ALONE -> StrumCheckToggle(strum.active == TunerTool.STRUM_CHECK, { strum.onChange(TunerTool.STRUM_CHECK, it) }, inTour = inTour)
            // Any other tool, or more than one (the 12-string has no strum check): one "more tools"
            // button gathers them, in the switch's place.
            else -> MoreToolsButton(strum.tools, strum.active, strum.onChange, inTour = inTour)
        }
    }
    // One press for the instrument button and the name beside it: a tap on either sinks and lights both.
    val namePress = remember { MutableInteractionSource() }
    val instrument = @Composable {
        if (names != null) {
            InstrumentButton(
                interactionSource = namePress,
                current = names.labelFor(tuning, spoken = true),
                onClick = names.onClick,
                // The tour lights up the outline, not the touch target round it.
                faceModifier = Modifier
                    .testTag(TunerTestTags.INSTRUMENT_BUTTON)
                    .then(if (inTour) Modifier.tourTarget(TourPart.PICKER) else Modifier),
            )
        }
    }
    val label = @Composable {
        if (names != null) {
            // Pressed (or its button pressed), the name sinks and lights up; a quiet label, so only the
            // tint, never Futuristic's neon frame tight round its text.
            val press = namePress
            val nameShape = MaterialTheme.shapes.small
            Box(
                Modifier
                    .heightIn(min = LABEL_MIN_HEIGHT)
                    .pressSink(press)
                    .clip(nameShape)
                    .clickable(press, PressIndication(nameShape)) {
                        press.pressFlash()
                        names.onClick()
                    }
                    .testTag(TunerTestTags.TUNING_NAME)
                    // The instrument button beside it says the same to TalkBack.
                    .clearAndSetSemantics {
                        hideFromAccessibility()
                        onClick {
                            names.onClick()
                            true
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    musicText(names.labelFor(tuning, spoken = false)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val style = MaterialTheme.styleTokens.style
    Layout(contents = listOf(strings, toggle, instrument, label)) { (stringMeasurables, toggleMeasurables, instrumentMeasurables, labelMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val stringsPlaceable = stringMeasurables.single().measure(partConstraints)
        val togglePlaceable = toggleMeasurables.firstOrNull()?.measure(partConstraints)
        val instrumentPlaceable = instrumentMeasurables.firstOrNull()?.measure(partConstraints)
        val width = stringsPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val stringsX = (width - stringsPlaceable.width) / 2
        // The first and last buttons' faces: the row spreads them evenly, the same inset at both ends.
        val faceInset = stringFaceInsetFor(style, stringsPlaceable.width.toDp(), tuning).dp.roundToPx().coerceAtLeast(0)
        val facesStart = stringsX + faceInset
        val facesEnd = stringsX + stringsPlaceable.width - faceInset
        // Each button's outline sits inside its 48 dp touch target: the switch's ends where the last
        // string's face does, the instrument button's starts where the first one's does.
        val toggleX = togglePlaceable?.let {
            val outlineInset = (it.width - STRUM_TOGGLE_SIZE.roundToPx()).coerceAtLeast(0) / 2
            (facesEnd - it.width + outlineInset).coerceIn(0, (width - it.width).coerceAtLeast(0))
        }
        val instrumentInset = instrumentPlaceable?.let { (it.width - INSTRUMENT_BUTTON_SIZE.roundToPx()).coerceAtLeast(0) / 2 } ?: 0
        val instrumentX = instrumentPlaceable?.let { (facesStart - instrumentInset).coerceIn(0, (width - it.width).coerceAtLeast(0)) }
        // The name between the two buttons' outlines, a gap from each.
        val labelStart = if (instrumentPlaceable != null && instrumentX != null) {
            instrumentX + instrumentPlaceable.width - instrumentInset + LABEL_BUTTON_GAP.roundToPx()
        } else {
            facesStart
        }
        val labelEnd = if (togglePlaceable != null && toggleX != null) {
            toggleX + (togglePlaceable.width - STRUM_TOGGLE_SIZE.roundToPx()).coerceAtLeast(0) / 2 - LABEL_TOGGLE_GAP.roundToPx()
        } else {
            facesEnd
        }
        val labelPlaceable = labelMeasurables.firstOrNull()
            ?.measure(partConstraints.copy(maxWidth = (labelEnd - labelStart).coerceAtLeast(0)))
        val rowHeight = maxOf(togglePlaceable?.height ?: 0, instrumentPlaceable?.height ?: 0, labelPlaceable?.height ?: 0)
        val stringsY = if (rowHeight > 0) rowHeight + TOGGLE_STRINGS_GAP.roundToPx() else 0
        val height = (stringsY + stringsPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        // The faces' inset is the same at both ends, so one line from the left says where both are.
        layout(width, height, alignmentLines = mapOf<AlignmentLine, Int>(StringFacesLeft to facesStart)) {
            if (togglePlaceable != null && toggleX != null) togglePlaceable.placeRelative(toggleX, (rowHeight - togglePlaceable.height) / 2)
            if (instrumentPlaceable != null && instrumentX != null) instrumentPlaceable.placeRelative(instrumentX, (rowHeight - instrumentPlaceable.height) / 2)
            labelPlaceable?.placeRelative(labelStart, (rowHeight - labelPlaceable.height) / 2)
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
        // Where the string buttons' faces start, in both modes (chromatic mode places no strings), so the
        // header over the slot on its side keeps the same edges in both ([HeaderOverSlot]).
        val faces = guitarPlaceable?.get(StringFacesLeft)?.takeIf { it != AlignmentLine.Unspecified }
        val lines: Map<AlignmentLine, Int> = if (faces != null && guitarPlaceable != null) mapOf(StringFacesLeft to (width - guitarPlaceable.width) / 2 + faces) else emptyMap()
        layout(width, height, alignmentLines = lines) {
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

/** The tuning's name is a touch target this high at least, like the switch beside it. */
private val LABEL_MIN_HEIGHT = 48.dp

/** At least this much room between the tuning's name and the strum check's switch. */
private val LABEL_TOGGLE_GAP = 12.dp

/** Between the instrument button's outline and the tuning's name. */
private val LABEL_BUTTON_GAP = 12.dp

/** Between the strum check's switch and the string row under it. */
private val TOGGLE_STRINGS_GAP = 8.dp

/** The signal bars as drawn, and their touch area's width at the mode switch row's start. */
private val SIGNAL_BARS_SIZE = 24.dp
private val SIGNAL_BARS_SLOT = 48.dp

/** The single column's text margin at each side. */
private val COLUMN_PADDING = 16.dp

/** The upright meter's margin at each side of the screen (the approved mock-up's 8 dp). */
private val METER_MARGIN = 8.dp

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

/** The room at each end of the mode switch's row: focus mode's 44 dp button outline and 8 dp clear of the switch; the signal bars' 48 dp touch area fits it too. */
private val FOCUS_BUTTON_SLOT = 52.dp

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

/** Instrument-and-tuning button (a 48 dp touch target) + 8 dp + string row (48 dp). */
private val GUITAR_SLOT_HEIGHT = 104.dp

/** The hint while listening; with [speech], as TalkBack says it ("Play the E flat 2 string"). */
@Composable
private fun listeningHint(mode: TunerMode, state: TunerUiState.Listening, sounding: Int? = null, speech: NoteNameSpeech? = null): String {
    val locked = state.lockedString
    // The string whose reference tone sounds now: "Playing D3", in the player's note names.
    val playing = sounding?.let { state.tuning.strings.getOrNull(it) }
    return when {
        playing != null -> stringResource(R.string.tuner_hint_playing, stringName(state, playing, speech))
        mode == TunerMode.CHROMATIC -> stringResource(R.string.tuner_hint_play_note)
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
