package io.github.klaboukes.twangle.core.audio

/** A microphone that [openFirst] managed to open, with the rate, input and sample format it opened with. */
internal data class OpenedMic<T>(
    val mic: T,
    val sampleRate: Int,
    val input: AudioInput,
    val format: SampleFormat = SampleFormat.FLOAT,
)

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

/**
 * [openFirst] in each of [formats] in turn: every rate the format offers ([rates]) and every input
 * at each, before the next format is tried at all. So float recording is kept wherever any rate
 * and input give it, and 16-bit is asked for only on a phone that refuses float everywhere.
 */
internal inline fun <T : Any> openFirst(
    formats: List<SampleFormat>,
    rates: (SampleFormat) -> List<Int>,
    inputs: List<AudioInput>,
    open: (format: SampleFormat, sampleRate: Int, input: AudioInput) -> T?,
): OpenedMic<T>? {
    for (format in formats) {
        for (rate in rates(format)) {
            for (input in inputs) {
                open(format, rate, input)?.let { return OpenedMic(it, rate, input, format) }
            }
        }
    }
    return null
}
