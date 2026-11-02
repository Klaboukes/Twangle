package io.github.klaboukes.twangle.app.widget

import io.github.klaboukes.twangle.core.designsystem.noteNameSpeech
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.instrumentNameRes
import io.github.klaboukes.twangle.feature.tuner.components.tuningName
import io.github.klaboukes.twangle.app.R as AppR
import io.github.klaboukes.twangle.feature.tuner.R as TunerR

/**
 * The part of the saved settings the home-screen widget shows: the tuner's mode, instrument and
 * tuning (a custom tuning by its own name, a lowered one by what it turns into, as the tuner names
 * it), and the theme and wallpaper colours it is drawn in (Ink, 2.0's one look). Settings
 * changes that leave this the same (A4, the meter's look, the metronome…) don't redraw the widget.
 */
internal data class WidgetState(
    val chromatic: Boolean,
    val instrumentId: String,
    val tuning: Tuning,
    val customTuningName: String?,
    val theme: ThemeMode,
    val dynamicColor: Boolean,
) {
    /**
     * The widget's line, e.g. "Guitar · Standard", "Bass (4) · Drop D loose" (a custom tuning) or, in
     * chromatic mode, "Chromatic". [string] looks up a string resource with its format arguments.
     */
    fun title(string: (Int, Array<out Any>) -> String): String =
        names(string)?.let { (instrument, tuning) -> string(AppR.string.widget_title, arrayOf(instrument, tuning)) }
            ?: string(TunerR.string.tuner_mode_chromatic, emptyArray())

    /** What TalkBack says for [title]: "Guitar, Standard" (as the tuner's own button), or "Chromatic". */
    fun spoken(string: (Int, Array<out Any>) -> String): String =
        names(string)?.let { (instrument, tuning) -> string(TunerR.string.tuner_spoken_picker, arrayOf(instrument, spokenSigns(tuning, string))) }
            ?: string(TunerR.string.tuner_mode_chromatic, emptyArray())

    /** The instrument's and the tuning's names, or null in chromatic mode. Unknown ids show as they are. */
    private fun names(string: (Int, Array<out Any>) -> String): Pair<String, String>? {
        if (chromatic) return null
        val instrument = instrumentNameRes(instrumentId)?.let { string(it, emptyArray()) } ?: instrumentId
        return instrument to tuningName(tuning, customTuningName, string)
    }

    /** [name] with its ♯ and ♭ in words for TalkBack ("Drop C sharp"), as the tuner says them. */
    private fun spokenSigns(name: String, string: (Int, Array<out Any>) -> String): String =
        if (name.none { it == '♯' || it == '♭' }) name else noteNameSpeech { string(it, emptyArray()) }.text(name)

    companion object {
        fun from(settings: AppSettings) = WidgetState(
            chromatic = settings.mode == TunerMode.CHROMATIC,
            instrumentId = settings.instrument.id,
            tuning = settings.tuning,
            customTuningName = settings.pickedCustomTuning?.name,
            theme = settings.theme,
            dynamicColor = settings.dynamicColor,
        )
    }
}
