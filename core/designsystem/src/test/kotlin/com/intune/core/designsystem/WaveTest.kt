package com.intune.core.designsystem

import com.intune.core.designsystem.dial.WAVE_TARGET_CYCLES
import com.intune.core.designsystem.dial.waveAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveTest {

    @Test
    fun `waves fade out at both ends`() {
        assertEquals(0f, waveAt(0f, WAVE_TARGET_CYCLES, 0.3f), 1e-6f)
        assertEquals(0f, waveAt(1f, WAVE_TARGET_CYCLES, 0.3f), 1e-6f)
    }

    @Test
    fun `waves stay within one`() {
        for (i in 0..200) {
            assertTrue(abs(waveAt(i / 200f, WAVE_TARGET_CYCLES * 1.35f, 0.17f)) <= 1f)
        }
    }

    @Test
    fun `a larger phase moves the wave to the right`() {
        // A quarter wave before the middle the wave is at its lowest; moving it a quarter wave to
        // the right brings that low point to the middle.
        val lowBeforeMiddle = waveAt(0.5f - 0.25f / WAVE_TARGET_CYCLES, WAVE_TARGET_CYCLES, 0f)
        assertEquals(lowBeforeMiddle, waveAt(0.5f, WAVE_TARGET_CYCLES, 0.25f), 1e-5f)
    }

    @Test
    fun `whole waves of phase change nothing`() {
        assertEquals(waveAt(0.4f, 3f, 0.2f), waveAt(0.4f, 3f, 1.2f), 1e-5f)
    }
}
