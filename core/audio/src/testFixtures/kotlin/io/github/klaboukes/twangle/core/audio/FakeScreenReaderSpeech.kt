package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [ScreenReaderSpeech] for tests: the test says when the voice plays ([isSpeaking]), and sees whether anything
 * watches it ([watchers]: the real one registers with the system only while watched).
 */
class FakeScreenReaderSpeech(speaking: Boolean = false) : ScreenReaderSpeech {

    private val state = MutableStateFlow(speaking)
    override val speaking: Flow<Boolean> = state

    /** Whether the screen reader's voice plays now; set it to start or stop the voice. */
    var isSpeaking: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }

    /** How many collectors watch [speaking] now. */
    val watchers: Int get() = state.subscriptionCount.value
}
