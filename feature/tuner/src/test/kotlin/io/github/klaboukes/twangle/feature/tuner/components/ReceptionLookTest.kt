package io.github.klaboukes.twangle.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceptionLookTest {

    @Test
    fun `good reception and the resting look are untouched`() {
        assertEquals(1f, ReceptionLook.alphaFor(1f, presence = 1f))
        assertEquals(0, ReceptionLook.blurStepFor(1f, presence = 1f))
        // At rest (no note) the reception changes nothing, whatever it was.
        assertEquals(1f, ReceptionLook.alphaFor(0f, presence = 0f))
        assertEquals(0, ReceptionLook.blurStepFor(0f, presence = 0f))
    }

    @Test
    fun `poor reception is faint and soft, never invisible`() {
        assertEquals(ReceptionLook.FAINTEST_ALPHA, ReceptionLook.alphaFor(0f, presence = 1f), 1e-6f)
        assertEquals(ReceptionLook.BLUR_STEPS, ReceptionLook.blurStepFor(0f, presence = 1f))
        assertTrue("the faintest needle stays brighter than the resting one", ReceptionLook.FAINTEST_ALPHA > 0.3f)
        assertTrue("the wave (0.8 opacity off pitch) stays at least half visible", 0.8f * ReceptionLook.FAINTEST_ALPHA >= 0.49f)
        // Out-of-range input is clamped.
        assertEquals(ReceptionLook.FAINTEST_ALPHA, ReceptionLook.alphaFor(-1f, presence = 2f), 1e-6f)
    }

    @Test
    fun `fades steadily with reception and with the picture's presence`() {
        var lastAlpha = 2f
        var lastBlur = -1
        for (i in 0..20) {
            val reception = 1f - i / 20f
            val alpha = ReceptionLook.alphaFor(reception, presence = 1f)
            val blur = ReceptionLook.blurStepFor(reception, presence = 1f)
            assertTrue(alpha < lastAlpha)
            assertTrue(blur >= lastBlur)
            lastAlpha = alpha
            lastBlur = blur
        }
        assertEquals(1f - (1f - ReceptionLook.FAINTEST_ALPHA) * 0.25f, ReceptionLook.alphaFor(0.5f, presence = 0.5f), 1e-6f)
    }

    @Test
    fun `the last reception is held while the note fades out`() {
        val held = HeldReception()
        assertEquals(1f, held.of(null))
        assertEquals(0.4f, held.of(0.4f))
        assertEquals(0.4f, held.of(null))
        assertEquals(0.9f, held.of(0.9f))
    }
}
