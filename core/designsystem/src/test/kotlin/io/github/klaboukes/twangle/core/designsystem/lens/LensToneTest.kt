package io.github.klaboukes.twangle.core.designsystem.lens

import io.github.klaboukes.twangle.core.settings.InTunePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LensToneTest {

    private val relaxedWindow = 5f
    private val relaxedExit = meterInTuneExit(InTunePrecision.RELAXED)

    @Test
    fun `far off the tone is amber`() {
        assertEquals(0f, readingToneAmount(30f, 5f), 0f)
        assertEquals(0f, readingToneAmount(-19f, 5f), 0f)
        // 10 cents out is still amber (the owner's review, 2026-11-03: it read lime, nearly in tune).
        assertEquals(0f, readingToneAmount(10f, 5f), 0f)
        assertEquals(0f, readingToneAmount(-12f, 5f), 0f)
        assertEquals(0f, readingToneAmount(Float.NaN, 5f), 0f)
    }

    @Test
    fun `it eases toward green as the note nears, never quite green before in tune`() {
        assertEquals(0.35f, readingToneAmount(5f, 5f), 1e-6f)
        assertEquals(0.35f, readingToneAmount(0f, 5f), 1e-6f)
        assertEquals(0.175f, readingToneAmount(-7.5f, 5f), 1e-6f)
        var last = 0f
        for (c in 20 downTo 0) {
            val amount = readingToneAmount(c.toFloat(), 5f)
            assertTrue("$c", amount >= last)
            last = amount
        }
    }

    @Test
    fun `the Precise window starts the easing nearer`() {
        assertTrue(readingToneAmount(8f, 2f) < readingToneAmount(8f, 5f))
    }

    @Test
    fun `the tuner's release is its own`() {
        assertEquals(7f, meterInTuneExit(InTunePrecision.RELAXED))
        assertEquals(3f, meterInTuneExit(InTunePrecision.PRECISE))
    }

    @Test
    fun `in tune the tone is fully green, up to where the tuner lets go`() {
        for (cents in listOf(0f, 3f, -5f, 7f, -7f)) {
            assertEquals("$cents", 1f, readingToneShare(cents, relaxedWindow, relaxedExit, tuned = 1f), 1e-6f)
        }
    }

    /**
     * The phone check of #587: the synthetic tone swept out of the zone at about 30 cents a second, and the word read
     * "Too low, 13 cents flat" in green. A quarter of a second after the tuner let go at 7 cents, its 0.6 s fade still
     * had about 0.6 of "in tune" left; the tone must still be the one 13 cents calls for, mostly amber.
     */
    @Test
    fun `13 cents off, the in-tune fade no longer greens the tone`() {
        val share = readingToneShare(-13f, relaxedWindow, relaxedExit, tuned = 0.62f)
        assertEquals(readingToneAmount(-13f, relaxedWindow), share, 1e-6f)
        assertTrue("mostly amber: $share", share < 0.5f)
    }

    @Test
    fun `the in-tune green lets go smoothly as the reading leaves`() {
        var last = Float.NaN
        var cents = 0f
        while (cents <= 20f) {
            val share = readingToneShare(cents, relaxedWindow, relaxedExit, tuned = 1f)
            if (!last.isNaN()) {
                assertTrue("never greener further out, at $cents", share <= last + 1e-6f)
                assertTrue("no jump at $cents: $last to $share", abs(share - last) < 0.05f)
            }
            last = share
            cents += 0.1f
        }
        assertEquals("gone past the release", readingToneAmount(12f, relaxedWindow), readingToneShare(12f, relaxedWindow, relaxedExit, 1f), 1e-6f)
    }

    @Test
    fun `out of tune and settled, the share is the cents' own`() {
        for (cents in listOf(-30f, -13f, -6f, 4f, 9f)) {
            assertEquals("$cents", readingToneAmount(cents, relaxedWindow), readingToneShare(cents, relaxedWindow, relaxedExit, tuned = 0f), 1e-6f)
        }
    }

    @Test
    fun `a note dying away keeps its in-tune green as it fades`() {
        assertEquals(0.4f, readingToneShare(Float.NaN, relaxedWindow, relaxedExit, tuned = 0.4f), 1e-6f)
    }

    @Test
    fun `Precise lets go nearer`() {
        val exit = meterInTuneExit(InTunePrecision.PRECISE)
        assertEquals(1f, readingToneShare(3f, 2f, exit, tuned = 1f), 1e-6f)
        assertEquals(readingToneAmount(7f, 2f), readingToneShare(7f, 2f, exit, tuned = 1f), 1e-6f)
    }
}
