package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class IntonationInterpreterTest {

    private val frameSeconds = 1024f / 48_000f
    private val interpreter = IntonationInterpreter(frameSeconds)
    private var options = TunerOptions(
        tuning = Tunings.STANDARD,
        intonationCheck = true,
        intonationString = 1,
        intonationAttempt = 1,
        intonationScaleMm = 648f,
    )

    private fun cents(c: Float) = 2.0.pow(c / 1200.0).toFloat()

    private fun play(hz: Float, seconds: Float, levelDbfs: Float = -20f): TunerUiState.IntonationCheck {
        var state = interpreter.shown(options)
        repeat((seconds / frameSeconds).toInt()) {
            state = interpreter.interpret(PitchReading(hz, 0.98f, levelDbfs, gateOpen = true), options)
        }
        return state
    }

    private fun silence(seconds: Float): TunerUiState.IntonationCheck {
        var state = interpreter.shown(options)
        repeat((seconds / frameSeconds).toInt()) {
            state = interpreter.interpret(PitchReading(0f, 0f, -90f, gateOpen = false), options)
        }
        return state
    }

    @Test
    fun `no string chosen asks for one`() {
        options = options.copy(intonationString = null)
        val state = interpreter.shown(options)
        assertEquals(IntonationStep.CHOOSE, state.step)
        assertNull(state.string)
        assertEquals(List(6) { null }, state.results)
        assertEquals(IntonationStep.CHOOSE, play(110f, 2f).step)
    }

    @Test
    fun `open, then the 12th fret 8 cents sharp, says move the saddle back`() {
        assertEquals(IntonationStep.OPEN, interpreter.shown(options).step)
        // Settling plus a second of readings takes about 1.3 s.
        val partway = play(110f, 0.8f)
        assertEquals(IntonationStep.OPEN, partway.step)
        assertTrue("progress ${partway.progress}", partway.progress in 0.1f..0.9f)
        assertEquals(IntonationStep.FRET, play(110f, 0.8f).step)

        val verdict = play(220f * cents(8f), 1.6f)
        assertEquals(IntonationStep.VERDICT, verdict.step)
        val result = verdict.result!!
        assertEquals(IntonationVerdict.MOVE_SADDLE_BACK, result.verdict)
        assertEquals(8f, result.offsetCents, 0.05f)
        assertSame(result, verdict.results[1])
        assertEquals(648f, verdict.scaleLengthMm)
        // The verdict stays while the string rings on.
        assertEquals(IntonationStep.VERDICT, play(220f, 2f).step)
    }

    @Test
    fun `flat and fine verdicts`() {
        play(110f, 1.6f)
        assertEquals(IntonationVerdict.MOVE_SADDLE_FORWARD, play(220f * cents(-6f), 1.6f).result?.verdict)
        options = options.copy(intonationAttempt = 2)
        play(110f, 1.6f)
        assertEquals(IntonationVerdict.FINE, play(220f * cents(2f), 1.6f).result?.verdict)
    }

    @Test
    fun `the open string still ringing doesn't count as the fret`() {
        assertEquals(IntonationStep.FRET, play(110f, 1.6f).step)
        val ringing = play(110f * cents(-3f), 3f)
        assertEquals(IntonationStep.FRET, ringing.step)
        assertEquals(0f, ringing.progress, 0f)
    }

    @Test
    fun `an open note too far from the string asks to tune it first, then takes the tuned one`() {
        val off = play(110f * cents(-25f), 1.6f)
        assertEquals(IntonationStep.OPEN, off.step)
        assertEquals(IntonationSnag.TUNE_FIRST, off.snag)
        assertEquals(-25, off.tuneFirstCents)
        // Still off: still asked.
        assertEquals(IntonationSnag.TUNE_FIRST, play(110f * cents(-25f), 1.6f).snag)
        val tuned = play(110f * cents(4f), 1.6f)
        assertEquals(IntonationStep.FRET, tuned.step)
        assertEquals(IntonationSnag.NONE, tuned.snag)
    }

    @Test
    fun `a wrong fret isn't the octave and waits for the 12th fret again`() {
        play(110f, 1.6f)
        // The 11th fret: a semitone under the octave.
        val wrong = play(220f * cents(-100f), 1.6f)
        assertEquals(IntonationStep.FRET, wrong.step)
        assertEquals(IntonationSnag.NOT_THE_OCTAVE, wrong.snag)
        assertNull(wrong.results[1])
        val right = play(220f * cents(1f), 1.6f)
        assertEquals(IntonationStep.VERDICT, right.step)
        assertEquals(IntonationSnag.NONE, right.snag)
        assertEquals(IntonationVerdict.FINE, right.result?.verdict)
    }

    @Test
    fun `an open string heard an octave up is still the open string, and its ringing isn't the fret`() {
        // A weak fundamental: the G3 string read as G4.
        options = options.copy(intonationString = 3)
        val g3 = 196f
        assertEquals(IntonationStep.FRET, play(g3 * 2, 1.6f).step)
        // Ringing on at G4, it isn't taken for the 12th fret.
        assertEquals(IntonationStep.FRET, play(g3 * 2, 2f).step)
        // A new pluck (here after a moment's silence) at the fret is.
        silence(0.2f)
        val verdict = play(g3 * 2 * cents(5f), 1.6f)
        assertEquals(IntonationStep.VERDICT, verdict.step)
        assertEquals(5f, verdict.result!!.offsetCents, 0.05f)
    }

    @Test
    fun `a new attempt or another string starts at the open note, and the results stay`() {
        play(110f, 1.6f)
        play(220f * cents(8f), 1.6f)
        options = options.copy(intonationString = 2, intonationAttempt = 2)
        val next = interpreter.shown(options)
        assertEquals(IntonationStep.OPEN, next.step)
        assertEquals(2, next.string)
        assertEquals(IntonationVerdict.MOVE_SADDLE_BACK, next.results[1]?.verdict)
        options = options.copy(intonationAttempt = 3)
        assertEquals(IntonationStep.OPEN, interpreter.shown(options).step)
    }

    @Test
    fun `a new tuning clears the results`() {
        play(110f, 1.6f)
        play(220f, 1.6f)
        options = options.copy(tuning = Tunings.DROP_D)
        val state = interpreter.shown(options)
        assertEquals(List(6) { null }, state.results)
    }

    @Test
    fun `the results outlive the interpreter through the memory the ViewModel keeps`() {
        val memory = IntonationResults()
        val first = IntonationInterpreter(frameSeconds, memory)
        repeat(80) { first.interpret(PitchReading(110f, 0.98f, -20f, true), options) }
        repeat(80) { first.interpret(PitchReading(220f, 0.98f, -20f, true), options) }
        val second = IntonationInterpreter(frameSeconds, memory)
        assertEquals(IntonationVerdict.FINE, second.shown(options).results[1]?.verdict)
    }

    @Test
    fun `the same state is handed on while nothing it shows changes`() {
        options = options.copy(intonationString = null)
        val a = interpreter.shown(options)
        val b = interpreter.interpret(PitchReading(110f, 0.98f, -20f, true), options)
        assertSame(a, b)
    }

    @Test
    fun `the tools offered follow the switches and the instrument`() {
        assertEquals(listOf(TunerTool.INTONATION_CHECK), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.GUITAR))
        assertEquals(listOf(TunerTool.INTONATION_CHECK), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.UKULELE))
        // Basses aren't strummed in chords; the violin has neither frets nor chords.
        assertEquals(listOf(TunerTool.INTONATION_CHECK), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.BASS_4))
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.VIOLIN))
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_OFF, Instruments.GUITAR))
        // The 12-string's octave strings blur a strum and a chord: only the 12th-fret check.
        assertEquals(listOf(TunerTool.INTONATION_CHECK), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.GUITAR_12))
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_OFF, Instruments.GUITAR_12))
        // The banjo: a strum and a chord read well; no 12th-fret check (its short string's octave is at the 17th).
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.BANJO_5))
        // The 12th-fret check also needs the player's Settings switch for its way in (2.0, Phase 71).
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.GUITAR, showIntonationCheck = false))
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_ON, Instruments.GUITAR_12, showIntonationCheck = false))
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_OFF, Instruments.GUITAR, showIntonationCheck = true))
        // Each tool follows its own switch.
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_ON.copy(intonationCheck = false), Instruments.GUITAR))
        // The strum check's and the chord name's switches are brakes, no tools (Phase 74).
        assertEquals(listOf(TunerTool.INTONATION_CHECK), tunerToolsFor(ToolSwitches.ALL_ON.copy(chordNaming = false, strumCheck = false), Instruments.GUITAR))
        // The pitch trail is no tool since 2.0's Phase 71 (the trail is the lens's look): its switch offers nothing here.
        assertEquals(tunerToolsFor(ToolSwitches.ALL_ON, Instruments.GUITAR), tunerToolsFor(ToolSwitches.ALL_ON.copy(pitchTrail = false), Instruments.GUITAR))
        assertEquals(emptyList<TunerTool>(), tunerToolsFor(ToolSwitches.ALL_OFF.copy(pitchTrail = true), Instruments.VIOLIN))
    }

    @Test
    fun `the check runs in guitar mode only`() {
        assertTrue(options.intonationChecking)
        assertTrue(!options.copy(mode = TunerMode.CHROMATIC).intonationChecking)
    }
}
