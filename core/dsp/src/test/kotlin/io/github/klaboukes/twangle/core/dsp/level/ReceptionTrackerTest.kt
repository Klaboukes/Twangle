package io.github.klaboukes.twangle.core.dsp.level

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.pitch.TunerChain
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The reception figure: volume, noise and clarity turned into one steady 0..1 value, on its own and
 * fed through the app's whole chain (frame assembly → gate → YIN → noise floor and clarity, see
 * [TunerChain]) with synthetic notes and rooms.
 */
class ReceptionTrackerTest {

    // ---- The mapping ----

    @Test
    fun `the level scores 0 where the gate opens and 1 for a strong note`() {
        assertEquals(0f, ReceptionTracker.levelScore(-60f))
        assertEquals(0f, ReceptionTracker.levelScore(-50f))
        assertEquals(0.5f, ReceptionTracker.levelScore(-42.5f), 1e-4f)
        assertEquals(1f, ReceptionTracker.levelScore(-35f))
        assertEquals(1f, ReceptionTracker.levelScore(-3f))
    }

    @Test
    fun `the SNR scores 0 for a note no louder than the room and 1 from 20 dB`() {
        assertEquals(0f, ReceptionTracker.snrScore(NoiseFloorTracker.MIN_SNR_DB))
        assertEquals(0f, ReceptionTracker.snrScore(0f))
        assertEquals(0.5f, ReceptionTracker.snrScore(10f), 1e-4f)
        assertEquals(1f, ReceptionTracker.snrScore(20f))
        assertEquals(1f, ReceptionTracker.snrScore(45f))
    }

    @Test
    fun `reception is the weakest factor and names it`() {
        assertSettles(-30f, 30f, 1f, expected = 1f, ReceptionLimit.NONE)
        assertSettles(-45f, 30f, 1f, expected = 1f / 3f, ReceptionLimit.LEVEL)
        assertSettles(-30f, 6f, 1f, expected = 0.3f, ReceptionLimit.NOISE)
        assertSettles(-30f, 30f, 0.4f, expected = 0.4f, ReceptionLimit.CLARITY)
    }

    @Test
    fun `an unknown room is no limit`() {
        val tracker = tracker()
        tracker.update(-30f, snrDb = Float.NaN, clarity = 1f)
        assertEquals(1f, tracker.noise)
        assertEquals(1f, tracker.reception)
    }

    @Test
    fun `starts at full reception, and a note's first frame sets it at once`() {
        val tracker = tracker()
        assertEquals(1f, tracker.reception)
        assertFalse(tracker.hasNote)
        assertTrue(tracker.levelDbfs.isNaN())
        tracker.update(-45f, 5f, 0.6f)
        assertTrue(tracker.hasNote)
        assertEquals(1f / 3f, tracker.level, 1e-4f)
        assertEquals(0.25f, tracker.noise, 1e-4f)
        assertEquals(0.6f, tracker.clarity, 1e-4f)
        assertEquals(0.25f, tracker.reception, 1e-4f)
        assertEquals(-45f, tracker.levelDbfs)
    }

    @Test
    fun `reset forgets the note, so the next one starts afresh`() {
        val tracker = tracker()
        repeat(100) { tracker.update(-48f, 2f, 0.1f) }
        tracker.reset()
        assertFalse(tracker.hasNote)
        assertEquals(1f, tracker.reception)
        assertEquals(ReceptionLimit.NONE, tracker.limit)
        tracker.update(-30f, 30f, 1f)
        assertEquals("not eased from the last note", 1f, tracker.reception)
    }

    // ---- Smoothing: no flicker ----

    @Test
    fun `better shows quickly, worse slowly`() {
        val better = tracker()
        better.update(-30f, 30f, 0f)
        framesFor(0.3f) { better.update(-30f, 30f, 1f) }
        assertTrue("clean again after 0.3 s: ${better.reception}", better.reception > 0.85f)

        val worse = tracker()
        worse.update(-30f, 30f, 1f)
        framesFor(0.2f) { worse.update(-30f, 30f, 0f) }
        assertTrue("still mostly crisp after 0.2 s of noise: ${worse.reception}", worse.reception > 0.6f)
        framesFor(1.5f) { worse.update(-30f, 30f, 0f) }
        assertTrue("faint after 1.7 s of noise: ${worse.reception}", worse.reception < 0.05f)
    }

    @Test
    fun `while a note's first window fills, it sharpens at once`() {
        val tracker = tracker()
        tracker.update(-40f, 10f, 0.2f)
        tracker.update(-35f, 20f, 0.6f)
        tracker.update(-30f, 30f, 1f)
        assertEquals(1f, tracker.reception)
        // Getting worse is slow from the start…
        tracker.update(-30f, 30f, 0.5f)
        assertTrue(tracker.reception > 0.9f)
        // …and after the onset, getting better is too.
        val later = tracker()
        later.update(-30f, 30f, 1f)
        framesFor(1f) { later.update(-30f, 30f, 0f) }
        later.update(-30f, 30f, 1f)
        assertTrue("one good frame after the onset: ${later.reception}", later.reception < 0.3f)
    }

    @Test
    fun `one or two rough frames barely dim it`() {
        val tracker = tracker()
        repeat(50) { tracker.update(-30f, 30f, 1f) }
        repeat(2) { tracker.update(-55f, -20f, 0f) }
        assertTrue("after two bad frames: ${tracker.reception}", tracker.reception > 0.9f)
    }

    @Test
    fun `factors jumping about frame by frame give a steady figure`() {
        // Clarity and SNR jitter from frame to frame, as they do in a noisy room.
        val random = Random(7)
        val tracker = tracker()
        val shown = ArrayList<Float>()
        framesFor(4f) {
            tracker.update(-33f + 4f * random.nextFloat(), 6f + 8f * random.nextFloat(), 0.3f + 0.6f * random.nextFloat())
            shown += tracker.reception
        }
        val settled = shown.drop(shown.size / 2)
        val biggestStep = settled.zipWithNext { a, b -> abs(b - a) }.max()
        assertTrue("biggest change from one frame to the next: $biggestStep", biggestStep < 0.03f)
        assertTrue("range once settled: ${settled.min()}..${settled.max()}", settled.max() - settled.min() < 0.15f)
    }

    @Test
    fun `takes the same time at every frame rate`() {
        // Hops of 941 samples at 44.1 kHz, 1024 at 48 kHz, 256 at 8 kHz (≈ 21 ms each), and further apart.
        for (frameSeconds in listOf(941f / 44_100, 1024f / 48_000, 256f / 8_000, 0.01f, 0.04f)) {
            val tracker = ReceptionTracker(frameSeconds)
            tracker.update(-30f, 30f, 1f)
            val frames = (0.2f / frameSeconds).roundToInt()
            repeat(frames) { tracker.update(-30f, 30f, 0f) }
            val expected = exp(-frames * frameSeconds / ReceptionTracker.FALL_SECONDS)
            assertEquals("frames $frameSeconds s apart", expected, tracker.reception, 1e-3f)
            assertEquals("frames $frameSeconds s apart", 0.67f, tracker.reception, 0.04f)
        }
    }

    // ---- Through the chain ----

    @Test
    fun `a loud clean note is crisp, a quiet one and a noisy one are faint`() {
        for (f in listOf(82.41f, 196f, 659.26f)) {
            val clean = settledReception(noteInRoom(f, noteDb = -25f, roomDb = -70f))
            assertTrue("$f Hz, loud and clean: $clean", clean.reception >= ReceptionTracker.CLEAR_RECEPTION)
            assertEquals(ReceptionLimit.NONE, clean.limit)

            val quiet = settledReception(noteInRoom(f, noteDb = -44f, roomDb = -75f))
            assertEquals("$f Hz, quiet", 0.4f, quiet.reception, 0.08f)
            assertEquals("$f Hz, quiet", ReceptionLimit.LEVEL, quiet.limit)

            // 3 dB over a loud room: plenty of level, but the note barely rises above the noise.
            val noisy = settledReception(noteInRoom(f, noteDb = -27f, roomDb = -30f))
            assertTrue("$f Hz, noisy: ${noisy.reception}", noisy.reception < 0.35f)
            assertTrue("$f Hz, noisy: ${noisy.limit}", noisy.limit == ReceptionLimit.NOISE || noisy.limit == ReceptionLimit.CLARITY)
            assertEquals("$f Hz, noisy: level is fine", 1f, noisy.level, 0.01f)
        }
    }

    @Test
    fun `a ringing pluck fades steadily as it dies away`() {
        val f = 110f
        val pluck = TestSignals.pluck(f, length = (4.0 * RATE).toInt(), amplitude = 0.3f, decaySeconds = 0.8)
        val stream = add(room(1.0, -70f) + pluck, room(5.0, -70f, seed = 2))
        val tracker = ReceptionTracker(FRAME_SECONDS)
        val shown = ArrayList<Float>()
        for (frame in TunerChain().run(stream)) {
            if (frame.rawHz > 0f) {
                tracker.update(frame.levelDbfs, frame.snrDb, frame.clarity)
                shown += tracker.reception
            }
        }
        // Crisp once the analysis window holds the note (≈ 85 ms, 4 frames), not after a slow fade-in.
        assertTrue("crisp from the start: ${shown.take(15)}", shown.subList(4, 15).all { it > 0.9f })
        assertTrue("faint at the end: ${shown.last()}", shown.last() < 0.4f)
        // Once the window holds the note it only ever fades: never brightens by more than a hair while the note dies.
        val brightening = shown.drop(5).zipWithNext { a, b -> b - a }.max()
        assertTrue("brightened by $brightening", brightening < 0.01f)
    }

    @Test
    fun `update allocates nothing`() {
        val tracker = tracker()
        repeat(500) { tracker.update(-40f + it % 9, (it % 25).toFloat(), (it % 10) / 10f) }
        val bytes = allocatedBytes {
            for (i in 0 until 2000) tracker.update(-40f + i % 9, (i % 25).toFloat(), (i % 10) / 10f)
        }
        assertEquals(0L, bytes)
    }

    @Test
    fun `rejects impossible settings`() {
        assertThrows(IllegalArgumentException::class.java) { ReceptionTracker(frameSeconds = 0f) }
    }

    // ---- Helpers ----

    private fun tracker() = ReceptionTracker(FRAME_SECONDS)

    private inline fun framesFor(seconds: Float, frame: () -> Unit) = repeat((seconds / FRAME_SECONDS).toInt()) { frame() }

    private fun assertSettles(levelDbfs: Float, snrDb: Float, clarity: Float, expected: Float, limit: ReceptionLimit) {
        val tracker = tracker()
        framesFor(3f) { tracker.update(levelDbfs, snrDb, clarity) }
        assertEquals("$levelDbfs dBFS, $snrDb dB, clarity $clarity", expected, tracker.reception, 1e-3f)
        assertEquals("$levelDbfs dBFS, $snrDb dB, clarity $clarity", limit, tracker.limit)
    }

    /** The tracker after the pitched frames of the note's last second in [stream]. */
    private fun settledReception(stream: FloatArray): ReceptionTracker {
        val tracker = ReceptionTracker(FRAME_SECONDS)
        for (frame in TunerChain().run(stream)) {
            val time = frame.endSample.toDouble() / RATE
            if (frame.rawHz > 0f && time - WINDOW_SECONDS >= NOTE_START && time < NOTE_END) {
                tracker.update(frame.levelDbfs, frame.snrDb, frame.clarity)
            }
        }
        assertTrue("no note heard", tracker.hasNote)
        return tracker
    }

    /** White noise at [dbfs] RMS for [seconds]. */
    private fun room(seconds: Double, dbfs: Float, seed: Long = 1L): FloatArray =
        TestSignals.noise((seconds * RATE).toInt(), rms = 10.0.pow(dbfs / 20.0).toFloat(), seed = seed)

    /** 4 s of room at [roomDb]; from [NOTE_START] to [NOTE_END] s a weak-fundamental note at [noteDb] RMS. */
    private fun noteInRoom(f: Float, noteDb: Float, roomDb: Float): FloatArray {
        val stream = room(4.0, roomDb, seed = 5)
        val start = (NOTE_START * RATE).toInt()
        val note = TestSignals.weakFundamental(f, length = ((NOTE_END - NOTE_START) * RATE).toInt())
        val scale = (10.0.pow(noteDb / 20.0) / TestSignals.rms(note)).toFloat()
        for (i in note.indices) stream[start + i] += scale * note[i]
        return stream
    }

    private fun add(a: FloatArray, b: FloatArray) = FloatArray(minOf(a.size, b.size)) { a[it] + b[it] }

    private companion object {
        const val RATE = TestSignals.SAMPLE_RATE
        const val FRAME_SECONDS = 1024f / RATE
        const val WINDOW_SECONDS = 4096.0 / RATE
        const val NOTE_START = 1.0
        const val NOTE_END = 3.0
    }
}
