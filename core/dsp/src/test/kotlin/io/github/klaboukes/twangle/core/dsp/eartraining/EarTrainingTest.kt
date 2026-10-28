package io.github.klaboukes.twangle.core.dsp.eartraining

import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapeFinder
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection.DOWN
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection.TOGETHER
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection.UP
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.tuningOf
import io.github.klaboukes.twangle.core.dsp.synth.StringMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class EarTrainingTest {

    // ---------- The notes asked for, per tuning ----------

    @Test
    fun `guitar standard gives its open strings, the naturals to the 5th fret and every note to the 12th`() {
        val tuning = Tunings.STANDARD
        assertEquals(notes("E2 A2 D3 G3 B3 E4"), NoteChoice.OPEN_STRINGS.notesOn(tuning))
        assertEquals(
            notes("E2 F2 G2 A2 B2 C3 D3 E3 F3 G3 A3 B3 C4 D4 E4 F4 G4 A4"),
            NoteChoice.NATURALS_TO_5TH.notesOn(tuning),
        )
        assertEquals((note("E2").midi..note("E5").midi).map(::Note), NoteChoice.ALL_TO_12TH.notesOn(tuning))
    }

    @Test
    fun `a bass asks for bass notes`() {
        val tuning = Instruments.BASS_4.defaultTuning
        assertEquals(notes("E1 A1 D2 G2"), NoteChoice.OPEN_STRINGS.notesOn(tuning))
        assertEquals(notes("E1 F1 G1 A1 B1 C2 D2 E2 F2 G2 A2 B2 C3"), NoteChoice.NATURALS_TO_5TH.notesOn(tuning))
        assertEquals((note("E1").midi..note("G3").midi).map(::Note), NoteChoice.ALL_TO_12TH.notesOn(tuning))
    }

    @Test
    fun `a re-entrant ukulele asks for its notes lowest first, from C4`() {
        val tuning = Instruments.UKULELE.defaultTuning
        assertEquals(notes("C4 E4 G4 A4"), NoteChoice.OPEN_STRINGS.notesOn(tuning))
        assertEquals((note("C4").midi..note("A5").midi).map(::Note), NoteChoice.ALL_TO_12TH.notesOn(tuning))
    }

    @Test
    fun `a lowered tuning asks for the lowered notes`() {
        val tuning = Tunings.STANDARD.lowered(2)
        assertEquals(notes("D2 G2 C3 F3 A3 D4"), NoteChoice.OPEN_STRINGS.notesOn(tuning))
        assertEquals((note("D2").midi..note("D5").midi).map(::Note), NoteChoice.ALL_TO_12TH.notesOn(tuning))
        // Half step down: the black-key open strings are left out of the naturals, E2 is their first fret.
        val halfDown = NoteChoice.NATURALS_TO_5TH.notesOn(Tunings.HALF_STEP_DOWN)
        assertEquals(note("E2"), halfDown.first())
        assertTrue(halfDown.all { it.isNatural })
        assertEquals(note("G4"), halfDown.last()) // the top string's 4th fret
    }

    @Test
    fun `a banjo's short string adds only the frets above where it starts`() {
        val tuning = Instruments.BANJO_5.defaultTuning // G4 D3 G3 B3 D4, the G4 string from the 5th fret
        assertEquals(notes("D3 G3 B3 D4 G4"), NoteChoice.OPEN_STRINGS.notesOn(tuning))
        assertEquals(note("G4"), NoteChoice.NATURALS_TO_5TH.notesOn(tuning).last())
        assertEquals(note("D5"), NoteChoice.ALL_TO_12TH.notesOn(tuning).last())
    }

    // ---------- Play it back ----------

    @Test
    fun `note questions come from the choice, in sequences of the length chosen`() {
        for (length in 1..NoteQuestions.MAX_LENGTH) {
            val source = NoteQuestions(Tunings.STANDARD, NoteChoice.NATURALS_TO_5TH, length)
            val random = Random(length)
            var previous: NoteQuestion? = null
            repeat(300) {
                val question = source.draw(random, previous)
                assertEquals(length, question.notes.size)
                assertTrue(question.notes.all { it in source.notes })
                question.notes.zipWithNext { a, b ->
                    assertNotEquals("no note twice in a row in $question", a, b)
                    assertTrue("within an octave in $question", abs(a.midi - b.midi) <= 12)
                }
                if (previous != null) assertNotEquals(previous!!.notes.first(), question.notes.first())
                assertTrue(source.offers(question))
                previous = question
            }
        }
    }

    @Test
    fun `every note of the choice is asked`() {
        val source = NoteQuestions(Instruments.UKULELE.defaultTuning, NoteChoice.OPEN_STRINGS)
        val visit = EarTrainingVisit(source, Random(3))
        val asked = (1..60).map { visit.next().notes.single() }.toSet()
        assertEquals(source.notes.toSet(), asked)
    }

    @Test
    fun `a note question checks the heard note in any octave or exactly`() {
        val question = NoteQuestion(notes("G3 B3"))
        assertTrue(question.isRightAt(0, note("G3"), NoteMatch.ANY_OCTAVE))
        assertTrue(question.isRightAt(0, note("G2"), NoteMatch.ANY_OCTAVE))
        assertTrue(question.isRightAt(0, note("G4"), NoteMatch.ANY_OCTAVE))
        assertFalse(question.isRightAt(0, note("G#3"), NoteMatch.ANY_OCTAVE))
        assertTrue(question.isRightAt(0, note("G3"), NoteMatch.EXACT))
        assertFalse(question.isRightAt(0, note("G2"), NoteMatch.EXACT))
        assertFalse(question.isRightAt(0, note("F#3"), NoteMatch.EXACT))
        assertTrue(question.isRightAt(1, note("B3"), NoteMatch.EXACT))
        assertFalse(question.isRightAt(1, note("G3"), NoteMatch.EXACT))
    }

    @Test
    fun `a note question holds one to four notes`() {
        assertThrows(IllegalArgumentException::class.java) { NoteQuestion(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { NoteQuestion(notes("E2 A2 D3 G3 B3")) }
        assertThrows(IllegalArgumentException::class.java) { NoteQuestions(Tunings.STANDARD, NoteChoice.OPEN_STRINGS, 5) }
    }

    // ---------- Intervals ----------

    @Test
    fun `interval questions use only the chosen intervals and directions, on the neck`() {
        val tuning = Tunings.STANDARD
        val chosen = setOf(Interval.PERFECT_FOURTH, Interval.PERFECT_FIFTH)
        val source = IntervalQuestions(tuning, chosen, setOf(UP, DOWN))
        val range = NoteChoice.ALL_TO_12TH.notesOn(tuning)
        val random = Random(7)
        val seen = mutableSetOf<Pair<Interval, IntervalDirection>>()
        repeat(400) {
            val question = source.draw(random, null)
            assertTrue(question.interval in chosen)
            assertTrue(question.direction in setOf(UP, DOWN))
            assertTrue(question.low in range && question.high in range)
            assertEquals(question.interval.semitones, question.high.midi - question.low.midi)
            seen += question.interval to question.direction
        }
        assertEquals(4, seen.size)
    }

    @Test
    fun `an interval sounds low then high going up, high then low going down`() {
        val low = note("A2")
        assertEquals(notes("A2 E3"), IntervalQuestion(Interval.PERFECT_FIFTH, UP, low).notes)
        assertEquals(notes("E3 A2"), IntervalQuestion(Interval.PERFECT_FIFTH, DOWN, low).notes)
        assertEquals(notes("A2 E3"), IntervalQuestion(Interval.PERFECT_FIFTH, TOGETHER, low).notes)
        assertTrue(IntervalQuestion(Interval.PERFECT_FIFTH, UP, low).isRight(Interval.PERFECT_FIFTH))
        assertFalse(IntervalQuestion(Interval.PERFECT_FIFTH, UP, low).isRight(Interval.PERFECT_FOURTH))
    }

    @Test
    fun `the intervals run from the minor second to the octave`() {
        assertEquals((1..12).toList(), Interval.entries.map { it.semitones })
    }

    @Test
    fun `an octave fits a ukulele, and nothing at all is refused`() {
        val ukulele = Instruments.UKULELE.defaultTuning
        val question = IntervalQuestions(ukulele, setOf(Interval.OCTAVE)).draw(Random(1), null)
        assertTrue(question.high in NoteChoice.ALL_TO_12TH.notesOn(ukulele))
        assertThrows(IllegalArgumentException::class.java) { IntervalQuestions(ukulele, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { IntervalQuestions(ukulele, setOf(Interval.OCTAVE), emptySet()) }
        // A one-string tuning with only an octave of neck: the octave itself is a single question.
        val oneString = tuningOf("one", "E2")
        val only = IntervalQuestions(oneString, setOf(Interval.OCTAVE))
        val first = only.draw(Random(1), null)
        assertEquals(first, only.draw(Random(1), first))
    }

    // ---------- Chord type ----------

    @Test
    fun `chord questions use the chosen types, the Chords tool's first shape, and a new root every time`() {
        val tuning = Tunings.STANDARD
        val chosen = setOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH)
        val source = ChordTypeQuestions(tuning, chosen)
        val random = Random(11)
        var previous: ChordTypeQuestion? = null
        repeat(60) {
            val question = source.draw(random, previous)
            assertTrue(question.quality in chosen)
            assertEquals(ChordShapeFinder.shapesOf(question.chord, tuning).first(), question.shape)
            assertTrue(question.isRight(question.quality))
            assertFalse(question.isRight(ChordQuality.SUS4))
            if (previous != null) assertNotEquals(previous!!.chord.root, question.chord.root)
            assertTrue(source.offers(question))
            previous = question
        }
    }

    @Test
    fun `all twenty of the Chords tool's types can be asked, on a guitar and a ukulele`() {
        assertEquals(20, ChordQuality.inPanel.size)
        for (tuning in listOf(Tunings.STANDARD, Instruments.UKULELE.defaultTuning)) {
            val source = ChordTypeQuestions(tuning, ChordQuality.inPanel.toSet())
            val visit = EarTrainingVisit(source, Random(5))
            val asked = (1..600).map { visit.next().quality }.toSet()
            assertEquals("on ${tuning.id}", ChordQuality.inPanel.toSet(), asked)
        }
    }

    @Test
    fun `every chord type alone can be asked on every instrument's every tuning but the banjo's`() {
        for (instrument in Instruments.all) for (tuning in instrument.tunings.filterNot { it.hasShortString }) {
            for (quality in ChordQuality.inPanel) {
                val question = ChordTypeQuestions(tuning, setOf(quality)).draw(Random(3), null)
                assertEquals("${tuning.id} $quality", quality, question.quality)
                assertTrue("${tuning.id} $quality: at most ${StringMix.MAX_NOTES} strings", question.shape.notesIn(tuning).count { it != null } in 1..StringMix.MAX_NOTES)
            }
        }
    }

    @Test
    fun `a fresh install asks major, minor and 7`() {
        assertEquals(listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH), ChordTypeQuestions.DEFAULT.toList())
    }

    @Test
    fun `chord type isn't offered on a banjo, nor with no type chosen`() {
        assertThrows(IllegalArgumentException::class.java) { ChordTypeQuestions(Instruments.BANJO_5.defaultTuning, setOf(ChordQuality.MAJOR)) }
        assertThrows(IllegalArgumentException::class.java) { ChordTypeQuestions(Tunings.STANDARD, emptySet()) }
    }

    // ---------- The visit ----------

    @Test
    fun `no question is asked twice in a row, missed ones coming back included`() {
        val sources = listOf<() -> QuestionSource<*>>(
            { NoteQuestions(Tunings.STANDARD, NoteChoice.OPEN_STRINGS) },
            { NoteQuestions(Instruments.BASS_4.defaultTuning, NoteChoice.OPEN_STRINGS, 3) },
            { IntervalQuestions(Tunings.STANDARD, setOf(Interval.OCTAVE)) },
            { IntervalQuestions(Instruments.UKULELE.defaultTuning, setOf(Interval.PERFECT_FOURTH, Interval.PERFECT_FIFTH), setOf(UP)) },
            { ChordTypeQuestions(Tunings.STANDARD, setOf(ChordQuality.MAJOR)) },
            { TwoQuestions },
        )
        for (seed in 0 until 20) for (make in sources) {
            @Suppress("UNCHECKED_CAST")
            val visit = EarTrainingVisit(make() as QuestionSource<Any>, Random(seed))
            val answers = Random(seed + 1000)
            var previous: Any? = null
            repeat(200) {
                val question = visit.next()
                assertNotEquals("seed $seed", previous, question)
                visit.answer(answers.nextInt(3) != 0) // a third of them missed
                previous = question
            }
        }
    }

    @Test
    fun `a missed question is asked again three to five questions later`() {
        for (seed in 0 until 50) {
            // Four notes in a row from the whole lower neck: too many questions for a new one to match by chance.
            val visit = EarTrainingVisit(NoteQuestions(Tunings.STANDARD, NoteChoice.ALL_TO_12TH, 4), Random(seed))
            visit.next()
            visit.answer(true)
            val missed = visit.next()
            visit.answer(false)
            val after = (1..8).map { visit.next().also { visit.answer(true) } }
            val back = after.indexOf(missed) + 1
            assertTrue("seed $seed: back after $back", back in EarTrainingVisit.MISSED_RETURN_MIN..EarTrainingVisit.MISSED_RETURN_MAX)
            assertEquals("asked again once, then mixed in as usual", 1, after.count { it == missed })
        }
    }

    @Test
    fun `a question missed again comes back again, one answered right does not`() {
        val visit = EarTrainingVisit(NoteQuestions(Tunings.STANDARD, NoteChoice.ALL_TO_12TH, 4), Random(42))
        val missed = visit.next()
        visit.answer(false)
        val firstRound = (1..5).map { visit.next().also { if (it == missed) visit.answer(false) else visit.answer(true) } }
        assertTrue(missed in firstRound)
        val secondRound = (1..5).map { visit.next().also { visit.answer(true) } }
        assertTrue("missed twice, so back again", missed in secondRound)
        val later = (1..20).map { visit.next().also { visit.answer(true) } }
        assertFalse("answered right, so not sent back", missed in later)
    }

    @Test
    fun `the tally counts this visit's answers and starts again with a new visit`() {
        val source = NoteQuestions(Tunings.STANDARD, NoteChoice.OPEN_STRINGS)
        val visit = EarTrainingVisit(source, Random(1))
        assertEquals(Tally(0, 0), visit.tally)
        listOf(true, true, false, true).forEach {
            visit.next()
            visit.answer(it)
        }
        assertEquals(Tally(right = 3, asked = 4), visit.tally)
        visit.next() // a question not answered doesn't count
        assertEquals(Tally(3, 4), visit.tally)
        assertEquals(Tally(0, 0), EarTrainingVisit(source, Random(1)).tally)
    }

    @Test
    fun `a question is answered once`() {
        val visit = EarTrainingVisit(NoteQuestions(Tunings.STANDARD, NoteChoice.OPEN_STRINGS), Random(1))
        assertThrows(IllegalStateException::class.java) { visit.answer(true) }
        visit.next()
        assertFalse(visit.answered)
        visit.answer(false)
        assertTrue(visit.answered)
        assertThrows(IllegalStateException::class.java) { visit.answer(true) }
    }

    @Test
    fun `changing the choices keeps the tally and drops the missed questions no longer asked`() {
        val fourths = IntervalQuestions(Tunings.STANDARD, setOf(Interval.PERFECT_FOURTH))
        val fifths = IntervalQuestions(Tunings.STANDARD, setOf(Interval.PERFECT_FIFTH))
        val visit = EarTrainingVisit<IntervalQuestion>(fourths, Random(9))
        visit.next()
        visit.answer(false)
        visit.change(fifths)
        assertEquals(Tally(0, 1), visit.tally)
        repeat(10) { assertEquals(Interval.PERFECT_FIFTH, visit.next().interval) }
    }

    @Test
    fun `the same seed repeats a visit, another seed doesn't`() {
        fun visit(seed: Int): List<Any> {
            val visits = listOf(
                EarTrainingVisit(NoteQuestions(Tunings.DROP_D, NoteChoice.NATURALS_TO_5TH, 3), Random(seed)),
                EarTrainingVisit(IntervalQuestions(Tunings.STANDARD, Interval.entries.toSet(), IntervalDirection.entries.toSet()), Random(seed)),
                EarTrainingVisit(ChordTypeQuestions(Tunings.STANDARD, ChordQuality.inPanel.toSet()), Random(seed)),
            )
            return visits.flatMap { v -> (1..30).map { i -> v.next().also { v.answer(i % 4 != 0) } } }
        }
        assertEquals(visit(2026), visit(2026))
        assertNotEquals(visit(2026), visit(2027))
    }

    /** The smallest choice that can avoid a repeat: two questions, so they must alternate. */
    private object TwoQuestions : QuestionSource<String> {
        override fun draw(random: Random, previous: String?): String =
            if (previous == null) listOf("a", "b").random(random) else if (previous == "a") "b" else "a"

        override fun offers(question: String): Boolean = true
    }

    private fun note(text: String): Note {
        val symbol = text.takeWhile { !it.isDigit() }
        return Note.of(PitchClass.entries.first { it.symbol == symbol }, text.substring(symbol.length).toInt())
    }

    private fun notes(text: String): List<Note> = text.split(' ').map(::note)
}
