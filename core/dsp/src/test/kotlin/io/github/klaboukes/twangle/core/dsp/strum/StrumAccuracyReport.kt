package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Accuracy tables of [StrumDetector] on synthetic strums, the numbers behind
 * docs/architecture/strum-and-metronome.md, *Strum check (polyphonic)*. Prints only, asserts nothing
 * (that is [StrumDetectorTest]'s job).
 * Not part of `test`; run with `./gradlew :core:dsp:benchmark`.
 *
 * Error = measured − true cents. Per string: `s` = shadowed, `-` = not heard.
 */
class StrumAccuracyReport {

    private val standard = StrumChords.expectedHz(Tunings.STANDARD)

    @Test
    fun standardTuning() {
        for (seconds in doubleArrayOf(0.171, StrumDetector.FRAME_SECONDS, 2 * StrumDetector.FRAME_SECONDS)) {
            val n = (seconds * StrumChords.SAMPLE_RATE).toInt()
            val detector = StrumDetector(StrumChords.SAMPLE_RATE, n)
            println("=== Standard tuning, frame $n samples (${(1000 * seconds).toInt()} ms). Error in cents, E2 A2 D3 G3 B3 E4")
            val zero = FloatArray(6)
            row("in tune", detector, standard, StrumChords.pluckSynthStrum(standard, n), zero)
            row("in tune, frame from 0 s", detector, standard, StrumChords.pluckSynthStrum(standard, n, fromSeconds = 0.0), zero)
            row("in tune, frame from 1.5 s", detector, standard, StrumChords.pluckSynthStrum(standard, n, fromSeconds = 1.5), zero)
            row("in tune, 20 dB SNR", detector, standard, TestSignals.withNoise(StrumChords.pluckSynthStrum(standard, n), 20f), zero)
            row("in tune, 10 dB SNR", detector, standard, TestSignals.withNoise(StrumChords.pluckSynthStrum(standard, n), 10f), zero)
            row("in tune, treble at .2-.3", detector, standard, StrumChords.pluckSynthStrum(standard, n, gains = floatArrayOf(1f, 1f, 1f, .3f, .2f, .2f)), zero)
            row("in tune, bass at .3", detector, standard, StrumChords.pluckSynthStrum(standard, n, gains = floatArrayOf(.3f, .3f, .5f, 1f, 1f, 1f)), zero)
            val b = doubleArrayOf(1.5e-4, 1e-4, 6e-5, 4e-5, 3e-5, 2e-5)
            val fundamental = FloatArray(6) { (1200 * log2(sqrt(1 + b[it]))).toFloat() }
            row("stiff strings (B 1.5e-4..2e-5)", detector, standard, StrumChords.stiffStrum(standard, n, b), fundamental)
            val b3 = DoubleArray(6) { 3 * b[it] }
            val fundamental3 = FloatArray(6) { (1200 * log2(sqrt(1 + b3[it]))).toFloat() }
            row("stiff strings, B x3", detector, standard, StrumChords.stiffStrum(standard, n, b3), fundamental3)
            val weak = TestSignals.WEAK_FUNDAMENTAL_HARMONICS
            row(
                "stiff + weak fundamental E2 A2", detector, standard,
                StrumChords.stiffStrum(standard, n, b, harmonicGain = { s, k -> if (s < 2 && k <= weak.size) weak[k - 1].toDouble() else 1.0 / k }),
                fundamental,
            )

            val single = Worst()
            for (off in floatArrayOf(-30f, -20f, -10f, 10f, 20f, 30f)) {
                for (s in 0 until 6) {
                    val cents = FloatArray(6).also { it[s] = off }
                    single.add(detect(detector, standard, StrumChords.pluckSynthStrum(StrumChords.detuned(standard, cents), n)), cents)
                }
            }
            single.print("one string ±10/20/30 (36 strums)")

            val pairs = Worst()
            for (a in 0 until 6) {
                for (b in a + 1 until 6) {
                    for ((offA, offB) in listOf(10f to -20f, -30f to 30f, 20f to 10f, -10f to -30f)) {
                        val cents = FloatArray(6).also {
                            it[a] = offA
                            it[b] = offB
                        }
                        pairs.add(detect(detector, standard, StrumChords.pluckSynthStrum(StrumChords.detuned(standard, cents), n)), cents)
                    }
                }
            }
            pairs.print("two strings ±10..30 (60 strums)")

            val random = Random(3)
            val chords = Worst()
            repeat(100) {
                val cents = FloatArray(6) { (random.nextDouble() * 60 - 30).toFloat() }
                chords.add(detect(detector, standard, StrumChords.pluckSynthStrum(StrumChords.detuned(standard, cents), n)), cents)
            }
            chords.print("all six random ±30 (100 strums)")

            val missing = Worst()
            var found = 0
            for (s in 0 until 6) {
                val gains = FloatArray(6) { 1f }.also { it[s] = 0f }
                val result = detect(detector, standard, StrumChords.pluckSynthStrum(standard, n, gains = gains))
                if (!result.present[s]) found++
                missing.add(result, zero, skip = s)
            }
            missing.print("one string missing, others ($found of 6 missing ones caught)")
            uneven(detector, standard, n, "uneven: ±30, gains .3-1")
        }
    }

    // 100 strums, every string random within ±30 cents at a random gain from 0.3 to 1; then the same
    // with one random string left out. Counts present strings read as missing and missing ones read
    // as heard.
    private fun uneven(detector: StrumDetector, expected: FloatArray, n: Int, label: String) {
        val random = Random(5)
        val all = Worst()
        var lost = 0
        var heard = 0
        val strings = expected.size
        repeat(100) {
            val cents = FloatArray(strings) { (random.nextDouble() * 60 - 30).toFloat() }
            val gains = FloatArray(strings) { (0.3 + 0.7 * random.nextDouble()).toFloat() }
            val result = detect(detector, expected, StrumChords.pluckSynthStrum(StrumChords.detuned(expected, cents), n, gains = gains))
            all.add(result, cents)
            val missing = random.nextInt(strings)
            gains[missing] = 0f
            val without = detect(detector, expected, StrumChords.pluckSynthStrum(StrumChords.detuned(expected, cents), n, gains = gains))
            if (without.present[missing]) heard++
            for (s in 0 until strings) if (s != missing && !without.present[s]) lost++
        }
        all.print("$label (100)")
        println("  ${"  … one left out (100)".padEnd(38)}others read as missing $lost of ${100 * (strings - 1)}, missing read as heard $heard of 100")
    }

    @Test
    fun otherTunings() {
        val tunings = listOf(Tunings.DROP_D, Tunings.DADGAD, Tunings.OPEN_G, Tunings.OPEN_D) +
            Instruments.all.filter { it != Instruments.GUITAR }.flatMap { it.tunings }
        println("=== Other tunings, frame by range (STANDARD 341 ms, LOW 683 ms). Error in cents per string")
        for (tuning in tunings) {
            val expected = StrumChords.expectedHz(tuning)
            val range = PitchRange.forLowestNote(expected.min())
            val n = StrumDetector.frameLengthFor(StrumChords.SAMPLE_RATE, range)
            val detector = StrumDetector(StrumChords.SAMPLE_RATE, n)
            val name = "${tuning.id} ($range)"
            row("$name in tune", detector, expected, StrumChords.pluckSynthStrum(expected, n), FloatArray(expected.size))
            val cents = FloatArray(expected.size) { if (it % 2 == 0) 15f else -20f }
            row("$name +15/-20", detector, expected, StrumChords.pluckSynthStrum(StrumChords.detuned(expected, cents), n), cents)
            val missed = StringBuilder()
            for (s in expected.indices) {
                val gains = FloatArray(expected.size) { 1f }.also { it[s] = 0f }
                val result = detect(detector, expected, StrumChords.pluckSynthStrum(expected, n, gains = gains))
                if (result.present[s]) missed.append(" $s")
            }
            println("  ${name.padEnd(38)} missing string read as heard:${if (missed.isEmpty()) " none" else missed}")
            if (tuning.stringCount == 6) uneven(detector, expected, n, "$name uneven")
        }
    }

    private fun detect(detector: StrumDetector, expected: FloatArray, signal: FloatArray): StrumResult =
        StrumResult(StrumDetector.MAX_STRINGS).also { detector.detect(signal, expected, expected.size, it) }

    private fun row(label: String, detector: StrumDetector, expected: FloatArray, signal: FloatArray, truth: FloatArray) {
        val result = detect(detector, expected, signal)
        val line = StringBuilder("  ${label.padEnd(38)}")
        for (s in 0 until result.stringCount) {
            line.append("%7.2f".format(result.cents[s] - truth[s]))
            line.append(if (!result.present[s]) "-" else if (result.shadowed[s]) "s" else " ")
        }
        println(line)
    }

    // Worst and RMS error per string over many strums; a present string that is lost counts apart.
    private class Worst {
        val worst = DoubleArray(StrumDetector.MAX_STRINGS)
        val squares = DoubleArray(StrumDetector.MAX_STRINGS)
        val counts = IntArray(StrumDetector.MAX_STRINGS)
        var lost = 0
        var strings = 0

        fun add(result: StrumResult, truth: FloatArray, skip: Int = -1) {
            strings = result.stringCount
            for (s in 0 until result.stringCount) {
                if (s == skip) continue
                if (!result.present[s]) {
                    lost++
                    continue
                }
                val error = abs(result.cents[s] - truth[s]).toDouble()
                worst[s] = max(worst[s], error)
                squares[s] += error * error
                counts[s]++
            }
        }

        fun print(label: String) {
            println(
                "  ${label.padEnd(38)}worst " + (0 until strings).joinToString(" ") { "%5.2f".format(worst[it]) } +
                    "   rms " + (0 until strings).joinToString(" ") { "%5.2f".format(sqrt(squares[it] / maxOf(1, counts[it]))) } +
                    if (lost > 0) "   lost $lost" else "",
            )
        }
    }
}
