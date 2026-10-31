package io.github.klaboukes.twangle.feature.metronome

import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BetweenLightsTest {
    @Test
    fun eachClickBetweenTheBeatsLightsItsOwnMarkInTurn() {
        // 120 BPM in sixteenths: a beat every 500 ms, a click every 125 ms.
        assertEquals(0, betweenClickAt(0f, 120, 4))
        assertEquals(0, betweenClickAt(124f, 120, 4))
        assertEquals(1, betweenClickAt(125f, 120, 4))
        assertEquals(2, betweenClickAt(260f, 120, 4))
        assertEquals(3, betweenClickAt(499f, 120, 4))
        // Past the beat's time (the next beat late, or stopped): none.
        assertEquals(0, betweenClickAt(500f, 120, 4))
        // Triplets at 60 BPM: 333 ms apart.
        assertEquals(1, betweenClickAt(340f, 60, 3))
        assertEquals(2, betweenClickAt(670f, 60, 3))
    }

    @Test
    fun noClicksBetweenOrAnUnknownTempoLightNothing() {
        assertEquals(0, betweenClickAt(300f, 120, 1))
        assertEquals(0, betweenClickAt(300f, 0, 2))
        assertEquals(0f, betweenClickLevel(300f, 120, 1), 0f)
    }

    @Test
    fun clicksTooCloseTogetherLeaveTheMarksUnlit() {
        // Sixteenths at 200 BPM are 75 ms apart: still lit; above that, only the beats light.
        assertEquals(1, betweenClickAt(80f, 200, 4))
        assertEquals(0, betweenClickAt(80f, 201, 4))
        assertEquals(0, betweenClickAt(65f, 250, 4))
        // Triplets at 250 BPM (80 ms) and eighths at 250 BPM (120 ms) still light.
        assertEquals(1, betweenClickAt(85f, 250, 3))
        assertEquals(1, betweenClickAt(125f, 250, 2))
        assertTrue(betweenClickMillis(200, 4) >= MIN_BETWEEN_LIGHT_MILLIS)
        assertTrue(betweenClickMillis(201, 4) < MIN_BETWEEN_LIGHT_MILLIS)
    }

    @Test
    fun aLitMarkFlashesAndFadesBackBeforeTheNextClick() {
        assertEquals(1f, betweenClickLevel(250f, 120, 2), 0.001f)
        val later = betweenClickLevel(400f, 120, 2)
        assertTrue("fading: $later", later < 0.15f)
        assertEquals(0f, betweenClickLevel(100f, 120, 2), 0f)
    }

    @Test
    fun theClockLightsTheClickAfterTheBeatItHeard() {
        val clock = BeatClock()
        assertEquals(0, clock.litBetween())
        clock.beat = MetronomeBeat(0L, 2, BeatAccent.PLAIN, heardAtNanos = 1_000_000_000L, bpm = 120, clicksPerBeat = 2)
        clock.frameNanos = 1_000_000_000L + 260_000_000L
        assertEquals(2, clock.litBeat())
        assertEquals(1, clock.litBetween())
        assertTrue(clock.litBetweenLevel() > 0.8f)
        clock.beat = null
        assertEquals(0, clock.litBetween())
        assertEquals(0f, clock.litBetweenLevel(), 0f)
    }
}
