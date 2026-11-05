package io.github.klaboukes.twangle.wear

import org.junit.Assert.assertEquals
import org.junit.Test

/** The bezel or crown turned into whole tempo steps, 1 BPM a click. */
class RotaryStepsTest {

    @Test
    fun `a bezel click is one step, either way`() {
        val steps = RotarySteps(pixelsPerStep = 64f)
        assertEquals(1, steps.of(64f))
        assertEquals(1, steps.of(64f))
        assertEquals(-1, steps.of(-64f))
        assertEquals(3, steps.of(192f))
    }

    @Test
    fun `a crown's small turns add up to a step and keep the rest`() {
        val steps = RotarySteps(pixelsPerStep = 64f)
        val turned = List(10) { steps.of(16f) }
        assertEquals(listOf(0, 0, 0, 1, 0, 0, 0, 1, 0, 0), turned)
    }

    @Test
    fun `turning back starts afresh`() {
        val steps = RotarySteps(pixelsPerStep = 64f)
        assertEquals(0, steps.of(48f))
        assertEquals(0, steps.of(-48f))
        assertEquals(-1, steps.of(-16f))
    }

    @Test
    fun `nothing for no turn or no step size`() {
        assertEquals(0, RotarySteps(64f).of(0f))
        assertEquals(0, RotarySteps(0f).of(100f))
    }
}
