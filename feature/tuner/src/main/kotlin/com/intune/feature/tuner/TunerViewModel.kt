package com.intune.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intune.core.audio.AudioRecorder
import com.intune.core.dsp.music.Tuning
import com.intune.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * the mic. Saved settings (tuning, A4) are followed live, also while listening.
 * Smoothing runs on [dspDispatcher] together with the pitch detection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    recorder: AudioRecorder,
    private val settingsRepository: SettingsRepository,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val pipeline = PitchPipeline(recorder, dspDispatcher)
    private val permissionGranted = MutableStateFlow(false)
    private val attempt = MutableStateFlow(0)
    private val _mode = MutableStateFlow(TunerMode.AUTO)

    val mode: StateFlow<TunerMode> = _mode.asStateFlow()

    /** The string locked in guitar mode, or null for automatic. Cleared when the tuning changes. */
    private val lockedString = MutableStateFlow<Int?>(null)

    /**
     * What readings are compared with; null until the saved settings have loaded. Rebuilt only
     * when an input changes, and read by the DSP loop on every frame.
     */
    private val options: StateFlow<TunerOptions?> =
        combine(settingsRepository.settings, _mode, lockedString) { settings, mode, locked ->
            TunerOptions(mode = mode, tuning = settings.tuning, a4Hz = settings.a4Hz, lockedString = locked)
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

    fun setMode(mode: TunerMode) {
        _mode.value = mode
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

    /** Opens the microphone again after an error. */
    fun retry() {
        attempt.value++
    }

    private fun listen(): Flow<TunerUiState> = flow {
        val interpreter = TunerInterpreter(pipeline.frameSeconds)
        val initial = options.filterNotNull().first()
        emit(TunerUiState.Listening(initial.mode, initial.tuning, initial.a4Hz))
        emitAll(pipeline.readings().map { interpreter.interpret(it, options.value ?: initial) })
    }
        .catch { emit(TunerUiState.Error(it.toTunerError())) }
        .flowOn(dspDispatcher)
}
