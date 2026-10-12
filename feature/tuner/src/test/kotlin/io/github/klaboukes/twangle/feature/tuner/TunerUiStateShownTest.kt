package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerUiStateShownTest {

    private fun detected(cents: Float, frequencyHz: Float = 82.41f, inTune: Boolean = false) = TunerUiState.Detected(
        mode = TunerMode.AUTO,
        tuning = Tunings.STANDARD,
        noteName = "E",
        octave = 2,
        frequencyHz = frequencyHz,
        cents = cents,
        targetString = 0,
        inTune = inTune,
    )

    @Test
    fun `readings that differ by less than the screen shows look the same`() {
        assertEquals(detected(-12.1f, 81.84f).asShown(), detected(-11.8f, 81.836f).asShown())
        assertEquals(detected(0.4f).asShown(), detected(-0.4f).asShown())
    }

    @Test
    fun `a change in whole cents or tenths of a hertz is visible`() {
        assertNotEquals(detected(-12.4f).asShown(), detected(-12.6f).asShown())
        assertNotEquals(detected(3f, 110.04f).asShown(), detected(3f, 110.06f).asShown())
        assertNotEquals(detected(3f, inTune = false).asShown(), detected(3f, inTune = true).asShown())
    }

    @Test
    fun `shown values are rounded the way the readout writes them`() {
        val shown = detected(-11.6f, 81.84f).asShown() as TunerUiState.Detected
        assertEquals(-12f, shown.cents)
        assertEquals(81.8f, shown.frequencyHz)
        // Guitar mode can be far off the string; that is still shown in whole cents.
        assertEquals(-230f, (detected(-230.3f).asShown() as TunerUiState.Detected).cents)
    }

    @Test
    fun `states without a reading are returned as they are`() {
        val listening = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD)
        assertSame(listening, listening.asShown())
        assertSame(TunerUiState.PermissionRequired, TunerUiState.PermissionRequired.asShown())
    }

    @Test
    fun `a reading looks like the last one when text and needle stay put`() {
        assertTrue(detected(-12.10f, 81.84f).looksLike(detected(-12.12f, 81.836f)))
        // The needle moved a visible amount, although the text stays "12 cents flat".
        assertFalse(detected(-12.10f).looksLike(detected(-12.30f)))
        // Pinned to the sharp edge either way.
        assertTrue(detected(230.2f).looksLike(detected(230.4f)))
        assertFalse(detected(230.4f).looksLike(detected(230.6f)))
    }

    @Test
    fun `a reading with any other visible change does not look like the last one`() {
        val base = detected(3f)
        assertFalse(base.looksLike(base.copy(inTune = true)))
        assertFalse(base.looksLike(base.copy(hint = TunerHint.TUNE_UP)))
        assertFalse(base.looksLike(base.copy(noteName = "F")))
        assertFalse(base.looksLike(base.copy(octave = 3)))
        assertFalse(base.looksLike(base.copy(targetString = 1)))
        assertFalse(base.looksLike(base.copy(lockedString = 0)))
        assertFalse(base.looksLike(base.copy(mode = TunerMode.CHROMATIC)))
        assertFalse(base.looksLike(base.copy(tuning = Tunings.DROP_D)))
        assertFalse(base.looksLike(base.copy(a4Hz = 442f)))
        assertFalse(base.looksLike(base.copy(notation = Notation.SOLFEGE)))
        assertFalse(base.looksLike(base.copy(reception = 0.9f)))
        assertFalse(base.looksLike(base.copy(tip = ReceptionTip.TOO_SOFT)))
        assertFalse(base.looksLike(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)))
        val listening = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)
        assertTrue(listening.looksLike(listening.copy()))
        assertFalse(listening.looksLike(listening.copy(tip = ReceptionTip.TOO_NOISY)))
    }

    @Test
    fun `reception is drawn, not written - small changes look the same and the text ignores it`() {
        val base = detected(3f).copy(reception = 0.5f)
        assertTrue(base.looksLike(base.copy(reception = 0.51f)))
        assertFalse(base.looksLike(base.copy(reception = 0.53f)))
        assertEquals(1f, (base.asShown() as TunerUiState.Detected).reception)
        assertEquals(base.asShown(), base.copy(reception = 0.2f).asShown())
    }

    @Test
    fun `looksLike compares every field of Detected`() {
        // looksLike lists the fields one by one so it allocates nothing. A new field needs a line
        // there and in the test above; then update this count.
        assertEquals(15, TunerUiState.Detected::class.java.declaredFields.size)
    }
}
