package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingThrottle
import io.github.klaboukes.twangle.feature.tuner.TuneWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenReadingKeyTest {

    private fun readout(cents: Float, inTune: Boolean = false, note: String = "E") =
        NoteReadout(noteName = note, octave = 2, cents = cents, inTune = inTune)

    @Test
    fun `tighten, loosen and in tune are three situations`() {
        val flat = spokenReadingKey(readout(-12f), TuneWord.TIGHTEN)
        val sharp = spokenReadingKey(readout(5f), TuneWord.LOOSEN)
        val inTune = spokenReadingKey(readout(-2f, inTune = true), TuneWord.IN_TUNE)
        assertEquals(3, setOf(flat, sharp, inTune).size)
    }

    @Test
    fun `only the cents moving keeps the situation`() {
        assertEquals(spokenReadingKey(readout(-12f), TuneWord.TIGHTEN), spokenReadingKey(readout(-30f), TuneWord.TIGHTEN))
        assertEquals(spokenReadingKey(readout(-6f, inTune = true), TuneWord.IN_TUNE), spokenReadingKey(readout(4f, inTune = true), TuneWord.IN_TUNE))
    }

    /** The word is held near the note, so a reading wobbling across it (−2, 0, +1 cents, still "Tighten") is one situation. */
    @Test
    fun `a reading wobbling across the note under the same word keeps the situation`() {
        val keys = listOf(-2f, 0f, 1f).map { spokenReadingKey(readout(it), TuneWord.TIGHTEN) }
        assertEquals(1, keys.toSet().size)
    }

    @Test
    fun `the same direction in either mode's words is one situation`() {
        assertEquals(spokenReadingKey(readout(-12f), TuneWord.TIGHTEN), spokenReadingKey(readout(-12f), TuneWord.TOO_LOW))
        assertEquals(spokenReadingKey(readout(8f), TuneWord.LOOSEN), spokenReadingKey(readout(8f), TuneWord.TOO_HIGH))
    }

    @Test
    fun `a tip or the trail's motion is another situation`() {
        val plain = spokenReadingKey(readout(-12f), TuneWord.TIGHTEN)
        val tip = spokenReadingKey(readout(-12f), TuneWord.TIGHTEN, tip = "Play a bit louder")
        val wavering = spokenReadingKey(readout(-12f), TuneWord.TIGHTEN, motion = "wavering")
        assertEquals(3, setOf(plain, tip, wavering).size)
        assertEquals(wavering, spokenReadingKey(readout(-15f), TuneWord.TIGHTEN, motion = "wavering"))
    }

    @Test
    fun `another note or octave is another situation`() {
        assertNotEquals(spokenReadingKey(readout(-12f), TuneWord.TIGHTEN), spokenReadingKey(readout(-12f, note = "A"), TuneWord.TIGHTEN))
        assertNotEquals(spokenReadingKey(readout(-12f), TuneWord.TIGHTEN), spokenReadingKey(readout(-12f).copy(octave = 3), TuneWord.TIGHTEN))
    }

    @Test
    fun `turning past the note is said after 400 ms, the cents alone never`() {
        val throttle = SpokenReadingThrottle()
        throttle.publish("E 2, 12 cents flat, Tighten", spokenReadingKey(readout(-12f), TuneWord.TIGHTEN), 0)
        assertNull(throttle.delayFor("E 2, 10 cents flat, Tighten", spokenReadingKey(readout(-10f), TuneWord.TIGHTEN), 5_000))
        assertEquals(
            SpokenReadingThrottle.DEFAULT_SETTLE_MILLIS,
            throttle.delayFor("E 2, 9 cents sharp, Loosen", spokenReadingKey(readout(9f), TuneWord.LOOSEN), 6_000),
        )
    }
}
