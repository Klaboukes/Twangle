package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTonePlayerTest {

    private val player = FakeTonePlayer()

    @Test
    fun `records plays and stops and reports playing state`() {
        assertFalse(player.isPlaying.value)

        player.play(82.41f, StringVoices.STEEL_GUITAR)
        assertTrue(player.isPlaying.value)
        player.play(110f, StringVoices.ELECTRIC_BASS)
        player.stop()

        assertFalse(player.isPlaying.value)
        assertEquals(listOf(82.41f, 110f), player.played)
        assertEquals(listOf(StringVoices.STEEL_GUITAR, StringVoices.ELECTRIC_BASS), player.voices)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `finish ends the tone as if it died away`() {
        player.play(440f, StringVoices.VIOLIN)
        player.finish()

        assertFalse(player.isPlaying.value)
        assertEquals(0, player.stopCount)
    }

    @Test
    fun `rejects a frequency the real player would reject`() {
        assertThrows(IllegalArgumentException::class.java) { player.play(0f, StringVoices.VIOLIN) }
        assertFalse(player.isPlaying.value)
    }
}
