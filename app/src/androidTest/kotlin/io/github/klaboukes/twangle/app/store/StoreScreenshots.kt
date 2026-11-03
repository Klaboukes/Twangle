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
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
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
 * *Phone screenshots*): each case opens the real app in its theme (Ink light or dark), feeds the tuner a fixed
 * sound through a stand-in microphone ([FixedSoundRecorder], nothing plays out loud), saves the
 * screen as it is (`raw-<n>.png`, without the status bar) and the finished picture
 * (`store-<n>.png`: the screen on a 1080 × 1920 canvas with the caption above, cut in on the part
 * that sells it, [Shot.focus], so its words can be read at Play's size; [StoreCanvas]). Focus mode's
 * picture stands on its side, 1920 × 1080, as the phone does.
 *
 * With `storeTablet=true` (a tablet, at least 600 dp the short way) it makes the 10-inch tablet set
 * instead (docs/store-listing.md, *Tablet screenshots*): the tablet locked upright and at the normal
 * text size (its own rotation and text size are put back: `scripts/ui-tests.sh` and [withFontScale]),
 * the screens without the status and navigation bars, each the whole tablet on the same canvases
 * (Phase 65: on its side the tuner's two panes left an empty middle).
 *
 * Opt-in, as it only makes files and changes the phone's Twangle (its theme and settings are
 * left as the last picture had them; the app's language is put back): without the instrumentation
 * argument `storeScreenshots=<Play locale>` it has no cases at all. `storeCaptions` carries that
 * locale's `screenshot_captions.txt` in Base64 (the shell would mangle other scripts).
 * `scripts/store-screenshots.sh` passes both, one locale at a time, and puts the pictures where Play
 * and F-Droid look for them.
 */
@RunWith(Parameterized::class)
class StoreScreenshots(private val shot: Shot) {

    /**
     * The eight screenshots of the plan, in its order, each in its theme, with the part of the
     * phone's screen it must show ([focus], fractions of its height from the top; the tablet's upright
     * screens fit whole).
     */
    enum class Shot(val dark: Boolean, val focus: ClosedFloatingPointRange<Float> = 0f..1f) {
        /** The chip, the ruler's A, the lens's green trail and "In tune" (the pegs left out). */
        IN_TUNE(dark = false, focus = 0f..0.62f),

        /**
         * Practice's four cards and the "Last time" row, the bar's three tabs under them: the whole
         * screen (2.0's navigation, Phase 69). Its case also saves the metronome's screen for the
         * website (`raw-metronome.png`), which has no store picture of its own.
         */
        PRACTICE(dark = true),

        /** The ruler's E, the lens's amber trail left of the zone, "Tighten" and the cents. */
        TUNE_UP(dark = true, focus = 0f..0.68f),

        /** The sheet "What are you tuning?": its instrument pills and the first tunings. */
        INSTRUMENTS(dark = false, focus = 0.24f..0.80f),

        /**
         * Two phones side by side, each whole (2.0, Phase 74): the strum check, its button tapped and the open strings
         * strummed, each string's column and the count; and Chords with Listen on, a strummed chord named and charted.
         */
        STRUM_AND_CHORD(dark = true),
        CHORDS_AND_CAPO(dark = false),

        /** At twice the text size: the ruler, the lens, "Tighten" and the cents. */
        TALKBACK(dark = true, focus = 0.065f..0.71f),
        PITCH_TRAIL(dark = false),
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
    private val tablet = arguments.getString(TABLET_ARGUMENT).toBoolean()
    private val out = File(arguments.getString(OUTPUT_DIR_ARGUMENT) ?: error("no $OUTPUT_DIR_ARGUMENT")).apply { mkdirs() }

    @Before
    fun setUp() {
        if (tablet) {
            check(app.resources.configuration.smallestScreenWidthDp >= TABLET_DP) { "$TABLET_ARGUMENT=true, but this is no tablet" }
            // Upright for the whole set, where the tuner is one column drawn larger with no empty middle
            // (on its side its two panes left one); focus mode still turns it. scripts/ui-tests.sh puts the lock back.
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 0")
            check(waitUntil { app.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }) { "the tablet didn't stand upright" }
        }
        // Play's Norwegian folder is no-NO, but Android finds the texts (values-nb) only for nb.
        showLanguage(if (locale == "no-NO") "nb-NO" else locale)
        runBlocking {
            container.focusMode.value = false
            settings.setTheme(if (shot.dark) ThemeMode.DARK else ThemeMode.LIGHT)
            // Ink's own colours, not the wallpaper's.
            settings.setDynamicColor(false)
            settings.setA4(NoteMapper.DEFAULT_A4_HZ)
            settings.setInstrument(Instruments.GUITAR)
            settings.setTuning(Tunings.STANDARD)
            settings.setMode(TunerMode.AUTO)
            settings.setNotation(null)
            settings.setInTunePrecision(InTunePrecision.DEFAULT)
            // As a new player sees it: the string tick-off is off by default (2.0).
            settings.setStringTickOff(false)
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
        container.focusMode.value = false
    }

    @Test
    fun screenshot() {
        // The tablet's screens at the normal text size, whatever its own (the TalkBack one at twice it).
        if (tablet) withFontScale(1f) { shoot() } else shoot()
    }

    private fun shoot() {
        when (shot) {
            Shot.IN_TUNE -> tuner { recorder.play(StoreSounds.heldNote(StoreSounds.hz(StoreSounds.A_STRING), NOTE_SECONDS)); settle(NOTE_SETTLE_MS) }
            Shot.PRACTICE -> {
                runBlocking {
                    // "Last time": Chords, C major.
                    settings.setPracticeTool(PracticeTool.CHORDS)
                    settings.setPracticeRoot(PitchClass.C)
                    settings.setPracticeChordQuality(ChordQuality.MAJOR)
                    settings.setMetronomeBpm(METRONOME_BPM)
                    settings.setMetronomeMeter(Meter.FOUR_FOUR)
                }
                lateinit var cards: Bitmap
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitFor(navigation)
                    measureCrop(scenario)
                    click(NavigationTestTags.tab(Destination.PRACTICE))
                    waitFor(PracticeTestTags.LAST_TIME)
                    settle(PANEL_SETTLE_MS)
                    cards = screenshot(scenario, "${shot.number}")
                    // The website's metronome picture: 120 beats a minute in 4/4, the first beat lit.
                    click(NavigationTestTags.tab(Destination.METRONOME))
                    click(MetronomeTestTags.START)
                    settle(PANEL_SETTLE_MS)
                    metronome.beat(0)
                    SystemClock.sleep(BEAT_LIT_MS)
                    screenshot(scenario, "metronome")
                }
                save(cards)
            }
            Shot.TUNE_UP -> tuner { recorder.play(flatLowE()); settle(NOTE_SETTLE_MS) }
            Shot.INSTRUMENTS -> tuner {
                // The panel is a window of its own, whose parts UI Automator can't find by tag.
                click(TunerTestTags.PICKER_CHIP)
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
            // The strum check's button, then Listen in Chords (2.0, Phase 74, after the owner's guitar check).
            Shot.STRUM_AND_CHORD -> {
                // The button tapped, then the open strings strummed, two of them off: each string's column and "4 of 6 in tune".
                val strings = capture(name = "${shot.number}a") {
                    click(TunerTestTags.STRUM_BUTTON)
                    waitFor(TunerTestTags.STRUM_PANEL)
                    recorder.play(StoreSounds.strum(StoreSounds.OPEN_STRINGS, STRUM_SECONDS, StoreSounds.OPEN_STRINGS_OFF))
                    settle(OPEN_STRUM_SETTLE_MS)
                }
                // Listen in Chords: C, G, then A minor, pictured while the last rings (Listen settles a strum behind, so on
                // the Xiaomi it shows G major, named and charted).
                val chord = capture(name = "${shot.number}b") {
                    click(NavigationTestTags.tab(Destination.PRACTICE))
                    click(PracticeTestTags.card(PracticeTool.CHORDS))
                    waitFor(PracticeTestTags.CHORDS)
                    click(PracticeTestTags.CHORDS_LISTEN)
                    recorder.play(
                        StoreSounds.strum(StoreSounds.C_MAJOR, STRUM_SECONDS) +
                            StoreSounds.strum(StoreSounds.G_MAJOR, STRUM_SECONDS) +
                            StoreSounds.strum(StoreSounds.A_MINOR, STRUM_SECONDS),
                    )
                    settle((2 * STRUM_SECONDS * 1000).toLong() + LAST_STRUM_SETTLE_MS)
                }
                compose(listOf(listOf(strings, chord).map { Window(it.asImageBitmap()) }), staggered = true)
            }
            Shot.CHORDS_AND_CAPO -> {
                runBlocking {
                    settings.setPracticeTool(PracticeTool.CHORDS)
                    settings.setPracticeRoot(PitchClass.C)
                    settings.setPracticeChordQuality(ChordQuality.MAJOR)
                    // A song in A played with G shapes: capo on the 2nd fret.
                    settings.setPracticeCapoShapes(SongKey(PitchClass.G))
                    settings.setPracticeCapoFret(CAPO_FRET)
                }
                val screens = mutableListOf<Bitmap>()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitFor(navigation)
                    // Practice's cards (Phase 69) open each tool full screen, Back leading to the cards.
                    click(NavigationTestTags.tab(Destination.PRACTICE))
                    click(PracticeTestTags.card(PracticeTool.CHORDS))
                    waitFor(PracticeTestTags.CHORDS)
                    settle(PANEL_SETTLE_MS)
                    screens += screenshot(scenario, "${shot.number}a")
                    click(PracticeTestTags.BACK)
                    click(PracticeTestTags.card(PracticeTool.CAPO))
                    device.wait(Until.gone(By.res(PracticeTestTags.CHORDS)), WAIT_MS)
                    settle(PANEL_SETTLE_MS)
                    screens += screenshot(scenario, "${shot.number}b")
                }
                compose(listOf(screens.map { Window(it.asImageBitmap()) }), staggered = true)
            }
            Shot.PITCH_TRAIL -> {
                val screen = capture(
                    after = { scenario ->
                        // Upright again before the activity goes, as focus mode off does with auto-rotate off.
                        container.focusMode.value = false
                        waitUntil { !scenario.landscape() }
                    },
                ) { scenario ->
                    // The trail is the lens's look since 2.0's Phase 71: nothing to pick. A held A4, as sung or played high up the neck: any note, so chromatic (Any note, set as the
                    // sheet's switch would set it). Not through UI Automator: once the trail moves, it waits for a still screen
                    // before each step, up to its time-out.
                    runBlocking { settings.setMode(TunerMode.CHROMATIC) }
                    // Focus mode turns the phone on its side, the lens in the big meter's place.
                    container.focusMode.value = true
                    check(waitUntil { scenario.landscape() }) { "focus mode didn't turn the screen" }
                    // Without its controls the tuner takes the whole screen: no rail and no status bar to cut off.
                    check(waitUntil { scenario.barsHidden() }) { "focus mode didn't go full screen" }
                    measureCrop(scenario)
                    recorder.play(StoreSounds.vibratoNote(StoreSounds.hz(StoreSounds.A4), NOTE_SECONDS, VIBRATO_STEADY_SECONDS))
                    settle(TRAIL_SETTLE_MS)
                }
                // On its side, as the phone (or tablet) stands in focus mode, on a canvas on its side, so
                // the trail, the note and "In tune" read the right way round (Phase 65; until then the
                // picture was turned upright, its words running up the screen).
                compose(listOf(listOf(Window(screen.asImageBitmap()))), spec = CanvasSpec.LANDSCAPE)
            }
        }
    }

    /** The navigation bar: every picture opens upright (focus mode turns it only after the app is open). */
    private val navigation get() = NavigationTestTags.BAR

    /** Opens the tuner, waits for it, runs [steps] and saves the screen, cut in on this shot's focus. */
    private fun tuner(steps: () -> Unit) = save(capture { steps() })

    /**
     * Opens the app on the tuner, runs [steps] in it, takes a picture of the screen (`raw-<name>.png`)
     * and closes the app ([after] first, with the app still open).
     */
    private fun capture(
        name: String = "${shot.number}",
        after: (ActivityScenario<MainActivity>) -> Unit = {},
        steps: (ActivityScenario<MainActivity>) -> Unit,
    ): Bitmap =
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(navigation)
            measureCrop(scenario)
            steps(scenario)
            screenshot(scenario, name).also {
                // Quiet again: while a note moves the screen, the main thread is never idle, and
                // onActivity waits for it.
                recorder.play(FloatArray(0))
                after(scenario)
            }
        }

    /**
     * Places [screen] on the canvas with this shot's caption (and [bubble]) and saves it: on the phone
     * cut in on [Shot.focus]; the tablet's upright screen whole, which fits about as large.
     */
    private fun save(screen: Bitmap, bubble: SpeechBubble? = null) =
        compose(listOf(listOf(Window(screen.asImageBitmap(), if (tablet) 0f..1f else shot.focus))), bubble = bubble)

    /**
     * The whole screen as it is, without the status bar, saved as `raw-<name>.png`. On its side the
     * navigation rail is cut off too, so the trail and the note fill the picture. What to cut off is
     * measured beforehand ([measureCrop]), while the screen is still.
     */
    private fun screenshot(scenario: ActivityScenario<MainActivity>, name: String): Bitmap {
        val (top, left, bottom) = crop ?: measureCrop(scenario)
        val full = instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        val screen = Bitmap.createBitmap(full, left, top, full.width - left, full.height - top - bottom)
        write(screen, "raw-$name.png")
        return screen
    }

    /**
     * The part of the screen [screenshot] cuts off: the status bar's height, on a phone on its side where
     * the navigation rail ends, and on the tablet the navigation bar's height.
     */
    private var crop: Triple<Int, Int, Int>? = null

    /**
     * Measures [crop] now. `onActivity` waits for the main thread to be idle, which it never is while
     * a note moves the meter or the trail: so this is done before the sound starts.
     */
    private fun measureCrop(scenario: ActivityScenario<MainActivity>): Triple<Int, Int, Int> {
        var measured = Triple(0, 0, 0)
        scenario.onActivity { activity ->
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val top = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val side = insets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())?.left ?: 0
            // Focus mode's full-screen tuner has no rail (and its status bar is hidden, so [top] is 0).
            val railShown = landscape && insets?.isVisible(WindowInsetsCompat.Type.statusBars()) != false
            val rail = if (railShown) side + (RAIL_DP * activity.resources.displayMetrics.density).toInt() else side
            measured = if (tablet) {
                Triple(top, 0, insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0)
            } else {
                Triple(top, rail, 0)
            }
        }
        crop = measured
        return measured
    }

    /** Places [rows] of screens on [spec]'s canvas with this shot's caption and saves it as `store-<n>.png`. */
    private fun compose(
        rows: List<List<Window>>,
        spec: CanvasSpec = CanvasSpec.PORTRAIT,
        bubble: SpeechBubble? = null,
        staggered: Boolean = false,
    ) {
        val caption = captions().getOrNull(shot.ordinal) ?: error("no caption ${shot.number} for $locale")
        val canvas = renderCanvas(spec.width, spec.height) {
            StoreCanvas(caption, rows, shot.dark, spec, bubble, staggered)
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

    /**
     * What TalkBack says about the note: the live region over it, as TalkBack reads it. Waited for,
     * up to [READING_WAIT_MS], the tree read afresh every [READING_POLL_MS]: the reading is paced
     * (`PacedSpeech`, the region's text blank until the first is published), and on a phone warm from
     * the earlier languages, at twice the text size, it once came after the note's 3 s settle (sv-SE
     * and vi, 2026-10-29).
     */
    private fun reading(): SpeechBubble {
        val end = SystemClock.uptimeMillis() + READING_WAIT_MS
        var said = spokenNote()
        while (said == null && SystemClock.uptimeMillis() < end) {
            settle(READING_POLL_MS)
            said = spokenNote()
        }
        // As a sentence on the screen: Dutch note names are said in small letters ("e 2, …").
        return SpeechBubble((said ?: error("TalkBack's reading of the note wasn't found")).replaceFirstChar { it.titlecase(Locale.forLanguageTag(locale)) })
    }

    /** The live region's text as it is now, or null while there is none yet. */
    private fun spokenNote(): String? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        return generateSequence(listOf(root)) { level -> level.flatMap { n -> (0 until n.childCount).mapNotNull { n.getChild(it) } }.ifEmpty { null } }
            .flatten()
            .firstOrNull { it.liveRegion != android.view.View.ACCESSIBILITY_LIVE_REGION_NONE && !it.contentDescription.isNullOrBlank() }
            ?.contentDescription?.toString()
    }

    /** The low E string 12 cents flat. */
    private fun flatLowE() = StoreSounds.heldNote(StoreSounds.hz(StoreSounds.LOW_E, cents = -12.0), NOTE_SECONDS)

    private fun ActivityScenario<MainActivity>.barsHidden(): Boolean {
        var hidden = false
        onActivity {
            val insets = ViewCompat.getRootWindowInsets(it.window.decorView)
            hidden = insets != null && !insets.isVisible(WindowInsetsCompat.Type.statusBars())
        }
        return hidden
    }

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

        /** `true` makes the 10-inch tablet set (on a tablet only). */
        const val TABLET_ARGUMENT = "storeTablet"
        private const val TABLET_DP = 600
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        private const val TEST_TIMEOUT_SECONDS = 120L
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

        /** The open strings' strum is pictured this long after it starts: its marks stand (after about half a second) while it rings. */
        private const val OPEN_STRUM_SETTLE_MS = 1_800L

        private const val CAPO_FRET = 2

        /** The website's metronome picture: its tempo, and how long after the first beat it is taken, still lit. */
        private const val METRONOME_BPM = 120
        private const val BEAT_LIT_MS = 120L

        /** The navigation rail's width (NavigationBar.kt, RAIL_WIDTH). */
        private const val RAIL_DP = 80

        /** The A is held steady this long, then the vibrato sets in; pictured when the trail shows both. */
        private const val VIBRATO_STEADY_SECONDS = 2.5
        private const val TRAIL_SETTLE_MS = 5_200L

        /** TalkBack users often read at large text: the screenshot shows the largest Twangle supports. */
        private const val TALKBACK_FONT_SCALE = 2f

        /** How long [reading] waits for TalkBack's paced reading of the note, and how often it looks. */
        private const val READING_WAIT_MS = 5_000L
        private const val READING_POLL_MS = 200L
    }
}
