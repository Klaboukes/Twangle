package io.github.klaboukes.twangle.core.dsp.speech

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNameSpeechTest {

    private val english = NoteNameSpeech("%1\$s sharp", "%1\$s flat")
    private val dutch = NoteNameSpeech("%1\$s kruis", "%1\$s mol", LetterNames.DUTCH)
    private val german = NoteNameSpeech("%1\$s erhöht", "%1\$s erniedrigt", LetterNames.GERMAN)
    private val french = NoteNameSpeech("%1\$s dièse", "%1\$s bémol", solfege = "Do Ré Mi Fa Sol La Si".split(' '))
    private val portuguese = NoteNameSpeech("%1\$s sustenido", "%1\$s bemol", solfege = "Dó Ré Mi Fá Sol Lá Si".split(' '))

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
    fun `swedish says every letter name the app can show the way Swedish musicians do`() {
        val swedish = NoteNameSpeech("%1\$s med kors", "%1\$s med b", LetterNames.SWEDISH)
        val expected = mapOf(
            "C" to "c", "C♯" to "ciss", "D♭" to "dess", "D" to "d", "D♯" to "diss", "E♭" to "ess",
            "E" to "e", "F" to "f", "F♯" to "fiss", "G♭" to "gess", "G" to "g", "G♯" to "giss",
            "A♭" to "ass", "A" to "a", "A♯" to "aiss", "B♭" to "bess", "B" to "b",
        )
        assertEquals(expected.keys, letterNamesShown())
        expected.forEach { (name, spoken) -> assertEquals(name, spoken, swedish.name(name)) }
        assertEquals("eiss", swedish.name("E♯"))
        assertEquals("fess", swedish.name("F♭"))
        assertEquals("Korsförtecken: c, ciss, d …", swedish.text("Korsförtecken: C, C♯, D …"))
        assertEquals("Do med kors", swedish.name("Do♯"))
    }

    @Test
    fun `german says every name the german notation can show the way German musicians do`() {
        val expected = mapOf(
            "C" to "C", "C♯" to "Cis", "D♭" to "Des", "D" to "D", "D♯" to "Dis", "E♭" to "Es",
            "E" to "E", "F" to "F", "F♯" to "Fis", "G♭" to "Ges", "G" to "G", "G♯" to "Gis",
            "A♭" to "As", "A" to "A", "A♯" to "Ais", "B" to "B", "H" to "H",
        )
        assertEquals(expected.keys, namesShown(Notation.GERMAN))
        expected.forEach { (name, spoken) -> assertEquals(name, spoken, german.name(name)) }
    }

    @Test
    fun `german says english letters with their German names, B as written`() {
        assertEquals("B", german.name("B♭"))
        assertEquals("His", german.name("B♯"))
        assertEquals("Ces", german.name("C♭"))
        assertEquals("B", german.name("B"))
        assertEquals("E Es As Des Ges B Es", german.text("E E♭ A♭ D♭ G♭ B E♭"))
        assertEquals("Deutsch: C, Cis, D … H", german.text("Deutsch: C, C♯, D … H"))
        assertEquals("Do erhöht", german.name("Do♯"))
    }

    @Test
    fun `hungarian says every name the german notation can show the way Hungarian musicians do`() {
        val hungarian = NoteNameSpeech("%1\$s kereszt", "%1\$s bé", LetterNames.HUNGARIAN, "Dó Ré Mi Fá Szol Lá Szi".split(' '))
        val expected = mapOf(
            "C" to "C", "C♯" to "Cisz", "D♭" to "Desz", "D" to "D", "D♯" to "Disz", "E♭" to "Esz",
            "E" to "E", "F" to "F", "F♯" to "Fisz", "G♭" to "Gesz", "G" to "G", "G♯" to "Gisz",
            "A♭" to "Asz", "A" to "A", "A♯" to "Aisz", "B" to "B", "H" to "H",
        )
        assertEquals(expected.keys, namesShown(Notation.GERMAN))
        expected.forEach { (name, spoken) -> assertEquals(name, spoken, hungarian.name(name)) }
        assertEquals("B", hungarian.name("B♭"))
        assertEquals("Hisz", hungarian.name("B♯"))
        assertEquals("Cesz", hungarian.name("C♭"))
        assertEquals("Esz Asz Desz Gesz B Esz", hungarian.text("E♭ A♭ D♭ G♭ B♭ E♭"))
        assertEquals("Német: C, Cisz, D … H", hungarian.text("Német: C, C♯, D … H"))
        assertEquals("Szol kereszt", hungarian.name("Sol♯"))
        // The resource value "hungarian" picks this style.
        assertEquals("Cisz", noteNameSpeech("%1\$s kereszt", "%1\$s bé", "hungarian", "Dó Ré Mi Fá Szol Lá Szi").name("C♯"))
    }

    @Test
    fun `solfege is said as the language writes it`() {
        assertEquals("Ré", french.name("Re"))
        assertEquals("Ré dièse", french.name("Re♯"))
        assertEquals("Mi bémol", french.name("Mi♭"))
        assertEquals("Dó sustenido", portuguese.name("Do♯"))
        assertEquals("Lá", portuguese.name("La"))
        assertEquals("Sol", portuguese.name("Sol"))
        assertEquals("Re", english.name("Re"))
        val arabic = NoteNameSpeech("%1\$s دييز", "%1\$s بيمول", solfege = "دو ري مي فا صول لا سي".split(' '))
        assertEquals("صول دييز", arabic.name("Sol♯"))
        assertEquals("سي بيمول", arabic.name("Si♭"))
        // A bare syllable in a text may be a word, so only one with a sign is changed there.
        assertEquals("Do Re Mi : Do, Dó sustenido, Re …", portuguese.text("Do Re Mi : Do, Do♯, Re …"))
    }

    @Test
    fun `a solfege list of the wrong length falls back to the written syllables`() {
        val broken = NoteNameSpeech("%1\$s sharp", "%1\$s flat", solfege = listOf("Do", "Re"))
        assertEquals("Si", broken.name("Si"))
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

    /** Every letter name, with either sign, that the English letter notations can put on screen. */
    private fun letterNamesShown(): Set<String> = namesShown(Notation.SHARPS) + namesShown(Notation.FLATS)

    /** Every name, with either sign, that [notation] can put on screen. */
    private fun namesShown(notation: Notation): Set<String> =
        PitchClass.entries.flatMap { listOf(notation.nameOf(it, Accidental.SHARP), notation.nameOf(it, Accidental.FLAT)) }.toSet()
}
