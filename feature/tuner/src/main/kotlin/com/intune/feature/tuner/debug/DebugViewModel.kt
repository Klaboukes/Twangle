package com.intune.feature.tuner.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intune.core.audio.AudioRecorder
import com.intune.core.dsp.music.NoteMapper
import com.intune.feature.tuner.PitchPipeline
import com.intune.feature.tuner.PitchReading
import com.intune.feature.tuner.TunerError
import com.intune.feature.tuner.toTunerError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** What the temporary debug screen shows. */
sealed interface DebugUiState {
    data object Starting : DebugUiState

    data class Live(
        val reading: PitchReading,
        /** Nearest note, e.g. "E2", or null without a pitch. */
        val note: String?,
        /** Raw deviation from [note] in cents, or 0 without a pitch. */
        val cents: Float,
        val sampleRate: Int,
        /** Microphone input name, e.g. "UNPROCESSED", if known. */
        val input: String?,
    ) : DebugUiState

    data class Failed(val error: TunerError) : DebugUiState
}

/**
 * Feeds the temporary debug screen (Phase 2) with raw, unsmoothed readings. TunerViewModel
 * replaces it in Phase 3.
 *
 * The mic is held only while the UI collects [state], plus 5 s so a rotation doesn't reopen it
 * (CLAUDE.md, hard rule 4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModel(
    private val recorder: AudioRecorder,
    dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val pipeline = PitchPipeline(recorder, dspDispatcher)
    private val noteMapper = NoteMapper()
    private val attempt = MutableStateFlow(0)

    val state: StateFlow<DebugUiState> = attempt
        .flatMapLatest {
            pipeline.readings()
                .map<PitchReading, DebugUiState> { toLive(it) }
                .onStart { emit(DebugUiState.Starting) }
                .catch { emit(DebugUiState.Failed(it.toTunerError())) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUiState.Starting)

    /** Opens the microphone again after a failure. */
    fun retry() {
        attempt.value++
    }

    private fun toLive(reading: PitchReading): DebugUiState.Live {
        val mapped = if (reading.isPitched) noteMapper.map(reading.frequencyHz) else null
        return DebugUiState.Live(
            reading = reading,
            note = mapped?.note?.toString(),
            cents = mapped?.cents ?: 0f,
            sampleRate = recorder.config.sampleRate,
            input = recorder.activeInput?.name,
        )
    }
}
