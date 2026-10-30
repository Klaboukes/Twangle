package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleFinder
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A scale on the neck in words (TalkBack), with the English patterns of `values/strings.xml`, and the neck's size. */
class ScaleSpeechTest {

    private val english = ScaleWords(
        separator = ", ",
        notes = "%1\$s, notes %2\$s",
        string = "%1\$s string",
        open = "open %1\$s",
        fret = "fret %1\$d %2\$s",
        root = "%1\$s, root",
        locale = Locale.ENGLISH,
        colour = "%1\$s, colour note",
        shortString = "short %1\$s string",
    )

    /** As TalkBack's speech would, so the test sees which names went through it. */
    private val say: (String) -> String = { it.replace("♭", " flat").replace("♯", " sharp") }

    private fun spoken(scale: Scale, tuning: Tuning = Tunings.STANDARD, notation: Notation = Notation.SHARPS, words: ScaleWords = english, name: String = "the scale") =
        spokenScale(scale, ScaleFinder.notesOf(scale, tuning), tuning, notation, name, words, say)

    @Test
    fun `a scale is said with its notes, then string by string from the thickest, the root marked`() {
        val aMinorPentatonic = Scale(PitchClass.A, ScaleType.MINOR_PENTATONIC)
        val spoken = spoken(aMinorPentatonic, name = "A minor pentatonic")
        assertTrue(
            spoken,
            spoken.startsWith(
                "A minor pentatonic, notes A, C, D, E, G, " +
                    "E 2 string, open E, fret 3 G, fret 5 A, root, fret 8 C, fret 10 D, fret 12 E, " +
                    "A 2 string, open A, root, fret 3 C, fret 5 D, fret 7 E, fret 10 G, fret 12 A, root, " +
                    "D 3 string, ",
            ),
        )
        assertTrue(spoken, spoken.endsWith("E 4 string, open E, fret 3 G, fret 5 A, root, fret 8 C, fret 10 D, fret 12 E"))
        assertEquals("every string", 6, Regex(" string, ").findAll(spoken).count())
        assertEquals("every place on the neck", ScaleFinder.notesOf(aMinorPentatonic, Tunings.STANDARD).size, Regex("(open|fret \\d+) ").findAll(spoken).count())
    }

    @Test
    fun `notes follow the scale's spelling and the notation, strings the tuning's own`() {
        val f = spoken(Scale(PitchClass.F, ScaleType.MAJOR))
        assertTrue("F major has a B flat, never an A sharp: $f", f.contains("notes F, G, A, B flat, C, D, E") && !f.contains("A sharp"))

        val blues = spoken(Scale(PitchClass.E, ScaleType.BLUES))
        assertTrue("the blue note is the flat fifth: $blues", blues.contains("notes E, G, A, B flat, B, D"))

        val majorBlues = spoken(Scale(PitchClass.G, ScaleType.MAJOR_BLUES))
        assertTrue("major blues' blue note is the flat 3rd: $majorBlues", majorBlues.contains("notes G, A, B flat, B, D, E") && majorBlues.contains("E 2 string, open E, fret 3 G, root, fret 5 A, fret 6 B flat, fret 7 B, fret 10 D, fret 12 E"))
        val aMajorBlues = spoken(Scale(PitchClass.A, ScaleType.MAJOR_BLUES))
        assertTrue("A major blues has a C and a C sharp: $aMajorBlues", aMajorBlues.contains("notes A, B, C, C sharp, E, F sharp"))

        val solfege = spoken(Scale(PitchClass.G, ScaleType.MAJOR), notation = Notation.SOLFEGE)
        assertTrue(solfege, solfege.contains("notes Sol, La, Si, Do, Re, Mi, Fa sharp") && solfege.contains("Mi 2 string, open Mi, fret 2 Fa sharp, fret 3 Sol, root"))

        val german = spoken(Scale(PitchClass.A_SHARP, ScaleType.MAJOR), notation = Notation.GERMAN)
        assertTrue("German B is B flat: $german", german.contains("notes B, C, D, E flat, F, G, A"))

        val halfDown = Tunings.HALF_STEP_DOWN
        val e = spoken(Scale(PitchClass.D_SHARP, ScaleType.MAJOR), tuning = halfDown)
        assertTrue("strings by the tuning's flats: $e", e.contains("E flat 2 string, open E flat, root") && e.contains("A flat 2 string, open A flat"))
    }

    @Test
    fun `the colour note's places are marked, as the neck rings them`() {
        val dorian = spoken(Scale(PitchClass.D, ScaleType.DORIAN), name = "D Dorian")
        assertTrue(dorian, dorian.startsWith("D Dorian, notes D, E, F, G, A, B, C, E 2 string, open E, fret 1 F, fret 3 G, fret 5 A, fret 7 B, colour note, fret 8 C, fret 10 D, root, fret 12 E"))
        assertEquals("every B on the neck", ScaleFinder.notesOf(Scale(PitchClass.D, ScaleType.DORIAN), Tunings.STANDARD).count { it.note.pitchClass == PitchClass.B }, Regex(", colour note").findAll(dorian).count())
        val lydian = spoken(Scale(PitchClass.A_SHARP, ScaleType.LYDIAN))
        assertTrue("B flat Lydian's sharp 4th is E: $lydian", lydian.contains("notes B flat, C, D, E, F, G, A") && lydian.contains("open E, colour note"))
        val harmonic = spoken(Scale(PitchClass.A, ScaleType.HARMONIC_MINOR))
        assertTrue("A harmonic minor's raised 7th: $harmonic", harmonic.contains("notes A, B, C, D, E, F, G sharp") && harmonic.contains("fret 4 G sharp, colour note"))
        val phrygianDominant = spoken(Scale(PitchClass.E, ScaleType.PHRYGIAN_DOMINANT), name = "E Phrygian dominant")
        assertTrue(
            "E Phrygian dominant's major 3rd, G sharp, ringed: $phrygianDominant",
            phrygianDominant.startsWith("E Phrygian dominant, notes E, F, G sharp, A, B, C, D, E 2 string, open E, root, fret 1 F, fret 4 G sharp, colour note, fret 5 A"),
        )
        assertEquals("every G sharp on the neck", ScaleFinder.notesOf(Scale(PitchClass.E, ScaleType.PHRYGIAN_DOMINANT), Tunings.STANDARD).count { it.note.pitchClass == PitchClass.G_SHARP }, Regex(", colour note").findAll(phrygianDominant).count())
        val a = spoken(Scale(PitchClass.A, ScaleType.PHRYGIAN_DOMINANT))
        assertTrue("A Phrygian dominant in D harmonic minor's signs: $a", a.contains("notes A, B flat, C sharp, D, E, F, G") && a.contains("fret 4 C sharp, colour note"))
        for (type in listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.MELODIC_MINOR, ScaleType.MINOR_PENTATONIC, ScaleType.BLUES, ScaleType.MAJOR_BLUES)) {
            assertTrue("$type has no colour note", !spoken(Scale(PitchClass.A, type)).contains("colour note"))
        }
    }

    @Test
    fun `every string of the instrument is said, on a ukulele, a 5-string bass and a tuning of one's own`() {
        val uke = Instruments.UKULELE.defaultTuning
        assertEquals(uke.stringCount, Regex(" string, ").findAll(spoken(Scale(PitchClass.C, ScaleType.MAJOR), uke)).count())
        val bass = Instruments.BASS_5.defaultTuning
        assertEquals(bass.stringCount, Regex(" string, ").findAll(spoken(Scale(PitchClass.C, ScaleType.MAJOR), bass)).count())
        // Open C, low to high C G C G C E.
        val openC = Tuning("custom_open_c", listOf(36, 43, 48, 55, 60, 64).map { Note(it) })
        val c = spoken(Scale(PitchClass.C, ScaleType.MAJOR_PENTATONIC), openC)
        assertTrue(c, c.contains("C 2 string, open C, root, fret 2 D") && c.contains("G 2 string, open G, fret 2 A"))
    }

    /**
     * The banjo (Phase 59): the short fifth string is the "short G 4 string" (the owner's pick A), said first as the neck
     * draws it, its open note and then its frets counted on the neck ("fret 7 A", the note the 1st string has there),
     * none below its start; the long strings as on any instrument. Double C and double D too ("short F sharp 4 string").
     */
    @Test
    fun `the banjo's short string is said as the short G 4 string, its frets counted on the neck`() {
        val banjo = Instruments.BANJO_5.defaultTuning
        val g = spoken(Scale(PitchClass.G, ScaleType.MAJOR), banjo, name = "G major")
        assertTrue(
            g,
            g.startsWith(
                "G major, notes G, A, B, C, D, E, F sharp, " +
                    "short G 4 string, open G, root, fret 7 A, fret 9 B, fret 10 C, fret 12 D, " +
                    "D 3 string, open D, fret 2 E, fret 4 F sharp, fret 5 G, root, ",
            ),
        )
        assertEquals("one short string, four long ones", 1, Regex("short [^,]+ string, ").findAll(g).count())
        assertEquals("every string", 5, Regex(" string, ").findAll(g).count())
        val doubleC = Instruments.BANJO_5.tunings.single { it.id == "banjo_double_c" }
        val c = spoken(Scale(PitchClass.C, ScaleType.MAJOR), doubleC, name = "C major")
        assertTrue(c, c.contains("short G 4 string, open G, fret 7 A, fret 9 B, fret 10 C, root, fret 12 D, C 3 string, open C, root"))
        val doubleD = Instruments.BANJO_5.tunings.single { it.id == "banjo_double_d" }
        val d = spoken(Scale(PitchClass.D, ScaleType.MAJOR), doubleD, name = "D major")
        assertTrue(d, d.contains("short F sharp 4 string, open F sharp, fret 6 G, fret 8 A, fret 10 B, fret 12 C sharp, D 3 string, open D, root"))
        // On any other instrument no string is short.
        assertTrue(!spoken(Scale(PitchClass.G, ScaleType.MAJOR)).contains("short"))
    }

    @Test
    fun `fret numbers are said in the language's digits`() {
        val arabic = english.let { ScaleWords(it.separator, it.notes, it.string, it.open, it.fret, it.root, Locale.forLanguageTag("ar"), it.colour) }
        val spoken = spoken(Scale(PitchClass.A, ScaleType.MINOR_PENTATONIC), words = arabic)
        assertTrue(spoken, spoken.contains("fret ٣ G") && spoken.contains("fret ١٢ E"))
    }

    @Test
    fun `the neck lies sideways on a phone, upright only where that is clearly bigger`() {
        val six = NeckGeometry(6)
        assertEquals("the mock-up's width", 282f, six.along)
        assertEquals("the Xiaomi's column, upright", NeckLie.SIDEWAYS, six.lieFor(361f, 300f))
        assertTrue("1.25 dp a unit or more, the mock-up's size", six.unitFor(NeckLie.SIDEWAYS, 361f, 300f) >= 1.25f)
        assertEquals("on its side, the end half", NeckLie.SIDEWAYS, six.lieFor(318f, 226f))
        assertEquals("a tablet upright", NeckLie.UPRIGHT, six.lieFor(500f, 900f))
        assertEquals("never past the largest unit", MAX_UNIT, six.unitFor(NeckLie.SIDEWAYS, 5000f, 5000f))
        val eight = NeckGeometry(8)
        assertTrue("more strings, a wider neck", eight.across > six.across)
    }

    /**
     * The banjo's neck (Phase 59, the owner's pick B): its short string (string 0) is drawn in full from the 5th fret and
     * its open note's dot sits just before it, in the 5th fret's space, as the other strings' sit before the nut; a fretted
     * note in its fret on every string. As big as any 5-string neck, either hand.
     */
    @Test
    fun `the banjo's short string starts at the 5th fret, its open note just before it`() {
        val banjo = Instruments.BANJO_5.defaultTuning
        for (mirrored in listOf(false, true)) {
            val g = NeckGeometry.of(banjo, mirrored = mirrored)
            assertEquals(5, g.strings)
            assertEquals("short string", 5, g.startOf(0))
            for (string in 1 until 5) assertEquals("long string $string", 0, g.startOf(string))
            assertEquals("the open g in the 5th fret's space", g.fretMiddle(5), g.noteMiddle(0, 0))
            assertTrue("before its start", g.noteMiddle(0, 0) < g.fretLine(5) && g.noteMiddle(0, 0) > g.fretLine(4))
            assertEquals("the long strings' open notes before the nut", g.fretMiddle(0), g.noteMiddle(1, 0))
            for (fret in 6..12) assertEquals("fret $fret", g.fretMiddle(fret), g.noteMiddle(0, fret))
            assertEquals("the size of any 5-string neck", NeckGeometry(5).along to NeckGeometry(5).across, g.along to g.across)
        }
        val guitar = NeckGeometry.of(Tunings.STANDARD)
        for (string in 0 until 6) assertEquals(0, guitar.startOf(string))
    }

    @Test
    fun `the neck's places as it lies`() {
        val g = NeckGeometry(6)
        // Sideways: the thickest string at the bottom, the open notes left of the nut.
        val lowOpen = g.point(NeckLie.SIDEWAYS, g.fretMiddle(0), g.stringLine(0))
        val highTwelfth = g.point(NeckLie.SIDEWAYS, g.fretMiddle(12), g.stringLine(5))
        assertTrue("thickest string below the thinnest", lowOpen.y > highTwelfth.y)
        assertTrue("open notes left of the 12th fret", lowOpen.x < highTwelfth.x)
        assertTrue("open notes before the nut", g.fretMiddle(0) < g.fretLine(0))
        assertTrue("inside the neck", highTwelfth.x <= g.along && highTwelfth.y >= 0f && lowOpen.y <= g.across)
        // Upright: the thickest string on the left, the nut at the top.
        val up = g.point(NeckLie.UPRIGHT, g.fretMiddle(0), g.stringLine(0))
        val down = g.point(NeckLie.UPRIGHT, g.fretMiddle(12), g.stringLine(5))
        assertTrue(up.x < down.x && up.y < down.y)
    }
}
