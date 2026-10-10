package com.intune.core.dsp.buffer

import com.intune.core.dsp.allocatedBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameAssemblerTest {

    @Test
    fun `first frame is ready after one full window`() {
        val assembler = FrameAssembler(windowSize = 8, hopSize = 2)
        assertEquals(7, assembler.push(ramp(0, 7)))
        assertFalse(assembler.isFrameReady)
        assertEquals(1, assembler.push(ramp(7, 1)))
        assertTrue(assembler.isFrameReady)

        val out = FloatArray(8)
        assembler.readFrame(out)
        assertArrayEquals(ramp(0, 8), out, 0f)
        assertFalse(assembler.isFrameReady)
    }

    @Test
    fun `later frames arrive every hop and overlap by window minus hop`() {
        val assembler = FrameAssembler(windowSize = 8, hopSize = 3)
        val frames = collectFrames(assembler, ramp(0, 20), chunkSize = 20)
        // Frames end at sample 8, 11, 14, 17, 20.
        assertEquals(5, frames.size)
        frames.forEachIndexed { i, frame -> assertArrayEquals("frame $i", ramp(i * 3, 8), frame, 0f) }
    }

    @Test
    fun `chunk size does not change the frames produced`() {
        val input = ramp(0, 10_000)
        val reference = collectFrames(FrameAssembler(), input, chunkSize = input.size)
        assertEquals(1 + (10_000 - 4096) / 1024, reference.size)
        for (chunkSize in listOf(1, 7, 480, 1024, 1500, 4096, 5000)) {
            val frames = collectFrames(FrameAssembler(), input, chunkSize)
            assertEquals("chunk $chunkSize", reference.size, frames.size)
            frames.indices.forEach { assertArrayEquals("chunk $chunkSize frame $it", reference[it], frames[it], 0f) }
        }
    }

    @Test
    fun `push consumes nothing while a frame is waiting`() {
        val assembler = FrameAssembler(windowSize = 4, hopSize = 2)
        assertEquals(4, assembler.push(ramp(0, 10)))
        assertTrue(assembler.isFrameReady)
        assertEquals(0, assembler.push(ramp(4, 6)))
    }

    @Test
    fun `push honours offset and length`() {
        val assembler = FrameAssembler(windowSize = 4, hopSize = 4)
        val source = ramp(0, 10)
        assertEquals(3, assembler.push(source, offset = 5, length = 3))
        assertEquals(1, assembler.push(source, offset = 2, length = 5))
        val out = FloatArray(4)
        assembler.readFrame(out)
        assertArrayEquals(floatArrayOf(5f, 6f, 7f, 2f), out, 0f)
    }

    @Test
    fun `hop equal to window gives back-to-back frames`() {
        val frames = collectFrames(FrameAssembler(windowSize = 4, hopSize = 4), ramp(0, 12), chunkSize = 5)
        assertEquals(3, frames.size)
        frames.forEachIndexed { i, frame -> assertArrayEquals(ramp(i * 4, 4), frame, 0f) }
    }

    @Test
    fun `reset starts over with a full window`() {
        val assembler = FrameAssembler(windowSize = 4, hopSize = 2)
        assembler.push(ramp(0, 4))
        assembler.readFrame(FloatArray(4))
        assembler.push(ramp(4, 1))
        assembler.reset()
        assertEquals(3, assembler.push(ramp(100, 3)))
        assertFalse(assembler.isFrameReady)
        assembler.push(ramp(103, 1))
        val out = FloatArray(4)
        assembler.readFrame(out)
        assertArrayEquals(ramp(100, 4), out, 0f)
    }

    @Test
    fun `invalid use is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FrameAssembler(windowSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { FrameAssembler(windowSize = 4, hopSize = 5) }
        assertThrows(IllegalArgumentException::class.java) { FrameAssembler(windowSize = 4, hopSize = 0) }
        val assembler = FrameAssembler(windowSize = 4, hopSize = 2)
        assertThrows(IllegalStateException::class.java) { assembler.readFrame(FloatArray(4)) }
        assertThrows(IllegalArgumentException::class.java) { assembler.push(FloatArray(4), offset = 2, length = 3) }
        assembler.push(ramp(0, 4))
        assertThrows(IllegalArgumentException::class.java) { assembler.readFrame(FloatArray(3)) }
    }

    @Test
    fun `push and readFrame do not allocate`() {
        val assembler = FrameAssembler()
        val chunk = ramp(0, 480)
        val out = FloatArray(assembler.windowSize)
        fun run() {
            repeat(1_000) {
                var pos = 0
                while (pos < chunk.size) {
                    pos += assembler.push(chunk, pos, chunk.size - pos)
                    if (assembler.isFrameReady) assembler.readFrame(out)
                }
            }
        }
        repeat(20) { run() }
        assertEquals(0L, allocatedBytes { run() })
    }

    private fun collectFrames(assembler: FrameAssembler, input: FloatArray, chunkSize: Int): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var start = 0
        while (start < input.size) {
            val chunk = input.copyOfRange(start, minOf(start + chunkSize, input.size))
            var pos = 0
            while (pos < chunk.size) {
                pos += assembler.push(chunk, pos, chunk.size - pos)
                if (assembler.isFrameReady) {
                    val frame = FloatArray(assembler.windowSize)
                    assembler.readFrame(frame)
                    frames += frame
                }
            }
            start += chunkSize
        }
        return frames
    }

    // Values start, start+1, …: each sample's value is its position in the stream.
    private fun ramp(start: Int, count: Int) = FloatArray(count) { (start + it).toFloat() }
}
