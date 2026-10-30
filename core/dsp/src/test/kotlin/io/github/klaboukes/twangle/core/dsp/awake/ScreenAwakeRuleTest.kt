package io.github.klaboukes.twangle.core.dsp.awake

import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase.OFF
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase.ON
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase.WARNING
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule.Sound.PLAYED
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule.Sound.QUIET
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule.Sound.SOUNDING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenAwakeRuleTest {

    private val minutes3 = ScreenAwakeRule.DEFAULT_QUIET_MILLIS
    private val warning = ScreenAwakeRule.DEFAULT_WARNING_MILLIS
    private val note = ScreenAwakeRule.DEFAULT_NOTE_MILLIS
    private val rule = ScreenAwakeRule(startMillis = 0)

    @Test
    fun `warns after three quiet minutes, then lets go ten seconds later`() {
        assertEquals(ON, rule.phase(minutes3 - 1))
        assertEquals(WARNING, rule.phase(minutes3))
        assertEquals(WARNING, rule.phase(minutes3 + warning - 1))
        assertEquals(OFF, rule.phase(minutes3 + warning))
    }

    @Test
    fun `a touch starts the quiet time afresh, also during the warning and after letting go`() {
        rule.touched(minutes3 + 5_000)
        assertEquals(ON, rule.phase(minutes3 + 5_000))
        assertEquals(WARNING, rule.phase(2 * minutes3 + 5_000))
        rule.touched(10 * minutes3)
        assertEquals(ON, rule.phase(10 * minutes3 + 1))
    }

    @Test
    fun `a note holds the screen on however long it sounds, and the quiet time starts when it stops`() {
        rule.heard(SOUNDING, 1_000)
        assertEquals(ON, rule.phase(20 * minutes3))
        rule.heard(QUIET, 20 * minutes3)
        assertEquals(ON, rule.phase(21 * minutes3 - 1))
        assertEquals(WARNING, rule.phase(21 * minutes3))
    }

    @Test
    fun `a tool's new state counts as playing, quiet states don't`() {
        rule.heard(PLAYED, 60_000)
        rule.heard(QUIET, 120_000)
        rule.heard(QUIET, 150_000)
        assertEquals(ON, rule.phase(60_000 + minutes3 - 1))
        assertEquals(WARNING, rule.phase(60_000 + minutes3))
    }

    @Test
    fun `the warning's bar runs from full to empty`() {
        assertEquals(1f, rule.warningLeft(0), 0f)
        assertEquals(1f, rule.warningLeft(minutes3), 0f)
        assertEquals(0.5f, rule.warningLeft(minutes3 + warning / 2), 1e-6f)
        assertEquals(0f, rule.warningLeft(minutes3 + warning), 0f)
        assertEquals(0f, rule.warningLeft(10 * minutes3), 0f)
    }

    @Test
    fun `says when the phase changes next`() {
        assertEquals(minutes3, rule.millisUntilChange(0))
        assertEquals(warning, rule.millisUntilChange(minutes3))
        assertNull(rule.millisUntilChange(minutes3 + warning))
        // A note on the way to counting: the change comes when it has sounded a second.
        rule.heard(SOUNDING, minutes3 + warning)
        assertEquals(note - 1, rule.millisUntilChange(minutes3 + warning + 1))
        assertEquals(minutes3, rule.millisUntilChange(minutes3 + warning + note))
    }

    @Test
    fun `a quiet room's short sounds don't count as playing`() {
        // Like the Xiaomi heard in a quiet room: notes of about 0.2 s every few seconds.
        var t = 0L
        while (t < minutes3) {
            rule.heard(SOUNDING, t)
            rule.heard(QUIET, t + 200)
            t += 5_000
        }
        assertEquals(WARNING, rule.phase(minutes3))
        assertEquals(OFF, rule.phase(minutes3 + warning))
    }

    @Test
    fun `a note held a second ends the warning and the quiet time counts from its end`() {
        rule.heard(SOUNDING, minutes3 + 2_000)
        assertEquals(WARNING, rule.phase(minutes3 + 2_000 + note - 1))
        assertEquals(ON, rule.phase(minutes3 + 2_000 + note))
        rule.heard(QUIET, minutes3 + 5_000)
        assertEquals(ON, rule.phase(2 * minutes3 + 5_000 - 1))
        assertEquals(WARNING, rule.phase(2 * minutes3 + 5_000))
    }

    @Test
    fun `without a warning the screen goes straight from held to let go`() {
        // The watch's rule: no warning, so no in-between phase and no bar.
        val watch = ScreenAwakeRule(startMillis = 0, warningMillis = 0)
        assertEquals(ON, watch.phase(minutes3 - 1))
        assertEquals(OFF, watch.phase(minutes3))
        assertEquals(minutes3, watch.millisUntilChange(0))
        assertNull(watch.millisUntilChange(minutes3))
        assertEquals(0f, watch.warningLeft(minutes3), 0f)
    }
}
