package io.github.klaboukes.twangle.core.designsystem

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNameSpeechTest {

    private val english = NoteNameSpeech("%1\$s sharp", "%1\$s flat", dutchLetters = false)
    private val dutch = NoteNameSpeech("%1\$s kruis", "%1\$s mol", dutchLetters = true)

    @Test
    fun `english says every letter name the app can show in words`() {
        val expected = mapOf(
            "C" to "C", "C♯" to "C sharp", "D♭" to "D flat", "D" to "D", "D♯" to "D sharp", "E♭" to "E flat",
            "E" to "E", "F" to "F", "F♯" to "F sharp", "G♭" to "G flat", "G" to "G", "G♯" to "G sharp",
            "A♭" to "A flat", "A" to "A", "A♯" to "A sharp", "B♭" to "B flat", "B" to "B",
        )
        assertEquals(expected.keys, letterNamesShown())
        expected.forEach { (name, spoken) -> assertEquals(name, spoken, english.name(name)) }
    }

    @Test
    fun `dutch says every letter name the app can show the way Dutch musicians do`() {
        val expected = mapOf(
            "C" to "c", "C♯" to "cis", "D♭" to "des", "D" to "d", "D♯" to "dis", "E♭" to "es",
            "E" to "e", "F" to "f", "F♯" to "fis", "G♭" to "ges", "G" to "g", "G♯" to "gis",
            "A♭" to "as", "A" to "a", "A♯" to "ais", "B♭" to "bes", "B" to "b",
        )
        assertEquals(expected.keys, letterNamesShown())
        expected.forEach { (name, spoken) -> assertEquals(name, spoken, dutch.name(name)) }
    }

    @Test
    fun `dutch also knows the spellings the app doesn't show today`() {
        assertEquals("eis", dutch.name("E♯"))
        assertEquals("bis", dutch.name("B♯"))
        assertEquals("ces", dutch.name("C♭"))
        assertEquals("fes", dutch.name("F♭"))
    }

    @Test
    fun `solfege keeps the sign as a word in both languages`() {
        val names = Notation.SOLFEGE.let { n ->
            PitchClass.entries.flatMap { listOf(n.nameOf(it, Accidental.SHARP), n.nameOf(it, Accidental.FLAT)) }
        }.toSet()
        names.forEach { name ->
            val base = name.dropLast(1)
            val (en, nl) = when (name.last()) {
                '♯' -> "$base sharp" to "$base kruis"
                '♭' -> "$base flat" to "$base mol"
                else -> name to name
            }
            assertEquals(name, en, english.name(name))
            assertEquals(name, nl, dutch.name(name))
        }
        assertEquals("Sol kruis", dutch.name("Sol♯"))
        assertEquals("Mi mol", dutch.name("Mi♭"))
        assertEquals("Do", dutch.name("Do"))
    }

    @Test
    fun `every preset tuning's strings are spoken the dutch way`() {
        fun spoken(tuning: Tuning, notation: Notation) =
            tuning.strings.map { dutch.name(notation.nameIn(tuning, it.pitchClass)) }
        assertEquals(listOf("es", "as", "des", "ges", "bes", "es"), spoken(Tunings.HALF_STEP_DOWN, Notation.SHARPS))
        assertEquals(listOf("d", "a", "d", "fis", "a", "d"), spoken(Tunings.OPEN_D, Notation.FLATS))
        assertEquals(listOf("e", "a", "d", "g", "b", "e"), spoken(Tunings.STANDARD, Notation.SHARPS))
    }

    @Test
    fun `note names inside a text are spoken, other words stay`() {
        assertEquals("Sharps: C, C sharp, D …", english.text("Sharps: C, C♯, D …"))
        assertEquals("Flats: C, D flat, D …", english.text("Flats: C, D♭, D …"))
        assertEquals("Do Re Mi: Do, Do sharp, Re …", english.text("Do Re Mi: Do, Do♯, Re …"))
        assertEquals("Kruisen: c, cis, d …", dutch.text("Kruisen: C, C♯, D …"))
        assertEquals("Mollen: c, des, d …", dutch.text("Mollen: C, D♭, D …"))
        assertEquals("Do Re Mi: Do, Do kruis, Re …", dutch.text("Do Re Mi: Do, Do♯, Re …"))
        assertEquals("es as des ges bes es", dutch.text("E♭ A♭ D♭ G♭ B♭ E♭"))
    }

    @Test
    fun `text without note names is unchanged`() {
        assertEquals("Light or dark", english.text("Light or dark"))
        assertEquals("Bes is geen woord hier", dutch.text("Bes is geen woord hier"))
    }

    /** Every letter name, with either sign, that [Notation] can put on screen. */
    private fun letterNamesShown(): Set<String> = listOf(Notation.SHARPS, Notation.FLATS).flatMap { notation ->
        PitchClass.entries.flatMap { listOf(notation.nameOf(it, Accidental.SHARP), notation.nameOf(it, Accidental.FLAT)) }
    }.toSet()
}
