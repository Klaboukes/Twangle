package io.github.klaboukes.twangle.app.widget

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import io.github.klaboukes.twangle.app.R as AppR
import io.github.klaboukes.twangle.feature.tuner.R as TunerR

class WidgetStateTest {

    /** The English strings the widget reads, looked up as Android would. */
    private val strings = mapOf(
        AppR.string.widget_title to "%1\$s · %2\$s",
        TunerR.string.tuner_spoken_picker to "%1\$s, %2\$s",
        TunerR.string.tuner_mode_chromatic to "Chromatic",
        TunerR.string.instrument_guitar to "Guitar",
        TunerR.string.instrument_bass_4 to "Bass (4)",
        TunerR.string.tuning_standard to "Standard",
        TunerR.string.tuning_drop_d to "Drop D",
    )
    private val string = { id: Int, args: Array<out Any> -> strings.getValue(id).format(*args) }

    @Test
    fun showsTheInstrumentAndTuning() {
        val state = WidgetState.from(AppSettings())
        assertEquals("Guitar · Standard", state.title(string))
        assertEquals("Guitar, Standard", state.spoken(string))
        val dropD = WidgetState.from(AppSettings(tuning = Tunings.DROP_D))
        assertEquals("Guitar · Drop D", dropD.title(string))
    }

    @Test
    fun anotherInstrumentsStandardTuningIsCalledStandard() {
        val bass = Instruments.BASS_4
        val state = WidgetState.from(AppSettings(instrument = bass, tuning = bass.defaultTuning))
        assertEquals("Bass (4) · Standard", state.title(string))
    }

    @Test
    fun aCustomTuningShowsItsOwnName() {
        val custom = CustomTuning("custom_1", "Drop D loose", Instruments.GUITAR, Tunings.DROP_D.strings.map { Note(it.midi - 1) })
        val state = WidgetState.from(AppSettings(tuning = custom.tuning, customTunings = listOf(custom)))
        assertEquals("Guitar · Drop D loose", state.title(string))
        assertEquals("Guitar, Drop D loose", state.spoken(string))
    }

    @Test
    fun chromaticModeSaysChromatic() {
        val state = WidgetState.from(AppSettings(mode = TunerMode.CHROMATIC))
        assertEquals("Chromatic", state.title(string))
        assertEquals("Chromatic", state.spoken(string))
    }

    @Test
    fun onlyWhatTheWidgetShowsRedrawsIt() {
        val base = WidgetState.from(AppSettings())
        // A4, the meter's look and the metronome don't change the widget.
        assertEquals(base, WidgetState.from(AppSettings(a4Hz = 442f, metronomeBpm = 90)))
        assertNotEquals(base, WidgetState.from(AppSettings(style = VisualStyle.RETRO)))
        assertNotEquals(base, WidgetState.from(AppSettings(tuning = Tunings.DROP_D)))
    }
}
