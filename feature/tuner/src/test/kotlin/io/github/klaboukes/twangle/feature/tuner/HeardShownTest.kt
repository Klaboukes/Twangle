package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

/**
 * [HeardShown]: how long what the tuner heard besides one string shows, and how it sits with the tuner's own reading
 * (2.0, Phase 74): the note set aside while a strum rings, the marks following the strings tuned after it,
 * everything gone after a quiet spell.
 */
class HeardShownTest {

    private val frame = 0.02f
    private val shown = HeardShown(frame)
    private val tuning = Tunings.STANDARD

    /** A2 to tighten and G3 to loosen, the rest in tune. */
    private val verdicts = listOf(
        StrumVerdict.IN_TUNE, StrumVerdict.FLAT, StrumVerdict.IN_TUNE, StrumVerdict.SHARP, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE,
    )

    private fun open(strum: Int, phase: StrumPhase = StrumPhase.RINGING, verdicts: List<StrumVerdict> = this.verdicts) = HeardStrum.OpenStrings(
        TunerUiState.StrumCheck(tuning, phase = phase, strings = verdicts.map { StrumString(it, cents = if (it == StrumVerdict.IN_TUNE) 0f else 20f) }),
        strum,
    )

    private fun listening(mode: TunerMode = TunerMode.AUTO) = TunerUiState.Listening(mode, tuning)

    private fun note(string: Int, inTune: Boolean = false, hint: TunerHint? = null, mode: TunerMode = TunerMode.AUTO) = TunerUiState.Detected(
        mode = mode, tuning = tuning, noteName = "A", octave = 2, frequencyHz = 110f, cents = if (inTune) 1f else -14f,
        targetString = string, inTune = inTune, hint = hint,
    )

    private fun frames(seconds: Float) = ceil(seconds / frame).toInt()

    private fun hold(seconds: Float, state: TunerUiState) {
        repeat(frames(seconds)) { shown.tuner(state) }
    }

    private fun marks() = (shown.shown as HeardStrum.OpenStrings).check.strings.map { it.verdict }

    @Test
    fun `while a strum of the open strings rings the tuner's note is set aside, and after it is not`() {
        assertFalse(shown.setsAside)
        shown.heard(open(1))
        assertTrue(shown.setsAside)
        // A note read meanwhile is one of the strum's: it changes nothing shown.
        shown.tuner(note(1, inTune = true))
        assertEquals(verdicts, marks())

        shown.heard(open(1, StrumPhase.DONE))
        assertFalse(shown.setsAside)
        assertEquals(verdicts, marks())
    }

    @Test
    fun `a string tuned on its own after the strum takes the tuner's verdict, the others keep the strum's`() {
        shown.heard(open(1, StrumPhase.DONE))
        // Not settled yet: no hint, not in tune.
        shown.tuner(note(1))
        assertEquals(verdicts, marks())
        shown.tuner(note(1, hint = TunerHint.TUNE_UP))
        assertEquals(verdicts, marks())
        shown.tuner(note(1, inTune = true, hint = TunerHint.IN_TUNE))
        assertEquals(StrumVerdict.IN_TUNE, marks()[1])
        assertEquals(StrumVerdict.SHARP, marks()[3])
        // A string the strum found in tune, now played off, says so.
        shown.tuner(note(0, hint = TunerHint.TUNE_DOWN))
        assertEquals(StrumVerdict.SHARP, marks()[0])
        shown.tuner(note(3, inTune = true, hint = TunerHint.IN_TUNE))
        assertEquals(listOf(StrumVerdict.SHARP, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE), marks())

        // A pluck after it (the same strum heard again as done) keeps them; a new strum shows its own verdicts.
        shown.heard(open(1, StrumPhase.HEARING))
        assertEquals(StrumVerdict.IN_TUNE, marks()[1])
        shown.heard(open(2))
        assertEquals(verdicts, marks())
    }

    @Test
    fun `in any-note mode a note changes no mark`() {
        shown.heard(open(1, StrumPhase.DONE))
        shown.tuner(note(1, inTune = true, hint = TunerHint.IN_TUNE, mode = TunerMode.CHROMATIC))
        assertEquals(verdicts, marks())
    }

    @Test
    fun `everything goes after a quiet spell, and comes back only with a new strum`() {
        shown.heard(open(1, StrumPhase.DONE))
        hold(HeardShown.QUIET_SECONDS - 1f, listening())
        assertTrue(shown.shown is HeardStrum.OpenStrings)
        // A note heard, or a sound, starts the spell afresh.
        shown.tuner(note(1, hint = TunerHint.TUNE_UP))
        hold(HeardShown.QUIET_SECONDS - 1f, listening())
        shown.heard(open(1, StrumPhase.DONE))
        hold(HeardShown.QUIET_SECONDS - 1f, listening())
        assertTrue(shown.shown is HeardStrum.OpenStrings)
        hold(2f, listening())
        assertSame(HeardStrum.None, shown.shown)

        // The same strum heard of again (a pluck after it) stays gone; a new one shows.
        shown.heard(open(1, StrumPhase.HEARING))
        assertSame(HeardStrum.None, shown.shown)
        shown.heard(open(2))
        assertEquals(verdicts, marks())
    }

    @Test
    fun `the quiet spell doesn't count while a strum rings`() {
        shown.heard(open(1))
        hold(HeardShown.QUIET_SECONDS + 5f, listening())
        assertTrue(shown.shown is HeardStrum.OpenStrings)
    }

    @Test
    fun `nothing heard clears everything at once`() {
        shown.heard(open(1, StrumPhase.DONE))
        shown.tuner(note(1, inTune = true, hint = TunerHint.IN_TUNE))
        assertSame(HeardStrum.None, shown.heard(HeardStrum.None))
        assertFalse(shown.setsAside)
        // A strum after that counts from where the analysis is: its own verdicts, nothing carried over.
        shown.heard(open(3, StrumPhase.DONE))
        assertEquals(verdicts, marks())
        assertSame(HeardStrum.None, shown.reset())
    }

    @Test
    fun `errors and the permission prompt are no reading`() {
        shown.heard(open(1, StrumPhase.DONE))
        hold(HeardShown.QUIET_SECONDS + 1f, TunerUiState.Error(TunerError.MIC_BUSY))
        assertTrue(shown.shown is HeardStrum.OpenStrings)
    }
}
