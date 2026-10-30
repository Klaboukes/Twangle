package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule.Sound.PLAYED
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule.Sound.QUIET
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule.Sound.SOUNDING
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenAwakeTest {

    @Test
    fun `a shown note is sounding, a tool's panel is played, listening is quiet`() {
        val tuning = Tunings.STANDARD
        val detected = TunerUiState.Detected(
            mode = TunerMode.AUTO,
            tuning = tuning,
            noteName = "A",
            octave = 2,
            frequencyHz = 110f,
            cents = 0f,
            targetString = 1,
            inTune = true,
        )
        assertEquals(SOUNDING, soundOf(detected))
        assertEquals(PLAYED, soundOf(TunerUiState.ChordName(tuning)))
        assertEquals(PLAYED, soundOf(TunerUiState.StrumCheck(tuning)))
        assertEquals(QUIET, soundOf(TunerUiState.Listening(TunerMode.AUTO, tuning)))
        assertEquals(QUIET, soundOf(TunerUiState.PermissionRequired))
    }
}
