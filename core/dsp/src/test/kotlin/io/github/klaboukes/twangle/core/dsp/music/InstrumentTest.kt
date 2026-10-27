package io.github.klaboukes.twangle.core.dsp.music

import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class InstrumentTest {

    private val mapper = NoteMapper()

    @Test
    fun `presets have the expected notes in string order`() {
        val expected = mapOf(
            "guitar_12_standard" to "E2 A2 D3 G3 B3 E4",
            "guitar_7_standard" to "B1 E2 A2 D3 G3 B3 E4",
            "guitar_8_standard" to "F#1 B1 E2 A2 D3 G3 B3 E4",
            "bass_4_standard" to "E1 A1 D2 G2",
            "bass_5_standard" to "B0 E1 A1 D2 G2",
            "ukulele_standard" to "G4 C4 E4 A4",
            "ukulele_low_g" to "G3 C4 E4 A4",
            "ukulele_baritone" to "D3 G3 B3 E4",
            "violin_standard" to "G3 D4 A4 E5",
            "viola_standard" to "C3 G3 D4 A4",
            "cello_standard" to "C2 G2 D3 A3",
            "mandolin_standard" to "G3 D4 A4 E5",
        )
        for ((id, notes) in expected) {
            val tuning = requireNotNull(Instruments.tuningById(id)) { id }
            assertEquals(id, notes, tuning.strings.joinToString(" "))
        }
    }

    @Test
    fun `instruments have the expected string counts`() {
        val expected = mapOf(
            Instruments.GUITAR to 6,
            Instruments.GUITAR_12 to 6,
            Instruments.GUITAR_7 to 7,
            Instruments.GUITAR_8 to 8,
            Instruments.BASS_4 to 4,
            Instruments.BASS_5 to 5,
            Instruments.UKULELE to 4,
            Instruments.VIOLIN to 4,
            Instruments.VIOLA to 4,
            Instruments.CELLO to 4,
            Instruments.MANDOLIN to 4,
        )
        assertEquals(expected.keys.toList(), Instruments.all)
        for ((instrument, count) in expected) {
            assertEquals(instrument.id, count, instrument.stringCount)
            for (tuning in instrument.tunings) assertEquals(tuning.id, count, tuning.stringCount)
        }
    }

    @Test
    fun `the six-string guitar keeps today's tunings, ids and default`() {
        assertSame(Tunings.all, Instruments.GUITAR.tunings)
        assertSame(Tunings.DEFAULT, Instruments.GUITAR.defaultTuning)
        assertSame(Instruments.GUITAR, Instruments.DEFAULT)
        assertSame(Instruments.GUITAR, Instruments.all.first())
        for (tuning in Tunings.all) assertSame(Instruments.GUITAR, Instruments.ofTuning(tuning.id))
    }

    @Test
    fun `ids are unique across instruments and tunings`() {
        assertEquals(Instruments.all.size, Instruments.all.map { it.id }.toSet().size)
        val tuningIds = Instruments.all.flatMap { it.tunings }.map { it.id }
        assertEquals(tuningIds.size, tuningIds.toSet().size)
    }

    @Test
    fun `lookups find every instrument and tuning and return null for unknown ids`() {
        for (instrument in Instruments.all) {
            assertSame(instrument, Instruments.byId(instrument.id))
            for (tuning in instrument.tunings) {
                assertSame(tuning, Instruments.tuningById(tuning.id))
                assertSame(tuning, instrument.tuningById(tuning.id))
                assertSame(instrument, Instruments.ofTuning(tuning.id))
            }
        }
        assertNull(Instruments.byId("banjo"))
        assertNull(Instruments.tuningById("not_a_tuning"))
        assertNull(Instruments.ofTuning("not_a_tuning"))
        assertNull(Instruments.GUITAR.tuningById("bass_4_standard"))
    }

    @Test
    fun `an instrument's range spans every preset`() {
        // Guitar's Standard starts at E2, but Open C reaches C2; the baritone ukulele reaches D3.
        val expected = mapOf(
            Instruments.GUITAR to ("C2" to "E4"),
            Instruments.GUITAR_12 to ("E2" to "E4"),
            Instruments.GUITAR_7 to ("B1" to "E4"),
            Instruments.GUITAR_8 to ("F#1" to "E4"),
            Instruments.BASS_4 to ("E1" to "G2"),
            Instruments.BASS_5 to ("B0" to "G2"),
            Instruments.UKULELE to ("D3" to "A4"),
            Instruments.VIOLIN to ("G3" to "E5"),
            Instruments.VIOLA to ("C3" to "A4"),
            Instruments.CELLO to ("C2" to "A3"),
            Instruments.MANDOLIN to ("G3" to "E5"),
        )
        for ((instrument, range) in expected) {
            assertEquals(instrument.id, range.first, instrument.lowestNote.toString())
            assertEquals(instrument.id, range.second, instrument.highestNote.toString())
        }
    }

    @Test
    fun `re-entrant ukulele's lowest note is not its first string`() {
        val standard = Instruments.UKULELE.defaultTuning
        assertEquals("C4", standard.lowestNote.toString())
        assertEquals("A4", standard.highestNote.toString())
        assertEquals("G3", Instruments.UKULELE.tuningById("ukulele_low_g")!!.lowestNote.toString())
        val baritone = Instruments.UKULELE.tuningById("ukulele_baritone")!!
        assertEquals("D3", baritone.lowestNote.toString())
        assertEquals(baritone.strings.first(), baritone.lowestNote)
    }

    @Test
    fun `lowest five-string bass note is B0 at about 30_9 Hz`() {
        assertEquals(30.8677f, mapper.frequencyOf(Instruments.BASS_5.lowestNote), 1e-3f)
        assertEquals(41.2034f, mapper.frequencyOf(Instruments.BASS_4.lowestNote), 1e-3f)
        assertEquals(659.2551f, mapper.frequencyOf(Instruments.VIOLIN.highestNote), 1e-2f)
    }

    @Test
    fun `presets know how players spell them`() {
        assertEquals(Accidental.SHARP, Instruments.GUITAR_8.defaultTuning.spelling)
        for (instrument in Instruments.all - Instruments.GUITAR - Instruments.GUITAR_8) {
            for (tuning in instrument.tunings) assertNull(tuning.id, tuning.spelling)
        }
    }

    @Test
    fun `each in-tune string of every preset is its own nearest string`() {
        for (instrument in Instruments.all) {
            for (tuning in instrument.tunings) {
                tuning.strings.forEachIndexed { i, note ->
                    val midi = mapper.midiOf(mapper.frequencyOf(note))
                    assertEquals("${tuning.id} string $i", i, tuning.nearestStringIndex(midi))
                    assertEquals("${tuning.id} string $i", 0f, tuning.centsFromString(midi, i), 0.01f)
                }
            }
        }
    }

    @Test
    fun `each instrument sounds like its own kind`() {
        for (instrument in Instruments.all) {
            val voice = instrument.voice
            val bowed = instrument.id in setOf("violin", "viola", "cello")
            assertEquals("${instrument.id} bowed", bowed, voice.bowed)
            assertEquals("${instrument.id} courses", if (instrument.id in setOf("mandolin", "guitar_12")) 2 else 1, voice.courses)
            assertEquals("${instrument.id} has a known voice", true, voice in StringVoices.all)
        }
        assertEquals(StringVoices.STEEL_GUITAR, Instruments.GUITAR.voice)
        assertEquals(StringVoices.STEEL_GUITAR, Instruments.GUITAR_8.voice)
        assertEquals(StringVoices.TWELVE_STRING, Instruments.GUITAR_12.voice)
        assertEquals(StringVoices.ELECTRIC_BASS, Instruments.BASS_5.voice)
        assertEquals(StringVoices.UKULELE, Instruments.UKULELE.voice)
        assertEquals(StringVoices.DEFAULT, Instruments.DEFAULT.voice)
    }

    @Test
    fun `invalid instruments are rejected`() {
        val four = Tuning("four", listOf(Note(40), Note(45), Note(50), Note(55)))
        assertThrows(IllegalArgumentException::class.java) { Instrument(" ", 4, listOf(four), StringVoices.STEEL_GUITAR) }
        assertThrows(IllegalArgumentException::class.java) { Instrument("none", 4, emptyList(), StringVoices.STEEL_GUITAR) }
        assertThrows(IllegalArgumentException::class.java) { Instrument("zero", 0, listOf(four), StringVoices.STEEL_GUITAR) }
        assertThrows(IllegalArgumentException::class.java) { Instrument("wrong_count", 6, listOf(four), StringVoices.STEEL_GUITAR) }
        assertThrows(IllegalArgumentException::class.java) { Instrument("twice", 4, listOf(four, four), StringVoices.STEEL_GUITAR) }
        val paired = four.copy(id = "paired", octaveCourses = setOf(0))
        assertThrows(IllegalArgumentException::class.java) { Instrument("mixed", 4, listOf(four, paired), StringVoices.STEEL_GUITAR) }
        assertThrows(IllegalArgumentException::class.java) { four.copy(octaveCourses = setOf(4)) }
    }

    @Test
    fun `the 12-string's lower four courses have an octave string, the top two are in unison`() {
        val twelve = Instruments.GUITAR_12
        assertEquals(setOf(0, 1, 2, 3), twelve.octaveCourses)
        assertEquals(listOf(true, true, true, true, false, false), (0 until 6).map(twelve.defaultTuning::hasOctaveString))
        // Tuned like the guitar, strung differently: no other instrument has octave strings.
        assertEquals(Tunings.STANDARD.strings, twelve.defaultTuning.strings)
        for (instrument in Instruments.all - twelve) assertEquals(instrument.id, emptySet<Int>(), instrument.octaveCourses)
        // A lowered 12-string keeps its pairs.
        assertEquals(twelve.octaveCourses, twelve.defaultTuning.lowered(2).octaveCourses)
    }

    @Test
    fun `a 12-string's octave string is its course's from the first note`() {
        val twelve = Instruments.GUITAR_12.defaultTuning
        // The octave strings sound E3 A3 D4 G4: each its own course, not the guitar's D, G, B or E string.
        listOf("E3" to 52, "A3" to 57, "D4" to 62, "G4" to 67).forEachIndexed { course, (octave, note) ->
            val midi = note.toFloat()
            assertEquals(octave, course, twelve.nearestStringIndex(midi))
            assertEquals(octave, 0f, twelve.distanceToString(midi, course), 1e-4f)
            // On the six-string guitar the same note is another string's, or off between two.
            assertEquals(octave, true, Tunings.STANDARD.nearestStringIndex(midi) != course)
        }
        // 20 cents sharp of the octave string is 20 cents off its course; the top two have none.
        assertEquals(0.2f, twelve.distanceToString(52.2f, 0), 1e-4f)
        assertEquals(12f, twelve.distanceToString(71f, 4), 1e-4f)
        assertEquals(5, twelve.nearestStringIndex(64f))
    }

    @Test
    fun `a 12-string lowered half a step is Half step down, like the guitar`() {
        val lowered = Instruments.GUITAR_12.defaultTuning.lowered(1)
        assertEquals(LoweredName.Preset(Tunings.HALF_STEP_DOWN), lowered.loweredName)
        assertEquals(Accidental.FLAT, lowered.spelling)
        assertEquals(LoweredName.Common(CommonTuning.C_STANDARD), Instruments.GUITAR_12.defaultTuning.lowered(4).loweredName)
    }
}
