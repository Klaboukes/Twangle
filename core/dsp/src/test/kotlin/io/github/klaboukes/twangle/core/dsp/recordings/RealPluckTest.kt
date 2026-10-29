package io.github.klaboukes.twangle.core.dsp.recordings

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.centsBetween
import io.github.klaboukes.twangle.core.dsp.music.nearestNote
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The tuner on a real guitar: every recording in `src/test/resources/fixtures/` (the owner's
 * steel-string acoustic through the Xiaomi 12X's own recorder, 2026-10-29; docs/architecture/testing.md,
 * *Real recordings*) through the app's whole chain ([io.github.klaboukes.twangle.core.dsp.pitch.TunerChain])
 * at the file's rate: the right note from the first reading and never another, how soon the needle
 * shows and settles, and in the noisy room the needle holding the string as it sinks into the TV
 * and then going blank. The figures held here are what these recordings measured, with a margin
 * ([RealPluckReport] prints them); they are bounds on this guitar through this phone, never constants
 * in the detector.
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
    fun `every take, quiet or noisy, shows its own string from the first reading on, and never another note`() {
        for (pluck in RealPlucks.all) {
            val frames = pluck.frames().filter { pluck.secondsOf(it) >= 0f }
            val first = frames.firstOrNull { it.isShown }
            assertNotNull("${pluck.name}: nothing shown", first)
            for (frame in frames.filter { it.isShown }) {
                val shown = mapper.nearestNote(frame.shownHz)
                assertEquals("${pluck.name} at ${"%.2f".format(pluck.secondsOf(frame))} s: ${frame.shownHz} Hz shown as", pluck.string, shown)
            }
        }
    }

    @Test
    fun `the needle catches each string quickly and settles within the measured time`() {
        // The first reading comes 0.10–0.20 s after the pluck in the quiet room, the one after a
        // first frame or two with no pitch (the pick's noise, the low strings' first periods), then
        // the three agreeing frames the onset asks for; in the noisy room the picked A took 0.30 s
        // (the TV through its attack). Settling is the string's: a hard pick on a low string glides
        // down with its partials for over a second (docs/architecture/signal-pipeline.md, *Accuracy
        // target*), the G and high E strings and a finger on the D settle within half a second.
        for (pluck in RealPlucks.all) {
            val frames = pluck.frames()
            val first = pluck.firstShown(frames)
            assertNotNull("${pluck.name}: nothing shown", first)
            val firstLimit = if (pluck.touch.quietRoom) FIRST_READING_SECONDS else FIRST_READING_NOISY_SECONDS
            assertTrue("${pluck.name}: first shown ${pluck.secondsOf(first!!)} s after the pluck (limit $firstLimit)", pluck.secondsOf(first) <= firstLimit)
            if (!pluck.touch.quietRoom) continue
            val settle = pluck.settleSeconds(frames, SETTLE_WITHIN_CENTS)
            val limit = SETTLE_SECONDS.getValue(pluck.string.toString() to pluck.touch)
            assertTrue("${pluck.name}: settled within $SETTLE_WITHIN_CENTS cents only from $settle s (limit $limit)", settle <= limit)
        }
    }

    @Test
    fun `in the noisy room the needle holds the string as it sinks into the TV, then goes blank`() {
        // The TV 20–25 dB under the pluck. As each string sank to within about 8 dB of it, the
        // detector read a whole number of the string's periods (G2 and then C2 for the G string, B2
        // for the B, A2 for the high E: a period the string and the TV's voice share) for 7–16
        // frames, above the gate, and the needle used to show them. Now a reading like that without
        // an attack is held as the string (PitchSmoother), so the needle shows the string to the
        // end and then nothing: the test above holds the note, this one the blank.
        for (pluck in RealPlucks.all.filter { !it.touch.quietRoom }) {
            val frames = pluck.frames()
            val last = frames.last { it.isShown }
            val after = frames.filter { it.endSample > last.endSample }
            assertTrue("${pluck.name}: shown again after the string was lost at ${pluck.secondsOf(last)} s", after.none { it.isShown })
            // The reading was lost to the detector finding nothing, not to another note.
            val lostTo = frames.filter { it.endSample > last.endSample }.take(PitchSmoother.DEFAULT_MAX_MISSED_FRAMES + 1)
            assertTrue("${pluck.name}: lost at ${pluck.secondsOf(last)} s with a pitch still found", lostTo.none { it.rawHz > 0f })
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
        /** Measured 0.10–0.20 s on the quiet takes (the low E 0.10–0.15, the D 0.16–0.20). */
        const val FIRST_READING_SECONDS = 0.25f

        /** Measured 0.11–0.16 s on the noisy takes but the picked A, 0.30 s. */
        const val FIRST_READING_NOISY_SECONDS = 0.35f

        /** The Precise in-tune window: the needle has settled once it stays this close to where it ends up. */
        const val SETTLE_WITHIN_CENTS = 2f

        /**
         * By when the reading stays within [SETTLE_WITHIN_CENTS] of the settled one, per open string
         * and touch: the measured worst of the two takes (`RealPluckReport`'s "settles", 2026-10-29)
         * plus a margin. E2 pick 1.40, finger 1.43; A2 pick 1.60, finger 1.05; D3 pick 0.93, finger
         * 0.20; G3 pick 0.38, finger 0.24; B3 pick 0.65, finger 0.64; E4 pick 0.43, finger 0.16.
         */
        val SETTLE_SECONDS: Map<Pair<String, Touch>, Float> = mapOf(
            ("E2" to Touch.PICK) to 1.6f, ("E2" to Touch.FINGER) to 1.6f,
            ("A2" to Touch.PICK) to 1.8f, ("A2" to Touch.FINGER) to 1.25f,
            ("D3" to Touch.PICK) to 1.1f, ("D3" to Touch.FINGER) to 0.3f,
            ("G3" to Touch.PICK) to 0.5f, ("G3" to Touch.FINGER) to 0.35f,
            ("B3" to Touch.PICK) to 0.8f, ("B3" to Touch.FINGER) to 0.8f,
            ("E4" to Touch.PICK) to 0.55f, ("E4" to Touch.FINGER) to 0.25f,
        )

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
