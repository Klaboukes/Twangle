package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTuningTest {

    private val dropC = notes(36, 43, 48, 53, 57, 62)
    private val guitar = CustomTuning("custom_1", "Drop C", Instruments.GUITAR, dropC)
    private val uke = CustomTuning("custom_2", "Uke | A, \"B\" 100% + mine é ♯", Instruments.UKULELE, notes(67, 60, 64, 69))

    @Test
    fun `a custom tuning's tuning has its id and notes and no spelling of its own`() {
        assertEquals("custom_1", guitar.tuning.id)
        assertEquals(dropC, guitar.tuning.strings)
        assertNull(guitar.tuning.spelling)
    }

    @Test
    fun `no preset id looks like a custom tuning id`() {
        for (instrument in Instruments.all) {
            for (tuning in instrument.tunings) {
                assertFalse(tuning.id, tuning.id.startsWith(CustomTuning.ID_PREFIX))
            }
        }
    }

    @Test
    fun `names are trimmed, spaces squeezed and long names cut off`() {
        assertEquals("Open C", CustomTuning.normalizeName("  Open \t\n  C "))
        assertEquals("", CustomTuning.normalizeName(" \n\t "))
        val long = CustomTuning.normalizeName("x".repeat(39) + " yz")
        assertEquals("cut to the limit, without a trailing space", "x".repeat(39), long)
        assertEquals(CustomTuning.MAX_NAME_LENGTH, CustomTuning.normalizeName("y".repeat(100)).length)
    }

    @Test
    fun `a long name is cut at a whole character, never inside an emoji or before its accent`() {
        // A guitar emoji is two chars (a surrogate pair): one straddling the limit goes whole.
        val guitar = "🎸"
        assertEquals("x".repeat(39), CustomTuning.cutName("x".repeat(39) + guitar + "y"))
        assertEquals("x".repeat(38) + guitar, CustomTuning.cutName("x".repeat(38) + guitar + "y"))
        // An e with its combining accent (two chars) stays together too.
        val accented = "é"
        assertEquals("x".repeat(39), CustomTuning.cutName("x".repeat(39) + accented + "z"))
        // As saved: no lone half of an emoji, so the name reads back as typed.
        val saved = CustomTuning.normalizeName(guitar.repeat(25))
        assertEquals(guitar.repeat(20), saved)
        assertEquals("unchanged at or under the limit", guitar.repeat(20), CustomTuning.cutName(guitar.repeat(20)))
        assertTrue("no lone surrogate", saved.codePoints().noneMatch { it in 0xD800..0xDFFF })
    }

    @Test
    fun `a custom tuning needs a prefixed id, a normalized name and one note per string`() {
        assertThrows(IllegalArgumentException::class.java) { CustomTuning("drop_c", "Drop C", Instruments.GUITAR, dropC) }
        assertThrows(IllegalArgumentException::class.java) { CustomTuning("custom_", "Drop C", Instruments.GUITAR, dropC) }
        assertThrows(IllegalArgumentException::class.java) { CustomTuning("custom_a|b", "Drop C", Instruments.GUITAR, dropC) }
        assertThrows(IllegalArgumentException::class.java) { CustomTuning("custom_1", "", Instruments.GUITAR, dropC) }
        assertThrows(IllegalArgumentException::class.java) { CustomTuning("custom_1", " Drop C", Instruments.GUITAR, dropC) }
        assertThrows(IllegalArgumentException::class.java) { CustomTuning("custom_1", "Drop C", Instruments.BASS_4, dropC) }
        assertThrows(IllegalArgumentException::class.java) {
            CustomTuning("custom_1", "Too low", Instruments.BASS_4, notes(-1, 33, 38, 43))
        }
    }

    @Test
    fun `saved tunings read back the same, in order, whatever the name holds`() {
        val tunings = listOf(guitar, uke)
        assertEquals(tunings, CustomTuningCodec.decode(CustomTuningCodec.encode(tunings)))
        assertEquals(emptyList<CustomTuning>(), CustomTuningCodec.decode(CustomTuningCodec.encode(emptyList())))
        assertEquals(emptyList<CustomTuning>(), CustomTuningCodec.decode(null))
    }

    @Test
    fun `fields added by a later version are ignored`() {
        val saved = CustomTuningCodec.encode(listOf(guitar)) + "|favourite|42"
        assertEquals(listOf(guitar), CustomTuningCodec.decode(saved))
    }

    @Test
    fun `damaged lines are skipped and the rest kept`() {
        val good = CustomTuningCodec.encode(listOf(guitar))
        val damaged = listOf(
            "",
            "garbage",
            "custom_3|banjo|50,55,59,62,67|Open+G",
            "custom_4|guitar|40,45,50|Too+few",
            "custom_5|guitar|40,45,50,55,59,x|Not+a+number",
            "custom_6|guitar|40,45,50,55,59,200|Out+of+range",
            "custom_7|guitar|40,45,50,55,59,64|",
            "custom_8|guitar|40,45,50,55,59,64|%E0%A4%A",
            "drop_c|guitar|40,45,50,55,59,64|Not+custom",
            "custom_1|guitar|40,45,50,55,59,64|Same+id+again",
        )
        val saved = (damaged.take(5) + good + damaged.drop(5)).joinToString("\n")
        assertEquals(listOf(guitar), CustomTuningCodec.decode(saved))
    }

    @Test
    fun `a saved name is normalized when read`() {
        val saved = "custom_9|guitar|40,45,50,55,59,64|+++My+++own+"
        assertEquals("My own", CustomTuningCodec.decode(saved).single().name)
    }

    @Test
    fun `picked and per-instrument custom tunings are found in the settings`() {
        val settings = AppSettings(customTunings = listOf(guitar, uke))
        assertNull("a preset is picked", settings.pickedCustomTuning)
        assertEquals(listOf(guitar), settings.customTuningsFor(Instruments.GUITAR))
        assertEquals(emptyList<CustomTuning>(), settings.customTuningsFor(Instruments.VIOLIN))

        val picked = settings.copy(instrument = Instruments.UKULELE, tuning = uke.tuning)
        assertEquals(uke, picked.pickedCustomTuning)
        assertEquals(Tunings.STANDARD.id, AppSettings().tuning.id)
    }

    private fun notes(vararg midis: Int): List<Note> = midis.map(::Note)
}
