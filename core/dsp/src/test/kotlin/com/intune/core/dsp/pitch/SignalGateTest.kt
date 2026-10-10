package com.intune.core.dsp.pitch

import com.intune.core.dsp.allocatedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class SignalGateTest {

    @Test
    fun `full-scale square wave is 0 dBFS and full-scale sine is about -3 dBFS`() {
        val square = FloatArray(N) { if (it % 100 < 50) 1f else -1f }
        assertEquals(0f, SignalGate.rmsDbfs(square), 0.01f)
        assertEquals(-3.01f, SignalGate.rmsDbfs(sine(amplitude = 1f)), 0.01f)
    }

    @Test
    fun `level follows amplitude at 20 dB per decade`() {
        for (db in listOf(-6f, -20f, -40f, -60f, -80f)) {
            val amplitude = 10.0.pow(db / 20.0).toFloat()
            assertEquals("$db dB", db - 3.01f, SignalGate.rmsDbfs(sine(amplitude)), 0.01f)
        }
    }

    @Test
    fun `digital silence is floored instead of minus infinity`() {
        assertEquals(SignalGate.SILENCE_DBFS, SignalGate.rmsDbfs(FloatArray(N)))
        assertEquals(SignalGate.SILENCE_DBFS, SignalGate.rmsDbfs(FloatArray(0)))
        assertEquals(SignalGate.SILENCE_DBFS, SignalGate.rmsDbfs(FloatArray(N) { 1e-9f }))
    }

    @Test
    fun `DC removal leaves a zero-mean signal with the AC part intact`() {
        val original = sine(amplitude = 0.3f)
        val shifted = FloatArray(N) { original[it] + 0.25f }
        SignalGate.removeDc(shifted)
        assertEquals(0.0, shifted.average(), 1e-6)
        for (i in 0 until N) assertEquals(original[i], shifted[i], 1e-5f)
    }

    @Test
    fun `DC removal and level only touch the first length samples`() {
        val frame = FloatArray(8) { if (it < 4) 1f else 5f }
        SignalGate.removeDc(frame, length = 4)
        assertEquals(listOf(0f, 0f, 0f, 0f, 5f, 5f, 5f, 5f), frame.toList())
        assertEquals(SignalGate.SILENCE_DBFS, SignalGate.rmsDbfs(frame, length = 4))
    }

    @Test
    fun `a DC offset alone does not open the gate`() {
        val gate = SignalGate()
        assertFalse(gate.process(FloatArray(N) { 0.5f }))
        assertEquals(SignalGate.SILENCE_DBFS, gate.levelDbfs)
    }

    @Test
    fun `gate opens on a loud note and stays closed on quiet noise`() {
        val gate = SignalGate()
        assertFalse(gate.process(sineAtDbfs(-60f)))
        assertEquals(-60f, gate.levelDbfs, 0.01f)
        assertTrue(gate.process(sineAtDbfs(-30f)))
        assertTrue(gate.isOpen)
    }

    @Test
    fun `hysteresis keeps a decaying note open until it falls well below the threshold`() {
        val gate = SignalGate(thresholdDbfs = -50f, hysteresisDb = 6f)
        assertFalse(gate.process(sineAtDbfs(-53f)))
        assertTrue(gate.process(sineAtDbfs(-49f)))
        assertTrue(gate.process(sineAtDbfs(-53f)))
        assertTrue(gate.process(sineAtDbfs(-55.9f)))
        assertFalse(gate.process(sineAtDbfs(-56.1f)))
        assertFalse(gate.process(sineAtDbfs(-53f)))
    }

    @Test
    fun `reset closes the gate`() {
        val gate = SignalGate()
        gate.process(sineAtDbfs(-20f))
        gate.reset()
        assertFalse(gate.isOpen)
        assertEquals(SignalGate.SILENCE_DBFS, gate.levelDbfs)
    }

    @Test
    fun `process does not allocate`() {
        val gate = SignalGate()
        val frame = sine(amplitude = 0.3f)
        repeat(1_000) { gate.process(frame) }
        assertEquals(0L, allocatedBytes { repeat(1_000) { gate.process(frame) } })
    }

    @Test
    fun `invalid settings are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SignalGate(thresholdDbfs = 3f) }
        assertThrows(IllegalArgumentException::class.java) { SignalGate(hysteresisDb = -1f) }
        assertThrows(IllegalArgumentException::class.java) { SignalGate.rmsDbfs(FloatArray(4), length = 5) }
    }

    // Whole number of periods (48 000 / 480 = 100 samples each) so the RMS is exact.
    private fun sine(amplitude: Float) = FloatArray(N) { (amplitude * sin(2 * PI * 480 * it / 48_000)).toFloat() }

    // A sine whose RMS level is exactly [dbfs].
    private fun sineAtDbfs(dbfs: Float) = sine(amplitude = (10.0.pow((dbfs + 3.0103) / 20.0)).toFloat())

    private companion object {
        const val N = 4800
    }
}
