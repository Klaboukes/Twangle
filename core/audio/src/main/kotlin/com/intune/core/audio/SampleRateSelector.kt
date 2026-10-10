package com.intune.core.audio

import android.media.AudioManager

/**
 * Picks the recording sample rate. Recording at the device's native rate avoids resampling in
 * Android's audio path.
 */
object SampleRateSelector {

    /**
     * Native rates used as they are. The DSP adapts to any rate from 8 to 96 kHz, but a higher
     * native rate (96 kHz) would only cost CPU, so a native rate is only taken near 48 kHz.
     */
    val SUPPORTED_RATES = 44_100..48_000

    /**
     * Rates tried when the native one is unknown or unsupported, best first. The low ones are a
     * last resort for a device or route that refuses the usual rates; the DSP upsamples them
     * internally (ARCHITECTURE §3, Key numbers).
     */
    val FALLBACK_RATES = listOf(AudioConfig.DEFAULT_SAMPLE_RATE, 44_100, 32_000, 16_000, 8_000)

    /**
     * Rates to try in order: the native rate reported by `AudioManager` (the raw property string,
     * possibly null) if it is in [SUPPORTED_RATES], then [FALLBACK_RATES].
     */
    fun candidates(nativeRateProperty: String?): List<Int> {
        val native = nativeRateProperty?.trim()?.toIntOrNull()?.takeIf { it in SUPPORTED_RATES }
        return (listOfNotNull(native) + FALLBACK_RATES).distinct()
    }

    /** The first of [candidates] for which [isSupported] is true, or [AudioConfig.DEFAULT_SAMPLE_RATE]. */
    fun select(nativeRateProperty: String?, isSupported: (Int) -> Boolean): Int =
        candidates(nativeRateProperty).firstOrNull(isSupported) ?: AudioConfig.DEFAULT_SAMPLE_RATE

    /** The native output rate property, the same rate the audio hardware usually captures at. */
    fun nativeRateProperty(audioManager: AudioManager): String? =
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
}
