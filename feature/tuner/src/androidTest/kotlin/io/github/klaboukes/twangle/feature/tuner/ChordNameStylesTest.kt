package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.SettledChord
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * The chord name (Phase 30) in each of the six styles, light and dark: its panel in the meter's place,
 * the line under it, the "more tools" button above the strings. Upright every phase fits without
 * scrolling and nothing moves between phases; at 2× text in Dutch every part is wholly shown (every
 * language overnight). The menu lists the three tools and turns the chord name on. TalkBack hears the
 * chord in words. Synthetic strums through a fake microphone name A minor and C/E on the real screen.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class ChordNameStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private val shownState = mutableStateOf<TunerUiState>(named(AM))
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val shownDark = mutableStateOf(false)
    private val toolChanges = mutableListOf<Pair<String, Boolean>>()

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerContent(
                        shownState.value,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        onStrumCheckChange = { toolChanges += "strum" to it },
                        tunerTools = TunerTool.entries,
                        onIntonationCheckChange = { toolChanges += "intonation" to it },
                        chordName = true,
                        onChordNameChange = { toolChanges += "chord" to it },
                    )
                }
            }
        }
    }

    private val panel get() = compose.onNodeWithTag(TunerTestTags.CHORD_PANEL)
    private val status get() = compose.onNodeWithTag(TunerTestTags.CHORD_STATUS)
    private val moreTools get() = compose.onNodeWithTag(TunerTestTags.MORE_TOOLS)
    private val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4")

    private fun parts(controls: Boolean = true): List<Pair<String, SemanticsNodeInteraction>> = buildList {
        add("panel" to panel)
        add("status" to status)
        if (controls) {
            add("more tools" to moreTools)
            strings.forEach { add("string $it" to compose.onNodeWithText(it)) }
        }
    }

    /** Upright on a narrow phone and on the Xiaomi at 1.25×, light and dark: every phase in view, clear of each other, the line never moving. */
    @Test
    fun uprightEveryPhaseFitsAndNothingMoves() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
                shownSize.value = size
                shownFontScale.value = scale
                val tops = mutableSetOf<Float>()
                for ((name, state) in phases()) {
                    shownState.value = state
                    compose.waitForIdle()
                    val case = "$style ${if (dark) "dark" else "light"} $name ${size.width} x ${size.height} at ${scale}x:"
                    val bounds = assertAllInView(case, parts())
                    tops += bounds[1].top.value
                    tops += bounds[2].top.value
                    if (size == XIAOMI_UPRIGHT) picture("${if (dark) "dark" else "light"}-$name")
                }
                if (tops.size != 2) fail("$style ${size.width} x ${size.height}: the line or the strings moved between phases: $tops")
            }
        }
    }

    /** At 2× text in Dutch, upright and on its side: the panel, the line and the strings wholly shown. */
    @Test
    fun atTwiceTheTextSizeInDutchEveryPartIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for ((name, state) in phases()) {
                shownState.value = state
                compose.waitForIdle()
                for ((part, node) in parts(controls = size == PHONE_UPRIGHT)) assertWhollyShown(node, "$style nl $name $size at 2x: $part")
            }
        }
    }

    /** Every language at 2× text, upright and on its side, every phase: every text wholly shown (overnight). */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                for ((name, state) in phases()) {
                    shownState.value = state
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$style $language $name ${size.width} x ${size.height} at 2x:")
                }
            }
        }
    }

    /** The "more tools" menu lists the three tools, the chord name on (a switch); picking it again turns it off; the button names it. */
    @Test
    fun theMoreToolsMenuListsTheChordName() {
        show()
        moreTools.performClick()
        val chord = compose.onNodeWithTag(TunerTestTags.tool(TunerTool.CHORD_NAME))
        chord.assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_chord_name)).assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.tool(TunerTool.STRUM_CHECK)).assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.tool(TunerTool.INTONATION_CHECK)).assertIsDisplayed()
        assertEquals(ToggleableState.On, chord.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
        assertEquals(Role.Switch, chord.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        chord.performClick()
        compose.waitForIdle()
        assertEquals(listOf("chord" to false), toolChanges)
        assertEquals(
            context.getString(R.string.tuner_tool_on, context.getString(R.string.tuner_chord_name)),
            moreTools.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    /** TalkBack hears the chord in words, with its notes and the chords before it; the line names it too. */
    @Test
    fun talkBackHearsTheChordInWords() {
        shownState.value = named(AM, recent = listOf(SettledChord(G, PitchClass.G), SettledChord(C, PitchClass.C), SettledChord(AM, PitchClass.A)))
        show()
        val minor = context.getString(R.string.tuner_chord_minor, "A")
        val separator = context.getString(R.string.tuner_spoken_separator)
        assertEquals(
            listOf(
                minor + separator + context.getString(R.string.tuner_chord_spoken_notes, listOf("A", "C", "E").joinToString(separator)) +
                    separator + context.getString(
                        R.string.tuner_chord_spoken_recent,
                        context.getString(R.string.tuner_chord_major, "C") + separator + context.getString(R.string.tuner_chord_major, "G"),
                    ),
            ),
            panel.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription),
        )
        assertEquals(
            listOf(minor + separator + context.getString(R.string.tuner_chord_next)),
            status.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription),
        )
        // A slash chord, and a flat said as a word.
        shownState.value = named(C, bass = PitchClass.E)
        compose.waitForIdle()
        val slash = context.getString(R.string.tuner_chord_slash, context.getString(R.string.tuner_chord_major, "C"), "E")
        assertTrue(spoken(panel).startsWith(slash))
        shownState.value = named(Chord.of(PitchClass.A_SHARP, ChordQuality.MAJOR))
        compose.waitForIdle()
        val flat = context.getString(io.github.klaboukes.twangle.core.designsystem.R.string.spoken_flat, "B")
        assertTrue(spoken(panel), spoken(panel).startsWith(context.getString(R.string.tuner_chord_major, flat)))
        // The power chord and the 9ths by their own words.
        for ((quality, words) in listOf(
            ChordQuality.POWER_CHORD to R.string.tuner_chord_power_chord,
            ChordQuality.SEVENTH_SHARP_NINE to R.string.tuner_chord_seventh_sharp_nine,
            ChordQuality.MINOR_NINTH to R.string.tuner_chord_minor_ninth,
            ChordQuality.MAJOR_NINTH to R.string.tuner_chord_major_ninth,
        )) {
            shownState.value = named(Chord.of(PitchClass.E, quality))
            compose.waitForIdle()
            assertTrue(spoken(panel), spoken(panel).startsWith(context.getString(words, "E")))
        }
        // Waiting and not a clear chord.
        shownState.value = TunerUiState.ChordName(Tunings.STANDARD, phase = ChordPhase.UNCLEAR)
        compose.waitForIdle()
        assertEquals(context.getString(R.string.tuner_chord_unclear), spoken(panel))
        assertTrue(spoken(status).startsWith(context.getString(R.string.tuner_chord_unclear)))
    }

    /** The words follow the app's language (Dutch: "A mineur", "C majeur, E in de bas") and the notes the player's notation ("La"). */
    @Test
    fun theWordsFollowTheLanguageAndTheNotation() {
        shownLocale.value = "nl"
        shownState.value = named(AM)
        show()
        assertTrue(spoken(status), spoken(status).startsWith("a mineur", ignoreCase = true))
        shownState.value = named(AM, notation = Notation.SOLFEGE)
        compose.waitForIdle()
        assertTrue(spoken(status), spoken(status).startsWith("la mineur", ignoreCase = true))
        shownState.value = named(C, bass = PitchClass.E)
        compose.waitForIdle()
        assertTrue(spoken(status), spoken(status).startsWith("c majeur, e in de bas", ignoreCase = true))
    }

    /**
     * The phone check with synthetic strums through a fake microphone: the real screen and ViewModel,
     * the chord name turned on from the menu, then an A minor strum and a C over E: the panel names A
     * minor, then C major with E in the bass. With `-a chordScreens=true` it saves pictures.
     */
    @Test
    fun syntheticStrumsAreNamedOnTheRealScreen() {
        val recorder = StrumOnCue(strum(AM_SHAPE, 3.0) + strum(C_OVER_E_SHAPE, 3.0) + strum(E5_SHAPE, 3.0) + FloatArray(SAMPLE_RATE * 3))
        compose.mainClock.autoAdvance = true
        compose.setContent {
            PhoneScreen(XIAOMI_UPRIGHT, SystemBars.tab(onItsSide = false)) {
                TwangleTheme(style = style, darkTheme = true, animate = false) {
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerScreen(
                        audioRecorder = recorder,
                        settingsRepository = FakeSettingsRepository(),
                        tonePlayer = FakeTonePlayer(),
                        tools = ToolSwitches.ALL_ON,
                    )
                }
            }
        }
        moreTools.performClick()
        picture("real-1-menu")
        compose.onNodeWithTag(TunerTestTags.tool(TunerTool.CHORD_NAME)).performClick()
        compose.waitForIdle()
        picture("real-2-waiting")
        recorder.cue = true
        val minor = context.getString(R.string.tuner_chord_minor, "A")
        compose.waitUntil(STEP_WAIT_MS) { spoken(panel).startsWith(minor) }
        picture("real-3-am")
        val slash = context.getString(R.string.tuner_chord_slash, context.getString(R.string.tuner_chord_major, "C"), "E")
        compose.waitUntil(STEP_WAIT_MS) { spoken(panel).startsWith(slash) }
        picture("real-4-c-over-e")
        compose.waitUntil(STEP_WAIT_MS) { spoken(status).startsWith(slash) }
        // A power chord: E5, not E or Em.
        val power = context.getString(R.string.tuner_chord_power_chord, "E")
        compose.waitUntil(STEP_WAIT_MS) { spoken(panel).startsWith(power) }
        picture("real-5-e5")
        // Rung out: it stays.
        Thread.sleep(2_500)
        compose.waitForIdle()
        picture("real-6-faded")
        assertTrue(spoken(panel), spoken(panel).startsWith(power))
    }

    private fun spoken(node: SemanticsNodeInteraction): String =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    /** A fake microphone: silence, then [signal] once [cue] is set, then silence again, in real time. */
    private class StrumOnCue(private val signal: FloatArray) : AudioRecorder {
        override val config = AudioConfig()

        @Volatile var cue = false

        override fun chunks(): Flow<FloatArray> = flow {
            val chunk = FloatArray(config.chunkSize)
            val chunkMillis = config.chunkSize * 1000L / config.sampleRate
            var pos = 0
            while (true) {
                chunk.fill(0f)
                if (cue && pos < signal.size) {
                    val count = minOf(chunk.size, signal.size - pos)
                    System.arraycopy(signal, pos, chunk, 0, count)
                    pos += count
                }
                delay(chunkMillis)
                emit(chunk)
            }
        }
    }

    private fun picture(name: String) {
        if (arguments.getString(SCREENS_ARGUMENT) != "true") return
        val dir = arguments.getString(OUTPUT_DIR_ARGUMENT) ?: return
        compose.waitForIdle()
        val bitmap = compose.onPhoneScreen().captureToImage().asAndroidBitmap()
        File(File(dir).apply { mkdirs() }, "chord-$style-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The phases to look at: waiting, listening, named (a triad, a slash chord, a long seventh, a power chord, the longest name, faded with the last chords), not a clear chord. */
    private fun phases(): List<Pair<String, TunerUiState.ChordName>> = listOf(
        "waiting" to TunerUiState.ChordName(Tunings.STANDARD),
        "listening" to TunerUiState.ChordName(Tunings.STANDARD, phase = ChordPhase.LISTENING),
        "am" to named(AM),
        "slash" to named(C, bass = PitchClass.E, recent = listOf(SettledChord(AM, PitchClass.A), SettledChord(G, PitchClass.G), SettledChord(C, PitchClass.E))),
        "long" to named(Chord.of(PitchClass.A_SHARP, ChordQuality.MAJOR_SEVENTH), recent = listOf(SettledChord(C, PitchClass.E), SettledChord(G, PitchClass.G))),
        "power" to named(Chord.of(PitchClass.E, ChordQuality.POWER_CHORD), recent = listOf(SettledChord(G, PitchClass.G))),
        "longest" to named(Chord.of(PitchClass.A_SHARP, ChordQuality.MAJOR_NINTH), recent = listOf(SettledChord(Chord.of(PitchClass.D_SHARP, ChordQuality.SEVENTH_SHARP_NINE), PitchClass.D_SHARP))),
        "faded" to named(G7, faded = true, recent = listOf(SettledChord(C, PitchClass.E), SettledChord(AM, PitchClass.A), SettledChord(G7, PitchClass.G))),
        "listening again" to named(AM, faded = true, phase = ChordPhase.LISTENING),
        "unclear" to TunerUiState.ChordName(Tunings.STANDARD, phase = ChordPhase.UNCLEAR, recent = listOf(SettledChord(AM, PitchClass.A))),
    )

    private fun assertAllInView(case: String, parts: List<Pair<String, SemanticsNodeInteraction>>): List<DpRect> {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val bounds = parts.map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        for ((name, rect) in bounds) {
            if (rect.left < window.left - SLACK || rect.top < window.top - SLACK || rect.right > window.right + SLACK || rect.bottom > window.bottom + SLACK) {
                fail("$case $name $rect is outside the window $window")
            }
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                if (first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
        return bounds.map { it.second }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private val AM = Chord.of(PitchClass.A, ChordQuality.MINOR)
        private val C = Chord.of(PitchClass.C, ChordQuality.MAJOR)
        private val G = Chord.of(PitchClass.G, ChordQuality.MAJOR)
        private val G7 = Chord.of(PitchClass.G, ChordQuality.DOMINANT_SEVENTH)

        fun named(
            chord: Chord,
            bass: PitchClass? = chord.root,
            faded: Boolean = false,
            phase: ChordPhase = ChordPhase.NAMED,
            recent: List<SettledChord> = listOf(SettledChord(chord, bass)),
            notation: Notation = Notation.SHARPS,
        ) = TunerUiState.ChordName(
            tuning = Tunings.STANDARD,
            notation = notation,
            phase = phase,
            chord = SettledChord(chord, bass),
            faded = faded,
            recent = recent,
        )

        /** Open A minor (x02210), C over E (032010) and the power chord E5 (022xxx), as MIDI notes low to high. */
        private val AM_SHAPE = intArrayOf(45, 52, 57, 60, 64)
        private val C_OVER_E_SHAPE = intArrayOf(40, 48, 52, 55, 60, 64)
        private val E5_SHAPE = intArrayOf(40, 47, 52)

        private const val SAMPLE_RATE = 48_000

        /** A downstroke over [midi] at 48 kHz, strings 15 ms apart, [seconds] long, through the app's own pluck synthesis. */
        private fun strum(midi: IntArray, seconds: Double): FloatArray {
            val length = (seconds * SAMPLE_RATE).toInt()
            val out = FloatArray(length)
            val tone = FloatArray(length)
            val synth = PluckSynth(SAMPLE_RATE, durationSeconds = 4f)
            midi.forEachIndexed { i, m ->
                val delay = (i * 0.015 * SAMPLE_RATE).toInt()
                tone.fill(0f)
                synth.start((440.0 * 2.0.pow((m - 69) / 12.0)).toFloat())
                synth.render(tone, delay, length - delay)
                for (n in 0 until length) out[n] += tone[n]
            }
            val loudest = out.maxOf { abs(it) }
            for (n in out.indices) out[n] *= 0.4f / loudest
            return out
        }

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L
        private const val STEP_WAIT_MS = 8_000L
        private const val SCREENS_ARGUMENT = "chordScreens"
        private const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp
    }
}
