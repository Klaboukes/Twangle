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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
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
import kotlinx.coroutines.flow.first
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
 * *Phone screenshots*): each case opens the real app in Ink dark, feeds the tuner a fixed
 * sound through a stand-in microphone ([FixedSoundRecorder], nothing plays out loud), saves the
 * screen as it is (`raw-<n>.png`, without the status bar) and the finished picture (`store-<n>.png`,
 * [StoreCanvas]): the from-scratch set (owner, 2026-11-09), one moment a picture drawn large under its words,
 * each cut from the real screen by the bounds of the parts that show it ([Shot.parts]), so it fits every phone,
 * the tablet and every language without fractions to keep up; one whole phone (4), and the privacy picture
 * drawn, not shot (3).
 *
 * With `storeTablet=true` (a tablet, at least 600 dp the short way) it makes the 10-inch tablet set
 * instead (docs/store-listing.md, *Tablet screenshots*): the tablet locked upright and at the normal
 * text size (its own rotation and text size are put back: `scripts/ui-tests.sh` and [withFontScale]),
 * the screens without the status and navigation bars, cut the same way.
 *
 * Opt-in, as it only makes files and changes the phone's Twangle (its theme and settings are
 * left as the last picture had them; the app's language is put back): without the instrumentation
 * argument `storeScreenshots=<Play locale>` it has no cases at all. Several locales, commas between, make each
 * one's pictures in turn in one run, into a folder per locale (`<locale>/store-<n>.png`): one run per language cost
 * about 15 s of start-up each (2026-11-09). `storeCaptions` carries each locale's `screenshot_captions.txt` as
 * `<locale>:<Base64>`, commas between (Base64, as the shell would mangle other scripts); `storeShots` ("6", "3,6")
 * makes only those pictures. `scripts/store-screenshots.sh` passes them, a few locales a run, and puts the
 * pictures where Play and F-Droid look for them.
 */
@RunWith(Parameterized::class)
class StoreScreenshots(private val locale: String, private val shot: Shot) {

    /**
     * The eight screenshots of the plan, in its order (the from-scratch set, owner 2026-11-09): the line over the tuner
     * in tune, the metronome (it is more than a tuner), nothing to sign up for and nothing leaving the phone, then the
     * whole app, the chord name, the Practice tools, the strum check (late: the owner doubts it yet) and big text.
     * [accent] colours its marked words; [parts] are the test tags whose bounds, together, are the part of the screen it
     * shows ([Moment.Cut]), [fade] softening its sides where the tuner's ruler runs to the screen's edges.
     */
    enum class Shot(val accent: Accent, val parts: Regex? = null, val fade: Boolean = false, val highContrast: Boolean = false) {
        /** The line, "It does what you need it to do. And nothing else.", over the A held in tune: the ruler, Classic and "In tune". */
        LINE(Accent.IN_TUNE, TUNER_PARTS, fade = true),

        /** The metronome at 120 in 4/4, its first beat lit: the tempo, the ruler, the beat bars, the meters and Start. */
        METRONOME(Accent.PRIMARY, Regex("metronome_(slower|faster|bpm|lights|tap|start)")),

        /** Drawn, no screen: the microphone and three facts (no sign-up, no internet needed, nothing recorded or sent). */
        PRIVACY(Accent.PRIMARY),

        /** The whole app on a phone, a bass's low E flat: the note, its peg and "Tighten", the bar's three tabs. */
        WHOLE_APP(Accent.FAR_OFF),

        /** Chords with Listen on: a strummed A minor named and charted. */
        CHORD_NAME(Accent.PRIMARY, Regex("practice_(name_picker|chords_listen|chord_chart)")),

        /** The Practice tools' cards under the "Last used" row, large. */
        PRACTICE(Accent.PRIMARY, Regex("practice_(last_time|card_.+)")),

        /** The strum check after the open strings were strummed, two of them off: each string's column and "4 of 6 in tune". */
        STRUM_CHECK(Accent.IN_TUNE, Regex("tuner_(strum_panel|strum_close|word)")),

        /**
         * At twice the text size and in the high-contrast look (Phase 86), its canvas too, a string a little sharp: the
         * ruler, the needle, "Loosen" and the cents, and TalkBack's reading. The picture for players who see poorly.
         */
        TALKBACK(Accent.FAR_OFF, TUNER_PARTS, fade = true, highContrast = true),
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
    private val tablet = arguments.getString(TABLET_ARGUMENT).toBoolean()
    private val out = File(File(arguments.getString(OUTPUT_DIR_ARGUMENT) ?: error("no $OUTPUT_DIR_ARGUMENT")), locale).apply { mkdirs() }

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
            settings.setTheme(ThemeMode.DARK)
            // On or off for this picture, whatever the phone's own contrast.
            settings.setHighContrast(shot.highContrast)
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
            settings.setKeepScreenOn(true)
            // The phone lies still on the desk.
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
            Shot.LINE -> cut { recorder.play(StoreSounds.heldNote(StoreSounds.hz(StoreSounds.A_STRING), NOTE_SECONDS)); settle(NOTE_SETTLE_MS) }
            Shot.METRONOME -> {
                runBlocking {
                    settings.setMetronomeBpm(METRONOME_BPM)
                    settings.setMetronomeMeter(Meter.FOUR_FOUR)
                }
                // 120 beats a minute in 4/4, the first beat lit; also the website's metronome picture.
                cut(after = { click(MetronomeTestTags.START) }) {
                    click(NavigationTestTags.tab(Destination.METRONOME))
                    click(MetronomeTestTags.START)
                    settle(PANEL_SETTLE_MS)
                    metronome.beat(0)
                    SystemClock.sleep(BEAT_LIT_MS)
                }
                File(out, "raw-${shot.number}.png").copyTo(File(out, "raw-metronome.png"), overwrite = true)
            }
            Shot.PRIVACY -> compose(Moment.Facts(caption().lines))
            Shot.WHOLE_APP -> {
                // A bass, so the one picture of the whole app shows it tunes more than a guitar.
                runBlocking {
                    settings.setInstrument(Instruments.BASS_4)
                    settings.setTuning(Instruments.BASS_4.tunings.first())
                }
                val screen = capture { recorder.play(StoreSounds.heldNote(StoreSounds.hz(BASS_LOW_E, cents = -12.0), NOTE_SECONDS)); settle(NOTE_SETTLE_MS) }
                compose(Moment.Phone(screen.asImageBitmap()))
            }
            // Listen in Chords (2.0, Phase 74): C, G, then A minor three times, pictured while one rings. Listen settles a
            // strum behind, and later on a slower device: at a fixed time the tablet still showed G major, or A minor's
            // name over G's chart (2026-11-09). So it waits until A minor is heard (Listen saves the chord heard as a
            // pick), then gives its chart a moment.
            Shot.CHORD_NAME -> cut {
                // From C major, so A minor saved means it was heard (the language before leaves it saved).
                runBlocking {
                    settings.setPracticeRoot(PitchClass.C)
                    settings.setPracticeChordQuality(ChordQuality.MAJOR)
                }
                click(NavigationTestTags.tab(Destination.PRACTICE))
                click(PracticeTestTags.card(PracticeTool.CHORDS))
                waitFor(PracticeTestTags.CHORDS)
                click(PracticeTestTags.CHORDS_LISTEN)
                recorder.play(
                    StoreSounds.strum(StoreSounds.C_MAJOR, STRUM_SECONDS) +
                        StoreSounds.strum(StoreSounds.G_MAJOR, STRUM_SECONDS) +
                        StoreSounds.strum(StoreSounds.A_MINOR, STRUM_SECONDS) +
                        StoreSounds.strum(StoreSounds.A_MINOR, STRUM_SECONDS) +
                        StoreSounds.strum(StoreSounds.A_MINOR, STRUM_SECONDS),
                )
                settle((2 * STRUM_SECONDS * 1000).toLong())
                check(
                    waitUntil {
                        val shown = runBlocking { settings.settings.first() }
                        shown.practiceRoot == PitchClass.A && shown.practiceChordQuality == ChordQuality.MINOR
                    },
                ) { "Listen never named the A minor" }
                settle(CHORD_CHART_SETTLE_MS)
            }
            Shot.PRACTICE -> {
                runBlocking {
                    // "Last used": Chords, C major.
                    settings.setPracticeTool(PracticeTool.CHORDS)
                    settings.setPracticeRoot(PitchClass.C)
                    settings.setPracticeChordQuality(ChordQuality.MAJOR)
                }
                cut {
                    click(NavigationTestTags.tab(Destination.PRACTICE))
                    waitFor(PracticeTestTags.LAST_TIME)
                    settle(PANEL_SETTLE_MS)
                }
            }
            Shot.STRUM_CHECK -> cut {
                // The strum check's button, then the open strings strummed, two of them off.
                click(TunerTestTags.STRUM_BUTTON)
                waitFor(TunerTestTags.STRUM_PANEL)
                recorder.play(StoreSounds.strum(StoreSounds.OPEN_STRINGS, STRUM_SECONDS, StoreSounds.OPEN_STRINGS_OFF))
                settle(OPEN_STRUM_SETTLE_MS)
            }
            Shot.TALKBACK -> withFontScale(TALKBACK_FONT_SCALE) {
                var spoken: SpeechBubble? = null
                cut(bubble = { spoken ?: error("TalkBack's reading of the note wasn't found") }) {
                    recorder.play(heldLowE(cents = 10.0))
                    settle(NOTE_SETTLE_MS)
                    spoken = reading()
                }
            }
        }
    }

    /**
     * Opens the app, runs [steps] in it, takes the screen and saves it on the canvas cut to [Shot.parts]: their bounds
     * together, measured on the screen as it stands, the full width and a margin above and below, on the screen's own
     * background ([edgeColour]). [after] runs with the app still open; [bubble] gives TalkBack's words, read in [steps].
     */
    private fun cut(after: () -> Unit = {}, bubble: (() -> SpeechBubble)? = null, steps: () -> Unit) {
        val parts = shot.parts ?: error("$shot has no parts to cut")
        lateinit var area: IntRect
        val screen = capture(after = { after() }) {
            steps()
            area = areaOf(parts)
        }
        compose(Moment.Cut(screen.asImageBitmap(), area, edgeColour(screen, area), shot.fade), bubble = bubble?.invoke())
    }

    /**
     * The part of the screen the test tags matching [parts] cover, in the saved screen's pixels ([crop] taken off): their
     * bounds together, the whole width, [CUT_MARGIN_DP] more above and below. Read from the accessibility tree as it is
     * now, not through UI Automator, which waits for an idle screen that a moving meter never gives.
     */
    private fun areaOf(parts: Regex): IntRect {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: error("no window to measure")
        val bounds = generateSequence(listOf(root)) { level -> level.flatMap { n -> (0 until n.childCount).mapNotNull { n.getChild(it) } }.ifEmpty { null } }
            .flatten()
            .filter { node -> node.viewIdResourceName?.let { parts.matches(it) } == true }
            .map { node -> android.graphics.Rect().also { node.getBoundsInScreen(it) } }
            .filter { !it.isEmpty }
            .toList()
        check(bounds.isNotEmpty()) { "none of ${parts.pattern} is on the screen" }
        val (top, left, bottom) = crop ?: error("the screen wasn't measured")
        val margin = (CUT_MARGIN_DP * app.resources.displayMetrics.density).toInt()
        val full = instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        val height = full.height - top - bottom
        return IntRect(
            left = 0,
            top = (bounds.minOf { it.top } - top - margin).coerceAtLeast(0),
            right = full.width - left,
            bottom = (bounds.maxOf { it.bottom } - top + margin).coerceAtMost(height),
        )
    }

    /**
     * The screen's background around [area]: the middle colour (by brightness) of pixels along its edges, which lie on
     * the backdrop (flat, or tinted while the tuner hears a note), so the canvas around the cut is the same colour.
     */
    private fun edgeColour(screen: Bitmap, area: IntRect): Color {
        val right = minOf(area.right, screen.width) - 1
        val bottom = minOf(area.bottom, screen.height) - 1
        val xs = area.left..right step EDGE_STEP
        val ys = area.top..bottom step EDGE_STEP
        val pixels = xs.flatMap { listOf(screen.getPixel(it, area.top), screen.getPixel(it, bottom)) } +
            ys.flatMap { listOf(screen.getPixel(area.left, it), screen.getPixel(right, it)) }
        return Color(pixels.sortedBy { android.graphics.Color.luminance(it) }[pixels.size / 2])
    }

    /** The navigation bar: every picture opens upright (focus mode turns it only after the app is open). */
    private val navigation get() = NavigationTestTags.BAR

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
            opened(scenario)
            steps(scenario)
            screenshot(scenario, name).also {
                // Quiet again: while a note moves the screen, the main thread is never idle, and
                // onActivity waits for it.
                recorder.play(FloatArray(0))
                after(scenario)
            }
        }

    /**
     * Waits for the app just opened: its navigation bar, then the screen itself in this locale's language,
     * then measures [crop]. The app taking the language isn't enough: once its first screen still came up
     * in the old one, mirrored the old way too (Arabic's first picture, 2026-11-06).
     */
    private fun opened(scenario: ActivityScenario<MainActivity>) {
        waitFor(navigation)
        val wanted = app.resources.configuration.locales[0].language
        var shown = ""
        check(waitUntil { scenario.onActivity { shown = it.resources.configuration.locales[0].language }; shown == wanted }) {
            "the screen came up in $shown, not $wanted"
        }
        measureCrop(scenario)
    }

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

    /** Draws [moment] on the canvas under this shot's caption (and [bubble]) and saves it as `store-<n>.png`. */
    private fun compose(moment: Moment, bubble: SpeechBubble? = null) {
        val caption = caption()
        val canvas = renderCanvas(CanvasSpec.PORTRAIT.width, CanvasSpec.PORTRAIT.height) {
            StoreCanvas(caption, moment, shot.accent, bubble = bubble, highContrast = shot.highContrast)
        }
        write(canvas, "store-${shot.number}.png")
    }

    /** This shot's line of the locale's captions. */
    private fun caption(): StoreCaption =
        StoreCaption.parse(captions().getOrNull(shot.ordinal) ?: error("no caption ${shot.number} for $locale"))

    private fun write(bitmap: Bitmap, name: String) {
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** This locale's eight captions, from `storeCaptions` (`<locale>:<Base64 of screenshot_captions.txt>`, commas between). */
    private fun captions(): List<String> {
        val encoded = (arguments.getString(CAPTIONS_ARGUMENT) ?: error("no $CAPTIONS_ARGUMENT")).split(',')
            .firstOrNull { it.substringBefore(':') == locale }?.substringAfter(':') ?: error("no captions for $locale")
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

    /** The low E string held [cents] off: flat for "Tighten", sharp for "Loosen". */
    private fun heldLowE(cents: Double) = StoreSounds.heldNote(StoreSounds.hz(StoreSounds.LOW_E, cents = cents), NOTE_SECONDS)

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
        @Parameterized.Parameters(name = "{0} {1}")
        fun shots(): List<Array<Any>> {
            val arguments = InstrumentationRegistry.getArguments()
            val locales = arguments.getString(LOCALE_ARGUMENT).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            // Only these pictures, by number ("6" or "3,6"), so one changed picture needs no run of all eight.
            val only = arguments.getString(SHOTS_ARGUMENT).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
            val shots = if (only.isEmpty()) Shot.entries else Shot.entries.filter { it.number in only }
            // A locale's pictures one after another, so its language is set once.
            return locales.flatMap { locale -> shots.map { arrayOf<Any>(locale, it) } }
        }

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

        /** The instrumentation argument that turns the screenshots on: the Play locales, e.g. `nl-NL` or `nl-NL,de-DE`. */
        const val LOCALE_ARGUMENT = "storeScreenshots"
        const val CAPTIONS_ARGUMENT = "storeCaptions"

        /** Only these pictures, by number, commas between (`scripts/store-screenshots.sh --shots`); all eight without it. */
        const val SHOTS_ARGUMENT = "storeShots"

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

        /** Each chord rings this long before the next. */
        private const val STRUM_SECONDS = 3.0

        /** After Listen names the A minor, its chart takes this to replace the one before. */
        private const val CHORD_CHART_SETTLE_MS = 1_000L

        /** The open strings' strum is pictured this long after it starts: its marks stand (after about half a second) while it rings. */
        private const val OPEN_STRUM_SETTLE_MS = 1_800L

        /** The tuner's parts a picture of it shows: the ruler, the meter and the word under it. */
        private val TUNER_PARTS = Regex("tuner_(ruler|gauge|word)")

        /** The bass's low E (E1), the whole app's note. */
        private const val BASS_LOW_E = 28

        /** The cut reaches this far above and below its parts; the edges' colour is read every [EDGE_STEP] pixels. */
        private const val CUT_MARGIN_DP = 8
        private const val EDGE_STEP = 8

        /** The website's metronome picture: its tempo, and how long after the first beat it is taken, still lit. */
        private const val METRONOME_BPM = 120
        private const val BEAT_LIT_MS = 120L

        /** The navigation rail's width (NavigationBar.kt, RAIL_WIDTH). */
        private const val RAIL_DP = 80

        /** TalkBack users often read at large text: the screenshot shows the largest Twangle supports. */
        private const val TALKBACK_FONT_SCALE = 2f

        /** How long [reading] waits for TalkBack's paced reading of the note, and how often it looks. */
        private const val READING_WAIT_MS = 5_000L
        private const val READING_POLL_MS = 200L
    }
}
