package io.github.klaboukes.twangle.feature.tuner

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Pictures of the whole tuner screen in each style, light and dark, for the owner: the style's
 * background behind the A string in tune (the low E and the A ticked off as tuned), on a 393 × 825 dp phone (the Xiaomi 12X inside its bars),
 * so a change to a background or an in-tune colour can be looked at in every style at once.
 *
 * Opt-in, as it only makes files: without the instrumentation argument `styleScreenshots=true` it has
 * no cases at all, so the normal suite pays nothing for it. `scripts/style-screens.sh` sets it, collects
 * the PNGs that `scripts/ui-tests.sh` pulls into `feature/tuner/build/outputs/connected_android_test_additional_output/`
 * and builds the contact sheet in `build/owner-screens/styles/`.
 */
@RunWith(Parameterized::class)
class StyleScreenshots(private val style: String) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val arguments = InstrumentationRegistry.getArguments()
    private val shownDark = mutableStateOf(false)

    @Test
    fun theTunerInTuneLightAndDark() {
        val dir = arguments.getString(OUTPUT_DIR_ARGUMENT)
        assumeTrue("Gradle gives no $OUTPUT_DIR_ARGUMENT", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        compose.setContent {
            PhoneScreen(PHONE, SystemBars.NONE) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerContent(
                        state = IN_TUNE,
                        mode = TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize(),
                        animationsOff = true,
                        tunedStrings = TunedStrings(Tunings.STANDARD, 0b11L),
                        // As in the app by default: "Show tuning help" on, so the ticks show.
                        showTuneHint = true,
                    )
                }
            }
        }
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            // The background's cross-fade, the in-tune colour and the glow all settle before the picture.
            compose.waitForIdle()
            val bitmap = compose.onPhoneScreen().captureToImage().asAndroidBitmap()
            val name = "style-$style-${if (dark) "dark" else "light"}".lowercase().replace('_', '-')
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<String> =
            if (InstrumentationRegistry.getArguments().getString(ARGUMENT) == "true") listOf("Ink") else emptyList()

        /** The instrumentation argument that turns these pictures on. */
        const val ARGUMENT = "styleScreenshots"

        /** Where `scripts/ui-tests.sh` (and Gradle's connected tests) collect extra files from. */
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        /** The Xiaomi 12X's screen inside its status and gesture bars. */
        private val PHONE = DpSize(393.dp, 825.dp)

        /** The A string, dead in tune. */
        private val IN_TUNE = TunerUiState.Detected(
            mode = TunerMode.AUTO,
            tuning = Tunings.STANDARD,
            noteName = "A",
            octave = 2,
            frequencyHz = 110f,
            cents = 0f,
            targetString = 1,
            inTune = true,
        )

        /** Two pictures, each taken and saved in well under a second. */
        const val TEST_TIMEOUT_SECONDS = 30L
    }
}
