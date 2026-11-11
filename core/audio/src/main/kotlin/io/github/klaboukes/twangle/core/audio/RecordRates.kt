package io.github.klaboukes.twangle.core.audio

/**
 * Which formats and rates a recorder tries, in order, when it opens the microphone. The rates the
 * phone accepts in each format ([accepted], best first; `AudioRecord.getMinBufferSize`, a call into
 * the audio server for each) are asked for the first time [order] is, on the recorder's read thread
 * as the microphone first opens, not when the recorder is made (on the main thread; on the watch at
 * every start), and kept.
 *
 * First comes the format and rate that opened last ([opened]); before anything opened, the first of
 * [formats] the phone accepts any rate in, at its best rate (48 kHz when it accepts none). Then every
 * format in [formats] order, each at that rate and then at its accepted rates.
 *
 * Used by one read thread at a time; the fields are volatile, so a later thread sees them too.
 */
internal class RecordRates(
    private val formats: List<SampleFormat>,
    private val accepted: (SampleFormat) -> List<Int>,
) {
    init {
        require(formats.isNotEmpty()) { "At least one format" }
    }

    @Volatile
    private var table: Map<SampleFormat, List<Int>>? = null

    @Volatile
    private var last: Opened? = null

    /** The formats and rates to try now, the accepted rates worked out the first time. */
    fun order(): Order {
        val table = table ?: formats.associateWith(accepted).also { table = it }
        val last = last
        val format = last?.format ?: formats.firstOrNull { table.getValue(it).isNotEmpty() } ?: formats.first()
        val rate = last?.rate ?: table.getValue(format).firstOrNull() ?: AudioConfig.DEFAULT_SAMPLE_RATE
        return Order(
            formats = (listOf(format) + formats).distinct(),
            rates = formats.associateWith { (listOf(rate) + table.getValue(it)).distinct() },
        )
    }

    /** The microphone opened in [format] at [rate]: tried first next time. */
    fun opened(format: SampleFormat, rate: Int) {
        last = Opened(format, rate)
    }

    /** The formats to try, in order, and the rates to try in each, in order. */
    class Order(val formats: List<SampleFormat>, val rates: Map<SampleFormat, List<Int>>) {
        /** The format tried first. */
        val first: SampleFormat get() = formats.first()

        /** The rate tried first. */
        val firstRate: Int get() = rates.getValue(first).first()
    }

    private class Opened(val format: SampleFormat, val rate: Int)
}
