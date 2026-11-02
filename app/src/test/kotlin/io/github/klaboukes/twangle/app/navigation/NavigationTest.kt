package io.github.klaboukes.twangle.app.navigation

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

    @Test
    fun aboutBelongsToSettingsAndTheEditorHasNoBar() {
        assertEquals(Destination.SETTINGS, Screen.ABOUT.destination)
        assertNull(Screen.TUNING_EDITOR.destination)
    }

    @Test
    fun backLeadsToTheTunerFromAboutViaSettingsAndLeavesFromTheTuner() {
        assertNull(Screen.TUNER.back)
        assertEquals(Screen.TUNER, Screen.METRONOME.back)
        assertEquals(Screen.TUNER, Screen.PRACTICE.back)
        assertEquals(Screen.TUNER, Screen.SETTINGS.back)
        assertEquals(Screen.TUNER, Screen.TUNING_EDITOR.back)
        assertEquals(Screen.SETTINGS, Screen.ABOUT.back)
    }

    @Test
    fun theTabsReadTuneMetronomePracticeAndWithoutPracticeTheMetronomeKeepsItsOwn() {
        assertEquals(
            listOf(Destination.TUNER, Destination.METRONOME, Destination.PRACTICE, Destination.SETTINGS),
            destinationsFor(practice = true),
        )
        assertEquals(listOf(Destination.TUNER, Destination.METRONOME, Destination.SETTINGS), destinationsFor(practice = false))
    }

    @Test
    fun theMetronomeAndPracticeAreTabsOfTheirOwn() {
        assertEquals(Destination.METRONOME, Screen.METRONOME.destination)
        assertEquals(Destination.PRACTICE, Screen.PRACTICE.destination)
    }

    @Test
    fun theRailIsForWindowsWiderThanTall() {
        assertTrue(useRail(873f, 393f))
        assertTrue(useRail(1280f, 760f))
        assertFalse(useRail(393f, 873f))
        assertFalse(useRail(800f, 1240f))
    }
}
