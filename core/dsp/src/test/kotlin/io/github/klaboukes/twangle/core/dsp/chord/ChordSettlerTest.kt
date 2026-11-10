package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordSettlerTest {

    private val am = Chord.of(PitchClass.A, ChordQuality.MINOR)
    private val c = Chord.of(PitchClass.C, ChordQuality.MAJOR)
    private val g = Chord.of(PitchClass.G, ChordQuality.MAJOR)

    private val settler = ChordSettler()
    private var t = 0.0
    private var strums = 0
    private var ringing = false

    /** A new strum, then 0.4 s until its first reading (StrumListener's timing). */
    private fun strum() {
        strums++
        ringing = true
        settler.update(t, strums, ringing)
        wait(0.4)
    }

    /** One reading every 0.1 s, one per entry ([NO] for a reading without a chord). */
    private fun readings(vararg chords: Chord?, bass: PitchClass? = null) {
        for (chord in chords) {
            settler.update(t, strums, ringing, reading = true, chord = chord, bass = bass ?: chord?.root)
            t += 0.1
        }
    }

    /** Time passes with no reading (the status every 0.1 s). */
    private fun wait(seconds: Double) {
        val until = t + seconds
        while (t < until - 1e-9) {
            settler.update(t, strums, ringing)
            t += 0.1
        }
    }

    private fun ringOut() {
        ringing = false
        settler.update(t, strums, ringing)
    }

    @Test
    fun `waiting until a strum, then listening until a chord wins 4 of the last 6 readings`() {
        settler.update(t, 0, false)
        assertFalse(settler.heardAny)
        assertNull(settler.shown)
        strum()
        assertTrue(settler.heardAny)
        assertTrue(settler.listening)
        readings(am, c, am, am)
        assertNull("3 of 4 is not yet 4", settler.shown)
        readings(am)
        assertEquals(SettledChord(am, am.root), settler.shown)
        assertFalse(settler.listening)
        assertFalse(settler.faded)
        assertEquals(listOf(SettledChord(am, am.root)), settler.recent)
    }

    @Test
    fun `odd readings and readings without a chord don't change the name`() {
        strum()
        readings(am, am, am, am)
        readings(c, null, am, c, null, null, c)
        assertEquals(SettledChord(am, am.root), settler.shown)
        assertFalse(settler.faded)
    }

    @Test
    fun `as the strum dies away the chord stays, faded`() {
        strum()
        readings(am, am, am, am, null, null)
        ringOut()
        wait(5.0)
        assertEquals(SettledChord(am, am.root), settler.shown)
        assertTrue(settler.faded)
        assertFalse(settler.unclear)
    }

    @Test
    fun `a new strum keeps the last chord faded until its own name wins`() {
        strum()
        readings(am, am, am, am)
        ringOut()
        wait(1.0)
        strum()
        assertEquals(SettledChord(am, am.root), settler.shown)
        assertTrue(settler.faded)
        assertTrue(settler.listening)
        readings(c, c, c)
        assertEquals(SettledChord(am, am.root), settler.shown)
        readings(c)
        assertEquals(SettledChord(c, c.root), settler.shown)
        assertFalse(settler.faded)
        assertEquals(listOf(SettledChord(am, am.root), SettledChord(c, c.root)), settler.recent)
    }

    @Test
    fun `the same chord strummed again brightens without a change`() {
        strum()
        readings(am, am, am, am)
        ringOut()
        strum()
        readings(am, am, am, am)
        assertEquals(SettledChord(am, am.root), settler.shown)
        assertFalse(settler.faded)
        assertEquals(listOf(SettledChord(am, am.root)), settler.recent)
    }

    @Test
    fun `a name stays at least 1_5 s before another replaces it`() {
        strum()
        readings(am, am, am, am)
        val shownAt = t - 0.1
        // Straight away another strum, of C.
        strum()
        readings(c, c, c, c)
        assertEquals("only ${t - shownAt} s", SettledChord(am, am.root), settler.shown)
        wait(ChordSettler.HOLD_SECONDS)
        assertEquals(SettledChord(c, c.root), settler.shown)
    }

    @Test
    fun `within one strum a chord that wins 4 of 6 replaces the one shown, after the hold`() {
        strum()
        readings(am, am, am, am)
        readings(c, c, c, c, c, c, c, c, c, c, c, c, c, c, c, c)
        assertEquals(SettledChord(c, c.root), settler.shown)
    }

    @Test
    fun `a strum that names nothing for 1_5 s is not a clear chord, for at least 2 s`() {
        strum()
        readings(null, c, null, am, null, null, g, null, null, null, null)
        assertTrue(settler.listening)
        assertFalse(settler.unclear)
        wait(0.2)
        assertTrue(settler.unclear)
        assertFalse(settler.listening)
        assertNull(settler.shown)
        // A clear strum straight after: its name waits until the message has shown 2 s.
        strum()
        readings(am, am, am, am)
        assertTrue(settler.unclear)
        wait(1.2)
        assertFalse(settler.unclear)
        assertEquals(SettledChord(am, am.root), settler.shown)
    }

    @Test
    fun `after a chord, an unclear strum says so and keeps the chord in recent`() {
        strum()
        readings(am, am, am, am)
        ringOut()
        wait(2.0)
        strum()
        readings(null, null, null, null, null, null, null, null, null, null, null, null)
        wait(0.2)
        assertTrue(settler.unclear)
        assertNull(settler.shown)
        assertEquals(listOf(SettledChord(am, am.root)), settler.recent)
    }

    @Test
    fun `a knock too short for a reading changes nothing`() {
        strum()
        readings(am, am, am, am)
        ringOut()
        strums++
        settler.update(t, strums, ringing = true)
        t += 0.1
        settler.update(t, strums, ringing = false)
        wait(3.0)
        assertEquals(SettledChord(am, am.root), settler.shown)
        assertFalse(settler.unclear)
        assertFalse(settler.listening)
    }

    @Test
    fun `the bass joins the name only when it agrees in 4 of 6 too, and makes a slash chord only when it isn't the root`() {
        strum()
        readings(c, c, c, c, bass = PitchClass.E)
        assertEquals(SettledChord(c, PitchClass.E), settler.shown)
        assertTrue(settler.shown!!.isSlash)

        val other = ChordSettler()
        other.update(0.0, 1, true)
        val basses = listOf(PitchClass.E, PitchClass.C, PitchClass.E, PitchClass.C, PitchClass.E, PitchClass.C)
        basses.forEachIndexed { i, bass -> other.update(0.4 + i * 0.1, 1, true, reading = true, chord = c, bass = bass) }
        assertEquals("no sure bass: the chord alone", SettledChord(c), other.shown)

        val root = ChordSettler()
        root.update(0.0, 1, true)
        repeat(4) { root.update(0.4 + it * 0.1, 1, true, reading = true, chord = c, bass = PitchClass.C) }
        assertEquals(SettledChord(c, PitchClass.C), root.shown)
        assertFalse(root.shown!!.isSlash)
    }

    @Test
    fun `recent keeps the last three chords`() {
        for (chord in listOf(am, c, g, am)) {
            strum()
            readings(chord, chord, chord, chord)
            wait(ChordSettler.HOLD_SECONDS)
            ringOut()
        }
        assertEquals(listOf(SettledChord(c, c.root), SettledChord(g, g.root), SettledChord(am, am.root)), settler.recent)
    }

    @Test
    fun `a listener started over starts it over`() {
        strum()
        readings(am, am, am, am)
        settler.update(t, 0, false)
        assertNull(settler.shown)
        assertFalse(settler.heardAny)
        assertTrue(settler.recent.isEmpty())
    }

    // Until Phase 89 reset() kept when the last chord was shown: on a clock started over, a strum that named nothing
    // waited that long before it said so.
    @Test
    fun `reset forgets when the last chord was shown`() {
        wait(10.0)
        strum()
        readings(am, am, am, am)
        settler.reset()
        t = 0.0
        strums = 0
        strum()
        readings(null, null, null, null, null, null, null, null, null, null, null)
        wait(0.2)
        assertTrue(settler.unclear)
    }

    @Test
    fun `update says whether anything shown changed`() {
        assertTrue(settler.update(0.0, 1, true))
        assertFalse(settler.update(0.1, 1, true))
        assertFalse(settler.update(0.4, 1, true, reading = true, chord = am, bass = PitchClass.A))
        settler.update(0.5, 1, true, reading = true, chord = am, bass = PitchClass.A)
        settler.update(0.6, 1, true, reading = true, chord = am, bass = PitchClass.A)
        assertTrue(settler.update(0.7, 1, true, reading = true, chord = am, bass = PitchClass.A))
    }

    @Test
    fun `readings that change nothing allocate nothing`() {
        strum()
        readings(am, am, am, am)
        var time = t
        val bytes = allocatedBytes {
            repeat(1_000) {
                settler.update(time, strums, true, reading = true, chord = am, bass = PitchClass.A)
                settler.update(time + 0.05, strums, true)
                time += 0.1
            }
        }
        assertEquals(0L, bytes)
    }
}
