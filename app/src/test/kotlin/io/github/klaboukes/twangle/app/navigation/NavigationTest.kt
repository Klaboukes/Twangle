package io.github.klaboukes.twangle.app.navigation

import io.github.klaboukes.twangle.core.settings.PracticeTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

    @Test
    fun eachTabOpensItsScreenAndThatScreenShowsItAsOpen() {
        for (destination in Destination.entries) assertEquals(destination, destination.screen.destination)
    }

    /** Settings and About (behind the gear, Phase 69) and the tuning editor are no tab: no bar shows on them. */
    @Test
    fun settingsAboutAndTheEditorAreNoTabs() {
        assertNull(Screen.SETTINGS.destination)
        assertNull(Screen.ABOUT.destination)
        assertNull(Screen.TUNING_EDITOR.destination)
    }

    @Test
    fun backLeadsSettingsToTheScreenThatOpenedItAboutToSettingsAndLeavesFromTheTuner() {
        val openers = Screen.entries.filter { it.destination != null }
        for (opener in openers) {
            assertNull(Screen.TUNER.back(opener))
            assertEquals(Screen.TUNER, Screen.METRONOME.back(opener))
            assertEquals(Screen.TUNER, Screen.PRACTICE.back(opener))
            assertEquals(Screen.TUNER, Screen.TUNING_EDITOR.back(opener))
            assertEquals(Screen.SETTINGS, Screen.ABOUT.back(opener))
            // A Practice tool too: on its side the rail's gear opens Settings from it.
            assertEquals(opener, Screen.SETTINGS.back(opener))
        }
    }

    @Test
    fun theTabsReadTuneMetronomePracticeAndWithoutPracticeTheMetronomeKeepsItsOwn() {
        assertEquals(listOf(Destination.TUNER, Destination.METRONOME, Destination.PRACTICE), destinationsFor(practice = true))
        assertEquals(listOf(Destination.TUNER, Destination.METRONOME), destinationsFor(practice = false))
    }

    @Test
    fun theMetronomeAndPracticeAreTabsOfTheirOwn() {
        assertEquals(Destination.METRONOME, Screen.METRONOME.destination)
        assertEquals(Destination.PRACTICE, Screen.PRACTICE.destination)
    }

    /**
     * Each Practice tool opens from its card (Phase 69) under the bar, the Practice tab open (owner,
     * 2026-11-03; full screen until then), and Back leads to the cards.
     */
    @Test
    fun eachPracticeToolIsOnThePracticeTabAndBackLeadsToTheCards() {
        assertEquals(PracticeTool.entries.size, PracticeTool.entries.map { it.screen }.toSet().size)
        for (tool in PracticeTool.entries) {
            assertEquals(tool, tool.screen.practiceTool)
            assertEquals("$tool is on the Practice tab", Destination.PRACTICE, tool.screen.destination)
            assertEquals(Screen.PRACTICE, tool.screen.back(Screen.PRACTICE))
        }
        assertNull(Screen.PRACTICE.practiceTool)
        assertNull(Screen.TUNER.practiceTool)
    }

    @Test
    fun chordsOpenedFromTheCapoHelperGoBackToIt() {
        assertEquals(Screen.PRACTICE_CAPO, Screen.PRACTICE_CHORDS.back(Screen.PRACTICE, chordsFromCapo = true))
        assertEquals(Screen.PRACTICE, Screen.PRACTICE_SCALES.back(Screen.PRACTICE, chordsFromCapo = true))
        assertEquals(Screen.PRACTICE, Screen.PRACTICE_CAPO.back(Screen.PRACTICE, chordsFromCapo = true))
    }

    /** The Practice tab, opened from another, comes back to where it was left: the cards or a tool. */
    @Test
    fun thePracticeTabComesBackToTheToolItWasLeftOn() {
        for (left in Screen.entries.filter { it.destination == Destination.PRACTICE }) {
            assertEquals(left, Destination.PRACTICE.screenFrom(practiceLeftOn = left))
            assertEquals(Screen.TUNER, Destination.TUNER.screenFrom(practiceLeftOn = left))
            assertEquals(Screen.METRONOME, Destination.METRONOME.screenFrom(practiceLeftOn = left))
        }
        // Never a screen of no tab.
        assertEquals(Screen.PRACTICE, Destination.PRACTICE.screenFrom(practiceLeftOn = Screen.SETTINGS))
    }

    /** A tap on the open Practice tab in a tool leads to the cards; elsewhere it stays. */
    @Test
    fun aTapOnTheOpenPracticeTabInAToolLeadsToTheCards() {
        for (tool in PracticeTool.entries) assertEquals(Screen.PRACTICE, tool.screen.reselected())
        for (screen in listOf(Screen.TUNER, Screen.METRONOME, Screen.PRACTICE)) assertNull(screen.reselected())
    }

    @Test
    fun aToolWhoseSwitchIsOffShowsTheCardsAndNoToolsShowTheMetronome() {
        val all = PracticeTool.entries
        for (screen in Screen.entries) assertEquals(screen, shownScreen(screen, all))
        val noCapo = all - PracticeTool.CAPO
        assertEquals(Screen.PRACTICE, shownScreen(Screen.PRACTICE_CAPO, noCapo))
        assertEquals(Screen.PRACTICE_CHORDS, shownScreen(Screen.PRACTICE_CHORDS, noCapo))
        assertEquals(Screen.METRONOME, shownScreen(Screen.PRACTICE, emptyList()))
        assertEquals(Screen.METRONOME, shownScreen(Screen.PRACTICE_SCALES, emptyList()))
        assertEquals(Screen.SETTINGS, shownScreen(Screen.SETTINGS, emptyList()))
    }

    @Test
    fun theRailIsForWindowsWiderThanTall() {
        assertTrue(useRail(873f, 393f))
        assertTrue(useRail(1280f, 760f))
        assertFalse(useRail(393f, 873f))
        assertFalse(useRail(800f, 1240f))
    }
}
