package io.github.klaboukes.twangle.feature.tuner.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
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
 * Pictures of every meter look in each style, light and dark, for the owner: set A with the marker
 * held 20 cents flat, set B in tune with the Bold glow fully lit. Each look sits on its style's
 * background, one look at a time, light above dark (the page setup of [MeterLooksTest]).
 *
 * Opt-in, as it only makes files: without the instrumentation argument `meterScreenshots=true` it
 * has no cases at all, so the normal suite pays nothing for it. `scripts/meter-look-screens.sh` sets it, collects the PNGs that
 * `scripts/ui-tests.sh` pulls into `feature/tuner/build/outputs/connected_android_test_additional_output/` and
 * builds the contact sheet in `build/owner-screens/meter-looks/`.
 */
@RunWith(Parameterized::class)
class MeterLookScreenshots(private val style: String) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val arguments = InstrumentationRegistry.getArguments()

    private val cents = mutableFloatStateOf(FLAT_CENTS)
    private val page = mutableIntStateOf(0)
    private val inTune = mutableStateOf(false)
    private val effect = mutableStateOf(InTuneEffect.CALM)

    private fun show() {
        compose.setContent {
            val look = MeterLook.entries[page.intValue]
            Column {
                for (dark in listOf(false, true)) {
                    TwangleTheme(darkTheme = dark, animate = false) {
                        Box(modifier = Modifier.testTag(caseTag(dark))) {
                            StyleBackdrop(Modifier.matchParentSize())
                            MeterFrame(
                                look = look,
                                cents = { cents.floatValue },
                                inTune = inTune.value,
                                glow = inTune.value,
                                effect = effect.value,
                                modifier = Modifier.padding(PADDING.dp).width(FRAME_WIDTH.dp),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Set A: every look 20 cents flat. */
    @Test
    fun flatInEveryLook() = eachLook(set = "a")

    /** Set B: every look in tune, its Bold glow fully lit. */
    @Test
    fun inTuneWithTheBoldGlowInEveryLook() {
        cents.floatValue = 0f
        inTune.value = true
        effect.value = InTuneEffect.BOLD
        eachLook(set = "b")
    }

    private fun eachLook(set: String) {
        val dir = arguments.getString(OUTPUT_DIR_ARGUMENT)
        assumeTrue("Gradle gives no $OUTPUT_DIR_ARGUMENT", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        show()
        for ((i, look) in MeterLook.entries.withIndex()) {
            page.intValue = i
            // The look's springs, the in-tune colour and the glow all settle before it is pictured.
            compose.waitForIdle()
            for (dark in listOf(false, true)) {
                val bitmap = compose.onNodeWithTag(caseTag(dark)).captureToImage().asAndroidBitmap()
                val name = listOf(set, style, look.name, if (dark) "DARK" else "LIGHT")
                    .joinToString("-") { it.lowercase().replace('_', '-') }
                File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<String> =
            if (InstrumentationRegistry.getArguments().getString(ARGUMENT) == "true") listOf("Ink") else emptyList()

        /** The instrumentation argument that turns these pictures on. */
        const val ARGUMENT = "meterScreenshots"

        /** Where `scripts/ui-tests.sh` (and Gradle's connected tests) collect extra files from. */
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        /** Set A's reading: clearly flat, so each look's marker stands away from the centre. */
        const val FLAT_CENTS = -20f

        /** About the meter's width on a phone held upright. */
        const val FRAME_WIDTH = 320
        const val PADDING = 16

        /** Twelve pictures, each taken and saved in well under a second. */
        const val TEST_TIMEOUT_SECONDS = 30L

        fun caseTag(dark: Boolean) = "screenshot_$dark"
    }
}
