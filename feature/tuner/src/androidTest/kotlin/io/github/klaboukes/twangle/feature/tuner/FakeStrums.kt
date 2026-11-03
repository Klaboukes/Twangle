package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.pow

/**
 * The real-screen checks' sounds (2.0, Phase 74): strums through the app's own pluck synthesis, played by a fake
 * microphone in real time, for the strum marks ([StrumCheckStylesTest]) and the chord name ([ChordNameStylesTest]).
 */
internal object FakeStrums {
    const val SAMPLE_RATE = 48_000

    /** A downstroke over the strings at [hz], strings 15 ms apart, [seconds] long, its loudest sample at 0.4. */
    fun strum(hz: FloatArray, seconds: Double): FloatArray {
        val length = (seconds * SAMPLE_RATE).toInt()
        val out = FloatArray(length)
        val tone = FloatArray(length)
        val synth = PluckSynth(SAMPLE_RATE, durationSeconds = 4f)
        hz.forEachIndexed { i, f ->
            val delay = (i * 0.015 * SAMPLE_RATE).toInt()
            tone.fill(0f)
            synth.start(f)
            synth.render(tone, delay, length - delay)
            for (n in 0 until length) out[n] += tone[n]
        }
        val loudest = out.maxOf { abs(it) }
        for (n in out.indices) out[n] *= 0.4f / loudest
        return out
    }

    /** MIDI notes as frequencies at A4 = 440 Hz. */
    fun midiHz(vararg midi: Int): FloatArray = FloatArray(midi.size) { (440.0 * 2.0.pow((midi[it] - 69) / 12.0)).toFloat() }

    /** [hz] each moved by its [cents]. */
    fun detuned(hz: FloatArray, cents: FloatArray): FloatArray = FloatArray(hz.size) { (hz[it] * 2.0.pow(cents[it] / 1200.0)).toFloat() }

    /** Silence this long. */
    fun quiet(seconds: Double): FloatArray = FloatArray((seconds * SAMPLE_RATE).toInt())
}

/** A fake microphone: silence, then [signal] once [cue] is set, then silence again, in real time. */
internal class StrumOnCue(private val signal: FloatArray) : AudioRecorder {
    override val config = AudioConfig()

    @Volatile var cue = false

    override fun chunks(): Flow<FloatArray> = flow {
        val chunk = FloatArray(config.chunkSize)
        val chunkMillis = config.chunkSize * 1000L / config.sampleRate
        var pos = 0
        while (true) {
            chunk.fill(0f)
            if (cue && pos < signal.size) {
                val count = minOf(chunk.size, signal.size - pos)
                System.arraycopy(signal, pos, chunk, 0, count)
                pos += count
            }
            delay(chunkMillis)
            emit(chunk)
        }
    }
}
