package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
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
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.TwangleButton
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberPacedSpeech
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.feature.tuner.components.ChromaticReadout
import io.github.klaboukes.twangle.feature.tuner.components.STRUM_TOGGLE_SIZE
import io.github.klaboukes.twangle.feature.tuner.components.stringFaceInsetFor
import io.github.klaboukes.twangle.feature.tuner.components.strumPanelMinWidth
import io.github.klaboukes.twangle.feature.tuner.components.InTuneHaptic
import io.github.klaboukes.twangle.feature.tuner.components.ModeSwitch
import io.github.klaboukes.twangle.feature.tuner.components.MeterFrame
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
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
 * [closeToolsRequest] (a tap on the navigation bar's Tuner tab) turns the tool that is on off.
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
    val showTuneHint by viewModel.showTuneHint.collectAsStateWithLifecycle()
    val showSignalBars by viewModel.showSignalBars.collectAsStateWithLifecycle()
    val focusMode by viewModel.focusMode.collectAsStateWithLifecycle()
    val soundingString by viewModel.soundingString.collectAsStateWithLifecycle()
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
    if (granted && keepScreenOn) KeepScreenOn()
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
                    instrument = instrument,
                    onInstrumentChange = viewModel::setInstrument,
                    customTunings = customTunings,
                    onNewTuning = { onOpenTuningEditor(it, null) },
                    onEditTuning = { onOpenTuningEditor(it.instrument, it.id) },
                    onDeleteTuning = { viewModel.deleteCustomTuning(it.id) },
                    pickerOpen = pickerOpen,
                    onPickerOpenChange = onPickerOpenChange,
                    onStringClick = viewModel::toggleString,
                    onStringLongClick = viewModel::playString,
                    soundingString = soundingString,
                    onRetry = viewModel::retry,
                    display = display,
                    waveLook = waveLook,
                    meterLook = meterLook,
                    inTuneEffect = inTuneEffect,
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
                )
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
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
    )
}

/**
 * Test tags for UI tests and the Baseline Profile generator (`:baselineprofile`), which finds them as
 * resource ids because `MainActivity` turns on `testTagsAsResourceId`. Tags don't change with the
 * app's language, unlike the visible text.
 */
object TunerTestTags {
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

    /** The panel's "New tuning" row, which opens the tuning editor. */
    const val NEW_TUNING = "tuner_new_tuning"

    /** The dialog that asks before one of the player's own tunings is deleted. */
    const val DELETE_TUNING_DIALOG = "tuner_delete_tuning_dialog"

    /** One of the player's own tunings in the panel, by its id, and its ⋮ button (Edit, Delete). */
    fun customTuning(id: String): String = "tuner_custom_tuning_$id"
    fun customTuningMenu(id: String): String = "tuner_custom_tuning_menu_$id"

    /** The instrument and tuning's name above the strings ("Guitar · Drop D"), which opens the panel. */
    const val TUNING_NAME = "tuner_tuning_name"

    /** The compact landscape layout, which shows the controls when tapped. */
    const val COMPACT = "tuner_compact"

    /** The compact layout's "Tap to show the controls" line, shown for a few seconds after it appears. */
    const val COMPACT_TAP_HINT = "tuner_compact_tap_hint"

    /** Strum check: its switch above the strings, its panel in the meter's place, and the line under it. */
    const val STRUM_TOGGLE = "tuner_strum_toggle"
    const val STRUM_PANEL = "tuner_strum_panel"
    const val STRUM_STATUS = "tuner_strum_status"

    /** The switch's name in a tooltip, shown on a long-press. */
    const val STRUM_TOOLTIP = "tuner_strum_tooltip"

    /** String [index]'s column in the strum check's panel. */
    fun strumString(index: Int): String = "tuner_strum_string_$index"

    /** The "more tools" button (hammer and screwdriver) above the strings, the menu it opens, and each tool's item in it. */
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
 * far off a note is shown under it: in cents (the needle) or with the wave view in [waveLook] (holding
 * perfectly still on the note); the meter above the note is drawn in
 * [meterLook] (its in-tune glow lit by [meterGlow], from [rememberInTuneGlow]; by default while the note is in
 * tune; as strong as [inTuneEffect]; with [animationsOff] its in-tune colour and glow switch without easing), and
 * [showTuneHint] adds "Tune up ↑" / "Tune down ↓" under an off-pitch note (saved settings). [showSignalBars]
 * adds small signal bars by the note, which open the live [signalFigures] when tapped. While
 * [pickerOpen] the instrument and tuning panel shows over the tuner ([InstrumentTuningPanel], opened
 * from the navigation bar), and [onPickerOpenChange] hears false once it has closed. [instrument] is
 * the one picked there (by default the one [state]'s tuning belongs to); picking another calls
 * [onInstrumentChange]. Picking an instrument or a tuning in chromatic mode switches to guitar mode,
 * where the strings show. [customTunings] are the player's own tunings (the panel lists the
 * instrument's under its presets, each with Edit, [onEditTuning], and Delete, [onDeleteTuning], after
 * asking), and [onNewTuning], when given, adds "New tuning" to the panel's list. A long-press on a string calls [onStringLongClick] (play its
 * note); [soundingString] is the string whose tone sounds now, marked on its button, and while it
 * sounds a tap on any string calls [onStringClick], which stops the tone. With [onStrumCheckChange] a
 * switch at the end of the row above the strings turns strum check on and off ([strumCheck]
 * is whether it is on); the start of that row names the instrument and tuning ("Guitar · Drop D", or
 * the player's own tuning's name), and a tap on it calls [onPickerOpenChange] with true. A [TunerUiState.StrumCheck] state shows its panel in the meter's place and its
 * line in the note's ([StrumPanel], [StrumStatus]); a tap on a string's column there calls [onStringClick] too.
 * Where [tunerTools] holds more than the strum check, one "more tools" button takes the switch's place
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
 * The parts the tour lights up mark themselves with [tourTarget].
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
    needleCents: () -> Float? = { (state as? TunerUiState.Detected)?.cents },
    reception: () -> Float? = { (state as? TunerUiState.Detected)?.reception },
    display: TunerDisplay = TunerDisplay.NEEDLE,
    waveLook: WaveLook = WaveLook.DEFAULT,
    meterLook: MeterLook = MeterLook.DEFAULT,
    inTuneEffect: InTuneEffect = InTuneEffect.DEFAULT,
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
    val header = @Composable { ModeSwitch(mode = mode, onModeChange = onModeChange, modifier = Modifier.tourTarget(TourPart.MODE)) }
    val guitarSlot = @Composable {
        val strings = StringActions(onStringClick, onStringLongClick, soundingString)
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
            ChordPanel(state, Modifier.zIndex(-1f).tourTarget(TourPart.METER))
        } else if (state is TunerUiState.StrumCheck) {
            StrumPanel(
                state,
                Modifier.zIndex(-1f).testTag(TunerTestTags.STRUM_PANEL).tourTarget(TourPart.METER),
                onStringClick = onStringClick,
                toneSounding = soundingString != null,
            )
        } else if (state is TunerUiState.IntonationCheck) {
            IntonationPanel(
                state,
                Modifier.zIndex(-1f).tourTarget(TourPart.METER),
                onStringClick = onStringClick,
                toneSounding = soundingString != null,
            )
        } else if (pitchTrail && pitchHistory != null) {
            // The pitch trail takes the needle's place; the note under it stays the tuner's.
            PitchTrailPanel(
                pitchHistory,
                Modifier.zIndex(-1f).tourTarget(TourPart.METER),
                animationsOff = animationsOff,
            )
        } else {
            MeterFrame(
                look = meterLook,
                effect = inTuneEffect,
                cents = needleCents,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                glow = meterGlow,
                animationsOff = animationsOff,
                // Drawn under its neighbours: its calm field and in-tune glow reach past its box, behind them.
                modifier = Modifier.zIndex(-1f).testTag(TunerTestTags.GAUGE).tourTarget(TourPart.METER),
                reception = reception,
            )
        }
    }
    // Kept here, as the bars are: TalkBack's pacing carries over when the note's display is built
    // afresh, so a note fading to "Play a string" (or a short drop-out) isn't said at once.
    val speech = rememberPacedSpeech()
    val noteFor = @Composable { compact: Boolean ->
        when (state) {
            is TunerUiState.StrumCheck -> StrumStatus(state)
            is TunerUiState.IntonationCheck -> IntonationStatus(state, onCheckAgain = onCheckAgain, onNextString = onNextString)
            is TunerUiState.ChordName -> ChordStatus(state)
            is TunerUiState.Detected -> NoteDisplay(
                readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune, state.hint),
                hint = "",
                display = display,
                compact = compact,
                waveLook = waveLook,
                showTuneHint = showTuneHint,
                reception = reception,
                tip = state.tip,
                signalBars = signalBars,
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
                    waveLook = waveLook,
                    showTuneHint = showTuneHint,
                    // While a reference tone sounds its line says which, not a tip about the mic.
                    tip = state.tip.takeIf { soundingString == null },
                    signalBars = signalBars,
                    speech = speech,
                )
            else -> NoteDisplay(
                readout = null,
                hint = "",
                display = display,
                compact = compact,
                waveLook = waveLook,
                showTuneHint = showTuneHint,
                signalBars = signalBars,
                speech = speech,
            )
        }
    }
    // The note (with its pill and frequency, or the wave) is a tour step of its own, after the meter.
    val note = @Composable { compact: Boolean -> Box(Modifier.tourTarget(TourPart.NOTE)) { noteFor(compact) } }
    val error = (state as? TunerUiState.Error)?.reason

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val twoPane = @Composable {
            TwoPaneLayout(
                error = error,
                onRetry = onRetry,
                header = header,
                guitarSlot = guitarSlot,
                gauge = gauge,
                note = note,
                compactNote = pictured || showTuneHint,
            )
        }
        val layout = tunerLayoutFor(maxWidth.value, maxHeight.value, compactLandscape)
        when (layout) {
            TunerLayout.SINGLE_COLUMN -> SingleColumnLayout(
                height = maxHeight,
                error = error,
                onRetry = onRetry,
                header = header,
                gauge = gauge,
                note = { note(false) },
                guitarSlot = guitarSlot,
            )
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
                // "Tap to show the controls" for a few seconds after the compact layout appears, or until a tap.
                var tapHintShown by remember { mutableStateOf(true) }
                val hintMillis = remember(accessibility) {
                    accessibility?.calculateRecommendedTimeoutMillis(COMPACT_TAP_HINT_SHOWN_MS, containsText = true)
                        ?: COMPACT_TAP_HINT_SHOWN_MS
                }
                LaunchedEffect(Unit) {
                    delay(hintMillis)
                    tapHintShown = false
                }
                // While the tour runs the controls stay, so it can point at them.
                val showing = controlsShown || controlsAlwaysShown
                LaunchedEffect(controlsShown, touches, pickerOpen) {
                    if (controlsShown && !pickerOpen) {
                        delay(shownMillis)
                        controlsShown = false
                    }
                }
                Crossfade(targetState = showing, label = "compactControls") { shown ->
                    if (shown) {
                        Box(Modifier.fillMaxSize().onEveryTouch { touches++ }) {
                            twoPane()
                        }
                    } else {
                        CompactLayout(
                            error = error,
                            onRetry = onRetry,
                            gauge = gauge,
                            note = { note(false) },
                            below = { CompactExtraFor(state, mode, pictured) },
                            // The strum check's columns are buttons: each keeps a 48 dp touch width.
                            gaugeMinWidth = if (state is TunerUiState.StrumCheck) strumPanelMinWidth(state.tuning.strings.size) else GAUGE_MIN_WIDTH,
                            tapHint = tapHintShown,
                            onShowControls = {
                                tapHintShown = false
                                controlsShown = true
                            },
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
 * navigation buttons), the gaps close up to [SECTION_SPACING] and the meter shrinks (keeping its shape,
 * down to [GAUGE_MIN_WIDTH]); only with a very large system font is it still taller than the screen,
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
    note: @Composable () -> Unit,
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
            // On a short screen (a phone with navigation buttons) the meter shrinks, so the strings stay in view.
            Shrinking { room -> FitGauge(gauge, maxWidth = COLUMN_MAX_WIDTH, maxHeight = room) }
            Spacer(Modifier.height(SECTION_SPACING))
            Box(textMargin) { note() }
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
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                header()
                guitarSlot()
            }
        }
    }
}

/**
 * The compact landscape layout: the [gauge] as big as the space allows, beside the [note] with
 * [below] under it (the small string row or the chromatic numbers). No mode switch, strum check
 * switch or string buttons. A tap anywhere, or TalkBack's double tap on the screen's
 * "Show controls" button, calls [onShowControls]; while [tapHint], a quiet line at the bottom says
 * so (TalkBack has the button instead). An [error] shows its message and retry instead.
 */
@Composable
private fun CompactLayout(
    error: TunerError?,
    onRetry: () -> Unit,
    gauge: @Composable () -> Unit,
    note: @Composable () -> Unit,
    below: @Composable () -> Unit,
    gaugeMinWidth: Dp,
    tapHint: Boolean,
    onShowControls: () -> Unit,
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
        CompactRow(error, onRetry, gauge, note, below, gaugeMinWidth)
        // Laid over the layout, so nothing moves when it fades out.
        AnimatedVisibility(
            visible = tapHint && error == null,
            enter = fadeIn(tween(TAP_HINT_FADE_MS)),
            exit = fadeOut(tween(TAP_HINT_FADE_MS)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            Text(
                stringResource(R.string.tuner_compact_tap_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.testTag(TunerTestTags.COMPACT_TAP_HINT).semantics { hideFromAccessibility() },
            )
        }
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
                    // With a very large system font the note column can be taller than the screen; then it scrolls.
                    modifier = Modifier.widthIn(max = noteMaxWidth).verticalScroll(rememberScrollState()),
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
        is TunerUiState.Detected -> GuitarControlsState(state.tuning, state.notation, state.targetString, state.lockedString, state.inTune)
        // A locked string is the target before anything is played, so it stays highlighted.
        is TunerUiState.Listening ->
            GuitarControlsState(state.tuning, state.notation, state.lockedString, state.lockedString, inTune = false)
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
)

/**
 * What the string buttons do: a tap ([onClick]: lock or unlock, or stop a sounding tone), a long-press
 * ([onLongClick]: play the string's note, none without it), and which string's tone is [sounding].
 */
private data class StringActions(
    val onClick: (Int) -> Unit,
    val onLongClick: ((Int) -> Unit)?,
    val sounding: Int?,
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
 * What the name over the string row is made of: the [instrument] picked and the player's own
 * [customTunings], whose names are their own (the presets' come from the string resources). A tap
 * on it calls [onClick] (opens the instrument and tuning panel, as the navigation bar's Instrument item).
 */
private data class TuningNames(val instrument: Instrument, val customTunings: List<CustomTuning>, val onClick: () -> Unit)

/** "Guitar · Drop D" for [tuning], or the player's own tuning's name after the instrument's. */
@Composable
private fun TuningNames.labelFor(tuning: Tuning, spoken: Boolean): String {
    val tuningName = customTunings.firstOrNull { it.id == tuning.id }?.name ?: tuningLabel(tuning)
    return stringResource(if (spoken) R.string.tuner_spoken_picker else R.string.tuner_tuning_name, instrumentLabel(instrument), tuningName)
}

/**
 * The tools the tuner offers ([tools]: the strum check, and in builds with its switch the 12th-fret
 * check), the one that is [active] (null: none), and what turning one on or off calls ([onChange]).
 */
private data class ToolsState(val tools: List<TunerTool>, val active: TunerTool?, val onChange: (TunerTool, Boolean) -> Unit)

/**
 * The string row: what guitar mode shows in the [ModeSlot]. With [strum] the strum check's switch sits
 * in a row of its own above the strings, at its end, its outline ending in line with the last
 * string's button ([stringFaceInsetFor]). With [names] that row starts with the instrument and tuning
 * ("Guitar · Drop D"), in line with the first string's button, quiet in the muted text colour; a long name (a large font, a long language) takes up to two
 * lines and widens the row rather than run under the switch. A tap on it opens the instrument and
 * tuning panel; TalkBack says it as "Guitar, Drop D".
 * [inTour]: the switch and the strings are the tour's steps. (The instrument and tuning are picked
 * from the navigation bar's Instrument item.)
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
        )
    }
    val toggle = @Composable {
        when {
            strum == null -> Unit
            // More than one tool: one "more tools" button gathers them, in the switch's place.
            strum.tools.size > 1 -> MoreToolsButton(strum.tools, strum.active, strum.onChange, inTour = inTour)
            // The strum check alone keeps its own switch, exactly as in v1.0.
            else -> StrumCheckToggle(strum.active == TunerTool.STRUM_CHECK, { strum.onChange(TunerTool.STRUM_CHECK, it) }, inTour = inTour)
        }
    }
    val label = @Composable {
        if (names != null) {
            val spoken = names.labelFor(tuning, spoken = true)
            val clickLabel = stringResource(R.string.tuner_change_instrument_or_tuning)
            // Pressed, the name sinks and lights up; a quiet label, so only the tint, never
            // Futuristic's neon frame tight round its text.
            val press = remember { MutableInteractionSource() }
            val nameShape = MaterialTheme.shapes.small
            Box(
                Modifier
                    .heightIn(min = LABEL_MIN_HEIGHT)
                    .pressSink(press)
                    .clip(nameShape)
                    .clickable(press, PressIndication(nameShape), onClickLabel = clickLabel, role = Role.Button) {
                        press.pressFlash()
                        names.onClick()
                    }
                    .testTag(TunerTestTags.TUNING_NAME)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        role = Role.Button
                        onClick(label = clickLabel) {
                            names.onClick()
                            true
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    names.labelFor(tuning, spoken = false),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val style = MaterialTheme.styleTokens.style
    Layout(contents = listOf(strings, toggle, label)) { (stringMeasurables, toggleMeasurables, labelMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val stringsPlaceable = stringMeasurables.single().measure(partConstraints)
        val togglePlaceable = toggleMeasurables.firstOrNull()?.measure(partConstraints)
        val gap = LABEL_TOGGLE_GAP.roundToPx()
        val toggleRoom = togglePlaceable?.let { it.width + gap } ?: 0
        val labelPlaceable = labelMeasurables.firstOrNull()
            ?.measure(partConstraints.copy(maxWidth = (constraints.maxWidth - toggleRoom).coerceAtLeast(0)))
        val labelRoom = labelPlaceable?.let { it.width + toggleRoom } ?: (togglePlaceable?.width ?: 0)
        val width = max(stringsPlaceable.width, labelRoom).coerceIn(constraints.minWidth, constraints.maxWidth)
        val rowHeight = max(togglePlaceable?.height ?: 0, labelPlaceable?.height ?: 0)
        val stringsY = if (rowHeight > 0) rowHeight + TOGGLE_STRINGS_GAP.roundToPx() else 0
        val height = (stringsY + stringsPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        // The first and last buttons' faces: the row spreads them evenly, the same inset at both ends.
        val faceInset = stringFaceInsetFor(style, stringsPlaceable.width.toDp(), tuning.strings.size).dp.roundToPx().coerceAtLeast(0)
        val facesStart = faceInset
        val facesEnd = stringsPlaceable.width - faceInset
        layout(width, height) {
            val stringsX = (width - stringsPlaceable.width) / 2
            // The switch's outline (inside its touch target) ends where the last button's face does.
            val toggleX = togglePlaceable?.let {
                val outlineInset = (it.width - STRUM_TOGGLE_SIZE.roundToPx()).coerceAtLeast(0) / 2
                (stringsX + facesEnd - it.width + outlineInset).coerceIn(0, width - it.width)
            } ?: width
            togglePlaceable?.placeRelative(toggleX, (rowHeight - togglePlaceable.height) / 2)
            // From the first button's face, unless that would run under the switch.
            labelPlaceable?.placeRelative(
                min(stringsX + facesStart, toggleX - (if (togglePlaceable != null) gap else 0) - labelPlaceable.width).coerceAtLeast(0),
                (rowHeight - labelPlaceable.height) / 2,
            )
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

/** The tuning's name is a touch target this high at least, like the switch beside it. */
private val LABEL_MIN_HEIGHT = 48.dp

/** At least this much room between the tuning's name and the strum check's switch. */
private val LABEL_TOGGLE_GAP = 12.dp

/** Between the strum check's switch and the string row under it. */
private val TOGGLE_STRINGS_GAP = 8.dp

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
private const val TAP_HINT_FADE_MS = 400

/** In the compact layout the meter grows with the screen, up to this. */
private val COMPACT_GAUGE_MAX_WIDTH = 840.dp

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
