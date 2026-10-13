package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneGlowRuleTest {

    private val rule = InTuneGlowRule()

    @Test
    fun `lights when the note becomes in tune`() {
        assertFalse(rule.update(false, "A2", 0))
        assertTrue(rule.update(true, "A2", 100))
    }

    @Test
    fun `lights on the same update that ticks`() {
        val tick = InTuneTickRule()
        val frames = listOf(false to "A2", true to "A2", true to "A2", false to null, false to "D3", true to "D3")
        frames.forEachIndexed { i, (inTune, target) ->
            val now = i * 1_000L
            val wasLit = rule.lit
            val ticked = tick.update(inTune, target, now)
            val lit = rule.update(inTune, target, now)
            assertEquals("frame $i", ticked, lit && !wasLit)
        }
    }

    @Test
    fun `stays lit while the note holds and never restarts`() {
        rule.update(true, "A2", 0)
        for (now in 20L..10_000L step 20L) {
            assertTrue(rule.update(true, "A2", now))
            assertEquals(Long.MAX_VALUE, rule.holdEndsAtMillis)
        }
    }

    @Test
    fun `goes out when the same note drifts out of tune`() {
        rule.update(true, "A2", 0)
        assertFalse(rule.update(false, "A2", 2_000))
    }

    @Test
    fun `goes out at once for another note`() {
        rule.update(true, "A2", 0)
        assertFalse(rule.update(false, "D3", 2_000))
    }

    @Test
    fun `holds through a brief dropout of the same note`() {
        rule.update(true, "A2", 0)
        // The reading is lost for 200 ms, then the note is back and earns "in tune" in 300 ms.
        assertTrue(rule.update(false, null, 1_000))
        assertEquals(1_000L + InTuneGlowRule.DEFAULT_HOLD_MILLIS, rule.holdEndsAtMillis)
        assertTrue(rule.update(false, "A2", 1_200))
        assertTrue(rule.update(false, "A2", 1_350))
        assertTrue(rule.update(true, "A2", 1_500))
        assertEquals(Long.MAX_VALUE, rule.holdEndsAtMillis)
    }

    @Test
    fun `the same note back but asking to be tuned puts it out at once`() {
        rule.update(true, "A2", 0)
        rule.update(false, null, 1_000)
        // Back sharp: still held while the hint has no direction yet, out once it says "Tune down".
        assertTrue(rule.update(false, "A2", 1_100))
        assertFalse(rule.update(false, "A2", 1_300, retuning = true))
        assertEquals(Long.MAX_VALUE, rule.holdEndsAtMillis)
        // And it stays out until the note is in tune again.
        assertFalse(rule.update(false, "A2", 1_400))
        assertTrue(rule.update(true, "A2", 2_000))
    }

    @Test
    fun `asking to be tuned during a lost reading changes nothing`() {
        rule.update(true, "A2", 0)
        assertTrue(rule.update(false, null, 1_000, retuning = true))
    }

    @Test
    fun `another note during a dropout puts it out`() {
        rule.update(true, "A2", 0)
        rule.update(false, null, 1_000)
        assertFalse(rule.update(false, "D3", 1_100))
    }

    @Test
    fun `goes out when the hold ends without the note`() {
        rule.update(true, "A2", 0)
        rule.update(false, null, 1_000)
        assertTrue(rule.update(false, null, 1_000 + InTuneGlowRule.DEFAULT_HOLD_MILLIS - 1))
        assertFalse(rule.update(false, null, rule.holdEndsAtMillis))
        assertEquals(Long.MAX_VALUE, rule.holdEndsAtMillis)
    }

    @Test
    fun `goes out when the same note comes back too late to earn in tune`() {
        rule.update(true, "A2", 0)
        rule.update(false, null, 1_000)
        rule.update(false, "A2", 1_400)
        assertFalse(rule.update(false, "A2", 1_000 + InTuneGlowRule.DEFAULT_HOLD_MILLIS))
        // Once out, the same note lights it afresh when it is in tune again.
        assertTrue(rule.update(true, "A2", 2_000))
    }

    @Test
    fun `the hold counts from when the note went, not from each update`() {
        rule.update(true, "A2", 0)
        rule.update(false, null, 1_000)
        rule.update(false, null, 1_300)
        rule.update(false, null, 1_500)
        assertEquals(1_000L + InTuneGlowRule.DEFAULT_HOLD_MILLIS, rule.holdEndsAtMillis)
    }

    @Test
    fun `a dropout the glow bridges never ticks twice`() {
        val tick = InTuneTickRule()
        tick.update(true, "A2", 0)
        rule.update(true, "A2", 0)
        tick.update(false, null, 1_000)
        rule.update(false, null, 1_000)
        val back = 1_000 + InTuneGlowRule.DEFAULT_HOLD_MILLIS - 1
        assertTrue(rule.update(true, "A2", back))
        assertFalse(tick.update(true, "A2", back))
    }

    @Test
    fun `starting in tune is lit without a new rise`() {
        val resumed = InTuneGlowRule(initiallyInTune = true)
        assertTrue(resumed.lit)
        assertTrue(resumed.update(true, "A2", 0))
    }

    @Test
    fun `never lit without a note`() {
        for (now in 0L..2_000L step 100L) assertFalse(rule.update(false, null, now))
    }
}
