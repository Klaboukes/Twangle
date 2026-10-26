package io.github.klaboukes.twangle.core.dsp.scale

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.KeySpelling
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
import io.github.klaboukes.twangle.core.dsp.music.PitchClass.G_SHARP
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.tuningOf
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.BLUES
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.DORIAN
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.HARMONIC_MINOR
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.LOCRIAN
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.LYDIAN
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MAJOR
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MAJOR_BLUES
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MAJOR_PENTATONIC
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MELODIC_MINOR
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MINOR_PENTATONIC
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.MIXOLYDIAN
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.NATURAL_MINOR
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.PHRYGIAN
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType.PHRYGIAN_DOMINANT
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
    fun `major blues is written in the major key, its blue note the minor 3rd written flat`() {
        assertEquals("C D E♭ E G A", names(C, MAJOR_BLUES))
        assertEquals("G A B♭ B D E", names(G, MAJOR_BLUES))
        assertEquals("A B C C♯ E F♯", names(A, MAJOR_BLUES))
        assertEquals("E F♯ G G♯ B C♯", names(E, MAJOR_BLUES))
        assertEquals("D E F F♯ A B", names(D, MAJOR_BLUES))
        assertEquals("B♭ C D♭ D F G", names(A_SHARP, MAJOR_BLUES))
        assertEquals("F♯ G♯ A A♯ C♯ D♯", names(F_SHARP, MAJOR_BLUES))
        assertEquals("D♭ E♭ E F A♭ B♭", names(C_SHARP, MAJOR_BLUES)) // F♭ written E.
        assertEquals("Sol La Si♭ Si Re Mi", names(G, MAJOR_BLUES, Notation.SOLFEGE))
        for (root in PitchClass.entries) {
            val scale = Scale(root, MAJOR_BLUES)
            assertEquals("$root major blues is the major pentatonic and the blue note", Scale(root, MAJOR_PENTATONIC).notes.toSet() + root.transposed(3), scale.notes.toSet())
            for (pc in Scale(root, MAJOR_PENTATONIC).notes) assertEquals("$root $pc as in the major pentatonic", Scale(root, MAJOR_PENTATONIC).accidentalOf(pc), scale.accidentalOf(pc))
            assertEquals("$root the blue note flat", Accidental.FLAT, scale.accidentalOf(root.transposed(3)))
        }
    }

    @Test
    fun `Phrygian dominant is written as the harmonic minor a fourth above, its major 3rd sharp`() {
        assertEquals("E F G♯ A B C D", names(E, PHRYGIAN_DOMINANT))
        assertEquals("A B♭ C♯ D E F G", names(A, PHRYGIAN_DOMINANT))
        assertEquals("D E♭ F♯ G A B♭ C", names(D, PHRYGIAN_DOMINANT))
        assertEquals("B C D♯ E F♯ G A", names(B, PHRYGIAN_DOMINANT))
        assertEquals("C D♭ E F G A♭ B♭", names(C, PHRYGIAN_DOMINANT))
        assertEquals("F♯ G A♯ B C♯ D E", names(F_SHARP, PHRYGIAN_DOMINANT))
        assertEquals("C♯ D F F♯ G♯ A B", names(C_SHARP, PHRYGIAN_DOMINANT)) // E♯ written F.
        assertEquals("Mi Fa Sol♯ La Si Do Re", names(E, PHRYGIAN_DOMINANT, Notation.SOLFEGE))
        assertEquals("E F G♯ A H C D", names(E, PHRYGIAN_DOMINANT, Notation.GERMAN))
        for (root in PitchClass.entries) {
            val harmonic = Scale(root.transposed(5), HARMONIC_MINOR)
            val scale = Scale(root, PHRYGIAN_DOMINANT)
            assertEquals("$root Phrygian dominant has its harmonic minor's notes", harmonic.notes.toSet(), scale.notes.toSet())
            assertEquals("$root starts on its own note", root, scale.notes.first())
            for (pc in scale.notes) assertEquals("$root Phrygian dominant $pc", harmonic.nameOf(pc, Notation.SHARPS), scale.nameOf(pc, Notation.SHARPS))
        }
    }

    @Test
    fun `each mode is written with its parent major key's signs`() {
        assertEquals("D E F G A B C", names(D, DORIAN))
        assertEquals("E F♯ G A B C♯ D", names(E, DORIAN))
        assertEquals("F♯ G♯ A B C♯ D♯ E", names(F_SHARP, DORIAN)) // E major's sharps.
        assertEquals("B♭ C D♭ E♭ F G A♭", names(A_SHARP, DORIAN)) // A♭ major's flats.
        assertEquals("E F G A B C D", names(E, PHRYGIAN))
        assertEquals("C♯ D E F♯ G♯ A B", names(C_SHARP, PHRYGIAN))
        assertEquals("C D♭ E♭ F G A♭ B♭", names(C, PHRYGIAN))
        assertEquals("F G A B C D E", names(F, LYDIAN))
        assertEquals("C D E F♯ G A B", names(C, LYDIAN))
        assertEquals("B♭ C D E F G A", names(A_SHARP, LYDIAN))
        assertEquals("D♭ E♭ F G A♭ B♭ C", names(C_SHARP, LYDIAN))
        assertEquals("G A B C D E F", names(G, MIXOLYDIAN))
        assertEquals("A B C♯ D E F♯ G", names(A, MIXOLYDIAN))
        assertEquals("B♭ C D E♭ F G A♭", names(A_SHARP, MIXOLYDIAN)) // E♭ major's flats.
        assertEquals("E♭ F G A♭ B♭ C D♭", names(D_SHARP, MIXOLYDIAN))
        assertEquals("B C D E F G A", names(B, LOCRIAN))
        assertEquals("F♯ G A B C D E", names(F_SHARP, LOCRIAN))
        assertEquals("E F G A B♭ C D", names(E, LOCRIAN))
        assertEquals("C♯ D E F♯ G A B", names(C_SHARP, LOCRIAN))
        assertEquals("D♯ E F♯ G♯ A B C♯", names(D_SHARP, LOCRIAN))
    }

    @Test
    fun `the modes of the key with six signs take their root's own key`() {
        assertEquals("G♯ A♯ B C♯ D♯ F F♯", names(G_SHARP, DORIAN)) // As G♯ minor: E♯ written F.
        assertEquals("E♭ F G♭ A♭ B♭ B D♭", names(D_SHARP, NATURAL_MINOR)) // As E♭ minor: C♭ written B.
        assertEquals("F G♭ A♭ B♭ B D♭ E♭", names(F, LOCRIAN)) // Never F F♯.
        assertEquals("B C♯ D♯ F F♯ G♯ A♯", names(B, LYDIAN))
        assertEquals("D♭ E♭ F G♭ A♭ B♭ B", names(C_SHARP, MIXOLYDIAN))
    }

    @Test
    fun `every mode has its parent major scale's notes and, but for the key with six signs, its names`() {
        val parentStep = mapOf(DORIAN to 10, PHRYGIAN to 8, LYDIAN to 7, MIXOLYDIAN to 5, NATURAL_MINOR to 3, LOCRIAN to 1, MAJOR to 0)
        assertEquals("the seven-note scales written in a major key are the modes", ScaleType.entries.filter { it.steps.size == 7 && !it.key.minor }.toSet(), parentStep.keys)
        for ((mode, step) in parentStep) for (root in PitchClass.entries) {
            val parent = Scale(root.transposed(step), MAJOR)
            val scale = Scale(root, mode)
            assertEquals("$root $mode", parent.notes.toSet(), scale.notes.toSet())
            assertEquals("$root $mode starts on its own note", root, scale.notes.first())
            if (parent.root != F_SHARP) {
                for (pc in scale.notes) assertEquals("$root $mode $pc", parent.nameOf(pc, Notation.SHARPS), scale.nameOf(pc, Notation.SHARPS))
            }
        }
    }

    @Test
    fun `major and natural minor are written as their keys, as before the modes`() {
        for (root in PitchClass.entries) {
            assertEquals("$root major", KeySpelling.ofMajorKey(root), Scale(root, MAJOR).accidentalOf(root.transposed(1)))
            assertEquals("$root minor", KeySpelling.ofMinorKey(root), Scale(root, NATURAL_MINOR).accidentalOf(root.transposed(1)))
        }
    }

    @Test
    fun `harmonic and melodic minor in the minor key's signs, their raised notes sharp`() {
        assertEquals("A B C D E F G♯", names(A, HARMONIC_MINOR))
        assertEquals("D E F G A B♭ C♯", names(D, HARMONIC_MINOR))
        assertEquals("G A B♭ C D E♭ F♯", names(G, HARMONIC_MINOR))
        assertEquals("C D E♭ F G A♭ B", names(C, HARMONIC_MINOR))
        assertEquals("E F♯ G A B C D♯", names(E, HARMONIC_MINOR))
        assertEquals("B C♯ D E F♯ G A♯", names(B, HARMONIC_MINOR))
        assertEquals("A B C D E F♯ G♯", names(A, MELODIC_MINOR))
        assertEquals("D E F G A B C♯", names(D, MELODIC_MINOR))
        assertEquals("G A B♭ C D E F♯", names(G, MELODIC_MINOR))
        assertEquals("C D E♭ F G A B", names(C, MELODIC_MINOR))
        assertEquals("E♭ F G♭ A♭ B♭ C D", names(D_SHARP, MELODIC_MINOR))
        assertEquals("B C♯ D E F♯ G♯ A♯", names(B, MELODIC_MINOR))
        assertEquals("La Si Do Re Mi Fa Sol♯", names(A, HARMONIC_MINOR, Notation.SOLFEGE))
        assertEquals("D E F G A H C♯", names(D, MELODIC_MINOR, Notation.GERMAN))
        assertEquals(Accidental.FLAT, Scale(D, HARMONIC_MINOR).accidentalOf(A_SHARP))
    }

    @Test
    fun `the colour note of each mode and of harmonic minor`() {
        assertEquals(B, Scale(D, DORIAN).colourNote)
        assertEquals(F, Scale(E, PHRYGIAN).colourNote)
        assertEquals(B, Scale(F, LYDIAN).colourNote)
        assertEquals(F_SHARP, Scale(C, LYDIAN).colourNote)
        assertEquals(F, Scale(G, MIXOLYDIAN).colourNote)
        assertEquals(F, Scale(B, LOCRIAN).colourNote)
        assertEquals(G_SHARP, Scale(A, HARMONIC_MINOR).colourNote)
        assertEquals("E Phrygian dominant's major 3rd", G_SHARP, Scale(E, PHRYGIAN_DOMINANT).colourNote)
        assertEquals(C_SHARP, Scale(A, PHRYGIAN_DOMINANT).colourNote)
        assertEquals("D Dorian is C major's notes, its 6th marked", "B", Scale(D, DORIAN).let { it.nameOf(it.colourNote!!, Notation.SHARPS) })
        for (type in listOf(MAJOR, NATURAL_MINOR, MELODIC_MINOR, MAJOR_PENTATONIC, MINOR_PENTATONIC, BLUES, MAJOR_BLUES)) {
            assertNull("$type has none", Scale(C, type).colourNote)
        }
        for (type in ScaleType.entries) for (root in PitchClass.entries) {
            val colour = Scale(root, type).colourNote ?: continue
            assertTrue("$root $type: the colour note is in the scale", colour in Scale(root, type))
            assertTrue("$root $type: never the root", colour != root)
        }
    }

    @Test
    fun `the scales in the panel's order, the most used first`() {
        assertEquals(
            listOf(
                MAJOR, NATURAL_MINOR,
                MAJOR_PENTATONIC, MINOR_PENTATONIC,
                BLUES, MAJOR_BLUES,
                DORIAN, MIXOLYDIAN,
                HARMONIC_MINOR, LYDIAN,
                PHRYGIAN, PHRYGIAN_DOMINANT,
                MELODIC_MINOR, LOCRIAN,
            ),
            ScaleType.entries,
        )
        // Saved by name, so a new order changes no saved choice: every name of before it still means its scale.
        val saved = listOf(
            "MAJOR", "DORIAN", "PHRYGIAN", "LYDIAN", "MIXOLYDIAN", "NATURAL_MINOR", "LOCRIAN",
            "HARMONIC_MINOR", "MELODIC_MINOR", "MAJOR_PENTATONIC", "MINOR_PENTATONIC", "BLUES",
        )
        assertEquals(ScaleType.entries.map { it.name }.toSet(), saved.toSet() + setOf("MAJOR_BLUES", "PHRYGIAN_DOMINANT"))
        assertEquals("seven full rows of two", 14, ScaleType.entries.size)
        assertEquals(listOf(0, 2, 3, 5, 7, 8, 10), ScaleType.valueOf("NATURAL_MINOR").steps)
        assertEquals(listOf(0, 3, 5, 6, 7, 10), ScaleType.valueOf("BLUES").steps)
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
        assertEquals(listOf(7, 7, 5, 5, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7), ScaleType.entries.map { it.steps.size })
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
