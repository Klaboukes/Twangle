package io.github.klaboukes.twangle.core.designsystem.lens

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import io.github.klaboukes.twangle.core.designsystem.meter.MeterReading
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory

/**
 * What a look inside the tuner's lens reads while drawing (2.0, Phase 71; docs/architecture/v2-plan.md,
 * *The lens*): [MeterReading]'s fields (the marker's cents on the spring, the in-tune window, presence, tuned,
 * glow) and the two the lens adds. The tuner's lens (`Lens` in `:feature:tuner`) gives the live reading; the
 * Settings cards (Phase 72) give a demo.
 *
 * Read it only in layer and draw phases (and a look's own frame loop), never while composing, so a look moves
 * without recomposing; reading it allocates nothing. The strobe drifts from [cents] in a frame loop of its own
 * (`StrobeLook`), so the reading carries no strobe phase (v2-plan.md's sketch had one) and a demo reading moves
 * the strobe the same way.
 */
@Stable
interface LensReading : MeterReading {
    /**
     * The reading's colour: muted without a reading, green while the tuner says in tune, otherwise amber eased
     * toward green as the note nears the in-tune zone (*The lens*, the reading's tone). The lens's border takes
     * it, and a look's line and marks.
     */
    val tone: Color

    /** The last seconds of readings, for the trail (written on the DSP thread, read while drawing). */
    val history: PitchHistory
}
