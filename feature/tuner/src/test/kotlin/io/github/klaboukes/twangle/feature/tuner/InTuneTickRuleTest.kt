package io.github.klaboukes.twangle.feature.tuner

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
}
