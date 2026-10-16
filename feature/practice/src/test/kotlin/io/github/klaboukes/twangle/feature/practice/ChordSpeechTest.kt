package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapeFinder
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A chord chart in words (TalkBack), with the English patterns of `values/strings.xml`. */
class ChordSpeechTest {

    private val english = ChartWords(
        separator = ", ",
        shape = "%1\$s, shape %2\$d of %3\$d",
        string = "%1\$s string",
        muted = "%1\$s, not played",
        open = "%1\$s, open",
        fret = "%1\$s, fret %2\$d, finger %3\$d, %4\$s",
        barre = "barre, fret %2\$d, finger %1\$d, strings %3\$s to %4\$s",
        locale = Locale.ENGLISH,
    )

    /** As TalkBack's speech would, so the test sees which names went through it. */
    private val say: (String) -> String = { it.replace("♭", " flat").replace("♯", " sharp") }

    private fun first(root: PitchClass, quality: ChordQuality, tuning: Tuning = Tunings.STANDARD) =
        ChordShapeFinder.shapesOf(Chord.of(root, quality), tuning).first()

    @Test
    fun `an open chord is said string by string from the thickest`() {
        val c = first(PitchClass.C, ChordQuality.MAJOR)
        assertEquals("x32010", c.toString())
        assertEquals(
            "C major, shape 1 of 6, E 2 string, not played, A 2 string, fret 3, finger 3, C, D 3 string, fret 2, finger 2, E, " +
                "G 3 string, open, B 3 string, fret 1, finger 1, C, E 4 string, open",
            spokenChart(c, 0, 6, Tunings.STANDARD, Notation.SHARPS, "C major", english, say),
        )
    }

    @Test
    fun `a barre is said once before the strings`() {
        val f = first(PitchClass.F, ChordQuality.MAJOR)
        assertEquals("133211", f.toString())
        val spoken = spokenChart(f, 0, 3, Tunings.STANDARD, Notation.SHARPS, "F major", english, say)
        assertTrue(spoken, spoken.startsWith("F major, shape 1 of 3, barre, fret 1, finger 1, strings E 2 to E 4, E 2 string, fret 1, finger 1, F"))
    }

    @Test
    fun `notes follow the chord's spelling and the notation, strings the tuning's own`() {
        val bFlat = first(PitchClass.A_SHARP, ChordQuality.MAJOR)
        val spoken = spokenChart(bFlat, 0, 1, Tunings.STANDARD, Notation.SHARPS, "B♭ major", english, say)
        assertTrue("B♭, never A♯: $spoken", spoken.contains("B flat") && !spoken.contains("A sharp"))

        val solfege = spokenChart(first(PitchClass.C, ChordQuality.MAJOR), 0, 1, Tunings.STANDARD, Notation.SOLFEGE, "Do major", english, say)
        assertTrue(solfege, solfege.contains("La 2 string, fret 3, finger 3, Do"))

        val halfDown = Tunings.HALF_STEP_DOWN
        val e = spokenChart(first(PitchClass.D_SHARP, ChordQuality.MAJOR, halfDown), 0, 1, halfDown, Notation.SHARPS, "E♭ major", english, say)
        assertTrue("strings by the tuning's flats: $e", e.contains("E flat 2 string") && e.contains("A flat 2 string"))
    }

    @Test
    fun `every string of the instrument is said, on a ukulele and in DADGAD`() {
        val uke = Instruments.UKULELE.defaultTuning
        val c = first(PitchClass.C, ChordQuality.MAJOR, uke)
        val spoken = spokenChart(c, 0, 1, uke, Notation.SHARPS, "C major", english, say)
        assertEquals(uke.stringCount, Regex(" string, ").findAll(spoken).count())

        val d = spokenChart(first(PitchClass.D, ChordQuality.MAJOR, Tunings.DADGAD), 0, 1, Tunings.DADGAD, Notation.SHARPS, "D major", english, say)
        assertTrue(d, d.contains("D 2 string") && d.contains("A 3 string"))
    }

    @Test
    fun `numbers are written in the language's digits`() {
        val arabic = ChartWords(", ", "%1\$s %2\$d/%3\$d", "%1\$s", "%1\$s x", "%1\$s o", "%1\$s %2\$d %3\$d %4\$s", "%1\$d %2\$d %3\$s %4\$s", Locale.forLanguageTag("ar"))
        val spoken = spokenChart(first(PitchClass.C, ChordQuality.MAJOR), 0, 6, Tunings.STANDARD, Notation.SHARPS, "C", arabic, say)
        assertTrue(spoken, spoken.startsWith("C ١/٦"))
        assertTrue("octaves stay 0-9, as on screen: $spoken", spoken.contains("A 2 ٣ ٣ C"))
    }

    @Test
    fun `the chart's size follows its strings and the room`() {
        val six = ChartGeometry(6)
        assertEquals(2 * ChartGeometry.SIDE + 5 * ChartGeometry.STRING_GAP, six.width)
        assertEquals(1.5f, six.unitFor(six.width * 1.5f, six.height * 3f), 1e-4f)
        assertEquals("at most the largest unit", MAX_UNIT, six.unitFor(10_000f, 10_000f), 1e-4f)
        assertTrue("a ukulele's chart is narrower", ChartGeometry(4).width < six.width)
    }
}
