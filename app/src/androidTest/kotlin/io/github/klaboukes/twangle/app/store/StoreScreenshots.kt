package io.github.klaboukes.twangle.app.store

import android.Manifest
import android.content.res.Configuration
import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.MainActivity
import io.github.klaboukes.twangle.app.TwangleApplication
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.PracticeChart
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The Play Store's phone screenshots, made on the phone in one language (docs/store-listing.md,
 * *Phone screenshots*): each case opens the real app in its style and theme, feeds the tuner a fixed
 * sound through a stand-in microphone ([FixedSoundRecorder], nothing plays out loud), saves the
 * screen as it is (`raw-<n>.png`, without the status bar) and the finished picture
 * (`store-<n>.png`: the screen on a 1080 × 1920 canvas with the caption above, [StoreCanvas]).
 *
 * Opt-in, as it only makes files and changes the phone's Twangle (its style, theme and settings are
 * left as the last picture had them; the app's language is put back): without the instrumentation
 * argument `storeScreenshots=<Play locale>` it has no cases at all. `storeCaptions` carries that
 * locale's `screenshot_captions.txt` in Base64 (the shell would mangle other scripts).
 * `scripts/store-screenshots.sh` passes both, one locale at a time, and puts the pictures where Play
 * and F-Droid look for them.
 */
@RunWith(Parameterized::class)
class StoreScreenshots(private val shot: Shot) {

    /** The eight screenshots of the plan, in its order, each in its style and theme. */
    enum class Shot(val style: VisualStyle, val dark: Boolean) {
        IN_TUNE(VisualStyle.CONTEMPORARY, dark = false),
        TUNE_UP(VisualStyle.CLASSIC, dark = true),
        INSTRUMENTS(VisualStyle.RETRO, dark = false),
        TALKBACK(VisualStyle.CONTEMPORARY, dark = true),
        CHORD_NAME(VisualStyle.FUTURISTIC, dark = true),
        METRONOME(VisualStyle.SPACE_AGE, dark = true),
        CHORDS_AND_CAPO(VisualStyle.ANCIENT, dark = false),
        PITCH_TRAIL(VisualStyle.CLASSIC, dark = false),
        ;

        val number: Int get() = ordinal + 1
    }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<TwangleApplication>()
    private val container = app.container
    private val settings = container.settingsRepository
    private val device = UiDevice.getInstance(instrumentation)
    private val recorder = FixedSoundRecorder()
    private val metronome = FakeMetronomePlayer()
    private val arguments = InstrumentationRegistry.getArguments()
    private val locale = arguments.getString(LOCALE_ARGUMENT).orEmpty()
    private val out = File(arguments.getString(OUTPUT_DIR_ARGUMENT) ?: error("no $OUTPUT_DIR_ARGUMENT")).apply { mkdirs() }

    @Before
    fun setUp() {
        showLanguage(locale)
        runBlocking {
            settings.setTourSeen(true)
            settings.setFocusMode(false)
            settings.setStyle(shot.style)
            settings.setTheme(if (shot.dark) ThemeMode.DARK else ThemeMode.LIGHT)
            // The style's own colours, not the wallpaper's.
            settings.setDynamicColor(false)
            settings.setA4(NoteMapper.DEFAULT_A4_HZ)
            settings.setInstrument(Instruments.GUITAR)
            settings.setTuning(Tunings.STANDARD)
            settings.setMode(TunerMode.AUTO)
            settings.setNotation(null)
            settings.setInTunePrecision(InTunePrecision.DEFAULT)
            settings.setDisplay(TunerDisplay.NEEDLE)
            settings.setMeterLook(MeterLook.DEFAULT)
            settings.setWaveLook(WaveLook.DEFAULT)
            settings.setInTuneEffect(InTuneEffect.DEFAULT)
            settings.setShowTuneHint(true)
            settings.setShowSignalBars(false)
            settings.setKeepScreenOn(true)
            // The phone lies still on the desk overnight.
            settings.setVibrateInTune(false)
            settings.setMetronomeVibrate(false)
        }
        container.testAudioRecorder = recorder
        container.testMetronomePlayer = metronome
    }

    @After
    fun tearDown() {
        container.testAudioRecorder = null
        container.testMetronomePlayer = null
        runBlocking { settings.setFocusMode(false) }
    }

    @Test
    fun screenshot() {
        when (shot) {
            Shot.IN_TUNE -> tuner { recorder.play(StoreSounds.heldNote(StoreSounds.hz(StoreSounds.A_STRING), NOTE_SECONDS)); settle(NOTE_SETTLE_MS) }
            Shot.TUNE_UP -> tuner { recorder.play(flatLowE()); settle(NOTE_SETTLE_MS) }
            Shot.INSTRUMENTS -> tuner {
                // The panel is a window of its own, whose parts UI Automator can't find by tag.
                click(NavigationTestTags.INSTRUMENT)
                settle(SHEET_SETTLE_MS)
            }
            Shot.TALKBACK -> withFontScale(TALKBACK_FONT_SCALE) {
                var spoken: SpeechBubble? = null
                val screen = capture { scenario ->
                    recorder.play(flatLowE())
                    settle(NOTE_SETTLE_MS)
                    spoken = reading()
                }
                save(screen, bubble = spoken ?: error("TalkBack's reading of the note wasn't found"))
            }
            Shot.CHORD_NAME -> tuner {
                click(TunerTestTags.MORE_TOOLS)
                click(TunerTestTags.tool(TunerTool.CHORD_NAME))
                waitFor(TunerTestTags.CHORD_PANEL)
                recorder.play(
                    StoreSounds.strum(StoreSounds.C_MAJOR, STRUM_SECONDS) +
                        StoreSounds.strum(StoreSounds.G_MAJOR, STRUM_SECONDS) +
                        StoreSounds.strum(StoreSounds.A_MINOR, STRUM_SECONDS),
                )
                settle((2 * STRUM_SECONDS * 1000).toLong() + LAST_STRUM_SETTLE_MS)
            }
            Shot.METRONOME -> {
                runBlocking {
                    settings.setPracticeTool(PracticeTool.METRONOME)
                    settings.setMetronomeBpm(METRONOME_BPM)
                    settings.setMetronomeMeter(Meter.FOUR_FOUR)
                }
                save(
                    capture {
                        click(NavigationTestTags.tab(Destination.METRONOME))
                        click(MetronomeTestTags.START)
                        settle(PANEL_SETTLE_MS)
                        // The first beat of the bar, as if just heard.
                        metronome.beat(0)
                        SystemClock.sleep(BEAT_LIT_MS)
                    },
                )
            }
            Shot.CHORDS_AND_CAPO -> {
                runBlocking {
                    settings.setPracticeTool(PracticeTool.CHORDS)
                    settings.setPracticeRoot(PitchClass.C)
                    settings.setPracticeChordQuality(ChordQuality.MAJOR)
                    settings.setPracticeChart(PracticeChart.CHORDS)
                    // A song in A played with G shapes: capo on the 2nd fret.
                    settings.setPracticeCapoShapes(SongKey(PitchClass.G))
                    settings.setPracticeCapoFret(CAPO_FRET)
                }
                val screens = mutableListOf<Bitmap>()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitFor(NavigationTestTags.BAR)
                    click(NavigationTestTags.tab(Destination.METRONOME))
                    waitFor(PracticeTestTags.CHORDS)
                    settle(PANEL_SETTLE_MS)
                    screens += screenshot(scenario, "7a")
                    click(PracticeTestTags.tool(PracticeTool.CAPO))
                    device.wait(Until.gone(By.res(PracticeTestTags.CHORDS)), WAIT_MS)
                    settle(PANEL_SETTLE_MS)
                    screens += screenshot(scenario, "7b")
                }
                compose(screens)
            }
            Shot.PITCH_TRAIL -> {
                save(
                    capture(
                        after = { scenario ->
                            // Upright again before the activity goes, as focus mode off does with auto-rotate off.
                            runBlocking { settings.setFocusMode(false) }
                            waitUntil { !scenario.landscape() }
                        },
                    ) { scenario ->
                        click(TunerTestTags.MORE_TOOLS)
                        click(TunerTestTags.tool(TunerTool.PITCH_TRAIL))
                        // A held A4, as sung or played high up the neck: any note, so chromatic. The last
                        // step through UI Automator: once the trail moves, it waits for a still screen
                        // before each step, up to its time-out.
                        click(TunerTestTags.mode(TunerMode.CHROMATIC))
                        // Focus mode turns the phone on its side, the trail in the big meter's place.
                        runBlocking { settings.setFocusMode(true) }
                        check(waitUntil { scenario.landscape() }) { "focus mode didn't turn the screen" }
                        measureCrop(scenario)
                        // "Tap to show the controls" shows for its first few seconds.
                        settle(TAP_HINT_MS)
                        recorder.play(StoreSounds.vibratoNote(StoreSounds.hz(StoreSounds.A4), NOTE_SECONDS, VIBRATO_STEADY_SECONDS))
                        settle(TRAIL_SETTLE_MS)
                    },
                )
            }
        }
    }

    /** Opens the tuner, waits for it, runs [steps] and saves the screen. */
    private fun tuner(steps: () -> Unit) = save(capture { steps() })

    /**
     * Opens the app on the tuner, runs [steps] in it, takes a picture of the screen and closes the
     * app ([after] first, with the app still open).
     */
    private fun capture(after: (ActivityScenario<MainActivity>) -> Unit = {}, steps: (ActivityScenario<MainActivity>) -> Unit): Bitmap =
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(NavigationTestTags.BAR)
            measureCrop(scenario)
            steps(scenario)
            screenshot(scenario, "${shot.number}").also {
                // Quiet again: while a note moves the screen, the main thread is never idle, and
                // onActivity waits for it.
                recorder.play(FloatArray(0))
                after(scenario)
            }
        }

    /** Places [screen] on the canvas with this shot's caption (and [bubble]) and saves it. */
    private fun save(screen: Bitmap, bubble: SpeechBubble? = null) = compose(listOf(screen), bubble)

    /**
     * The whole screen as it is, without the status bar, saved as `raw-<name>.png`. On its side the
     * navigation rail is cut off too, so the trail and the note fill the picture. What to cut off is
     * measured beforehand ([measureCrop]), while the screen is still.
     */
    private fun screenshot(scenario: ActivityScenario<MainActivity>, name: String): Bitmap {
        val (top, left) = crop ?: measureCrop(scenario)
        val full = instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        val screen = Bitmap.createBitmap(full, left, top, full.width - left, full.height - top)
        write(screen, "raw-$name.png")
        return screen
    }

    /** The part of the screen [screenshot] cuts off: the status bar's height, and on its side where the navigation rail ends. */
    private var crop: Pair<Int, Int>? = null

    /**
     * Measures [crop] now. `onActivity` waits for the main thread to be idle, which it never is while
     * a note moves the meter or the trail: so this is done before the sound starts.
     */
    private fun measureCrop(scenario: ActivityScenario<MainActivity>): Pair<Int, Int> {
        var measured = 0 to 0
        scenario.onActivity { activity ->
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val top = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val side = insets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())?.left ?: 0
            val rail = if (landscape) side + (RAIL_DP * activity.resources.displayMetrics.density).toInt() else 0
            measured = top to rail
        }
        crop = measured
        return measured
    }

    /** Places [screens] on the canvas with this shot's caption and saves it as `store-<n>.png`. */
    private fun compose(screens: List<Bitmap>, bubble: SpeechBubble? = null) {
        val caption = captions().getOrNull(shot.ordinal) ?: error("no caption ${shot.number} for $locale")
        val canvas = renderCanvas {
            StoreCanvas(caption, screens.map { it.asImageBitmap() }, shot.style, shot.dark, bubble)
        }
        write(canvas, "store-${shot.number}.png")
    }

    private fun write(bitmap: Bitmap, name: String) {
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** This locale's eight captions, from `storeCaptions` (Base64 of `screenshot_captions.txt`). */
    private fun captions(): List<String> {
        val encoded = arguments.getString(CAPTIONS_ARGUMENT) ?: error("no $CAPTIONS_ARGUMENT")
        return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8).replace("\r\n", "\n").trimEnd().lines()
    }

    /** What TalkBack says about the note: the live region over it, as TalkBack reads it. */
    private fun reading(): SpeechBubble {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: error("no window to read")
        val node = generateSequence(listOf(root)) { level -> level.flatMap { n -> (0 until n.childCount).mapNotNull { n.getChild(it) } }.ifEmpty { null } }
            .flatten()
            .firstOrNull { it.liveRegion != android.view.View.ACCESSIBILITY_LIVE_REGION_NONE && !it.contentDescription.isNullOrBlank() }
            ?: error("TalkBack's reading of the note wasn't found")
        // As a sentence on the screen: Dutch note names are said in small letters ("e 2, …").
        return SpeechBubble(node.contentDescription.toString().replaceFirstChar { it.titlecase(Locale.forLanguageTag(locale)) })
    }

    /** The low E string 12 cents flat. */
    private fun flatLowE() = StoreSounds.heldNote(StoreSounds.hz(StoreSounds.LOW_E, cents = -12.0), NOTE_SECONDS)

    private fun ActivityScenario<MainActivity>.landscape(): Boolean {
        var side = false
        onActivity { side = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        return side
    }

    private fun waitFor(tag: String) = check(device.wait(Until.hasObject(By.res(tag)), WAIT_MS)) { "$tag never showed" }

    private fun click(tag: String) = (device.wait(Until.findObject(By.res(tag)), WAIT_MS) ?: error("no $tag")).click()

    /** Lets the screen settle for [ms]. Not waitForIdle: the meter, the glow and the trail move all the time, so it would wait for its time-out. */
    private fun settle(ms: Long) = SystemClock.sleep(ms)

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(POLL_MS)
        }
        return condition()
    }

    /** Runs [block] with the phone's text at [scale] × its normal size, then puts the phone's own size back. */
    private fun withFontScale(scale: Float, block: () -> Unit) {
        val before = Settings.System.getFloat(app.contentResolver, Settings.System.FONT_SCALE, 1f)
        try {
            shell("settings put system font_scale $scale")
            check(waitUntil { app.resources.configuration.fontScale == scale }) { "the text size didn't change" }
            block()
        } finally {
            shell("settings put system font_scale $before")
            waitUntil { app.resources.configuration.fontScale == before }
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).let { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    /** Shows the app in [tag]'s language, as the Language setting does (Android keeps it per app). */
    private fun showLanguage(tag: String) {
        val wanted = Locale.forLanguageTag(tag)
        if (app.resources.configuration.locales[0].language == wanted.language && current == tag) return
        if (languageBefore == null) languageBefore = appLocales()
        setAppLocales(tag)
        check(waitUntil { app.resources.configuration.locales[0].language == wanted.language }) { "the app's language didn't change to $tag" }
        current = tag
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun shots(): List<Shot> =
            if (InstrumentationRegistry.getArguments().getString(LOCALE_ARGUMENT).isNullOrBlank()) emptyList() else Shot.entries

        /** The app's language before the first picture, put back after the last. */
        private var languageBefore: String? = null
        private var current: String? = null

        @AfterClass
        @JvmStatic
        fun putTheLanguageBack() {
            val before = languageBefore ?: return
            setAppLocales(before)
            languageBefore = null
            current = null
        }

        /**
         * The app's own languages, as tags ("" for the phone's). Android 13+ keeps them itself
         * (LocaleManager); AppCompat's own call would reach it only once one of its activities ran.
         */
        private fun appLocales(): String {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            } else {
                AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }
        }

        private fun setAppLocales(tags: String) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                instrumentation.targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
            } else {
                instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags)) }
            }
        }

        /** The instrumentation argument that turns the screenshots on: the Play locale, e.g. `nl-NL`. */
        const val LOCALE_ARGUMENT = "storeScreenshots"
        const val CAPTIONS_ARGUMENT = "storeCaptions"
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        private const val TEST_TIMEOUT_SECONDS = 60L
        private const val WAIT_MS = 5_000L
        private const val POLL_MS = 100L

        /** Long enough to outlast every picture. */
        private const val NOTE_SECONDS = 30.0

        /** A held note settles, its glow lit or its hint shown, well within this. */
        private const val NOTE_SETTLE_MS = 3_000L
        private const val PANEL_SETTLE_MS = 1_000L
        private const val SHEET_SETTLE_MS = 1_500L

        /** Each chord rings this long before the next; the last is pictured this long after it starts, bright and named. */
        private const val STRUM_SECONDS = 3.0
        private const val LAST_STRUM_SETTLE_MS = 1_800L

        private const val METRONOME_BPM = 120
        private const val BEAT_LIT_MS = 120L
        private const val CAPO_FRET = 2

        private const val TAP_HINT_MS = 4_500L

        /** The navigation rail's width (NavigationBar.kt, RAIL_WIDTH). */
        private const val RAIL_DP = 80

        /** The A is held steady this long, then the vibrato sets in; pictured when the trail shows both. */
        private const val VIBRATO_STEADY_SECONDS = 2.5
        private const val TRAIL_SETTLE_MS = 5_200L

        /** TalkBack users often read at large text: the screenshot shows the largest Twangle supports. */
        private const val TALKBACK_FONT_SCALE = 2f
    }
}
