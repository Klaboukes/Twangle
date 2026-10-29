package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.animation.core.FloatSpringSpec
import io.github.klaboukes.twangle.core.settings.MeterLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MeterFeelTest {

    /** Where a marker at [from] cents is [seconds] after a new reading of [to] cents. */
    private fun MeterFeel.at(seconds: Float, from: Float, to: Float): Float =
        FloatSpringSpec(dampingRatio, stiffness).getValueFromNanos((seconds * 1e9f).toLong(), from, to, 0f)

    /** The furthest the marker swings past [to] on its way from [from], in cents. */
    private fun MeterFeel.overshoot(from: Float, to: Float): Float {
        var most = 0f
        var t = 0f
        while (t < 4f) {
            val past = (at(t, from, to) - to) * (if (to > from) 1f else -1f)
            if (past > most) most = past
            t += 0.002f
        }
        return most
    }

    /** How long until the marker stays within [within] cents of [to]. */
    private fun MeterFeel.settleSeconds(from: Float, to: Float, within: Float): Float {
        var settled = 0f
        var t = 0f
        while (t < 6f) {
            if (abs(at(t, from, to) - to) > within) settled = t
            t += 0.002f
        }
        return settled
    }

    @Test
    fun `every look settles naturally, never jittery`() {
        for (look in MeterLook.entries) {
            val feel = meterFeel(look)
            assertTrue("$look damping ${feel.dampingRatio}", feel.dampingRatio >= 0.5f)
            assertTrue("$look settles in ${feel.settleSeconds(-38f, 0f, 0.5f)} s", feel.settleSeconds(-38f, 0f, 0.5f) < 2.5f)
        }
    }

    @Test
    fun `classic swings a little past a new string, like a mechanical needle, then settles`() {
        val classic = meterFeel(MeterLook.CLASSIC)
        // From 38 cents flat to the note: a few cents past it (about 9 % of the swing), not a wobble.
        val past = classic.overshoot(-38f, 0f)
        assertTrue("overshoot $past cents", past in 2f..5f)
        assertTrue("settles in ${classic.settleSeconds(-38f, 0f, 0.5f)} s", classic.settleSeconds(-38f, 0f, 0.5f) < 1.2f)
        assertEquals(0f, classic.at(3f, -38f, 0f), 0.05f)
    }

    @Test
    fun `the light arc follows almost without swinging`() {
        val arc = meterFeel(MeterLook.LIGHT_ARC)
        assertTrue(arc.overshoot(-38f, 0f) < 0.2f)
        assertTrue(arc.overshoot(10f, 40f) < 0.2f)
    }

    @Test
    fun `the note's pointer follows closely, hardly swinging past the note`() {
        val pointers = meterFeel(MeterLook.POINTERS)
        assertEquals(1.9f, pointers.frequencyHz)
        assertEquals(0.8f, pointers.dampingRatio)
        // From 38 cents flat: under a cent past the note, which the stretched middle shows 7.5u per cent.
        assertTrue(pointers.overshoot(-38f, 0f) < 1f)
    }

    @Test
    fun `the spring swings at the look's frequency`() {
        // ω² for a unit mass: 1.6 Hz → (2π · 1.6)² ≈ 101.
        assertEquals(101.06f, MeterFeel(1.6f, 0.6f).stiffness, 0.05f)
    }

    @Test
    fun `the in-tune fade eases in and out gently`() {
        assertEquals(0f, MeterSmoothstep.transform(0f), 1e-6f)
        assertEquals(0.5f, MeterSmoothstep.transform(0.5f), 1e-6f)
        assertEquals(1f, MeterSmoothstep.transform(1f), 1e-6f)
        assertTrue(MeterSmoothstep.transform(0.1f) < 0.1f)
        assertTrue(METER_IN_TUNE_IN_MS < METER_IN_TUNE_OUT_MS)
    }
}
