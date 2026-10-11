package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuneDirectionTest {

    @Test
    fun `a flat note says tune up`() {
        assertEquals(TuneDirection.UP, tuneDirectionFor(-12f, inTune = false))
        assertEquals(TuneDirection.UP, tuneDirectionFor(-0.6f, inTune = false))
        assertEquals(TuneDirection.UP, tuneDirectionFor(-80f, inTune = false))
    }

    @Test
    fun `a sharp note says tune down`() {
        assertEquals(TuneDirection.DOWN, tuneDirectionFor(12f, inTune = false))
        assertEquals(TuneDirection.DOWN, tuneDirectionFor(0.6f, inTune = false))
        assertEquals(TuneDirection.DOWN, tuneDirectionFor(80f, inTune = false))
    }

    @Test
    fun `nothing once in tune, even a few cents off`() {
        assertNull(tuneDirectionFor(-4f, inTune = true))
        assertNull(tuneDirectionFor(6f, inTune = true))
        assertNull(tuneDirectionFor(0f, inTune = true))
    }

    @Test
    fun `nothing while the reading shows 0 cents`() {
        assertNull(tuneDirectionFor(0f, inTune = false))
        assertNull(tuneDirectionFor(0.4f, inTune = false))
        assertNull(tuneDirectionFor(-0.4f, inTune = false))
    }

    @Test
    fun `nothing for a broken reading`() {
        assertNull(tuneDirectionFor(Float.NaN, inTune = false))
    }
}
