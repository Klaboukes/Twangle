package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** What `DroneSynthTest` and `DroneSynthReport` measure in a drone: loudness, pitch, clicks. */
internal object DroneMeasures {

    /** [seconds] of [synth] as it is, rendered in chunks of [chunk] samples (as a player would). */
    fun render(synth: DroneSynth, seconds: Double, chunk: Int = 480): FloatArray {
        val out = FloatArray((seconds * synth.sampleRate).toInt())
        var at = 0
        while (at < out.size) {
            val n = synth.render(out, at, minOf(chunk, out.size - at))
            if (n == 0) break
            at += n
        }
        return out
    }

    fun peak(x: FloatArray, span: IntRange = x.indices): Float {
        var p = 0f
        for (i in span) p = maxOf(p, abs(x[i]))
        return p
    }

    fun rms(x: FloatArray, span: IntRange = x.indices): Double {
        var sum = 0.0
        for (i in span) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (span.last - span.first + 1))
    }

    /**
     * How loud [x] sounds over [span]: the RMS after ITU-R BS.1770's K-weighting (a +4 dB shelf above
     * 1.7 kHz, a high-pass at 38 Hz), as `DroneCandidatesReport` measured the candidates.
     */
    fun loudness(x: FloatArray, rate: Int, span: IntRange): Double {
        val d = DoubleArray(x.size) { x[it].toDouble() }
        val shelved = biquad(d, highShelf(rate, 1_681.97, 4.0, 0.7072))
        val weighted = biquad(shelved, highPass(rate, 38.135, 0.5003))
        var sum = 0.0
        for (i in span) sum += weighted[i] * weighted[i]
        return sqrt(sum / (span.last - span.first + 1))
    }

    /**
     * What the app's own detector reads from [x] every 0.1 s over [span], each in cents from [rootHz]
     * folded into the octave and taken to the nearer of the drone's two pitch classes, the root and
     * the pure fifth (the tanpura's Pa is the fifth's pitch class): readings of any octave of either
     * count. Unpitched frames are left out.
     */
    fun readings(x: FloatArray, rate: Int, rootHz: Double, span: IntRange): List<Double> {
        val settings = AnalysisSettings.forSampleRate(rate, PitchRange.forLowestNote((rootHz / 4).toFloat().coerceAtLeast(20f)))
        val detector = settings.newPitchDetector()
        val window = FloatArray(settings.windowSize)
        val fifth = 1200 * log2(1.5)
        return (span.first until span.last - settings.windowSize step rate / 10).mapNotNull { from ->
            x.copyInto(window, 0, from, from + settings.windowSize)
            val result = detector.detect(window)
            if (!result.isPitched) return@mapNotNull null
            val cents = 1200 * log2(result.frequencyHz / rootHz)
            val folded = cents - 1200 * round(cents / 1200)
            val fromFifth = (cents - fifth) - 1200 * round((cents - fifth) / 1200)
            if (abs(folded) <= abs(fromFifth)) folded else fromFifth
        }
    }

    /**
     * The click detector: [x] through four second-order Butterworth high-passes at 14 kHz (48 dB an
     * octave), well above every partial (8 kHz at most), so a steady drone leaves almost nothing and
     * a jump in the waveform or its slope leaves a spike. Returns the largest |output| over [span].
     */
    fun highPassPeak(x: FloatArray, rate: Int, span: IntRange): Double {
        var y = DoubleArray(x.size) { x[it].toDouble() }
        val k = highPass(rate, 14_000.0, sqrt(0.5))
        repeat(4) { y = biquad(y, k) }
        var p = 0.0
        for (i in span) p = maxOf(p, abs(y[i]))
        return p
    }

    /** The largest sample-to-sample step over [span]. */
    fun largestStep(x: FloatArray, span: IntRange): Double {
        var p = 0.0
        for (i in maxOf(1, span.first)..span.last) p = maxOf(p, abs(x[i] - x[i - 1]).toDouble())
        return p
    }

    /** RBJ cookbook high shelf: b0 b1 b2 a1 a2, normalised by a0. */
    private fun highShelf(rate: Int, hz: Double, gainDb: Double, q: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * hz / rate
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
    private fun highPass(rate: Int, hz: Double, q: Double): DoubleArray {
        val w0 = 2 * PI * hz / rate
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
}
