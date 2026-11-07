package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.guide.GuideCue
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.GuideTiming
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** The watch's vibration guide in its pipeline (Phase 85): when it taps, that it stops, and that the reading holds across its taps. */
class WatchGuideTest {

    /**
     * Plays [signal] chunk by chunk in virtual time, as the fake recorder does, and counts the samples played, so a
     * pulse's moment is known to the sample. With a [buzz] (the motor heard through the case) each pulse played is
     * mixed into the sound from [latencyMillis] after it was asked for, once per tap of its pattern.
     */
    private class GuideRecorder(
        private val signal: FloatArray,
        private val buzz: ((GuidePulse) -> FloatArray)? = null,
        latencyMillis: Int = 0,
        override val config: AudioConfig = AudioConfig(),
    ) : AudioRecorder {
        private val latency = config.sampleRate * latencyMillis / 1000
        var played = 0
            private set
        private val heard = FloatArray(signal.size)

        /** The motor plays [pulse] now: its buzz goes into the sound still to come. */
        fun motor(pulse: GuidePulse) {
            val sound = buzz?.invoke(pulse) ?: return
            val from = played + latency
            for (i in sound.indices) if (from + i < heard.size) heard[from + i] += sound[i]
        }

        override fun chunks(): Flow<FloatArray> = flow {
            val chunk = FloatArray(config.chunkSize)
            while (played < signal.size) {
                delay(chunk.size * 1000L / config.sampleRate)
                for (i in chunk.indices) {
                    val n = played + i
                    chunk[i] = if (n < signal.size) signal[n] + heard[n] else 0f
                }
                played += chunk.size
                emit(chunk)
            }
        }
    }

    /** The guide's motor, recording what it plays and when (in seconds of sound), and how often it is stopped. */
    private class Motor(private val recorder: GuideRecorder) : WatchGuideVibrator {
        val played = mutableListOf<Pair<Float, GuidePulse>>()
        var stops = 0

        override fun play(pulse: GuidePulse) {
            if (pulse == GuidePulse.NONE) return
            played += recorder.played / RATE to pulse
            recorder.motor(pulse)
        }

        override fun stop() {
            stops++
        }
    }

    private class Run(val states: List<WatchTunerState>, val motor: Motor)

    private suspend fun TestScope.run(
        recorder: GuideRecorder,
        on: () -> Boolean = { true },
        speech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
    ): Run {
        val motor = Motor(recorder)
        val states = WatchPitchPipeline(
            recorder,
            dspDispatcher = StandardTestDispatcher(testScheduler),
            screenReaderSpeech = speech,
            guideVibrator = motor,
            guideOn = on,
        ).states().toList()
        return Run(states, motor)
    }

    private fun gaps(played: List<Pair<Float, GuidePulse>>): List<Float> = played.zipWithNext { a, b -> b.first - a.first }

    /** [hz] moved by [cents]. */
    private fun off(hz: Float, cents: Float): Float = hz * 2f.pow(cents / 1200f)

    @Test
    fun `the cue follows the word and waits within the last 5 cents`() {
        assertEquals(GuideCue.NONE, watchGuideCue(heard = false, inTune = false, cents = 0))
        assertEquals(GuideCue.IN_TUNE, watchGuideCue(heard = true, inTune = true, cents = 4))
        assertEquals(GuideCue.WAIT, watchGuideCue(heard = true, inTune = false, cents = -5))
        assertEquals(GuideCue.WAIT, watchGuideCue(heard = true, inTune = false, cents = 3))
        assertEquals(GuideCue.TIGHTEN, watchGuideCue(heard = true, inTune = false, cents = -6))
        assertEquals(GuideCue.LOOSEN, watchGuideCue(heard = true, inTune = false, cents = 6))
    }

    @Test
    fun `the default follows the screen reader until the player chooses`() {
        assertEquals(false, watchVibrationGuideOn(null, screenReaderOn = false))
        assertEquals(true, watchVibrationGuideOn(null, screenReaderOn = true))
        assertEquals(false, watchVibrationGuideOn(false, screenReaderOn = true))
        assertEquals(true, watchVibrationGuideOn(true, screenReaderOn = false))
    }

    @Test
    fun `a flat string taps once a group, as soon as it is heard and then at the watch's pace`() = runTest {
        val run = run(GuideRecorder(TestSignals.sawtooth(off(110f, -20f), length = 48_000 * 5)))
        val played = run.motor.played
        assertTrue("taps: $played", played.size >= 3)
        assertTrue(played.all { it.second == GuidePulse.TIGHTEN })
        assertTrue("first tap at ${played.first().first} s", played.first().first < 0.4f)
        val expected = GuideTiming.WATCH.intervalSeconds(-20f)
        gaps(played).forEach { assertEquals(expected, it, 0.06f) }
    }

    @Test
    fun `a sharp string taps twice a group`() = runTest {
        val played = run(GuideRecorder(TestSignals.sawtooth(off(196f, 25f), length = 48_000 * 3))).motor.played
        assertTrue("taps: $played", played.isNotEmpty())
        assertTrue(played.all { it.second == GuidePulse.LOOSEN })
    }

    @Test
    fun `further off the groups come slower`() = runTest {
        val far = gaps(run(GuideRecorder(TestSignals.sawtooth(off(146.83f, -40f), length = 48_000 * 6))).motor.played).average()
        val near = gaps(run(GuideRecorder(TestSignals.sawtooth(off(146.83f, -8f), length = 48_000 * 6))).motor.played).average()
        assertTrue("$far s far off, $near s near", far > near + 0.4)
    }

    @Test
    fun `nothing in silence, and nothing once the note has gone`() = runTest {
        assertTrue(run(GuideRecorder(FloatArray(48_000 * 2))).motor.played.isEmpty())

        val flat = TestSignals.sawtooth(off(110f, -20f), length = 48_000 * 2)
        val recorder = GuideRecorder(flat + FloatArray(48_000 * 3))
        val run = run(recorder)
        val listeningAt = (run.motor.played.lastOrNull()?.first ?: 0f)
        assertTrue("taps came with the note: ${run.motor.played}", run.motor.played.isNotEmpty())
        assertEquals(WatchTunerState.Listening, run.states.last())
        // The note ends at 2 s and is shown at most its hold longer: no tap after that.
        assertTrue("last tap at $listeningAt s", listeningAt < 2f + 0.9f)
    }

    @Test
    fun `in tune buzzes once, in place of the screen's tap, then the guide is quiet`() = runTest {
        val run = run(GuideRecorder(TestSignals.sawtooth(220f, length = 48_000 * 4)))
        assertEquals(listOf(GuidePulse.IN_TUNE), run.motor.played.map { it.second })
        val reading = run.states.filterIsInstance<WatchTunerState.Detected>().last()
        assertEquals(WatchTunerState.Detected(Note.of(PitchClass.A, 3), 0, inTune = true), reading)
    }

    @Test
    fun `off, it plays nothing and the watch reads as before`() = runTest {
        val signal = TestSignals.sawtooth(off(110f, -20f), length = 48_000 * 2)
        val off = run(GuideRecorder(signal), on = { false })
        assertTrue(off.motor.played.isEmpty())
        val before = WatchPitchPipeline(GuideRecorder(signal), dspDispatcher = StandardTestDispatcher(testScheduler)).states().toList()
        assertEquals(before, off.states)
    }

    @Test
    fun `turned off, it stops at once and plays no more`() = runTest {
        val recorder = GuideRecorder(TestSignals.sawtooth(off(110f, -20f), length = 48_000 * 5))
        var on = true
        val run = run(recorder, on = { on.also { if (recorder.played >= 48_000 * 2) on = false } })
        assertTrue(run.motor.played.isNotEmpty())
        assertTrue("stopped", run.motor.stops >= 1)
        assertTrue("nothing after 2 s: ${run.motor.played}", run.motor.played.all { it.first <= 2.05f })
    }

    @Test
    fun `the tuner stopping listening stops it`() = runTest {
        val recorder = GuideRecorder(TestSignals.sawtooth(off(110f, -20f), length = 48_000 * 5))
        val motor = Motor(recorder)
        // The screen goes after the first reading.
        WatchPitchPipeline(recorder, dspDispatcher = StandardTestDispatcher(testScheduler), guideVibrator = motor, guideOn = { true })
            .states().take(2).toList()
        assertEquals(1, motor.stops)
    }

    @Test
    fun `while the screen reader speaks the guide waits`() = runTest {
        val speech = FakeScreenReaderSpeech()
        backgroundScope.launch { delay(1_000); speech.isSpeaking = true }
        backgroundScope.launch { delay(2_500); speech.isSpeaking = false }
        val run = run(GuideRecorder(TestSignals.sawtooth(off(110f, -20f), length = 48_000 * 5)), speech = speech)
        val tail = ScreenReaderSpeech.TAIL_SECONDS
        // Virtual time runs a little ahead of the sound (21 ms per 21.3 ms chunk): a frame's slack either side.
        assertTrue(
            "taps during the voice: ${run.motor.played}",
            run.motor.played.none { it.first > 1.05f && it.first < 2.5f + tail - 0.05f },
        )
        assertTrue("taps again after it", run.motor.played.any { it.first >= 2.5f + tail - 0.05f })
    }

    /**
     * The microphone hears every pulse ([motorBuzz], the in-tune tap's model, each of the pattern's taps): across them
     * the reading holds the string's note and its cents within one of the clean reading, never "Play a note" while the
     * string is held, never another note, on every string, the motor at half the note's level up to twice it.
     */
    @Test
    fun `the reading holds steady across the guide's taps`() = runTest {
        for (hz in floatArrayOf(82.41f, 110f, 146.83f, 196f, 246.94f, 329.63f)) {
            for (cents in floatArrayOf(-20f, 15f)) {
                val signal = TestSignals.sawtooth(off(hz, cents), length = 48_000 * 4)
                val clean = run(GuideRecorder(signal), on = { false }).states.filterIsInstance<WatchTunerState.Detected>().last()
                val peak = signal.maxOf { abs(it) }
                for (level in floatArrayOf(0.5f, 2f)) {
                    for (latency in intArrayOf(45, 90)) {
                        val buzzed = run(GuideRecorder(signal, { pulse -> motorBuzz(pulse, 200f, level * peak) }, latency))
                        val name = "$hz Hz $cents cents, buzz at $level×, $latency ms late"
                        assertTrue("$name: taps ${buzzed.motor.played}", buzzed.motor.played.size >= 2)
                        val shown = buzzed.states.drop(buzzed.states.indexOfFirst { it is WatchTunerState.Detected })
                        for (state in shown) {
                            assertTrue("$name: $state", state is WatchTunerState.Detected && state.note == clean.note)
                            assertTrue("$name: $state, clean ${clean.cents}", abs((state as WatchTunerState.Detected).cents - clean.cents) <= 1)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a plucked string dying away under the taps is never read as another note`() = runTest {
        for (hz in floatArrayOf(110f, 196f, 246.94f)) {
            val signal = TestSignals.pluck(off(hz, -20f), length = 48_000 * 4, decaySeconds = 1.5)
            val peak = signal.maxOf { abs(it) }
            for (buzzHz in floatArrayOf(150f, 250f)) {
                val run = run(GuideRecorder(signal, { pulse -> motorBuzz(pulse, buzzHz, peak) }, latencyMillis = 90))
                val notes = run.states.filterIsInstance<WatchTunerState.Detected>().map { it.note }.toSet()
                assertTrue("$hz Hz, $buzzHz Hz buzz: $notes", notes.size == 1)
            }
        }
    }

    /**
     * The motor heard for [pulse]: each of its taps a sine at [hz] at [level] for the tap's length, then fading (7 ms
     * decay) for 50 ms, as `WatchPitchPipelineTest` models the in-tune tap (measured on the Galaxy Watch 5).
     */
    private fun motorBuzz(pulse: GuidePulse, hz: Float, level: Float): FloatArray {
        val timing = GuideTiming.WATCH
        val taps = when (pulse) {
            GuidePulse.NONE -> return FloatArray(0)
            GuidePulse.TIGHTEN -> listOf(0f to timing.tapSeconds)
            GuidePulse.LOOSEN -> listOf(0f to timing.tapSeconds, timing.pairSeconds to timing.tapSeconds)
            GuidePulse.IN_TUNE -> listOf(0f to timing.buzzSeconds)
        }
        val ring = (RATE * 0.05f).toInt()
        val length = taps.maxOf { ((it.first + it.second) * RATE).toInt() } + ring
        val sound = FloatArray(length)
        for ((start, seconds) in taps) {
            val from = (start * RATE).toInt()
            val drive = (seconds * RATE).toInt()
            for (i in 0 until drive + ring) {
                val fade = if (i < drive) 1.0 else exp(-(i - drive) / (RATE * 0.007))
                sound[from + i] += (level * kotlin.math.sin(2 * Math.PI * hz * (from + i) / RATE) * fade).toFloat()
            }
        }
        return sound
    }

    private companion object {
        const val RATE = 48_000f
    }
}
