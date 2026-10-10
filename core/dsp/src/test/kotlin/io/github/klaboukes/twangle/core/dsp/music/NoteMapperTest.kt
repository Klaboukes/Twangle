package io.github.klaboukes.twangle.core.dsp.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.pow

class NoteMapperTest {

    private val mapper = NoteMapper()

    @Test
    fun `A4 at 440 Hz is MIDI 69 with zero cents`() {
        val reading = mapper.map(440f)
        assertEquals(Note(69), reading.note)
        assertEquals(PitchClass.A, reading.note.pitchClass)
        assertEquals(4, reading.note.octave)
        assertEquals(0f, reading.cents, CENT_TOLERANCE)
        assertEquals(440f, reading.frequencyHz)
    }

    @Test
    fun `standard guitar strings map to their notes`() {
        val strings = listOf(
            82.4069f to "E2",
            110.0000f to "A2",
            146.8324f to "D3",
            195.9977f to "G3",
            246.9417f to "B3",
            329.6276f to "E4",
        )
        for ((hz, name) in strings) {
            val reading = mapper.map(hz)
            assertEquals("note for $hz Hz", name, reading.note.toString())
            assertEquals("cents for $hz Hz", 0f, reading.cents, CENT_TOLERANCE)
        }
    }

    @Test
    fun `frequencyOf returns reference equal-tempered pitches`() {
        assertEquals(82.4069f, mapper.frequencyOf(Note.of(PitchClass.E, 2)), 1e-3f)
        assertEquals(261.6256f, mapper.frequencyOf(Note.of(PitchClass.C, 4)), 1e-3f)
        assertEquals(440f, mapper.frequencyOf(Note.of(PitchClass.A, 4)), 1e-3f)
        assertEquals(1318.5102f, mapper.frequencyOf(Note.of(PitchClass.E, 6)), 1e-2f)
    }

    @Test
    fun `every note from 60 to 1500 Hz round-trips with zero cents`() {
        // MIDI 35 (B1, 61.7 Hz) .. 90 (F#6, 1480 Hz) covers the guitar detection range.
        for (midi in 35..90) {
            val hz = mapper.frequencyOf(Note(midi))
            val reading = mapper.map(hz)
            assertEquals("note for MIDI $midi", Note(midi), reading.note)
            assertEquals("cents for MIDI $midi", 0f, reading.cents, CENT_TOLERANCE)
            assertEquals("midiOf for MIDI $midi", midi.toFloat(), mapper.midiOf(hz), 1e-4f)
        }
    }

    @Test
    fun `small deviations are reported in cents with the right sign`() {
        for (offset in listOf(-49f, -25f, -3f, -0.5f, 0.5f, 3f, 25f, 49f)) {
            val hz = detune(110f, offset)
            val reading = mapper.map(hz)
            assertEquals("note for $offset cents", "A2", reading.note.toString())
            assertEquals("cents for $offset cents", offset, reading.cents, CENT_TOLERANCE)
            assertEquals("centsOff for $offset cents", offset, mapper.centsOff(hz), CENT_TOLERANCE)
        }
    }

    @Test
    fun `cents stay within minus 50 to plus 50 around the half-way point`() {
        val justBelowHalf = mapper.map(detune(440f, 49.9f))
        assertEquals(Note(69), justBelowHalf.note)
        assertEquals(49.9f, justBelowHalf.cents, CENT_TOLERANCE)

        val justAboveHalf = mapper.map(detune(440f, 50.1f))
        assertEquals(Note(70), justAboveHalf.note)
        assertEquals(-49.9f, justAboveHalf.cents, CENT_TOLERANCE)
    }

    @Test
    fun `nearestNote agrees with map`() {
        assertEquals(Note.of(PitchClass.E, 2), mapper.nearestNote(detune(82.4069f, -20f)))
        assertEquals(Note.of(PitchClass.F, 2), mapper.nearestNote(detune(82.4069f, 60f)))
    }

    @Test
    fun `octave boundary is between B and C`() {
        assertEquals("B3", Note(59).toString())
        assertEquals("C4", Note(60).toString())
        assertEquals(60, Note.of(PitchClass.C, 4).midi)
        assertEquals("C-1", Note(0).toString())
        assertEquals(Note(0), Note.of(PitchClass.C, -1))
    }

    @Test
    fun `sharps use the sharp symbol`() {
        assertEquals("F#2", Note.of(PitchClass.F_SHARP, 2).toString())
        assertEquals("F#2", mapper.map(92.4986f).note.toString())
    }

    @Test
    fun `custom A4 shifts every note`() {
        val mapper432 = NoteMapper(a4Hz = 432f)
        assertEquals(0f, mapper432.map(432f).cents, CENT_TOLERANCE)
        assertEquals(Note(69), mapper432.map(432f).note)

        // 440 Hz is 1200·log2(440/432) = 31.77 cents sharp of A4 = 432 Hz.
        val reading = mapper432.map(440f)
        assertEquals(Note(69), reading.note)
        assertEquals(31.766f, reading.cents, CENT_TOLERANCE)

        // Low E follows the reference: 82.4069 · 432/440.
        assertEquals(80.9086f, mapper432.frequencyOf(Note.of(PitchClass.E, 2)), 1e-3f)
    }

    @Test
    fun `A4 limits are inclusive`() {
        assertEquals(415f, NoteMapper(415f).a4Hz)
        assertEquals(466f, NoteMapper(466f).a4Hz)
    }

    @Test
    fun `A4 outside 415 to 466 Hz is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NoteMapper(414.9f) }
        assertThrows(IllegalArgumentException::class.java) { NoteMapper(466.1f) }
    }

    @Test
    fun `invalid frequencies are rejected`() {
        for (hz in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { mapper.map(hz) }
            assertThrows(IllegalArgumentException::class.java) { NoteMapper.centsBetween(hz, 440f) }
        }
    }

    @Test
    fun `centsBetween measures intervals`() {
        assertEquals(1200f, NoteMapper.centsBetween(880f, 440f), CENT_TOLERANCE)
        assertEquals(-1200f, NoteMapper.centsBetween(220f, 440f), CENT_TOLERANCE)
        assertEquals(700f, NoteMapper.centsBetween(mapper.frequencyOf(Note(76)), 440f), CENT_TOLERANCE)
        assertEquals(0f, NoteMapper.centsBetween(440f, 440f), CENT_TOLERANCE)
    }

    private fun detune(hz: Float, cents: Float): Float = (hz * 2.0.pow(cents / 1200.0)).toFloat()

    private companion object {
        /** Well inside the project's ±1 cent accuracy target. */
        const val CENT_TOLERANCE = 0.01f
    }
}
