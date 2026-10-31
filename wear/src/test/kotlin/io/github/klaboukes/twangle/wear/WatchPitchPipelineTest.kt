package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2

class WatchPitchPipelineTest {

    /** Every state the pipeline sends while the fake recorder plays [signal] once. */
    private suspend fun TestScope.statesFor(signal: FloatArray, config: AudioConfig = AudioConfig()): List<WatchTunerState> =
        WatchPitchPipeline(FakeAudioRecorder(signal, config), dspDispatcher = StandardTestDispatcher(testScheduler))
            .states().toList()

    private fun List<WatchTunerState>.lastDetected(): WatchTunerState.Detected {
        val detected = filterIsInstance<WatchTunerState.Detected>()
        if (detected.isEmpty()) fail("no note detected in $this")
        return detected.last()
    }

    private fun centsBetween(hz: Float, referenceHz: Float): Float = 1200f * log2(hz / referenceHz)

    @Test
    fun `starts listening, then names A4 in tune`() = runTest {
        val states = statesFor(TestSignals.sine(440f, length = 48_000))

        assertEquals(WatchTunerState.Listening, states.first())
        assertEquals(WatchTunerState.Detected(Note.of(PitchClass.A, 4), 0, inTune = true), states.lastDetected())
    }

    @Test
    fun `cents off match the played pitch within one cent`() = runTest {
        for (hz in floatArrayOf(82.41f * 1.006f, 196f * 0.99f, 445f, 659.26f * 1.02f)) {
            val reading = statesFor(TestSignals.sawtooth(hz, length = 48_000)).lastDetected()
            val target = 440f * Math.pow(2.0, (reading.note.midi - 69) / 12.0).toFloat()
            val expected = centsBetween(hz, target)
            assertTrue("$hz Hz read ${reading.cents} cents, expected $expected", abs(reading.cents - expected) <= 1f)
        }
    }

    @Test
    fun `a steady note sends only a few states, not one per frame`() = runTest {
        // Two seconds of a held note is about 94 frames.
        val states = statesFor(TestSignals.sine(196f, length = 96_000))

        assertEquals(Note.of(PitchClass.G, 3), states.lastDetected().note)
        assertTrue("sent ${states.size} states", states.size <= 6)
    }

    @Test
    fun `silence stays listening`() = runTest {
        assertEquals(listOf(WatchTunerState.Listening), statesFor(FloatArray(48_000)))
    }

    @Test
    fun `a watch microphone at 16 kHz reads the same note`() = runTest {
        val rate = 16_000
        val states = statesFor(TestSignals.sine(220f, length = rate, sampleRate = rate), AudioConfig.forSampleRate(rate))

        assertEquals(WatchTunerState.Detected(Note.of(PitchClass.A, 3), 0, inTune = true), states.lastDetected())
    }

    @Test
    fun `a microphone that opens at 16 kHz instead of 48 kHz reads the same note`() = runTest {
        val rate = 16_000
        val recorder = FakeAudioRecorder(
            TestSignals.sine(220f, length = rate, sampleRate = rate),
            AudioConfig(),
            openedConfig = AudioConfig.forSampleRate(rate),
        )
        val states = WatchPitchPipeline(recorder, dspDispatcher = StandardTestDispatcher(testScheduler)).states().toList()

        assertEquals(WatchTunerState.Detected(Note.of(PitchClass.A, 3), 0, inTune = true), states.lastDetected())
    }

    @Test
    fun `in tune only after 300 ms within 5 cents`() = runTest {
        val states = statesFor(TestSignals.sine(440f * 1.002f, length = 48_000)) // +3.5 cents
        val readings = states.filterIsInstance<WatchTunerState.Detected>()

        assertFalse("not in tune at once", readings.first().inTune)
        assertTrue(readings.last().inTune)
    }

    @Test
    fun `a note 20 cents off is never in tune`() = runTest {
        val states = statesFor(TestSignals.sine(440f * 1.0116f, length = 48_000)) // +20 cents

        assertTrue(states.filterIsInstance<WatchTunerState.Detected>().none { it.inTune })
        assertEquals(20, states.lastDetected().cents)
    }

    @Test
    fun `a short dropout of the same note keeps the reading and its glow`() = runTest {
        val a4 = TestSignals.sine(440f, length = 48_000)
        val gap = FloatArray(48_000 * 4 / 10) // 400 ms of silence
        val states = statesFor(a4 + gap + a4)
        val afterFirstInTune = states.drop(states.indexOfFirst { it is WatchTunerState.Detected && it.inTune })

        assertTrue("never back to listening: $afterFirstInTune", afterFirstInTune.none { it == WatchTunerState.Listening })
        assertTrue(afterFirstInTune.filterIsInstance<WatchTunerState.Detected>().all { it.inTune })
    }

    @Test
    fun `after the note stops it goes back to listening within a second or so`() = runTest {
        val states = statesFor(TestSignals.sine(440f, length = 48_000) + FloatArray(48_000 * 12 / 10))

        assertEquals(WatchTunerState.Listening, states.last())
    }

    @Test
    fun `a new note has to earn in tune again`() = runTest {
        val a4 = TestSignals.sine(440f, length = 48_000)
        val e4 = TestSignals.sine(329.63f, length = 48_000)
        val states = statesFor(a4 + e4)
        val firstE4 = states.filterIsInstance<WatchTunerState.Detected>().first { it.note == Note.of(PitchClass.E, 4) }

        assertFalse(firstE4.inTune)
        assertTrue(states.lastDetected().inTune)
    }

    @Test
    fun `an unsupported sample rate fails the flow`() = runTest {
        val pipeline = WatchPitchPipeline(FakeAudioRecorder(FloatArray(10), AudioConfig(sampleRate = 4_000)))
        try {
            pipeline.states().toList()
            fail("expected a failure")
        } catch (e: AudioRecorderException.InitializationFailed) {
            assertTrue(e.message.orEmpty().contains("4000"))
        }
    }

    /**
     * The microphone hears the watch's own motor. A watch's vibration motor (a linear resonant
     * actuator) rings at its resonance, about 150–250 Hz; the tap drives it for [TAP_MILLIS], then
     * it dies away; the microphone sits in the same case, so it picks the buzz up through the body.
     * Modelled as a sine at [hz] at full strength for [TAP_MILLIS], then fading (8 ms decay) for
     * 40 ms more, its peak [level] times the note's peak.
     */
    private fun motorBuzz(hz: Float, level: Float, notePeak: Float): FloatArray {
        val drive = (48_000 * TAP_MILLIS / 1000).toInt()
        val sine = TestSignals.sine(hz, length = drive + 48_000 * 40 / 1000, amplitude = level * notePeak)
        return FloatArray(sine.size) { i -> if (i < drive) sine[i] else sine[i] * exp(-(i - drive) / (48_000 * 0.008)).toFloat() }
    }

    /**
     * Plays [signal] like the fake recorder, chunk by chunk in virtual time, and mixes [buzz] in from
     * [latencyMillis] after [tap] was called (the motor starting a little after the tap is asked for):
     * the loop of a real watch, whose microphone hears the tap its own reading set off.
     */
    private class WristRecorder(
        private val signal: FloatArray,
        private val buzz: FloatArray,
        latencyMillis: Int,
        override val config: AudioConfig = AudioConfig(),
    ) : AudioRecorder {
        private val latency = config.sampleRate * latencyMillis / 1000
        private var played = 0
        var buzzAt = -1
            private set

        fun tap() {
            // Only the first tap buzzes here; the test counts any further ones.
            if (buzzAt < 0) buzzAt = played + latency
        }

        override fun chunks(): Flow<FloatArray> = flow {
            val chunk = FloatArray(config.chunkSize)
            while (played < signal.size) {
                // Waits first, so a tap asked for meanwhile starts after the chunks heard so far.
                delay(chunk.size * 1000L / config.sampleRate)
                for (i in chunk.indices) {
                    val n = played + i
                    var sample = if (n < signal.size) signal[n] else 0f
                    if (buzzAt in 0..n && n - buzzAt < buzz.size) sample += buzz[n - buzzAt]
                    chunk[i] = sample
                }
                played += chunk.size
                emit(chunk)
            }
        }
    }

    private class TapRun(val taps: Int, val buzzAt: Int, val afterTap: List<WatchTunerState>)

    /** Runs [signal] through the pipeline and the watch's tap rule, the motor buzzing on the tap. */
    private suspend fun TestScope.tapRun(signal: FloatArray, buzz: FloatArray, latencyMillis: Int): TapRun {
        val recorder = WristRecorder(signal, buzz, latencyMillis)
        val rule = WatchInTuneTap()
        var taps = 0
        val afterTap = mutableListOf<WatchTunerState>()
        WatchPitchPipeline(recorder, dspDispatcher = StandardTestDispatcher(testScheduler)).states().collect { state ->
            if (taps > 0) afterTap += state
            if (rule.update(state, testScheduler.currentTime)) {
                taps++
                recorder.tap()
            }
        }
        return TapRun(taps, recorder.buzzAt, afterTap)
    }

    /**
     * Steady across the tap: one tap only (the buzz never earns a second), and every reading after it
     * the same note, still in tune, within a cent of [cleanCents] (the rounding of the whole cents),
     * and at [cleanCents] once the buzz has passed. Without the pipeline looking away during the tap
     * a buzz near a string's own partials nudged the number by up to 3 cents for a few frames (a
     * 250 Hz buzz on the plucked D string, 60 ms late), though the note always stayed in tune.
     */
    private fun assertSteadyAcrossTheTap(name: String, signal: FloatArray, buzz: FloatArray, latencyMillis: Int, cleanCents: Int) = runTest {
        val run = tapRun(signal, buzz, latencyMillis)
        assertEquals("$name: taps", 1, run.taps)
        assertTrue("$name: the buzz came while the note rang (at ${run.buzzAt})", run.buzzAt in 0 until signal.size - buzz.size)
        for (state in run.afterTap) {
            assertTrue("$name: after the tap $state", state is WatchTunerState.Detected && state.inTune)
            val cents = (state as WatchTunerState.Detected).cents
            assertTrue("$name: read $cents cents after the tap, $cleanCents before", abs(cents - cleanCents) <= 1)
        }
        val last = run.afterTap.lastOrNull() as WatchTunerState.Detected?
        assertTrue("$name: back at $cleanCents cents, not ${last?.cents}", last == null || last.cents == cleanCents)
    }

    @Test
    fun `the wrist tap leaves the reading steady on every string`() {
        // Each string of a guitar in standard tuning, held (a bright sawtooth) and plucked (dying
        // away), the motor buzzing across a watch motor's range at half the note's level, starting at
        // once, 30 or 60 ms late.
        for (hz in floatArrayOf(82.41f, 110f, 146.83f, 196f, 246.94f, 329.63f)) {
            val held = TestSignals.sawtooth(hz, length = 72_000)
            val plucked = TestSignals.pluck(hz, length = 72_000, decaySeconds = 2.0)
            for ((kind, signal) in listOf("held" to held, "plucked" to plucked)) {
                val peak = signal.maxOf { abs(it) }
                for (buzzHz in floatArrayOf(150f, 175f, 200f, 225f, 250f)) {
                    for (latency in intArrayOf(0, 30, 60)) {
                        assertSteadyAcrossTheTap(
                            "$kind $hz Hz, $buzzHz Hz buzz $latency ms late",
                            signal,
                            motorBuzz(buzzHz, level = 0.5f, notePeak = peak),
                            latency,
                            cleanCents = 0,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the wrist tap leaves a note a few cents off where it was`() {
        // +3.5 cents earns "in tune" (within 5); the tap must not push it out (past 7).
        val signal = TestSignals.sawtooth(196f * 1.002f, length = 72_000)
        var clean = 0
        runTest { clean = statesFor(signal).lastDetected().cents }
        assertSteadyAcrossTheTap("G3 +3.5 cents", signal, motorBuzz(175f, level = 0.5f, notePeak = 0.5f), latencyMillis = 15, cleanCents = clean)
    }

    @Test
    fun `even a buzz as loud as the note or twice as loud leaves the reading steady`() {
        // A margin: the motor heard as loud as the string, or louder, on the strings round its range.
        for (hz in floatArrayOf(110f, 146.83f, 196f, 246.94f)) {
            val plucked = TestSignals.pluck(hz, length = 72_000, decaySeconds = 2.0)
            val peak = plucked.maxOf { abs(it) }
            for (level in floatArrayOf(1f, 2f)) {
                for (buzzHz in floatArrayOf(150f, 200f, 250f)) {
                    assertSteadyAcrossTheTap("$hz Hz, $buzzHz Hz buzz at $level×", plucked, motorBuzz(buzzHz, level, peak), latencyMillis = 20, cleanCents = 0)
                }
            }
        }
    }
}
