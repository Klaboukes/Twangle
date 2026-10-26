package io.github.klaboukes.twangle.core.dsp.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LoweredTuningTest {

    private fun names(tuning: Tuning, notation: Notation = Notation.SHARPS): String =
        tuning.strings.joinToString(" ") { notation.nameIn(tuning, it.pitchClass) }

    @Test
    fun `every string goes down by the same number of semitones and the id stays`() {
        for (instrument in Instruments.all) {
            for (tuning in instrument.tunings) {
                for (semitones in 1..tuning.lowerableBy) {
                    val lowered = tuning.lowered(semitones)
                    assertEquals(tuning.id, lowered.id)
                    assertEquals(semitones, lowered.loweredBy)
                    assertEquals(tuning.strings.map { it.midi - semitones }, lowered.strings.map { it.midi })
                }
            }
        }
    }

    @Test
    fun `not lowered is the tuning itself, with no lowered name`() {
        assertSame(Tunings.DROP_D, Tunings.DROP_D.lowered(0))
        assertNull(Tunings.DROP_D.loweredName)
    }

    @Test
    fun `lowering adds up, at most two and a half steps below the tuning as written`() {
        val twice = Tunings.OPEN_G.lowered(2).lowered(3)
        assertEquals(5, twice.loweredBy)
        assertEquals(Tunings.OPEN_G.lowered(5), twice)
        assertEquals(0, twice.lowerableBy)
        assertEquals(5, Tunings.STANDARD.lowerableBy)
        assertEquals(3, Tunings.STANDARD.lowered(2).lowerableBy)
        assertThrows(IllegalArgumentException::class.java) { Tunings.STANDARD.lowered(6) }
        assertThrows(IllegalArgumentException::class.java) { Tunings.STANDARD.lowered(-1) }
    }

    @Test
    fun `no string goes below B0, so the five-string bass can't be lowered`() {
        assertEquals(Note(23), Tuning.LOWEST_STRING)
        assertEquals(0, Instruments.BASS_5.defaultTuning.lowerableBy)
        assertThrows(IllegalArgumentException::class.java) { Instruments.BASS_5.defaultTuning.lowered(1) }
        // The four-string bass goes all the way, to B0.
        assertEquals(5, Instruments.BASS_4.defaultTuning.lowerableBy)
        assertEquals("B0", Instruments.BASS_4.defaultTuning.lowered(5).lowestNote.toString())
        // A tuning of the player's own two semitones above B0 goes two down.
        assertEquals(2, Tuning("custom_low", listOf(Note(25), Note(30), Note(35), Note(40))).lowerableBy)
        for (instrument in Instruments.all) {
            for (tuning in instrument.tunings) {
                val lowest = tuning.lowered(tuning.lowerableBy).lowestNote
                assertTrue("${tuning.id}: $lowest", lowest.midi >= Tuning.LOWEST_STRING.midi)
            }
        }
    }

    @Test
    fun `standard lowered gets the names players use`() {
        assertEquals(LoweredName.Preset(Tunings.HALF_STEP_DOWN), Tunings.STANDARD.lowered(1).loweredName)
        assertEquals(LoweredName.Preset(Tunings.FULL_STEP_DOWN), Tunings.STANDARD.lowered(2).loweredName)
        assertEquals(LoweredName.Common(CommonTuning.C_SHARP_STANDARD), Tunings.STANDARD.lowered(3).loweredName)
        assertEquals(LoweredName.Common(CommonTuning.C_STANDARD), Tunings.STANDARD.lowered(4).loweredName)
        assertEquals(LoweredName.Common(CommonTuning.B_STANDARD), Tunings.STANDARD.lowered(5).loweredName)
        // The lowered standards lowered further.
        assertEquals(LoweredName.Common(CommonTuning.C_STANDARD), Tunings.HALF_STEP_DOWN.lowered(3).loweredName)
        assertEquals(LoweredName.Common(CommonTuning.B_FLAT_STANDARD), Tunings.HALF_STEP_DOWN.lowered(5).loweredName)
        assertEquals(LoweredName.Common(CommonTuning.A_STANDARD), Tunings.FULL_STEP_DOWN.lowered(5).loweredName)
    }

    @Test
    fun `drop D lowered gets the names players use`() {
        val expected = listOf(
            CommonTuning.DROP_C_SHARP, CommonTuning.DROP_C, CommonTuning.DROP_B, CommonTuning.DROP_B_FLAT, CommonTuning.DROP_A,
        )
        expected.forEachIndexed { i, name ->
            assertEquals(LoweredName.Common(name), Tunings.DROP_D.lowered(i + 1).loweredName)
        }
    }

    @Test
    fun `a lowered tuning that is another preset takes its name`() {
        assertEquals(LoweredName.Preset(Tunings.OPEN_D), Tunings.OPEN_E.lowered(2).loweredName)
        assertEquals(LoweredName.Preset(Tunings.FULL_STEP_DOWN), Tunings.HALF_STEP_DOWN.lowered(1).loweredName)
    }

    @Test
    fun `any other lowered tuning says how far down it is`() {
        assertEquals(LoweredName.Shifted(1), Tunings.OPEN_G.lowered(1).loweredName)
        assertEquals(LoweredName.Shifted(5), Tunings.DADGAD.lowered(5).loweredName)
        assertEquals(LoweredName.Shifted(3), Tunings.OPEN_C.lowered(3).loweredName)
        assertEquals(LoweredName.Shifted(2), Instruments.BASS_4.defaultTuning.lowered(2).loweredName)
        assertEquals(LoweredName.Shifted(1), Instruments.UKULELE.defaultTuning.lowered(1).loweredName)
    }

    @Test
    fun `the player's own tuning lowered into a guitar shape takes its name, other instruments' don't`() {
        val mine = Tuning("custom_1", Tunings.DROP_D.strings)
        assertEquals(LoweredName.Common(CommonTuning.DROP_C), mine.lowered(2).loweredName)
        assertEquals(LoweredName.Preset(Tunings.FULL_STEP_DOWN), Tuning("custom_2", Tunings.HALF_STEP_DOWN.strings).lowered(1).loweredName)
        // Four strings: a ukulele's, a bass's or a violin's, so no preset's name.
        val violinUp = Tuning("custom_3", Instruments.VIOLIN.defaultTuning.strings.map { Note(it.midi + 1) })
        assertEquals(LoweredName.Shifted(1), violinUp.lowered(1).loweredName)
    }

    @Test
    fun `a lowered tuning is spelled as its name is written`() {
        assertEquals("E♭ A♭ D♭ G♭ B♭ E♭", names(Tunings.STANDARD.lowered(1)))
        assertEquals("C♯ F♯ B E G♯ C♯", names(Tunings.STANDARD.lowered(3), Notation.FLATS))
        assertEquals("C F B♭ E♭ G C", names(Tunings.STANDARD.lowered(4)))
        assertEquals("B E A D F♯ B", names(Tunings.STANDARD.lowered(5), Notation.FLATS))
        assertEquals("C♯ G♯ C♯ F♯ A♯ D♯", names(Tunings.DROP_D.lowered(1), Notation.FLATS))
        assertEquals("B F♯ B E G♯ C♯", names(Tunings.DROP_D.lowered(3), Notation.FLATS))
        assertEquals("B♭ F B♭ E♭ G C", names(Tunings.DROP_D.lowered(4)))
        // Open E lowered a step is Open D, with its F♯.
        assertEquals("D A D F♯ A D", names(Tunings.OPEN_E.lowered(2), Notation.FLATS))
    }

    @Test
    fun `a lowered tuning without a name is in flats from a black-key lowest string, else in sharps`() {
        assertEquals("D♭ G♭ D♭ G♭ B♭ D♭", names(Tunings.OPEN_G.lowered(1)))
        assertEquals("B E B E G♯ B", names(Tunings.OPEN_G.lowered(3), Notation.FLATS))
        assertEquals("C G C F G C", names(Tunings.DADGAD.lowered(2)))
        assertNull(Tunings.DADGAD.lowered(2).spelling)
        assertEquals(Accidental.FLAT, Instruments.BASS_4.defaultTuning.lowered(1).spelling)
    }

    @Test
    fun `common tunings are six-string, distinct and none of them a preset`() {
        val presets = Tunings.all.map { it.strings }.toSet()
        for (common in CommonTuning.entries) {
            assertEquals(common.name, 6, common.tuning.stringCount)
            assertTrue(common.name, common.tuning.strings !in presets)
        }
        assertEquals(CommonTuning.entries.size, CommonTuning.entries.map { it.tuning.strings }.toSet().size)
    }
}
