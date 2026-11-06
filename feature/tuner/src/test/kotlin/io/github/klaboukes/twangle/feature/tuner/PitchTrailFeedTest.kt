package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class PitchTrailFeedTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun detected(frequencyHz: Float, cents: Float, mode: TunerMode = TunerMode.CHROMATIC, a4Hz: Float = 440f) =
        TunerUiState.Detected(
            mode = mode, tuning = Tunings.STANDARD, a4Hz = a4Hz, noteName = "A", octave = 2,
            frequencyHz = frequencyHz, cents = cents, targetString = null, inTune = abs(cents) < 5f,
        )

    // ---- The note aimed at ----

    @Test
    fun `the target is the note the cents are measured from`() {
        // A2 (MIDI 45) 30 cents sharp, and 40 cents flat.
        assertEquals(45, PitchTrailFeed.targetMidiOf(detected(110f * 2f.pow(0.3f / 12f), 30f)))
        assertEquals(45, PitchTrailFeed.targetMidiOf(detected(110f * 2f.pow(-0.4f / 12f), -40f)))
        // A string tuned far off in guitar mode: 150 cents below the A string is still aimed at A2.
        assertEquals(45, PitchTrailFeed.targetMidiOf(detected(110f * 2f.pow(-1.5f / 12f), -150f, TunerMode.AUTO)))
        // Another concert pitch.
        assertEquals(69, PitchTrailFeed.targetMidiOf(detected(432f, 0f, a4Hz = 432f)))
    }

    @Test
    fun `notes become entries and anything else a gap, on a steady clock`() {
        val feed = PitchTrailFeed(PitchHistory())
        val frame = 0.02f
        feed.record(detected(110f, 0f), frame)
        feed.record(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), frame)
        feed.gap(frame)
        feed.record(detected(110f * 2f.pow(0.1f / 12f), 10f), frame)
        val history = feed.history
        assertEquals(4L, history.end)
        assertEquals(45, history.targetAt(0))
        assertTrue(history.inTuneAt(0))
        assertTrue(history.isGapAt(1))
        assertTrue(history.isGapAt(2))
        assertEquals(10f, history.centsAt(3), 0f)
        for (i in 0L until 4L) assertEquals((i + 1) * frame, history.timeAt(i), 1e-6f)
        // A new session starts afresh, and the clock goes on.
        feed.start()
        assertTrue(history.isEmpty)
        feed.gap(frame)
        assertEquals(5 * frame, history.timeAt(4), 1e-6f)
    }
}
