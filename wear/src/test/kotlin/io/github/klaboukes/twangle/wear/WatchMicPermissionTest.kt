package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch's microphone prompt follows the phone's rules: its button sends the player to the
 * watch's settings only after a real "don't ask again", never after a dismissed first dialog.
 */
class WatchMicPermissionTest {

    @Test
    fun `a first dialog dismissed without an answer keeps Allow microphone`() {
        // Android answers "denied" with no rationale before or after, as for a permanent refusal.
        val status = MicPermissionRules.afterRequest(granted = false, rationaleBefore = false, rationaleAfter = false, refusedBefore = false)
        assertEquals(MicPermissionStatus.NOT_REQUESTED, status)
        assertFalse(needsSettings(status))
        assertFalse("a dismissal is no refusal to remember", MicPermissionRules.isRefusal(status))
    }

    @Test
    fun `one refusal asks again, a second one leads to settings`() {
        val first = MicPermissionRules.afterRequest(granted = false, rationaleBefore = false, rationaleAfter = true, refusedBefore = false)
        assertFalse(needsSettings(first))
        assertTrue(MicPermissionRules.isRefusal(first))
        val second = MicPermissionRules.afterRequest(granted = false, rationaleBefore = true, rationaleAfter = false, refusedBefore = true)
        assertTrue(needsSettings(second))
    }

    @Test
    fun `after a restart, a refusal remembered on the watch leads to settings`() {
        // Permanently refused in an earlier session: no rationale on open, so the watch asks once,
        // and Android answers at once without a dialog.
        val opened = MicPermissionRules.initial(granted = false, shouldShowRationale = false)
        assertFalse(needsSettings(opened))
        val answered = MicPermissionRules.afterRequest(granted = false, rationaleBefore = false, rationaleAfter = false, refusedBefore = true)
        assertTrue(needsSettings(answered))
    }

    @Test
    fun `a grant needs no button, and losing it brings the prompt back`() {
        assertFalse(needsSettings(MicPermissionStatus.GRANTED))
        val lost = MicPermissionRules.onResume(MicPermissionStatus.GRANTED, granted = false, shouldShowRationale = false)
        assertEquals(MicPermissionStatus.NOT_REQUESTED, lost)
        assertFalse(needsSettings(lost))
    }
}
