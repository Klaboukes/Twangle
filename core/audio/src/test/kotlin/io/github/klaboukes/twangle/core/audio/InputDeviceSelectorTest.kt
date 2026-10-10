package io.github.klaboukes.twangle.core.audio

import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
import android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC
import android.media.AudioDeviceInfo.TYPE_TELEPHONY
import android.media.AudioDeviceInfo.TYPE_USB_DEVICE
import android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
import io.github.klaboukes.twangle.core.audio.InputDeviceSelector.TYPE_BLE_HEADSET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputDeviceSelectorTest {

    @Test
    fun `without Bluetooth, routing is left to Android`() {
        assertNull(InputDeviceSelector.preferredInputIndex(listOf(TYPE_BUILTIN_MIC, TYPE_TELEPHONY)))
        assertNull(InputDeviceSelector.preferredInputIndex(listOf(TYPE_BUILTIN_MIC, TYPE_WIRED_HEADSET)))
        assertNull(InputDeviceSelector.preferredInputIndex(listOf(TYPE_USB_DEVICE, TYPE_BUILTIN_MIC)))
        assertNull(InputDeviceSelector.preferredInputIndex(emptyList()))
    }

    @Test
    fun `a Bluetooth headset mic makes the built-in mic preferred`() {
        assertEquals(1, InputDeviceSelector.preferredInputIndex(listOf(TYPE_BLUETOOTH_SCO, TYPE_BUILTIN_MIC)))
        assertEquals(0, InputDeviceSelector.preferredInputIndex(listOf(TYPE_BUILTIN_MIC, TYPE_WIRED_HEADSET, TYPE_BLUETOOTH_SCO)))
    }

    @Test
    fun `LE Audio headsets count as Bluetooth too`() {
        assertEquals(1, InputDeviceSelector.preferredInputIndex(listOf(TYPE_BLE_HEADSET, TYPE_BUILTIN_MIC)))
    }

    @Test
    fun `with Bluetooth but no built-in mic, routing is left to Android`() {
        assertNull(InputDeviceSelector.preferredInputIndex(listOf(TYPE_BLUETOOTH_SCO, TYPE_USB_DEVICE)))
    }
}
