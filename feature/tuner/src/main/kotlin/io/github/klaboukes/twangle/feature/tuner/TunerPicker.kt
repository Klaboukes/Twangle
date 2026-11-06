package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import io.github.klaboukes.twangle.core.designsystem.PickerChip
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.InstrumentTuningPanel
import io.github.klaboukes.twangle.feature.tuner.components.instrumentLabel
import io.github.klaboukes.twangle.feature.tuner.components.tuningLabel

// The picker chip at the top of the tuner and the sheet it opens, "What are you tuning?" (2.0, Phase 70).

/**
 * The picker sheet, "What are you tuning?" ([InstrumentTuningPanel]), for [tuning]. Its Any note
 * switch sets the [mode] (on: [TunerMode.CHROMATIC]); the strings show in guitar mode only, so an
 * instrument, a tuning or a "Lower by" picked in chromatic mode switches to it. With [onIntonationCheck]
 * its last row starts the 12th-fret check.
 */
@Composable
internal fun TuningPanel(
    instrument: Instrument,
    tuning: PanelTuning,
    mode: TunerMode,
    onModeChange: (TunerMode) -> Unit,
    onInstrumentChange: (Instrument) -> Unit,
    onTuningChange: (Tuning) -> Unit,
    onDismiss: () -> Unit,
    customTunings: List<CustomTuning>,
    onNewTuning: ((Instrument) -> Unit)?,
    onEditTuning: ((CustomTuning) -> Unit)?,
    onDeleteTuning: ((CustomTuning) -> Unit)?,
    onLoweredByChange: ((Int) -> Unit)?,
    onIntonationCheck: (() -> Unit)? = null,
) {
    val toGuitar = { if (mode != TunerMode.AUTO) onModeChange(TunerMode.AUTO) }
    InstrumentTuningPanel(
        instrument = instrument,
        tuning = tuning.tuning,
        onInstrumentChange = {
            toGuitar()
            onInstrumentChange(it)
        },
        onTuningChange = {
            toGuitar()
            onTuningChange(it)
        },
        onDismiss = onDismiss,
        notation = tuning.notation,
        customTunings = customTunings,
        onNewTuning = onNewTuning,
        onEditTuning = onEditTuning,
        onDeleteTuning = onDeleteTuning,
        onLoweredByChange = onLoweredByChange?.let { lower ->
            { semitones: Int ->
                toGuitar()
                lower(semitones)
            }
        },
        anyNote = mode == TunerMode.CHROMATIC,
        onAnyNoteChange = { on -> onModeChange(if (on) TunerMode.CHROMATIC else TunerMode.AUTO) },
        onIntonationCheck = onIntonationCheck,
    )
}

/**
 * The picker chip at the top of the tuner ([PickerChip]): in guitar mode the instrument and [tuning]
 * ("Guitar · Drop D", or the player's own tuning's name after the instrument's, [names]), in chromatic
 * [mode] "Any note"; a tap ([onClick]) opens the picker sheet.
 */
@Composable
internal fun TunerPickerChip(mode: TunerMode, tuning: PanelTuning, names: TuningNames, onClick: () -> Unit) {
    val anyNote = stringResource(R.string.tuner_any_note)
    val chromatic = mode == TunerMode.CHROMATIC
    PickerChip(
        label = if (chromatic) anyNote else names.labelFor(tuning.tuning, spoken = false),
        spoken = if (chromatic) anyNote else names.labelFor(tuning.tuning, spoken = true),
        onClick = onClick,
        modifier = Modifier.testTag(TunerTestTags.PICKER_CHIP),
    )
}

/**
 * What the picker chip's name is made of: the [instrument] picked and the player's own
 * [customTunings], whose names are their own (the presets' come from the string resources).
 */
internal data class TuningNames(val instrument: Instrument, val customTunings: List<CustomTuning>)

/** "Guitar · Drop D" for [tuning], or the player's own tuning's name after the instrument's. */
@Composable
private fun TuningNames.labelFor(tuning: Tuning, spoken: Boolean): String {
    val tuningName = tuningLabel(tuning, customTunings.firstOrNull { it.id == tuning.id }?.name)
    return if (spoken) {
        stringResource(R.string.tuner_spoken_picker, instrumentLabel(instrument), rememberNoteNameSpeech().text(tuningName))
    } else {
        stringResource(R.string.tuner_tuning_name, instrumentLabel(instrument), tuningName)
    }
}
