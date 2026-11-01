package io.github.klaboukes.twangle.core.dsp.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenReadingThrottleTest {

    private val throttle = SpokenReadingThrottle(settleMillis = 400, listeningSettleMillis = 1_500, repeatMillis = 2_000)

    private fun publishAt(text: String, key: Any?, now: Long) {
        assertEquals(0L, throttle.delayFor(text, key, now))
        throttle.publish(text, key, now)
    }

    @Test
    fun `the first reading is spoken straight away`() {
        assertEquals(0L, throttle.delayFor("E 2, 3 cents flat", "E2", 0))
    }

    @Test
    fun `the same text again needs nothing`() {
        publishAt("E 2, 3 cents flat", "E2", 0)
        assertNull(throttle.delayFor("E 2, 3 cents flat", "E2", 5_000))
    }

    @Test
    fun `only the cents moving is spoken at most every 2 seconds`() {
        publishAt("E 2, 3 cents flat", "E2", 0)
        assertEquals(1_500L, throttle.delayFor("E 2, 2 cents flat", "E2", 500))
        assertEquals(500L, throttle.delayFor("E 2, 1 cent flat", "E2", 1_500))
        assertEquals(0L, throttle.delayFor("E 2, 1 cent flat", "E2", 2_100))
    }

    @Test
    fun `a new note is spoken once it has lasted 400 ms`() {
        publishAt("E 2, 3 cents flat", "E2", 0)
        assertEquals(400L, throttle.delayFor("A 2, 5 cents sharp", "A2", 100))
        // The cents changing doesn't restart the wait; the note is still new.
        assertEquals(200L, throttle.delayFor("A 2, 4 cents sharp", "A2", 300))
    }

    @Test
    fun `becoming in tune counts as a new situation`() {
        publishAt("E 2, 6 cents flat", "E2/false", 0)
        assertEquals(400L, throttle.delayFor("E 2, in tune", "E2/true", 100))
    }

    @Test
    fun `losing the note is spoken only after 1·5 s, so short drop-outs stay quiet`() {
        publishAt("E 2, in tune", "E2/true", 0)
        assertEquals(1_500L, throttle.delayFor("Play a string", null, 3_000))
        // The note comes back before that: back to the same situation, nothing new to say.
        assertNull(throttle.delayFor("E 2, in tune", "E2/true", 3_200))
    }

    @Test
    fun `turning past the note is a new situation, spoken after 400 ms`() {
        publishAt("E 2, 12 cents flat", "E2/flat", 0)
        assertEquals(400L, throttle.delayFor("E 2, 12 cents sharp", "E2/sharp", 100))
    }

    @Test
    fun `the hint after a pluck dies away is published but not announced again`() {
        publishAt("Play a string", null, 0)
        assertTrue("the first hint is said", throttle.announced)
        throttle.publish("E 2, in tune", "E2/in tune", 1_000)
        assertTrue(throttle.announced)
        throttle.publish("Play a string", null, 4_500)
        assertEquals("Play a string", throttle.published)
        assertFalse("said once is enough", throttle.announced)
        // The next pluck is said as ever.
        throttle.publish("A 2, 5 cents sharp", "A2/sharp", 6_000)
        assertTrue(throttle.announced)
    }

    @Test
    fun `other words with no note are announced`() {
        publishAt("Play a string", null, 0)
        throttle.publish("E 2, in tune", "E2/in tune", 1_000)
        throttle.publish("Play a bit louder", null, 4_500)
        assertTrue("a tip is new", throttle.announced)
        // Straight from one text with no note to another: the player changed something (a string locked).
        throttle.publish("Play the A string", null, 8_000)
        assertTrue(throttle.announced)
    }
}
