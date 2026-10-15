package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SustainCheckTest {

    private val check = SustainCheck(HOP)

    @Test
    fun `a steady tone is sustained`() {
        for (f in listOf(30f, 82.41f, 329.63f, 1318.5f)) {
            val drop = check.dropOf(TestSignals.sine(f, WINDOW))
            // Blocks a little under a period of 30 Hz hold a varying share of a cycle.
            assertTrue("$f Hz: dropped $drop dB", drop < 2f)
        }
    }

    @Test
    fun `a low note's cycle fits in every block, so even a spiky bass waveform is steady`() {
        // B0 to E1 with a strong 2nd harmonic: its highest peak comes once per 26–32 ms cycle,
        // longer than a hop, so hop-sized blocks missed it and read up to 12 dB down.
        val settings = AnalysisSettings.forSampleRate(RATE, PitchRange.LOW)
        val low = settings.newSustainCheck()
        assertEquals(1715, low.blockSize)
        assertEquals(HOP, AnalysisSettings.forSampleRate(RATE).newSustainCheck().blockSize)
        for (f in listOf(30.87f, 36.71f, 41.2f)) {
            val tone = TestSignals.weakFundamental(f, length = settings.windowSize + 24 * HOP)
            for (end in settings.windowSize..tone.size step HOP) {
                // Peaks match exactly; the energy of 1.1–1.3 cycles varies a little.
                val drop = low.dropOf(tone.copyOfRange(end - settings.windowSize, end))
                assertTrue("$f Hz at $end: dropped $drop dB", drop < SustainCheck.DEFAULT_MAX_DROP_DB / 2)
            }
        }
    }

    @Test
    fun `a note that starts in the newest block passes at once`() {
        val frame = FloatArray(WINDOW)
        TestSignals.pluck(110f, HOP / 2).copyInto(frame, WINDOW - HOP / 2)
        assertEquals(0f, check.dropOf(frame), 0f)
        assertTrue(check.isSustained(frame))
    }

    @Test
    fun `a plucked string is sustained from its attack until it has faded`() {
        // Fast-fading high string and slow low one, over a quiet room; every hop for 3 s.
        for ((f, decay) in listOf(82.41f to 0.8, 329.63f to 0.3, 1318.5f to 0.15)) {
            val pluck = TestSignals.pluck(f, length = 3 * RATE, decaySeconds = decay)
            val room = TestSignals.noise(pluck.size, rms = ROOM_RMS, seed = 2)
            val stream = FloatArray(WINDOW + pluck.size) { if (it < WINDOW) room[it % room.size] else pluck[it - WINDOW] + room[it - WINDOW] }
            for (end in WINDOW + HOP..stream.size step HOP) {
                val frame = stream.copyOfRange(end - WINDOW, end)
                assertTrue(
                    "$f Hz at ${(end - WINDOW) / RATE.toFloat()} s: dropped ${check.dropOf(frame)} dB",
                    check.isSustained(frame, floorDbfs = ROOM_DBFS),
                )
            }
        }
    }

    @Test
    fun `a tick that has died away is not sustained`() {
        for (f in listOf(300f, 1000f, 3000f)) for (decay in listOf(0.002, 0.008, 0.015)) {
            // The tick in the oldest block, room noise 40 dB below its peak after it.
            val frame = TestSignals.noise(WINDOW, rms = 0.005f, seed = 4)
            val tick = TestSignals.tick(floatArrayOf(f), decay, length = HOP)
            for (i in tick.indices) frame[i] += tick[i]
            assertFalse("$f Hz, ${decay * 1000} ms", check.isSustained(frame))
            assertTrue("$f Hz, ${decay * 1000} ms: dropped only ${check.dropOf(frame)} dB", check.dropOf(frame) > 20f)
        }
    }

    @Test
    fun `a quiet tick that has passed leaves the room, which isn't sustained`() {
        // A tick only 7 dB above the room can't drop further than that, as on the phone.
        val frame = TestSignals.noise(WINDOW, rms = ROOM_RMS, seed = 4)
        val tick = TestSignals.tick(floatArrayOf(1400f), 0.008, length = HOP, amplitude = 5 * ROOM_RMS)
        for (i in tick.indices) frame[i] += tick[i]
        assertTrue("dropped ${check.dropOf(frame)} dB", check.dropDb in SustainCheck.ROOM_DROP_DB..SustainCheck.DEFAULT_MAX_DROP_DB)
        assertFalse(check.isSustained(frame, floorDbfs = ROOM_DBFS))
        // While the room is unknown, only the big drop counts.
        assertTrue(check.isSustained(frame))
        // The room alone is sustained: it is no quieter at the end than before.
        assertTrue(check.isSustained(TestSignals.noise(WINDOW, rms = ROOM_RMS, seed = 5), floorDbfs = ROOM_DBFS))
    }

    @Test
    fun `silence and frames shorter than a block are not down at all`() {
        assertEquals(0f, check.dropOf(FloatArray(WINDOW)), 0f)
        assertEquals(0f, check.dropOf(TestSignals.sine(110f, HOP - 1)), 0f)
        // A sound followed by digital silence: as far down as it gets.
        val cut = TestSignals.sine(110f, WINDOW).also { it.fill(0f, WINDOW - HOP, WINDOW) }
        assertEquals(SustainCheck.MAX_REPORTED_DROP_DB, check.dropOf(cut), 0f)
    }

    @Test
    fun `dropDb does not allocate`() {
        val frame = TestSignals.pluck(110f, WINDOW)
        repeat(100) { check.dropOf(frame) }
        assertEquals(0L, allocatedBytes { repeat(100) { check.dropOf(frame) } })
    }

    @Test
    fun `invalid settings and lengths are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SustainCheck(0) }
        assertThrows(IllegalArgumentException::class.java) { SustainCheck(HOP, maxDropDb = SustainCheck.ROOM_DROP_DB) }
        assertThrows(IllegalArgumentException::class.java) { check.dropOf(FloatArray(10), 11) }
    }

    private fun SustainCheck.dropOf(frame: FloatArray, length: Int = frame.size): Float {
        measure(frame, length)
        return dropDb
    }

    private companion object {
        const val ROOM_RMS = 0.0003f
        val ROOM_DBFS = SignalGate.rmsDbfs(floatArrayOf(ROOM_RMS, -ROOM_RMS))
        const val RATE = TestSignals.SAMPLE_RATE
        const val WINDOW = 4096
        const val HOP = 1024
    }
}
