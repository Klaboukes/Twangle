package io.github.klaboukes.twangle.core.dsp.scale

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.A
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.A_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.B
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.C
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.C_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.D
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.D_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.E
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.F
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.F_SHARP
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.G
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.tuningOf
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.BLUES
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MAJOR
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MAJOR_PENTATONIC
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MINOR_PENTATONIC
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.NATURAL_MINOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleTest {

    private fun names(root: PitchClass, type: ScaleType, notation: Notation = Notation.SHARPS): String {
        val scale = Scale(root, type)
        return scale.notes.joinToString(" ") { scale.nameOf(it, notation) }
    }

    @Test
    fun `the notes of each scale, in the key's spelling`() {
        assertEquals("C D E F G A B", names(C, MAJOR))
        assertEquals("G A B C D E F♯", names(G, MAJOR))
        assertEquals("F G A B♭ C D E", names(F, MAJOR))
        assertEquals("D♭ E♭ F G♭ A♭ B♭ C", names(C_SHARP, MAJOR))
        assertEquals("A B C D E F G", names(A, NATURAL_MINOR))
        assertEquals("E F♯ G A B C D", names(E, NATURAL_MINOR))
        assertEquals("D E F G A B♭ C", names(D, NATURAL_MINOR))
        assertEquals("C♯ D♯ E F♯ G♯ A B", names(C_SHARP, NATURAL_MINOR))
        assertEquals("C D E G A", names(C, MAJOR_PENTATONIC))
        assertEquals("A C D E G", names(A, MINOR_PENTATONIC))
        assertEquals("E G A B D", names(E, MINOR_PENTATONIC))
        assertEquals("B♭ D♭ E♭ F A♭", names(A_SHARP, MINOR_PENTATONIC))
    }

    @Test
    fun `the blues scale's blue note is written flat`() {
        assertEquals("A C D E♭ E G", names(A, BLUES))
        assertEquals("E G A B♭ B D", names(E, BLUES))
        assertEquals("C E♭ F G♭ G B♭", names(C, BLUES))
        assertEquals("F♯ A B C C♯ E", names(F_SHARP, BLUES))
    }

    @Test
    fun `names follow the player's notation`() {
        assertEquals("Fa Sol La Si♭ Do Re Mi", names(F, MAJOR, Notation.SOLFEGE))
        assertEquals("F G A B C D E", names(F, MAJOR, Notation.GERMAN))
        assertEquals("E F♯ G A H C D", names(E, NATURAL_MINOR, Notation.GERMAN))
        // The key wins over the notation's own sign.
        assertEquals("E F♯ G A B C D", names(E, NATURAL_MINOR, Notation.FLATS))
    }

    @Test
    fun `scale sizes and degrees`() {
        assertEquals(listOf(7, 7, 5, 5, 6), ScaleType.entries.map { it.steps.size })
        val blues = Scale(A, BLUES)
        assertEquals(1, blues.degreeOf(A))
        assertEquals(4, blues.degreeOf(D_SHARP))
        assertNull(blues.degreeOf(B))
        assertTrue(G in blues)
        assertFalse(F in blues)
        for (type in ScaleType.entries) assertEquals(type.steps.size, Integer.bitCount(type.mask))
    }

    @Test
    fun `E minor pentatonic on standard guitar, frets 0 to 12`() {
        val notes = ScaleFinder.notesOf(Scale(E, MINOR_PENTATONIC), Tunings.STANDARD)
        val lowE = notes.filter { it.string == 0 }.map { it.fret }
        assertEquals(listOf(0, 3, 5, 7, 10, 12), lowE)
        val roots = notes.filter { it.isRoot }.map { it.string to it.fret }
        assertEquals(
            listOf(0 to 0, 0 to 12, 1 to 7, 2 to 2, 3 to 9, 4 to 5, 5 to 0, 5 to 12),
            roots,
        )
        assertEquals("E2", notes.first().note.toString())
        assertEquals(36, notes.size) // 5 notes an octave, the 12th fret again as the open string: 6 a string.
    }

    @Test
    fun `every note on the neck of any tuning is in the scale, and every scale note is there`() {
        val tunings = Instruments.all.flatMap { it.tunings } + tuningOf("my_own", "C2 G2 D3 G3 B3 D4")
        for (tuning in tunings) for (type in ScaleType.entries) for (root in PitchClass.entries) {
            val scale = Scale(root, type)
            val notes = ScaleFinder.notesOf(scale, tuning)
            for (string in 0 until tuning.stringCount) {
                val expected = (0..ScaleFinder.HIGHEST_FRET).filter { PitchClass.entries[(tuning.strings[string].midi + it).mod(12)] in scale }
                assertEquals("${tuning.id} $root $type string $string", expected, notes.filter { it.string == string }.map { it.fret })
            }
            for (n in notes) {
                assertEquals(tuning.strings[n.string].midi + n.fret, n.note.midi)
                assertEquals(scale.degreeOf(n.note.pitchClass), n.degree)
                assertEquals(n.note.pitchClass == root, n.isRoot)
            }
        }
    }

    @Test
    fun `fewer frets when asked`() {
        val notes = ScaleFinder.notesOf(Scale(G, MAJOR), Instruments.UKULELE.defaultTuning, highestFret = 4)
        assertTrue(notes.all { it.fret in 0..4 })
        assertEquals(listOf(0, 2, 4), notes.filter { it.string == 0 }.map { it.fret }) // G A B on the G string.
        assertThrows(IllegalArgumentException::class.java) { ScaleFinder.notesOf(Scale(G, MAJOR), Tunings.STANDARD, -1) }
    }
}
