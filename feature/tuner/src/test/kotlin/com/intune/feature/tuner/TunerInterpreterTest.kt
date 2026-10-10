package com.intune.feature.tuner

import com.intune.core.dsp.music.NoteMapper
import com.intune.core.dsp.music.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TunerInterpreterTest {

    private val frameSeconds = 1024f / 48_000f
    private val interpreter = TunerInterpreter(frameSeconds)
    private val mapper = NoteMapper()
    private val standard = Tunings.STANDARD

    private fun pitched(hz: Float) = PitchReading(hz, confidence = 0.95f, levelDbfs = -30f, gateOpen = true)
    private val silent = PitchReading(0f, confidence = 0f, levelDbfs = -70f, gateOpen = false)

    /** Frequency of a fractional MIDI note, e.g. 40.1 = E2 + 10 cents. */
    private fun hz(midi: Float) = mapper.frequencyOf(midi)

    /** Feeds [frames] identical readings and returns the last state. */
    private fun play(
        reading: PitchReading,
        mode: TunerMode = TunerMode.AUTO,
        frames: Int = 40,
    ): TunerUiState {
        var state: TunerUiState? = null
        repeat(frames) { state = interpreter.interpret(reading, mode, standard) }
        return state!!
    }

    private fun TunerUiState.detected(): TunerUiState.Detected {
        assertTrue("expected Detected, was $this", this is TunerUiState.Detected)
        return this as TunerUiState.Detected
    }

    @Test
    fun `silence is listening`() {
        assertEquals(TunerUiState.Listening(TunerMode.AUTO, standard), play(silent))
    }

    @Test
    fun `chromatic mode shows the nearest note`() {
        val state = play(pitched(hz(69.08f)), TunerMode.CHROMATIC).detected()

        assertEquals("A", state.noteName)
        assertEquals(4, state.octave)
        assertEquals(8f, state.cents, 0.5f)
        assertEquals(hz(69.08f), state.frequencyHz, 0.05f)
        assertNull(state.targetString)
    }

    @Test
    fun `chromatic mode names sharps`() {
        val state = play(pitched(hz(61f)), TunerMode.CHROMATIC).detected()
        assertEquals("C#", state.noteName)
        assertEquals(4, state.octave)
    }

    @Test
    fun `auto mode targets the closest string`() {
        val state = play(pitched(hz(40.12f))).detected() // E2, 12 cents sharp

        assertEquals(0, state.targetString)
        assertEquals("E", state.noteName)
        assertEquals(2, state.octave)
        assertEquals(12f, state.cents, 0.5f)
    }

    @Test
    fun `auto mode shows the full distance to a string tuned far off`() {
        // Low E string slackened down to D2: still string 6, 200 cents flat.
        val state = play(pitched(hz(38f))).detected()

        assertEquals(0, state.targetString)
        assertEquals(-200f, state.cents, 0.5f)
    }

    @Test
    fun `every string of standard tuning is found`() {
        standard.strings.forEachIndexed { index, note ->
            val state = play(pitched(mapper.frequencyOf(note))).detected()
            assertEquals(index, state.targetString)
            assertTrue("string $index off by ${state.cents} c", abs(state.cents) < 0.5f)
        }
    }

    @Test
    fun `the target string is sticky half-way between two strings`() {
        // A2 = 45 and D3 = 50, so 47.5 is exactly half-way.
        assertEquals(1, play(pitched(hz(47.5f))).detected().targetString)
        // Slightly closer to D3, but not by more than 15 cents: stays on A2.
        assertEquals(1, play(pitched(hz(47.55f))).detected().targetString)
        // Clearly closer to D3 now.
        assertEquals(2, play(pitched(hz(47.7f))).detected().targetString)
        // And it stays there on the way back until A2 is clearly closer.
        assertEquals(2, play(pitched(hz(47.45f))).detected().targetString)
        assertEquals(1, play(pitched(hz(47.3f))).detected().targetString)
    }

    @Test
    fun `switching mode keeps the reading`() {
        play(pitched(hz(45.05f)))
        val state = interpreter.interpret(pitched(hz(45.05f)), TunerMode.CHROMATIC, standard).detected()

        assertEquals(TunerMode.CHROMATIC, state.mode)
        assertNull(state.targetString)
        assertEquals(5f, state.cents, 0.5f)
    }

    @Test
    fun `a short dropout keeps the note and a long one clears it`() {
        play(pitched(hz(45f)))
        assertTrue(play(silent, frames = 4) is TunerUiState.Detected)
        assertTrue(play(silent, frames = 1) is TunerUiState.Listening)
    }

    @Test
    fun `a steady note close to the string becomes in tune after 300 ms`() {
        val reading = pitched(hz(40.03f)) // E2, 3 cents sharp
        // The smoother snaps on the first frame, so the 300 ms count starts right away.
        assertFalse(play(reading, frames = 14).detected().inTune) // 14 × 21.3 ms ≈ 299 ms
        assertTrue(play(reading, frames = 1).detected().inTune)
    }

    @Test
    fun `a note 10 cents off is never in tune`() {
        assertFalse(play(pitched(hz(40.1f)), frames = 100).detected().inTune)
    }

    @Test
    fun `a new string has to earn in tune again`() {
        play(pitched(hz(40f)))
        // 3 frames: the median of 5 switches over to the new string.
        val state = play(pitched(hz(45f)), frames = 3).detected()
        assertEquals(1, state.targetString)
        assertFalse(state.inTune)
    }

    @Test
    fun `silence ends in tune`() {
        play(pitched(hz(45f)))
        play(silent, frames = 5)
        assertFalse(interpreter.interpret(pitched(hz(45f)), TunerMode.AUTO, standard).detected().inTune)
    }
}
