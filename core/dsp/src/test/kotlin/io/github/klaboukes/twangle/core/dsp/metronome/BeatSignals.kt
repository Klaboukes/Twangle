package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Synthetic music with a known tempo, for [OnsetTempoDetector]'s tests: click tracks and drum
 * grooves (kick, snare, hi-hat), with loose timing, swing and noise. Seeded, so every run is the same.
 */
object BeatSignals {

    const val SAMPLE_RATE = TestSignals.SAMPLE_RATE

    /** A drum pattern over one beat: which drums play on the beat and on the eighth after it. */
    enum class Groove {
        /** Kick on 1 and 3, snare on 2 and 4, hi-hat on every eighth. */
        ROCK,

        /** Kick on every beat, snare on 2 and 4, hi-hat on the off-beat eighths only. */
        FOUR_ON_THE_FLOOR,

    }

    /**
     * Clicks at [bpm] for [seconds]: the metronome's own wood-block sound, every [accentEvery]-th
     * one the louder downbeat (0: all plain). Each click is moved by a random amount with standard
     * deviation [jitterSeconds] (a loose player).
     */
    fun clicks(
        bpm: Double,
        seconds: Double,
        sampleRate: Int = SAMPLE_RATE,
        accentEvery: Int = 0,
        jitterSeconds: Double = 0.0,
        startSeconds: Double = 0.25,
        seed: Long = 7L,
    ): FloatArray {
        val out = FloatArray((seconds * sampleRate).roundToInt())
        val sounds = ClickSounds(sampleRate)
        val random = Random(seed)
        var beat = 0
        while (true) {
            val t = startSeconds + beat * 60.0 / bpm + jitterSeconds * random.nextGaussian()
            val at = (t * sampleRate).roundToInt()
            if (at >= out.size) break
            val accent = if (accentEvery > 0 && beat % accentEvery == 0) BeatAccent.DOWNBEAT else BeatAccent.PLAIN
            add(out, sounds.waveform(accent), at, 1f)
            beat++
        }
        return out
    }

    /** The sample each of [clicks]' beats starts on (without jitter). */
    fun clickTimes(bpm: Double, seconds: Double, sampleRate: Int = SAMPLE_RATE, startSeconds: Double = 0.25): List<Long> {
        val times = ArrayList<Long>()
        var beat = 0
        while (true) {
            val at = ((startSeconds + beat * 60.0 / bpm) * sampleRate).roundToInt()
            if (at >= seconds * sampleRate) break
            times.add(at.toLong())
            beat++
        }
        return times
    }

    /**
     * A drum [groove] at [bpm] for [seconds]. [swing] moves the off-beat eighth from half to that
     * fraction of the beat (2/3 is a triplet swing); [jitterSeconds] moves every hit at random.
     * With [chords], a soft sustained chord changes every bar of four, so there is tonal music too.
     */
    fun drums(
        bpm: Double,
        seconds: Double,
        groove: Groove = Groove.ROCK,
        sampleRate: Int = SAMPLE_RATE,
        swing: Double = 0.5,
        jitterSeconds: Double = 0.0,
        chords: Boolean = true,
        startSeconds: Double = 0.2,
        seed: Long = 11L,
    ): FloatArray {
        val out = FloatArray((seconds * sampleRate).roundToInt())
        val random = Random(seed)
        val kick = kick(sampleRate)
        val snare = snare(sampleRate, random)
        val hat = hat(sampleRate, random)
        val beatSeconds = 60.0 / bpm
        var beat = 0
        while (true) {
            val beatStart = startSeconds + beat * beatSeconds
            if (beatStart * sampleRate >= out.size) break
            val inBar = beat % 4
            fun hit(sound: FloatArray, t: Double, gain: Float) =
                add(out, sound, ((t + jitterSeconds * random.nextGaussian()) * sampleRate).roundToInt(), gain)
            val offBeat = beatStart + swing * beatSeconds
            when (groove) {
                Groove.ROCK -> {
                    if (inBar == 0 || inBar == 2) hit(kick, beatStart, 1f) else hit(snare, beatStart, 1f)
                    hit(hat, beatStart, 1f)
                    hit(hat, offBeat, 0.8f)
                }
                Groove.FOUR_ON_THE_FLOOR -> {
                    hit(kick, beatStart, 1f)
                    if (inBar == 1 || inBar == 3) hit(snare, beatStart, 0.9f)
                    hit(hat, offBeat, 1f)
                }
            }
            beat++
        }
        if (chords) addChords(out, sampleRate, 4 * beatSeconds, startSeconds)
        return out
    }

    /**
     * A bass line plucked on every beat at [bpm] for [seconds], a note per beat from a four-bar
     * riff, with a softer chord plucked on beat 1 of every bar: music with no drums at all.
     */
    fun plucks(bpm: Double, seconds: Double, sampleRate: Int = SAMPLE_RATE, startSeconds: Double = 0.3): FloatArray {
        val out = FloatArray((seconds * sampleRate).roundToInt())
        val riff = floatArrayOf(82.41f, 82.41f, 98f, 110f, 73.42f, 73.42f, 87.31f, 98f)
        val ring = (0.9 * sampleRate).roundToInt()
        val notes = riff.map { TestSignals.pluck(it, ring, sampleRate, amplitude = 0.5f, decaySeconds = 0.3) }
        val chord = floatArrayOf(164.8f, 246.9f, 329.6f).map { TestSignals.pluck(it, ring, sampleRate, amplitude = 0.15f, decaySeconds = 0.6) }
        var beat = 0
        while (true) {
            val at = ((startSeconds + beat * 60.0 / bpm) * sampleRate).roundToInt()
            if (at >= out.size) break
            // Each note stops (a short fade) where the next begins, as a bass player's would.
            val length = minOf(ring, (60.0 / bpm * sampleRate).roundToInt())
            val note = notes[beat % notes.size]
            for (i in 0 until length) {
                val j = at + i
                if (j >= out.size) break
                val fade = minOf(1f, (length - i) / (0.005f * sampleRate))
                out[j] += note[i] * fade
            }
            if (beat % 4 == 0) for (c in chord) add(out, c, at, 1f)
            beat++
        }
        return out
    }

    /** [first] followed by [second]. */
    fun join(first: FloatArray, second: FloatArray): FloatArray = first.copyOf(first.size + second.size).also {
        second.copyInto(it, first.size)
    }

    /** Feeds [signal] to [detector] in chunks of [chunk] samples, calling [afterChunk] after each. */
    inline fun feed(detector: TempoDetector, signal: FloatArray, chunk: Int = 1024, afterChunk: (Long) -> Unit = {}) {
        var pos = 0
        while (pos < signal.size) {
            val n = minOf(chunk, signal.size - pos)
            detector.process(signal, pos, n)
            pos += n
            afterChunk(detector.samplesProcessed)
        }
    }

    // A kick: a sine falling from 150 to 50 Hz, decaying over 150 ms, with a 3 ms click on top.
    private fun kick(sampleRate: Int): FloatArray {
        val n = (0.35 * sampleRate).roundToInt()
        var phase = 0.0
        val noise = Random(3)
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            phase += 2 * PI * (50 + 100 * exp(-t / 0.03)) / sampleRate
            val body = sin(phase) * exp(-t / 0.15)
            val click = if (t < 0.003) 0.4 * noise.nextGaussian() * (1 - t / 0.003) else 0.0
            (0.8 * body + click).toFloat()
        }
    }

    // A snare: noise and a 190 Hz tone, decaying over 80 and 50 ms.
    private fun snare(sampleRate: Int, random: Random): FloatArray {
        val n = (0.3 * sampleRate).roundToInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            (0.35 * random.nextGaussian() * exp(-t / 0.08) + 0.3 * sin(2 * PI * 190 * t) * exp(-t / 0.05)).toFloat()
        }
    }

    // A closed hi-hat: high-passed noise (a first difference) decaying over 20 ms.
    private fun hat(sampleRate: Int, random: Random): FloatArray {
        val n = (0.12 * sampleRate).roundToInt()
        val white = DoubleArray(n + 2) { random.nextGaussian() }
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            (0.3 * (white[i + 1] - white[i]) * exp(-t / 0.02)).toFloat()
        }
    }

    // A soft triad (three sines with a slow attack) that changes every bar.
    private fun addChords(out: FloatArray, sampleRate: Int, barSeconds: Double, startSeconds: Double) {
        val roots = doubleArrayOf(220.0, 174.6, 261.6, 196.0)
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate - startSeconds
            if (t < 0) continue
            val bar = (t / barSeconds).toInt()
            val inBar = t - bar * barSeconds
            val root = roots[bar % roots.size]
            val envelope = minOf(1.0, inBar / 0.05) * 0.04
            out[i] += (envelope * (sin(2 * PI * root * t) + sin(2 * PI * root * 1.26 * t) + sin(2 * PI * root * 1.5 * t))).toFloat()
        }
    }

    private fun add(out: FloatArray, sound: FloatArray, at: Int, gain: Float) {
        for (i in sound.indices) {
            val j = at + i
            if (j < 0) continue
            if (j >= out.size) break
            out[j] += gain * sound[i]
        }
    }
}
