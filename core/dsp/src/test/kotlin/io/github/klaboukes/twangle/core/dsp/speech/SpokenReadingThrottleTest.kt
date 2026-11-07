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
    fun `another text in the same situation is spoken at most every 2 seconds`() {
        // Listen's tempo, keyed by whether it has one: the number moving is said every 2 s.
        publishAt("Listening, 118 beats a minute", true, 0)
        assertEquals(1_500L, throttle.delayFor("Listening, 120 beats a minute", true, 500))
        assertEquals(500L, throttle.delayFor("Listening, 121 beats a minute", true, 1_500))
        assertEquals(0L, throttle.delayFor("Listening, 121 beats a minute", true, 2_100))
    }

    /**
     * With nothing played, a tip that comes and goes with the room's sounds ("Play a bit louder") is said the first
     * time; the waiting line coming back, and the tip again, are only held for a touch (Phase 81: on the Xiaomi TalkBack
     * said them in turn every few seconds, its own voice clearing the tip it had just said).
     */
    @Test
    fun `a tip flipping with the waiting line is said once`() {
        publishAt("Play a string", null, 0)
        throttle.publish("Play a bit louder", null, 3_000)
        assertTrue("the tip is new", throttle.announced)
        for (now in listOf(6_000L, 9_000L, 12_000L)) {
            throttle.publish("Play a string", null, now)
            assertFalse("Play a string at $now ms", throttle.announced)
            throttle.publish("Play a bit louder", null, now + 1_500)
            assertFalse("the tip again at ${now + 1_500} ms", throttle.announced)
        }
        // Something the player did: said.
        throttle.publish("Play the A string", null, 20_000)
        assertTrue(throttle.announced)
        // A reading is said as ever.
        throttle.publish("E 2, 12 cents flat, Tighten", SpokenReadingKey("E2", SpokenSide.FLAT), 22_000)
        assertTrue(throttle.announced)
    }

    private val eFlat = SpokenReadingKey("E2", SpokenSide.FLAT)
    private val eSharp = SpokenReadingKey("E2", SpokenSide.SHARP)
    private val eInTune = SpokenReadingKey("E2", SpokenSide.IN_TUNE)

    @Test
    fun `a reading's cents moving never change what TalkBack was given`() {
        publishAt("E 2, 12 cents flat, Tighten", eFlat, 0)
        for ((now, cents) in listOf(500L to 11, 2_500L to 14, 10_000L to 9, 60_000L to 3)) {
            assertNull("$cents cents flat at $now ms", throttle.delayFor("E 2, $cents cents flat, Tighten", eFlat, now))
        }
        assertEquals("E 2, 12 cents flat, Tighten", throttle.published)
    }

    @Test
    fun `a tip or the trail's motion coming is a new situation, its cents moving are not`() {
        publishAt("E 2, 12 cents flat, Tighten", eFlat, 0)
        val wavering = eFlat.copy(words = "wavering")
        assertEquals(400L, throttle.delayFor("E 2, 12 cents flat, Tighten, wavering", wavering, 3_000))
        throttle.publish("E 2, 12 cents flat, Tighten, wavering", wavering, 3_400)
        assertNull(throttle.delayFor("E 2, 15 cents flat, Tighten, wavering", wavering, 9_000))
    }

    @Test
    fun `a played note is spoken once it has settled, with its cents of then`() {
        publishAt("Play a string", null, 0)
        // The pluck's first moment, before the word says which way: not said yet.
        val unsettled = SpokenReadingKey("E2", null)
        assertEquals(400L, throttle.delayFor("E 2, 20 cents flat", unsettled, 1_000))
        // The word settles 200 ms in: a new situation, said 400 ms after that, with the cents it has then.
        assertEquals(400L, throttle.delayFor("E 2, 15 cents flat, Tighten", eFlat, 1_200))
        assertEquals(100L, throttle.delayFor("E 2, 13 cents flat, Tighten", eFlat, 1_500))
        throttle.publish("E 2, 12 cents flat, Tighten", eFlat, 1_600)
        assertNull(throttle.delayFor("E 2, 11 cents flat, Tighten", eFlat, 1_700))
    }

    @Test
    fun `in tune, Tighten to Loosen and a new note are each spoken`() {
        publishAt("E 2, 12 cents flat, Tighten", eFlat, 0)
        assertEquals(400L, throttle.delayFor("E 2, in tune", eInTune, 1_000))
        throttle.publish("E 2, in tune", eInTune, 1_400)
        assertNull(throttle.delayFor("E 2, in tune", eInTune, 3_000))
        assertEquals(400L, throttle.delayFor("E 2, 8 cents sharp, Loosen", eSharp, 5_000))
        throttle.publish("E 2, 8 cents sharp, Loosen", eSharp, 5_400)
        assertEquals(400L, throttle.delayFor("E 2, 12 cents flat, Tighten", eFlat, 6_000))
        throttle.publish("E 2, 12 cents flat, Tighten", eFlat, 6_400)
        assertEquals(400L, throttle.delayFor("A 2, 5 cents flat, Tighten", SpokenReadingKey("A2", SpokenSide.FLAT), 7_000))
    }

    @Test
    fun `a side that doesn't last 400 ms isn't spoken, and coming back says nothing new`() {
        publishAt("E 2, 3 cents flat, Tighten", eFlat, 0)
        assertEquals(400L, throttle.delayFor("E 2, 2 cents sharp, Loosen", eSharp, 1_000))
        // Back before it settled: the reading TalkBack has stays.
        assertNull(throttle.delayFor("E 2, 4 cents flat, Tighten", eFlat, 1_200))
        assertEquals("E 2, 3 cents flat, Tighten", throttle.published)
    }

    @Test
    fun `the same note plucked again after Play a string is spoken again`() {
        publishAt("E 2, 12 cents flat, Tighten", eFlat, 0)
        throttle.publish("Play a string", null, 3_000)
        assertEquals(400L, throttle.delayFor("E 2, 9 cents flat, Tighten", eFlat, 4_000))
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
