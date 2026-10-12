package io.github.klaboukes.twangle.core.dsp.level

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.TunerChain
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The room's background level and a note's height above it, fed through the app's whole chain
 * (frame assembly → gate → YIN → [NoiseFloorTracker], see [TunerChain]) with synthetic rooms.
 */
class NoiseFloorTrackerTest {

    // ---- Through the chain ----

    @Test
    fun `a room reads its own level, below and above the gate, with no SNR`() {
        for (db in listOf(-90f, -70f, -60f, -50f, -40f, -30f)) {
            val frames = TunerChain().run(room(3.0, db))
            for (frame in frames.after(0.2)) {
                assertEquals("$db dBFS room at ${frame.time} s", db, frame.floorDbfs, 0.5f)
                assertTrue("$db dBFS room: SNR without a note", frame.snrDb.isNaN())
            }
        }
    }

    @Test
    fun `a note over the room - the floor holds and the SNR is how far the note rises above it`() {
        // Room at −50 dBFS (the S26 measured −52…−56), so even a 0 dB note opens the gate.
        for (f in listOf(82.41f, 196f, 659.26f)) for (snr in listOf(0f, 3f, 6f, 10f, 20f, 30f, 40f)) {
            val frames = TunerChain().run(noteInRoom(f, snr, roomDb = -50f))
            val during = frames.between(NOTE_START + 0.1, NOTE_END)
            val pitched = during.filter { it.rawHz > 0f }
            assertTrue("$f Hz at $snr dB: only ${pitched.size} pitched frames", pitched.size > during.size / 4)
            for (frame in during) assertEquals("$f Hz at $snr dB, ${frame.time} s: floor", -50f, frame.floorDbfs, 0.5f)
            for (frame in pitched) assertEquals("$f Hz at $snr dB, ${frame.time} s: SNR", snr, frame.snrDb, 1f)
            for (frame in during.filter { it.rawHz == 0f }) assertTrue(frame.snrDb.isNaN())
        }
    }

    @Test
    fun `the floor drops at once when the room gets quieter and rises within the window when it gets louder`() {
        val stream = room(3.0, -40f, seed = 1) + room(3.0, -60f, seed = 2) + room(4.0, -40f, seed = 3)
        val frames = TunerChain().run(stream)
        for (frame in frames.between(3.25, 6.0)) assertEquals("quieter at ${frame.time} s", -60f, frame.floorDbfs, 1f)
        // A louder room is only believed once it has lasted the whole window (2 s)…
        for (frame in frames.between(6.0, 7.5)) assertEquals("just louder at ${frame.time} s", -60f, frame.floorDbfs, 1f)
        // …then it is.
        for (frame in frames.after(8.3)) assertEquals("louder at ${frame.time} s", -40f, frame.floorDbfs, 1f)
    }

    @Test
    fun `a loud burst between notes, shorter than the window, doesn't lift the floor`() {
        val stream = room(2.0, -60f, seed = 1) + room(1.0, -25f, seed = 2) + room(2.0, -60f, seed = 3)
        for (frame in TunerChain().run(stream).after(0.2)) {
            assertEquals("at ${frame.time} s", -60f, frame.floorDbfs, 1f)
        }
    }

    @Test
    fun `a ringing pluck and its quiet tail don't lift the floor`() {
        for (f in listOf(82.41f, 246.94f, 659.26f)) {
            val roomBefore = room(2.0, -70f, seed = 1)
            val pluck = TestSignals.pluck(f, length = (5.0 * RATE).toInt(), decaySeconds = 0.8)
            val pluckInRoom = add(pluck, room(5.0, -70f, seed = 2))
            val frames = TunerChain().run(roomBefore + pluckInRoom)
            assertTrue("$f Hz: the pluck was never heard", frames.any { it.rawHz > 0f })
            for (frame in frames.after(0.2)) assertEquals("$f Hz at ${frame.time} s", -70f, frame.floorDbfs, 1f)
        }
    }

    @Test
    fun `the window lasts two seconds at every sample rate`() {
        for (rate in listOf(8_000, 16_000, 22_050, 44_100, 48_000, 96_000)) {
            val settings = AnalysisSettings.forSampleRate(rate)
            val tracker = settings.newNoiseFloorTracker()
            assertEquals("$rate Hz", 2f, tracker.windowFrames * settings.hopSeconds, settings.hopSeconds)
        }
        assertEquals(94, AnalysisSettings.forSampleRate(48_000).newNoiseFloorTracker().windowFrames)
    }

    // ---- The tracker on its own ----

    @Test
    fun `no floor before a note-free frame, and no SNR without a floor or a note`() {
        val tracker = tracker()
        assertFalse(tracker.hasFloor)
        assertTrue(tracker.floorDbfs.isNaN())
        tracker.update(-30f, noteSounding = true)
        assertFalse("a note is not the room", tracker.hasFloor)
        assertTrue("no floor to compare with", tracker.snrDb.isNaN())
        tracker.update(-60f, noteSounding = false)
        assertEquals(-60f, tracker.floorDbfs, 1e-4f)
        assertTrue("no note, no SNR", tracker.snrDb.isNaN())
        tracker.update(-40f, noteSounding = true)
        assertEquals(-60f, tracker.floorDbfs, 1e-4f)
        assertEquals(19.96f, tracker.snrDb, 0.01f)
    }

    @Test
    fun `one or two silent frames don't move the floor, three in a row do`() {
        val tracker = tracker()
        repeat(50) { tracker.update(-60f, noteSounding = false) }
        repeat(2) { tracker.update(-120f, noteSounding = false) }
        repeat(5) {
            tracker.update(-60f, noteSounding = false)
            assertEquals(-60f, tracker.floorDbfs, 1e-4f)
        }
        repeat(3) { tracker.update(-120f, noteSounding = false) }
        assertEquals(-120f, tracker.floorDbfs, 1e-4f)
    }

    @Test
    fun `notes never move the floor, however long they last`() {
        val tracker = tracker()
        repeat(10) { tracker.update(-60f, noteSounding = false) }
        repeat(1000) { tracker.update(-20f, noteSounding = true) }
        assertEquals(-60f, tracker.floorDbfs, 1e-4f)
    }

    @Test
    fun `reset forgets the room`() {
        val tracker = tracker()
        repeat(10) { tracker.update(-60f, noteSounding = false) }
        tracker.update(-30f, noteSounding = true)
        tracker.reset()
        assertFalse(tracker.hasFloor)
        assertTrue(tracker.snrDb.isNaN())
        tracker.update(-45f, noteSounding = false)
        assertEquals(-45f, tracker.floorDbfs, 1e-4f)
    }

    @Test
    fun `SNR subtracts the background's power from the note's`() {
        // 10 dB above the floor: the note alone is 10^1 − 1 = 9 times the background.
        assertEquals(9.54f, NoiseFloorTracker.snrDb(-50f, -60f), 0.01f)
        assertEquals(30f, NoiseFloorTracker.snrDb(-30f, -60f), 0.01f)
        assertEquals(0f, NoiseFloorTracker.snrDb(-60f + 3.0103f, -60f), 0.01f)
        assertEquals(NoiseFloorTracker.MIN_SNR_DB, NoiseFloorTracker.snrDb(-60f, -60f))
        assertEquals(NoiseFloorTracker.MIN_SNR_DB, NoiseFloorTracker.snrDb(-70f, -60f))
    }

    @Test
    fun `rejects impossible settings`() {
        assertThrows(IllegalArgumentException::class.java) { NoiseFloorTracker(frameSeconds = 0f) }
        assertThrows(IllegalArgumentException::class.java) { NoiseFloorTracker(FRAME_SECONDS, windowSeconds = FRAME_SECONDS / 2) }
        assertThrows(IllegalArgumentException::class.java) { NoiseFloorTracker(FRAME_SECONDS, medianFrames = 0) }
    }

    @Test
    fun `update allocates nothing`() {
        val tracker = tracker()
        repeat(500) { tracker.update(-60f + it % 7, noteSounding = it % 3 == 0) }
        val bytes = allocatedBytes {
            for (i in 0 until 2000) tracker.update(-60f + i % 7, noteSounding = i % 3 == 0)
        }
        assertEquals(0L, bytes)
    }

    // ---- Helpers ----

    private fun tracker() = NoiseFloorTracker(FRAME_SECONDS)

    private val TunerChain.Frame.time: Double get() = endSample.toDouble() / RATE

    private fun List<TunerChain.Frame>.after(seconds: Double) = filter { it.time >= seconds }

    // Frames whose whole window lies in [from, to) seconds.
    private fun List<TunerChain.Frame>.between(from: Double, to: Double) =
        filter { it.time - WINDOW_SECONDS >= from && it.time < to }

    /** White noise at [dbfs] RMS for [seconds]. */
    private fun room(seconds: Double, dbfs: Float, seed: Long = 1L): FloatArray =
        TestSignals.noise((seconds * RATE).toInt(), rms = 10.0.pow(dbfs / 20.0).toFloat(), seed = seed)

    /** 4 s of room at [roomDb]; from [NOTE_START] to [NOTE_END] s a weak-fundamental note [snr] dB above it. */
    private fun noteInRoom(f: Float, snr: Float, roomDb: Float): FloatArray {
        val stream = room(4.0, roomDb, seed = 5)
        val start = (NOTE_START * RATE).toInt()
        val note = TestSignals.weakFundamental(f, length = ((NOTE_END - NOTE_START) * RATE).toInt())
        val scale = (10.0.pow((roomDb + snr) / 20.0) / TestSignals.rms(note)).toFloat()
        for (i in note.indices) stream[start + i] += scale * note[i]
        return stream
    }

    private fun add(a: FloatArray, b: FloatArray) = FloatArray(a.size) { a[it] + b[it] }

    private companion object {
        const val RATE = TestSignals.SAMPLE_RATE
        const val FRAME_SECONDS = 1024f / RATE
        const val WINDOW_SECONDS = 4096.0 / RATE
        const val NOTE_START = 1.0
        const val NOTE_END = 3.0
    }
}
