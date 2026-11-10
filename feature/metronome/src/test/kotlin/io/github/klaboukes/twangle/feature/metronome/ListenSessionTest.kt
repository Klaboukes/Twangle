package io.github.klaboukes.twangle.feature.metronome

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.TempoDetector
import io.github.klaboukes.twangle.core.dsp.metronome.TempoStatus
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Stop and Listen tapped quickly (Phase 89): each listening has its own tempo detector, and the beat grid of one that
 * was stopped never shows over the next one's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenSessionTest {

    private val main = StandardTestDispatcher()
    private val rate = AudioConfig.DEFAULT_SAMPLE_RATE

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(dsp: CoroutineDispatcher, detectorFor: (Int) -> TempoDetector) = MetronomeViewModel(
        FakeMetronomePlayer(),
        FakeSettingsRepository(),
        savedBpm = 80,
        savedMeter = Meter.FOUR_FOUR,
        recorder = EndlessRecorder(),
        dspDispatcher = dsp,
        nanoTime = { testScheduler.currentTime * 1_000_000L },
        detectorFor = detectorFor,
    )

    @Test
    fun `each listening makes its own detector`() = runTest(main) {
        val made = mutableListOf<TempoDetector>()
        val vm = viewModel(main) { Settled(it).also { d -> made += d } }
        vm.listen()
        advanceTimeBy(100)
        vm.stopListening()
        vm.listen()
        advanceTimeBy(100)
        vm.stopListening()
        assertEquals("one per listening", 2, made.size)
        assertNotSame("never shared", made[0], made[1])
    }

    // A Stop's cancel reaches the DSP thread only once the chunk in hand is done: the stopped listening's beats from
    // that chunk are dropped, the next listening's lights start dark.
    @Test
    fun `a stopped listening's last beats never show over the next one`() = runTest(main) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = Settled(rate, hold = true)
            val second = Settled(rate, beats = false)
            val detectors = ArrayDeque(listOf<TempoDetector>(first, second))
            val vm = viewModel(executor.asCoroutineDispatcher()) { detectors.removeFirst() }
            vm.listen()
            runCurrent()
            assertTrue("the first listening has its chunk", first.entered.await(5, TimeUnit.SECONDS))
            vm.stopListening()
            vm.listen()
            runCurrent()
            // The first listening finishes its chunk (it hears a steady beat), then the second begins.
            first.release.countDown()
            assertTrue("the second listening has begun", second.entered.await(5, TimeUnit.SECONDS))
            assertNull("no beat lights from the stopped listening", vm.heard.value)
            vm.stopListening()
            runCurrent()
        } finally {
            // The DSP thread's last chunk done before Dispatchers.Main is reset: a chunk still running then hands its
            // result to a Main that is gone, an error the next test class reported as its own (now and then, until
            // 2026-11-10).
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            runCurrent()
        }
    }

    /** A microphone that sends a 10 ms chunk of silence every 10 ms until it is let go of. */
    private inner class EndlessRecorder : AudioRecorder {
        override val config = AudioConfig(rate, rate / 100)

        override fun chunks(): Flow<FloatArray> = flow {
            val chunk = FloatArray(config.chunkSize)
            while (true) {
                emit(chunk)
                delay(10)
            }
        }
    }

    /**
     * A detector settling on 120 BPM with a beat at sample 100 (none with [beats] false). With [hold], its first chunk
     * waits for [release]; [entered] opens at its first chunk.
     */
    private class Settled(override val sampleRate: Int, private val hold: Boolean = false, beats: Boolean = true) :
        TempoDetector {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override var samplesProcessed = 0L

        override fun process(samples: FloatArray, offset: Int, length: Int) {
            entered.countDown()
            if (hold) release.await(5, TimeUnit.SECONDS)
            samplesProcessed += length
        }

        override fun reset() {
            samplesProcessed = 0L
        }

        override val status = TempoStatus.SETTLING
        override val bpm = 120
        override val exactBpm = 120f
        override val halfBpm = 60
        override val doubleBpm = 240
        override val alternativeBpm = 60
        override val confidence = 0.9f
        override val steadySeconds = 1f
        override val lastBeatSample = if (beats) 100L else -1L
        override val beatIntervalSamples = if (beats) sampleRate / 2f else 0f
    }
}
