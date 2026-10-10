package com.intune.feature.tuner

import com.intune.feature.tuner.MicPermissionStatus.DENIED
import com.intune.feature.tuner.MicPermissionStatus.GRANTED
import com.intune.feature.tuner.MicPermissionStatus.NOT_REQUESTED
import com.intune.feature.tuner.MicPermissionStatus.PERMANENTLY_DENIED
import org.junit.Assert.assertEquals
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
        assertEquals(GRANTED, MicPermissionRules.afterRequest(granted = true, shouldShowRationale = false))
    }

    @Test
    fun `first refusal leads to the explanation`() {
        assertEquals(DENIED, MicPermissionRules.afterRequest(granted = false, shouldShowRationale = true))
    }

    @Test
    fun `refusal without rationale is permanent and needs Settings`() {
        assertEquals(PERMANENTLY_DENIED, MicPermissionRules.afterRequest(granted = false, shouldShowRationale = false))
    }

    @Test
    fun `granting in Settings is picked up on resume`() {
        for (status in MicPermissionStatus.entries) {
            assertEquals(GRANTED, MicPermissionRules.onResume(status, granted = true))
        }
    }

    @Test
    fun `resuming without a grant keeps the current status`() {
        for (status in listOf(NOT_REQUESTED, DENIED, PERMANENTLY_DENIED)) {
            assertEquals(status, MicPermissionRules.onResume(status, granted = false))
        }
    }
}
