package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShape
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
    fun `a power chord is said with its muted strings, and a 7 sharp 9 with its sharp`() {
        val f5 = first(PitchClass.F, ChordQuality.POWER_CHORD)
        assertEquals("133xxx", f5.toString())
        assertEquals(
            "F power chord, shape 1 of 4, E 2 string, fret 1, finger 1, F, A 2 string, fret 3, finger 3, C, " +
                "D 3 string, fret 3, finger 4, F, G 3 string, not played, B 3 string, not played, E 4 string, not played",
            spokenChart(f5, 0, 4, Tunings.STANDARD, Notation.SHARPS, "F power chord", english, say),
        )
        val e = spokenChart(first(PitchClass.E, ChordQuality.SEVENTH_SHARP_NINE), 0, 1, Tunings.STANDARD, Notation.SHARPS, "E seven sharp nine", english, say)
        // The third G♯ and the sharp ninth on top, which sounds as G and is said so (E G G♯ B D).
        assertTrue(e, e.contains("G 3 string, fret 1, finger 1, G sharp") && e.endsWith("E 4 string, fret 3, finger 4, G"))
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

    private val banjoWords = ChartWords(
        english.separator, english.shape, english.string, english.muted, english.open, english.fret, english.barre, english.locale,
        shortString = "short %1\$s string",
    )

    @Test
    fun `the banjo's short string is the short G 4 string, its frets counted on the neck`() {
        val openG = Instruments.BANJO_5.defaultTuning
        // The owner's pick A (docs/design/banjo-charts-preview.html, row 5): its open note and the word "short".
        assertEquals(
            "C major, shape 1 of 6, short G 4 string, open, D 3 string, fret 2, finger 2, E, G 3 string, open, " +
                "B 3 string, fret 1, finger 1, C, D 4 string, fret 2, finger 3, E",
            spokenChart(first(PitchClass.C, ChordQuality.MAJOR, openG), 0, 6, openG, Notation.SHARPS, "C major", banjoWords, say),
        )
        assertEquals(
            "D seventh, shape 1 of 6, short G 4 string, not played, D 3 string, open, G 3 string, fret 2, finger 2, A, " +
                "B 3 string, fret 1, finger 1, C, D 4 string, fret 4, finger 4, F sharp",
            spokenChart(first(PitchClass.D, ChordQuality.DOMINANT_SEVENTH, openG), 0, 6, openG, Notation.SHARPS, "D seventh", banjoWords, say),
        )
        // Up the neck under a barre: fret 7, as banjo tab counts it.
        val d = ChordShapeFinder.shapesOf(Chord.of(PitchClass.D, ChordQuality.MAJOR), openG).first { it.toString() == "77777" }
        val spoken = spokenChart(d, 0, 6, openG, Notation.SHARPS, "D major", banjoWords, say)
        assertTrue(spoken, spoken.contains("barre, fret 7, finger 1, strings G 4 to D 4, short G 4 string, fret 7, finger 1, A, D 3 string, fret 7"))
        // Double D's short string is the short F sharp 4 string.
        val doubleD = Instruments.BANJO_5.tuningById("banjo_double_d")!!
        val dd = spokenChart(first(PitchClass.D, ChordQuality.MAJOR, doubleD), 0, 1, doubleD, Notation.SHARPS, "D major", banjoWords, say)
        assertTrue(dd, dd.contains("short F sharp 4 string, open, D 3 string"))
        // Every other instrument's strings stay as they were.
        assertEquals(
            spokenChart(first(PitchClass.C, ChordQuality.MAJOR), 0, 6, Tunings.STANDARD, Notation.SHARPS, "C major", english, say),
            spokenChart(first(PitchClass.C, ChordQuality.MAJOR), 0, 6, Tunings.STANDARD, Notation.SHARPS, "C major", banjoWords, say),
        )
    }

    @Test
    fun `a banjo chart has a fret row more and the short string from its own nut at the 5th fret`() {
        val openG = Instruments.BANJO_5.defaultTuning
        val banjo = ChartGeometry.of(openG)
        assertEquals("frets 1 to 5", 5, banjo.rows)
        assertEquals("the strings run on past the last row", ChartGeometry.RUN, banjo.run)
        assertEquals(ChartGeometry(5).height + ChartGeometry.ROW + ChartGeometry.RUN, banjo.height)
        assertEquals("a guitar's chart as it was", ChordShape.CHART_ROWS to 0f, ChartGeometry.of(Tunings.STANDARD).let { it.rows to it.run })
        assertEquals(ChartGeometry(6).height, ChartGeometry.of(Tunings.STANDARD).height)
        // From the nut: the short string's own nut on the last line, its o or × in the row above it, the neck above
        // four strings wide; the long strings start at the top.
        assertEquals(5, banjo.ownNutLine(0, start = 1))
        assertEquals(banjo.rowY(4), banjo.markY(0, start = 1))
        assertEquals(ChartGeometry.MARK_Y, banjo.markY(1, start = 1))
        assertEquals(banjo.stringX(1) to banjo.stringX(4), banjo.lineEnds(0, start = 1))
        assertEquals(banjo.stringX(1) to banjo.stringX(4), banjo.lineEnds(4, start = 1))
        assertEquals("the 5th fret's line the whole width", banjo.stringX(0) to banjo.stringX(4), banjo.lineEnds(5, start = 1))
        for (string in 1 until 5) assertEquals(null, banjo.ownNutLine(string, start = 1))
        // Higher up: its nut on the 5th fret's line wherever that is, the top line from the 6th; above it the whole way.
        assertEquals(3, banjo.ownNutLine(0, start = 3))
        assertEquals(0, banjo.ownNutLine(0, start = 6))
        assertEquals("by its peg, where any o goes", ChartGeometry.MARK_Y, banjo.markY(0, start = 6))
        assertEquals(null, banjo.ownNutLine(0, start = 7))
        assertEquals(ChartGeometry.MARK_Y, banjo.markY(0, start = 7))
        assertEquals(banjo.stringX(0) to banjo.stringX(4), banjo.lineEnds(0, start = 7))
        // Its peg on the outer side: the left, or the right when mirrored (left-handed).
        assertEquals(-1f, banjo.outward(0))
        val mirrored = ChartGeometry.of(openG, mirrored = true)
        assertEquals(1f, mirrored.outward(0))
        assertEquals(mirrored.stringX(4) to mirrored.stringX(1), mirrored.lineEnds(0, start = 1))
    }
}
