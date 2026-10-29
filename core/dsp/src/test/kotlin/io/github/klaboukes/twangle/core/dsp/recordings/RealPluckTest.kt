package io.github.klaboukes.twangle.core.dsp.recordings

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.centsBetween
import io.github.klaboukes.twangle.core.dsp.music.nearestNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The tuner on a real guitar: every recording in `src/test/resources/fixtures/` (the owner's
 * steel-string acoustic through the Xiaomi 12X's own recorder, 2026-10-29; docs/architecture/testing.md,
 * *Real recordings*) through the app's whole chain ([io.github.klaboukes.twangle.core.dsp.pitch.TunerChain])
 * at the file's rate. The quiet-room takes are asserted on here; the noisy-room takes only run through
 * [RealPluckReport] until the roadmap's *Quick and steady on real strings*.
 */
internal class RealPluckTest {

    private val mapper = NoteMapper()

    @Test
    fun `the session is complete, as the recorder writes it`() {
        val names = RealPlucks.all.map { it.name }.toSet()
        for (string in RealPlucks.strings) {
            for (take in 1..2) for (touch in listOf(Touch.PICK, Touch.FINGER)) {
                assertTrue("missing guitar-$string-${touch.fileName}-$take", "guitar-$string-${touch.fileName}-$take" in names)
            }
            assertTrue("missing guitar-$string-pick-noisy-1", "guitar-$string-pick-noisy-1" in names)
        }
        for (pluck in RealPlucks.all) {
            assertEquals("${pluck.name}: rate", 48_000, pluck.sampleRate)
            assertEquals("${pluck.name}: length", 4f, pluck.wav.seconds, 0.05f)
            assertTrue("${pluck.name}: comment '${pluck.wav.comment}'", "Xiaomi" in pluck.wav.comment && "UNPROCESSED" in pluck.wav.comment && "48000 Hz" in pluck.wav.comment)
            // The pluck's attack is inside the file, after the recorder's first 50 ms of silence.
            assertTrue("${pluck.name}: onset at ${pluck.onsetSeconds} s", pluck.onsetSeconds in 0.05f..2.5f)
            assertTrue("${pluck.name}: peak ${pluck.peakDbfs} dBFS", pluck.peakDbfs in -32f..-10f)
        }
    }

    @Test
    fun `in a quiet room every take shows its own string from the first reading on, and never another note`() {
        for (pluck in RealPlucks.quietRoom) {
            val frames = pluck.frames().filter { pluck.secondsOf(it) >= 0f }
            val first = frames.firstOrNull { it.isShown }
            assertNotNull("${pluck.name}: nothing shown", first)
            assertTrue("${pluck.name}: first shown ${pluck.secondsOf(first!!)} s after the pluck", pluck.secondsOf(first) <= FIRST_READING_SECONDS)
            for (frame in frames.filter { it.isShown }) {
                val shown = mapper.nearestNote(frame.shownHz)
                assertEquals("${pluck.name} at ${"%.2f".format(pluck.secondsOf(frame))} s: ${frame.shownHz} Hz shown as", pluck.string, shown)
            }
        }
    }

    @Test
    fun `in a quiet room the reading half a second in is within a few cents of the settled one, and only settles from there`() {
        // The roadmap's 2 cents hold on the G and high E strings and the finger-plucked D. The low
        // strings glide: a hard pick on the owner's low E, A or D reads +7 to +9 cents half a second
        // in and +3 to +5 at 2 s (the whole waveform, without the refinement, twice that), and even
        // with a finger the E and A move 3 to 5 cents between 0.5 and 2 s. That is the string, not
        // the detector: its upper partials start 10–25 cents sharp and settle as they die away,
        // and on the low E partial 1 itself sits 5–8 cents under where partials 3 and up point
        // (the body's resonances pull it), so the reading, which weighs partials 1 to 3, settles
        // with them. See docs/architecture/signal-pipeline.md, *Accuracy target* and *Edge cases*.
        for (pluck in RealPlucks.quietRoom) {
            val frames = pluck.frames()
            val settled = pluck.settled(frames)
            assertNotNull("${pluck.name}: no settled reading", settled)
            val half = pluck.shownAt(frames, 0.5f)
            assertTrue("${pluck.name}: nothing shown 0.5 s in", half != null && half.isShown)
            val limit = SETTLE_CENTS.getValue(pluck.string.toString() to pluck.touch)
            val fromHalf = frames.filter { it.isShown && pluck.secondsOf(it) >= 0.5f && it.endSample <= settled!!.endSample }
            for (frame in fromHalf) {
                val delta = NoteMapper.centsBetween(frame.shownHz, settled!!.shownHz)
                assertTrue(
                    "${pluck.name} at ${"%.2f".format(pluck.secondsOf(frame))} s: $delta cents from the settled reading (limit $limit)",
                    abs(delta) <= limit,
                )
            }
        }
    }

    private companion object {
        /** Measured 0.10–0.20 s on the quiet takes; *Quick and steady on real strings* tightens it. */
        const val FIRST_READING_SECONDS = 0.25f

        /**
         * How far the reading may stray from the settled one from 0.5 s on, per open string and
         * touch: 2 cents where the roadmap's figure holds, else the measured worst on the owner's
         * guitar (2026-10-29, `RealPluckReport`'s "worst") plus a margin: E2 pick 4.4, finger 4.7;
         * A2 pick 6.5, finger 4.0; D3 pick 3.5; B3 pick 2.9, finger 2.6.
         */
        val SETTLE_CENTS: Map<Pair<String, Touch>, Float> = mapOf(
            ("E2" to Touch.PICK) to 5f, ("E2" to Touch.FINGER) to 5.5f,
            ("A2" to Touch.PICK) to 7.5f, ("A2" to Touch.FINGER) to 5f,
            ("D3" to Touch.PICK) to 4.5f, ("D3" to Touch.FINGER) to 2f,
            ("G3" to Touch.PICK) to 2f, ("G3" to Touch.FINGER) to 2f,
            ("B3" to Touch.PICK) to 3.5f, ("B3" to Touch.FINGER) to 3.5f,
            ("E4" to Touch.PICK) to 2f, ("E4" to Touch.FINGER) to 2f,
        )
    }
}
