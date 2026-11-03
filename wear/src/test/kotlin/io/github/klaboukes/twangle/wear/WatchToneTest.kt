package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.designsystem.lens.meterInTuneExit
import io.github.klaboukes.twangle.core.designsystem.lens.readingToneShare
import io.github.klaboukes.twangle.core.designsystem.meter.meterInTuneWindow
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchToneTest {

    /** The watch's copy is the phone's tone at the phone's default (Relaxed) window, the one the watch's detector uses. */
    @Test
    fun `the tone is the phone's, from amber far off to green in tune`() {
        val window = meterInTuneWindow(InTunePrecision.RELAXED)
        val exit = meterInTuneExit(InTunePrecision.RELAXED)
        for (tenths in -600..600 step 7) {
            val cents = tenths / 10f
            for (tuned in listOf(0f, 0.3f, 1f)) {
                assertEquals("$cents cents, tuned $tuned", readingToneShare(cents, window, exit, tuned), WatchTone.share(cents, tuned), 1e-6f)
            }
        }
        assertEquals(readingToneShare(Float.NaN, window, exit, 0.5f), WatchTone.share(Float.NaN, 0.5f), 1e-6f)
    }

    @Test
    fun `far off is amber, in tune is green`() {
        assertEquals(WatchColors.farOff, WatchTone.colour(WatchTone.share(40f, 0f)))
        assertEquals(WatchColors.inTune, WatchTone.colour(WatchTone.share(1f, 1f)))
    }
}
