package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.feature.tuner.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Every instrument and every preset tuning has a name for users, not its saved id. */
class PickerNamesTest {

    @Test
    fun `every instrument has a name`() {
        for (instrument in Instruments.all) {
            assertNotNull("no name for ${instrument.id}", instrumentNameRes(instrument.id))
        }
        assertEquals(
            "no two instruments share a name",
            Instruments.all.size,
            Instruments.all.map { instrumentNameRes(it.id) }.toSet().size,
        )
    }

    @Test
    fun `every preset tuning has a name, every instrument's first one Standard`() {
        for (instrument in Instruments.all) {
            assertEquals(instrument.id, R.string.tuning_standard, tuningNameRes(instrument.defaultTuning.id))
            for (tuning in instrument.tunings) {
                assertNotNull("no name for ${tuning.id}", tuningNameRes(tuning.id))
            }
            assertEquals(
                "two of ${instrument.id}'s tunings share a name",
                instrument.tunings.size,
                instrument.tunings.map { tuningNameRes(it.id) }.toSet().size,
            )
        }
    }

    @Test
    fun `an unknown id has no name, so the label shows the id`() {
        assertEquals(null, tuningNameRes("removed_in_v2"))
        assertEquals(null, instrumentNameRes("theremin"))
    }
}
