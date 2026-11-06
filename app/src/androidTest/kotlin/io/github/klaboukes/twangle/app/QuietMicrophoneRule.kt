package io.github.klaboukes.twangle.app

import androidx.test.core.app.ApplicationProvider
import io.github.klaboukes.twangle.app.store.FixedSoundRecorder
import org.junit.rules.ExternalResource

/**
 * Gives the tuner a stand-in microphone that hears only a faint hiss ([FixedSoundRecorder], nothing played), so a
 * test of the app's screens doesn't hear the room. With the real microphone, sound near the phone keeps the lens
 * drawing, the app's main thread never goes idle, and everything that waits for it (`ActivityScenario.onActivity`
 * and `close`, UI Automator's `pressBack`) waits until the test times out: `TunerTabTest` failed this way 2 runs in
 * 3 on 2026-11-03, with or without a change. Not for tests of the microphone itself (`MicLifecycleTest`, `TuningWidgetTest`). The tuner
 * takes its recorder when its screen is made, so put it around any rule that launches `MainActivity`.
 */
class QuietMicrophoneRule : ExternalResource() {

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container

    override fun before() {
        container.testAudioRecorder = FixedSoundRecorder()
    }

    override fun after() {
        container.testAudioRecorder = null
    }
}
