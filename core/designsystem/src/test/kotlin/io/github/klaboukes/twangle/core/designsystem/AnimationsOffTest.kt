package io.github.klaboukes.twangle.core.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationsOffTest {

    @Test
    fun `only an animator scale of 0 means Remove animations`() {
        assertTrue(animationsOffAt(0f))
        assertFalse(animationsOffAt(1f))
        assertFalse(animationsOffAt(0.5f))
        assertFalse(animationsOffAt(10f))
    }
}
