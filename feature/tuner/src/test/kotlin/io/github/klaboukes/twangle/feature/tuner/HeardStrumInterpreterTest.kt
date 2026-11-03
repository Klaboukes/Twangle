package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.strum.StrumKind
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [HeardStrumInterpreter]: what shows, and for how long, as the analysis beside the tuner decides each sound. */
class HeardStrumInterpreterTest {

    private val options = TunerOptions(tuning = Tunings.STANDARD)
    private val interpreter = HeardStrumInterpreter()
    private val c = Chord.of(PitchClass.C, ChordQuality.MAJOR)

    private fun strings(vararg cents: Float) = cents.map { StrumStringReading(it, present = true, shadowed = false) }

    private fun open(count: Int, reading: Int, vararg cents: Float, ringing: Boolean = true) = HeardReading(
        count, ringing, StrumKind.OPEN_STRINGS,
        strum = StrumReading(count, ringing, reading, strings(*cents), levelDbfs = -30f),
    )

    private fun chord(count: Int, seconds: Double, chord: Chord? = c) = HeardReading(
        count, true, StrumKind.CHORD, seconds,
        chord = ChordReading(seconds, count, ringing = true, reading = true, chord = chord, bass = PitchClass.C),
    )

    private fun heard(reading: HeardReading) = interpreter.interpret(reading, options)

    @Test
    fun `a strum of open strings shows its verdicts, and a pluck after it leaves them shown but done`() {
        assertSame(HeardStrum.None, heard(HeardReading.FRESH))
        assertSame(HeardStrum.None, heard(HeardReading(1, ringing = true)))
        val shown = heard(open(1, 1, 0f, -20f, 0f, 15f, 0f, 0f)) as HeardStrum.OpenStrings
        assertEquals(StrumPhase.RINGING, shown.check.phase)
        assertEquals(StrumVerdict.FLAT, shown.check.strings[1].verdict)
        assertEquals(StrumVerdict.SHARP, shown.check.strings[3].verdict)

        // A new sound: the strings shown wait for it…
        val hearing = heard(HeardReading(2, ringing = true)) as HeardStrum.OpenStrings
        assertEquals(StrumPhase.HEARING, hearing.check.phase)
        // …and it was one string: the last strum's verdicts stay, no longer ringing.
        val after = heard(HeardReading(2, ringing = true, kind = StrumKind.NONE)) as HeardStrum.OpenStrings
        assertEquals(StrumPhase.DONE, after.check.phase)
        assertEquals(shown.check.strings, after.check.strings)
    }

    @Test
    fun `a chord shows from its first reading, and is named once settled`() {
        heard(HeardReading(1, ringing = true))
        val listening = heard(chord(1, 0.5)) as HeardStrum.FrettedChord
        assertEquals(ChordPhase.LISTENING, listening.name.phase)
        var shown: HeardStrum = listening
        for (i in 1..5) shown = heard(chord(1, 0.5 + 0.1 * i))
        assertSame(c, (shown as HeardStrum.FrettedChord).name.chord?.chord)
    }

    @Test
    fun `a chord replaces a strum of open strings only once named, and a strum replaces a chord at its first reading`() {
        heard(open(1, 1, 0f, 0f, 0f, 0f, 0f, 0f))
        heard(HeardReading(2, ringing = true, kind = StrumKind.CHORD))
        assertTrue(heard(chord(2, 1.0)) is HeardStrum.OpenStrings)
        var shown: HeardStrum = HeardStrum.None
        for (i in 1..5) shown = heard(chord(2, 1.0 + 0.1 * i))
        assertTrue("$shown", shown is HeardStrum.FrettedChord)
        assertTrue(heard(open(3, 1, 0f, 0f, 0f, 0f, 0f, 0f)) is HeardStrum.OpenStrings)
    }

    @Test
    fun `a reading that hears too few strings shows nothing new, and a fresh start clears what was shown`() {
        val few = HeardReading(
            1, true, StrumKind.OPEN_STRINGS,
            strum = StrumReading(1, true, 1, strings(0f) + List(5) { StrumStringReading(Float.NaN, present = false, shadowed = false) }),
        )
        assertSame(HeardStrum.None, heard(few))
        assertTrue(heard(open(2, 1, 0f, 0f, 0f, 0f, 0f, 0f)) is HeardStrum.OpenStrings)
        assertSame(HeardStrum.None, heard(HeardReading.FRESH))
    }
}
