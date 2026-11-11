package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.IntonationPanel
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import io.github.klaboukes.twangle.feature.tuner.components.Ruler
import io.github.klaboukes.twangle.feature.tuner.components.StrumPanel
import io.github.klaboukes.twangle.feature.tuner.components.rulerHeardMidi
import io.github.klaboukes.twangle.feature.tuner.components.rulerNames
import io.github.klaboukes.twangle.feature.tuner.components.rulerPosition
import io.github.klaboukes.twangle.feature.tuner.components.rulerRestMidi

/**
 * The tuner's gauge slot (Phase 91, from [TunerContent]): the lens ([Lens], 2.0 Phases 71 and 78) drawing
 * [look]'s look straight on the page, or a tool's panel in its place: the 12th-fret check's for a
 * [TunerUiState.IntonationCheck] (its × calls [onIntonationCheckChange] with false), else the strum check's
 * [strumPanel] while there is one (its × calls [onStrumCheckClose]).
 */
@Composable
internal fun TunerGauge(
    state: TunerUiState,
    strumPanel: TunerUiState.StrumCheck?,
    look: ReadingLook,
    onIntonationCheckChange: ((Boolean) -> Unit)?,
    onStrumCheckClose: (() -> Unit)?,
) {
    // The 12th-fret check takes the lens's place, at the old meter's shape.
    if (state is TunerUiState.IntonationCheck) {
        // The pegs below pick the string; the panel's one control is its ×, which ends the check.
        IntonationPanel(state, Modifier.zIndex(-1f), onClose = onIntonationCheckChange?.let { { it(false) } })
    } else if (strumPanel != null) {
        // The strum check takes the lens's place; its × closes it, as the 12th-fret check's does.
        StrumPanel(strumPanel, Modifier.zIndex(-1f), onClose = onStrumCheckClose)
    } else {
        Lens(
            look = look.look,
            cents = look.needleCents,
            precision = look.precision,
            inTune = (state as? TunerUiState.Detected)?.inTune == true,
            glow = look.glow,
            animationsOff = look.animationsOff,
            // A window wider than tall gets the lens's wide shape and its looks drawn wide.
            wide = LocalLensWide.current,
            // Drawn under its neighbours: its in-tune glow reaches past its box and fades out on the page.
            modifier = Modifier.zIndex(-1f).testTag(TunerTestTags.GAUGE),
            reception = look.reception,
            tone = look.tone,
        )
    }
}

/**
 * The ruler over the lens (2.0, Phase 71) as [shown] has it: it names the note. [large]: upright, the needle in
 * the room over the lens (2026-11-03); on its side and in focus mode the plain size. [edgeClear] keeps its ends
 * clear of what stands at the window's edges.
 */
@Composable
internal fun TunerRuler(shown: RulerShown, state: TunerUiState, look: ReadingLook, large: Boolean, edgeClear: Dp) {
    Ruler(
        pitch = look.rulerPitch,
        names = shown.names,
        rest = shown.rest,
        heard = shown.heard,
        cents = look.needleCents,
        inTune = (state as? TunerUiState.Detected)?.inTune == true,
        precision = look.precision,
        animationsOff = look.animationsOff,
        edgeClear = edgeClear,
        large = large,
    )
}

/** The [ruler] over the [gauge], [gap] apart, or the gauge alone without one. */
@Composable
internal fun RulerOverGauge(ruler: (@Composable () -> Unit)?, gap: Dp, gauge: @Composable () -> Unit) {
    if (ruler == null) {
        gauge()
    } else {
        // Under its neighbours, as the lens is on its own: the lens's glow reaches past its box, behind them.
        Column(Modifier.zIndex(-1f), horizontalAlignment = Alignment.CenterHorizontally) {
            ruler()
            Spacer(Modifier.height(gap))
            gauge()
        }
    }
}

/** What the ruler shows: its 12 [names], where it rests while quiet ([rest]) and the [heard] note (null while quiet). */
internal class RulerShown(val names: List<String>, val rest: Int, val heard: Int?)

/** The note the ruler last showed as heard, so it rests there once the string dies away (not state: nothing redraws for it). */
internal class LastHeardNote {
    var midi: Int? = null
}

/**
 * What the ruler shows for [state] ([RulerShown]): with a note, the heard note as the screen's text has the
 * reading; waiting for one, where it rests ([rulerRestMidi]: a locked string, the note [lastHeard], the
 * tuning's first string or A4). Null for every other state: the ruler shows only in the plain tuner.
 */
@Composable
internal fun rulerFor(state: TunerUiState, lastHeard: LastHeardNote): RulerShown? {
    val mode: TunerMode
    val tuning: Tuning
    val notation: Notation
    val locked: Int?
    when (state) {
        is TunerUiState.Detected -> {
            mode = state.mode
            tuning = state.tuning
            notation = state.notation
            locked = state.lockedString
        }
        is TunerUiState.Listening -> {
            mode = state.mode
            tuning = state.tuning
            notation = state.notation
            locked = state.lockedString
        }
        else -> return null
    }
    val guitar = mode == TunerMode.AUTO
    val names = remember(notation, tuning.spelling, guitar) { rulerNames(notation, tuning, guitar) }
    val heard = (state as? TunerUiState.Detected)?.let { rulerHeardMidi(rulerPosition(it.targetMidi, it.cents)) }
    if (heard != null) SideEffect { lastHeard.midi = heard }
    return RulerShown(names, rest = heard ?: rulerRestMidi(mode, tuning, locked, lastHeard.midi), heard = heard)
}
