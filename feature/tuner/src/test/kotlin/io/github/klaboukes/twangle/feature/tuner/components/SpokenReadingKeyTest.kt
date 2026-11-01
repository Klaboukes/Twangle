package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.core.designsystem.SpokenReadingThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpokenReadingKeyTest {

    private fun readout(cents: Float, inTune: Boolean = false, note: String = "E") =
        NoteReadout(noteName = note, octave = 2, frequencyHz = 82f, cents = cents, inTune = inTune)

    @Test
    fun `flat, sharp and in tune are three situations`() {
        val flat = spokenReadingKey(readout(-12f))
        val sharp = spokenReadingKey(readout(5f))
        val inTune = spokenReadingKey(readout(-2f, inTune = true))
        assertEquals(3, setOf(flat, sharp, inTune).size)
    }

    @Test
    fun `only the cents moving keeps the situation`() {
        assertEquals(spokenReadingKey(readout(-12f)), spokenReadingKey(readout(-30f)))
        assertEquals(spokenReadingKey(readout(-6f, inTune = true)), spokenReadingKey(readout(4f, inTune = true)))
    }

    @Test
    fun `another note or octave is another situation`() {
        assertNotEquals(spokenReadingKey(readout(-12f)), spokenReadingKey(readout(-12f, note = "A")))
        assertNotEquals(spokenReadingKey(readout(-12f)), spokenReadingKey(readout(-12f).copy(octave = 3)))
    }

    @Test
    fun `turning past the note is said after 400 ms, not 2 s`() {
        val throttle = SpokenReadingThrottle()
        throttle.publish("E 2, 12 cents flat", spokenReadingKey(readout(-12f)), 0)
        assertEquals(
            SpokenReadingThrottle.DEFAULT_SETTLE_MILLIS,
            throttle.delayFor("E 2, 9 cents sharp", spokenReadingKey(readout(9f)), 100),
        )
    }
}
