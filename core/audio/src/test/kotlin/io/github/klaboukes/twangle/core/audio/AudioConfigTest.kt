package io.github.klaboukes.twangle.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioConfigTest {

    @Test
    fun `chunks last one 21 ms hop at any rate`() {
        assertEquals(AudioConfig(48_000, 1024), AudioConfig.forSampleRate(48_000))
        assertEquals(941, AudioConfig.forSampleRate(44_100).chunkSize)
        assertEquals(341, AudioConfig.forSampleRate(16_000).chunkSize)
        assertEquals(171, AudioConfig.forSampleRate(8_000).chunkSize)
    }
}
