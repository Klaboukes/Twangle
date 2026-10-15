package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * [OnsetTempoDetector] on synthetic music whose tempo is known: click tracks and drum grooves
 * across 30–250 BPM, with noise, loose timing and swing. Tempos must be right within ±1 BPM after
 * [LISTEN_SECONDS] (the screen listens for up to 15 s).
 */
class OnsetTempoDetectorTest {

    private val rate = BeatSignals.SAMPLE_RATE

    // Feeds [signal] and returns the detector, after checking it settled on [expected] ± 1 BPM.
    private fun assertTempo(name: String, expected: Double, signal: FloatArray, sampleRate: Int = rate): OnsetTempoDetector {
        val detector = OnsetTempoDetector(sampleRate)
        BeatSignals.feed(detector, signal)
        val message = "$name at $expected BPM: heard ${detector.bpm} (${detector.exactBpm}), ${detector.status}, confidence ${detector.confidence}"
        assertTrue(message, abs(detector.bpm - expected) <= 1.0)
        assertTrue(message, abs(detector.exactBpm - expected) <= 1.0)
        assertTrue(message, detector.status == TempoStatus.STEADY || detector.status == TempoStatus.SETTLING)
        return detector
    }

    @Test
    fun `click tracks across 30 to 250 BPM, with noise`() {
        for (bpm in listOf(30.0, 33.0, 41.5, 52.0, 60.0, 72.0, 88.0, 97.3, 110.0, 120.0, 133.0, 150.0, 168.0, 185.0, 200.0, 222.0, 240.0, 250.0)) {
            assertTempo("clicks", bpm, TestSignals.withNoise(BeatSignals.clicks(bpm, LISTEN_SECONDS), snrDb = 10f))
        }
    }

    @Test
    fun `the metronome's own accented clicks give the beat, not the bar`() {
        for ((bpm, meter) in listOf(60 to Meter.FOUR_FOUR, 96 to Meter.THREE_FOUR, 144 to Meter.FOUR_FOUR, 210 to Meter.TWO_FOUR)) {
            val track = ClickTrack(rate, bpm, meter)
            val signal = FloatArray((LISTEN_SECONDS * rate).toInt())
            track.render(signal)
            assertTempo("ClickTrack in $meter", bpm.toDouble(), TestSignals.withNoise(signal, snrDb = 10f))
        }
    }

    @Test
    fun `rock and four-on-the-floor drums, with chords and noise`() {
        for (bpm in listOf(70.0, 85.0, 96.0, 110.0, 120.0, 128.0, 140.0, 160.0, 175.0, 200.0)) {
            assertTempo("rock", bpm, TestSignals.withNoise(BeatSignals.drums(bpm, LISTEN_SECONDS), snrDb = 15f))
        }
        for (bpm in listOf(96.0, 110.0, 124.0, 140.0, 160.0, 175.0, 200.0)) {
            val drums = BeatSignals.drums(bpm, LISTEN_SECONDS, BeatSignals.Groove.FOUR_ON_THE_FLOOR)
            assertTempo("four on the floor", bpm, TestSignals.withNoise(drums, snrDb = 15f))
        }
    }

    @Test
    fun `a plucked bass line without drums`() {
        for (bpm in listOf(60.0, 80.0, 100.0, 125.0)) {
            assertTempo("plucked bass", bpm, TestSignals.withNoise(BeatSignals.plucks(bpm, LISTEN_SECONDS), snrDb = 15f))
        }
    }

    @Test
    fun `swing and a loose drummer`() {
        for (bpm in listOf(90.0, 130.0, 165.0)) {
            val drums = BeatSignals.drums(bpm, LISTEN_SECONDS, swing = 2.0 / 3, jitterSeconds = 0.008)
            assertTempo("swung, loose rock", bpm, TestSignals.withNoise(drums, snrDb = 15f))
        }
        for (bpm in listOf(45.0, 100.0, 190.0)) {
            val clicks = BeatSignals.clicks(bpm, LISTEN_SECONDS, jitterSeconds = 0.012, seed = bpm.toLong())
            assertTempo("clicks 12 ms loose", bpm, TestSignals.withNoise(clicks, snrDb = 10f))
        }
    }

    @Test
    fun `silence, noise and a held tone have no steady beat`() {
        val n = (LISTEN_SECONDS * rate).toInt()
        val cases = mapOf(
            "silence" to FloatArray(n),
            "quiet noise" to TestSignals.noise(n, rms = 0.001f),
            "loud noise" to TestSignals.noise(n, rms = 0.2f, seed = 5),
            "a held chord" to TestSignals.withNoise(TestSignals.harmonics(110f, floatArrayOf(1f, 0.5f, 0.3f), n), snrDb = 20f),
        )
        for ((name, signal) in cases) {
            val detector = OnsetTempoDetector(rate)
            var everShown = false
            BeatSignals.feed(detector, signal) { if (detector.bpm != 0) everShown = true }
            assertEquals(name, TempoStatus.NO_BEAT, detector.status)
            assertEquals(name, 0, detector.bpm)
            assertTrue("$name showed a tempo", !everShown)
        }
    }

    @Test
    fun `nothing is reported in the first three seconds`() {
        val detector = OnsetTempoDetector(rate)
        val clicks = BeatSignals.clicks(120.0, 2.9)
        BeatSignals.feed(detector, clicks)
        assertEquals(TempoStatus.LISTENING, detector.status)
        assertEquals(0, detector.bpm)
    }

    @Test
    fun `a tempo change is followed within eight seconds`() {
        val first = BeatSignals.drums(100.0, 12.0)
        val second = BeatSignals.drums(130.0, 8.0, startSeconds = 0.0)
        val detector = assertTempo("100 then 130", 130.0, TestSignals.withNoise(BeatSignals.join(first, second), snrDb = 15f))
        assertEquals(130, detector.bpm)
    }

    @Test
    fun `a steady beat is shown without jumping and becomes steady`() {
        for (bpm in listOf(30.0, 76.0, 97.5, 133.0, 250.0)) {
            // Drums where a groove makes sense, clicks at the ends of the range.
            val music = if (bpm in 60.0..200.0) BeatSignals.drums(bpm, LISTEN_SECONDS, chords = false) else BeatSignals.clicks(bpm, LISTEN_SECONDS)
            val detector = OnsetTempoDetector(rate)
            var firstShownAt = -1L
            var steadyAt = -1L
            var shown = 0
            var changes = 0
            BeatSignals.feed(detector, TestSignals.withNoise(music, snrDb = 15f)) { samples ->
                if (detector.bpm != shown) {
                    if (shown != 0) changes++
                    shown = detector.bpm
                    if (firstShownAt < 0) firstShownAt = samples
                }
                if (steadyAt < 0 && detector.status == TempoStatus.STEADY) steadyAt = samples
            }
            val name = "$bpm BPM: first shown at ${firstShownAt / rate.toDouble()} s, steady at ${steadyAt / rate.toDouble()} s, $changes changes"
            assertTrue(name, firstShownAt in 0..(5L * rate))
            assertTrue(name, steadyAt in 0..(10L * rate))
            assertTrue(name, changes <= 1)
            assertTrue(name, abs(detector.bpm - bpm) <= 1.0)
        }
    }

    @Test
    fun `half and double tempo are offered when in range`() {
        val d100 = assertTempo("clicks", 100.0, TestSignals.withNoise(BeatSignals.clicks(100.0, LISTEN_SECONDS), snrDb = 20f))
        assertEquals(50, d100.halfBpm)
        assertEquals(200, d100.doubleBpm)
        assertTrue(d100.alternativeBpm == 50 || d100.alternativeBpm == 200)

        val d40 = assertTempo("clicks", 40.0, TestSignals.withNoise(BeatSignals.clicks(40.0, LISTEN_SECONDS), snrDb = 20f))
        assertEquals(0, d40.halfBpm)
        assertEquals(80, d40.doubleBpm)
        assertEquals(80, d40.alternativeBpm)

        val d240 = assertTempo("clicks", 240.0, TestSignals.withNoise(BeatSignals.clicks(240.0, LISTEN_SECONDS), snrDb = 20f))
        assertEquals(120, d240.halfBpm)
        assertEquals(0, d240.doubleBpm)
        assertEquals(120, d240.alternativeBpm)
    }

    @Test
    fun `the beat grid falls on the heard beats`() {
        val cases = listOf(
            Triple("clicks", 45.0, rate), Triple("clicks", 92.0, rate), Triple("clicks", 120.0, rate),
            Triple("clicks", 174.0, rate), Triple("clicks", 250.0, rate), Triple("clicks", 133.0, 16_000),
            Triple("drums", 92.0, rate), Triple("drums", 174.0, 44_100), Triple("plucks", 100.0, rate),
        )
        for ((kind, bpm, sampleRate) in cases) {
            val seconds = 10.0
            val music = when (kind) {
                "clicks" -> BeatSignals.clicks(bpm, seconds, sampleRate)
                "drums" -> BeatSignals.drums(bpm, seconds, sampleRate = sampleRate, startSeconds = 0.25)
                else -> BeatSignals.plucks(bpm, seconds, sampleRate, startSeconds = 0.25)
            }
            val times = BeatSignals.clickTimes(bpm, seconds, sampleRate)
            val detector = OnsetTempoDetector(sampleRate)
            var worstMs = 0.0
            BeatSignals.feed(detector, TestSignals.withNoise(music, snrDb = 15f)) {
                if (detector.lastBeatSample >= 0) {
                    val nearest = times.minOf { t -> abs(t - detector.lastBeatSample) }
                    worstMs = maxOf(worstMs, nearest * 1000.0 / sampleRate)
                }
            }
            val name = "$kind at $bpm BPM, $sampleRate Hz"
            assertTrue("$name: beat grid up to $worstMs ms off", worstMs <= BEAT_TOLERANCE_MS)
            val interval = sampleRate * 60.0 / bpm
            assertEquals("$name: interval", interval, detector.beatIntervalSamples.toDouble(), interval * 0.01)
        }
    }

    @Test
    fun `other sample rates`() {
        for (sampleRate in listOf(8_000, 16_000, 44_100, 96_000)) {
            val clicks = TestSignals.withNoise(BeatSignals.clicks(97.0, LISTEN_SECONDS, sampleRate), snrDb = 15f)
            assertTempo("clicks at $sampleRate Hz", 97.0, clicks, sampleRate)
            val drums = TestSignals.withNoise(BeatSignals.drums(143.0, LISTEN_SECONDS, sampleRate = sampleRate), snrDb = 15f)
            assertTempo("drums at $sampleRate Hz", 143.0, drums, sampleRate)
        }
    }

    @Test
    fun `chunk sizes do not matter`() {
        val signal = TestSignals.withNoise(BeatSignals.drums(118.0, LISTEN_SECONDS), snrDb = 15f)
        val results = listOf(1, 241, 1024, 4800, 48_000).map { chunk ->
            val detector = OnsetTempoDetector(rate)
            BeatSignals.feed(detector, signal, chunk)
            detector.exactBpm
        }
        for (r in results) assertEquals(results[0], r, 1e-4f)
        assertEquals(118f, results[0], 1f)
    }

    @Test
    fun `reset starts afresh`() {
        val detector = OnsetTempoDetector(rate)
        BeatSignals.feed(detector, BeatSignals.clicks(150.0, 10.0))
        assertEquals(150, detector.bpm)
        detector.reset()
        assertEquals(TempoStatus.LISTENING, detector.status)
        assertEquals(0, detector.bpm)
        assertEquals(0L, detector.samplesProcessed)
        assertEquals(-1L, detector.lastBeatSample)
        BeatSignals.feed(detector, BeatSignals.clicks(80.0, LISTEN_SECONDS))
        assertEquals(80, detector.bpm)
    }

    @Test
    fun `process allocates nothing`() {
        val detector = OnsetTempoDetector(rate)
        val signal = TestSignals.withNoise(BeatSignals.drums(120.0, 20.0), snrDb = 15f)
        // Warm up over 10 s (estimates run from 3 s), then measure 10 s more, every estimate included.
        val chunk = 1024
        val half = signal.size / 2 / chunk * chunk
        BeatSignals.feed(detector, signal.copyOf(half), chunk)
        val bytes = allocatedBytes(attempts = 1) {
            var pos = half
            while (pos + chunk <= signal.size) {
                detector.process(signal, pos, chunk)
                pos += chunk
            }
        }
        assertEquals(0L, bytes)
        assertEquals(120, detector.bpm)
    }

    @Test
    fun `doubling gets stricter with tempo`() {
        var previous = 0.0
        for (bpm in 60..250 step 10) {
            val t = OnsetTempoDetector.doublingThreshold(bpm.toDouble())
            assertTrue(t >= previous)
            previous = t
        }
        assertEquals(0.15, OnsetTempoDetector.doublingThreshold(60.0), 1e-9)
        assertEquals(0.6, OnsetTempoDetector.doublingThreshold(250.0), 1e-9)
    }

    private companion object {
        // Within the screen's 15 s of listening.
        const val LISTEN_SECONDS = 14.0
        const val BEAT_TOLERANCE_MS = 8.0
    }
}
