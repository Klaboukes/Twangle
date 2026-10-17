package io.github.klaboukes.twangle.app

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FocusModeTest {

    private val autoRotateOff = { false }
    private val autoRotateOn = { true }

    @Test
    fun `focus mode asks for either side, also with auto-rotate off`() {
        val turn = FocusTurn(focusMode = false, uprightBefore = true).next(focusMode = true, upright = true, autoRotate = autoRotateOff)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, turn.orientation)
        assertEquals("on its side now", turn, turn.next(focusMode = true, upright = false, autoRotate = autoRotateOff))
    }

    @Test
    fun `without focus mode the request is let go`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, FocusTurn(focusMode = false, uprightBefore = true).orientation)
    }

    @Test
    fun `switched off with auto-rotate off, it turns back upright and then lets go`() {
        val on = FocusTurn(focusMode = true, uprightBefore = true)
        val off = on.next(focusMode = false, upright = false, autoRotate = autoRotateOff)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, off.orientation)
        val upright = off.next(focusMode = false, upright = true, autoRotate = autoRotateOff)
        assertFalse(upright.turningBack)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, upright.orientation)
    }

    @Test
    fun `switched off with auto-rotate on, the phone decides`() {
        val off = FocusTurn(focusMode = true, uprightBefore = true).next(focusMode = false, upright = false, autoRotate = autoRotateOn)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, off.orientation)
    }

    @Test
    fun `turned on while on its side, switching off keeps it on its side`() {
        val on = FocusTurn(focusMode = false, uprightBefore = true).next(focusMode = true, upright = false, autoRotate = autoRotateOff)
        val off = on.next(focusMode = false, upright = false, autoRotate = autoRotateOff)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, off.orientation)
    }

    @Test
    fun `a large screen that stays upright needs no turning back`() {
        // Android 16+ may ignore the request: the window never left upright.
        val off = FocusTurn(focusMode = true, uprightBefore = true).next(focusMode = false, upright = true, autoRotate = autoRotateOff)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, off.orientation)
    }

    @Test
    fun `it survives the activity being recreated`() {
        val turn = FocusTurn(focusMode = false, uprightBefore = true, turningBack = true)
        val saved = with(FocusTurn.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(turn) }!!
        assertEquals(turn, FocusTurn.Saver.restore(saved))
    }
}
