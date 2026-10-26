package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneSynth
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneRootTest {

    @Test
    fun `the drone's root lies from G sharp 2 to G3`() {
        assertEquals("G♯2", 103.83f, droneRootHz(PitchClass.G_SHARP, 440f), 0.01f)
        assertEquals("A2", 110f, droneRootHz(PitchClass.A, 440f), 0.01f)
        assertEquals("C3", 130.81f, droneRootHz(PitchClass.C, 440f), 0.01f)
        assertEquals("D3", 146.83f, droneRootHz(PitchClass.D, 440f), 0.01f)
        assertEquals("G3", 196f, droneRootHz(PitchClass.G, 440f), 0.01f)
    }

    @Test
    fun `the drone's root follows the A4 setting`() {
        assertEquals(108f, droneRootHz(PitchClass.A, 432f), 0.01f)
        assertEquals(110.5f, droneRootHz(PitchClass.A, 442f), 0.01f)
    }

    @Test
    fun `every root at every A4 the settings allow is one the player takes`() {
        for (a4 in listOf(415f, 440f, 466f)) {
            for (root in PitchClass.entries) {
                val hz = droneRootHz(root, a4)
                assertTrue("$root at $a4: $hz Hz", hz in DroneSynth.MIN_ROOT_HZ..DroneSynth.MAX_ROOT_HZ)
            }
        }
    }

    @Test
    fun `the default choice is the tanpura with the fifth, not lower`() {
        val choice = DroneChoice()
        assertEquals(DroneSound.TANPURA, choice.sound)
        assertEquals(DroneVoicing.FIFTH, choice.voicing)
        assertEquals(false, choice.lower)
    }
}
