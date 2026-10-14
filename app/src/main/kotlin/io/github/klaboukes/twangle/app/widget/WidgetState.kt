package io.github.klaboukes.twangle.app.widget

import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.components.instrumentNameRes
import io.github.klaboukes.twangle.feature.tuner.components.tuningNameRes
import io.github.klaboukes.twangle.app.R as AppR
import io.github.klaboukes.twangle.feature.tuner.R as TunerR

/**
 * The part of the saved settings the home-screen widget shows: the tuner's mode, instrument and
 * tuning (a custom tuning by its own name), and the style and theme it is drawn in. Settings
 * changes that leave this the same (A4, the meter's look, the metronome…) don't redraw the widget.
 */
internal data class WidgetState(
    val chromatic: Boolean,
    val instrumentId: String,
    val tuningId: String,
    val customTuningName: String?,
    val style: VisualStyle,
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
        names(string)?.let { (instrument, tuning) -> string(TunerR.string.tuner_spoken_picker, arrayOf(instrument, tuning)) }
            ?: string(TunerR.string.tuner_mode_chromatic, emptyArray())

    /** The instrument's and the tuning's names, or null in chromatic mode. Unknown ids show as they are. */
    private fun names(string: (Int, Array<out Any>) -> String): Pair<String, String>? {
        if (chromatic) return null
        val instrument = instrumentNameRes(instrumentId)?.let { string(it, emptyArray()) } ?: instrumentId
        val tuning = customTuningName ?: tuningNameRes(tuningId)?.let { string(it, emptyArray()) } ?: tuningId
        return instrument to tuning
    }

    companion object {
        fun from(settings: AppSettings) = WidgetState(
            chromatic = settings.mode == TunerMode.CHROMATIC,
            instrumentId = settings.instrument.id,
            tuningId = settings.tuning.id,
            customTuningName = settings.pickedCustomTuning?.name,
            style = settings.style,
            theme = settings.theme,
            dynamicColor = settings.dynamicColor,
        )
    }
}
