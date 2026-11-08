package io.github.klaboukes.twangle.app

import android.Manifest
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeDronePlayer
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.feature.eartraining.EarTrainingTestTags
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.practice.PracticeTestTags
import io.github.klaboukes.twangle.feature.practice.R as PracticeR
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout

/**
 * The Practice tab (Phase 32) in the real app (on in every build since Phase 34): a tab of its own
 * beside the metronome's (Phase 69), it shows a card per tool (Phase 69: chords, scales, the capo helper
 * and Ear training, no metronome), a card opens its tool under the bar (full screen until 2026-11-03) and
 * its back arrow, Back and a tap on the Practice tab come back to the cards, while the tab opened again
 * from another comes back to the tool; the tool opened is saved at once and the "last time" row under the
 * cards reopens it, also after the app starts again, with none while nothing was used. The Chords
 * tool's chord picked is saved too and comes back with it, and so is the Scales tool's scale, on the
 * same root. The capo helper (Phase 33) saves its shapes and fret, and a row tapped opens its shape in
 * Chords, whose Back leads back to the capo helper. The drone on Scales (Phase 48, a silent stand-in
 * here) saves its panel's picks and stops when Scales goes or the app goes to the background. The
 * Metronome tab (a silent stand-in) is the metronome alone and stops when Practice takes its place. Ear
 * training (Phase 55, a silent speaker and microphone here) asks its first question at once and holds
 * the microphone only while it is shown.
 */
class PracticeTabTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        // Focus mode's full-screen tuner hides the bar these tests open Practice from.
        .around(FocusModeOffRule())
        .around(HighContrastPassRule())
        .around(QuietMicrophoneRule())

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val drone = FakeDronePlayer()
    private val metronome = FakeMetronomePlayer()
    private val tone = FakeTonePlayer()
    private val ears = FakeAudioRecorder.silence()
    private var tuningBefore: Tuning? = null

    @Before
    fun setUp() {
        container.testDronePlayer = drone
        container.testMetronomePlayer = metronome
        container.testTonePlayer = tone
        container.testEarTrainingRecorder = ears
        runBlocking {
            // The guitar, which every tool draws: a banjo left picked (a phone check) has no chart or neck.
            tuningBefore = container.settingsRepository.settings.first().tuning
            container.settingsRepository.setInstrument(Instruments.GUITAR)
            container.settingsRepository.setPracticeTool(null)
            container.settingsRepository.setPracticeRoot(PitchClass.C)
            container.settingsRepository.setPracticeChordQuality(ChordQuality.MAJOR)
            container.settingsRepository.setPracticeScaleType(ScaleType.MAJOR)
            container.settingsRepository.setPracticeCapoShapes(SongKey(PitchClass.C))
            container.settingsRepository.setPracticeCapoFret(0)
            container.settingsRepository.setDroneSound(DroneSound.TANPURA)
            container.settingsRepository.setDroneVoicing(DroneVoicing.FIFTH)
            container.settingsRepository.setDroneLower(false)
            resetEarTraining()
        }
    }

    /**
     * Ear training as on a fresh install: Play it back, one open string at a time, any octave; Intervals'
     * six and up; Chord type's major, minor and 7. A phone check that left three notes in a row would otherwise play three tones a question.
     */
    private suspend fun resetEarTraining() {
        container.settingsRepository.setEarExercise(Exercise.DEFAULT)
        container.settingsRepository.setEarNoteChoice(NoteChoice.OPEN_STRINGS)
        container.settingsRepository.setEarNotesInRow(1)
        container.settingsRepository.setEarNoteMatch(NoteMatch.ANY_OCTAVE)
        container.settingsRepository.setEarIntervals(Interval.DEFAULT)
        container.settingsRepository.setEarDirections(IntervalDirection.DEFAULT)
        container.settingsRepository.setEarChordTypes(ChordTypeQuestions.DEFAULT)
    }

    @After
    fun tearDown() {
        container.testDronePlayer = null
        container.testMetronomePlayer = null
        container.testTonePlayer = null
        container.testEarTrainingRecorder = null
        runBlocking {
            container.settingsRepository.setDroneSound(DroneSound.TANPURA)
            container.settingsRepository.setDroneVoicing(DroneVoicing.FIFTH)
            container.settingsRepository.setDroneLower(false)
            resetEarTraining()
            container.settingsRepository.setPracticeTool(null)
            container.settingsRepository.setPracticeRoot(PitchClass.C)
            container.settingsRepository.setPracticeChordQuality(ChordQuality.MAJOR)
            container.settingsRepository.setPracticeScaleType(ScaleType.MAJOR)
            container.settingsRepository.setPracticeCapoShapes(SongKey(PitchClass.C))
            container.settingsRepository.setPracticeCapoFret(0)
            tuningBefore?.let { container.settingsRepository.setTuning(it) }
        }
    }

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)
    private fun gone(tag: String): Boolean = device.wait(Until.gone(By.res(tag)), WAIT_MS)
    private fun click(tag: String) = checkNotNull(device.wait(Until.findObject(By.res(tag)), WAIT_MS)) { "$tag not shown" }.click()

    private fun savedTool(): PracticeTool? = runBlocking { container.settingsRepository.settings.first().practiceTool }

    /** Waits up to [WAIT_MS] for the saved tool to become [tool]. */
    private fun waitForSaved(tool: PracticeTool): PracticeTool? {
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (savedTool() != tool && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        return savedTool()
    }

    /** Waits up to [WAIT_MS] for the saved chord to become [root] and [quality]. */
    private fun waitForSavedChord(root: PitchClass, quality: ChordQuality): Pair<PitchClass, ChordQuality> {
        fun saved() = runBlocking { container.settingsRepository.settings.first().let { it.practiceRoot to it.practiceChordQuality } }
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (saved() != root to quality && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        return saved()
    }

    /** Waits up to [WAIT_MS] for the saved scale to become [type]. */
    private fun waitForSavedScale(type: ScaleType): ScaleType {
        fun saved() = runBlocking { container.settingsRepository.settings.first().practiceScaleType }
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (saved() != type && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        return saved()
    }

    private fun checked(tag: String): Boolean = device.findObject(By.res(tag)).isChecked

    /** From the cards, opens [tool] with its card: the cards go, the bar stays. */
    private fun openTool(tool: PracticeTool) {
        click(PracticeTestTags.card(tool))
        assertTrue("the cards go", gone(PracticeTestTags.CARDS))
        assertTrue("the tool's back arrow shows", shown(PracticeTestTags.BACK))
    }

    /** From a tool, back to the cards with its back arrow. */
    private fun backToCards() {
        click(PracticeTestTags.BACK)
        assertTrue("back on the cards", shown(PracticeTestTags.CARDS))
    }

    /** For each of [options]: opens the name button's panel and taps it there, which closes the panel. */
    private fun pick(vararg options: String) {
        for (option in options) {
            click(PracticeTestTags.NAME_PICKER)
            click(option)
            assertTrue("a pick closes the panel", gone(option))
        }
    }

    /** Whether each of [options] is picked in the name button's panel; the panel is closed again with Back. */
    private fun picked(vararg options: String): Boolean {
        click(PracticeTestTags.NAME_PICKER)
        val picked = options.all { device.wait(Until.findObject(By.res(it)), WAIT_MS).isChecked }
        device.pressBack()
        gone(options.first())
        return picked
    }

    private fun openPractice() {
        val tab = NavigationTestTags.tab(Destination.PRACTICE)
        assertTrue("the bar shows", shown(tab))
        assertTrue(
            "the tab is called Practice",
            device.findObject(By.res(tab)).hasObject(By.text(context.getString(R.string.nav_practice))),
        )
        click(tab)
        assertTrue("the cards show", shown(PracticeTestTags.CARDS))
    }

    @Test
    fun theCardsOpenEachToolFullScreenAndTheLastTimeRowReopensTheOneUsedLast() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertTrue("a card for every tool", PracticeTool.entries.all { shown(PracticeTestTags.card(it)) })
            assertFalse("no metronome in Practice", device.hasObject(By.res(MetronomeTestTags.RULER)) || device.hasObject(By.res(METRONOME_CARD_TAG)))
            assertFalse("no tool opens by itself", device.hasObject(By.res(PracticeTestTags.CHORDS)))
            assertFalse("nothing used yet: no last time row", device.hasObject(By.res(PracticeTestTags.LAST_TIME)))
            assertEquals("nothing saved until a tool is opened", null, savedTool())

            openTool(PracticeTool.SCALES)
            assertTrue("scales show", shown(PracticeTestTags.SCALES))
            assertTrue("the bar stays", shown(NavigationTestTags.tab(Destination.PRACTICE)))
            assertTrue("on the Practice tab", device.findObject(By.res(NavigationTestTags.tab(Destination.PRACTICE))).isSelected)
            assertEquals("the tool opened is saved", PracticeTool.SCALES, waitForSaved(PracticeTool.SCALES))
            // Another tab and back: the tool it was left on, not the cards.
            click(NavigationTestTags.tab(Destination.TUNER))
            assertTrue(gone(PracticeTestTags.SCALES))
            click(NavigationTestTags.tab(Destination.PRACTICE))
            assertTrue("the tab comes back to Scales", shown(PracticeTestTags.SCALES))
            assertFalse("not to the cards", device.hasObject(By.res(PracticeTestTags.CARDS)))
            // A tap on the open tab: the cards.
            click(NavigationTestTags.tab(Destination.PRACTICE))
            assertTrue("a tap on the Practice tab leads to the cards", shown(PracticeTestTags.CARDS))
            openTool(PracticeTool.SCALES)
            device.pressBack()
            assertTrue("Back leads to the cards", shown(PracticeTestTags.CARDS))
            assertTrue("with the bar", shown(NavigationTestTags.tab(Destination.PRACTICE)))
            assertTrue("and the last time row", shown(PracticeTestTags.LAST_TIME))

            openTool(PracticeTool.CHORDS)
            assertTrue("chords show", shown(PracticeTestTags.CHORDS))
            assertEquals("the tool opened is saved", PracticeTool.CHORDS, waitForSaved(PracticeTool.CHORDS))

            // A chord picked is saved too.
            assertTrue("a fresh install shows C major", shown(PracticeTestTags.CHART) && picked(PracticeTestTags.root(PitchClass.C)))
            pick(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR))
            assertEquals("the chord is saved", PitchClass.A to ChordQuality.MINOR, waitForSavedChord(PitchClass.A, ChordQuality.MINOR))

            // The back arrow, another tab and back: the cards, with Chords last time.
            backToCards()
            click(NavigationTestTags.tab(Destination.METRONOME))
            assertTrue(gone(PracticeTestTags.CARDS))
            click(NavigationTestTags.tab(Destination.PRACTICE))
            assertTrue("the tab opens on its cards", shown(PracticeTestTags.CARDS))
            // "A minor", or "La minor" where an earlier test left solfège picked.
            assertTrue(
                "the last time row names Chords and its chord",
                device.wait(Until.hasObject(By.res(PracticeTestTags.LAST_TIME).hasDescendant(By.desc(Pattern.compile(".*(A|La) minor.*")))), WAIT_MS),
            )
        }
        // A fresh start: the cards again, the last time row reopening the tool used last.
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertFalse("no tool opens by itself", device.hasObject(By.res(PracticeTestTags.CHORDS)))
            click(PracticeTestTags.LAST_TIME)
            assertTrue("the row opens chords", shown(PracticeTestTags.CHORDS))
            assertTrue(
                "on the chord picked last",
                picked(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR)),
            )
            assertTrue("its chart in words", device.wait(Until.hasObject(By.res(PracticeTestTags.CHART).desc(Pattern.compile("(A|La) minor, shape 1 of .*"))), WAIT_MS))

            // Scales, a tool of its own: the tool and the scale are saved too, the root shared.
            backToCards()
            openTool(PracticeTool.SCALES)
            assertTrue("the Scales tool shows", shown(PracticeTestTags.SCALES))
            assertTrue("the neck shows", shown(PracticeTestTags.NECK))
            assertFalse("no chord chart", device.hasObject(By.res(PracticeTestTags.CHART)))
            assertEquals("the pick is saved", PracticeTool.SCALES, waitForSaved(PracticeTool.SCALES))
            pick(PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC))
            assertTrue(
                "the neck in words, on the root picked for the chord",
                device.wait(Until.hasObject(By.res(PracticeTestTags.NECK).desc(Pattern.compile("(A|La) minor pentatonic, notes .*"))), WAIT_MS),
            )
            assertEquals("the scale is saved", ScaleType.MINOR_PENTATONIC, waitForSavedScale(ScaleType.MINOR_PENTATONIC))
            // Back to Chords: on the same root.
            backToCards()
            openTool(PracticeTool.CHORDS)
            assertTrue("on the chord picked last", picked(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR)))
            device.pressBack()
            assertTrue(shown(PracticeTestTags.CARDS))
            device.pressBack()
            assertTrue("Back from the cards leads to the tuner", gone(PracticeTestTags.CARDS))
        }
    }

    /** The metronome has a tab of its own (Phase 69): the metronome alone, no tool button; Practice taking its place stops it. */
    @Test
    fun theMetronomeHasATabOfItsOwnAndPracticeTakingItsPlaceStopsIt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val tab = NavigationTestTags.tab(Destination.METRONOME)
            assertTrue("the bar shows", shown(tab))
            assertTrue(
                "the tab is called Metronome",
                device.findObject(By.res(tab)).hasObject(By.text(context.getString(R.string.nav_metronome))),
            )
            click(tab)
            assertTrue("the metronome shows", shown(MetronomeTestTags.RULER))
            assertFalse("with no Practice cards", device.hasObject(By.res(PracticeTestTags.CARDS)))
            click(MetronomeTestTags.START)
            assertTrue("Start clicks", waitFor { metronome.isPlaying.value })

            click(NavigationTestTags.tab(Destination.PRACTICE))
            assertTrue("Practice shows", shown(PracticeTestTags.CARDS))
            assertTrue("the metronome has gone", gone(MetronomeTestTags.RULER))
            assertTrue("and stopped", waitFor { !metronome.isPlaying.value })
            assertEquals("the metronome is no Practice tool", null, savedTool())

            device.pressBack()
            assertTrue("Back from Practice leads to the tuner", gone(PracticeTestTags.CARDS))
            assertFalse("not to the metronome", device.hasObject(By.res(MetronomeTestTags.RULER)))
        }
    }

    @Test
    fun theCapoHelperSavesItsPicksAndOpensAShapeInChords() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            // Scales first, so Chords and Scales have state of their own to be replaced.
            openTool(PracticeTool.SCALES)
            assertTrue("scales show", shown(PracticeTestTags.NECK))
            backToCards()

            openTool(PracticeTool.CAPO)
            assertTrue("the capo helper shows", shown(PracticeTestTags.CAPO))
            assertEquals("the pick is saved", PracticeTool.CAPO, waitForSaved(PracticeTool.CAPO))
            val noCapo = device.findObject(By.res(PracticeTestTags.CAPO_SOUNDS_IN)).contentDescription
            repeat(3) { click(PracticeTestTags.CAPO_HIGHER) }
            assertTrue("the capo on 3", device.wait(Until.hasObject(By.res(PracticeTestTags.CAPO_FRET).desc(Pattern.compile(Pattern.quote(onFret(3)) + ".*"))), WAIT_MS))
            assertTrue("the shapes sound in another key", device.findObject(By.res(PracticeTestTags.CAPO_SOUNDS_IN)).contentDescription != noCapo)
            assertEquals("the fret is saved", 3, waitForSavedFret(3))

            click(PracticeTestTags.capoRow(Chord.of(PitchClass.F, ChordQuality.MAJOR)))
            assertTrue("Chords shows", shown(PracticeTestTags.CHORDS) && shown(PracticeTestTags.CHART))
            assertFalse("not scales", device.hasObject(By.res(PracticeTestTags.NECK)))
            assertTrue(
                "on the shape tapped",
                picked(PracticeTestTags.root(PitchClass.F), PracticeTestTags.type(ChordQuality.MAJOR)),
            )
            assertEquals("the chord is saved", PitchClass.F to ChordQuality.MAJOR, waitForSavedChord(PitchClass.F, ChordQuality.MAJOR))
            assertEquals("the tool is saved", PracticeTool.CHORDS, waitForSaved(PracticeTool.CHORDS))

            // Back from those Chords leads back to the capo helper, then to the cards.
            click(PracticeTestTags.BACK)
            assertTrue("back on the capo helper", shown(PracticeTestTags.CAPO))
            assertTrue(
                "the capo helper kept its fret",
                device.wait(Until.hasObject(By.res(PracticeTestTags.CAPO_FRET).desc(Pattern.compile(Pattern.quote(onFret(3)) + ".*"))), WAIT_MS),
            )
            device.pressBack()
            assertTrue("then the cards", shown(PracticeTestTags.CARDS))
        }
    }

    @Test
    fun theDroneSavesItsPicksAndStopsWhenScalesGoes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openPractice()
            openTool(PracticeTool.SCALES)
            assertTrue("the drone button shows", shown(PracticeTestTags.DRONE_PLAY))
            click(PracticeTestTags.DRONE_PLAY)
            assertTrue("▶ plays", waitFor { drone.isPlaying.value })
            assertEquals("C3, the tanpura with the fifth", 130.81f, drone.lastPlay!!.rootHz, 0.05f)

            click(PracticeTestTags.DRONE_SOUND)
            click(PracticeTestTags.droneSound(DroneSound.FLUTE_ORGAN))
            click(PracticeTestTags.droneVoicing(DroneVoicing.ROOT))
            click(PracticeTestTags.DRONE_LOWER)
            device.pressBack()
            assertTrue("Back closes the panel", gone(PracticeTestTags.DRONE_PANEL))
            assertTrue("the picks are saved", waitFor { savedDrone() == Triple(DroneSound.FLUTE_ORGAN, DroneVoicing.ROOT, true) })
            assertEquals("and heard", Triple(DroneSound.FLUTE_ORGAN, DroneVoicing.ROOT, true), drone.lastPlay!!.let { Triple(it.sound, it.voicing, it.lower) })

            // Back to the cards stops it; back on Scales it stays still.
            backToCards()
            assertTrue("leaving Scales stops the drone", waitFor { !drone.isPlaying.value })
            val plays = drone.plays.size
            openTool(PracticeTool.SCALES)
            assertTrue("back on Scales", shown(PracticeTestTags.DRONE_PLAY))
            assertEquals("still until a tap", plays, drone.plays.size)

            // The app going to the background (the screen off) stops it too.
            click(PracticeTestTags.DRONE_PLAY)
            assertTrue(waitFor { drone.isPlaying.value })
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("the background stops the drone", waitFor { !drone.isPlaying.value })
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
        // A fresh start opens the panel on the picks saved.
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            openTool(PracticeTool.SCALES)
            assertTrue(shown(PracticeTestTags.DRONE_SOUND))
            click(PracticeTestTags.DRONE_SOUND)
            assertTrue("the flute organ picked", device.wait(Until.findObject(By.res(PracticeTestTags.droneSound(DroneSound.FLUTE_ORGAN))), WAIT_MS).isChecked)
            assertTrue("the root alone picked", checked(PracticeTestTags.droneVoicing(DroneVoicing.ROOT)))
            assertTrue("Lower on", checked(PracticeTestTags.DRONE_LOWER))
            device.pressBack()
            assertTrue(gone(PracticeTestTags.DRONE_PANEL))
            assertFalse("nothing plays by itself", drone.isPlaying.value)
        }
    }

    @Test
    fun earTrainingAsksAtOnceAndHoldsTheMicrophoneOnlyWhileItIsShown() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            openTool(PracticeTool.EAR_TRAINING)
            assertTrue("Ear training shows", shown(EarTrainingTestTags.SCREEN))
            assertTrue("with its exercise's pill", shown(EarTrainingTestTags.EXERCISE))
            assertEquals("the pick is saved", PracticeTool.EAR_TRAINING, waitForSaved(PracticeTool.EAR_TRAINING))
            assertTrue("its first question sounds at once", waitFor { tone.played.size == 1 })
            assertTrue("in the guitar's voice", tone.voices.single() == Instruments.GUITAR.voice)
            assertTrue("Play it back listens", waitFor { ears.isRecording.value })

            // Another tool takes its place: the microphone is let go (5 s later, as the tuner's).
            backToCards()
            openTool(PracticeTool.CHORDS)
            assertTrue(shown(PracticeTestTags.CHORDS))
            val end = SystemClock.uptimeMillis() + MIC_RELEASE_MS
            while (ears.isRecording.value && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
            assertFalse("the microphone let go", ears.isRecording.value)

            // Back on it, a new visit asks at once.
            backToCards()
            openTool(PracticeTool.EAR_TRAINING)
            assertTrue(shown(EarTrainingTestTags.SCREEN))
            assertTrue("a new visit asks at once", waitFor { tone.played.size == 2 })
        }
    }

    private fun savedDrone(): Triple<DroneSound, DroneVoicing, Boolean> =
        runBlocking { container.settingsRepository.settings.first().let { Triple(it.droneSound, it.droneVoicing, it.droneLower) } }

    /** Waits up to [WAIT_MS] for [condition]. */
    private fun waitFor(condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        return condition()
    }

    /** "Capo on fret 3", in the app's language. */
    private fun onFret(fret: Int): String = context.getString(PracticeR.string.practice_capo_on_fret, fret)

    /** Waits up to [WAIT_MS] for the capo's saved fret to become [fret]. */
    private fun waitForSavedFret(fret: Int): Int {
        fun saved() = runBlocking { container.settingsRepository.settings.first().practiceCapoFret }
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (saved() != fret && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        return saved()
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 40L
        const val WAIT_MS = 5_000L

        /** Ear training keeps the microphone 5 s after its screen goes (a turning phone), as the tuner does. */
        const val MIC_RELEASE_MS = 9_000L

        /** The metronome's card, were there one (1.1's `PracticeTool.METRONOME`): none since 2.0. */
        const val METRONOME_CARD_TAG = "practice_card_metronome"
    }
}
