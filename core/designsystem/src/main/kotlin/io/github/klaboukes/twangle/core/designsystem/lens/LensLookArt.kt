package io.github.klaboukes.twangle.core.designsystem.lens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import io.github.klaboukes.twangle.core.settings.LensLook

/**
 * What the lens draws inside its box for [look] (docs/architecture/v2-plan.md, *The lens*), from one [reading]:
 * the box is the lens's (rounded, clipped, its border, ♭ and ♯ drawn by the lens), [wide] when the window is
 * wider than tall. A look only draws: it decides nothing about in tune, takes no touches and says nothing.
 *
 * Phase 71 draws the trail for every look; Phase 72 adds the Light arc and the strobe here.
 */
// [wide] is for Phase 72's looks (the Light arc's fan opens less wide); the trail is the same either way.
@Suppress("UNUSED_PARAMETER")
@Composable
fun BoxScope.LensLookArt(look: LensLook, reading: LensReading, wide: Boolean) {
    when (look) {
        LensLook.TRAIL, LensLook.LIGHT_ARC, LensLook.STROBE -> TrailLook(reading)
    }
}
