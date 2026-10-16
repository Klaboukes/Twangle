package io.github.klaboukes.twangle.core.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Decides which physical microphone to ask for when a Bluetooth headset is connected.
 *
 * Android only records from a Bluetooth headset's mic over SCO/HFP or LE Audio, a call-quality
 * link: 8 or 16 kHz, band-limited to about 3.4 or 7 kHz, often with the headset's own noise
 * suppression. Twangle never requests that route (no `startBluetoothSco()` or
 * `setCommunicationDevice()`), but another app or the system may have opened it. So while a
 * Bluetooth input is present, the recorder pins the phone's built-in mic as its preferred device.
 * Wired headsets and USB audio interfaces keep Android's normal routing, so a guitarist can still
 * plug in a better mic. If there is no built-in mic, routing is left to Android; the DSP handles a
 * narrowband, low-rate stream without crashing (docs/architecture/notes-and-audio.md, Audio source selection).
 */
object InputDeviceSelector {

    /** `AudioDeviceInfo.TYPE_BLE_HEADSET` (API 31), spelled out so older APIs can compare with it. */
    const val TYPE_BLE_HEADSET = 26

    /** Device types that mean "a Bluetooth headset mic could take over the input". */
    val BLUETOOTH_INPUT_TYPES: Set<Int> = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, TYPE_BLE_HEADSET)

    /**
     * Index into [inputTypes] (the `AudioDeviceInfo.type` of each input device) of the device to
     * prefer, or null to leave routing to Android: the built-in mic when a Bluetooth input is
     * present, otherwise nothing.
     */
    fun preferredInputIndex(inputTypes: List<Int>): Int? {
        if (inputTypes.none { it in BLUETOOTH_INPUT_TYPES }) return null
        return inputTypes.indexOf(AudioDeviceInfo.TYPE_BUILTIN_MIC).takeIf { it >= 0 }
    }

    /** The input device to pin with `AudioRecord.setPreferredDevice`, or null for Android's default. */
    fun preferredInput(audioManager: AudioManager): AudioDeviceInfo? {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return preferredInputIndex(inputs.map { it.type })?.let { inputs[it] }
    }
}
