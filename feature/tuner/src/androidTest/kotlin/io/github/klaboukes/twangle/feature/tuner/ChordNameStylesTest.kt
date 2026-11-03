package io.github.klaboukes.twangle.feature.tuner

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.designsystem.StyleBackdrop
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.SettledChord
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.components.ChordSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A fretted chord's name heard by itself (2.0, Phase 74; 1.1's chord name without its mode): the symbol in the ruler's
 * place ("Am", "C/E", "B♭maj9"), its words in the word's place ("A minor"), which TalkBack says; faded once its strum
 * has rung out; nothing while a chord is only being listened to or wasn't clear (the ruler stays). Upright on a narrow
 * phone and on the owner's Xiaomi at 1.25× text, light and dark, on its side and in focus mode (clear of the exit
 * button), and in Any note: the name within the ruler's place, wholly shown, and nothing else moves. At 2× text in
 * Dutch every part is wholly shown (every language overnight). Synthetic strums through a fake microphone name A
 * minor, C over E and E5 on the real screen (`-a chordScreens=true` saves pictures).
 */
@RunWith(AndroidJUnit4::class)
class ChordNameStylesTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private val shownHeard = mutableStateOf<HeardStrum>(HeardStrum.FrettedChord(named(AM), 1))
    private val shownMode = mutableStateOf(TunerMode.AUTO)
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val shownDark = mutableStateOf(false)
    private val shownFocus = mutableStateOf(false)

    private fun show() {
        compose.setContent {
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(shownSize.value, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
                    StyleBackdrop(Modifier.fillMaxSize())
                    TunerContent(
                        TunerUiState.Listening(shownMode.value, Tunings.STANDARD),
                        shownMode.value,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        heard = shownHeard.value,
                        focusMode = shownFocus.value,
                        onFocusModeChange = {},
                        animationsOff = true,
                    )
                }
            }
        }
    }

    private val chordName get() = compose.onNodeWithTag(TunerTestTags.CHORD_NAME, useUnmergedTree = true)
    private val ruler get() = compose.onNodeWithTag(TunerTestTags.RULER, useUnmergedTree = true)
    private val wordText get() = compose.onNodeWithTag(TunerTestTags.WORD_TEXT, useUnmergedTree = true)
    private fun heardChord(state: TunerUiState.ChordName) {
        shownHeard.value = HeardStrum.FrettedChord(state, 1)
        compose.waitForIdle()
    }

    /** Upright on a narrow phone and on the Xiaomi at 1.25×, light and dark: every chord's symbol within the ruler's place, its words in the word's, nothing else moving. */
    @Test
    fun uprightTheNameStandsInTheRulersPlace() {
        show()
        for (dark in listOf(false, true)) {
            shownDark.value = dark
            for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
                shownSize.value = size
                shownFontScale.value = scale
                assertChordsInTheRulersPlace("${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x", pictures = size == XIAOMI_UPRIGHT)
            }
        }
    }

    /** On its side (the wide layout) and in focus mode, where the name keeps clear of the exit button. */
    @Test
    fun onItsSideAndInFocusMode() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        assertChordsInTheRulersPlace("on its side")
        shownFocus.value = true
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.COMPACT).assertIsDisplayed()
        assertChordsInTheRulersPlace("in focus mode")
        val exit = compose.onNodeWithTag(TunerTestTags.EXIT_FOCUS).getBoundsInRoot()
        heardChord(named(Chord.of(PitchClass.A_SHARP, ChordQuality.MAJOR_NINTH), bass = PitchClass.D))
        val name = chordName.getBoundsInRoot()
        if (name.overlaps(exit)) fail("in focus mode the name $name overlaps the exit button $exit")
    }

    /** In Any note a chord is named too (no pegs, the same ruler's place). */
    @Test
    fun anyNoteNamesAChordToo() {
        shownMode.value = TunerMode.CHROMATIC
        show()
        chordName.assert(SemanticsMatcher.expectValue(ChordSymbol, "Am"))
        assertEquals(context.getString(R.string.tuner_chord_minor, "A"), wordText.text())
    }

    /** A chord only being listened to, or not clear, shows nothing: the ruler and "Play a string" stay. So does nothing heard (the chord name's brake on). */
    @Test
    fun noNameWithoutAChord() {
        show()
        for (state in listOf(
            TunerUiState.ChordName(Tunings.STANDARD, phase = ChordPhase.LISTENING),
            TunerUiState.ChordName(Tunings.STANDARD, phase = ChordPhase.UNCLEAR, recent = listOf(SettledChord(AM, PitchClass.A))),
        )) {
            heardChord(state)
            chordName.assertDoesNotExist()
            ruler.assertExists()
            assertEquals(context.getString(R.string.tuner_hint_play_string), wordText.text())
        }
        shownHeard.value = HeardStrum.None
        compose.waitForIdle()
        chordName.assertDoesNotExist()
        ruler.assertExists()
    }

    /** At 2× text in Dutch, upright and on its side: the name and its words wholly shown. */
    @Test
    fun atTwiceTheTextSizeInDutchEveryPartIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for ((name, state) in chords()) {
                heardChord(state)
                assertWhollyShown(chordName, "nl $name $size at 2x: the name")
                assertWhollyShown(wordText, "nl $name $size at 2x: the words")
            }
        }
    }

    /** Every language at 2× text, upright and on its side, every chord: every text wholly shown (overnight). */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                for ((name, state) in chords()) {
                    heardChord(state)
                    compose.assertEveryTextWhollyShown("$language $name ${size.width} x ${size.height} at 2x:")
                }
            }
        }
    }

    /** TalkBack hears the chord in words through the word, calmly paced: a slash chord, a flat said as a word, the power chord and the 9ths by their own words. */
    @Test
    fun talkBackHearsTheChordInWords() {
        show()
        val minor = context.getString(R.string.tuner_chord_minor, "A")
        said(minor)
        heardChord(named(C, bass = PitchClass.E))
        said(context.getString(R.string.tuner_chord_slash, context.getString(R.string.tuner_chord_major, "C"), "E"))
        heardChord(named(Chord.of(PitchClass.A_SHARP, ChordQuality.MAJOR)))
        val flat = context.getString(io.github.klaboukes.twangle.core.designsystem.R.string.spoken_flat, "B")
        said(context.getString(R.string.tuner_chord_major, flat))
        // On screen the flat is a sign.
        assertEquals(context.getString(R.string.tuner_chord_major, "B♭"), wordText.text())
        for ((quality, words) in listOf(
            ChordQuality.POWER_CHORD to R.string.tuner_chord_power_chord,
            ChordQuality.SEVENTH_SHARP_NINE to R.string.tuner_chord_seventh_sharp_nine,
            ChordQuality.MINOR_NINTH to R.string.tuner_chord_minor_ninth,
            ChordQuality.MAJOR_NINTH to R.string.tuner_chord_major_ninth,
        )) {
            heardChord(named(Chord.of(PitchClass.E, quality)))
            said(context.getString(words, "E"))
        }
        // The name in the ruler's place is a drawing TalkBack skips, as the ruler is.
        assertEquals(null, chordName.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    /** The words follow the app's language (Dutch: "A mineur", "C majeur, E in de bas") and the symbol the player's notation ("Lam"). */
    @Test
    fun theWordsFollowTheLanguageAndTheNotation() {
        shownLocale.value = "nl"
        show()
        assertTrue(wordText.text(), wordText.text().startsWith("a mineur", ignoreCase = true))
        heardChord(named(AM, notation = Notation.SOLFEGE))
        assertTrue(wordText.text(), wordText.text().startsWith("la mineur", ignoreCase = true))
        chordName.assert(SemanticsMatcher.expectValue(ChordSymbol, "Lam"))
        heardChord(named(C, bass = PitchClass.E))
        assertTrue(wordText.text(), wordText.text().startsWith("c majeur, e in de bas", ignoreCase = true))
        chordName.assert(SemanticsMatcher.expectValue(ChordSymbol, "C/E"))
    }

    /**
     * Synthetic strums through a fake microphone on the real screen and ViewModel: an A minor strum, a C over E and an
     * E5. Since the strum check became a button (after the owner's guitar check, 2026-11-03) the tuner names no chord by
     * itself: the ruler stays (the chord name goes to Chords, as Listen). With `-a chordScreens=true` it saves pictures.
     */
    @Test
    fun syntheticStrumsAreNotNamedOnTheRealScreen() {
        val recorder = StrumOnCue(
            FakeStrums.strum(FakeStrums.midiHz(*AM_SHAPE), 3.0) + FakeStrums.strum(FakeStrums.midiHz(*C_OVER_E_SHAPE), 3.0) +
                FakeStrums.strum(FakeStrums.midiHz(*E5_SHAPE), 3.0) + FakeStrums.quiet(3.0),
        )
        compose.mainClock.autoAdvance = true
        compose.setContent {
            PhoneScreen(XIAOMI_UPRIGHT, SystemBars.tab(onItsSide = false)) {
                TwangleTheme(darkTheme = true, animate = false) {
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
        compose.waitUntil(STEP_WAIT_MS) { compose.onAllNodesWithTag(TunerTestTags.RULER, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        picture("real-1-waiting")
        recorder.cue = true
        // The three strums ring for 9 s; none is named while they do.
        repeat(9) {
            Thread.sleep(1_000)
            compose.waitForIdle()
            assertEquals("after ${it + 1} s", null, shownSymbol())
        }
        picture("real-2-not-named")
    }

    /** Each chord: its symbol within the ruler's place (the ruler gone), wholly shown, its words as the word; the lens, the word and the pegs where they are without it. */
    private fun assertChordsInTheRulersPlace(case: String, pictures: Boolean = false) {
        shownHeard.value = HeardStrum.None
        compose.waitForIdle()
        val rulerPlace = ruler.getBoundsInRoot()
        val parts = buildList {
            add("lens" to compose.onNodeWithTag(TunerTestTags.GAUGE))
            add("word" to compose.onNodeWithTag(TunerTestTags.WORD))
            if (!shownFocus.value && shownMode.value == TunerMode.AUTO) add("first peg" to compose.onNodeWithText("E2"))
        }
        val before = parts.map { (_, node) -> node.getBoundsInRoot() }
        for ((name, state) in chords()) {
            heardChord(state)
            ruler.assertDoesNotExist()
            val rect = chordName.getBoundsInRoot()
            val chord = "$case $name: the name $rect"
            if (rect.top < rulerPlace.top - SLACK || rect.bottom > rulerPlace.bottom + SLACK || rect.left < rulerPlace.left - SLACK || rect.right > rulerPlace.right + SLACK) {
                fail("$chord isn't within the ruler's place $rulerPlace")
            }
            assertWhollyShown(chordName, chord)
            assertEquals(chord, wordsOf(state), wordText.text())
            parts.forEachIndexed { i, (part, node) ->
                val now = node.getBoundsInRoot()
                // The word's width follows its text: only its line may not move.
                val moved = if (part == "word") now.top != before[i].top || now.bottom != before[i].bottom else now != before[i]
                if (moved) fail("$chord: the $part moved, ${before[i]} without it, $now with it")
            }
            if (pictures) picture("$case-$name".replace(' ', '-'))
        }
    }

    /** The chord's words as the word shows them. */
    private fun wordsOf(state: TunerUiState.ChordName): String {
        val chord = state.chord ?: return ""
        val root = io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling.rootOf(chord.chord, state.notation)
        val quality = when (chord.chord.quality) {
            ChordQuality.MAJOR -> R.string.tuner_chord_major
            ChordQuality.MINOR -> R.string.tuner_chord_minor
            ChordQuality.MAJOR_SEVENTH -> R.string.tuner_chord_major_seventh
            ChordQuality.MAJOR_NINTH -> R.string.tuner_chord_major_ninth
            ChordQuality.POWER_CHORD -> R.string.tuner_chord_power_chord
            ChordQuality.DOMINANT_SEVENTH -> R.string.tuner_chord_seventh
            else -> error("no words for ${chord.chord.quality} here")
        }
        val words = context.getString(quality, root)
        val bass = chord.bass
        return if (chord.isSlash && bass != null) {
            context.getString(R.string.tuner_chord_slash, words, io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling.nameOf(bass, chord.chord, state.notation))
        } else {
            words
        }
    }

    /** TalkBack is given [words] through the word, paced as every reading is (a new one after a moment). */
    private fun said(words: String) {
        compose.waitUntil(SAID_WAIT_MS) { compose.onAllNodes(hasContentDescription(words)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shownSymbol(): String? =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(ChordSymbol), useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(ChordSymbol)

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun picture(name: String) {
        if (arguments.getString(SCREENS_ARGUMENT) != "true") return
        val dir = arguments.getString(OUTPUT_DIR_ARGUMENT) ?: return
        compose.waitForIdle()
        val bitmap = compose.onPhoneScreen().captureToImage().asAndroidBitmap()
        File(File(dir).apply { mkdirs() }, "chord-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The chords to look at: a triad, a slash chord, a long seventh, a power chord, the longest name, faded. */
    private fun chords(): List<Pair<String, TunerUiState.ChordName>> = listOf(
        "am" to named(AM),
        "slash" to named(C, bass = PitchClass.E),
        "long" to named(Chord.of(PitchClass.A_SHARP, ChordQuality.MAJOR_SEVENTH)),
        "power" to named(Chord.of(PitchClass.E, ChordQuality.POWER_CHORD)),
        "longest" to named(Chord.of(PitchClass.A_SHARP, ChordQuality.MAJOR_NINTH), bass = PitchClass.D),
        "faded" to named(G7, faded = true),
    )

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        private val AM = Chord.of(PitchClass.A, ChordQuality.MINOR)
        private val C = Chord.of(PitchClass.C, ChordQuality.MAJOR)
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

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 30L
        private const val STEP_WAIT_MS = 8_000L
        private const val SAID_WAIT_MS = 4_000L
        private const val SCREENS_ARGUMENT = "chordScreens"
        private const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp
    }
}
