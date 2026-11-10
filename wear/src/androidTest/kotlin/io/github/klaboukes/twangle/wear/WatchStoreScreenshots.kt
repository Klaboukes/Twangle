package io.github.klaboukes.twangle.wear

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The watch app's Play Store screenshots (Phase 88; docs/store-listing.md, *Watch screenshots*), made by
 * `scripts/watch-screenshots.sh` on the Galaxy Watch 5 or the Wear OS emulator: for each Play locale in
 * `-e watchStoreScreenshots` (`en-US,nl-NL,…`), the app's own pages as the watch shows them, in that language
 * (the words, and the note names its players write), with nothing heard or played for real:
 *  1. the tuner with the A held in tune: the arc's green glow, "In tune" and "0 cents";
 *  2. the tuner with the low E 12 cents flat: the dot left of the top, "Tighten" and "12 cents flat";
 *  3. the metronome page at 120 BPM, playing, its first beat lit.
 *
 * Each is the watch's round screen only, no frame: the square the app draws in, black round the circle, as the
 * watch's own screenshot is, but drawn at [SIZE_PX] (the watch's pages are laid out in fractions of the screen,
 * so they look the same at any size) rather than the watch's 396 px, so they stay sharp at the size Play shows
 * them, above Play's 384 px least. Opaque, so Play takes them as they are. Saved as
 * `<additionalTestOutputDir>/<locale>/watch-<n>.png`.
 */
@RunWith(AndroidJUnit4::class)
class WatchStoreScreenshots {

    @get:Rule
    val compose = createComposeRule()

    /** What is drawn now: a locale and a picture. */
    private var shown by mutableStateOf<Pair<String, Shot>?>(null)
    private var layer: GraphicsLayer? = null

    @Test(timeout = 15 * 60 * 1000L)
    fun pictures() {
        val arguments = InstrumentationRegistry.getArguments()
        val locales = arguments.getString(LOCALE_ARGUMENT)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        assumeTrue("only with -e $LOCALE_ARGUMENT <Play locales>", locales.isNotEmpty())
        val out = File(arguments.getString(OUTPUT_DIR_ARGUMENT) ?: error("no $OUTPUT_DIR_ARGUMENT"))

        compose.setContent {
            val recorded = rememberGraphicsLayer().also { layer = it }
            // Larger than the watch's window: laid out unbounded at the top left and recorded whole into a layer.
            Box(
                Modifier
                    .wrapContentSize(AbsoluteAlignment.TopLeft, unbounded = true)
                    .drawWithContent {
                        recorded.record { this@drawWithContent.drawContent() }
                        drawLayer(recorded)
                    },
            ) {
                shown?.let { (locale, shot) -> key(locale, shot) { Picture(locale, shot) } }
            }
        }
        for (locale in locales) {
            val dir = File(out, locale).apply { mkdirs() }
            for (shot in Shot.entries) {
                shown = locale to shot
                // The arc's green, the dot, the page and the beat light settle.
                compose.mainClock.advanceTimeBy(SETTLE_MS)
                compose.waitForIdle()
                File(dir, "watch-${shot.number}.png").outputStream().use { capture().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** The picture as it stands, opaque, at [SIZE_PX] square. */
    private fun capture(): Bitmap = runBlocking {
        withContext(Dispatchers.Main) {
            val drawn = (layer ?: error("no layer")).toImageBitmap().asAndroidBitmap()
            // A hardware bitmap can't be written as PNG: a software copy, opaque so it is saved without alpha.
            Bitmap.createBitmap(drawn.copy(Bitmap.Config.ARGB_8888, true), 0, 0, SIZE_PX, SIZE_PX).also { it.setHasAlpha(false) }
        }
    }

    /**
     * [shot] in [locale] (a Play locale: Norwegian's folder is `no-NO`, but its texts are found for `nb`): the
     * app's pages as [WatchApp] lays them out, the watch's own size in dp drawn at [SIZE_PX].
     */
    @Composable
    private fun Picture(locale: String, shot: Shot) {
        val tag = if (locale == "no-NO") "nb-NO" else locale
        val screenDp = LocalConfiguration.current.screenWidthDp
        DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList(tag))) {
            // Inside the language's override, which sets its own density from the watch's.
            CompositionLocalProvider(LocalDensity provides Density(SIZE_PX.toFloat() / screenDp, fontScale = 1f)) {
                Box(Modifier.requiredSize(screenDp.dp).background(Color.Black)) {
                    WatchTheme {
                        AppScaffold {
                            WatchAppPages(
                                tools = WatchToolSwitches.ALL_ON,
                                opening = shot.page,
                                request = null,
                                onRequestDone = {},
                                tunerPage = {
                                    ScreenScaffold(timeText = {}) { WatchTuner(shot.tuner ?: WatchTunerState.Listening, onRetry = {}) }
                                },
                                metronomePage = {
                                    ScreenScaffold(timeText = {}) {
                                        WatchMetronome(METRONOME, onTurn = {}, onTempo = {}, onPlay = {}, onClick = {}, pixelsPerStep = 1f)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** The pictures, in the order Play shows them. */
    enum class Shot(val number: Int, val page: WatchPage, val tuner: WatchTunerState.Detected?) {
        IN_TUNE(1, WatchPage.TUNER, WatchTunerState.Detected(Note.of(PitchClass.A, 2), 0, inTune = true)),
        FLAT(2, WatchPage.TUNER, WatchTunerState.Detected(Note.of(PitchClass.E, 2), -12)),
        METRONOME(3, WatchPage.METRONOME, null),
    }

    companion object {
        /** The instrumentation argument that turns the pictures on: the Play locales, e.g. `nl-NL` or `en-US,nl-NL`. */
        const val LOCALE_ARGUMENT = "watchStoreScreenshots"
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        /** Each picture's side: well above Play's 384 px least for Wear OS screenshots. */
        const val SIZE_PX = 960

        private const val SETTLE_MS = 2_000L

        /** The metronome as the phone's picture shows it: 120 BPM, playing with its click, the first beat lit. */
        private val METRONOME = WatchMetronomeState(bpm = 120, playing = true, click = true, litBeat = 0)
    }
}
