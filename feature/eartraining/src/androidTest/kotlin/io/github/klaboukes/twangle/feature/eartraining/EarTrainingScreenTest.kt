package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.designsystem.TopBar
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapeFinder
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.Tally
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertPanelUnder
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The Ear training screen (Phase 55) in Ink, from its state alone: the exercise's name pill under the top row and
 * the panel of the three it opens (upright and on its side), a pick passed on; the ▶ that sounds the question again; this visit's tally shown once
 * a question was answered, with nothing moving; Play it back's heard note or "Listening…", and
 * "Microphone is off" with its button; Intervals' question and its answer buttons of one size, right
 * and wrong shown gently with nothing moving; the "What is asked" ⋯ at the top row's end and the floating panel it
 * opens under it with Play it back's and Intervals' choices, open across a turned phone; Chord type's question and its buttons (three in a column, all twenty scrolling
 * in their own box), right and wrong, its chips, and the no-shape line; and the whole screen fitting, upright and on its side, at 2× text in Dutch
 * (every language with `--languages`).
 */
@RunWith(AndroidJUnit4::class)
class EarTrainingScreenTest {

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
    private val inRow = mutableListOf<Int>()
    private val matches = mutableListOf<NoteMatch>()
    private val intervalPicks = mutableListOf<Interval>()
    private val intervalToggles = mutableListOf<Interval>()
    private val directionToggles = mutableListOf<IntervalDirection>()
    private val typePicks = mutableListOf<ChordQuality>()
    private val typeToggles = mutableListOf<ChordQuality>()
    private var replays = 0
    private var micAsks = 0
    private var settingsOpened = 0

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
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
                        onNotesInRow = {
                            inRow += it
                            shownState.value = shownState.value.copy(notesInRow = it)
                        },
                        onNoteMatch = {
                            matches += it
                            shownState.value = shownState.value.copy(noteMatch = it)
                        },
                        onPickInterval = { intervalPicks += it },
                        onToggleInterval = {
                            intervalToggles += it
                            val now = shownState.value.intervals
                            shownState.value = shownState.value.copy(intervals = if (it in now) now - it else now + it)
                        },
                        onToggleDirection = {
                            directionToggles += it
                            val now = shownState.value.directions
                            shownState.value = shownState.value.copy(directions = if (it in now) now - it else now + it)
                        },
                        onPickChordType = { typePicks += it },
                        onToggleChordType = {
                            typeToggles += it
                            val now = shownState.value.chordTypes
                            shownState.value = shownState.value.copy(chordTypes = if (it in now) now - it else now + it)
                        },
                        onAllowMic = { micAsks++ },
                        onOpenSettings = { settingsOpened++ },
                        // The top row's place (`:app` puts the back arrow and the picker chip there, the ⋯ at its end).
                        header = { end ->
                            TopBar(
                                middle = { Box(Modifier.size(160.dp, 48.dp).testTag(HEADER)) },
                                modifier = Modifier.fillMaxWidth().testTag(TOP_ROW),
                                end = end,
                            )
                        },
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
    private fun textOf(tag: String): String =
        node(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString()

    /**
     * The exercise is a name pill under the top row, as Chords' name (Phase 77: until then a switch of three short
     * words), opening the three exercises in the floating panel under it; a pick is passed on and closes it.
     */
    @Test
    fun theExercisePillOpensTheThreeExercisesAndAPickIsPassedOn() {
        show()
        val pill = node(EarTrainingTestTags.EXERCISE)
            .assert(hasText("Play it back"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Exercise: Play it back")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assertIsDisplayed()
        // As Chords' name: 8 dp under the top row, as wide as it (up to 400 dp), 56 dp high.
        val row = node(TOP_ROW).getBoundsInRoot()
        val at = pill.getBoundsInRoot()
        assertEquals("8 dp under the top row", row.bottom.value + 8f, at.top.value, 0.5f)
        assertEquals("as wide as the top row", row.width.value, at.width.value, 1f)
        assertEquals("from its start", row.left.value, at.left.value, 1f)
        assertEquals("the one picker height", 56f, at.height.value, 0.5f)
        assertTrue("no panel yet", !exists(EarTrainingTestTags.EXERCISE_PANEL))

        pill.performClick()
        compose.waitForIdle()
        node(EarTrainingTestTags.EXERCISE_PANEL)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Exercise"))
        assertPanelUnder(pill, node(EarTrainingTestTags.EXERCISE_PANEL), node(WINDOW), "the exercises")
        val panel = node(EarTrainingTestTags.EXERCISE_PANEL).getBoundsInRoot()
        assertEquals("from the pill's start", at.left.value, panel.left.value, 0.5f)
        assertEquals("as wide as the pill", at.width.value, panel.width.value, 1f)
        val names = mapOf(Exercise.PLAY_IT_BACK to "Play it back", Exercise.INTERVALS to "Intervals", Exercise.CHORD_TYPE to "Chord type")
        for ((exercise, name) in names) {
            node(EarTrainingTestTags.exercise(exercise))
                .assert(hasText(name))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertIsDisplayed()
        }
        node(EarTrainingTestTags.exercise(Exercise.PLAY_IT_BACK)).assertIsSelected()
        node(EarTrainingTestTags.exercise(Exercise.INTERVALS)).assertIsNotSelected()
        node(EarTrainingTestTags.exercise(Exercise.INTERVALS)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(Exercise.INTERVALS), picked)
        assertTrue("a pick closes it", !exists(EarTrainingTestTags.EXERCISE_PANEL))
        node(EarTrainingTestTags.EXERCISE).assert(hasText("Intervals"))
        assertEquals("the pill stays put", at, node(EarTrainingTestTags.EXERCISE).getBoundsInRoot())
        // Until Intervals' first question is in the state, nothing shows under the pill.
        assertTrue(!exists(EarTrainingTestTags.REPLAY) && !exists(EarTrainingTestTags.HEARD))

        // The one picked again closes it too, passing nothing on; Back closes it.
        node(EarTrainingTestTags.EXERCISE).performClick()
        compose.waitForIdle()
        node(EarTrainingTestTags.exercise(Exercise.INTERVALS)).assertIsSelected().performClick()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.EXERCISE_PANEL))
        assertEquals(listOf(Exercise.INTERVALS), picked)
        node(EarTrainingTestTags.EXERCISE).performClick()
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.EXERCISE_PANEL))
    }

    /**
     * On its side the pill stands in the start half under the top row, in the middle of the height it leaves (as
     * Chords' name), and its panel opens from the top of that half, open across a turn.
     */
    @Test
    fun onItsSideThePillStandsInTheStartHalfAndItsPanelOpensOverIt() {
        shownSize.value = PHONE_LANDSCAPE
        show()
        val window = node(WINDOW).getBoundsInRoot()
        val row = node(TOP_ROW).getBoundsInRoot()
        val pill = node(EarTrainingTestTags.EXERCISE).getBoundsInRoot()
        val middle = (window.left + window.right) / 2
        assertTrue("in the start half $pill", pill.right <= middle)
        assertEquals("as wide as the top row", row.width.value, pill.width.value, 1f)
        assertTrue("under the top row $pill", pill.top >= row.bottom + 8.dp)
        val room = (row.bottom + window.bottom) / 2
        assertEquals("in the middle of the height left", room.value, ((pill.top + pill.bottom) / 2).value, 12f)
        node(EarTrainingTestTags.EXERCISE).performClick()
        compose.waitForIdle()
        for (size in listOf(PHONE_LANDSCAPE, XIAOMI, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            val panel = node(EarTrainingTestTags.EXERCISE_PANEL).assertIsDisplayed().getBoundsInRoot()
            val now = node(EarTrainingTestTags.EXERCISE).getBoundsInRoot()
            assertEquals("$size: from the pill's start", now.left.value, panel.left.value, 0.5f)
            assertEquals("$size: as wide as the pill", now.width.value, panel.width.value, 1f)
            if (size == XIAOMI) {
                assertPanelUnder(node(EarTrainingTestTags.EXERCISE), node(EarTrainingTestTags.EXERCISE_PANEL), node(WINDOW), "$size")
            } else {
                assertTrue("$size: from the top of the start half $panel", panel.top < now.top)
            }
            for (exercise in Exercise.entries) assertWhollyShown(node(EarTrainingTestTags.exercise(exercise)), "$size $exercise", scroll = false)
        }
        Espresso.pressBack()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.EXERCISE_PANEL))
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
        val tags = listOf(EarTrainingTestTags.EXERCISE, EarTrainingTestTags.CHOICES, EarTrainingTestTags.REPLAY)
        val before = tags.map { node(it).getBoundsInRoot() }
        shownState.value = shownState.value.copy(tally = Tally(right = 7, asked = 9))
        compose.waitForIdle()
        node(EarTrainingTestTags.TALLY).assert(hasText("7 of 9")).assertIsDisplayed()
        assertEquals("7 right of 9", spoken(EarTrainingTestTags.TALLY))
        assertEquals("nothing moves", before, tags.map { node(it).getBoundsInRoot() })
        assertEquals("What is asked", spoken(EarTrainingTestTags.CHOICES))
        node(EarTrainingTestTags.CHOICES).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        // The top row's ⋯ (Phase 77: until then the 52 dp options button at the exercise row's end), its outline at the row's end.
        val choices = node(EarTrainingTestTags.CHOICES).getBoundsInRoot()
        val row = node(TOP_ROW).getBoundsInRoot()
        assertEquals("the top row's 44 dp ⋯", 44f, choices.width.value, 0.5f)
        assertEquals(44f, choices.height.value, 0.5f)
        assertEquals("at the row's end", row.right.value, choices.right.value, 0.5f)
        assertEquals("in the row's middle", (row.top + row.bottom).value / 2, (choices.top + choices.bottom).value / 2, 0.5f)
    }

    @Test
    fun playItBackShowsTheNoteHeardOrWhatToDoAndNothingMoves() {
        show()
        // While nothing is heard: what to do, in the note's place (Phase 65: a dash and "Listening…" until then).
        node(EarTrainingTestTags.WAITING).assert(hasText("Play it on your instrument")).assertIsDisplayed()
        assertTrue("no note yet", !exists(EarTrainingTestTags.HEARD))
        assertEquals("nothing under it", "", textOf(EarTrainingTestTags.HEARD_LINE))
        val tags = listOf(EarTrainingTestTags.REPLAY, EarTrainingTestTags.HEARD_LINE)
        val before = tags.map { node(it).getBoundsInRoot() }
        val waiting = node(EarTrainingTestTags.WAITING).getBoundsInRoot()
        shownHeard.value = Note.of(PitchClass.F_SHARP, 3)
        compose.waitForIdle()
        assertEquals("Heard: F sharp 3", spoken(EarTrainingTestTags.HEARD))
        node(EarTrainingTestTags.HEARD).assert(hasText("F♯3")).assertIsDisplayed()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Hold the note you heard"))
        assertTrue("the note takes the hint's place", !exists(EarTrainingTestTags.WAITING))
        val note = node(EarTrainingTestTags.HEARD).getBoundsInRoot()
        assertEquals("in the middle of that place", (waiting.top + waiting.bottom).value / 2, (note.top + note.bottom).value / 2, 1f)
        assertTrue("no dots for one note", !exists(EarTrainingTestTags.DOTS))

        // A wrong note held: said quietly, nothing moves.
        shownState.value = shownState.value.copy(wrongHeld = true)
        compose.waitForIdle()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Not that one"))
        // The right one: "Right", softly, and nothing moves either.
        shownHeard.value = Note.of(PitchClass.G, 2)
        shownState.value = shownState.value.copy(wrongHeld = false, found = 1, answered = true, tally = Tally(right = 0, asked = 1))
        compose.waitForIdle()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Right")).assertIsDisplayed()
        node(EarTrainingTestTags.HEARD).assert(hasText("G2"))
        assertEquals("nothing moves", before.map { it.top }, tags.map { node(it).getBoundsInRoot().top })
        assertEquals(before[0], node(EarTrainingTestTags.REPLAY).getBoundsInRoot())
        // The note gone again: the hint is back.
        shownHeard.value = null
        compose.waitForIdle()
        node(EarTrainingTestTags.WAITING).assertIsDisplayed()
        assertEquals("nothing moves", before.map { it.top }, tags.map { node(it).getBoundsInRoot().top })
    }

    @Test
    fun aMicrophoneThatStoppedIsSaidPolitelyUnderTheHintAndNothingMoves() {
        show()
        val tags = listOf(EarTrainingTestTags.REPLAY, EarTrainingTestTags.WAITING, EarTrainingTestTags.HEARD_LINE)
        val before = tags.map { node(it).getBoundsInRoot() }
        shownState.value = shownState.value.copy(micStopped = true)
        compose.waitForIdle()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Microphone stopped")).assertIsDisplayed()
        node(EarTrainingTestTags.HEARD_LINE).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        node(EarTrainingTestTags.WAITING).assertIsDisplayed()
        assertTrue("no microphone button: it listens again by itself", !exists(EarTrainingTestTags.MIC_BUTTON))
        assertEquals("nothing moves", before.map { it.top }, tags.map { node(it).getBoundsInRoot().top })
        assertEquals(before[0], node(EarTrainingTestTags.REPLAY).getBoundsInRoot())

        // Said before "Right" too, so the player knows why nothing more is heard.
        shownState.value = shownState.value.copy(found = 1, answered = true)
        compose.waitForIdle()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Microphone stopped"))
        // Listening again: the line goes back to the usual one.
        shownState.value = shownState.value.copy(micStopped = false, found = 0, answered = false)
        compose.waitForIdle()
        assertEquals("", textOf(EarTrainingTestTags.HEARD_LINE))
    }

    @Test
    fun notesInARowShowADotForEachAndWhichNoteIsLookedFor() {
        val notes = NoteQuestion(listOf(Note.of(PitchClass.E, 3), Note.of(PitchClass.G, 3), Note.of(PitchClass.A, 3)))
        shownState.value = shownState.value.copy(notes = notes, notesInRow = 3)
        show()
        node(EarTrainingTestTags.WAITING).assert(hasText("Play them on your instrument")).assertIsDisplayed()
        node(EarTrainingTestTags.DOTS).assertIsDisplayed()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Note 1 of 3"))
        val dots = node(EarTrainingTestTags.DOTS).getBoundsInRoot()
        assertEquals("three dots, 10 dp apart by 8", 3 * 10f + 2 * 8f, dots.width.value, 0.5f)
        val line = node(EarTrainingTestTags.HEARD_LINE).getBoundsInRoot()
        shownState.value = shownState.value.copy(found = 2)
        shownHeard.value = Note.of(PitchClass.G, 3)
        compose.waitForIdle()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Note 3 of 3"))
        shownState.value = shownState.value.copy(found = 3, answered = true)
        compose.waitForIdle()
        node(EarTrainingTestTags.HEARD_LINE).assert(hasText("Right"))
        assertEquals("nothing moves", dots, node(EarTrainingTestTags.DOTS).getBoundsInRoot())
        assertEquals(line.top, node(EarTrainingTestTags.HEARD_LINE).getBoundsInRoot().top)
    }

    /**
     * The note looked for is a ring with a dot in it (◉), a found one a filled disc: they differ by shape,
     * not by colour alone (Phase 65: both were filled discs). Between the ring and its dot the background
     * shows; a found disc is filled there.
     */
    @Test
    fun theNoteLookedForIsARingNotOnlyAnotherColour() {
        val notes = NoteQuestion(listOf(Note.of(PitchClass.E, 3), Note.of(PitchClass.G, 3), Note.of(PitchClass.A, 3)))
        shownState.value = shownState.value.copy(notes = notes, notesInRow = 3, found = 1)
        show()
        val image = node(EarTrainingTestTags.DOTS).captureToImage().toPixelMap()
        // Three 10 dp dots, 8 dp apart.
        val perDp = image.width / (3 * 10f + 2 * 8f)
        fun at(xDp: Float) = image[(xDp * perDp).toInt(), (5f * perDp).toInt()]
        fun near(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.06f
        val background = at(10f + 4f)
        val lookedFor = 5f + 18f
        assertTrue("its dot is drawn", !near(background, at(lookedFor)))
        assertTrue("the background between its dot and its ring", near(background, at(lookedFor + 2.75f)))
        assertTrue("its ring is drawn", !near(background, at(lookedFor + 4.25f)))
        assertTrue("a found note is filled there", !near(background, at(5f + 2.75f)))
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
        node(EarTrainingTestTags.CHOICES).performScrollTo().performClick()
        compose.waitForIdle()
        // The floating panel under the ⋯ (a sheet from the bottom until Phase 77), their ends in line, as wide as the top
        // row; TalkBack hears it by its name.
        node(EarTrainingTestTags.CHOICES_PANEL)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "What is asked"))
        assertPanelUnder(node(EarTrainingTestTags.CHOICES), node(EarTrainingTestTags.CHOICES_PANEL), node(WINDOW), "What is asked")
        val panel = node(EarTrainingTestTags.CHOICES_PANEL).getBoundsInRoot()
        assertEquals("its end under the ⋯'s", node(EarTrainingTestTags.CHOICES).getBoundsInRoot().right.value, panel.right.value, 0.5f)
        assertEquals("as wide as the top row", node(TOP_ROW).getBoundsInRoot().width.value, panel.width.value, 1f)
        assertTrue("no title of its own", compose.onAllNodes(hasText("What is asked")).fetchSemanticsNodes().isEmpty())
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

        // How many notes in a row: one to four, one picked, each the same width.
        compose.onNode(hasText("Notes in a row")).assertExists()
        val widths = (1..4).map { count ->
            node(EarTrainingTestTags.notesInRow(count))
                .assert(hasText(count.toString()))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .getBoundsInRoot().width.value
        }
        assertTrue("steady sizes $widths", widths.all { kotlin.math.abs(it - widths[0]) < 1f })
        node(EarTrainingTestTags.notesInRow(1)).assertIsSelected()
        node(EarTrainingTestTags.notesInRow(3)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(3), inRow)
        node(EarTrainingTestTags.notesInRow(3)).assertIsSelected()

        // Any octave counts: a switch, on until turned off.
        node(EarTrainingTestTags.ANY_OCTAVE)
            .assert(hasText("Any octave counts", substring = true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOn()
            .performClick()
        compose.waitForIdle()
        assertEquals(listOf(NoteMatch.EXACT), matches)
        node(EarTrainingTestTags.ANY_OCTAVE).assertIsOff()
        // It stays open for more, and Back closes it.
        Espresso.pressBack()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.CHOICES_PANEL))
    }

    /** The panel keeps open when the phone turns, under the ⋯ in its new place; on its side, short, it scrolls. */
    @Test
    fun whatIsAskedStaysOpenUnderTheButtonAcrossATurnedPhone() {
        shownState.value = ALL_CHORDS_ASKED
        show()
        node(EarTrainingTestTags.CHOICES).performClick()
        compose.waitForIdle()
        for (size in listOf(PHONE_LANDSCAPE, NARROW_UPRIGHT, XIAOMI)) {
            shownSize.value = size
            compose.waitForIdle()
            val case = "$size:"
            node(EarTrainingTestTags.CHOICES_PANEL).assertIsDisplayed()
            assertPanelUnder(node(EarTrainingTestTags.CHOICES), node(EarTrainingTestTags.CHOICES_PANEL), node(WINDOW), case)
            val panel = node(EarTrainingTestTags.CHOICES_PANEL).getBoundsInRoot()
            val window = node(WINDOW).getBoundsInRoot()
            assertTrue("$case inside the window $panel", panel.top >= window.top && panel.bottom <= window.bottom + 0.5.dp)
            assertTrue("$case inside the window $panel", panel.left >= window.left && panel.right <= window.right + 0.5.dp)
            // The last of the twenty scrolls into view inside the panel.
            assertWhollyShown(node(EarTrainingTestTags.askChordType(ChordQuality.inPanel.last())), "$case the last chord type")
        }
        Espresso.pressBack()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.CHOICES_PANEL))
    }

    @Test
    fun intervalsShowsTheQuestionAndAButtonOfOneSizeForEachIntervalChosen() {
        shownState.value = INTERVALS_ASKED
        show()
        node(EarTrainingTestTags.REPLAY).assertIsDisplayed()
        node(EarTrainingTestTags.DIRECTION).assert(hasText("Up"))
        node(EarTrainingTestTags.INTERVAL_LINE).assert(hasText("Which interval?"))
        assertTrue("no microphone line or heard note", !exists(EarTrainingTestTags.MIC_OFF) && !exists(EarTrainingTestTags.HEARD))
        val names = mapOf(
            Interval.MINOR_THIRD to ("m3" to "minor 3rd"), Interval.MAJOR_THIRD to ("M3" to "major 3rd"),
            Interval.PERFECT_FOURTH to ("P4" to "4th"), Interval.PERFECT_FIFTH to ("P5" to "5th"),
            Interval.MAJOR_SIXTH to ("M6" to "major 6th"), Interval.OCTAVE to ("P8" to "octave"),
        )
        val bounds = Interval.DEFAULT.map { interval ->
            val (sign, name) = names.getValue(interval)
            node(EarTrainingTestTags.answer(interval))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(name)))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(hasText(sign, substring = true))
                .assertIsDisplayed()
                .getBoundsInRoot()
        }
        assertTrue("only the intervals chosen", (Interval.entries - Interval.DEFAULT).none { exists(EarTrainingTestTags.answer(it)) })
        assertTrue("one steady size ${bounds.map { it.width to it.height }}", bounds.all { abs(it.width.value - bounds[0].width.value) < 0.5f && abs(it.height.value - bounds[0].height.value) < 0.5f })
        assertTrue("at least 56 dp high", bounds[0].height.value >= 55.5f)
        assertEquals("two to a row", bounds[0].top, bounds[1].top)
        assertTrue("the next row under", bounds[2].top > bounds[0].bottom)

        node(EarTrainingTestTags.answer(Interval.PERFECT_FOURTH)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(Interval.PERFECT_FOURTH), intervalPicks)

        // One interval only: its one button keeps the same size, in the middle.
        shownState.value = INTERVALS_ASKED.copy(intervals = setOf(Interval.PERFECT_FIFTH))
        compose.waitForIdle()
        val alone = node(EarTrainingTestTags.answer(Interval.PERFECT_FIFTH)).getBoundsInRoot()
        assertEquals(bounds[0].width.value, alone.width.value, 0.5f)
        val screen = node(WINDOW).getBoundsInRoot()
        assertEquals("centred", (screen.left + screen.right).value / 2, (alone.left + alone.right).value / 2, 1f)
    }

    @Test
    fun rightAndWrongAreShownGentlyAndNothingMoves() {
        shownState.value = INTERVALS_ASKED
        show()
        val tags = listOf(EarTrainingTestTags.REPLAY, EarTrainingTestTags.DIRECTION, EarTrainingTestTags.INTERVAL_LINE) + Interval.DEFAULT.map(EarTrainingTestTags::answer)
        val before = tags.map { node(it).getBoundsInRoot() }

        // Right: "Right", and no button can answer again.
        shownState.value = INTERVALS_ASKED.copy(answered = true, picked = Interval.PERFECT_FIFTH, tally = Tally(right = 1, asked = 1))
        compose.waitForIdle()
        node(EarTrainingTestTags.INTERVAL_LINE).assert(hasText("Right"))
        assertEquals("Right", spoken(EarTrainingTestTags.INTERVAL_LINE))
        node(EarTrainingTestTags.answer(Interval.PERFECT_FOURTH)).assertIsNotEnabled().performClick()
        compose.waitForIdle()
        assertEquals("answered once", emptyList<Interval>(), intervalPicks)
        assertEquals("nothing moves", before, tags.map { node(it).getBoundsInRoot() })

        // Wrong: quietly said, and TalkBack hears the answer.
        shownState.value = INTERVALS_ASKED.copy(answered = true, picked = Interval.PERFECT_FOURTH, tally = Tally(right = 0, asked = 1))
        compose.waitForIdle()
        node(EarTrainingTestTags.INTERVAL_LINE).assert(hasText("Not that one"))
        assertEquals("Not that one. The answer: 5th", spoken(EarTrainingTestTags.INTERVAL_LINE))
        assertEquals("nothing moves", before, tags.map { node(it).getBoundsInRoot() })

        // The next question: every button answers again.
        shownState.value = INTERVALS_ASKED.copy(interval = IntervalQuestion(Interval.OCTAVE, IntervalDirection.TOGETHER, Note.of(PitchClass.E, 2)))
        compose.waitForIdle()
        node(EarTrainingTestTags.DIRECTION).assert(hasText("Together"))
        node(EarTrainingTestTags.INTERVAL_LINE).assert(hasText("Which interval?"))
        node(EarTrainingTestTags.answer(Interval.PERFECT_FOURTH)).assertIsEnabled()
        assertEquals(before.drop(1).map { it.top }, tags.drop(1).map { node(it).getBoundsInRoot().top })
    }

    @Test
    fun whatIsAskedPicksTheIntervalsAndHowTheyArePlayed() {
        shownState.value = INTERVALS_ASKED
        show()
        node(EarTrainingTestTags.CHOICES).performScrollTo().performClick()
        compose.waitForIdle()
        // The panel's heading; the exercise pill above it says "Intervals" too.
        compose.onNode(hasText("Intervals") and hasAnyAncestor(hasTestTag(EarTrainingTestTags.CHOICES_PANEL))).assertExists()
        compose.onNode(hasText("Played")).assertExists()
        val widths = Interval.entries.map { interval ->
            val chip = node(EarTrainingTestTags.askInterval(interval))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            if (interval in Interval.DEFAULT) chip.assertIsOn() else chip.assertIsOff()
            chip.getBoundsInRoot().width.value
        }
        assertTrue("steady sizes $widths", widths.all { abs(it - widths[0]) < 1f })
        assertEquals("minor 2nd", spoken(EarTrainingTestTags.askInterval(Interval.MINOR_SECOND)))
        node(EarTrainingTestTags.askInterval(Interval.MINOR_SECOND)).assert(hasText("m2")).performClick()
        node(EarTrainingTestTags.askInterval(Interval.OCTAVE)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(Interval.MINOR_SECOND, Interval.OCTAVE), intervalToggles)
        node(EarTrainingTestTags.askInterval(Interval.MINOR_SECOND)).assertIsOn()
        node(EarTrainingTestTags.askInterval(Interval.OCTAVE)).assertIsOff()

        val names = mapOf(IntervalDirection.UP to "Up", IntervalDirection.DOWN to "Down", IntervalDirection.TOGETHER to "Together")
        for ((direction, name) in names) {
            node(EarTrainingTestTags.direction(direction)).assert(hasText(name)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        }
        node(EarTrainingTestTags.direction(IntervalDirection.UP)).assertIsOn()
        node(EarTrainingTestTags.direction(IntervalDirection.TOGETHER)).assertIsOff().performClick()
        compose.waitForIdle()
        assertEquals(listOf(IntervalDirection.TOGETHER), directionToggles)
        node(EarTrainingTestTags.direction(IntervalDirection.TOGETHER)).assertIsOn()
        assertTrue("no Play it back choices here", !exists(EarTrainingTestTags.ANY_OCTAVE))
        Espresso.pressBack()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.CHOICES_PANEL))
    }

    @Test
    fun chordTypeShowsTheQuestionAndAButtonOfOneSizeForEachTypeChosen() {
        shownState.value = CHORD_ASKED
        show()
        node(EarTrainingTestTags.REPLAY).assertIsDisplayed()
        node(EarTrainingTestTags.CHORD_LINE).assert(hasText("Which kind of chord?"))
        assertTrue("no microphone line or heard note", !exists(EarTrainingTestTags.MIC_OFF) && !exists(EarTrainingTestTags.HEARD))
        val names = mapOf(
            ChordQuality.MAJOR to Triple("Major", "C", "major"),
            ChordQuality.MINOR to Triple("Minor", "Cm", "minor"),
            ChordQuality.DOMINANT_SEVENTH to Triple("7", "C7", "seventh"),
        )
        val bounds = names.map { (type, words) ->
            val (sign, symbol, spokenName) = words
            node(EarTrainingTestTags.chordAnswer(type))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(spokenName)))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(hasText(sign))
                .assert(hasText(symbol))
                .assertIsDisplayed()
                .getBoundsInRoot()
        }
        assertTrue("only the types chosen", (ChordQuality.inPanel - ChordTypeQuestions.DEFAULT).none { exists(EarTrainingTestTags.chordAnswer(it)) })
        assertTrue("one steady size ${bounds.map { it.width to it.height }}", bounds.all { abs(it.width.value - bounds[0].width.value) < 0.5f && abs(it.height.value - bounds[0].height.value) < 0.5f })
        assertTrue("at least 56 dp high", bounds[0].height.value >= 55.5f)
        assertEquals("three stand in one column", bounds[0].left, bounds[1].left)
        assertTrue("the next under", bounds[1].top > bounds[0].bottom)
        node(EarTrainingTestTags.chordAnswer(ChordQuality.MINOR)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(ChordQuality.MINOR), typePicks)

        // Four or more: two to a row, each half the row.
        shownState.value = CHORD_ASKED.copy(chordTypes = ChordTypeQuestions.DEFAULT + ChordQuality.SUS4)
        compose.waitForIdle()
        val major = node(EarTrainingTestTags.chordAnswer(ChordQuality.MAJOR)).getBoundsInRoot()
        val minor = node(EarTrainingTestTags.chordAnswer(ChordQuality.MINOR)).getBoundsInRoot()
        assertEquals("two to a row", major.top, minor.top)
        assertTrue("half the width", major.width < bounds[0].width * 0.6f)
        node(EarTrainingTestTags.chordAnswer(ChordQuality.SUS4)).assert(hasText("sus4")).assert(hasText("Csus4"))
    }

    @Test
    fun allTwentyChordTypesScrollInTheirOwnBoxAndTheQuestionStaysPut() {
        shownState.value = ALL_CHORDS_ASKED
        show()
        val replay = node(EarTrainingTestTags.REPLAY).getBoundsInRoot()
        val line = node(EarTrainingTestTags.CHORD_LINE).getBoundsInRoot()
        val box = node(EarTrainingTestTags.CHORD_ANSWERS).getBoundsInRoot()
        assertTrue("the box ends inside the window ${box.bottom}", box.bottom <= node(WINDOW).getBoundsInRoot().bottom)
        val sizes = ChordQuality.inPanel.map { type ->
            assertWhollyShown(node(EarTrainingTestTags.chordAnswer(type)), "$type")
            node(EarTrainingTestTags.chordAnswer(type)).getBoundsInRoot().let { it.width to it.height }
        }
        assertTrue("one steady size $sizes", sizes.all { abs(it.first.value - sizes[0].first.value) < 0.5f && abs(it.second.value - sizes[0].second.value) < 0.5f })
        assertEquals("the play button stays where it was", replay, node(EarTrainingTestTags.REPLAY).getBoundsInRoot())
        assertEquals("and the question", line, node(EarTrainingTestTags.CHORD_LINE).getBoundsInRoot())
        node(EarTrainingTestTags.chordAnswer(ChordQuality.AUGMENTED)).assert(hasText("aug")).assert(hasText("Caug"))
        node(EarTrainingTestTags.chordAnswer(ChordQuality.HALF_DIMINISHED)).assert(hasText("m7♭5")).assert(hasText("Cm7♭5"))
        node(EarTrainingTestTags.chordAnswer(ChordQuality.POWER_CHORD)).assert(hasText("Power")).assert(hasText("C5"))
    }

    @Test
    fun chordTypeRightAndWrongAreShownGentlyAndTheRightOneComesIntoView() {
        shownState.value = ALL_CHORDS_ASKED
        show()
        val tags = listOf(EarTrainingTestTags.REPLAY, EarTrainingTestTags.CHORD_LINE, EarTrainingTestTags.CHORD_ANSWERS)
        val before = tags.map { node(it).getBoundsInRoot() }
        // A wrong pick at the top, the answer (augmented) at the bottom of the list: it is scrolled into view.
        shownState.value = ALL_CHORDS_ASKED.copy(answered = true, pickedType = ChordQuality.MAJOR, tally = Tally(right = 0, asked = 1))
        compose.waitForIdle()
        node(EarTrainingTestTags.CHORD_LINE).assert(hasText("Not that one"))
        assertEquals("Not that one. The answer: augmented", spoken(EarTrainingTestTags.CHORD_LINE))
        assertWhollyShown(node(EarTrainingTestTags.chordAnswer(ChordQuality.AUGMENTED)), "the answer in view", scroll = false)
        node(EarTrainingTestTags.chordAnswer(ChordQuality.MINOR)).assertIsNotEnabled().performClick()
        compose.waitForIdle()
        assertEquals("answered once", emptyList<ChordQuality>(), typePicks)
        assertEquals("nothing moves", before, tags.map { node(it).getBoundsInRoot() })

        shownState.value = ALL_CHORDS_ASKED.copy(answered = true, pickedType = ChordQuality.AUGMENTED, tally = Tally(right = 1, asked = 1))
        compose.waitForIdle()
        node(EarTrainingTestTags.CHORD_LINE).assert(hasText("Right"))
        assertEquals("Right", spoken(EarTrainingTestTags.CHORD_LINE))
        assertEquals("nothing moves", before, tags.map { node(it).getBoundsInRoot() })
    }

    @Test
    fun whatIsAskedPicksTheChordTypes() {
        shownState.value = CHORD_ASKED
        show()
        // Chord type's column doesn't scroll (its answers do, in their own box).
        node(EarTrainingTestTags.CHOICES).performClick()
        compose.waitForIdle()
        // The panel's heading (the switch's word is "Chord type" too since Phase 65).
        compose.onNode(hasText("Chord type") and hasAnyAncestor(hasTestTag(EarTrainingTestTags.CHOICES_PANEL))).assertExists()
        val widths = ChordQuality.inPanel.map { type ->
            val chip = node(EarTrainingTestTags.askChordType(type)).performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            if (type in ChordTypeQuestions.DEFAULT) chip.assertIsOn() else chip.assertIsOff()
            chip.getBoundsInRoot().width.value
        }
        assertTrue("steady sizes $widths", widths.all { abs(it - widths[0]) < 1f })
        assertEquals("seventh suspended fourth", spoken(EarTrainingTestTags.askChordType(ChordQuality.SEVENTH_SUS4)))
        node(EarTrainingTestTags.askChordType(ChordQuality.SEVENTH_SUS4)).assert(hasText("7sus4")).performScrollTo().performClick()
        node(EarTrainingTestTags.askChordType(ChordQuality.MAJOR)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(ChordQuality.SEVENTH_SUS4, ChordQuality.MAJOR), typeToggles)
        node(EarTrainingTestTags.askChordType(ChordQuality.SEVENTH_SUS4)).assertIsOn()
        node(EarTrainingTestTags.askChordType(ChordQuality.MAJOR)).assertIsOff()
        assertTrue("no other exercise's choices here", !exists(EarTrainingTestTags.ANY_OCTAVE) && !exists(EarTrainingTestTags.askInterval(Interval.OCTAVE)))
        Espresso.pressBack()
        compose.waitForIdle()
        assertTrue(!exists(EarTrainingTestTags.CHOICES_PANEL))
    }

    @Test
    fun whereNoTypeHasAShapeChordTypeShowsTheChordsToolsQuietLineInPlaceOfTheQuestion() {
        shownState.value = EarTrainingState(exercise = Exercise.CHORD_TYPE, chordsUnavailable = ChordsUnavailable.NO_SHAPE)
        show()
        node(EarTrainingTestTags.CHORDS_UNAVAILABLE).assert(hasText("No shape within reach in this tuning")).assertIsDisplayed()
        assertTrue("no question, no play button", !exists(EarTrainingTestTags.REPLAY) && !exists(EarTrainingTestTags.CHORD_LINE))
        node(EarTrainingTestTags.CHOICES).assertIsDisplayed()
    }

    @Test
    fun chordTypeWithAllTwentyFitsUprightAndOnItsSide() {
        shownState.value = ALL_CHORDS_ASKED.copy(tally = Tally(right = 12, asked = 15))
        show()
        for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE, NARROW_UPRIGHT)) {
            shownSize.value = size
            compose.waitForIdle()
            val case = "$size, all chord types:"
            for (tag in listOf(EarTrainingTestTags.REPLAY, EarTrainingTestTags.CHORD_LINE) + ChordQuality.inPanel.map(EarTrainingTestTags::chordAnswer)) {
                assertWhollyShown(node(tag), "$case $tag")
            }
            compose.assertEveryTextWhollyShown(case)
        }
        shownSize.value = XIAOMI
        compose.waitForIdle()
        // The play button and the question stand where they are; the answers' box fills the room under them, inside the window.
        for (tag in listOf(EarTrainingTestTags.REPLAY, EarTrainingTestTags.CHORD_LINE)) {
            assertWhollyShown(node(tag), "Xiaomi all chord types $tag", scroll = false)
        }
        val box = node(EarTrainingTestTags.CHORD_ANSWERS).getBoundsInRoot()
        assertTrue("the answers' box inside the window $box", box.bottom <= node(WINDOW).getBoundsInRoot().bottom + 0.5.dp)
        assertTrue("and most of the room under the question $box", box.height > 400.dp)
    }

    @Test
    fun itFitsUprightAndOnItsSideInLightAndDark() {
        // The tallest question: four notes in a row, their dots and "Note 2 of 4".
        shownState.value = shownState.value.copy(tally = Tally(right = 12, asked = 15), notes = FOUR_NOTES, notesInRow = 4, found = 1)
        shownHeard.value = Note.of(PitchClass.A_SHARP, 2)
        show()
        for (dark in listOf(false, true)) for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE, NARROW_UPRIGHT)) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "$size dark $dark:"
            val window = bars().safeArea(size)
            for (tag in listOf(HEADER, EarTrainingTestTags.TALLY, EarTrainingTestTags.CHOICES, EarTrainingTestTags.REPLAY, EarTrainingTestTags.HEARD, EarTrainingTestTags.DOTS, EarTrainingTestTags.HEARD_LINE) +
                EarTrainingTestTags.EXERCISE) {
                assertWhollyShown(node(tag), "$case $tag", scroll = false)
            }
            val header = node(HEADER).getBoundsInRoot()
            assertTrue("$case the top row at the top $header", header.top - window.top in 4.dp..16.dp)
            val row = node(EarTrainingTestTags.EXERCISE).getBoundsInRoot()
            assertTrue("$case the exercise pill under it $row", row.top >= header.bottom)
            compose.assertEveryTextWhollyShown("$case")
        }
        // Intervals with all twelve: the end half or the column scrolls where it must, and everything shows.
        shownState.value = ALL_INTERVALS_ASKED.copy(tally = Tally(right = 12, asked = 15))
        for (dark in listOf(false, true)) for (size in listOf(XIAOMI, PHONE_UPRIGHT, PHONE_LANDSCAPE, NARROW_UPRIGHT)) {
            shownDark.value = dark
            shownSize.value = size
            compose.waitForIdle()
            val case = "$size dark $dark, all intervals:"
            for (tag in listOf(EarTrainingTestTags.REPLAY, EarTrainingTestTags.DIRECTION, EarTrainingTestTags.INTERVAL_LINE) + Interval.entries.map(EarTrainingTestTags::answer)) {
                assertWhollyShown(node(tag), "$case $tag")
            }
            compose.assertEveryTextWhollyShown(case)
        }
        shownSize.value = XIAOMI
        shownDark.value = false
        compose.waitForIdle()
        // On the Xiaomi six answers fit without scrolling.
        shownState.value = INTERVALS_ASKED
        compose.waitForIdle()
        for (tag in Interval.DEFAULT.map(EarTrainingTestTags::answer)) assertWhollyShown(node(tag), "Xiaomi six answers $tag", scroll = false)
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
                    assertWhollyShown(node(tag), "nl $size $mic $tag at 2x")
                }
                compose.assertEveryTextWhollyShown("nl $size $mic at 2x:")
            }
            shownMic.value = MicPermissionStatus.GRANTED
            checkExercisesAtTwiceTheTextSize("nl", size)
            for ((name, state) in playItBackStates()) {
                shownState.value = state
                compose.waitForIdle()
                assertWhollyShown(node(EarTrainingTestTags.HEARD_LINE), "nl $size $name at 2x")
                compose.assertEveryTextWhollyShown("nl $size $name at 2x:")
            }
            for ((name, state) in intervalsStates()) {
                shownState.value = state
                compose.waitForIdle()
                for (interval in Interval.entries) assertWhollyShown(node(EarTrainingTestTags.answer(interval)), "nl $size $name $interval at 2x")
                compose.assertEveryTextWhollyShown("nl $size $name at 2x:")
            }
            node(EarTrainingTestTags.CHOICES).performScrollTo().performClick()
            compose.waitForIdle()
            for (tag in Interval.entries.map(EarTrainingTestTags::askInterval) + IntervalDirection.entries.map(EarTrainingTestTags::direction)) {
                assertWhollyShown(node(tag), "nl $size $tag at 2x")
            }
            Espresso.pressBack()
            compose.waitForIdle()
            shownState.value = shownState.value.copy(
                exercise = Exercise.PLAY_IT_BACK,
                notes = NoteQuestion(listOf(Note.of(PitchClass.G, 3))),
                notesInRow = 1,
                found = 0,
                wrongHeld = false,
                answered = false,
                interval = null,
                picked = null,
            )
            // The panel, with its new rows.
            node(EarTrainingTestTags.CHOICES).performScrollTo().performClick()
            compose.waitForIdle()
            for (tag in (1..4).map(EarTrainingTestTags::notesInRow) + EarTrainingTestTags.ANY_OCTAVE) assertWhollyShown(node(tag), "nl $size $tag at 2x")
            Espresso.pressBack()
            compose.waitForIdle()
        }
    }

    @Test
    fun dutchChordTypeAtTwiceTheTextSizeIsWhollyShownUpright() = dutchChordTypeAtTwiceTheTextSize(NARROW_UPRIGHT)

    @Test
    fun dutchChordTypeAtTwiceTheTextSizeIsWhollyShownOnItsSide() = dutchChordTypeAtTwiceTheTextSize(PHONE_LANDSCAPE)

    private fun dutchChordTypeAtTwiceTheTextSize(size: DpSize) {
        shownLocale.value = "nl"
        shownFontScale.value = 2f
        shownSize.value = size
        shownState.value = shownState.value.copy(tally = Tally(right = 12, asked = 15))
        show()
        checkChordTypeAtTwiceTheTextSize("nl", size)
    }

    /**
     * Chord type at 2× text in [language] on [size]: three types in a column and all twenty, the
     * question, right and wrong, the no-shape line, and its chips in the panel, wholly shown.
     */
    private fun checkChordTypeAtTwiceTheTextSize(language: String, size: DpSize) {
        val tally = shownState.value.tally
        val states = listOf(
            "three types" to CHORD_ASKED,
            "which kind" to ALL_CHORDS_ASKED,
            "right" to ALL_CHORDS_ASKED.copy(answered = true, pickedType = ChordQuality.AUGMENTED),
            "not that one" to ALL_CHORDS_ASKED.copy(answered = true, pickedType = ChordQuality.SEVENTH_SHARP_NINE),
        )
        for ((name, state) in states) {
            shownState.value = state.copy(tally = tally)
            compose.waitForIdle()
            // The buttons' boxes once; their texts in every state.
            if (!state.answered) for (type in state.chordTypes) assertWhollyShown(node(EarTrainingTestTags.chordAnswer(type)), "$language $size $name $type at 2x")
            compose.assertEveryTextWhollyShown("$language $size chord type $name at 2x:")
        }
        shownState.value = EarTrainingState(exercise = Exercise.CHORD_TYPE, chordsUnavailable = ChordsUnavailable.NO_SHAPE, tally = tally)
        compose.waitForIdle()
        assertWhollyShown(node(EarTrainingTestTags.CHORDS_UNAVAILABLE), "$language $size no shape at 2x")
        compose.assertEveryTextWhollyShown("$language $size no shape at 2x:")
        shownState.value = ALL_CHORDS_ASKED.copy(tally = tally)
        compose.waitForIdle()
        node(EarTrainingTestTags.CHOICES).performClick()
        compose.waitForIdle()
        for (type in ChordQuality.inPanel) assertWhollyShown(node(EarTrainingTestTags.askChordType(type)), "$language $size chip $type at 2x")
        Espresso.pressBack()
        compose.waitForIdle()
    }

    /** The exercise's pill with each exercise's name, and its panel of the three, wholly shown at 2× text in [language] on [size]. */
    private fun checkExercisesAtTwiceTheTextSize(language: String, size: DpSize) {
        val before = shownState.value
        for (exercise in Exercise.entries) {
            shownState.value = EarTrainingState(exercise = exercise, tally = before.tally)
            compose.waitForIdle()
            assertWhollyShown(node(EarTrainingTestTags.EXERCISE), "$language $size $exercise pill at 2x")
            compose.assertEveryTextWhollyShown("$language $size $exercise pill at 2x:")
        }
        node(EarTrainingTestTags.EXERCISE).performScrollTo().performClick()
        compose.waitForIdle()
        for (exercise in Exercise.entries) assertWhollyShown(node(EarTrainingTestTags.exercise(exercise)), "$language $size $exercise row at 2x")
        compose.assertEveryTextWhollyShown("$language $size exercises panel at 2x:")
        Espresso.pressBack()
        compose.waitForIdle()
        shownState.value = before
        compose.waitForIdle()
    }

    /** Intervals' states with the most text, every interval asked: the question, right, and wrong. */
    private fun intervalsStates(): List<Pair<String, EarTrainingState>> {
        val base = ALL_INTERVALS_ASKED.copy(tally = shownState.value.tally)
        return listOf(
            "which interval" to base,
            "right" to base.copy(answered = true, picked = Interval.PERFECT_FIFTH),
            "not that one" to base.copy(answered = true, picked = Interval.MAJOR_SEVENTH),
        )
    }

    /** Play it back's states with the most text: a wrong note held in a row of four, the note looked for, and right. */
    private fun playItBackStates(): List<Pair<String, EarTrainingState>> {
        val base = shownState.value.copy(notes = FOUR_NOTES, notesInRow = 4)
        return listOf(
            "not that one" to base.copy(found = 1, wrongHeld = true),
            "note 2 of 4" to base.copy(found = 1),
            "right" to base.copy(found = 4, answered = true),
            "hold it" to base.copy(notes = NoteQuestion(listOf(Note.of(PitchClass.G, 3))), notesInRow = 1),
            "microphone stopped" to base.copy(found = 1, micStopped = true),
        )
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
                    compose.assertEveryTextWhollyShown("$language $size $mic at 2x:")
                }
                shownMic.value = MicPermissionStatus.GRANTED
                checkExercisesAtTwiceTheTextSize(language, size)
                val single = shownState.value
                for ((name, state) in playItBackStates()) {
                    shownState.value = state
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$language $size $name at 2x:")
                }
                for ((name, state) in intervalsStates()) {
                    shownState.value = state
                    compose.waitForIdle()
                    for (interval in Interval.entries) assertWhollyShown(node(EarTrainingTestTags.answer(interval)), "$language $size $name $interval at 2x")
                    compose.assertEveryTextWhollyShown("$language $size $name at 2x:")
                }
                node(EarTrainingTestTags.CHOICES).performScrollTo().performClick()
                compose.waitForIdle()
                for (tag in Interval.entries.map(EarTrainingTestTags::askInterval) + IntervalDirection.entries.map(EarTrainingTestTags::direction)) {
                    assertWhollyShown(node(tag), "$language $size $tag at 2x")
                }
                Espresso.pressBack()
                compose.waitForIdle()
                checkChordTypeAtTwiceTheTextSize(language, size)
                shownState.value = single
                compose.waitForIdle()
                node(EarTrainingTestTags.CHOICES).performScrollTo().performClick()
                compose.waitForIdle()
                for (choice in NoteChoice.entries) assertWhollyShown(node(EarTrainingTestTags.noteChoice(choice)), "$language $size $choice at 2x")
                for (tag in (1..4).map(EarTrainingTestTags::notesInRow) + EarTrainingTestTags.ANY_OCTAVE) assertWhollyShown(node(tag), "$language $size $tag at 2x")
                Espresso.pressBack()
                compose.waitForIdle()
            }
        }
    }

    companion object {
        private const val WINDOW = "window"
        private const val HEADER = "header"
        private const val TOP_ROW = "top_row"
        private const val TEST_TIMEOUT_SECONDS = 30L

        /** The Xiaomi 12X less the status and navigation bars, a short narrow phone less the bars, on its side less the rail, and a 320 dp phone. */
        private val XIAOMI = DpSize(393.dp, 760.dp)
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)

        /** Intervals asking a fifth up from A2, with the six intervals asked on a fresh install; and with all twelve. */
        private val INTERVALS_ASKED = EarTrainingState(
            exercise = Exercise.INTERVALS,
            interval = IntervalQuestion(Interval.PERFECT_FIFTH, IntervalDirection.UP, Note.of(PitchClass.A, 2)),
        )
        private val ALL_INTERVALS_ASKED = INTERVALS_ASKED.copy(
            intervals = Interval.entries.toSet(),
            directions = IntervalDirection.entries.toSet(),
            interval = IntervalQuestion(Interval.PERFECT_FIFTH, IntervalDirection.TOGETHER, Note.of(PitchClass.A, 2)),
        )

        /** Chord type asking A minor (x02210), with the three a fresh install asks; and with all twenty, asking an augmented chord. */
        private val CHORD_ASKED = EarTrainingState(
            exercise = Exercise.CHORD_TYPE,
            chord = Chord.of(PitchClass.A, ChordQuality.MINOR).let { ChordTypeQuestion(it, ChordShapeFinder.shapesOf(it, Tunings.STANDARD).first()) },
        )
        private val ALL_CHORDS_ASKED = CHORD_ASKED.copy(
            chordTypes = ChordQuality.inPanel.toSet(),
            chord = Chord.of(PitchClass.E, ChordQuality.AUGMENTED).let { ChordTypeQuestion(it, ChordShapeFinder.shapesOf(it, Tunings.STANDARD).first()) },
        )

        /** Four notes in a row, the most Play it back asks. */
        private val FOUR_NOTES = NoteQuestion(listOf(PitchClass.E, PitchClass.G, PitchClass.A, PitchClass.D).map { Note.of(it, 3) })
    }
}
