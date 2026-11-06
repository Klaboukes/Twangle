package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** [ChordListener]: synthetic strums through a fake microphone are named, one after another; one string names nothing. */
class ChordListenerTest {

    private val rate = 48_000

    private fun midiHz(vararg midi: Int) = FloatArray(midi.size) { (440.0 * 2.0.pow((midi[it] - 69) / 12.0)).toFloat() }

    /** 0.3 s of quiet, then a downstroke over [hz] (15 ms a string), [seconds] long. */
    private fun strum(hz: FloatArray, seconds: Double = 3.0): FloatArray {
        val start = (0.3 * rate).roundToInt()
        val length = (seconds * rate).roundToInt()
        val out = FloatArray(start + length)
        val tone = FloatArray(length)
        val synth = PluckSynth(rate, durationSeconds = 4f)
        for (i in hz.indices) {
            val delay = (i * 0.015 * rate).roundToInt()
            tone.fill(0f)
            synth.start(hz[i])
            synth.render(tone, delay, length - delay)
            for (n in 0 until length) out[start + n] += tone[n]
        }
        val loudest = out.maxOf { abs(it) }
        for (n in out.indices) out[n] *= 0.4f / loudest
        return out
    }

    private suspend fun kotlinx.coroutines.test.TestScope.heard(signal: FloatArray): List<Chord> =
        ChordListener(FakeAudioRecorder(signal), StandardTestDispatcher(testScheduler)).chords(440f).toList()

    @Test
    fun `strummed chords are named one after another`() = runTest {
        // A minor (x02210: A2 E3 A3 C4 E4), then G (320003: G2 B2 D3 G3 B3 G4).
        val am = strum(midiHz(45, 52, 57, 60, 64))
        val g = strum(midiHz(43, 47, 50, 55, 59, 67))
        assertEquals(
            listOf(Chord.of(PitchClass.A, ChordQuality.MINOR), Chord.of(PitchClass.G, ChordQuality.MAJOR)),
            heard(am + g),
        )
    }

    @Test
    fun `the same chord twice is handed on once, and one string names nothing`() = runTest {
        val c = strum(midiHz(48, 52, 55, 60, 64))
        assertEquals(listOf(Chord.of(PitchClass.C, ChordQuality.MAJOR)), heard(c + c))
        assertEquals(emptyList<Chord>(), heard(strum(midiHz(45))))
    }
}
