package io.github.klaboukes.twangle.core.designsystem.lens

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import io.github.klaboukes.twangle.core.designsystem.meter.MeterReading

/**
 * What a look of the tuner's meter reads while drawing (2.0, Phases 71 and 78; docs/architecture/v2-plan.md,
 * *The meter*): [MeterReading]'s fields (the marker's cents on the spring, the in-tune window, presence, tuned,
 * glow) and the reading's [tone]. The tuner's meter (`Lens` in `:feature:tuner`) gives the live reading; the
 * Settings cards (Phase 72) give a demo.
 *
 * Read it only in layer and draw phases (and a look's own frame loop), never while composing, so a look moves
 * without recomposing; reading it allocates nothing. The strobe drifts from [cents] in a frame loop of its own
 * (`StrobeLook`), so the reading carries no strobe phase (v2-plan.md's sketch had one) and a demo reading moves
 * the strobe the same way. The trail's history went to its own reading ([TrailReading]) when the trail left the
 * meter for Practice (Phase 78).
 */
@Stable
interface LensReading : MeterReading {
    /**
     * The reading's colour: muted without a reading, green while the tuner says in tune, otherwise amber eased
     * toward green as the note nears the in-tune zone (*The meter*, the reading's tone). A look's line, lights and
     * stripes take it.
     */
    val tone: Color
}
