package io.github.klaboukes.twangle.core.dsp.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class TuningTest {

    private val mapper = NoteMapper()

    @Test
    fun `presets have the expected notes from low to high`() {
        val expected = mapOf(
            Tunings.STANDARD to "E2 A2 D3 G3 B3 E4",
            Tunings.DROP_D to "D2 A2 D3 G3 B3 E4",
            Tunings.HALF_STEP_DOWN to "D#2 G#2 C#3 F#3 A#3 D#4",
            Tunings.FULL_STEP_DOWN to "D2 G2 C3 F3 A3 D4",
            Tunings.DADGAD to "D2 A2 D3 G3 A3 D4",
            Tunings.OPEN_G to "D2 G2 D3 G3 B3 D4",
            Tunings.OPEN_D to "D2 A2 D3 F#3 A3 D4",
            Tunings.OPEN_E to "E2 B2 E3 G#3 B3 E4",
        )
        for ((tuning, notes) in expected) {
            assertEquals(tuning.id, notes, tuning.strings.joinToString(" "))
            assertEquals(tuning.id, 6, tuning.stringCount)
        }
    }

    @Test
    fun `standard tuning strings match reference frequencies`() {
        val hz = listOf(82.4069f, 110.0000f, 146.8324f, 195.9977f, 246.9417f, 329.6276f)
        Tunings.STANDARD.strings.forEachIndexed { i, note ->
            assertEquals("string index $i", hz[i], mapper.frequencyOf(note), 1e-3f)
        }
    }

    @Test
    fun `preset list holds all eight with unique ids and standard as default`() {
        assertEquals(8, Tunings.all.size)
        assertEquals(8, Tunings.all.map { it.id }.toSet().size)
        assertSame(Tunings.STANDARD, Tunings.DEFAULT)
        assertSame(Tunings.STANDARD, Tunings.all.first())
    }

    @Test
    fun `byId finds presets and returns null for unknown ids`() {
        for (tuning in Tunings.all) assertSame(tuning, Tunings.byId(tuning.id))
        assertNull(Tunings.byId("not_a_tuning"))
    }

    @Test
    fun `each in-tune string is its own nearest string with zero cents`() {
        for (tuning in Tunings.all) {
            tuning.strings.forEachIndexed { i, note ->
                val midi = mapper.midiOf(mapper.frequencyOf(note))
                assertEquals("${tuning.id} string $i", i, tuning.nearestStringIndex(midi))
                assertEquals("${tuning.id} string $i", 0f, tuning.centsFromString(midi, i), CENT_TOLERANCE)
            }
        }
    }

    @Test
    fun `detuned strings still map to the right string with signed cents`() {
        val tuning = Tunings.STANDARD
        for (offset in listOf(-180f, -80f, -30f, 30f, 80f, 180f)) {
            tuning.strings.forEachIndexed { i, note ->
                val midi = mapper.midiOf(detune(mapper.frequencyOf(note), offset))
                assertEquals("string $i at $offset cents", i, tuning.nearestStringIndex(midi))
                assertEquals("string $i at $offset cents", offset, tuning.centsFromString(midi, i), CENT_TOLERANCE)
            }
        }
    }

    @Test
    fun `nearest string is judged in cents, not Hz`() {
        // 128 Hz is closer to A2 (110 Hz) in Hz (18 vs 18.8) but closer to D3 (146.8 Hz) in cents
        // (262 vs 238). Measured in cents, D3 wins.
        val midi = mapper.midiOf(128f)
        assertEquals(2, Tunings.STANDARD.nearestStringIndex(midi))
    }

    @Test
    fun `exact midpoint between two strings picks the lower string`() {
        // A2 = MIDI 45 and D3 = MIDI 50, so 47.5 is exactly half-way.
        assertEquals(1, Tunings.STANDARD.nearestStringIndex(47.5f))
    }

    @Test
    fun `notes outside the tuning snap to the outermost strings`() {
        assertEquals(0, Tunings.STANDARD.nearestStringIndex(mapper.midiOf(60f)))
        assertEquals(5, Tunings.STANDARD.nearestStringIndex(mapper.midiOf(1500f)))
    }

    @Test
    fun `string lookup follows A4 calibration`() {
        val calibrated = NoteMapper(a4Hz = 432f)
        val e2At432 = calibrated.frequencyOf(Tunings.STANDARD.strings[0])
        val midi = calibrated.midiOf(e2At432)
        assertEquals(0, Tunings.STANDARD.nearestStringIndex(midi))
        assertEquals(0f, Tunings.STANDARD.centsFromString(midi, 0), CENT_TOLERANCE)
        // The same frequency read against A4 = 440 Hz is about 32 cents flat.
        assertEquals(-31.77f, Tunings.STANDARD.centsFromString(mapper.midiOf(e2At432), 0), 0.05f)
    }

    @Test
    fun `invalid tunings are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Tuning("empty", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Tuning(" ", listOf(Note(40))) }
    }

    @Test
    fun `a custom single-string tuning always returns that string`() {
        val single = Tuning("single", listOf(Note(52)))
        for (midi in listOf(30f, 52f, 90f)) assertEquals(0, single.nearestStringIndex(midi))
        assertTrue(single.centsFromString(60f, 0) > 0f)
    }

    private fun detune(hz: Float, cents: Float): Float = (hz * 2.0.pow(cents / 1200.0)).toFloat()

    private companion object {
        const val CENT_TOLERANCE = 0.01f
    }
}
