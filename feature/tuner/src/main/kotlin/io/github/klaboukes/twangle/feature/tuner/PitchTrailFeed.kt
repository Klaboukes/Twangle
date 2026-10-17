package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Feeds the pitch trail's [history] (Phase 31) from the tuner, one entry per reading, on the DSP
 * thread: a shown note as its target and cents, anything else (silence, the app's own tone, a tool
 * using the microphone) as a gap. Time stamps are the tuner's audio clock, one frame
 * (`frameSeconds`, ≈ 21 ms) apart, kept across listening sessions so they never go back; each new
 * session [start]s the history afresh. Allocates nothing.
 */
internal class PitchTrailFeed(val history: PitchHistory) {

    /** Seconds of tuner readings so far (a Double, so it stays exact over hours). */
    private var clock = 0.0

    /** A new listening session: the line starts afresh rather than joining the last session's. */
    fun start() = history.clear()

    /** One tuner reading, shown as [state]. */
    fun record(state: TunerUiState, frameSeconds: Float) {
        val time = tick(frameSeconds)
        if (state is TunerUiState.Detected) {
            history.push(time, targetMidiOf(state), state.cents, state.inTune)
        } else {
            history.pushGap(time)
        }
    }

    /** One frame in which the tuner shows no reading of its own (a tone, a tool): a gap. */
    fun gap(frameSeconds: Float) = history.pushGap(tick(frameSeconds))

    private fun tick(frameSeconds: Float): Float {
        clock += frameSeconds
        return clock.toFloat()
    }

    companion object {
        /**
         * The note [state] is aimed at, as a MIDI number: its pitch (12 · log2(f / A4) + 69,
         * the equal-tempered MIDI scale) less its cents. A string's own note in guitar mode, the
         * nearest note in chromatic mode.
         */
        fun targetMidiOf(state: TunerUiState.Detected): Int =
            (MIDI_A4 + SEMITONES_PER_OCTAVE * log2(state.frequencyHz / state.a4Hz) - state.cents / CENTS_PER_SEMITONE).roundToInt()

        private const val MIDI_A4 = 69f
        private const val SEMITONES_PER_OCTAVE = 12f
        private const val CENTS_PER_SEMITONE = 100f
    }
}
