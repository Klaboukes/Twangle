package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.audio.MicPermissionStatus.DENIED
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus.GRANTED
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus.NOT_REQUESTED
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus.PERMANENTLY_DENIED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicPermissionRulesTest {

    @Test
    fun `on open, a granted permission needs nothing`() {
        assertEquals(GRANTED, MicPermissionRules.initial(granted = true, shouldShowRationale = false))
    }

    @Test
    fun `on open, an earlier refusal shows the explanation`() {
        assertEquals(DENIED, MicPermissionRules.initial(granted = false, shouldShowRationale = true))
    }

    @Test
    fun `on open, no rationale means we simply ask`() {
        assertEquals(NOT_REQUESTED, MicPermissionRules.initial(granted = false, shouldShowRationale = false))
    }

    @Test
    fun `allowing in the dialog grants`() {
        assertEquals(GRANTED, after(granted = true))
    }

    @Test
    fun `first refusal leads to the explanation`() {
        assertEquals(DENIED, after(rationaleAfter = true))
    }

    @Test
    fun `dismissing the first dialog without an answer keeps Allow microphone`() {
        assertEquals(NOT_REQUESTED, after())
    }

    @Test
    fun `dismissing the dialog again after one refusal keeps the explanation`() {
        assertEquals(DENIED, after(rationaleBefore = true, rationaleAfter = true, refusedBefore = true))
    }

    @Test
    fun `a refusal that takes the rationale away is permanent and needs Settings`() {
        // Don't ask again (Android 10 and older), or the second refusal (Android 11 and newer).
        assertEquals(PERMANENTLY_DENIED, after(rationaleBefore = true, refusedBefore = true))
        assertEquals(PERMANENTLY_DENIED, after(rationaleBefore = true))
    }

    @Test
    fun `no dialog after an earlier refusal means Android stopped asking`() {
        // Permanently denied in an earlier session: the screen opens without a rationale, asks, and
        // Android answers at once.
        assertEquals(PERMANENTLY_DENIED, after(refusedBefore = true))
    }

    @Test
    fun `refusals are remembered, a grant or a dismissal is not`() {
        assertTrue(MicPermissionRules.isRefusal(DENIED))
        assertTrue(MicPermissionRules.isRefusal(PERMANENTLY_DENIED))
        assertFalse(MicPermissionRules.isRefusal(GRANTED))
        assertFalse(MicPermissionRules.isRefusal(NOT_REQUESTED))
    }

    @Test
    fun `granting in Settings is picked up on resume`() {
        for (status in MicPermissionStatus.entries) {
            for (rationale in listOf(false, true)) {
                assertEquals(GRANTED, MicPermissionRules.onResume(status, granted = true, shouldShowRationale = rationale))
            }
        }
    }

    @Test
    fun `resuming without a grant keeps the prompt as it was`() {
        for (status in listOf(NOT_REQUESTED, DENIED, PERMANENTLY_DENIED)) {
            for (rationale in listOf(false, true)) {
                assertEquals(status, MicPermissionRules.onResume(status, granted = false, shouldShowRationale = rationale))
            }
        }
    }

    @Test
    fun `a permission lost while the app was open goes back to the prompt`() {
        assertEquals(NOT_REQUESTED, MicPermissionRules.onResume(GRANTED, granted = false, shouldShowRationale = false))
        assertEquals(DENIED, MicPermissionRules.onResume(GRANTED, granted = false, shouldShowRationale = true))
    }

    private fun after(
        granted: Boolean = false,
        rationaleBefore: Boolean = false,
        rationaleAfter: Boolean = false,
        refusedBefore: Boolean = false,
    ) = MicPermissionRules.afterRequest(granted, rationaleBefore, rationaleAfter, refusedBefore)
}
