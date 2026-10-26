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
            assertEquals("${instrument.id} courses", if (instrument.id == "mandolin") 2 else 1, voice.courses)
            assertEquals("${instrument.id} has a known voice", true, voice in StringVoices.all)
        }
        assertEquals(StringVoices.STEEL_GUITAR, Instruments.GUITAR.voice)
        assertEquals(StringVoices.STEEL_GUITAR, Instruments.GUITAR_8.voice)
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
    }
}
