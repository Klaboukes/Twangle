package io.github.klaboukes.twangle.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.WaveLook
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the tuner's audio pipeline and exposes everything the screen shows as one [state].
 *
 * The microphone opens only when the permission is granted ([onPermissionChanged]) *and* the UI
 * collects [state]. It stays open 5 s after the UI stops collecting, so a rotation doesn't reopen
 * it (CLAUDE.md, hard rule 4). Switching [mode] takes effect on the next frame without reopening
 * the mic, and is saved so the app reopens in it. Saved settings (tuning, A4, notation, in-tune precision) are
 * followed live, also while listening.
 * Smoothing runs on [dspDispatcher] together with the pitch detection. [savedMode] is the mode
 * shown until the saved settings arrive, so a screen that already read them starts in the right one;
 * [savedStringLockTipSeen] does the same for the one-time string-lock tip, and
 * [savedKeepTwoPaneLandscape] for the layout on its side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    recorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    savedMode: TunerMode = AppSettings().mode,
    savedStringLockTipSeen: Boolean = AppSettings().stringLockTipSeen,
    savedKeepTwoPaneLandscape: Boolean = AppSettings().keepTwoPaneLandscape,
) : ViewModel() {

    private val pipeline = PitchPipeline(recorder, dspDispatcher)
    private val permissionGranted = MutableStateFlow(false)
    private val attempt = MutableStateFlow(0)

    /**
     * The mode just picked on the switch, or null until one is. It wins over the saved mode, so
     * the switch and the readings follow a tap at once instead of after the save.
     */
    private val pickedMode = MutableStateFlow<TunerMode?>(null)

    /** Guitar or chromatic: the one last picked, which starts as the saved one. */
    val mode: StateFlow<TunerMode> =
        combine(settingsRepository.settings, pickedMode) { settings, picked -> picked ?: settings.mode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, savedMode)

    /** Whether the screen should stay on while the tuner is shown (a saved setting). */
    val keepScreenOn: StateFlow<Boolean> = settingsRepository.settings
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether reaching "in tune" gives a haptic tick (a saved setting). */
    val vibrateInTune: StateFlow<Boolean> = settingsRepository.settings
        .map { it.vibrateInTune }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** What shows how far off a note is under it: cents or the wave (a saved setting). */
    val display: StateFlow<TunerDisplay> = settingsRepository.settings
        .map { it.display }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().display)

    /** Whether "Tune up ↑" / "Tune down ↓" shows under an off-pitch note and TalkBack says it (a saved setting). */
    val showTuneHint: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showTuneHint }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().showTuneHint)

    /** Whether small signal bars by the note show how well the tuner hears (a saved setting, off by default). */
    val showSignalBars: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showSignalBars }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().showSignalBars)

    private val signal = MutableStateFlow<SignalFigures?>(null)

    /**
     * What the tuner hears now, for the signal bars' figures: updated about [SIGNAL_UPDATES_PER_SECOND]
     * times a second while listening and [showSignalBars] is on, else left as it is (null until then).
     */
    val signalFigures: StateFlow<SignalFigures?> = signal

    /** What the wave view looks like (a saved setting). */
    val waveLook: StateFlow<WaveLook> = settingsRepository.settings
        .map { it.waveLook }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaveLook.DEFAULT)

    /** What the meter above the note looks like (a saved setting). */
    val meterLook: StateFlow<MeterLook> = settingsRepository.settings
        .map { it.meterLook }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeterLook.DEFAULT)

    /** How strong the meter's in-tune glow is: Calm or Bold (a saved setting). */
    val inTuneEffect: StateFlow<InTuneEffect> = settingsRepository.settings
        .map { it.inTuneEffect }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InTuneEffect.DEFAULT)

    /** Whether the wave keeps breathing on the note or holds still there (a saved, temporary setting). */
    val waveBreathesInTune: StateFlow<Boolean> = settingsRepository.settings
        .map { it.waveBreathesInTune }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().waveBreathesInTune)

    /**
     * Whether a phone on its side keeps the two-pane layout instead of the compact one (a saved
     * setting). Starts from the saved value, so a turned phone doesn't switch layout a moment after opening.
     */
    val keepTwoPaneLandscape: StateFlow<Boolean> = settingsRepository.settings
        .map { it.keepTwoPaneLandscape }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), savedKeepTwoPaneLandscape)

    /** True once the string-lock tip is dismissed here; hides it at once instead of after the save. */
    private val stringLockTipDismissed = MutableStateFlow(savedStringLockTipSeen)

    /**
     * Whether guitar mode shows the one-time tip "Tap a string to lock it". It goes for
     * good once dismissed ([dismissStringLockTip]) or once a string is tapped ([toggleString]).
     */
    val showStringLockTip: StateFlow<Boolean> =
        combine(settingsRepository.settings, stringLockTipDismissed) { settings, dismissed ->
            !settings.stringLockTipSeen && !dismissed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, !savedStringLockTipSeen)

    /** The string locked in guitar mode, or null for automatic. Cleared when the tuning changes. */
    private val lockedString = MutableStateFlow<Int?>(null)

    /**
     * What readings are compared with; null until the saved settings have loaded. Rebuilt only
     * when an input changes, and read by the DSP loop on every frame.
     */
    private val options: StateFlow<TunerOptions?> =
        combine(settingsRepository.settings, pickedMode, lockedString) { settings, picked, locked ->
            TunerOptions(
                mode = picked ?: settings.mode,
                tuning = settings.tuning,
                a4Hz = settings.a4Hz,
                lockedString = locked,
                notation = settings.notation,
                inTunePrecision = settings.inTunePrecision,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // A lock belongs to one tuning: a string index means something else in another tuning.
        viewModelScope.launch {
            settingsRepository.settings.map { it.tuning }.distinctUntilChanged().drop(1).collect {
                lockedString.value = null
            }
        }
    }

    val state: StateFlow<TunerUiState> = combine(permissionGranted, attempt) { granted, _ -> granted }
        .flatMapLatest { granted -> if (granted) listen() else flowOf(TunerUiState.PermissionRequired) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerUiState.PermissionRequired)

    /** Tells the ViewModel whether `RECORD_AUDIO` is granted. The mic stays closed until it is. */
    fun onPermissionChanged(granted: Boolean) {
        permissionGranted.value = granted
    }

    /** Switches to [mode] straight away and saves it, so the next start opens in it too. */
    fun setMode(mode: TunerMode) {
        pickedMode.value = mode
        viewModelScope.launch { settingsRepository.setMode(mode) }
    }

    /**
     * Locks guitar mode to string [index] (lowest = 0), or back to automatic if it was already
     * locked. Kept while switching to chromatic and back. Also dismisses the string-lock tip: the
     * user has found out what tapping a string does.
     */
    fun toggleString(index: Int) {
        lockedString.value = if (lockedString.value == index) null else index
        dismissStringLockTip()
    }

    /** Hides the string-lock tip and saves that, so it doesn't come back at the next start. */
    fun dismissStringLockTip() {
        if (stringLockTipDismissed.value) return
        stringLockTipDismissed.value = true
        viewModelScope.launch { settingsRepository.setStringLockTipSeen(true) }
    }

    /** Saves [tuning] as the selected tuning; listening follows it from the next frame. */
    fun setTuning(tuning: Tuning) {
        viewModelScope.launch { settingsRepository.setTuning(tuning) }
    }

    /** Opens the microphone again after an error. */
    fun retry() {
        attempt.value++
    }

    private companion object {
        /** The signal bars' figures change this often: often enough to follow, slow enough to read. */
        const val SIGNAL_UPDATES_PER_SECOND = 4f
    }

    private fun listen(): Flow<TunerUiState> = flow {
        val interpreter = TunerInterpreter(pipeline.frameSeconds)
        val initial = options.filterNotNull().first()
        emit(interpreter.listening(initial))
        val framesPerFigure = (1f / (SIGNAL_UPDATES_PER_SECOND * pipeline.frameSeconds)).toInt().coerceAtLeast(1)
        var frame = 0
        emitAll(
            pipeline.readings().map { reading ->
                val state = interpreter.interpret(reading, options.value ?: initial)
                if (showSignalBars.value && frame++ % framesPerFigure == 0) {
                    val reception = interpreter.reception
                    signal.value = SignalFigures(
                        levelDbfs = if (reception.hasNote) reception.levelDbfs else reading.levelDbfs,
                        noiseFloorDbfs = reading.noiseFloorDbfs,
                        clarity = if (reception.hasNote) reception.clarity else Float.NaN,
                    )
                }
                state
            },
        )
    }
        // Most frames look the same on screen as the last state sent: an equal Listening state in
        // silence, or a note whose readout and needle moved less than the screen shows. Dropping
        // them here, on the DSP thread, keeps them from waking the main thread ~47 times a second.
        .distinctUntilChanged { old, new -> old.looksLike(new) }
        .catch { emit(TunerUiState.Error(it.toTunerError())) }
        .flowOn(dspDispatcher)
}
