package com.intune.core.dsp.smoothing

import com.intune.core.dsp.allocatedBytes
import com.intune.core.dsp.music.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

class PitchSmootherTest {

    private val smoother = PitchSmoother()

    @Test
    fun `first reading is shown as is`() {
        assertFalse(smoother.hasReading)
        assertTrue(smoother.update(E2 + 0.12f))
        assertTrue(smoother.hasReading)
        assertEquals(Note(40), smoother.note)
        assertEquals(12f, smoother.cents, 0.01f)
    }

    @Test
    fun `jitter is reduced`() {
        val random = Random(1)
        val input = FloatArray(200) { E2 + 0.05f + 0.03f * random.nextGaussian().toFloat() }
        val output = FloatArray(input.size)
        for (i in input.indices) {
            smoother.update(input[i])
            output[i] = smoother.cents
        }
        val inputSpread = spread(input.drop(20).map { 100 * (it - E2) })
        val outputSpread = spread(output.drop(20).toList())
        assertTrue("spread $inputSpread → $outputSpread cents", outputSpread < inputSpread / 2)
        assertEquals(5f, output.drop(20).average().toFloat(), 0.5f)
    }

    @Test
    fun `a steady change is followed within a few frames`() {
        repeat(10) { smoother.update(E2) }
        repeat(15) { smoother.update(E2 + 0.2f) }
        assertEquals(20f, smoother.cents, 0.5f)
    }

    @Test
    fun `a one-frame spike is ignored`() {
        repeat(10) { smoother.update(E2) }
        smoother.update(E2 + 3f)
        assertEquals(Note(40), smoother.note)
        assertEquals(0f, smoother.cents, 0.01f)
    }

    @Test
    fun `octave glitches are rejected`() {
        repeat(10) { smoother.update(E2 + 0.03f) }
        assertFalse(smoother.update(E2 + 12.03f))
        assertFalse(smoother.update(E2 - 11.9f))
        assertTrue(smoother.update(E2 + 0.03f))
        assertFalse(smoother.update(E2 + 24f))
        assertEquals(Note(40), smoother.note)
        assertEquals(3f, smoother.cents, 0.01f)
    }

    @Test
    fun `a real octave change is accepted after it persists`() {
        repeat(10) { smoother.update(E2) }
        assertFalse(smoother.update(E2 + 12f))
        assertFalse(smoother.update(E2 + 12f))
        assertTrue(smoother.update(E2 + 12.05f))
        assertEquals(Note(52), smoother.note)
        assertEquals(5f, smoother.cents, 0.01f)
    }

    @Test
    fun `plucking another string jumps straight to it`() {
        repeat(10) { smoother.update(E2) }
        val readings = mutableListOf<Float>()
        repeat(5) {
            smoother.update(A2 - 0.1f)
            readings += smoother.midi
        }
        // Each reading is either still E2 (median not yet flipped) or right on A2; never in between.
        for (midi in readings) assertTrue("slid through $midi", abs(midi - E2) < 0.01f || abs(midi - (A2 - 0.1f)) < 0.01f)
        assertEquals(Note(45), smoother.note)
        assertEquals(-10f, smoother.cents, 0.01f)
    }

    @Test
    fun `a note between two semitones does not flicker`() {
        repeat(10) { smoother.update(E2 + 0.45f) }
        assertEquals(Note(40), smoother.note)
        // Wandering across the half-way point and up to +60 cents keeps E2 (shown as sharp).
        for (offset in listOf(0.48f, 0.52f, 0.55f, 0.6f, 0.5f, 0.62f)) repeat(5) { smoother.update(E2 + offset) }
        assertEquals(Note(40), smoother.note)
        assertTrue(smoother.cents > 50f)
        // Clearly past the hysteresis band, it moves to F2.
        repeat(10) { smoother.update(E2 + 0.7f) }
        assertEquals(Note(41), smoother.note)
        assertEquals(-30f, smoother.cents, 1f)
    }

    @Test
    fun `unsure readings move the needle less than confident ones`() {
        val unsure = PitchSmoother()
        repeat(10) {
            smoother.update(E2, confidence = 1f)
            unsure.update(E2, confidence = 1f)
        }
        repeat(3) {
            smoother.update(E2 + 0.2f, confidence = 0.99f)
            unsure.update(E2 + 0.2f, confidence = 0.85f)
        }
        // Same median input; the confident one has moved further towards +20 cents.
        assertTrue("${smoother.cents} vs ${unsure.cents}", smoother.cents > 2 * unsure.cents)
        assertTrue(unsure.cents > 0f)
        // Both still get there.
        repeat(60) {
            smoother.update(E2 + 0.2f, confidence = 0.99f)
            unsure.update(E2 + 0.2f, confidence = 0.85f)
        }
        assertEquals(20f, smoother.cents, 0.1f)
        assertEquals(20f, unsure.cents, 0.5f)
    }

    @Test
    fun `an unsure reading of a new string still snaps to it`() {
        repeat(10) { smoother.update(E2) }
        repeat(5) { smoother.update(A2, confidence = 0.8f) }
        assertEquals(Note(45), smoother.note)
        assertEquals(0f, smoother.cents, 0.01f)
    }

    @Test
    fun `short dropouts keep the reading and long ones clear it`() {
        repeat(10) { smoother.update(E2) }
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES) { smoother.noReading() }
        assertTrue(smoother.hasReading)
        smoother.update(E2)
        repeat(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES + 1) { smoother.noReading() }
        assertFalse(smoother.hasReading)
        // After clearing, the next note is shown right away with no octave check against the old one.
        assertTrue(smoother.update(E2 + 12f))
        assertEquals(Note(52), smoother.note)
    }

    @Test
    fun `reset clears the reading`() {
        smoother.update(E2)
        smoother.reset()
        assertFalse(smoother.hasReading)
        assertTrue(smoother.midi.isNaN())
    }

    @Test
    fun `update does not allocate`() {
        val random = Random(3)
        val input = FloatArray(1_000) { E2 + 0.1f * random.nextGaussian().toFloat() }
        fun run() {
            for (m in input) smoother.update(m)
            smoother.noReading()
        }
        repeat(20) { run() }
        assertEquals(0L, allocatedBytes { run() })
    }

    @Test
    fun `invalid settings and input are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(medianSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(emaAlpha = 0f) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(hysteresisCents = 60f) }
        assertThrows(IllegalArgumentException::class.java) { PitchSmoother(octaveConfirmFrames = 0) }
        assertThrows(IllegalArgumentException::class.java) { smoother.update(Float.NaN) }
    }

    private fun spread(values: List<Float>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private companion object {
        const val E2 = 40f
        const val A2 = 45f
    }
}
