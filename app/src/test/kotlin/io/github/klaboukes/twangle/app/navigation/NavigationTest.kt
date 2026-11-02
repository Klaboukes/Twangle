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
    fun backLeadsSettingsToTheTabThatOpenedItAboutToSettingsAndLeavesFromTheTuner() {
        for (opener in Destination.entries) {
            assertNull(Screen.TUNER.back(opener))
            assertEquals(Screen.TUNER, Screen.METRONOME.back(opener))
            assertEquals(Screen.TUNER, Screen.PRACTICE.back(opener))
            assertEquals(Screen.TUNER, Screen.TUNING_EDITOR.back(opener))
            assertEquals(Screen.SETTINGS, Screen.ABOUT.back(opener))
        }
        assertEquals(Screen.TUNER, Screen.SETTINGS.back(Destination.TUNER))
        assertEquals(Screen.METRONOME, Screen.SETTINGS.back(Destination.METRONOME))
        assertEquals(Screen.PRACTICE, Screen.SETTINGS.back(Destination.PRACTICE))
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

    /** Each Practice tool opens full screen from its card (Phase 69): no bar, and Back leads to the cards. */
    @Test
    fun eachPracticeToolIsAFullScreenOfItsOwnAndBackLeadsToTheCards() {
        assertEquals(PracticeTool.entries.size, PracticeTool.entries.map { it.screen }.toSet().size)
        for (tool in PracticeTool.entries) {
            assertEquals(tool, tool.screen.practiceTool)
            assertNull("$tool has no bar", tool.screen.destination)
            for (opener in Destination.entries) assertEquals(Screen.PRACTICE, tool.screen.back(opener))
        }
        assertNull(Screen.PRACTICE.practiceTool)
        assertNull(Screen.TUNER.practiceTool)
    }

    @Test
    fun chordsOpenedFromTheCapoHelperGoBackToIt() {
        assertEquals(Screen.PRACTICE_CAPO, Screen.PRACTICE_CHORDS.back(Destination.PRACTICE, chordsFromCapo = true))
        assertEquals(Screen.PRACTICE, Screen.PRACTICE_SCALES.back(Destination.PRACTICE, chordsFromCapo = true))
        assertEquals(Screen.PRACTICE, Screen.PRACTICE_CAPO.back(Destination.PRACTICE, chordsFromCapo = true))
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
