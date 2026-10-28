package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

/** [FftDifference] against the direct double sum it replaced in [YinPitchDetector]. */
class FftDifferenceTest {

    private val signals = mapOf(
        "sine 82 Hz" to TestSignals.sine(82.41f),
        "weak fundamental 110 Hz" to TestSignals.weakFundamental(110f),
        "sawtooth 1318 Hz" to TestSignals.sawtooth(1318.5f),
        "pluck 329 Hz in noise" to TestSignals.withNoise(TestSignals.pluck(329.6f), snrDb = 10f),
        "white noise" to TestSignals.noise(rms = 0.1f),
    )

    @Test
    fun `matches the direct sum for the 48 kHz analysis sizes`() {
        // W and lastLag as YinPitchDetector uses them for a 4096-sample frame at 48 kHz (N = 4096).
        assertMatchesDirect(integrationLength = 3170, lastLag = 825, start = 100)
    }

    @Test
    fun `matches the direct sum when the span is not a power of two`() {
        assertMatchesDirect(integrationLength = 2900, lastLag = 760, start = 37)
        assertMatchesDirect(integrationLength = 50, lastLag = 13, start = 0)
    }

    @Test
    fun `a constant signal has zero difference at every lag`() {
        val x = FloatArray(4096) { 0.3f }
        val out = DoubleArray(826)
        FftDifference(3170, 825).compute(x, 100, out)
        for (tau in out.indices) assertEquals("d($tau)", 0.0, out[tau], 0.0)
    }

    @Test
    fun `silence has zero difference`() {
        val out = DoubleArray(826) { 1.0 }
        FftDifference(3170, 825).compute(FloatArray(4096), 100, out)
        for (tau in out.indices) assertEquals("d($tau)", 0.0, out[tau], 0.0)
    }

    @Test
    fun `rejects a frame that is too short`() {
        val difference = FftDifference(3170, 825)
        assertThrows(IllegalArgumentException::class.java) { difference.compute(FloatArray(4000), 10, DoubleArray(826)) }
    }

    @Test
    fun `does not allocate`() {
        val difference = FftDifference(3170, 825)
        val x = TestSignals.pluck(110f)
        val out = DoubleArray(826)
        val gains = DoubleArray(difference.size / 2 + 1) { 1.0 }
        fun frame() {
            difference.compute(x, 100, out)
            difference.selectBand(gains, 40)
            difference.bandCorrelation(582)
            difference.taperedEnergy(x, 100, 582)
        }
        repeat(1_000) { frame() }
        assertEquals(0L, allocatedBytes { repeat(100) { frame() } })
    }

    @Test
    fun `with every bin in the band, the band correlation is the tapered correlation of the samples`() {
        // Sounds with nothing near DC or Nyquist, where the band leaves bins out.
        val difference = FftDifference(3170, 825)
        val gains = DoubleArray(difference.size / 2 + 1) { 1.0 }
        for ((name, x) in signals.filterKeys { it != "white noise" }) {
            difference.compute(x, 100, DoubleArray(826))
            difference.selectBand(gains, difference.size / 2 - FftDifference.TAPER_ORDER - 1)
            val energy = taperedSum(x, 100, 3170, difference.size, 0)
            for (tau in listOf(0, 1, 37, 400, 582, 825)) {
                val expected = taperedSum(x, 100, 3170, difference.size, tau)
                assertEquals("$name, r_g($tau)", expected, difference.bandCorrelation(tau), energy * 1e-3)
            }
            assertEquals("$name, tapered energy", energy, difference.taperedEnergy(x, 100, 0), energy * 1e-9)
        }
    }

    @Test
    fun `the band leaves out what lies above it`() {
        // A low E's fundamental with a strong partial at 1 kHz: a band up to 300 Hz keeps only the
        // fundamental, whose tapered correlation it then is.
        val low = TestSignals.sine(82.41f)
        val high = TestSignals.sine(1000f, amplitude = 0.8f)
        val x = FloatArray(low.size) { low[it] + high[it] }
        val difference = FftDifference(3170, 825)
        difference.compute(x, 100, DoubleArray(826))
        val bins = (300.0 * difference.size / 48_000).toInt()
        difference.selectBand(DoubleArray(difference.size / 2 + 1) { 1.0 }, bins)
        val energy = taperedSum(low, 100, 3170, difference.size, 0)
        for (tau in listOf(0, 291, 582, 600)) {
            assertEquals("r_g($tau)", taperedSum(low, 100, 3170, difference.size, tau), difference.bandCorrelation(tau), energy * 1e-2)
        }
    }

    private fun assertMatchesDirect(integrationLength: Int, lastLag: Int, start: Int) {
        val fast = FftDifference(integrationLength, lastLag)
        for ((name, x) in signals) {
            val expected = directDifference(x, start, integrationLength, lastLag)
            val actual = DoubleArray(lastLag + 1)
            fast.compute(x, start, actual)
            // Agreement relative to the size of d itself: far below what moves a reading by 0.01 cent.
            val scale = expected.max()
            for (tau in 0..lastLag) {
                assertEquals("$name, W=$integrationLength, d($tau)", expected[tau], actual[tau], scale * 1e-10)
            }
        }
    }

    /**
     * `Σ_{j<W} g(j) · x[s+j] · x[s+j+τ]` with FftDifference's taper g: v = (cos u − cos θ) / (1 − cos θ),
     * u = 2π(j − W/2)/N, θ = πW/N, g = 3v² − 2v³.
     */
    private fun taperedSum(x: FloatArray, start: Int, integrationLength: Int, n: Int, tau: Int): Double {
        val c = cos(PI * integrationLength / n)
        var sum = 0.0
        for (j in 0 until integrationLength) {
            val v = (cos(2 * PI * (j - integrationLength / 2.0) / n) - c) / (1 - c)
            sum += v * v * (3 - 2 * v) * x[start + j] * x[start + j + tau]
        }
        return sum
    }

    /**
     * The direct form `d(τ) = Σ_{j<W} (x[s+j] − x[s+j+τ])²`, all in doubles. (The detector's old
     * loop subtracted in float first, which is off by about 1e-9 of d; the FFT form is closer.)
     */
    private fun directDifference(x: FloatArray, start: Int, integrationLength: Int, lastLag: Int): DoubleArray =
        DoubleArray(lastLag + 1) { tau ->
            var sum = 0.0
            for (j in start until start + integrationLength) {
                val d = x[j].toDouble() - x[j + tau].toDouble()
                sum += d * d
            }
            sum
        }
}
