package io.github.klaboukes.twangle.core.dsp.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {

    @Test
    fun `the first tap gives nothing, the second the tempo between them`() {
        val tap = TapTempo()
        assertNull(tap.tap(1_000))
        assertEquals(120, tap.tap(1_500))
    }

    @Test
    fun `steady taps give their tempo`() {
        for (bpm in listOf(30, 60, 97, 140, 233, 250)) {
            val tap = TapTempo()
            val interval = 60_000.0 / bpm
            var result: Int? = null
            for (i in 0 until 10) result = tap.tap(10_000 + (i * interval).toLong())
            assertEquals("tapping at $bpm BPM", bpm, result)
        }
    }

    @Test
    fun `the tempo is the average of the last five intervals`() {
        val tap = TapTempo()
        // Slow taps first, then six at 100 BPM (600 ms): only those count once six are in.
        var t = 0L
        for (i in 0 until 3) {
            tap.tap(t)
            t += 1_500
        }
        var result: Int? = null
        for (i in 0 until 6) {
            result = tap.tap(t)
            t += 600
        }
        assertEquals(100, result)
    }

    @Test
    fun `one late tap only shifts the tempo by its share`() {
        val tap = TapTempo()
        var result: Int? = null
        // Four intervals of 500 ms and one of 600: 2600 ms over 5 = 520 ms, 115 BPM (the late one alone is 100).
        for (t in listOf(0L, 500L, 1_000L, 1_500L, 2_000L, 2_600L)) result = tap.tap(t)
        assertEquals(115, result)
    }

    @Test
    fun `a pause of more than two seconds starts afresh`() {
        val tap = TapTempo()
        tap.tap(0)
        assertEquals(60, tap.tap(1_000))
        assertNull("after a long pause the next tap is a first one", tap.tap(3_001))
        assertEquals(120, tap.tap(3_501))
    }

    @Test
    fun `a pause of exactly two seconds still counts, as 30 BPM`() {
        val tap = TapTempo()
        tap.tap(0)
        assertEquals(30, tap.tap(2_000))
    }

    @Test
    fun `very fast taps are kept at 250 BPM`() {
        val tap = TapTempo()
        tap.tap(0)
        assertEquals(250, tap.tap(100))
    }

    @Test
    fun `a tap at the same moment starts afresh instead of dividing by zero`() {
        val tap = TapTempo()
        tap.tap(500)
        assertNull(tap.tap(500))
        assertEquals(120, tap.tap(1_000))
    }

    @Test
    fun `reset forgets the taps`() {
        val tap = TapTempo()
        tap.tap(0)
        tap.reset()
        assertNull(tap.tap(500))
    }

    @Test
    fun `each tempo has its marking, from Grave to Prestissimo`() {
        val expected = mapOf(
            30 to TempoMarking.GRAVE, 39 to TempoMarking.GRAVE, 40 to TempoMarking.LARGO, 59 to TempoMarking.LARGO,
            60 to TempoMarking.LARGHETTO, 66 to TempoMarking.ADAGIO, 76 to TempoMarking.ANDANTE,
            96 to TempoMarking.ANDANTE, 108 to TempoMarking.MODERATO, 119 to TempoMarking.MODERATO,
            120 to TempoMarking.ALLEGRO, 167 to TempoMarking.ALLEGRO, 168 to TempoMarking.PRESTO,
            199 to TempoMarking.PRESTO, 200 to TempoMarking.PRESTISSIMO, 250 to TempoMarking.PRESTISSIMO,
        )
        for ((bpm, marking) in expected) assertEquals("$bpm BPM", marking, TempoMarking.of(bpm))
    }
}
