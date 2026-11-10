package io.github.klaboukes.twangle.core.dsp.smoothing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneTickRuleTest {

    private val rule = InTuneTickRule()

    @Test
    fun `ticks once when a note becomes in tune`() {
        assertFalse(rule.update(false, "A2", 0))
        assertTrue(rule.update(true, "A2", 100))
        assertFalse(rule.update(true, "A2", 120))
        assertFalse(rule.update(true, "A2", 5_000))
    }

    // Until Phase 89 the first in-tune moment measured its quiet time from Long.MIN_VALUE, which overflowed: with no
    // target (null, as no earlier one) it never ticked.
    @Test
    fun `the first in-tune moment ticks whatever the target and the clock`() {
        assertTrue(InTuneTickRule().update(true, null, 1_000))
        assertTrue(InTuneTickRule().update(true, null, 0))
        assertTrue(InTuneTickRule().update(true, "A2", Long.MAX_VALUE))
        assertTrue(InTuneTickRule().update(true, "A2", -5_000))
    }

    @Test
    fun `a short drop-out on the same note doesn't tick again`() {
        assertTrue(rule.update(true, "A2", 0))
        assertFalse(rule.update(false, null, 1_000))
        assertFalse(rule.update(true, "A2", 2_400))
    }

    @Test
    fun `the quiet time counts from when the note was last in tune`() {
        rule.update(true, "A2", 0)
        rule.update(true, "A2", 10_000)
        rule.update(false, null, 10_020)
        assertFalse(rule.update(true, "A2", 11_000))
        rule.update(false, null, 11_020)
        assertTrue(rule.update(true, "A2", 12_600))
    }

    @Test
    fun `the same note after a pause ticks again`() {
        rule.update(true, "A2", 0)
        rule.update(false, null, 20)
        assertTrue(rule.update(true, "A2", 1_600))
    }

    @Test
    fun `another note ticks straight away`() {
        rule.update(true, "A2", 0)
        rule.update(false, null, 20)
        assertTrue(rule.update(true, "D3", 40))
    }

    @Test
    fun `appearing while already in tune doesn't tick`() {
        val restored = InTuneTickRule(initiallyInTune = true)
        assertFalse(restored.update(true, "A2", 0))
    }
    @Test
    fun `with the pill sheen's long quiet time a drifting note shines once`() {
        // A real string drifts a few cents around the note and drops in and out of tune.
        val sheen = InTuneTickRule(quietMillis = 6_000)
        assertTrue(sheen.update(true, "A2", 0))
        var now = 0L
        repeat(10) {
            now += 1_500
            assertFalse(sheen.update(false, "A2", now))
            now += 2_500
            assertFalse("back in tune at $now", sheen.update(true, "A2", now))
        }
        // Another string shines at once; the same one only after 6 s out of tune.
        sheen.update(false, null, now + 500)
        assertTrue(sheen.update(true, "D3", now + 1_000))
        sheen.update(false, "D3", now + 2_000)
        assertTrue(sheen.update(true, "D3", now + 8_000))
    }
}
