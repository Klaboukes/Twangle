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

        val solfege = spoken(Scale(PitchClass.G, ScaleType.MAJOR), notation = Notation.SOLFEGE)
        assertTrue(solfege, solfege.contains("notes Sol, La, Si, Do, Re, Mi, Fa sharp") && solfege.contains("Mi 2 string, open Mi, fret 2 Fa sharp, fret 3 Sol, root"))

        val german = spoken(Scale(PitchClass.A_SHARP, ScaleType.MAJOR), notation = Notation.GERMAN)
        assertTrue("German B is B flat: $german", german.contains("notes B, C, D, E flat, F, G, A"))

        val halfDown = Tunings.HALF_STEP_DOWN
        val e = spoken(Scale(PitchClass.D_SHARP, ScaleType.MAJOR), tuning = halfDown)
        assertTrue("strings by the tuning's flats: $e", e.contains("E flat 2 string, open E flat, root") && e.contains("A flat 2 string, open A flat"))
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

    @Test
    fun `fret numbers are said in the language's digits`() {
        val arabic = english.let { ScaleWords(it.separator, it.notes, it.string, it.open, it.fret, it.root, Locale.forLanguageTag("ar")) }
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
