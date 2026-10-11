package io.github.klaboukes.twangle.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
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
 * shown until the saved settings arrive, so a screen that already read them starts in the right one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    recorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    savedMode: TunerMode = AppSettings().mode,
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

    /** Whether how far off a note is shows as waves instead of cents (a saved setting). */
    val showWaves: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showWaves }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().showWaves)

    /** Whether "Tune up ↑" / "Tune down ↓" shows under an off-pitch note and TalkBack says it (a saved setting). */
    val showTuneHint: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showTuneHint }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().showTuneHint)

    /** What the wave view looks like (a saved setting). */
    val waveLook: StateFlow<WaveLook> = settingsRepository.settings
        .map { it.waveLook }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaveLook.DEFAULT)

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
     * locked. Kept while switching to chromatic and back.
     */
    fun toggleString(index: Int) {
        lockedString.value = if (lockedString.value == index) null else index
    }

    /** Saves [tuning] as the selected tuning; listening follows it from the next frame. */
    fun setTuning(tuning: Tuning) {
        viewModelScope.launch { settingsRepository.setTuning(tuning) }
    }

    /** Saves [style] as the app's look, chosen from the tuner's style sheet. */
    fun setStyle(style: VisualStyle) {
        viewModelScope.launch { settingsRepository.setStyle(style) }
    }

    /** Opens the microphone again after an error. */
    fun retry() {
        attempt.value++
    }

    private fun listen(): Flow<TunerUiState> = flow {
        val interpreter = TunerInterpreter(pipeline.frameSeconds)
        val initial = options.filterNotNull().first()
        emit(interpreter.listening(initial))
        emitAll(pipeline.readings().map { interpreter.interpret(it, options.value ?: initial) })
    }
        // Most frames look the same on screen as the last state sent: an equal Listening state in
        // silence, or a note whose readout and needle moved less than the screen shows. Dropping
        // them here, on the DSP thread, keeps them from waking the main thread ~47 times a second.
        .distinctUntilChanged { old, new -> old.looksLike(new) }
        .catch { emit(TunerUiState.Error(it.toTunerError())) }
        .flowOn(dspDispatcher)
}
