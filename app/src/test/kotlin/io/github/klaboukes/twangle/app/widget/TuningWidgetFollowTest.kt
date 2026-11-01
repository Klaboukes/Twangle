package io.github.klaboukes.twangle.app.widget

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningWidgetFollowTest {

    @Test
    fun aRedrawThatFailsIsLoggedAndTheWidgetFollowsOn() = runBlocking {
        val settings = MutableStateFlow(AppSettings())
        val drawn = mutableListOf<WidgetState>()
        val logged = mutableListOf<RuntimeException>()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var first = true

        val job = TuningWidget.follow(
            settings,
            scope,
            draw = {
                if (first) {
                    first = false
                    throw IllegalStateException("The launcher went away")
                }
                drawn += it
            },
            logError = { logged += it },
        )
        repeat(3) { yield() }
        settings.value = AppSettings(mode = TunerMode.CHROMATIC)
        repeat(3) { yield() }
        settings.value = AppSettings(instrument = Instruments.BASS_4, tuning = Instruments.BASS_4.defaultTuning)
        repeat(3) { yield() }

        assertEquals("the failure was logged", 1, logged.size)
        assertEquals("and the next changes were drawn", 2, drawn.size)
        assertTrue("it still follows", job.isActive && scope.isActive)
        scope.cancel()
    }
}
