package io.github.klaboukes.twangle.app

import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeChart
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
 * Metronome tab is called Practice, the tool switch at its top picks the metronome or chords, the
 * pick is saved at once, and the tab opens on the tool used last, also after the app starts again;
 * the Chords tool's chord picked is saved too and comes back with it, and so are its Chords · Scales
 * choice and the scale picked. The capo helper (Phase 33) saves its shapes and fret, and a row
 * tapped opens its shape in Chords.
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

    @Before
    fun setUp() {
        runBlocking {
            // The tour would cover the tuner and the bar.
            container.settingsRepository.setTourSeen(true)
            container.settingsRepository.setPracticeTool(PracticeTool.METRONOME)
            container.settingsRepository.setPracticeRoot(PitchClass.C)
            container.settingsRepository.setPracticeChordQuality(ChordQuality.MAJOR)
            container.settingsRepository.setPracticeChart(PracticeChart.CHORDS)
            container.settingsRepository.setPracticeScaleType(ScaleType.MAJOR)
            container.settingsRepository.setPracticeCapoShapes(SongKey(PitchClass.C))
            container.settingsRepository.setPracticeCapoFret(0)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            container.settingsRepository.setPracticeTool(PracticeTool.METRONOME)
            container.settingsRepository.setPracticeRoot(PitchClass.C)
            container.settingsRepository.setPracticeChordQuality(ChordQuality.MAJOR)
            container.settingsRepository.setPracticeChart(PracticeChart.CHORDS)
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

    /** Waits up to [WAIT_MS] for the saved choice and scale to become [chart] and [type]. */
    private fun waitForSavedScale(chart: PracticeChart, type: ScaleType): Pair<PracticeChart, ScaleType> {
        fun saved() = runBlocking { container.settingsRepository.settings.first().let { it.practiceChart to it.practiceScaleType } }
        val end = SystemClock.uptimeMillis() + WAIT_MS
        while (saved() != chart to type && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        return saved()
    }

    private fun checked(tag: String): Boolean = device.findObject(By.res(tag)).isChecked

    /** Opens [picker]'s grid and taps [option] in it, then waits for the grid to close. */
    private fun pick(picker: String, option: String) {
        click(picker)
        click(option)
        gone(option)
    }

    /** Whether [option] is the one picked in [picker]'s grid; the grid is closed again by tapping it. */
    private fun picked(picker: String, option: String): Boolean {
        click(picker)
        val picked = device.wait(Until.findObject(By.res(option)), WAIT_MS).isChecked
        click(option)
        gone(option)
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
        assertTrue("the tool switch shows", shown(PracticeTestTags.SWITCH))
    }

    @Test
    fun theSwitchPicksTheToolAndPracticeOpensOnTheOneUsedLast() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertTrue("a fresh Practice opens on the metronome", shown(MetronomeTestTags.DIAL))
            assertTrue("both tools", shown(PracticeTestTags.tool(PracticeTool.METRONOME)) && shown(PracticeTestTags.tool(PracticeTool.CHORDS)))

            click(PracticeTestTags.tool(PracticeTool.CHORDS))
            assertTrue("chords show", shown(PracticeTestTags.CHORDS))
            assertTrue("the metronome has gone", gone(MetronomeTestTags.DIAL))
            assertEquals("the pick is saved", PracticeTool.CHORDS, waitForSaved(PracticeTool.CHORDS))

            // A chord picked is saved too.
            assertTrue("a fresh install shows C major", shown(PracticeTestTags.CHART) && picked(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.C)))
            pick(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.A))
            pick(PracticeTestTags.TYPE_PICKER, PracticeTestTags.type(ChordQuality.MINOR))
            assertEquals("the chord is saved", PitchClass.A to ChordQuality.MINOR, waitForSavedChord(PitchClass.A, ChordQuality.MINOR))

            // Another tab and back: still chords.
            click(NavigationTestTags.tab(Destination.SETTINGS))
            assertTrue(gone(PracticeTestTags.SWITCH))
            click(NavigationTestTags.tab(Destination.METRONOME))
            assertTrue("back on chords", shown(PracticeTestTags.CHORDS))
        }
        // A fresh start opens Practice on the tool used last.
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            assertTrue("opens on chords", shown(PracticeTestTags.CHORDS))
            assertTrue(
                "on the chord picked last",
                picked(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.A)) && picked(PracticeTestTags.TYPE_PICKER, PracticeTestTags.type(ChordQuality.MINOR)),
            )
            // "A minor", or "La minor" where an earlier test left solfège picked.
            assertTrue("its chart in words", device.wait(Until.hasObject(By.res(PracticeTestTags.CHART).desc(Pattern.compile("(A|La) minor, shape 1 of .*"))), WAIT_MS))
            assertFalse("not on the metronome", device.hasObject(By.res(MetronomeTestTags.DIAL)))

            // Scales: the choice and the scale are saved too, the root shared.
            click(PracticeTestTags.chart(PracticeChart.SCALES))
            assertTrue("the neck shows", shown(PracticeTestTags.NECK))
            pick(PracticeTestTags.TYPE_PICKER, PracticeTestTags.scale(ScaleType.MINOR_PENTATONIC))
            assertTrue(
                "the neck in words, on the root picked for the chord",
                device.wait(Until.hasObject(By.res(PracticeTestTags.NECK).desc(Pattern.compile("(A|La) minor pentatonic, notes .*"))), WAIT_MS),
            )
            assertEquals("scales saved", PracticeChart.SCALES to ScaleType.MINOR_PENTATONIC, waitForSavedScale(PracticeChart.SCALES, ScaleType.MINOR_PENTATONIC))
            click(PracticeTestTags.tool(PracticeTool.METRONOME))
            assertTrue("the metronome shows", shown(MetronomeTestTags.DIAL))
            assertEquals("the pick is saved", PracticeTool.METRONOME, waitForSaved(PracticeTool.METRONOME))
        }
    }

    @Test
    fun theCapoHelperSavesItsPicksAndOpensAShapeInChords() {
        runBlocking { container.settingsRepository.setPracticeChart(PracticeChart.SCALES) }
        ActivityScenario.launch(MainActivity::class.java).use {
            openPractice()
            // Chords first, on scales, so it has state of its own to be replaced.
            click(PracticeTestTags.tool(PracticeTool.CHORDS))
            assertTrue("scales show", shown(PracticeTestTags.NECK))

            click(PracticeTestTags.tool(PracticeTool.CAPO))
            assertTrue("the capo helper shows", shown(PracticeTestTags.CAPO))
            assertEquals("the pick is saved", PracticeTool.CAPO, waitForSaved(PracticeTool.CAPO))
            val noCapo = device.findObject(By.res(PracticeTestTags.CAPO_SOUNDS_IN)).contentDescription
            repeat(3) { click(PracticeTestTags.CAPO_HIGHER) }
            assertTrue("the capo on 3", device.wait(Until.hasObject(By.res(PracticeTestTags.CAPO_FRET).desc(Pattern.compile(Pattern.quote(onFret(3)) + ".*"))), WAIT_MS))
            assertTrue("the shapes sound in another key", device.findObject(By.res(PracticeTestTags.CAPO_SOUNDS_IN)).contentDescription != noCapo)
            assertEquals("the fret is saved", 3, waitForSavedFret(3))

            click(PracticeTestTags.capoRow(Chord.of(PitchClass.F, ChordQuality.MAJOR)))
            assertTrue("Chords shows", shown(PracticeTestTags.CHART))
            assertTrue(
                "on the shape tapped",
                picked(PracticeTestTags.ROOT_PICKER, PracticeTestTags.root(PitchClass.F)) && picked(PracticeTestTags.TYPE_PICKER, PracticeTestTags.type(ChordQuality.MAJOR)),
            )
            assertTrue("the switch on Chords", checked(PracticeTestTags.tool(PracticeTool.CHORDS)))
            assertEquals("the chord is saved", PitchClass.F to ChordQuality.MAJOR, waitForSavedChord(PitchClass.F, ChordQuality.MAJOR))
            assertEquals("chords, not scales", PracticeChart.CHORDS to ScaleType.MAJOR, waitForSavedScale(PracticeChart.CHORDS, ScaleType.MAJOR))
            assertEquals("the tool is saved", PracticeTool.CHORDS, waitForSaved(PracticeTool.CHORDS))

            click(PracticeTestTags.tool(PracticeTool.CAPO))
            assertTrue(
                "the capo helper kept its fret",
                device.wait(Until.hasObject(By.res(PracticeTestTags.CAPO_FRET).desc(Pattern.compile(Pattern.quote(onFret(3)) + ".*"))), WAIT_MS),
            )
        }
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
