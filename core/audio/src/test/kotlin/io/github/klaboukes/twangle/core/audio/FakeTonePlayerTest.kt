package io.github.klaboukes.twangle.core.audio

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

        player.play(82.41f)
        assertTrue(player.isPlaying.value)
        player.play(110f)
        player.stop()

        assertFalse(player.isPlaying.value)
        assertEquals(listOf(82.41f, 110f), player.played)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `finish ends the tone as if it died away`() {
        player.play(440f)
        player.finish()

        assertFalse(player.isPlaying.value)
        assertEquals(0, player.stopCount)
    }

    @Test
    fun `rejects a frequency the real player would reject`() {
        assertThrows(IllegalArgumentException::class.java) { player.play(0f) }
        assertFalse(player.isPlaying.value)
    }
}
