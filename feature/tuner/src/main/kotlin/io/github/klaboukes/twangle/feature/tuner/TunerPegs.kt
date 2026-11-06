package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.TUNER_BUTTON_FACE
import io.github.klaboukes.twangle.feature.tuner.components.shortStringGapFor
import io.github.klaboukes.twangle.feature.tuner.components.stringFaceInsetFor
import io.github.klaboukes.twangle.feature.tuner.components.LiveTone
import io.github.klaboukes.twangle.feature.tuner.components.StringSelector
import io.github.klaboukes.twangle.feature.tuner.components.StringMark
import io.github.klaboukes.twangle.feature.tuner.components.markOf
import kotlin.math.min

// The pegs' slot under the word: the strings to tap and hold, the strum check's button, the 12th-fret check's panel.

/**
 * The [PegsSlot] for each [TunerUiState]: the pegs (with the strum check's button [strumButton], the signal [bars] and
 * a strum's marks above them in the plain tuner) in guitar mode; chromatic mode (Any note) leaves the slot empty, at
 * the same height.
 */
@Composable
internal fun GuitarSlotFor(
    state: TunerUiState,
    mode: TunerMode,
    strings: StringActions,
    strumButton: (@Composable () -> Unit)? = null,
    wide: Boolean = false,
    bars: (@Composable () -> Unit)? = null,
) {
    // The strum check's button and the signal bars with the plain tuner's pegs only (the 12th-fret check's pegs pick its string).
    val plain = state is TunerUiState.Detected || state is TunerUiState.Listening
    val button = strumButton.takeIf { plain }
    val shownBars = bars.takeIf { plain }
    val controls = when (state) {
        // The strings ticked off only while they belong to the tuning shown (a new one starts afresh).
        is TunerUiState.Detected -> GuitarControlsState(
            state.tuning, state.notation, state.targetString, state.lockedString, state.inTune,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
            tone = strings.tone,
            strumMarks = strings.strumMarks,
        )
        // A locked string is the target before anything is played, so it stays ringed (in the resting tone).
        is TunerUiState.Listening -> GuitarControlsState(
            state.tuning, state.notation, state.lockedString, state.lockedString, inTune = false,
            tuned = strings.tuned?.takeIf { it.tuning == state.tuning },
            tone = strings.tone,
            strumMarks = strings.strumMarks,
        )
        // The 12th-fret check: the string being checked, and every string's result on its button.
        is TunerUiState.IntonationCheck ->
            GuitarControlsState(
                state.tuning, state.notation, targetString = state.string, lockedString = null, inTune = false,
                marks = state.results.map(::markOf), checking = true,
            )
        // Errors, and PermissionRequired (only for a moment, until the ViewModel hears that the
        // permission is granted): empty space of the same height.
        else -> null
    }
    // One call site for both Detected and Listening, so the controls stay in the composition when a
    // note starts or stops.
    val guitar: (@Composable () -> Unit)? = if (controls != null) {
        {
            GuitarControls(
                controls.tuning, controls.notation, strings,
                controls.targetString, controls.lockedString, controls.inTune,
                marks = controls.marks,
                checking = controls.checking,
                tuned = controls.tuned,
                tone = controls.tone,
                strumMarks = controls.strumMarks,
                strumButton = button,
                beside = wide,
                bars = shownBars,
            )
        }
    } else {
        null
    }
    PegsSlot(shown = mode == TunerMode.AUTO, pegs = guitar, minHeight = if (wide) WIDE_PEGS_HEIGHT + MARKS_ROOM else GUITAR_SLOT_HEIGHT)
}

/** What [GuitarControls] shows for the current [TunerUiState]. */
private data class GuitarControlsState(
    val tuning: Tuning,
    val notation: Notation,
    val targetString: Int?,
    val lockedString: Int?,
    val inTune: Boolean,
    val marks: List<StringMark?>? = null,
    val checking: Boolean = false,
    val tuned: TunedStrings? = null,
    val tone: LiveTone? = null,
    val strumMarks: List<StrumString>? = null,
)

/**
 * What the pegs do: a tap ([onClick]: lock or unlock, or stop a sounding tone), a long-press
 * ([onLongClick]: play the string's note, none without it), which string's tone is [sounding],
 * which strings are ticked off as tuned ([tuned], only with the string tick-off on), the reading's [tone] the
 * heard peg is ringed in, and a strum's verdicts to mark above the pegs ([strumMarks], Phase 74).
 */
internal data class StringActions(
    val onClick: (Int) -> Unit,
    val onLongClick: ((Int) -> Unit)?,
    val sounding: Int?,
    val tuned: TunedStrings? = null,
    val tone: LiveTone? = null,
    val strumMarks: List<StrumString>? = null,
)

/** Whether these ticks are of [tuning] and tick off every one of its strings. */
internal fun TunedStrings?.coversAll(tuning: Tuning): Boolean =
    this != null && this.tuning == tuning && tuning.strings.indices.all { it in this }

/** The instrument [state]'s tuning belongs to; the default guitar without a tuning. */
internal fun instrumentOf(state: TunerUiState): Instrument {
    val tuning = when (state) {
        is TunerUiState.Listening -> state.tuning
        is TunerUiState.Detected -> state.tuning
        else -> null
    }
    return tuning?.let { Instruments.ofTuning(it.id) } ?: Instruments.DEFAULT
}

/**
 * The string row: what guitar mode shows in the [PegsSlot]. The strum check's button ([strumButton], 2.0 Phase 74,
 * the owner's pick after the guitar check of 2026-11-03) sits in a row of its own above the strings at its end, its
 * outline ending in line with the last string's button ([stringFaceInsetFor]); between that row and the pegs
 * [MARKS_ROOM] is kept for a strum's marks above each peg ([strumMarks]: ↑ ↓ ✓), so a mark never meets the button and
 * nothing moves when either comes or goes. The row's height is kept without the button too (the 12-string, the
 * 12th-fret check, a build without the check). [beside] (the wide layout, Phase 73, where height is scarce) puts the
 * button beside the strings instead, after the last, in a slot kept whether it shows or not and as wide a one before
 * the first, so the strings stay centred and never move. The signal [bars] (behind their Settings switch) stand at the
 * row's start, across from the button, the bars' left edge in line with the first string's face (the owner's review of
 * 2026-11-03: beside the ruler's heard note they read as the next note's); not in [beside]. The instrument button and
 * the tuning's name that started this row until Phase 70 went (the picker chip at the top says and opens the same), and the 12th-fret check's
 * button, at this row's start, when the check moved to the picker sheet (Phase 71, 2026-11-03).
 */
@Composable
internal fun GuitarControls(
    tuning: Tuning,
    notation: Notation,
    stringActions: StringActions,
    targetString: Int?,
    lockedString: Int?,
    inTune: Boolean = false,
    marks: List<StringMark?>? = null,
    checking: Boolean = false,
    tuned: TunedStrings? = null,
    tone: LiveTone? = null,
    strumMarks: List<StrumString>? = null,
    strumButton: (@Composable () -> Unit)? = null,
    beside: Boolean = false,
    bars: (@Composable () -> Unit)? = null,
) {
    val buttonSlot = @Composable { if (strumButton != null) strumButton() }
    val barsSlot = @Composable { if (bars != null && !beside) Box(Modifier.size(SIGNAL_BARS_SIZE)) { bars() } }
    val strings = @Composable {
        StringSelector(
            tuning,
            targetString,
            notation = notation,
            inTune = inTune,
            lockedString = lockedString,
            onStringClick = stringActions.onClick,
            onStringLongClick = stringActions.onLongClick,
            soundingString = stringActions.sounding,
            marks = marks,
            checking = checking,
            tuned = tuned,
            tone = tone,
            strumMarks = strumMarks,
        )
    }
    if (beside) {
        WidePegs(tuning.strings.size, strings, buttonSlot, shortGap = shortStringGapFor(Dp.Infinity, tuning, small = false))
        return
    }
    val facesSpan = LocalStringFacesSpan.current
    // Without the strings, the header over them takes its own width again.
    if (facesSpan != null) DisposableEffect(facesSpan) { onDispose { facesSpan.width = 0 } }
    Layout(contents = listOf(strings, buttonSlot, barsSlot)) { (stringMeasurables, buttonMeasurables, barsMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val stringsPlaceable = stringMeasurables.single().measure(partConstraints)
        val buttonPlaceable = buttonMeasurables.firstOrNull()?.measure(partConstraints)
        val barsPlaceable = barsMeasurables.firstOrNull()?.measure(partConstraints)
        val width = stringsPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val stringsX = (width - stringsPlaceable.width) / 2
        // The first and last buttons' faces: the row spreads them evenly, the same inset at both ends.
        val faceInset = stringFaceInsetFor(stringsPlaceable.width.toDp(), tuning).dp.roundToPx().coerceAtLeast(0)
        val facesStart = stringsX + faceInset
        val facesEnd = stringsX + stringsPlaceable.width - faceInset
        // The strum check's outline ends where the last string's face does.
        val buttonX = buttonPlaceable?.let {
            val outlineInset = (it.width - TUNER_BUTTON_FACE.roundToPx()).coerceAtLeast(0) / 2
            (facesEnd + outlineInset - it.width).coerceIn(0, (width - it.width).coerceAtLeast(0))
        }
        // The button's row, kept without it too, then room for a strum's marks above the pegs (Phase 74).
        val rowHeight = maxOf(STRUM_ROW_HEIGHT.roundToPx(), buttonPlaceable?.height ?: 0)
        val stringsY = rowHeight + MARKS_ROOM.roundToPx()
        val height = (stringsY + stringsPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        // For the header over the strings on its side: as wide as the faces (they are centred, as it is).
        facesSpan?.width = (facesEnd - facesStart).coerceAtLeast(0)
        layout(width, height) {
            if (buttonPlaceable != null && buttonX != null) buttonPlaceable.placeRelative(buttonX, (rowHeight - buttonPlaceable.height) / 2)
            // The bars' left edge where the first string's face starts, level with the button.
            barsPlaceable?.placeRelative(facesStart.coerceIn(0, (width - barsPlaceable.width).coerceAtLeast(0)), (rowHeight - barsPlaceable.height) / 2)
            stringsPlaceable.placeRelative(stringsX, stringsY)
        }
    }
}

/**
 * The space under the word for the [pegs]: placed while [shown] (guitar mode), at the bottom of the slot, and still
 * measured in Any note, where they are not placed, so the slot keeps their height (at least [minHeight]) and
 * switching modes never moves anything, also with a large system font, where the pegs grow. While not shown they are
 * neither drawn nor tappable, and their semantics are cleared, so TalkBack and UI tests don't find them either.
 * (Until Phase 71's last step this was the mode slot, which could also hold something for chromatic mode.)
 */
@Composable
private fun PegsSlot(shown: Boolean, pegs: (@Composable () -> Unit)?, minHeight: Dp = GUITAR_SLOT_HEIGHT) {
    val part = @Composable { if (pegs != null) Box(if (shown) Modifier else Modifier.clearAndSetSemantics {}) { pegs() } }
    Layout(content = part, modifier = Modifier.heightIn(min = minHeight)) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = (placeable?.width ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable?.height ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        // Measured in either mode, so the pegs tell the header over the slot on its side the faces' width in Any note
        // too ([StringFacesSpan]), and its edges stay put.
        layout(width, height) {
            if (shown) placeable?.placeRelative((width - placeable.width) / 2, height - placeable.height)
        }
    }
}
