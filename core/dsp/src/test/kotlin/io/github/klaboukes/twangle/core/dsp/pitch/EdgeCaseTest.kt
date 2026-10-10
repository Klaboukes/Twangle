package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Real-world edge cases (ROADMAP phase 5), each fed through the whole chain the app runs (frame
 * assembly → gate → YIN → smoother, see [TunerChain]) and judged by what the user would see:
 * - a very noisy room: broadband noise and mains hum at 0–10 dB signal-to-noise ratio,
 * - high strings whose 2nd/3rd harmonics are much stronger than the fundamental,
 * - long decaying plucks, down to and below the gate,
 * - Bluetooth-headset-like audio: band-limited, and at 8 or 16 kHz.
 */
class EdgeCaseTest {

    private val mapper = NoteMapper()

    // ---- Very noisy room ----

    @Test
    fun `broadband noise - the right note at any SNR from 0 to 10 dB, steadier as it gets quieter`() {
        // Worst shown error allowed at each SNR (dB → cents). Measured worst: 9.7, 2.5, 1.7, 0.8.
        val tolerance = mapOf(0f to 12f, 3f to 3f, 6f to 2f, 10f to 1f)
        for (f in GUITAR_RANGE) for ((snr, cents) in tolerance) {
            val stream = TestSignals.withNoise(TestSignals.weakFundamental(f, length = 3 * RATE), snr, seed = 7)
            val shown = steadyPart(TunerChain().run(stream))
            assertTrue("$f Hz at $snr dB: shown in only ${shown.count { it.isShown }} frames", shown.count { it.isShown } > shown.size / 2)
            assertShownWithin("$f Hz, white noise at $snr dB", f, shown, cents)
        }
    }

    @Test
    fun `mains hum 10 dB below the string - the right note within 3 cents`() {
        for (humHz in listOf(50f, 60f)) for (f in GUITAR_RANGE) {
            val stream = TestSignals.mix(TestSignals.weakFundamental(f, length = 3 * RATE), TestSignals.hum(humHz, 3 * RATE), 10f)
            val shown = steadyPart(TunerChain().run(stream))
            assertTrue("$f Hz with $humHz Hz hum: nothing shown", shown.all { it.isShown })
            assertShownWithin("$f Hz with $humHz Hz hum", f, shown, 3f)
        }
    }

    @Test
    fun `hum or noise alone shows nothing, however loud`() {
        val rooms = mapOf(
            "50 Hz hum" to TestSignals.hum(50f, 3 * RATE, rms = 0.05f),
            "60 Hz hum" to TestSignals.hum(60f, 3 * RATE, rms = 0.05f),
            "60 Hz hum + noise" to TestSignals.mix(TestSignals.hum(60f, 3 * RATE, rms = 0.03f), TestSignals.noise(3 * RATE, seed = 2), 6f),
            "white noise" to TestSignals.noise(3 * RATE, rms = 0.03f, seed = 2),
        )
        for ((name, stream) in rooms) {
            val frames = TunerChain().run(stream)
            assertTrue("$name should open the gate", frames.all { it.gateOpen })
            assertFalse("$name was shown as a note", frames.any { it.isShown })
        }
    }

    // ---- Harmonics on high strings ----

    @Test
    fun `high strings with strong 2nd or 3rd harmonics read the fundamental within 1 cent`() {
        val yin = YinPitchDetector()
        // B3 open (246.9 Hz) up to C6 (1046.5 Hz), past the 12th fret of the high E string.
        for ((name, harmonics) in HIGH_STRING_TONES) for (f in sweep(25, 246.94, 1046.5)) {
            assertWithin("$name, $f Hz", f, yin.detect(TestSignals.harmonics(f, harmonics)), 1f)
            val noisy = TestSignals.withNoise(TestSignals.harmonics(f, harmonics), 20f, seed = 3)
            assertWithin("$name, $f Hz, 20 dB SNR", f, yin.detect(noisy), 1f)
        }
    }

    @Test
    fun `a decaying high-string pluck with a strong 2nd harmonic never flips an octave`() {
        for ((name, harmonics) in HIGH_STRING_TONES) for (f in listOf(246.94f, 329.63f, 493.88f, 659.26f, 987.77f)) {
            val pluck = TestSignals.pluck(f, harmonics, length = 3 * RATE, decaySeconds = 1.0)
            val frames = TunerChain().run(TestSignals.withNoise(pluck, 30f, seed = 4))
            val shown = frames.filter { it.isShown }
            assertTrue("$name $f Hz: shown in ${shown.size} frames", shown.size > 100)
            assertShownWithin("$name $f Hz pluck", f, shown.filter { it.endSample < 3 * RATE / 2 }, 1f)
            // As it fades into the noise it may wobble, but never jumps to another note.
            assertShownWithin("$name $f Hz pluck tail", f, shown, 10f)
        }
    }

    // ---- Sustain decay ----

    @Test
    fun `a long decaying pluck stays on the note to the end, then the gate closes once`() {
        // Peak 0.5, harmonics fading faster than the fundamental, in a quiet room (−70 dBFS).
        for (f in GUITAR_RANGE) {
            val frames = TunerChain().run(pluckOver(f, noiseDbfs = -70f))
            val closed = frames.indexOfFirst { !it.gateOpen && it.endSample > WINDOW }
            assertTrue("$f Hz: gate never closed", closed > 0)
            assertTrue("$f Hz: gate closed after only ${frames[closed].endSample / RATE.toFloat()} s", frames[closed].endSample > 3 * RATE)
            assertTrue("$f Hz: gate opened again", frames.drop(closed).none { it.gateOpen })
            assertShownWithin("$f Hz decay", f, frames.subList(0, closed), 1f)
            // The reading clears soon after the gate closes: no stale needle.
            assertFalse("$f Hz: still shown 5 frames after the gate closed", frames[closed + 5].isShown)
        }
    }

    @Test
    fun `decay in a noisier room - the last frames before the gate closes wobble but never jump`() {
        // −60 dBFS room noise: only ~4 dB below the level at which the gate closes.
        for (f in GUITAR_RANGE) {
            val frames = TunerChain().run(pluckOver(f, noiseDbfs = -60f))
            val closed = frames.indexOfFirst { !it.gateOpen && it.endSample > WINDOW }
            assertTrue("$f Hz: gate opened again", frames.drop(closed).none { it.gateOpen })
            assertShownWithin("$f Hz decay over −60 dBFS", f, frames, 6f)
        }
    }

    @Test
    fun `decay into room noise louder than the gate - the reading fades out without junk`() {
        // −53 dBFS room noise, like the S26 measured: the gate never closes again after a pluck,
        // so the detector itself has to stop reporting once the note sinks into the noise.
        for (f in GUITAR_RANGE) {
            val frames = TunerChain().run(pluckOver(f, noiseDbfs = -53f, seconds = 8))
            assertTrue("$f Hz: gate closed", frames.drop(10).all { it.gateOpen })
            assertShownWithin("$f Hz decay into noise", f, frames, 12f)
            val lastShown = frames.indexOfLast { it.isShown }
            assertTrue("$f Hz: still shown at the end", lastShown < frames.size - 50)
        }
    }

    // ---- Bluetooth-headset-like audio ----

    @Test
    fun `band-limited headset audio at 48 kHz reads within 1 cent, even without a fundamental`() {
        // What Android delivers when it resamples a headset's 8 or 16 kHz mic to the 48 kHz we ask
        // for: narrowband 300–3400 Hz (low E's fundamental and 2nd harmonic are gone) or wideband
        // 100–7000 Hz.
        for ((low, high) in listOf(300f to 3400f, 100f to 7000f)) for (f in GUITAR_RANGE) {
            for ((name, tone) in listOf(
                "pluck" to TestSignals.pluck(f, length = 3 * RATE, decaySeconds = 2.0),
                "weak fundamental" to TestSignals.weakFundamental(f, length = 3 * RATE),
            )) {
                val stream = TestSignals.bandLimit(tone, lowHz = low, highHz = high)
                val shown = steadyPart(TunerChain().run(stream), untilSeconds = 2f)
                assertTrue("$name $f Hz in $low–$high Hz: nothing shown", shown.all { it.isShown })
                assertShownWithin("$name $f Hz in $low–$high Hz", f, shown, 1f)
            }
        }
    }

    @Test
    fun `native 8 and 16 kHz headset audio reads within 1 cent`() {
        for ((rate, band) in listOf(8_000 to (300f to 3400f), 16_000 to (100f to 7000f))) {
            val chain = TunerChain(AnalysisSettings.forSampleRate(rate))
            for (f in GUITAR_RANGE) {
                val pluck = TestSignals.pluck(f, length = 3 * rate, sampleRate = rate, decaySeconds = 2.0)
                val stream = TestSignals.withNoise(TestSignals.bandLimit(pluck, rate, band.first, band.second), 30f, seed = 3)
                val frames = chain.run(stream)
                val shown = frames.filter { it.endSample in rate / 2..2 * rate }
                assertTrue("$f Hz at $rate Hz: nothing shown", shown.all { it.isShown })
                assertShownWithin("$f Hz at $rate Hz", f, shown, 1f)
                // ~47 readings a second, as at 48 kHz.
                assertEquals("frames per second at $rate Hz", 47f, frames.size / 3f, 1.5f)
            }
        }
    }

    @Test
    fun `every supported sample rate detects 62 to 1500 Hz within 1 cent`() {
        for (rate in listOf(8_000, 11_025, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000, 96_000)) {
            val settings = AnalysisSettings.forSampleRate(rate)
            val detector = settings.newPitchDetector()
            val n = settings.windowSize
            for (f in sweep(31, 62.0, 1500.0)) {
                assertWithin("sine $f Hz at $rate", f, detector.detect(TestSignals.sine(f, n, rate)), 1f)
                assertWithin("saw $f Hz at $rate", f, detector.detect(TestSignals.sawtooth(f, n, rate)), 1f)
                assertWithin("pluck $f Hz at $rate", f, detector.detect(TestSignals.pluck(f, n, rate)), 1f)
                val weak = TestSignals.harmonics(f, TestSignals.WEAK_FUNDAMENTAL_HARMONICS, n, rate)
                assertWithin("weak fundamental $f Hz at $rate", f, detector.detect(weak), 1f)
            }
        }
    }

    // ---- Helpers ----

    // A pluck (peak 0.5, 0.8 s decay) over white room noise at [noiseDbfs].
    private fun pluckOver(f: Float, noiseDbfs: Float, seconds: Int = 7): FloatArray {
        val n = seconds * RATE
        val pluck = TestSignals.pluck(f, length = n, decaySeconds = 0.8)
        val noise = TestSignals.noise(n, rms = 10.0.pow(noiseDbfs / 20.0).toFloat(), seed = 3)
        return FloatArray(n) { pluck[it] + noise[it] }
    }

    // Frames from 0.5 s (the smoother has settled) up to [untilSeconds].
    private fun steadyPart(frames: List<TunerChain.Frame>, untilSeconds: Float = Float.MAX_VALUE) =
        frames.filter { it.endSample >= RATE / 2 && it.endSample <= untilSeconds * RATE }

    // Every shown reading is the right note and within [toleranceCents] of [expectedHz].
    private fun assertShownWithin(message: String, expectedHz: Float, frames: List<TunerChain.Frame>, toleranceCents: Float) {
        val expectedNote = mapper.nearestNote(expectedHz)
        for (frame in frames.filter { it.isShown }) {
            val at = "$message at ${"%.2f".format(frame.endSample / RATE.toFloat())} s"
            assertEquals("$at: wrong note (${frame.shownHz} Hz)", expectedNote, mapper.nearestNote(frame.shownHz))
            val error = NoteMapper.centsBetween(frame.shownHz, expectedHz)
            assertTrue("$at: off by $error cents", abs(error) <= toleranceCents)
        }
    }

    private fun assertWithin(message: String, expectedHz: Float, result: PitchResult, toleranceCents: Float) {
        assertTrue("$message: no pitch found", result.isPitched)
        val error = NoteMapper.centsBetween(result.frequencyHz, expectedHz)
        assertTrue("$message: off by $error cents (got ${result.frequencyHz} Hz)", abs(error) <= toleranceCents)
    }

    private fun sweep(count: Int, low: Double, high: Double): List<Float> =
        (0 until count).map { (low * (high / low).pow(it / (count - 1.0))).toFloat() }

    private companion object {
        const val RATE = TestSignals.SAMPLE_RATE
        const val WINDOW = 4096

        /** Drop D low string to the high E string's 12th fret and beyond. */
        val GUITAR_RANGE = listOf(73.42f, 82.41f, 110f, 196f, 246.94f, 329.63f, 493.88f, 659.26f, 1046.5f)

        /**
         * Harmonic amplitudes (fundamental first) of high strings as a phone mic may hear them.
         * The last two used to read an octave high: the 2nd harmonic dominates and the odd ones
         * are weak, so the tone very nearly repeats every half period.
         */
        val HIGH_STRING_TONES = mapOf(
            "strong 2nd and 3rd" to floatArrayOf(0.3f, 1f, 0.7f, 0.3f, 0.2f),
            "faint fundamental" to floatArrayOf(0.05f, 1f, 1f, 0.5f, 0.3f),
            "strong 3rd" to floatArrayOf(0.1f, 0.6f, 1f, 0.3f),
            "strong 2nd only" to floatArrayOf(0.2f, 1f, 0.2f, 0.1f),
            "strong 2nd and 4th" to floatArrayOf(0.2f, 1f, 0.1f, 0.3f),
        )
    }
}
