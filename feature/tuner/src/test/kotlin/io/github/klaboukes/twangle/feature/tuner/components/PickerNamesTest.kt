package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.core.dsp.music.CommonTuning
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.feature.tuner.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun `every preset tuning has a name, every instrument's first one Standard but the banjo's Open G`() {
        for (instrument in Instruments.all) {
            val first = if (instrument == Instruments.BANJO_5) R.string.tuning_open_g else R.string.tuning_standard
            assertEquals(instrument.id, first, tuningNameRes(instrument.defaultTuning.id))
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

    /** Names as the English strings give them: a resource's name, its argument after a colon. */
    private val string = { id: Int, args: Array<out Any> -> "$id" + args.joinToString("") { ":$it" } }

    @Test
    fun `every common tuning has a name of its own`() {
        val names = CommonTuning.entries.map(::commonTuningNameRes)
        assertEquals(CommonTuning.entries.size, names.toSet().size)
        assertTrue("none is a preset's name", names.none { it in Instruments.all.flatMap { i -> i.tunings }.map { t -> tuningNameRes(t.id) } })
    }

    @Test
    fun `every amount a tuning can be lowered has its words`() {
        assertEquals(Tuning.MAX_LOWERED_BY + 1, (0..Tuning.MAX_LOWERED_BY).map(::loweredAmountRes).toSet().size)
        assertEquals(Tuning.MAX_LOWERED_BY, (1..Tuning.MAX_LOWERED_BY).map(::shiftedTuningNameRes).toSet().size)
    }

    @Test
    fun `a lowered tuning is named after what it turns into, else after itself with how far down`() {
        assertEquals("${R.string.tuning_drop_d}", tuningName(Tunings.DROP_D, null, string))
        assertEquals("${R.string.tuning_drop_c}", tuningName(Tunings.DROP_D.lowered(2), null, string))
        assertEquals("${R.string.tuning_half_step_down}", tuningName(Tunings.STANDARD.lowered(1), null, string))
        assertEquals("${R.string.tuning_c_standard}", tuningName(Tunings.STANDARD.lowered(4), null, string))
        assertEquals("${R.string.tuning_lowered_1}:${R.string.tuning_open_g}", tuningName(Tunings.OPEN_G.lowered(1), null, string))
        // The player's own: its own name, lowered with the shift.
        val mine = Tuning("custom_1", Tunings.OPEN_G.strings.map { it })
        assertEquals("Nick", tuningName(mine, "Nick", string))
        assertEquals("${R.string.tuning_lowered_3}:Nick", tuningName(mine.lowered(3), "Nick", string))
    }
}
