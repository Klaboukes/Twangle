package io.github.klaboukes.twangle.wear

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pictures of the watch tuner (2.0's note, dot along the arc and word), for the PR and the owner (the emulator's
 * mic hears nothing). Only with `-e watchScreenshots true`; saved to the app's files folder on the watch:
 * `/sdcard/Android/data/io.github.klaboukes.twangle/files/`.
 */
@RunWith(AndroidJUnit4::class)
class WatchTunerScreenshots {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun onlyWhenAsked() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("watchScreenshots") == "true")
    }

    @Test
    fun listening() = capture("watch-listening", WatchTunerState.Listening)

    @Test
    fun farOff() = capture("watch-far-off", WatchTunerState.Detected(Note.of(PitchClass.D_SHARP, 3), -28))

    @Test
    fun close() = capture("watch-close", WatchTunerState.Detected(Note.of(PitchClass.E, 2), 8))

    @Test
    fun inTune() = capture("watch-in-tune", WatchTunerState.Detected(Note.of(PitchClass.A, 2), 1, inTune = true))

    private fun capture(name: String, state: WatchTunerState) {
        compose.setContent {
            WatchTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) { WatchTuner(state, onRetry = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(2_000) // the arc's green and the dot settle
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
