package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/** The frame chain the phone's tuner, Ear training and the watch share (Phase 65), on synthetic signals. */
class PitchChainTest {

    private class Reading(val hz: Float, val gateOpen: Boolean, val levelDbfs: Float, val floorDbfs: Float, val snrDb: Float)

    private fun cents(measured: Float, expected: Float) = 1200f * log2(measured / expected)

    /** Feeds [signal] in hop-sized chunks, asking [range] before each push, and returns one reading per analysed frame. */
    private fun PitchChain.read(signal: FloatArray, chunk: Int = 1024, range: () -> PitchRange = { PitchRange.STANDARD }): List<Reading> {
        val readings = ArrayList<Reading>()
        var start = 0
        while (start < signal.size) {
            val end = minOf(start + chunk, signal.size)
            var pos = start
            while (pos < end) {
                this.range = range()
                pos += push(signal, pos, end - pos)
                if (analyse()) readings += Reading(pitch.frequencyHz, gateOpen, levelDbfs, noiseFloorDbfs, snrDb)
            }
            start = end
        }
        return readings
    }

    @Test
    fun `a steady note gives one reading per hop within a cent`() {
        for (rate in intArrayOf(8_000, 16_000, 44_100, 48_000, 96_000)) {
            val settings = AnalysisSettings.forSampleRate(rate)
            val readings = PitchChain(rate).read(TestSignals.sawtooth(110f, length = rate, sampleRate = rate), chunk = 333)
            // The first frame after one window, then one per hop.
            assertEquals("$rate Hz", (rate - settings.windowSize) / settings.hopSize + 1, readings.size)
            for (r in readings) assertTrue("$rate Hz: off by ${cents(r.hz, 110f)} c", abs(cents(r.hz, 110f)) < 1f)
        }
    }

    @Test
    fun `the low range reads B0 after one long window, the standard range does not`() {
        val b0 = 30.87f
        val signal = TestSignals.weakFundamental(b0, length = 24 * 1024)
        val low = PitchChain(48_000).read(signal) { PitchRange.LOW }
        // The frames before one long window (8192 samples) are passed by: (24 · 1024 − 8192) / 1024 + 1.
        assertEquals(17, low.size)
        for (r in low) assertTrue("off by ${cents(r.hz, b0)} c", abs(cents(r.hz, b0)) < 1f)

        val standard = PitchChain(48_000).read(TestSignals.sine(b0, length = 24 * 1024))
        assertEquals(21, standard.size)
        assertTrue(standard.none { it.hz > 0f })
    }

    @Test
    fun `switching to the low range mid-note reads on the next hop`() {
        val e1 = 41.2f
        var hops = 0
        val readings = PitchChain(48_000).read(TestSignals.sine(e1, length = 24 * 1024)) {
            if (hops++ < 11) PitchRange.STANDARD else PitchRange.LOW
        }
        // No frame lost at the switch: the ring buffer already held the long window.
        assertEquals(21, readings.size)
        assertTrue(readings.take(8).none { it.hz > 0f })
        for (r in readings.drop(8)) assertTrue("off by ${cents(r.hz, e1)} c", abs(cents(r.hz, e1)) < 1f)
    }

    @Test
    fun `mains hum alone is no note in the low range, with or without the canceller`() {
        for (hz in floatArrayOf(50f, 60f)) {
            val hum = TestSignals.hum(hz, length = 3 * 48_000, rms = 0.05f)
            for (cancelHum in listOf(true, false)) {
                val readings = PitchChain(48_000, cancelHum = cancelHum).read(hum) { PitchRange.LOW }
                if (cancelHum) {
                    assertTrue("$hz Hz hum read as a note", readings.none { it.hz > 0f })
                } else {
                    // Without the canceller and its mains rule the low range reads the hum itself.
                    assertTrue("$hz Hz hum unheard without the canceller", readings.any { abs(cents(it.hz, hz)) < 50f })
                }
            }
        }
    }

    @Test
    fun `quiet input keeps the gate closed and teaches the noise floor`() {
        val room = TestSignals.noise(48_000, rms = 10f.pow(-60f / 20f), seed = 5)
        val readings = PitchChain(48_000).read(room)
        assertTrue(readings.none { it.gateOpen || it.hz > 0f })
        val floor = readings.last().floorDbfs
        assertTrue("floor $floor dBFS", abs(floor - -60f) < 3f)
    }

    @Test
    fun `a note in a room carries its SNR over the floor`() {
        val room = TestSignals.noise(96_000, rms = 10f.pow(-50f / 20f), seed = 3)
        val note = TestSignals.sawtooth(110f, length = 48_000)
        val noteRms = 10f.pow(-30f / 20f) / TestSignals.rms(note).toFloat()
        val stream = FloatArray(room.size) { room[it] + if (it >= 48_000) note[it - 48_000] * noteRms else 0f }
        val last = PitchChain(48_000).read(stream).last()
        assertTrue(last.hz > 0f)
        assertTrue("SNR ${last.snrDb} dB", abs(last.snrDb - 20f) < 3f)
    }

    @Test
    fun `a skipped frame is never heard, and the reading before it stays`() {
        val chain = PitchChain(48_000)
        val note = TestSignals.sine(220f, length = 48_000)
        chain.read(note.copyOfRange(0, 24_000))
        val before = chain.pitch
        assertTrue(before.isPitched)
        // Loud noise that would close nothing but change everything, passed by frame by frame.
        val noise = TestSignals.noise(24_000, rms = 0.5f, seed = 9)
        val level = chain.levelDbfs
        var pos = 0
        var skipped = 0
        while (pos < noise.size) {
            pos += chain.push(noise, pos, noise.size - pos)
            if (chain.isFrameReady) {
                chain.skipFrame()
                skipped++
            }
        }
        assertTrue(skipped > 20)
        assertEquals(before, chain.pitch)
        assertEquals(level, chain.levelDbfs)
        assertFalse(chain.isFrameReady)
    }

    @Test
    fun `after a restart the next frame waits for a whole window again`() {
        val chain = PitchChain(48_000)
        val signal = TestSignals.weakFundamental(30.87f, length = 24 * 1024)
        assertEquals(17, chain.read(signal) { PitchRange.LOW }.size)
        chain.restart()
        assertEquals(17, chain.read(signal) { PitchRange.LOW }.size)
    }

    @Test
    fun `the per-frame loop allocates nothing once both ranges are made`() {
        val chain = PitchChain(48_000)
        val signal = TestSignals.weakFundamental(41.2f, length = 48_000)
        var range = PitchRange.LOW
        fun run() {
            var pos = 0
            while (pos < signal.size) {
                chain.range = range
                pos += chain.push(signal, pos, minOf(1024, signal.size - pos))
                if (chain.analyse() && chain.pitch.isPitched) range = if (range == PitchRange.LOW) PitchRange.STANDARD else PitchRange.LOW
            }
        }
        repeat(3) { run() } // Warm up: class loading, JIT, the low range's buffers.
        assertEquals(0L, allocatedBytes { run() })
    }
}
