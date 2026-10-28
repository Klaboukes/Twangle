package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.centsBetween
import io.github.klaboukes.twangle.core.dsp.music.nearestNote
import io.github.klaboukes.twangle.core.dsp.strum.StrumChords
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

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
            // At 0 dB YIN finds the top note (C6) in only about a fifth of the frames, and a reading
            // needs three agreeing frames in a row to start, so it shows in about a fifth of them.
            val minShown = if (snr > 0f || f < 1000f) shown.size / 2 else shown.size / 8
            assertTrue("$f Hz at $snr dB: shown in only ${shown.count { it.isShown }} frames", shown.count { it.isShown } > minShown)
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

    @Test
    fun `a G string with a faint fundamental and faint odd harmonics reads G3, not G4`() {
        // How a phone can hear the G string: its 196 Hz fundamental and odd harmonics faint, so the
        // tone very nearly repeats every half period. It used to read G4 in every frame. Measured:
        // 0.5–2 % of the mean of d left at the half period, against under 0.2 % for a true G4.
        val yin = YinPitchDetector()
        for ((name, harmonics) in FAINT_ODD_TONES) for (snr in listOf(60f, 40f, 30f)) {
            val tone = TestSignals.withNoise(TestSignals.harmonics(196f, harmonics), snr, seed = 3)
            assertWithin("$name at $snr dB", 196f, yin.detect(tone), 1f)
        }
        // A true G4 still reads G4, clean or in noise.
        for (snr in listOf(99f, 30f, 20f, 10f)) {
            for (tone in listOf(TestSignals.sine(392f), TestSignals.sawtooth(392f), TestSignals.weakFundamental(392f))) {
                assertWithin("G4 at $snr dB", 392f, yin.detect(TestSignals.withNoise(tone, snr, seed = 3)), 5f)
            }
        }
    }

    // ---- Stiff strings ----

    @Test
    fun `a stiff string reads its fundamental within 1 cent half a second and a second after the pluck`() {
        // A real string's partial k sits at k·f·√(1 + B·k²) (Fletcher 1964), sharp of k·f, and the
        // whole waveform used to pull the reading sharp with them: the low E plucked hard read +3.6
        // cents at 0.5 s. Every string of the standard tuning at the strum tests' stiffness and at
        // three times it, plucked with a pick (partials at 1/k) and a finger (1/k²).
        val standard = Tunings.STANDARD.strings.map { mapper.frequencyOf(it) }
        for (stiffer in listOf(1.0, 3.0)) for ((touch, gain) in TOUCHES) for ((string, f) in standard.withIndex()) {
            val b = STRING_STIFFNESS[string] * stiffer
            val pluck = StrumChords.stiffStrum(
                floatArrayOf(f), length = 3 * RATE / 2, inharmonicity = doubleArrayOf(b),
                harmonicGain = { _, k -> gain(k) }, fromSeconds = 0.0,
            )
            val frames = TunerChain().run(pluck)
            // Partial 1 itself, as StrumDetectorTest defines a stiff string's fundamental.
            val fundamental = (f * sqrt(1 + b)).toFloat()
            for (seconds in listOf(0.5f, 1f)) {
                val frame = frames.first { it.endSample >= seconds * RATE }
                val at = "$touch-plucked ${Tunings.STANDARD.strings[string]} at B = $b, $seconds s after the pluck"
                assertTrue("$at: nothing shown", frame.isShown)
                val error = NoteMapper.centsBetween(frame.shownHz, fundamental)
                assertTrue("$at: off by $error cents", abs(error) <= 1f)
            }
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

    // ---- Ticks, knocks and clicks ----

    @Test
    fun `ticks, knocks and clicks in a quiet or noisy room show nothing`() {
        // Each rang at a pitch and showed as a note for a tenth of a second, a 2–5 kHz tick as a
        // note far sharp of the high E string. Ten of each, one every 0.4 s, in either range. In the
        // low range a 30 ms ping is left out: its 36 ms blocks (one cycle of its lowest note) can't
        // see a sound fade that fast, so a ping like that may show (a known limit).
        for (range in PitchRange.entries) for (roomDbfs in listOf(-70f, -55f)) for ((name, click) in CLICKS) {
            if (range == PitchRange.LOW && name.endsWith("30 ms")) continue
            val frames = TunerChain(AnalysisSettings.forSampleRate(RATE, range)).run(clicksOver(click, roomDbfs))
            assertTrue("$name over $roomDbfs dBFS never opened the gate", frames.any { it.gateOpen })
            val shown = frames.filter { it.isShown }
            assertTrue("$range: $name over $roomDbfs dBFS shown as ${shown.map { it.shownHz }}", shown.isEmpty())
        }
    }

    @Test
    fun `ticks while a string rings neither lose the reading nor move it by a cent`() {
        // A tick every 0.4 s over a string at peak 0.2, ringing for 4 s: quieter than the string
        // (peak 0.05) or much louder (peak 0.9). Measured worst: 0.2 and 0.7 cents. Ticks about as
        // loud as the string move it up to 3.5 cents for a frame or two.
        for (tickPeak in listOf(0.05f, 0.9f)) for (f in GUITAR_RANGE) for ((name, click) in TICKS) {
            val n = 4 * RATE
            val note = TestSignals.withNoise(TestSignals.weakFundamental(f, length = n, amplitude = 0.2f), 40f, seed = 6)
            val ticks = clicksOver(FloatArray(click.size) { click[it] * tickPeak / CLICK_PEAK }, roomDbfs = -120f, seconds = 4)
            val frames = TunerChain().run(FloatArray(n) { note[it] + ticks[it] })
            val steady = steadyPart(frames)
            assertTrue("$f Hz with ${name}s at $tickPeak: reading lost", steady.all { it.isShown })
            assertShownWithin("$f Hz with ${name}s at $tickPeak", f, steady, 1f)
        }
    }

    @Test
    fun `a plucked string still shows within 0 point 15 s of its first sample`() {
        for (f in GUITAR_RANGE) {
            val silence = TestSignals.noise(RATE / 2, rms = 0.0003f, seed = 8)
            val frames = TunerChain().run(silence + pluckOver(f, noiseDbfs = -70f, seconds = 2))
            val first = frames.first { it.isShown }
            val delaySeconds = (first.endSample - silence.size) / RATE.toFloat()
            // One hop to reach the window, three agreeing frames: ≈ 64 ms plus the frame's slack.
            assertTrue("$f Hz shown after $delaySeconds s", delaySeconds < 0.15f)
            assertShownWithin("$f Hz first readings", f, frames.filter { it.endSample < silence.size + RATE / 4 }, 3f)
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

    // [click] every 0.4 s from 0.2 s on, over white room noise at [roomDbfs], [seconds] long.
    private fun clicksOver(click: FloatArray, roomDbfs: Float, seconds: Int = 4): FloatArray {
        val stream = TestSignals.noise(seconds * RATE, rms = 10.0.pow(roomDbfs / 20.0).toFloat(), seed = 9)
        for (start in RATE / 5 until stream.size - click.size step 2 * RATE / 5) {
            for (i in click.indices) stream[start + i] += click[i]
        }
        return stream
    }

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
         * Inharmonicity B of the standard tuning's strings, low E to high E, as the strum tests
         * assume (`StrumDetectorTest`): a wound low E is the stiffest.
         */
        val STRING_STIFFNESS = doubleArrayOf(1.5e-4, 1e-4, 6e-5, 4e-5, 3e-5, 2e-5)

        /** Partial k's starting level: a pick's sharp attack (1/k) or a finger's soft one (1/k²). */
        val TOUCHES: Map<String, (Int) -> Double> = mapOf(
            "pick" to { k -> 1.0 / k },
            "finger" to { k -> 1.0 / (k * k) },
        )

        const val CLICK_PEAK = 0.9f

        /**
         * Ticks at peak [CLICK_PEAK] (−1 dBFS): single resonances inside and above the range ringing
         * for 2 ms, a knock and a clock tick with three each, a digital click and a 5 ms burst of noise.
         */
        val TICKS: Map<String, FloatArray> = buildMap {
            for (f in listOf(330f, 660f, 1000f, 1400f, 2000f, 3000f, 5000f)) {
                put("$f Hz tick", TestSignals.tick(floatArrayOf(f), 0.002, length = RATE / 5, amplitude = CLICK_PEAK))
            }
            put("knock", TestSignals.tick(floatArrayOf(420f, 1130f, 2470f), 0.006, length = RATE / 5, amplitude = CLICK_PEAK))
            put("clock tick", TestSignals.tick(floatArrayOf(2100f, 3300f, 4700f), 0.003, length = RATE / 5, amplitude = CLICK_PEAK))
            put("digital click", floatArrayOf(CLICK_PEAK))
            put("noise burst", TestSignals.noise(RATE / 200, rms = CLICK_PEAK / 3, seed = 10))
        }

        /**
         * [TICKS] plus pings: the same resonances ringing for 8 and 30 ms. On their own none of these
         * may show; over a ringing string a ping as loud as it can pull the reading off (a known limit).
         */
        val CLICKS: Map<String, FloatArray> = TICKS + buildMap {
            for (f in listOf(330f, 660f, 1000f, 1400f, 2000f, 3000f, 5000f)) for (ms in listOf(8, 30)) {
                put("$f Hz ping ringing $ms ms", TestSignals.tick(floatArrayOf(f), ms / 1000.0, length = RATE / 5, amplitude = CLICK_PEAK))
            }
        }

        /**
         * Harmonic amplitudes (fundamental first) of high strings as a phone mic may hear them.
         * The last two used to read an octave high: the 2nd harmonic dominates and the odd ones
         * are weak, so the tone very nearly repeats every half period.
         */
        val FAINT_ODD_TONES = mapOf(
            "very faint fundamental" to floatArrayOf(0.03f, 1f, 0.1f, 0.4f, 0.05f, 0.2f),
            "no fundamental to speak of" to floatArrayOf(0.01f, 1f, 0.05f, 0.5f, 0.02f, 0.2f),
        )

        val HIGH_STRING_TONES = mapOf(
            "strong 2nd and 3rd" to floatArrayOf(0.3f, 1f, 0.7f, 0.3f, 0.2f),
            "faint fundamental" to floatArrayOf(0.05f, 1f, 1f, 0.5f, 0.3f),
            "strong 3rd" to floatArrayOf(0.1f, 0.6f, 1f, 0.3f),
            "strong 2nd only" to floatArrayOf(0.2f, 1f, 0.2f, 0.1f),
            "strong 2nd and 4th" to floatArrayOf(0.2f, 1f, 0.1f, 0.3f),
        )
    }
}
