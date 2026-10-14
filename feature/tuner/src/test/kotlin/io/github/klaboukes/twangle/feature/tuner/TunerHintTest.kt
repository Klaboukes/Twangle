package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TunerHintTest {

    // tunerHintFor: the wanted hint for one reading.

    @Test
    fun `a flat note says tune up`() {
        assertEquals(TunerHint.TUNE_UP, tunerHintFor(-12f, inTune = false))
        assertEquals(TunerHint.TUNE_UP, tunerHintFor(-0.6f, inTune = false))
        assertEquals(TunerHint.TUNE_UP, tunerHintFor(-80f, inTune = false))
    }

    @Test
    fun `a sharp note says tune down`() {
        assertEquals(TunerHint.TUNE_DOWN, tunerHintFor(12f, inTune = false))
        assertEquals(TunerHint.TUNE_DOWN, tunerHintFor(0.6f, inTune = false))
        assertEquals(TunerHint.TUNE_DOWN, tunerHintFor(80f, inTune = false))
    }

    @Test
    fun `in tune says in tune, even a few cents off`() {
        assertEquals(TunerHint.IN_TUNE, tunerHintFor(-4f, inTune = true))
        assertEquals(TunerHint.IN_TUNE, tunerHintFor(6f, inTune = true, previous = TunerHint.TUNE_DOWN))
    }

    @Test
    fun `no direction while the reading shows 0 cents and none was given before`() {
        assertNull(tunerHintFor(0f, inTune = false))
        assertNull(tunerHintFor(0.4f, inTune = false))
        assertNull(tunerHintFor(-0.4f, inTune = false))
        assertNull(tunerHintFor(Float.NaN, inTune = false))
    }

    @Test
    fun `within the margin the previous direction is kept`() {
        assertEquals(TunerHint.TUNE_UP, tunerHintFor(3f, false, TunerHint.TUNE_UP, marginCents = 5f))
        assertEquals(TunerHint.TUNE_DOWN, tunerHintFor(-5f, false, TunerHint.TUNE_DOWN, marginCents = 5f))
        assertEquals(TunerHint.TUNE_UP, tunerHintFor(0.4f, false, TunerHint.TUNE_UP))
        // Past the margin it follows the reading again.
        assertEquals(TunerHint.TUNE_DOWN, tunerHintFor(5.2f, false, TunerHint.TUNE_UP, marginCents = 5f))
        // "In tune" is no direction to keep.
        assertNull(tunerHintFor(3f, false, TunerHint.IN_TUNE, marginCents = 5f))
    }

    // The tuning cue through the steady hint line, as the tuner shows it.

    /** One hop at 48 kHz, as on the phone. */
    private val frame = 1024f / 48_000f

    private var cue = TuningCue()
    private val line = SteadyHint<TunerHint>()
    private val shownHints = mutableListOf<TunerHint?>()

    /** Feeds [cents] (a function of the time in seconds) for [seconds] and records what shows. */
    private fun play(seconds: Float, cents: (Float) -> Float) {
        val frames = (seconds / frame).toInt()
        for (i in 0 until frames) shownHints += line.update(cue.update(cents(i * frame), frame), frame)
    }

    /** How often the shown hint changed from frame [from] on. */
    private fun changesFrom(from: Int): Int =
        shownHints.drop(from).zipWithNext().count { (a, b) -> a != b }

    private fun wobble(centre: Float, depth: Float, hz: Float): (Float) -> Float =
        { t -> centre + depth * sin(2 * PI * hz * t).toFloat() }

    @Test
    fun `a reading wobbling across the target changes the hint at most once`() {
        play(1f) { -12f }
        assertEquals(TunerHint.TUNE_UP, shownHints.last())
        val from = shownHints.lastIndex
        play(3f, wobble(centre = 0f, depth = 3f, hz = 4f))
        assertTrue("changed ${changesFrom(from)} times", changesFrom(from) <= 1)
        assertEquals(TunerHint.IN_TUNE, shownHints.last())
    }

    @Test
    fun `the old instant hint flickered on that reading`() {
        // Before the steady line the hint was the plain direction of each frame.
        val detector = InTuneDetector()
        val reading = wobble(0f, 3f, 4f)
        var last: TunerHint? = TunerHint.TUNE_UP
        var changes = 0
        // The first 300 ms, before the detector calls it in tune.
        for (i in 0 until (0.3f / frame).toInt()) {
            val cents = reading(i * frame)
            val hint = tunerHintFor(cents, detector.update(cents, frame))
            if (hint != last) changes++
            last = hint
        }
        assertTrue("changed $changes times", changes >= 3)
    }

    @Test
    fun `a reading wobbling around the in-tune exit changes the hint at most once`() {
        play(1f) { 0f }
        assertEquals(TunerHint.IN_TUNE, shownHints.last())
        val from = shownHints.lastIndex
        play(3f, wobble(centre = 7f, depth = 1.5f, hz = 5f))
        assertTrue("changed ${changesFrom(from)} times", changesFrom(from) <= 1)
        assertEquals(TunerHint.TUNE_DOWN, shownHints.last())
    }

    @Test
    fun `precise, a reading wobbling around the in-tune entry changes the hint at most once`() {
        cue = TuningCue(InTuneDetector.forPrecision(InTunePrecision.PRECISE))
        play(3f, wobble(centre = 2f, depth = 1.5f, hz = 3f))
        assertTrue("changed ${changesFrom(0)} times", changesFrom(0) <= 1)
    }

    @Test
    fun `precise, a reading wobbling across the target changes the hint at most once`() {
        cue = TuningCue(InTuneDetector.forPrecision(InTunePrecision.PRECISE))
        play(1f) { -10f }
        val from = shownHints.lastIndex
        play(3f, wobble(centre = 0f, depth = 2.5f, hz = 3f))
        assertTrue("changed ${changesFrom(from)} times", changesFrom(from) <= 1)
    }

    @Test
    fun `a steady note off pitch shows its direction and keeps it`() {
        play(2f) { 9f }
        assertEquals(TunerHint.TUNE_DOWN, shownHints.last())
        assertEquals(1, changesFrom(0))
    }

    @Test
    fun `a note that starts inside the window shows nothing until it is in tune`() {
        play(0.29f) { 3f }
        assertTrue(shownHints.all { it == null })
        play(0.05f) { 3f }
        assertEquals(TunerHint.IN_TUNE, shownHints.last())
    }

    @Test
    fun `tuning past the note shows the new direction`() {
        play(1f) { -12f }
        play(1f) { 12f }
        assertEquals(TunerHint.TUNE_DOWN, shownHints.last())
    }
}
