package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.pitch.Fft
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

/**
 * Phase 48: how the app's drone ([DroneSynth], 48 kHz) measures, for the docs and for setting its
 * levels ([DroneSynth.LEVELS]). Per sound and voicing, on D3 at A4 = 440 Hz: the K-weighted
 * loudness (dB, held from 3 to 12 s), the loudest sample over every root of the octave at
 * A4 = 466 Hz, lower or not (15 s each, a seed per root), the app's detector's average reading and
 * the gain that would put the unscaled sound at [TARGET_LOUDNESS] (what `LEVELS` holds). Then the
 * production sounds against round 2's candidates ([DroneCandidates], root alone at D3, 32 kHz):
 * brightness (spectral centroid) and the share of energy above 400 Hz, which should match.
 *
 * Not a test: run it with the benchmarks (`./gradlew :core:dsp:benchmark --tests '*DroneSynthReport*'`).
 */
class DroneSynthReport {

    @Test
    fun measure() {
        val rate = 48_000
        val d3 = NoteMapper(440f).frequencyOf(DroneSynth.rootNote(PitchClass.D))
        val held = (3 * rate) until (12 * rate)
        println("sound        voicing        loudness   worst peak   reading     unscaled gain for ${"%.3f".format(TARGET_LOUDNESS)}")
        for (sound in DroneSound.entries) {
            for (voicing in DroneVoicing.entries) {
                val synth = DroneSynth(rate)
                synth.play(d3, sound, voicing)
                val x = DroneMeasures.render(synth, 12.0)
                val loudness = DroneMeasures.loudness(x, rate, held)
                var worst = 0f
                for (pc in PitchClass.entries) {
                    for (lower in listOf(false, true)) {
                        val other = DroneSynth(rate, seed = pc.ordinal * 7L + 1)
                        other.play(NoteMapper(466f).frequencyOf(DroneSynth.rootNote(pc)), sound, voicing, lower)
                        worst = maxOf(worst, DroneMeasures.peak(DroneMeasures.render(other, 15.0)))
                    }
                }
                val readings = DroneMeasures.readings(x, rate, d3.toDouble(), held)
                val level = DroneSynth.LEVELS[sound.ordinal][voicing.ordinal]
                println(
                    "%-12s %-13s  %6.1f dB    %.3f      %+.2f cents   %.4f".format(
                        sound, voicing, 20 * log10(loudness), worst, readings.average(), TARGET_LOUDNESS * level / loudness,
                    ),
                )
            }
        }

        println("against the candidates (D3 alone, 32 kHz, 1.5–6.5 s): centroid, energy above 400 Hz")
        val slow = 32_000
        for ((i, candidate) in DroneCandidate.entries.withIndex()) {
            val sound = DroneSound.entries[i]
            val before = DroneCandidates.render(candidate, slow, d3.toDouble(), fifth = false, octave = false, frames = 8 * slow)
            val synth = DroneSynth(slow)
            synth.play(d3, sound, DroneVoicing.ROOT)
            val rendered = DroneMeasures.render(synth, 8.0)
            val now = DoubleArray(rendered.size) { rendered[it].toDouble() }
            val (c0, a0) = colour(before, slow)
            val (c1, a1) = colour(now, slow)
            println("%-12s candidate %5.0f Hz %3.0f %%   app %5.0f Hz %3.0f %%".format(sound, c0, 100 * a0, c1, 100 * a1))
        }
    }

    /** The spectral centroid (25 Hz–8 kHz) and the share of energy above 400 Hz of [x] from 1.5 to 6.5 s, in Hann frames of 8192. */
    private fun colour(x: DoubleArray, rate: Int): Pair<Double, Double> {
        val n = 8192
        val fft = Fft(n)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        var weighted = 0.0
        var total = 0.0
        var above = 0.0
        var from = (1.5 * rate).toInt()
        while (from + n <= 6.5 * rate) {
            for (i in 0 until n) {
                re[i] = x[from + i] * (0.5 - 0.5 * cos(2 * PI * i / n))
                im[i] = 0.0
            }
            fft.forward(re, im)
            for (k in 1 until n / 2) {
                val hz = k.toDouble() * rate / n
                if (hz < 25 || hz > 8_000) continue
                val power = re[k] * re[k] + im[k] * im[k]
                weighted += hz * power
                total += power
                if (hz > 400) above += power
            }
            from += n
        }
        return weighted / total to above / total
    }

    private companion object {
        /** The loudness (K-weighted RMS) every sound and voicing is set to: the tanpura's peaks allow it with a margin under 0.8. */
        const val TARGET_LOUDNESS = 0.075
    }
}
