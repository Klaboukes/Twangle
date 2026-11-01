package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
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

    /** The status shown; set it to change the prompt while it shows (a test may set its content only once). */
    private val shownStatus = mutableStateOf(MicPermissionStatus.NOT_REQUESTED)

    private fun show(status: MicPermissionStatus, practiceTab: Boolean = true) {
        shownStatus.value = status
        compose.setContent {
            MaterialTheme {
                PermissionPrompt(shownStatus.value, onRequest = { requests++ }, onOpenSettings = { settingsOpened++ }, practiceTab = practiceTab)
            }
        }
    }

    private val everyStatus = listOf(MicPermissionStatus.NOT_REQUESTED, MicPermissionStatus.DENIED, MicPermissionStatus.PERMANENTLY_DENIED)

    /** Shows each status in turn and runs [check] on it. */
    private fun forEveryStatus(check: () -> Unit) {
        show(everyStatus.first())
        for (status in everyStatus) {
            shownStatus.value = status
            compose.waitForIdle()
            check()
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

    /** TalkBack can jump to the title by headings, as on every other screen. */
    @Test
    fun theTitleIsAHeading() {
        forEveryStatus { compose.onNodeWithText(text(R.string.mic_permission_title)).assert(isHeading()) }
    }

    /** A player who says no learns the app still has a use, in every state of the prompt. */
    @Test
    fun itSaysWhatWorksWithoutTheMicrophone() {
        forEveryStatus { compose.onNodeWithText(text(R.string.mic_permission_without_mic)).assertIsDisplayed() }
    }

    /** With the Practice tab's switch off, only the metronome is named: the tab leaves no trace. */
    @Test
    fun withoutThePracticeTabOnlyTheMetronomeIsNamed() {
        show(MicPermissionStatus.NOT_REQUESTED, practiceTab = false)
        compose.onNodeWithText(text(R.string.mic_permission_without_mic_metronome)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mic_permission_without_mic)).assertDoesNotExist()
    }
}
