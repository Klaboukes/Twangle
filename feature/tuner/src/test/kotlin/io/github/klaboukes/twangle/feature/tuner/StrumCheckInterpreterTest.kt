package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrumCheckInterpreterTest {

    private val options = TunerOptions(mode = TunerMode.AUTO, tuning = Tunings.STANDARD, strumCheck = true)

    private fun strings(vararg cents: Float, shadowed: Set<Int> = emptySet()): List<StrumStringReading> =
        cents.mapIndexed { i, c -> StrumStringReading(c, present = !c.isNaN(), shadowed = i in shadowed) }

    private val inTune = strings(0.4f, -1f, 2f, 0f, 1f, -0.3f)

    @Test
    fun `waits for a strum, hears it, then shows every string while it rings and after`() {
        val interpreter = StrumCheckInterpreter()
        val waiting = interpreter.waiting(options)
        assertEquals(StrumPhase.WAITING, waiting.phase)
        assertTrue(waiting.strings.isEmpty())
        assertEquals(Tunings.STANDARD, waiting.tuning)

        assertEquals(StrumPhase.HEARING, interpreter.interpret(StrumReading(1, ringing = true, readingsSinceStrum = 0), options).phase)

        val ringing = interpreter.interpret(StrumReading(1, true, 1, strings(0.4f, -12.6f, 2f, 8f, Float.NaN, -0.3f)), options)
        assertEquals(StrumPhase.RINGING, ringing.phase)
        assertEquals(
            listOf(StrumVerdict.IN_TUNE, StrumVerdict.FLAT, StrumVerdict.IN_TUNE, StrumVerdict.SHARP, StrumVerdict.NOT_HEARD, StrumVerdict.IN_TUNE),
            ringing.strings.map { it.verdict },
        )
        // Whole cents, as the panel writes them; 0 for a string not heard.
        assertEquals(listOf(0f, -13f, 2f, 8f, 0f, 0f), ringing.strings.map { it.cents })

        val done = interpreter.interpret(StrumReading(1, ringing = false, readingsSinceStrum = 9), options)
        assertEquals(StrumPhase.DONE, done.phase)
        assertEquals(ringing.strings, done.strings)
    }

    @Test
    fun `a new strum keeps the last strings until its own first reading`() {
        val interpreter = StrumCheckInterpreter()
        interpreter.interpret(StrumReading(1, true, 1, inTune), options)
        val hearing = interpreter.interpret(StrumReading(2, ringing = true, readingsSinceStrum = 0), options)
        assertEquals(StrumPhase.HEARING, hearing.phase)
        assertEquals(6, hearing.strings.size)
        assertTrue(hearing.strings.all { it.verdict == StrumVerdict.IN_TUNE })
    }

    @Test
    fun `a strum too short to read leaves what was shown, or waits`() {
        val interpreter = StrumCheckInterpreter()
        assertEquals(StrumPhase.WAITING, interpreter.interpret(StrumReading(1, ringing = false, readingsSinceStrum = 0), options).phase)
        interpreter.interpret(StrumReading(2, true, 1, inTune), options)
        val short = interpreter.interpret(StrumReading(3, ringing = false, readingsSinceStrum = 0), options)
        assertEquals(StrumPhase.DONE, short.phase)
        assertEquals(6, short.strings.size)
    }

    @Test
    fun `a string in tune stays so a cent further within one strum, not into the next`() {
        val interpreter = StrumCheckInterpreter()
        interpreter.interpret(StrumReading(1, true, 1, strings(0f, 4.5f, 0f, 0f, 0f, 0f)), options)
        interpreter.interpret(StrumReading(1, true, 2, strings(0f, 5.6f, 0f, 0f, 0f, 0f)), options)
        val edge = interpreter.interpret(StrumReading(1, true, 3, strings(0f, 5.6f, 0f, 0f, 0f, 0f)), options)
        assertEquals(6f, edge.strings[1].cents)
        assertEquals(StrumVerdict.IN_TUNE, edge.strings[1].verdict)

        // As from the pipeline: the next strum is heard, then read.
        interpreter.interpret(StrumReading(2, ringing = true, readingsSinceStrum = 0), options)
        val next = interpreter.interpret(StrumReading(2, true, 1, strings(0f, 5.6f, 0f, 0f, 0f, 0f)), options)
        assertEquals(StrumVerdict.SHARP, next.strings[1].verdict)
    }

    @Test
    fun `a strum's readings count together, the loud ones most`() {
        val interpreter = StrumCheckInterpreter()
        val nan = Float.NaN
        // Two loud readings, then fainter ones as the strings die away: off by several cents, a
        // string lost, a missing one found.
        interpreter.interpret(StrumReading(1, true, 1, strings(-10f, nan, 25f, 0.2f, -30f, 0.1f), levelDbfs = -42f), options)
        interpreter.interpret(StrumReading(1, true, 2, strings(-9.6f, nan, 24.8f, 0f, -29.9f, -0.8f), levelDbfs = -43f), options)
        var state = interpreter.interpret(StrumReading(1, true, 3, strings(-14.2f, 59.6f, 25.3f, -0.3f, nan, -0.8f), levelDbfs = -50f), options)
        repeat(5) { n ->
            state = interpreter.interpret(StrumReading(1, true, 4 + n, strings(-15f, 61f, 27f, 1.5f, nan, 2f), levelDbfs = -53f), options)
        }
        assertEquals(listOf(-10f, 0f, 25f, 0f, -30f, 0f), state.strings.map { it.cents })
        assertEquals(
            listOf(StrumVerdict.FLAT, StrumVerdict.NOT_HEARD, StrumVerdict.SHARP, StrumVerdict.IN_TUNE, StrumVerdict.FLAT, StrumVerdict.IN_TUNE),
            state.strings.map { it.verdict },
        )

        // Equal weights: the median, not the mean.
        val equal = StrumCheckInterpreter()
        equal.interpret(StrumReading(1, true, 1, strings(1f, 0f, 0f, 0f, 0f, 0f)), options)
        equal.interpret(StrumReading(1, true, 2, strings(30f, 0f, 0f, 0f, 0f, 0f)), options)
        assertEquals(2f, equal.interpret(StrumReading(1, true, 3, strings(2f, 0f, 0f, 0f, 0f, 0f)), options).strings[0].cents)
    }

    @Test
    fun `a new strum starts its own readings`() {
        val interpreter = StrumCheckInterpreter()
        interpreter.interpret(StrumReading(1, true, 1, strings(0f, -20f, 0f, 0f, 0f, 0f), levelDbfs = -30f), options)
        interpreter.interpret(StrumReading(1, false, 1), options)
        // The string tuned up and strummed again, more softly: heard, then read.
        interpreter.interpret(StrumReading(2, ringing = true, readingsSinceStrum = 0), options)
        val next = interpreter.interpret(StrumReading(2, true, 1, strings(0f, 1f, 0f, 0f, 0f, 0f), levelDbfs = -45f), options)
        assertEquals(1f, next.strings[1].cents)
        assertEquals(StrumVerdict.IN_TUNE, next.strings[1].verdict)
    }

    @Test
    fun `a sound that hears fewer than half the strings is no strum`() {
        val interpreter = StrumCheckInterpreter()
        val nan = Float.NaN
        val noise = strings(nan, -43f, nan, 100f, nan, nan)
        interpreter.interpret(StrumReading(1, ringing = true, readingsSinceStrum = 0), options)
        assertEquals(StrumPhase.HEARING, interpreter.interpret(StrumReading(1, true, 1, noise), options).phase)
        val nothing = interpreter.interpret(StrumReading(1, ringing = false, readingsSinceStrum = 1), options)
        assertEquals(StrumPhase.WAITING, nothing.phase)
        assertTrue(nothing.strings.isEmpty())

        // After a real strum, a voice or a knock leaves it on screen.
        interpreter.interpret(StrumReading(2, true, 1, strings(0f, -20f, 0f, nan, 0f, 0f)), options)
        interpreter.interpret(StrumReading(2, ringing = false, readingsSinceStrum = 1), options)
        interpreter.interpret(StrumReading(3, ringing = true, readingsSinceStrum = 0), options)
        val hearing = interpreter.interpret(StrumReading(3, true, 1, noise), options)
        assertEquals(StrumPhase.HEARING, hearing.phase)
        assertEquals(-20f, hearing.strings[1].cents)
        val done = interpreter.interpret(StrumReading(3, ringing = false, readingsSinceStrum = 1), options)
        assertEquals(StrumPhase.DONE, done.phase)
        assertEquals(StrumVerdict.FLAT, done.strings[1].verdict)
        assertEquals(StrumVerdict.NOT_HEARD, done.strings[3].verdict)

        // Half the strings is enough: three strummed, three missing.
        val half = interpreter.interpret(StrumReading(4, true, 1, strings(0f, 0f, 0f, nan, nan, nan)), options)
        assertEquals(StrumPhase.RINGING, half.phase)
        assertEquals(StrumVerdict.IN_TUNE, half.strings[1].verdict)
    }

    @Test
    fun `precise asks for 2 cents, and a less sure string gets a little more room`() {
        val precise = options.copy(inTunePrecision = InTunePrecision.PRECISE)
        val state = StrumCheckInterpreter().interpret(StrumReading(1, true, 1, strings(3f, 1.5f, 0f, 0f, 4f, -6f, shadowed = setOf(4, 5))), precise)
        assertEquals(2f, state.toleranceCents)
        assertEquals(StrumVerdict.SHARP, state.strings[0].verdict)
        assertEquals(StrumVerdict.IN_TUNE, state.strings[1].verdict)
        // 2 + 2.5 cents for the shadowed B3 and E4.
        assertEquals(StrumVerdict.IN_TUNE, state.strings[4].verdict)
        assertFalse(state.strings[4].sure)
        assertEquals(StrumVerdict.FLAT, state.strings[5].verdict)
        assertTrue(state.strings[0].sure)
    }

    @Test
    fun `starting over clears the strings, and the state follows the notation`() {
        val interpreter = StrumCheckInterpreter()
        interpreter.interpret(StrumReading(1, true, 1, inTune), options)
        val solfege = options.copy(notation = Notation.SOLFEGE)
        val over = interpreter.interpret(StrumReading(0, ringing = false, readingsSinceStrum = 0), solfege)
        assertEquals(StrumPhase.WAITING, over.phase)
        assertTrue(over.strings.isEmpty())
        assertEquals(Notation.SOLFEGE, over.notation)
    }
}
