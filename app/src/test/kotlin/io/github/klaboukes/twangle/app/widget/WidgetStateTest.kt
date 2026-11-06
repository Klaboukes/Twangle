package io.github.klaboukes.twangle.app.widget

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import io.github.klaboukes.twangle.app.R as AppR
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.feature.tuner.R as TunerR

class WidgetStateTest {

    /** The English strings the widget reads, looked up as Android would. */
    private val strings = mapOf(
        AppR.string.widget_title to "%1\$s · %2\$s",
        TunerR.string.tuner_spoken_picker to "%1\$s, %2\$s",
        TunerR.string.tuner_any_note to "Any note",
        TunerR.string.instrument_guitar to "Guitar",
        TunerR.string.instrument_bass_4 to "Bass (4)",
        TunerR.string.tuning_standard to "Standard",
        TunerR.string.tuning_drop_d to "Drop D",
        TunerR.string.tuning_open_g to "Open G",
        TunerR.string.tuning_half_step_down to "Half step down",
        TunerR.string.tuning_drop_c_sharp to "Drop C♯",
        TunerR.string.tuning_lowered_1 to "%1\$s, ½ step down",
        DesignR.string.spoken_sharp to "%1\$s sharp",
        DesignR.string.spoken_flat to "%1\$s flat",
        DesignR.string.spoken_letter_names to "words",
        DesignR.string.spoken_solfege to "Do Re Mi Fa Sol La Si",
    )
    // As the widget's own: a string without arguments is read as it is.
    private val string = { id: Int, args: Array<out Any> -> if (args.isEmpty()) strings.getValue(id) else strings.getValue(id).format(*args) }

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
    fun aLoweredTuningShowsWhatItTurnsInto() {
        assertEquals("Guitar · Drop C♯", WidgetState.from(AppSettings(tuning = Tunings.DROP_D.lowered(1))).title(string))
        assertEquals("Guitar, Drop C sharp", WidgetState.from(AppSettings(tuning = Tunings.DROP_D.lowered(1))).spoken(string))
        assertEquals("Guitar · Half step down", WidgetState.from(AppSettings(tuning = Tunings.STANDARD.lowered(1))).title(string))
        assertEquals("Guitar · Open G, ½ step down", WidgetState.from(AppSettings(tuning = Tunings.OPEN_G.lowered(1))).title(string))
    }

    @Test
    fun chromaticModeSaysAnyNote() {
        val state = WidgetState.from(AppSettings(mode = TunerMode.CHROMATIC))
        assertEquals("Any note", state.title(string))
        assertEquals("Any note", state.spoken(string))
    }

    @Test
    fun onlyWhatTheWidgetShowsRedrawsIt() {
        val base = WidgetState.from(AppSettings())
        // A4, the meter's look and the metronome don't change the widget.
        assertEquals(base, WidgetState.from(AppSettings(a4Hz = 442f, metronomeBpm = 90)))
        assertNotEquals(base, WidgetState.from(AppSettings(dynamicColor = true)))
        assertNotEquals(base, WidgetState.from(AppSettings(tuning = Tunings.DROP_D)))
        assertNotEquals(base, WidgetState.from(AppSettings(tuning = Tunings.STANDARD.lowered(2))))
    }
}
