package io.github.klaboukes.twangle.core.designsystem.lens

import io.github.klaboukes.twangle.core.designsystem.dial.STROBE_ROWS
import io.github.klaboukes.twangle.core.designsystem.dial.stripeOffset
import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strobe in the lens (Phase 72; the mock-up's `lensStrobe`): three bands, the top band's stripes a fifth of
 * the lens's width apart (24 to 64 dp), halved in each band below, each band drifting twice as fast across as
 * the one above; flat drifts left, sharp right, still within half a cent. At 2.75 px a dp.
 */
class StrobeLookTest {

    private val dp = 2.75f

    private fun geometry(width: Float, height: Float) = StrobeGeometry(width * dp, height * dp, dp)

    @Test
    fun `three bands, the top band's stripes a fifth of the lens's width apart, halved in each band below`() {
        assertEquals(3, STROBE_ROWS)
        // The Xiaomi's lens upright (353 dp): a fifth would be 70.6, so 64; a 2:1 lens on its side 380 dp: 64 too.
        assertEquals(64f * dp, geometry(353f, 300f).topPeriod, 1e-3f)
        // A narrow lens, 250 dp: 50 dp.
        val narrow = geometry(250f, 125f)
        assertEquals(50f * dp, narrow.topPeriod, 1e-3f)
        assertEquals(25f * dp, narrow.period(1), 1e-3f)
        assertEquals(12.5f * dp, narrow.period(2), 1e-3f)
        // Never finer than 24 dp in the top band (a Settings card's small lens).
        assertEquals(24f * dp, geometry(100f, 60f).topPeriod, 1e-3f)
    }

    @Test
    fun `the bands fill the lens above the room for the flat and sharp signs`() {
        for ((w, h) in listOf(353f to 300f, 353f to 176f, 380f to 190f, 826f to 243f)) {
            val g = geometry(w, h)
            assertEquals(14f * dp, g.left, 1e-3f)
            assertEquals((w - 28f) * dp, g.bandWidth, 1e-2f)
            assertEquals(14f * dp, g.bandTop(0), 1e-3f)
            for (band in 1 until STROBE_ROWS) assertEquals(g.bandTop(band - 1) + g.bandHeight + 10f * dp, g.bandTop(band), 1e-2f)
            // 20 dp left under the bands (above the 14 dp padding) for ♭ and ♯.
            val bottom = g.bandTop(STROBE_ROWS - 1) + g.bandHeight
            assertEquals((h - 14f - 20f) * dp, bottom, 1e-2f)
            assertTrue("$w x $h: bands ${g.bandHeight / dp} dp", g.bandHeight > 20f * dp)
            // The centre line in the middle, 4 dp past the bands, inside the box.
            assertEquals(w * dp / 2f, g.centreX, 1e-3f)
            assertEquals(10f * dp, g.lineTop, 1e-3f)
            assertTrue(g.lineBottom < h * dp)
        }
    }

    @Test
    fun `flat drifts left, sharp right, and within half a cent the stripes stand still`() {
        for (cents in listOf(-30f, -3f, -1f)) {
            val drift = settled(cents)
            val before = drift.phase
            assertTrue(drift.advance(FRAME, cents))
            assertTrue("$cents cents flat: the stripes move left", stepOf(before, drift.phase) < 0f)
        }
        for (cents in listOf(1f, 3f, 30f)) {
            val drift = settled(cents)
            val before = drift.phase
            assertTrue(drift.advance(FRAME, cents))
            assertTrue("$cents cents sharp: the stripes move right", stepOf(before, drift.phase) > 0f)
        }
        for (cents in listOf(0f, 0.3f, -0.4f)) {
            val drift = settled(cents)
            val before = drift.phase
            assertFalse("$cents cents: still", drift.advance(FRAME, cents))
            assertEquals(before, drift.phase, 0f)
        }
    }

    @Test
    fun `further off drifts faster, up to the strobe's top speed`() {
        val speeds = listOf(1f, 3f, 6f, 10.5f, 30f).map { settled(it).speed }
        for (i in 1 until speeds.size) assertTrue("speeds $speeds", speeds[i] >= speeds[i - 1])
        assertEquals(StrobeMotion.MAX_STRIPES_PER_SECOND * StrobeDrift.TOP_BAND_SHARE, speeds.last(), 1e-4f)
        // 3 cents sharp: the strobe's 1 stripe a second, the top band an eighth of its own.
        assertEquals(StrobeMotion.driftSpeed(3f) * StrobeDrift.TOP_BAND_SHARE, settled(3f).speed, 1e-3f)
    }

    @Test
    fun `each band drifts twice as fast across as the one above, and wraps without a jump`() {
        val g = geometry(353f, 300f)
        val drift = settled(5f)
        val before = FloatArray(STROBE_ROWS) { drift.bandPhase(it) }
        drift.advance(FRAME, 5f)
        val moved = FloatArray(STROBE_ROWS) { band -> (drift.bandPhase(band) - before[band]) * g.period(band) }
        for (band in 1 until STROBE_ROWS) assertEquals("band $band", 2f * moved[band - 1], moved[band], 1e-3f * dp)
        // Each band's phase is a whole multiple of the top band's (1, 4, 16), so when the top band's wraps from 1
        // back to 0 every band's stripes look exactly as they did: no jump.
        for (band in 0 until STROBE_ROWS) {
            val multiple = drift.bandPhase(band) / drift.phase
            assertEquals("band $band", Math.round(multiple).toFloat(), multiple, 1e-3f)
            assertEquals(stripeOffset(0f, g.period(band)), stripeOffset(Math.round(multiple).toFloat(), g.period(band)), 1e-3f)
        }
    }

    /** A drift that has followed [cents] for a second, so it moves at its full speed. */
    private fun settled(cents: Float) = StrobeDrift().apply { repeat(60) { advance(FRAME, cents) } }

    /** How far the phase went from [before] to [after], the short way round (it wraps at 1). */
    private fun stepOf(before: Float, after: Float): Float {
        var step = after - before
        if (step > 0.5f) step -= 1f
        if (step < -0.5f) step += 1f
        return step
    }

    private companion object {
        const val FRAME = 1f / 60f
    }
}
