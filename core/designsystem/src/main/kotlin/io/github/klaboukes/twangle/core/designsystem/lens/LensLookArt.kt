package io.github.klaboukes.twangle.core.designsystem.lens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import io.github.klaboukes.twangle.core.designsystem.meter.LightArcLook
import io.github.klaboukes.twangle.core.settings.LensLook

/**
 * What the lens draws inside its box for [look] (docs/architecture/v2-plan.md, *The lens*), from one [reading]:
 * the box is the lens's (rounded, clipped, its border, ♭ and ♯ drawn by the lens), [wide] when the window is
 * wider than tall (the Light arc's fan opens less wide there; the trail and the strobe fill any box the same
 * way). A look only draws: it decides nothing about in tune, takes no touches and says nothing.
 *
 * The trail ([TrailLook], the default), the Light arc ([LightArcLook]) and the strobe ([StrobeLook]), Phases 71
 * and 72.
 */
@Composable
fun BoxScope.LensLookArt(look: LensLook, reading: LensReading, wide: Boolean) {
    when (look) {
        LensLook.TRAIL -> TrailLook(reading)
        LensLook.LIGHT_ARC -> LightArcLook(reading, wide)
        LensLook.STROBE -> StrobeLook(reading)
    }
}
