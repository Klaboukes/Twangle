package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.Tally
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The Ear training screen (Phase 55) in each style, from its state alone: the three exercises as icons
 * TalkBack names, a pick passed on; the ▶ that sounds the question again; this visit's tally shown once
 * a question was answered, with nothing moving; Play it back's heard note or "Listening…", and
 * "Microphone is off" with its button; the "What is asked" panel and its choice of notes; and the
 * whole screen fitting, upright and on its side, at 2× text in Dutch (every language with `--languages`).
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class EarTrainingScreenTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownSize = mutableStateOf(XIAOMI)
    private fun bars() = SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, Navigation.BUTTONS)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf("en")
    private val shownState = mutableStateOf(EarTrainingState(notes = NoteQuestion(listOf(Note.of(PitchClass.G, 3)))))
    private val shownHeard = mutableStateOf<Note?>(null)
    private val shownMic = mutableStateOf(MicPermissionStatus.GRANTED)
    private val picked = mutableListOf<Exercise>()
    private val choices = mutableListOf<NoteChoice>()
    private var replays = 0
    private var micAsks = 0
    private var settingsOpened = 0

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    EarTrainingContent(
                        state = shownState.value,
                        heard = shownHeard.value,
                        notation = Notation.SHARPS,
                        mic = shownMic.value,
                        onPickExercise = {
                            picked += it
                            shownState.value = shownState.value.copy(exercise = it)
                        },
                        onReplay = { replays++ },
                        onNoteChoice = {
                            choices += it
                            shownState.value = shownState.value.copy(noteChoice = it)
                        },
                        onAllowMic = { micAsks++ },
                        onOpenSettings = { settingsOpened++ },
                        // The tool button's place (`:app` puts the tool button and the tuning line there).
                        header = { Box(Modifier.size(160.dp, 48.dp).testTag(HEADER)) },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun spoken(tag: String): String =
        node(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    @Test
    fun talkBackNamesEachExerciseAndAPickIsPassedOn() {
        show()
        val names = mapOf(Exercise.PLAY_IT_BACK to "Play it back", Exercise.INTERVALS to "Intervals", Exercise.CHORD_TYPE to "Chord type")
        for (exercise in Exercise.entries) {
            node(EarTrainingTestTags.exercise(exercise))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(names.getValue(exercise))))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertIsDisplayed()
        }
        node(EarTrainingTestTags.exercise(Exercise.PLAY_IT_BACK)).assertIsSelected()
        val before = Exercise.entries.map { node(EarTrainingTestTags.exercise(it)).getBoundsInRoot() }
        node(EarTrainingTestTags.exercise(Exercise.INTERVALS)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(Exercise.INTERVALS), picked)
        node(EarTrainingTestTags.exercise(Exercise.INTERVALS)).assertIsSelected()
        node(EarTrainingTestTags.exercise(Exercise.PLAY_IT_BACK)).assertIsNotSelected()
        assertEquals("the icons keep their places", before, Exercise.entries.map { node(EarTrainingTestTags.exercise(it)).getBoundsInRoot() })
        assertEquals("one steady width each", 56f, before[0].width.value, 0.5f)
        // Intervals asks nothing yet: no ▶ and no Play it back prompt.
        assertTrue(!exists(EarTrainingTestTags.REPLAY) && !exists(EarTrainingTestTags.HEARD))
    }

    @Test
    fun theRoundButtonSoundsTheQuestionAgain() {
        show()
        node(EarTrainingTestTags.REPLAY)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hear it again")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        compose.waitForIdle()
        assertEquals(1, replays)
        val bounds = node(EarTrainingTestTags.REPLAY).getBoundsInRoot()
        assertEquals("72 dp round", 72f, bounds.width.value, 0.5f)
        assertEquals(72f, bounds.height.value, 0.5f)
    }

    @Test
    fun theTallyShowsOnceAQuestionWasAnsweredAndNothingMoves() {
        show()
        assertTrue("nothing to count yet", !exists(EarTrainingTestTags.TALLY))
        val tags = Exercise.entries.map(EarTrainingTestTags::exercise) + listOf(EarTrainingTestTags.CHOICES, EarTrainingTestTags.REPLAY)
        val before = tags.map { node(it).getBoundsInRoot() }
        shownState.value = shownState.value.copy(tally = Tally(right = 7, asked = 9))
        compose.waitForIdle()
        node(EarTrainingTestTags.TALLY).assert(hasText("7 of 9")).assertIsDisplayed()
        assertEquals("7 right of 9", spoken(EarTrainingTestTags.TALLY))
        assertEquals("nothing moves", before, tags.map { node(it).getBoundsInRoot() })
        assertEquals("What is asked", spoken(EarTrainingTestTags.CHOICES))
    }

    @Test
    fun playItBackShowsTheNoteHeardOrListeningAndNothingMoves() {
        show()
        node(EarTrainingTestTags.HEARD).assert(hasText("Listening…"))
        compose.onNode(hasText("Play it on your instrument")).assertIsDisplayed()
        val replay = node(EarTrainingTestTags.REPLAY).getBoundsInRoot()
        val prompt = compose.onNode(hasText("Play it on your instrument")).getBoundsInRoot()
        shownHeard.value = Note.of(PitchClass.F_SHARP, 3)
        compose.waitForIdle()
        assertEquals("Heard: F sharp 3", spoken(EarTrainingTestTags.HEARD))
        node(EarTrainingTestTags.HEARD).assert(hasText("F♯3")).assertIsDisplayed()
        assertEquals(replay, node(EarTrainingTestTags.REPLAY).getBoundsInRoot())
        assertEquals(prompt, compose.onNode(hasText("Play it on your instrument")).getBoundsInRoot())
    }

    @Test
    fun withoutTheMicrophoneItSaysSoAndAsksAgainOrOpensSettings() {
        shownMic.value = MicPermissionStatus.DENIED
        show()
        node(EarTrainingTestTags.MIC_OFF).assert(hasText("Microphone is off")).assertIsDisplayed()
        assertTrue("no note shown", !exists(EarTrainingTestTags.HEARD))
        node(EarTrainingTestTags.MIC_BUTTON).assert(hasText("Allow microphone")).performClick()
        compose.waitForIdle()
        assertEquals(1, micAsks)
        shownMic.value = MicPermissionStatus.PERMANENTLY_DENIED
        compose.waitForIdle()
        node(EarTrainingTestTags.MIC_BUTTON).assert(hasText("Open settings")).performClick()
        compose.waitForIdle()
        assertEquals(1, settingsOpened)
        assertEquals(1, micAsks)
    }

    @Test
    fun theChoicesButtonOpensWhatIsAskedAndAPickIsPassedOn() {
        show()
        node(EarTrainingTestTags.CHOICES).performClick()
        compose.waitForIdle()
        node(EarTrainingTestTags.CHOICES_PANEL).assertExists()
        compose.onNode(hasText("What is asked")).assertIsDisplayed()
        val names = mapOf(
            NoteChoice.OPEN_STRINGS to "Open strings",
            NoteChoice.NATURALS_TO_5TH to "Natural notes to the 5th fret",
            NoteChoice.ALL_TO_12TH to "Every note to the 12th fret",
        )
        for ((choice, name) in names) {
            node(EarTrainingTestTags.noteChoice(choice))
                .assert(hasText(name))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        node(EarTrainingTestTags.noteChoice(NoteChoice.OPEN_STRINGS)).assertIsSelected()
        node(EarTrainingTestTags.noteChoice(NoteChoice.NATURALS_TO_5TH)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(NoteChoice.NATURALS_TO_5TH), choices)
        node(EarTrainingTestTags.noteChoice(NoteChoice.NATURALS_TO_5TH)).assertIsSelected()
        node(EarTrainingTestTags.noteChoice(NoteChoice.OPEN_STRINGS)).assertIsNotSelected()
        // It stays open for more, and Back closes it.
        Espresso.pressBack()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.CHOICES_PANEL))
    }

    @Test
    fun itFitsUprightAndOnItsSideInLightAndDark() {
        shownState.value = shownState.value.copy(tally = Tally(right = 12, asked = 15))
        shownHeard.value = Note.of(PitchClass.A_SHARP, 2)
        show()
        for (dark in listOf(false, true)) for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE, NARROW_UPRIGHT)) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "$style $size dark $dark:"
            val window = bars().safeArea(size)
            for (tag in listOf(HEADER, EarTrainingTestTags.TALLY, EarTrainingTestTags.CHOICES, EarTrainingTestTags.REPLAY, EarTrainingTestTags.HEARD) +
                Exercise.entries.map(EarTrainingTestTags::exercise)) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
            }
            val header = node(HEADER).getBoundsInRoot()
            assertTrue("$case the top row at the top $header", header.top - window.top in 4.dp..16.dp)
            val row = node(EarTrainingTestTags.exercise(Exercise.PLAY_IT_BACK)).getBoundsInRoot()
            assertTrue("$case the exercises under it $row", row.top >= header.bottom)
            compose.assertEveryTextWhollyShown("$case")
        }
    }

    @Test
    fun dutchAtTwiceTheTextSizeIsWhollyShown() {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        shownState.value = shownState.value.copy(tally = Tally(right = 12, asked = 15))
        show()
        for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            for (mic in listOf(MicPermissionStatus.GRANTED, MicPermissionStatus.PERMANENTLY_DENIED)) {
                shownMic.value = mic
                compose.waitForIdle()
                for (tag in listOf(EarTrainingTestTags.TALLY, EarTrainingTestTags.CHOICES, EarTrainingTestTags.REPLAY)) {
                    assertWhollyShown(node(tag), "$style nl $size $mic $tag at 2x")
                }
                compose.assertEveryTextWhollyShown("$style nl $size $mic at 2x:")
            }
        }
    }

    /** Every language at 2× text, upright on a 320 dp phone and on its side, the microphone on and off, and the panel. Only with `--languages`. */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownState.value = shownState.value.copy(tally = Tally(right = 12, asked = 15))
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(NARROW_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                for (mic in listOf(MicPermissionStatus.GRANTED, MicPermissionStatus.DENIED, MicPermissionStatus.PERMANENTLY_DENIED)) {
                    shownMic.value = mic
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$style $language $size $mic at 2x:")
                }
                shownMic.value = MicPermissionStatus.GRANTED
                node(EarTrainingTestTags.CHOICES).performClick()
                compose.waitForIdle()
                for (choice in NoteChoice.entries) assertWhollyShown(node(EarTrainingTestTags.noteChoice(choice)), "$style $language $size $choice at 2x")
                Espresso.pressBack()
                compose.waitForIdle()
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val HEADER = "header"
        private const val TEST_TIMEOUT_SECONDS = 30L

        /** The Xiaomi 12X less the status and navigation bars, a short narrow phone less the bars, on its side less the rail, and a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)
    }
}
