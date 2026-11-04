package io.github.klaboukes.twangle.core.designsystem.lens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import io.github.klaboukes.twangle.core.designsystem.meter.ClassicLook
import io.github.klaboukes.twangle.core.designsystem.meter.LightArcLook
import io.github.klaboukes.twangle.core.settings.LensLook

/**
 * What the meter draws in its box for [look] (docs/architecture/v2-plan.md, *The meter*), from one [reading]: the
 * box is the meter's (on the page, no frame; ♭ and ♯ drawn by the meter), [wide] when the window is wider than tall
 * (the Light arc's fan opens less wide there; Classic's dial and the strobe fill any box the same way). A look only
 * draws: it decides nothing about in tune, takes no touches and says nothing.
 *
 * Classic ([ClassicLook], the default since Phase 78), the Light arc ([LightArcLook]) and the strobe ([StrobeLook]).
 * The trail ([TrailLook]) was the default from Phase 71 to 77 and is a Practice tool since.
 */
@Composable
fun BoxScope.LensLookArt(look: LensLook, reading: LensReading, wide: Boolean) {
    when (look) {
        LensLook.CLASSIC -> ClassicLook(reading)
        LensLook.LIGHT_ARC -> LightArcLook(reading, wide)
        LensLook.STROBE -> StrobeLook(reading)
    }
}
