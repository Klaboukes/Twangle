package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDronePlayerTest {

    private val player = FakeDronePlayer()

    @Test
    fun `records plays and stops and reports playing state`() {
        assertFalse(player.isPlaying.value)
        assertNull(player.lastPlay)

        player.play(146.83f)
        assertTrue(player.isPlaying.value)
        player.play(110f, DroneSound.WARM_PAD, DroneVoicing.FIFTH_OCTAVE, lower = true)
        player.stop()

        assertFalse(player.isPlaying.value)
        assertEquals(
            listOf(
                DronePlay(146.83f, DroneSound.TANPURA, DroneVoicing.FIFTH, lower = false),
                DronePlay(110f, DroneSound.WARM_PAD, DroneVoicing.FIFTH_OCTAVE, lower = true),
            ),
            player.plays,
        )
        assertEquals(DronePlay(110f, DroneSound.WARM_PAD, DroneVoicing.FIFTH_OCTAVE, lower = true), player.lastPlay)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `interrupt stops the drone as a call would`() {
        player.play(196f, DroneSound.HARMONIUM)
        player.interrupt()

        assertFalse(player.isPlaying.value)
        assertEquals(0, player.stopCount)
    }

    @Test
    fun `rejects a root the real player would reject`() {
        assertThrows(IllegalArgumentException::class.java) { player.play(30f) }
        assertThrows(IllegalArgumentException::class.java) { player.play(1_500f) }
        assertFalse(player.isPlaying.value)
    }
}
