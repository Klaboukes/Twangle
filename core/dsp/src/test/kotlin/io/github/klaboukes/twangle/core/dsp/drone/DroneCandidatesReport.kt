package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.Fft
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 48, "Hear them first", round 2: renders the four candidate drones ([DroneCandidate]) into
 * `docs/design/drone-sounds-preview/` as 16-bit mono WAV files at [RATE], for the owner to play
 * side by side (open its `index.html`). For each candidate, [SECONDS] long:
 * - `<sound>-d3.wav`: the root alone, D3 (146.8 Hz, the cello's and the guitar's open D);
 * - `<sound>-d3-fifth.wav`: D3 with its fifth, a pure 3:2 so the drone itself doesn't beat (the
 *   tanpura's fifth is its Pa, below the root);
 * - `<sound>-d3-fifth-octave.wav`: with the fifth and the octave (2:1);
 * - `<sound>-a2-fifth.wav`: a fifth lower, A2 (110 Hz) with its fifth, to hear the low register
 *   (which a phone's speaker hardly plays);
 * - `<sound>-with-scale.wav`: D3 with its fifth under the D Dorian scale, D4 to D5 and back, played
 *   in the tuner's steel-guitar voice (the reference tones' [StringSynth]), the same guitar under
 *   every sound.
 * Each drone fades in over [FADE_IN_SECONDS] and out over [FADE_OUT_SECONDS], starting and ending
 * on exactly 0; nothing loops. Every drone file is equally loud, the loudest the four allow with
 * every peak at most [MAX_PEAK]. In the scale files the guitar is [GUITAR_OVER_DRONE_DB] louder
 * than the drone (while the scale plays), equally loud in all four. Loudness is measured as LUFS
 * are ([loudness]), so a bright sound and a dark one are matched as the ear hears them.
 *
 * It prints, per candidate (the root alone), what the app's own detector reads (the average over
 * the held part and how far the readings move), how much the level moves, how much of the sound a
 * phone's speaker plays (the share of energy above 400 Hz) and its brightness (the spectral
 * centroid, over the held part and its range over quarter seconds), then each file's peak and
 * loudness. The round's aim: four sounds far apart on those figures. It fails if a candidate's
 * average reading is more than 1 cent off, a file clips past [MAX_PEAK] or doesn't start and end
 * on silence.
 *
 * Not a test: run it with the benchmarks
 * (`./gradlew :core:dsp:benchmark --tests '*DroneCandidatesReport*'`). The output is the same on
 * every run, so the files (not committed, only listened to) can be made again byte for byte.
 */
class DroneCandidatesReport {

    private class Variant(val name: String, val rootHz: Double, val fifth: Boolean, val octave: Boolean)

    @Test
    fun writeCandidates() {
        val dir = File(repoRoot(), "docs/design/drone-sounds-preview").apply { mkdirs() }
        val d3 = 440.0 * 2.0.pow(-19 / 12.0)
        val a2 = 110.0
        val variants = listOf(
            Variant("d3", d3, fifth = false, octave = false),
            Variant("d3-fifth", d3, fifth = true, octave = false),
            Variant("d3-fifth-octave", d3, fifth = true, octave = true),
            Variant("a2-fifth", a2, fifth = true, octave = false),
        )
        val frames = (SECONDS * RATE).roundToInt()
        val held = (HELD_FROM_SECONDS * RATE).roundToInt() until frames - ((FADE_OUT_SECONDS + 0.2) * RATE).roundToInt()

        println("sound         pitch: average  readings move   level moves (50 ms RMS)   above 400 Hz   centroid: held  quarter seconds")
        val failures = mutableListOf<String>()
        val drones = mutableListOf<Pair<String, DoubleArray>>()
        for (candidate in DroneCandidate.entries) {
            val root = DroneCandidates.render(candidate, RATE, d3, fifth = false, octave = false, frames = frames)
            val readings = readings(root, d3, held)
            val (centroid, low, high) = centroids(root, held)
            println(
                "%-12s  %+6.2f cents   %5.1f cents      %4.1f dB                   %3.0f %%          %5.0f Hz      %5.0f–%.0f Hz".format(
                    candidate.file, readings.average(), readings.max() - readings.min(), swingDb(root, held),
                    100 * shareAbove(root, 400.0, held), centroid, low, high,
                ),
            )
            if (abs(readings.average()) > 1.0) failures += "${candidate.file} reads ${readings.average()} cents off on average"
            for (v in variants) {
                drones += "${candidate.file}-${v.name}" to faded(DroneCandidates.render(candidate, RATE, v.rootHz, v.fifth, v.octave, frames))
            }
        }
        // One loudness for every drone: the loudness the loudest-peaking file allows under MAX_PEAK.
        val target = drones.minOf { (_, x) -> MAX_PEAK * loudness(x, held) / x.maxOf { abs(it) } }
        val files = drones.map { (name, x) -> name to scaled(x, target / loudness(x, held)) }.toMutableList()

        // The scale over D3 and its fifth.
        val guitar = scaleTrack()
        val scaleSpan = (SCALE_START_SECONDS * RATE).roundToInt() until ((SCALE_START_SECONDS + 15 * NOTE_SECONDS) * RATE).roundToInt()
        val mixes = DroneCandidate.entries.map { candidate ->
            val drone = faded(DroneCandidates.render(candidate, RATE, d3, fifth = true, octave = false, frames = guitar.size))
            val droneGain = loudness(guitar, scaleSpan) / loudness(drone, scaleSpan) / 10.0.pow(GUITAR_OVER_DRONE_DB / 20)
            "${candidate.file}-with-scale" to DoubleArray(guitar.size) { guitar[it] + droneGain * drone[it] }
        }
        val mixGain = mixes.minOf { (_, x) -> MAX_PEAK / x.maxOf { abs(it) } }
        files += mixes.map { (name, x) -> name to scaled(x, mixGain) }

        // Only this round's files stay in the folder.
        val names = files.map { "${it.first}.wav" }.toSet()
        dir.listFiles { f -> f.name.endsWith(".wav") && f.name !in names }?.forEach { it.delete() }

        println("file                          seconds   peak   RMS dBFS   loudness (K-weighted dB, held)")
        for ((name, x) in files) {
            val samples = FloatArray(x.size) { x[it].toFloat() }
            val peak = samples.maxOf { abs(it) }
            val span = if (name.endsWith("-with-scale")) scaleSpan else held
            println("%-28s  %5.1f    %5.3f   %6.1f     %6.1f".format(name, x.size.toDouble() / RATE, peak, 20 * log10(rms(x, span)), 20 * log10(loudness(x, span))))
            writeWav(File(dir, "$name.wav"), samples)
            if (samples.first() != 0f || samples.last() != 0f) failures += "$name doesn't start and end on silence"
            if (peak > MAX_PEAK + 1e-6f) failures += "$name peaks above $MAX_PEAK"
        }
        println("WAV files in ${dir.canonicalPath}")
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    /** [x] with a raised-cosine fade in and out, so the first and last samples are exactly 0. */
    private fun faded(x: DoubleArray): DoubleArray {
        val fadeIn = (FADE_IN_SECONDS * RATE).roundToInt()
        val fadeOut = (FADE_OUT_SECONDS * RATE).roundToInt()
        return DoubleArray(x.size) { n ->
            val left = x.size - 1 - n
            val g = when {
                n < fadeIn -> 0.5 - 0.5 * cos(PI * n / fadeIn)
                left < fadeOut -> 0.5 - 0.5 * cos(PI * left / fadeOut)
                else -> 1.0
            }
            g * x[n]
        }
    }

    private fun scaled(x: DoubleArray, gain: Double) = DoubleArray(x.size) { x[it] * gain }

    /**
     * The D Dorian scale, D4 E4 F4 G4 A4 B4 C5 D5 and back down to D4, one note every
     * [NOTE_SECONDS] from [SCALE_START_SECONDS], on the tuner's steel guitar ([StringVoices.STEEL_GUITAR]).
     * Each note is damped (8 ms) as the next one starts, as when a finger moves on; the last rings
     * on for 2 s and fades. The drone outlasts it by a moment.
     */
    private fun scaleTrack(): DoubleArray {
        val dorian = intArrayOf(62, 64, 65, 67, 69, 71, 72, 74) // D4 … D5 (MIDI)
        val notes = dorian + dorian.reversedArray().drop(1)
        val step = (NOTE_SECONDS * RATE).roundToInt()
        val first = (SCALE_START_SECONDS * RATE).roundToInt()
        val ring = (2.0 * RATE).roundToInt()
        val damp = (0.008 * RATE).roundToInt()
        val total = first + (notes.size - 1) * step + ring + (FADE_OUT_SECONDS * RATE).roundToInt()
        val out = DoubleArray(total)
        val synth = StringSynth(RATE, StringVoices.STEEL_GUITAR)
        val tone = FloatArray(ring)
        for ((i, midi) in notes.withIndex()) {
            val last = i == notes.size - 1
            val length = if (last) ring else step + damp
            val fade = if (last) (0.5 * RATE).roundToInt() else damp
            synth.start((440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat())
            tone.fill(0f)
            val written = synth.render(tone, 0, length)
            val onset = first + i * step
            for (n in 0 until written) {
                val left = length - 1 - n
                val g = if (left < fade) 0.5 - 0.5 * cos(PI * left / fade) else 1.0
                out[onset + n] += g * tone[n]
            }
        }
        return out
    }

    /**
     * What the app's detector reads from [x] every 0.1 s over [span], each in cents from [hz],
     * folded to the nearest octave of it (the tanpura's low Sa is the root an octave down).
     */
    private fun readings(x: DoubleArray, hz: Double, span: IntRange): List<Double> {
        val settings = AnalysisSettings.forSampleRate(RATE, PitchRange.forLowestNote((hz / 2).toFloat()))
        val detector = settings.newPitchDetector()
        val window = FloatArray(settings.windowSize)
        return (span.first until span.last - settings.windowSize step RATE / 10).mapNotNull { from ->
            for (i in window.indices) window[i] = x[from + i].toFloat()
            val result = detector.detect(window)
            if (!result.isPitched) return@mapNotNull null
            val cents = 1200 * log2(result.frequencyHz / hz)
            cents - 1200 * round(cents / 1200)
        }
    }

    /** The loudest minus the quietest 50 ms RMS over [span], in dB: how much the level moves. */
    private fun swingDb(x: DoubleArray, span: IntRange): Double {
        val w = RATE / 20
        val levels = (span.first until span.last - w step w).map { s -> rms(x, s until s + w) }
        return 20 * log10(levels.max() / levels.min())
    }

    /**
     * The share of [x]'s energy over [span] above [hz], through two second-order Butterworth
     * high-passes (RBJ cookbook): a rough stand-in for a phone's small speaker, which plays little
     * below 300–500 Hz.
     */
    private fun shareAbove(x: DoubleArray, hz: Double, span: IntRange): Double {
        val butterworth = highPass(hz, sqrt(0.5))
        val high = biquad(biquad(x, butterworth), butterworth)
        return rms(high, span).pow(2) / rms(x, span).pow(2)
    }

    /**
     * The spectral centroid of [x] over [span], in quarter-second frames ([CENTROID_FRAME] samples,
     * Hann window, 25 Hz–8 kHz, by FFT): the energy-weighted centroid of the whole span, and the
     * lowest and highest of the frames' own (how much its brightness moves).
     */
    private fun centroids(x: DoubleArray, span: IntRange): Triple<Double, Double, Double> {
        val n = CENTROID_FRAME
        val fft = Fft(n)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        var weightedAll = 0.0
        var totalAll = 0.0
        val frames = mutableListOf<Double>()
        for (from in span.first until span.last - n step n) {
            for (i in 0 until n) {
                re[i] = x[from + i] * (0.5 - 0.5 * cos(2 * PI * i / n))
                im[i] = 0.0
            }
            fft.forward(re, im)
            var weighted = 0.0
            var total = 0.0
            for (k in 1 until n / 2) {
                val hz = k.toDouble() * RATE / n
                if (hz < 25 || hz > 8_000) continue
                val power = re[k] * re[k] + im[k] * im[k]
                weighted += hz * power
                total += power
            }
            frames += weighted / total
            weightedAll += weighted
            totalAll += total
        }
        return Triple(weightedAll / totalAll, frames.min(), frames.max())
    }

    /**
     * How loud [x] sounds over [span]: the RMS after ITU-R BS.1770's K-weighting (a +4 dB shelf
     * above 1.7 kHz for the head, a high-pass at 38 Hz), the weighting behind LUFS, so a bright
     * sound and a dark one at the same figure sound about as loud. The two filters are the
     * standard's, made at [RATE] from the RBJ cookbook's formulas.
     */
    private fun loudness(x: DoubleArray, span: IntRange): Double {
        val shelved = biquad(x, highShelf(1_681.97, 4.0, 0.7072))
        return rms(biquad(shelved, highPass(38.135, 0.5003)), span)
    }

    /** RBJ cookbook high shelf: b0 b1 b2 a1 a2, normalised by a0. */
    private fun highShelf(hz: Double, gainDb: Double, q: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * hz / RATE
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val s = 2 * sqrt(a) * alpha
        val a0 = (a + 1) - (a - 1) * c + s
        return doubleArrayOf(
            a * ((a + 1) + (a - 1) * c + s) / a0, -2 * a * ((a - 1) + (a + 1) * c) / a0, a * ((a + 1) + (a - 1) * c - s) / a0,
            2 * ((a - 1) - (a + 1) * c) / a0, ((a + 1) - (a - 1) * c - s) / a0,
        )
    }

    /** RBJ cookbook high-pass: b0 b1 b2 a1 a2, normalised by a0. */
    private fun highPass(hz: Double, q: Double): DoubleArray {
        val w0 = 2 * PI * hz / RATE
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val a0 = 1 + alpha
        return doubleArrayOf((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
    }

    private fun biquad(input: DoubleArray, k: DoubleArray): DoubleArray {
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        return DoubleArray(input.size) {
            val y = k[0] * input[it] + k[1] * x1 + k[2] * x2 - k[3] * y1 - k[4] * y2
            x2 = x1
            x1 = input[it]
            y2 = y1
            y1 = y
            y
        }
    }

    private fun rms(x: DoubleArray, span: IntRange = x.indices): Double {
        var sum = 0.0
        for (i in span) sum += x[i] * x[i]
        return sqrt(sum / (span.last - span.first + 1))
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "no settings.gradle.kts above ${System.getProperty("user.dir")}" }
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
        DataOutputStream(file.outputStream().buffered()).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(data.capacity())
            out.write(header.array())
            out.write(data.array())
        }
    }

    private companion object {
        /**
         * 32 kHz: every partial is under 8 kHz ([StringSynth.MAX_PARTIAL_HZ]), as in the app at
         * 48 kHz, so nothing that matters is lost, the files are two thirds the size, and the
         * detector needs no upsampling ([AnalysisSettings.UPSAMPLE_BELOW_RATE]).
         */
        const val RATE = 32_000
        const val SECONDS = 8.0
        const val FADE_IN_SECONDS = 0.3
        const val FADE_OUT_SECONDS = 1.5

        /** Pitch, level and colour are measured from here (the pad has swelled in) to just before the fade-out. */
        const val HELD_FROM_SECONDS = 1.5
        const val MAX_PEAK = 0.8

        const val SCALE_START_SECONDS = 2.0
        const val NOTE_SECONDS = 0.5

        /** The centroid's frames: 8192 samples, a quarter of a second at [RATE], bins 3.9 Hz apart. */
        const val CENTROID_FRAME = 8_192

        /** How much louder ([loudness] while the scale plays) the guitar is than the drone in the scale files. */
        const val GUITAR_OVER_DRONE_DB = 6.0
    }
}
