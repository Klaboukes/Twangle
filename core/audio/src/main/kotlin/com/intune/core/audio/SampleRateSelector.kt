package com.intune.core.audio

import android.media.AudioManager

/**
 * Picks the recording sample rate. Recording at the device's native rate avoids resampling in
 * Android's audio path.
 */
object SampleRateSelector {

    /** The DSP is tuned for windows of ~85 ms (4096 samples), so only rates near 48 kHz are used. */
    val SUPPORTED_RATES = 44_100..48_000

    /** Rates tried when the native one is unknown or unsupported, best first. */
    val FALLBACK_RATES = listOf(AudioConfig.DEFAULT_SAMPLE_RATE, 44_100)

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
