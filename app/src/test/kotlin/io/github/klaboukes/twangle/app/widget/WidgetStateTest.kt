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
        val state = widgetState(AppSettings())
        assertEquals("Guitar · Standard", state.title(string))
        assertEquals("Guitar, Standard", state.spoken(string))
        val dropD = widgetState(AppSettings(tuning = Tunings.DROP_D))
        assertEquals("Guitar · Drop D", dropD.title(string))
    }

    @Test
    fun anotherInstrumentsStandardTuningIsCalledStandard() {
        val bass = Instruments.BASS_4
        val state = widgetState(AppSettings(instrument = bass, tuning = bass.defaultTuning))
        assertEquals("Bass (4) · Standard", state.title(string))
    }

    @Test
    fun aCustomTuningShowsItsOwnName() {
        val custom = CustomTuning("custom_1", "Drop D loose", Instruments.GUITAR, Tunings.DROP_D.strings.map { Note(it.midi - 1) })
        val state = widgetState(AppSettings(tuning = custom.tuning, customTunings = listOf(custom)))
        assertEquals("Guitar · Drop D loose", state.title(string))
        assertEquals("Guitar, Drop D loose", state.spoken(string))
    }

    @Test
    fun aLoweredTuningShowsWhatItTurnsInto() {
        assertEquals("Guitar · Drop C♯", widgetState(AppSettings(tuning = Tunings.DROP_D.lowered(1))).title(string))
        assertEquals("Guitar, Drop C sharp", widgetState(AppSettings(tuning = Tunings.DROP_D.lowered(1))).spoken(string))
        assertEquals("Guitar · Half step down", widgetState(AppSettings(tuning = Tunings.STANDARD.lowered(1))).title(string))
        assertEquals("Guitar · Open G, ½ step down", widgetState(AppSettings(tuning = Tunings.OPEN_G.lowered(1))).title(string))
    }

    @Test
    fun chromaticModeSaysAnyNote() {
        val state = widgetState(AppSettings(mode = TunerMode.CHROMATIC))
        assertEquals("Any note", state.title(string))
        assertEquals("Any note", state.spoken(string))
    }

    @Test
    fun onlyWhatTheWidgetShowsRedrawsIt() {
        val base = widgetState(AppSettings())
        // A4, the meter's look and the metronome don't change the widget.
        assertEquals(base, widgetState(AppSettings(a4Hz = 442f, metronomeBpm = 90)))
        assertNotEquals(base, widgetState(AppSettings(dynamicColor = true)))
        assertNotEquals(base, widgetState(AppSettings(tuning = Tunings.DROP_D)))
        assertNotEquals(base, widgetState(AppSettings(tuning = Tunings.STANDARD.lowered(2))))
    }

    // Phase 91: the widget is drawn in the high-contrast look as the app shows itself, the player's switch first.
    @Test
    fun theHighContrastLookIsThePlayersChoiceElseThePhones() {
        assertEquals(false, WidgetState.from(AppSettings(), phoneHighContrast = false).highContrast)
        assertEquals(true, WidgetState.from(AppSettings(), phoneHighContrast = true).highContrast)
        assertEquals(false, WidgetState.from(AppSettings(highContrast = false), phoneHighContrast = true).highContrast)
        assertEquals(true, WidgetState.from(AppSettings(highContrast = true), phoneHighContrast = false).highContrast)
        assertEquals(
            "the phone's contrast changes nothing once the player has set the switch",
            WidgetState.from(AppSettings(highContrast = true), phoneHighContrast = false),
            WidgetState.from(AppSettings(highContrast = true), phoneHighContrast = true),
        )
    }

    /** The widget's state on a phone that doesn't ask for high contrast. */
    private fun widgetState(settings: AppSettings) = WidgetState.from(settings, phoneHighContrast = false)
}
