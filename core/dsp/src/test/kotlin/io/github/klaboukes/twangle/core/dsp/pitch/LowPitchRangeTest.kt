package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.centsBetween
import io.github.klaboukes.twangle.core.dsp.music.nearestNote
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/** The low-note range ([PitchRange.LOW]): an 8192-sample window at 48 kHz that reads down to 30 Hz within 1 cent. */
class LowPitchRangeTest {

    private val settings = AnalysisSettings.forSampleRate(48_000, PitchRange.LOW)
    private val yin = settings.newPitchDetector()
    private val length = settings.windowSize
    private val mapper = NoteMapper()

    @Test
    fun `at 48 kHz the low range is an 8192-sample window from 28 Hz with the standard hop`() {
        assertEquals(8192, settings.windowSize)
        assertEquals(1024, settings.hopSize)
        assertEquals(28f, settings.minFrequencyHz)
        assertEquals(1500f, settings.maxFrequencyHz)
        assertEquals(PitchRange.LOW, settings.range)
        assertEquals(8192, yin.windowSize)
        for (rate in listOf(8_000, 16_000, 44_100, 96_000)) {
            val low = AnalysisSettings.forSampleRate(rate, PitchRange.LOW)
            val standard = AnalysisSettings.forSampleRate(rate)
            assertEquals("window at $rate", 8192 / 48_000.0, low.windowSize.toDouble() / rate, 1e-4)
            assertEquals("hop at $rate", standard.hopSize, low.hopSize)
            assertEquals(low.windowSize, low.newPitchDetector().windowSize)
        }
    }

    @Test
    fun `sines from 30 to 1500 Hz are within 1 cent at any phase`() {
        for (f in sweep(121)) {
            for (phase in listOf(0.0, 1.3, 4.0)) {
                val result = yin.detect(TestSignals.sine(f, length, phase = phase))
                assertWithinCents("sine $f Hz, phase $phase", f, result, ACCURACY_CENTS)
                assertTrue("confidence at $f Hz", result.confidence > 0.95f)
            }
        }
    }

    @Test
    fun `harmonic-rich tones from 30 to 1500 Hz are within 1 cent`() {
        val signals = mapOf<String, (Float) -> FloatArray>(
            "sawtooth" to { TestSignals.sawtooth(it, length) },
            "square" to { TestSignals.square(it, length) },
            "pluck" to { TestSignals.pluck(it, length) },
            "weak fundamental" to { TestSignals.weakFundamental(it, length) },
        )
        for ((name, generate) in signals) {
            for (f in sweep(61)) assertWithinCents("$name $f Hz", f, yin.detect(generate(f)), ACCURACY_CENTS)
        }
    }

    @Test
    fun `every string of every low instrument reads its own note within 1 cent, even a semitone flat`() {
        val lowInstruments = Instruments.all.filter { PitchRange.forLowestNote(mapper.frequencyOf(it.lowestNote)) == PitchRange.LOW }
        // The six-string guitar is here for Open C (C2); its other presets keep the standard window.
        assertEquals(
            listOf("guitar", "guitar_7", "guitar_8", "bass_4", "bass_5", "cello"),
            lowInstruments.map { it.id },
        )
        for (instrument in lowInstruments) {
            for (note in instrument.tunings.flatMap { it.strings }.toSet()) {
                for (cents in listOf(-100f, -50f, 0f, 50f)) {
                    val f = mapper.frequencyOf(note) * 2f.pow(cents / 1200f)
                    for ((name, signal) in listOf(
                        "weak fundamental" to TestSignals.weakFundamental(f, length),
                        "pluck" to TestSignals.pluck(f, length, decaySeconds = 1.0),
                    )) {
                        val result = yin.detect(signal)
                        assertWithinCents("${instrument.id} $note $cents c, $name", f, result, ACCURACY_CENTS)
                    }
                }
                val result = yin.detect(TestSignals.weakFundamental(mapper.frequencyOf(note), length))
                assertEquals("${instrument.id} $note", note, mapper.nearestNote(result.frequencyHz))
            }
        }
    }

    @Test
    fun `tones with background hiss at 20 dB SNR stay within 1 cent`() {
        for (f in sweep(41)) {
            val seed = f.toBits().toLong()
            for ((name, clean) in listOf(
                "sine" to TestSignals.sine(f, length),
                "sawtooth" to TestSignals.sawtooth(f, length),
                "weak fundamental" to TestSignals.weakFundamental(f, length),
            )) {
                val noisy = TestSignals.withNoise(clean, snrDb = 20f, seed = seed)
                assertWithinCents("$name $f Hz + noise", f, yin.detect(noisy), ACCURACY_CENTS)
            }
        }
    }

    @Test
    fun `heavy hiss at 10 dB SNR still gives the right note`() {
        for (f in sweep(41)) {
            val noisy = TestSignals.withNoise(TestSignals.weakFundamental(f, length), snrDb = 10f, seed = 42)
            assertWithinCents("$f Hz + heavy noise", f, yin.detect(noisy), 5f)
        }
    }

    @Test
    fun `below 28 Hz, silence and noise have no pitch`() {
        for (f in listOf(20f, 25f, 27f)) assertFalse("$f Hz", yin.detect(TestSignals.sine(f, length)).isPitched)
        assertFalse(yin.detect(FloatArray(length)).isPitched)
        for (seed in 1L..5L) {
            val result = yin.detect(TestSignals.noise(length, rms = 0.2f, seed = seed))
            assertFalse("noise seed $seed gave $result", result.isPitched)
        }
    }

    @Test
    fun `other sample rates read low notes within 1 cent`() {
        for (rate in listOf(8_000, 16_000, 44_100)) {
            val low = AnalysisSettings.forSampleRate(rate, PitchRange.LOW)
            val detector = low.newPitchDetector()
            for (f in listOf(30.87f, 41.2f, 46.25f, 61.74f, 98f, 220f)) {
                for ((name, signal) in listOf(
                    "sine" to TestSignals.sine(f, low.windowSize, rate),
                    "pluck" to TestSignals.pluck(f, low.windowSize, rate, decaySeconds = 1.0),
                )) {
                    assertWithinCents("$rate Hz: $name $f Hz", f, detector.detect(signal), ACCURACY_CENTS)
                }
            }
        }
    }

    @Test
    fun `detect does not allocate`() {
        val frame = TestSignals.pluck(41.2f, length)
        repeat(WARM_UP) { yin.detect(frame) }
        var result = PitchResult.NONE
        val bytes = allocatedBytes { repeat(50) { result = yin.detect(frame) } }
        assertTrue(result.isPitched)
        assertEquals("bytes allocated by 50 detect() calls", 0L, bytes)
    }

    @Test
    fun `only tunings below what the standard window reads get the low range`() {
        for (a4 in listOf(NoteMapper.MIN_A4_HZ, 440f, NoteMapper.MAX_A4_HZ)) {
            val mapper = NoteMapper(a4)
            for (tuning in Tunings.all - Tunings.OPEN_C) {
                assertEquals("${tuning.id} at A4 = $a4", PitchRange.STANDARD, PitchRange.forLowestNote(mapper.frequencyOf(tuning.lowestNote)))
            }
            for (instrument in listOf(Instruments.UKULELE, Instruments.VIOLIN, Instruments.VIOLA, Instruments.MANDOLIN)) {
                for (tuning in instrument.tunings) {
                    assertEquals(tuning.id, PitchRange.STANDARD, PitchRange.forLowestNote(mapper.frequencyOf(tuning.lowestNote)))
                }
            }
            for (instrument in listOf(Instruments.GUITAR_7, Instruments.GUITAR_8, Instruments.BASS_4, Instruments.BASS_5)) {
                for (tuning in instrument.tunings) {
                    assertEquals(tuning.id, PitchRange.LOW, PitchRange.forLowestNote(mapper.frequencyOf(tuning.lowestNote)))
                }
            }
        }
        // The cello's C2 (65.4 Hz), and Open C's on the guitar, is a semitone flat at 61.7 Hz, just out
        // of the standard range; tuned to A4 = 466 Hz it sits high enough for the standard window.
        for (c2 in listOf(Instruments.CELLO.defaultTuning.lowestNote, Tunings.OPEN_C.lowestNote)) {
            assertEquals(PitchRange.LOW, PitchRange.forLowestNote(NoteMapper(415f).frequencyOf(c2)))
            assertEquals(PitchRange.LOW, PitchRange.forLowestNote(NoteMapper(440f).frequencyOf(c2)))
            assertEquals(PitchRange.STANDARD, PitchRange.forLowestNote(NoteMapper(466f).frequencyOf(c2)))
        }
        // The edge: a string whose semitone-flat pitch drops under 62 Hz.
        assertEquals(PitchRange.STANDARD, PitchRange.forLowestNote(66f))
        assertEquals(PitchRange.LOW, PitchRange.forLowestNote(65f))
    }

    // ---- Mains hum (MainsHumCanceller) ----

    @Test
    fun `mains hum 6 dB below every low string, even a semitone flat, reads within 1 cent from the first frame`() {
        for (humHz in listOf(50f, 60f)) for (f in lowStrings().flatMap { listOf(it, it * SEMITONE_DOWN) }) {
            val (onset, frames) = pluckOverHum(f, humHz)
            assertHumFreeReading("$f Hz over $humHz Hz hum", f, frames, onset)
        }
    }

    @Test
    fun `mains a little off 50 or 60 Hz is followed, and the strings still read within 1 cent`() {
        for (humHz in listOf(50.05f, 49.9f, 59.95f, 60.1f)) for (f in listOf(30.87f, 41.2f, 49f, 61.74f, 98f, 123.47f)) {
            val (onset, frames) = pluckOverHum(f, humHz, leadSeconds = 4)
            assertHumFreeReading("$f Hz over $humHz Hz hum", f, frames, onset)
        }
    }

    @Test
    fun `mains hum alone shows no note, from the moment the microphone opens`() {
        for (humHz in listOf(50f, 60f, 50.1f, 59.9f)) {
            val hum = TestSignals.hum(humHz, 4 * RATE, rms = 0.05f)
            for ((name, stream) in listOf("$humHz Hz hum" to hum, "$humHz Hz hum + noise" to TestSignals.mix(hum, TestSignals.noise(4 * RATE, seed = 2), 6f))) {
                val frames = TunerChain(settings).run(stream)
                assertFalse("$name was shown as a note", frames.any { it.isShown })
            }
        }
    }

    @Test
    fun `in a quiet room the notes beside the notches read within 0,1 cent of how they read without them`() {
        val lead = RATE
        // G1, B1, G2 and B2 next to 50, 60, 100 and 120 Hz; B♭1, B1 a semitone flat, next to 60 Hz.
        for (f in listOf(49f, 58.27f, 61.74f, 98f, 123.47f)) {
            for ((name, note) in listOf(
                "sine" to TestSignals.sine(f, 3 * RATE),
                "pluck" to TestSignals.pluck(f, 3 * RATE, decaySeconds = 2.0),
                "weak fundamental" to TestSignals.weakFundamental(f, 3 * RATE),
            )) {
                val stream = TestSignals.withNoise(FloatArray(lead + note.size) { if (it >= lead) note[it - lead] else 0f }, 40f, seed = 3)
                val without = TunerChain(settings, humCanceller = { null }).run(stream)
                val with = TunerChain(settings).run(stream)
                for (i in with.indices) {
                    val message = "$name $f Hz at ${with[i].endSample / RATE.toFloat()} s"
                    assertEquals("$message: shown", without[i].isShown, with[i].isShown)
                    if (!with[i].isShown) continue
                    val moved = abs(NoteMapper.centsBetween(with[i].shownHz, without[i].shownHz))
                    assertTrue("$message: moved $moved cents", moved <= 0.1f)
                }
            }
        }
    }

    @Test
    fun `a note already sounding as the canceller starts reads as it would without it`() {
        // The canceller learns it as the room; taking that out would ring the notches beside it.
        // A2 (110 Hz) is a whole number of 10 Hz from every notch.
        for (f in listOf(30.87f, 41.2f, 49f, 58.27f, 61.74f, 82.41f, 98f, 110f, 123.47f)) {
            for ((name, note) in listOf(
                "sine" to TestSignals.sine(f, 2 * RATE),
                "weak fundamental" to TestSignals.weakFundamental(f, 2 * RATE),
            )) {
                val without = TunerChain(settings, humCanceller = { null }).run(note)
                val with = TunerChain(settings).run(note)
                for (i in with.indices) {
                    val message = "$name $f Hz at ${with[i].endSample / RATE.toFloat()} s"
                    assertEquals("$message: shown", without[i].isShown, with[i].isShown)
                    if (!with[i].isShown) continue
                    val moved = abs(NoteMapper.centsBetween(with[i].shownHz, without[i].shownHz))
                    assertTrue("$message: moved $moved cents", moved <= 0.1f)
                }
            }
        }
    }

    @Test
    fun `the hum canceller does not allocate`() {
        val canceller = MainsHumCanceller(48_000)
        val stream = TestSignals.mix(TestSignals.pluck(41.2f, RATE), TestSignals.hum(50f, RATE), 6f)
        repeat(WARM_UP) { canceller.process(stream.copyOf(), 0, stream.size) }
        val chunk = FloatArray(1024)
        val bytes = allocatedBytes { repeat(200) { canceller.process(chunk, 0, chunk.size) } }
        assertEquals("bytes allocated by the canceller", 0L, bytes)
        assertTrue(MainsHumCanceller.isMainsPitch(50.3f))
        assertFalse(MainsHumCanceller.isMainsPitch(49f))
        assertFalse(MainsHumCanceller.isMainsPitch(58.27f))
        assertFalse(MainsHumCanceller.isMainsPitch(61.74f))
    }

    @Test
    fun `the standard range has no hum canceller`() {
        assertEquals(null, AnalysisSettings.forSampleRate(48_000).newHumCanceller())
        for (rate in listOf(8_000, 44_100, 48_000)) {
            assertEquals(rate, AnalysisSettings.forSampleRate(rate, PitchRange.LOW).newHumCanceller()?.sampleRate)
        }
    }

    // Every string of every low instrument's tunings, once.
    private fun lowStrings(): List<Float> = Instruments.all
        .filter { PitchRange.forLowestNote(mapper.frequencyOf(it.lowestNote)) == PitchRange.LOW }
        .flatMap { instrument -> instrument.tunings.flatMap { it.strings } }
        .toSet().map { mapper.frequencyOf(it) }

    // [leadSeconds] of hum (it was there before the pluck), then a pluck at [f] Hz, the hum 6 dB below
    // it, over faint room noise, through the whole chain. Returns the pluck's first sample and the frames.
    private fun pluckOverHum(f: Float, humHz: Float, leadSeconds: Int = 2): Pair<Int, List<TunerChain.Frame>> {
        val lead = leadSeconds * RATE
        val note = TestSignals.pluck(f, 3 * RATE, decaySeconds = 2.0)
        val hum = TestSignals.hum(humHz, lead + note.size, rms = 0.5f * TestSignals.rms(note.copyOf(RATE / 2)).toFloat())
        val stream = TestSignals.withNoise(FloatArray(hum.size) { hum[it] + if (it >= lead) note[it - lead] else 0f }, 50f, seed = 5)
        return lead to TunerChain(settings).run(stream)
    }

    // Shown from at most 0.25 s after the pluck, in every frame to 2.5 s, and every reading within 1 cent.
    private fun assertHumFreeReading(message: String, f: Float, frames: List<TunerChain.Frame>, onset: Int) {
        val after = frames.filter { it.endSample > onset && it.endSample <= onset + 5 * RATE / 2 }
        val first = after.indexOfFirst { it.isShown }
        assertTrue("$message: not shown within 0.25 s", first >= 0 && after[first].endSample <= onset + RATE / 4)
        for (frame in after.drop(first)) {
            assertTrue("$message: lost at ${(frame.endSample - onset) / RATE.toFloat()} s", frame.isShown)
            val error = NoteMapper.centsBetween(frame.shownHz, f)
            assertTrue("$message: off by $error cents at ${(frame.endSample - onset) / RATE.toFloat()} s", abs(error) <= ACCURACY_CENTS)
        }
    }

    // [count] frequencies spaced evenly in pitch from 30 to 1500 Hz, both ends included.
    private fun sweep(count: Int): List<Float> =
        (0 until count).map { (MIN_HZ * (MAX_HZ / MIN_HZ).pow(it / (count - 1.0))).toFloat() }

    private fun assertWithinCents(message: String, expectedHz: Float, result: PitchResult, toleranceCents: Float) {
        assertTrue("$message: no pitch found", result.isPitched)
        val error = NoteMapper.centsBetween(result.frequencyHz, expectedHz)
        assertTrue("$message: off by $error cents (got ${result.frequencyHz} Hz)", abs(error) <= toleranceCents)
    }

    private companion object {
        const val ACCURACY_CENTS = 1f
        const val MIN_HZ = 30.0
        const val MAX_HZ = 1500.0
        const val WARM_UP = 200
        const val RATE = 48_000
        val SEMITONE_DOWN = 2f.pow(-1f / 12)
    }
}
