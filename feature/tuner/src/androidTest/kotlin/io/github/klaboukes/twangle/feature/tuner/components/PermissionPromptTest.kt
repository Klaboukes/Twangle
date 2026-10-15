package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.tuner.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionPromptTest {

    @get:Rule
    val compose = createSharedActivityComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private var requests = 0
    private var settingsOpened = 0

    private fun show(status: MicPermissionStatus) {
        compose.setContent {
            MaterialTheme {
                PermissionPrompt(status, onRequest = { requests++ }, onOpenSettings = { settingsOpened++ })
            }
        }
    }

    @Test
    fun notRequestedExplainsAndAsks() {
        show(MicPermissionStatus.NOT_REQUESTED)
        compose.onNodeWithText(text(R.string.mic_permission_intro)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_permission_allow)).performClick()

        assertEquals(1, requests)
        assertEquals(0, settingsOpened)
    }

    @Test
    fun deniedShowsTheRationaleAndAsksAgain() {
        show(MicPermissionStatus.DENIED)
        compose.onNodeWithText(text(R.string.mic_permission_rationale)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_permission_allow)).performClick()

        assertEquals(1, requests)
    }

    @Test
    fun permanentlyDeniedSendsToSettings() {
        show(MicPermissionStatus.PERMANENTLY_DENIED)
        compose.onNodeWithText(text(R.string.mic_permission_settings_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_permission_open_settings)).performClick()

        assertEquals(0, requests)
        assertEquals(1, settingsOpened)
    }
}
