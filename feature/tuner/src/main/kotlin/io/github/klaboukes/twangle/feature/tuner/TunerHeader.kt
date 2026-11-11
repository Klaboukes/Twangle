package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.runtime.Composable
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.FocusAction
import io.github.klaboukes.twangle.feature.tuner.components.FocusModeButton

/**
 * The tuner's header slot (Phase 91, from [TunerContent]): the top row with the app's [settingsButton] (the
 * gear) at its start, [title] ("Tune") in its middle and focus mode's button at its end, and the picker chip
 * on its own line under it (2.0, Phase 70; Phase 77), in the row's middle instead with [chipInRow] (a phone
 * on its side, [ChipRow]). The chip names [tuning] (none over an error, where the sheet can't open: [tuning]
 * null) and calls [onPickerOpenChange] with true. Focus mode's button is there only with [onFocusModeChange].
 */
@Composable
internal fun TunerHeader(
    chipInRow: Boolean,
    mode: TunerMode,
    tuning: PanelTuning?,
    names: TuningNames,
    onPickerOpenChange: (Boolean) -> Unit,
    focusMode: Boolean,
    onFocusModeChange: ((Boolean) -> Unit)?,
    contentState: TunerContentState,
    settingsButton: (@Composable () -> Unit)?,
    title: String?,
) {
    val chip = @Composable {
        if (tuning != null) {
            TunerPickerChip(mode, tuning, names, onClick = { onPickerOpenChange(true) })
        }
    }
    val focusButton = onFocusModeChange?.let { onFocus ->
        @Composable { TunerFocusButton(focusMode, onFocus, contentState) }
    }
    ChipRow(
        chip = chip,
        button = focusButton,
        gear = settingsButton,
        title = title,
        chipInRow = chipInRow,
        hasChip = tuning != null,
    )
}

/**
 * Focus mode's button at the end of the chip's row, in both modes in the same place (owner, 2026-10-25, idea A1
 * of the Phase 43 mock-up: chromatic mode has no strings or tools to stand it on). Among the controls a tap brings
 * up in focus mode it goes back to full screen (owner, 2026-10-19: the exit button there looked as if a tap had
 * already left focus mode); its exit is in the same corner of the full-screen tuner. On, upright (a large screen
 * keeping its rotation lock), it exits.
 */
@Composable
private fun TunerFocusButton(focusMode: Boolean, onFocusModeChange: (Boolean) -> Unit, contentState: TunerContentState) {
    val hide = contentState.hideControls
    when {
        hide != null -> FocusModeButton(FocusAction.HIDE_CONTROLS, onClick = hide)
        focusMode -> FocusModeButton(FocusAction.EXIT, onClick = { onFocusModeChange(false) })
        else -> FocusModeButton(FocusAction.ENTER, onClick = { onFocusModeChange(true) })
    }
}
