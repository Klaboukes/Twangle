package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.level.ReceptionLimit
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.TunerMode
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
        repeat(frames) { state = interpreter.interpret(reading, TunerOptions(mode, standard)) }
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
    fun `a loud clean note is heard well, a quiet or noisy one less so`() {
        val clean = PitchReading(hz(40f), 0.99f, levelDbfs = -25f, gateOpen = true, noiseFloorDbfs = -70f, snrDb = 45f, clarity = 1f)
        assertEquals(1f, play(clean).detected().reception, 1e-4f)
        play(silent)
        val quiet = clean.copy(levelDbfs = -45f, snrDb = 25f)
        assertEquals(1f / 3f, play(quiet, frames = 200).detected().reception, 0.01f)
        play(silent)
        val noisy = clean.copy(snrDb = 4f, clarity = 0.35f)
        assertEquals(0.2f, play(noisy, frames = 200).detected().reception, 0.01f)
        assertEquals(ReceptionLimit.NOISE, interpreter.reception.limit)
    }

    @Test
    fun `reception starts afresh with each note`() {
        val noisy = PitchReading(hz(40f), 0.9f, levelDbfs = -30f, gateOpen = true, snrDb = 2f, clarity = 0.1f)
        play(noisy, frames = 200)
        assertTrue(play(silent) is TunerUiState.Listening)
        val clean = noisy.copy(snrDb = 40f, clarity = 1f)
        assertEquals("not eased from the noisy note", 1f, play(clean, frames = ONSET).detected().reception, 1e-4f)
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
        assertEquals("C♯", state.noteName)
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
        val state = interpreter.interpret(pitched(hz(45.05f)), TunerOptions(TunerMode.CHROMATIC, standard)).detected()

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
        // The reading starts on the onset's last frame, and the 300 ms count with it.
        assertTrue(play(reading, frames = ONSET - 1) is TunerUiState.Listening)
        assertFalse(play(reading, frames = 14).detected().inTune) // 14 × 21.3 ms ≈ 299 ms
        assertTrue(play(reading, frames = 1).detected().inTune)
    }

    @Test
    fun `a note wobbling around the string changes the hint at most once, and in tune agrees`() {
        val options = TunerOptions(TunerMode.AUTO, standard)
        val hints = mutableListOf<TunerHint?>()
        // 1 s at 12 cents flat of A2, then 3 s wobbling ±3 cents around it, 4 times a second.
        for (i in 0 until 188) {
            val t = i * frameSeconds
            val midi = if (i < 47) 44.88f else 45f + 0.03f * kotlin.math.sin(2 * Math.PI * 4 * t).toFloat()
            val shown = interpreter.interpret(pitched(hz(midi)), options)
            if (i < ONSET - 1) {
                // Still confirming the onset.
                assertTrue(shown is TunerUiState.Listening)
                hints += null
                continue
            }
            val state = shown.detected()
            assertEquals(state.hint == TunerHint.IN_TUNE, state.inTune)
            hints += state.hint
        }
        assertEquals(TunerHint.TUNE_UP, hints[46])
        val changes = hints.drop(46).zipWithNext().count { (a, b) -> a != b }
        assertTrue("changed $changes times", changes <= 1)
        assertEquals(TunerHint.IN_TUNE, hints.last())
    }

    @Test
    fun `a note re-plucked sharp after a dropout never shows a tune hint with the in-tune look`() {
        val options = TunerOptions(TunerMode.AUTO, standard)
        val glow = InTuneGlowRule()
        val oldGlow = InTuneGlowRule() // without the hint: what the meter did before
        var tuneDownShown = false
        var oldOverlap = false
        // 1 s in tune on E2, a 107 ms dropout (long enough to lose the note), then the string is
        // plucked again and rings 15 cents sharp, as a fresh pluck often does.
        val readings = List(47) { pitched(hz(40f)) } + List(5) { silent } + List(47) { pitched(hz(40.15f)) }
        readings.forEachIndexed { i, reading ->
            val state = interpreter.interpret(reading, options)
            val detected = state as? TunerUiState.Detected
            val target = detected?.let { it.noteName + it.octave }
            val retuning = detected?.hint?.isDirection == true
            val now = (i * frameSeconds * 1_000).toLong()
            val lit = glow.update(detected?.inTune == true, target, now, retuning)
            val oldLit = oldGlow.update(detected?.inTune == true, target, now)
            if (retuning) {
                assertFalse("frame $i: in tune with ${detected?.hint}", detected!!.inTune)
                assertFalse("frame $i: glow lit with ${detected.hint}", lit)
                tuneDownShown = tuneDownShown || detected.hint == TunerHint.TUNE_DOWN
                oldOverlap = oldOverlap || oldLit
            }
        }
        assertTrue(tuneDownShown)
        assertTrue("the case the fix is for", oldOverlap)
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

    /** Feeds [frames] readings of [hz] with [precision] and returns the last state. */
    private fun playWith(precision: InTunePrecision, hz: Float, frames: Int = 40): TunerUiState.Detected {
        val options = TunerOptions(TunerMode.AUTO, standard, inTunePrecision = precision)
        var state: TunerUiState? = null
        repeat(frames) { state = interpreter.interpret(pitched(hz), options) }
        return state!!.detected()
    }

    @Test
    fun `relaxed counts 4 cents sharp as in tune, with no hint`() {
        val state = playWith(InTunePrecision.RELAXED, hz(45.04f))
        assertEquals(4f, state.cents, 0.3f)
        assertTrue(state.inTune)
        assertEquals(TunerHint.IN_TUNE, state.hint)
    }

    @Test
    fun `precise keeps 4 cents sharp out of tune, and the hint says tune down`() {
        val state = playWith(InTunePrecision.PRECISE, hz(45.04f))
        assertFalse(state.inTune)
        assertEquals(TunerHint.TUNE_DOWN, state.hint)
    }

    @Test
    fun `precise counts 1 cent flat as in tune, with no hint`() {
        val state = playWith(InTunePrecision.PRECISE, hz(44.99f))
        assertTrue(state.inTune)
        assertEquals(TunerHint.IN_TUNE, state.hint)
    }

    @Test
    fun `switching to precise while a note plays applies the new window`() {
        assertTrue(playWith(InTunePrecision.RELAXED, hz(45.04f)).inTune)
        assertFalse(playWith(InTunePrecision.PRECISE, hz(45.04f)).inTune)
        // Back to relaxed, the note earns "in tune" again.
        assertTrue(playWith(InTunePrecision.RELAXED, hz(45.04f)).inTune)
    }

    @Test
    fun `a tick's frames neither show a note nor end the one shown`() {
        // A tick ringing at 1000 Hz for one frame, then the frames in which it has died away.
        val tick = pitched(1000f)
        val passed = PitchReading(0f, confidence = 0f, levelDbfs = -40f, gateOpen = true, sustained = false)
        repeat(3) {
            assertTrue(play(tick, frames = 1) is TunerUiState.Listening)
            assertTrue(play(passed, frames = 3) is TunerUiState.Listening)
        }
        // Over a ringing E2: hidden under the tick for longer than a dropout may last, still shown.
        play(pitched(hz(40f)))
        val hidden = PitchReading(0f, confidence = 0f, levelDbfs = -30f, gateOpen = true, sustained = false)
        assertEquals("E", play(hidden, frames = 8).detected().noteName)
    }

    @Test
    fun `silence ends in tune`() {
        play(pitched(hz(45f)))
        play(silent, frames = 5)
        assertFalse(play(pitched(hz(45f)), frames = ONSET).detected().inTune)
    }

    @Test
    fun `notes are measured against the calibrated A4`() {
        val a442 = TunerOptions(TunerMode.CHROMATIC, standard, a4Hz = 442f)
        var state: TunerUiState? = null
        // 442 Hz is A4 exactly once A4 is calibrated to 442 Hz, but 7.85 cents sharp at 440 Hz.
        repeat(40) { state = interpreter.interpret(pitched(442f), a442) }

        val detected = state!!.detected()
        assertEquals("A", detected.noteName)
        assertEquals(0f, detected.cents, 0.1f)
        assertEquals(442f, detected.a4Hz)
        assertEquals(442f, detected.frequencyHz, 0.05f)
    }

    @Test
    fun `changing A4 while a note plays re-measures it`() {
        val before = play(pitched(442f), TunerMode.CHROMATIC).detected()
        assertEquals(7.85f, before.cents, 0.2f)

        var state: TunerUiState? = null
        repeat(40) { state = interpreter.interpret(pitched(442f), TunerOptions(TunerMode.CHROMATIC, standard, a4Hz = 442f)) }
        assertEquals(0f, state!!.detected().cents, 0.1f)
    }

    @Test
    fun `listening reports the A4 in use`() {
        val state = interpreter.interpret(silent, TunerOptions(TunerMode.AUTO, standard, a4Hz = 432f))
        assertEquals(TunerUiState.Listening(TunerMode.AUTO, standard, a4Hz = 432f), state)
    }

    @Test
    fun `a locked string is the target even when another string is closer`() {
        // E2 is played but A2 is locked: the reading shows the 5 semitones to A2, not the nearby E2.
        val locked = TunerOptions(TunerMode.AUTO, standard, lockedString = 1)
        var state: TunerUiState? = null
        repeat(40) { state = interpreter.interpret(pitched(hz(40f)), locked) }

        val detected = state!!.detected()
        assertEquals(1, detected.targetString)
        assertEquals(1, detected.lockedString)
        assertEquals("A", detected.noteName)
        assertEquals(-500f, detected.cents, 0.5f)
    }

    @Test
    fun `releasing the lock goes back to the nearest string`() {
        repeat(40) { interpreter.interpret(pitched(hz(40f)), TunerOptions(TunerMode.AUTO, standard, lockedString = 1)) }
        val state = play(pitched(hz(40f)), TunerMode.AUTO).detected()

        assertEquals(0, state.targetString)
        assertNull(state.lockedString)
        assertEquals(0f, state.cents, 0.5f)
    }

    @Test
    fun `listening shows the locked string`() {
        val state = interpreter.interpret(silent, TunerOptions(TunerMode.AUTO, standard, lockedString = 3))
        assertEquals(TunerUiState.Listening(TunerMode.AUTO, standard, lockedString = 3), state)
    }

    @Test
    fun `the lock is ignored in chromatic mode and when the string doesn't exist`() {
        val chromatic = TunerOptions(TunerMode.CHROMATIC, standard, lockedString = 1)
        var state: TunerUiState? = null
        repeat(40) { state = interpreter.interpret(pitched(hz(40f)), chromatic) }
        assertEquals("E", state!!.detected().noteName)
        assertNull(state!!.detected().lockedString)

        repeat(40) { state = interpreter.interpret(pitched(hz(40f)), TunerOptions(TunerMode.AUTO, standard, lockedString = 9)) }
        assertEquals(0, state!!.detected().targetString)
        assertNull(state!!.detected().lockedString)
    }

    @Test
    fun `note names follow the notation`() {
        fun nameOf(notation: Notation): String {
            var state: TunerUiState? = null
            repeat(40) { state = interpreter.interpret(pitched(hz(61f)), TunerOptions(TunerMode.CHROMATIC, standard, notation = notation)) }
            return state!!.detected().noteName
        }
        assertEquals("C♯", nameOf(Notation.SHARPS))
        assertEquals("D♭", nameOf(Notation.FLATS))
        assertEquals("Do♯", nameOf(Notation.SOLFEGE))
    }

    @Test
    fun `guitar mode spells the string the way the tuning does, chromatic follows the setting`() {
        val halfStepDown = Tunings.HALF_STEP_DOWN
        fun nameOf(mode: TunerMode, notation: Notation): String {
            val tuner = TunerInterpreter(frameSeconds)
            var state: TunerUiState? = null
            // 63 = E♭4 / D♯4, the top string of Half step down.
            repeat(40) { state = tuner.interpret(pitched(hz(63f)), TunerOptions(mode, halfStepDown, notation = notation)) }
            return state!!.detected().noteName
        }
        assertEquals("E♭", nameOf(TunerMode.AUTO, Notation.SHARPS))
        assertEquals("Mi♭", nameOf(TunerMode.AUTO, Notation.SOLFEGE))
        assertEquals("D♯", nameOf(TunerMode.CHROMATIC, Notation.SHARPS))
        assertEquals("Re♯", nameOf(TunerMode.CHROMATIC, Notation.SOLFEGE))
        assertEquals("E♭", nameOf(TunerMode.CHROMATIC, Notation.FLATS))
    }

    private companion object {
        /** Frames before a new note shows. */
        const val ONSET = PitchSmoother.DEFAULT_ONSET_FRAMES
    }
}
