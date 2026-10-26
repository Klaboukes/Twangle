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
import io.github.klaboukes.twangle.core.audio.FakeDronePlayer
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeTool
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
 * The Practice tab (Phase 32) in the real app (on in every build since Phase 34): the bar's
 * Metronome tab is called Practice, the tool button at its top (Phase 47) picks the metronome, chords or scales from its list, the
 * pick is saved at once, and the tab opens on the tool used last, also after the app starts again;
 * the Chords tool's chord picked is saved too and comes back with it, and so is the Scales tool's
 * scale, on the same root. The capo helper (Phase 33) saves its shapes and fret, and a row
 * tapped opens its shape in Chords. The drone on Scales (Phase 48, a silent stand-in here) saves its panel's picks and
 * stops when another tab takes Practice's place or the app goes to the background.
 */
class PracticeTabTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build())
        .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
        // Focus mode's full-screen tuner hides the bar these tests open Practice from.
        .around(FocusModeOffRule())

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val drone = FakeDronePlayer()

    @Before
    fun setUp() {
        container.testDronePlayer = drone
        runBlocking {
            // The tour would cover the tuner and the bar.
            container.settingsRepository.setTourSeen(true)
            container.settingsRepository.setPracticeTool(PracticeTool.METRONOME)
            container.settingsRepository.setPracticeRoot(PitchClass.C)
            container.settingsRepository.setPracticeChordQuality(ChordQuality.MAJOR)
            container.settingsRepository.setPracticeScaleType(ScaleType.MAJOR)
            container.settingsRepository.setPracticeCapoShapes(SongKey(PitchClass.C))
            container.settingsRepository.setPracticeCapoFret(0)
            container.settingsRepository.setDroneSound(DroneSound.TANPURA)
            container.settingsRepository.setDroneVoicing(DroneVoicing.FIFTH)
            container.settingsRepository.setDroneLower(false)
        }
    }

    @After
    fun tearDown() {
        container.testDronePlayer = null
        runBlocking {
            container.settingsRepository.setDroneSound(DroneSound.TANPURA)
            container.settingsRepository.setDroneVoicing(DroneVoicing.FIFTH)
            container.settingsRepository.setDroneLower(false)
            container.settingsRepository.setPracticeTool(PracticeTool.METRONOME)
            container.settingsRepository.setPracticeRoot(PitchClass.C)
            container.settingsRepository.setPracticeChordQuality(ChordQuality.MAJOR)
            container.settingsRepository.setPracticeScaleType(ScaleType.MAJOR)
            container.settingsRepository.setPracticeCapoShapes(SongKey(PitchClass.C))
            container.settingsRepository.setPracticeCapoFret(0)
        }
    }

    private fun shown(tag: String): Boolean = device.wait(Until.hasObject(By.res(tag)), WAIT_MS)
    private fun gone(tag: String): Boolean = device.wait(Until.gone(By.res(tag)), WAIT_MS)
    private fun click(tag: String) = checkNotNull(device.wait(Until.findObject(By.res(tag)), WAIT_MS)) { "$tag not shown" }.click()

    private fun savedTool(): PracticeTool = runBlocking { container.settingsRepository.settings.first().practiceTool }

    /** Waits up to [WAIT_MS] for the saved tool to become [tool]. */
    private fun waitForSaved(tool: PracticeTool): PracticeTool {
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

    /** Opens the tool button's list and picks [tool] in it, which closes it. */
    private fun pickTool(tool: PracticeTool) {
        click(PracticeTestTags.TOOL_BUTTON)
        click(PracticeTestTags.tool(tool))
        gone(PracticeTestTags.tool(tool))
    }

    /** Whether [tool] is the one picked in the tool button's list; the list is closed again with Back. */
    private fun toolPicked(tool: PracticeTool): Boolean {
        click(PracticeTestTags.TOOL_BUTTON)
        val picked = device.wait(Until.findObject(By.res(PracticeTestTags.tool(tool))), WAIT_MS).isChecked
        device.pressBack()
        gone(PracticeTestTags.tool(tool))
        return picked
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
        val tab = NavigationTestTags.tab(Destination.METRONOME)
        assertTrue("the bar shows", shown(tab))
        assertTrue(
            "the tab is called Practice",
            device.findObject(By.res(tab)).hasObject(By.text(context.getString(R.string.nav_practice))),
        )
        click(tab)
        assertTrue("the tool button shows", shown(PracticeTestTags.TOOL_BUTTON))
    }

    @Test
    fun theToolButtonPicksTheToolAndPracticeOpensOnTheOneUsedLast() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertTrue("a fresh Practice opens on the metronome", shown(MetronomeTestTags.DIAL))
            click(PracticeTestTags.TOOL_BUTTON)
            assertTrue(
                "the list has every tool",
                listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO).all { shown(PracticeTestTags.tool(it)) },
            )
            assertTrue("the metronome picked", checked(PracticeTestTags.tool(PracticeTool.METRONOME)))
            click(PracticeTestTags.tool(PracticeTool.CHORDS))
            assertTrue("the list closes", gone(PracticeTestTags.tool(PracticeTool.METRONOME)))
            assertTrue("chords show", shown(PracticeTestTags.CHORDS))
            assertTrue("the metronome has gone", gone(MetronomeTestTags.DIAL))
            assertEquals("the pick is saved", PracticeTool.CHORDS, waitForSaved(PracticeTool.CHORDS))

            // A chord picked is saved too.
            assertTrue("a fresh install shows C major", shown(PracticeTestTags.CHART) && picked(PracticeTestTags.root(PitchClass.C)))
            pick(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR))
            assertEquals("the chord is saved", PitchClass.A to ChordQuality.MINOR, waitForSavedChord(PitchClass.A, ChordQuality.MINOR))

            // Another tab and back: still chords.
            click(NavigationTestTags.tab(Destination.SETTINGS))
            assertTrue(gone(PracticeTestTags.TOOL_BUTTON))
            click(NavigationTestTags.tab(Destination.METRONOME))
            assertTrue("back on chords", shown(PracticeTestTags.CHORDS))
        }
        // A fresh start opens Practice on the tool used last.
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertTrue("opens on chords", shown(PracticeTestTags.CHORDS))
            assertTrue(
                "on the chord picked last",
                picked(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR)),
            )
            // "A minor", or "La minor" where an earlier test left solfège picked.
            assertTrue("its chart in words", device.wait(Until.hasObject(By.res(PracticeTestTags.CHART).desc(Pattern.compile("(A|La) minor, shape 1 of .*"))), WAIT_MS))
            assertFalse("not on the metronome", device.hasObject(By.res(MetronomeTestTags.DIAL)))

            // Scales, a tool of its own: the tool and the scale are saved too, the root shared.
            pickTool(PracticeTool.SCALES)
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
            pickTool(PracticeTool.CHORDS)
            assertTrue("on the chord picked last", picked(PracticeTestTags.root(PitchClass.A), PracticeTestTags.type(ChordQuality.MINOR)))
            pickTool(PracticeTool.METRONOME)
            assertTrue("the metronome shows", shown(MetronomeTestTags.DIAL))
            assertEquals("the pick is saved", PracticeTool.METRONOME, waitForSaved(PracticeTool.METRONOME))
        }
    }

    @Test
    fun theCapoHelperSavesItsPicksAndOpensAShapeInChords() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            // Scales first, so Chords and Scales have state of their own to be replaced.
            pickTool(PracticeTool.SCALES)
            assertTrue("scales show", shown(PracticeTestTags.NECK))

            pickTool(PracticeTool.CAPO)
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
            assertTrue("the tool button on Chords", toolPicked(PracticeTool.CHORDS))
            assertEquals("the chord is saved", PitchClass.F to ChordQuality.MAJOR, waitForSavedChord(PitchClass.F, ChordQuality.MAJOR))
            assertEquals("the tool is saved", PracticeTool.CHORDS, waitForSaved(PracticeTool.CHORDS))

            pickTool(PracticeTool.CAPO)
            assertTrue(
                "the capo helper kept its fret",
                device.wait(Until.hasObject(By.res(PracticeTestTags.CAPO_FRET).desc(Pattern.compile(Pattern.quote(onFret(3)) + ".*"))), WAIT_MS),
            )
        }
    }

    @Test
    fun theDroneSavesItsPicksAndStopsWhenScalesGoes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openPractice()
            pickTool(PracticeTool.SCALES)
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

            // Another tab stops it; back on Scales it stays still.
            click(NavigationTestTags.tab(Destination.SETTINGS))
            assertTrue(gone(PracticeTestTags.TOOL_BUTTON))
            assertTrue("another tab stops the drone", waitFor { !drone.isPlaying.value })
            val plays = drone.plays.size
            click(NavigationTestTags.tab(Destination.METRONOME))
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
    }
}
