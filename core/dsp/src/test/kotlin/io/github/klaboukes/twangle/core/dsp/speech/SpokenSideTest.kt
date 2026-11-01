package io.github.klaboukes.twangle.core.dsp.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenSideTest {

    @Test
    fun `below the note is flat, above it sharp`() {
        assertEquals(SpokenSide.FLAT, SpokenSide.of(-12, inTune = false))
        assertEquals(SpokenSide.SHARP, SpokenSide.of(5, inTune = false))
    }

    @Test
    fun `in tune wins over the cents`() {
        assertEquals(SpokenSide.IN_TUNE, SpokenSide.of(-4, inTune = true))
        assertEquals(SpokenSide.IN_TUNE, SpokenSide.of(3, inTune = true))
    }

    @Test
    fun `right on the note before it counts as in tune`() {
        assertEquals(SpokenSide.ON_THE_NOTE, SpokenSide.of(0, inTune = false))
    }
}
