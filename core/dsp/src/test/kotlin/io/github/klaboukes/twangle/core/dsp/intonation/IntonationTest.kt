package io.github.klaboukes.twangle.core.dsp.intonation

import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict.FINE
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict.MOVE_SADDLE_BACK
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict.MOVE_SADDLE_FORWARD
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict.NOT_THE_OCTAVE
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchDetector
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.pitch.TunerChain
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class IntonationTest {

    private val mapper = NoteMapper()

    // The comparison on its own.

    @Test
    fun `the octave is 0 cents off, and the offset is signed`() {
        assertEquals(0f, Intonation.offsetCents(82.41f, 164.82f), 0.01f)
        assertEquals(10f, Intonation.offsetCents(100f, 200f * cents(10f)), 0.01f)
        assertEquals(-10f, Intonation.offsetCents(100f, 200f * cents(-10f)), 0.01f)
        assertEquals(-1200f, Intonation.offsetCents(110f, 110f), 0.01f)
        assertEquals(-100f, Intonation.offsetCents(110f, 220f * cents(-100f)), 0.01f)
    }

    @Test
    fun `within the tolerance is fine, beyond it the saddle moves, past half a semitone it is not the octave`() {
        assertEquals(FINE, Intonation.verdictOf(0f))
        assertEquals(FINE, Intonation.verdictOf(3f))
        assertEquals(FINE, Intonation.verdictOf(-3f))
        assertEquals(MOVE_SADDLE_BACK, Intonation.verdictOf(3.1f))
        assertEquals(MOVE_SADDLE_FORWARD, Intonation.verdictOf(-3.1f))
        assertEquals(MOVE_SADDLE_BACK, Intonation.verdictOf(50f))
        assertEquals(MOVE_SADDLE_FORWARD, Intonation.verdictOf(-50f))
        assertEquals(NOT_THE_OCTAVE, Intonation.verdictOf(50.1f))
        assertEquals(NOT_THE_OCTAVE, Intonation.verdictOf(-50.1f))
        assertEquals(NOT_THE_OCTAVE, Intonation.verdictOf(-1200f))
        assertEquals(NOT_THE_OCTAVE, Intonation.verdictOf(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `a different tolerance can be passed`() {
        assertEquals(FINE, Intonation.verdictOf(4.5f, toleranceCents = 5f))
        assertEquals(MOVE_SADDLE_BACK, Intonation.verdictOf(1.5f, toleranceCents = 1f))
        assertEquals(MOVE_SADDLE_BACK, Intonation.verdictOf(0.1f, toleranceCents = 0f))
    }

    @Test
    fun `bad input is refused`() {
        assertThrows(IllegalArgumentException::class.java) { Intonation.offsetCents(0f, 110f) }
        assertThrows(IllegalArgumentException::class.java) { Intonation.offsetCents(110f, -1f) }
        assertThrows(IllegalArgumentException::class.java) { Intonation.offsetCents(Float.NaN, 110f) }
        assertThrows(IllegalArgumentException::class.java) { Intonation.offsetCents(110f, Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { Intonation.verdictOf(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Intonation.verdictOf(0f, toleranceCents = -1f) }
        assertThrows(IllegalArgumentException::class.java) { Intonation.verdictOf(0f, toleranceCents = 50f) }
    }

    @Test
    fun `the comparison is relative, so a mistuned open string or another A4 gets the same verdict`() {
        for (a4 in listOf(415f, 440f, 466f)) {
            for (openOffCents in listOf(-30f, 0f, 20f)) {
                val open = NoteMapper(a4).frequencyOf(Note(52)) * cents(openOffCents)
                assertEquals(MOVE_SADDLE_BACK, Intonation.check(open, 2 * open * cents(6f)).verdict)
                assertEquals(MOVE_SADDLE_FORWARD, Intonation.check(open, 2 * open * cents(-6f)).verdict)
                assertEquals(FINE, Intonation.check(open, 2 * open * cents(1f)).verdict)
            }
        }
    }

    // Synthetic plucks through the pitch detector, every string of every instrument's standard tuning.

    @Test
    fun `a sharp 12th fret moves the saddle back`() {
        forEveryString { name, openHz, detector, length ->
            for (off in listOf(4f, 8f, 25f)) {
                val result = read(openHz, openHz * 2 * cents(off), detector, length)
                assertEquals("$name +$off cents: $result", MOVE_SADDLE_BACK, result.verdict)
                assertNear("$name +$off cents", off, result)
            }
        }
    }

    @Test
    fun `a flat 12th fret moves the saddle forward`() {
        forEveryString { name, openHz, detector, length ->
            for (off in listOf(-4f, -8f, -25f)) {
                val result = read(openHz, openHz * 2 * cents(off), detector, length)
                assertEquals("$name $off cents: $result", MOVE_SADDLE_FORWARD, result.verdict)
                assertNear("$name $off cents", off, result)
            }
        }
    }

    @Test
    fun `a 12th fret within a cent and a half of the octave is fine`() {
        forEveryString { name, openHz, detector, length ->
            for (off in listOf(0f, 1.5f, -1.5f)) {
                val result = read(openHz, openHz * 2 * cents(off), detector, length)
                assertEquals("$name $off cents: $result", FINE, result.verdict)
                assertNear("$name $off cents", off, result)
            }
        }
    }

    @Test
    fun `a wrong note at the fret is not the octave`() {
        val wrong = mapOf(
            "11th fret" to -100f,
            "13th fret" to 100f,
            "7th fret" to -500f,
            "open string again" to -1200f,
            "a fifth above the octave" to 700f,
            "an octave and a quarter tone" to 55f,
        )
        forEveryString { name, openHz, detector, length ->
            for ((what, off) in wrong) {
                val fret = openHz * 2 * cents(off)
                if (fret > MAX_DETECTED_HZ) continue
                val result = read(openHz, fret, detector, length)
                assertEquals("$name, $what: $result", NOT_THE_OCTAVE, result.verdict)
            }
        }
    }

    @Test
    fun `settled readings from the tuner chain give the verdict on every guitar string`() {
        val chain = TunerChain()
        for (note in Tunings.STANDARD.strings) {
            val openHz = mapper.frequencyOf(note)
            for ((off, expected) in listOf(8f to MOVE_SADDLE_BACK, -8f to MOVE_SADDLE_FORWARD, 1f to FINE)) {
                val open = settled(chain, openHz)
                val fret = settled(chain, openHz * 2 * cents(off))
                val result = Intonation.check(open, fret)
                assertEquals("$note $off cents: $result", expected, result.verdict)
                assertNear("$note $off cents", off, result)
            }
        }
    }

    /** The median of what the tuner shows over 1.5 s of a pluck, as the mode will read a step. */
    private fun settled(chain: TunerChain, hz: Float): Float {
        val shown = chain.run(TestSignals.pluck(hz, length = 72_000, decaySeconds = 1.0))
            .filter { it.isShown }
            .map { it.shownHz }
            .sorted()
        assertTrue("nothing shown for $hz Hz", shown.size > 10)
        return shown[shown.size / 2]
    }

    private fun forEveryString(block: (name: String, openHz: Float, detector: PitchDetector, length: Int) -> Unit) {
        for (instrument in Instruments.all) {
            val tuning = instrument.defaultTuning
            val range = PitchRange.forLowestNote(mapper.frequencyOf(tuning.lowestNote))
            val settings = AnalysisSettings.forSampleRate(TestSignals.SAMPLE_RATE, range)
            val detector = settings.newPitchDetector()
            for (note in tuning.strings) {
                block("${instrument.id} $note", mapper.frequencyOf(note), detector, settings.windowSize)
            }
        }
    }

    private fun read(openHz: Float, fretHz: Float, detector: PitchDetector, length: Int): IntonationResult {
        val open = detector.detect(TestSignals.pluck(openHz, length = length))
        val fret = detector.detect(TestSignals.pluck(fretHz, length = length))
        assertTrue("open $openHz Hz not read", open.isPitched)
        assertTrue("fret $fretHz Hz not read", fret.isPitched)
        return Intonation.check(open.frequencyHz, fret.frequencyHz)
    }

    private fun assertNear(message: String, expectedCents: Float, result: IntonationResult) {
        assertTrue(
            "$message: read ${result.offsetCents} cents",
            abs(result.offsetCents - expectedCents) <= READING_ERROR_CENTS,
        )
    }

    private fun cents(c: Float): Float = 2.0.pow(c / 1200.0).toFloat()

    private companion object {
        /** Two readings, each within the ±1 cent target, measured well inside it. */
        const val READING_ERROR_CENTS = 1f
        const val MAX_DETECTED_HZ = 1500f
    }
}
