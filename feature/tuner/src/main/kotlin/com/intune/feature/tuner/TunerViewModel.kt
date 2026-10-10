package com.intune.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intune.core.audio.AudioRecorder
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the tuner's audio pipeline and exposes everything the screen shows as one [state].
 *
 * The microphone opens only when the permission is granted ([onPermissionChanged]) *and* the UI
 * collects [state]. It stays open 5 s after the UI stops collecting, so a rotation doesn't reopen
 * it (CLAUDE.md, hard rule 4). Switching [mode] takes effect on the next frame without reopening
 * the mic. Smoothing runs on [dspDispatcher] together with the pitch detection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModel(
    recorder: AudioRecorder,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val pipeline = PitchPipeline(recorder, dspDispatcher)
    private val permissionGranted = MutableStateFlow(false)
    private val attempt = MutableStateFlow(0)
    private val _mode = MutableStateFlow(TunerMode.AUTO)

    /** The selected tuning. Fixed to standard until the settings screen arrives (Phase 4). */
    private val tuning: Tuning = Tunings.DEFAULT

    val mode: StateFlow<TunerMode> = _mode.asStateFlow()

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

    /** Opens the microphone again after an error. */
    fun retry() {
        attempt.value++
    }

    private fun listen(): Flow<TunerUiState> = flow {
        val interpreter = TunerInterpreter(pipeline.frameSeconds)
        emit(TunerUiState.Listening(_mode.value, tuning))
        emitAll(pipeline.readings().map { interpreter.interpret(it, _mode.value, tuning) })
    }
        .catch { emit(TunerUiState.Error(it.toTunerError())) }
        .flowOn(dspDispatcher)
}
