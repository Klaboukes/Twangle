package io.github.klaboukes.twangle.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** What the app's container hands out more than once. */
@RunWith(AndroidJUnit4::class)
class AppContainerTest {

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container

    @After
    fun noTestRecorder() {
        container.testEarTrainingRecorder = null
    }

    // Until Phase 89 a new one was made each time the screen was drawn.
    @Test
    fun earTrainingsListenerIsMadeOnce() {
        val listener = container.earTrainingListener
        assertSame(listener, container.earTrainingListener)
        // A test's recorder gets a listener of its own, and the microphone's comes back after it.
        container.testEarTrainingRecorder = FakeAudioRecorder.silence()
        assertNotSame(listener, container.earTrainingListener)
        container.testEarTrainingRecorder = null
        assertSame(listener, container.earTrainingListener)
    }
}
