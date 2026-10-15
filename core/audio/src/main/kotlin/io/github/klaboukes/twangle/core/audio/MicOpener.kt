package io.github.klaboukes.twangle.core.audio

/** A microphone that [openFirst] managed to open, with the rate and input it opened at. */
internal data class OpenedMic<T>(val mic: T, val sampleRate: Int, val input: AudioInput)

/**
 * Opens the microphone at the first of [rates] that works, trying every one of [inputs] (best
 * first) at each rate before moving on to the next rate: a phone that refuses its usual rate
 * still records at another one, and the best input is kept wherever possible. [open] returns null
 * when a combination doesn't work; it may also throw, which ends the search (e.g. a missing
 * permission). Null when nothing opened.
 */
internal inline fun <T : Any> openFirst(
    rates: List<Int>,
    inputs: List<AudioInput>,
    open: (sampleRate: Int, input: AudioInput) -> T?,
): OpenedMic<T>? {
    for (rate in rates) {
        for (input in inputs) {
            open(rate, input)?.let { return OpenedMic(it, rate, input) }
        }
    }
    return null
}
