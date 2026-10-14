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
        val edge = interpreter.interpret(StrumReading(1, true, 2, strings(0f, 5.6f, 0f, 0f, 0f, 0f)), options)
        assertEquals(StrumVerdict.IN_TUNE, edge.strings[1].verdict)

        val next = interpreter.interpret(StrumReading(2, true, 1, strings(0f, 5.6f, 0f, 0f, 0f, 0f)), options)
        assertEquals(StrumVerdict.SHARP, next.strings[1].verdict)
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
