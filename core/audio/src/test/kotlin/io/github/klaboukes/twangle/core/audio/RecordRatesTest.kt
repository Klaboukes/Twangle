package io.github.klaboukes.twangle.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordRatesTest {

    private val asked = mutableListOf<SampleFormat>()

    private fun rates(
        formats: List<SampleFormat> = SampleFormat.entries,
        accepted: Map<SampleFormat, List<Int>> = mapOf(
            SampleFormat.FLOAT to listOf(44_100, 48_000, 16_000),
            SampleFormat.PCM_16BIT to listOf(44_100, 48_000, 32_000, 16_000, 8_000),
        ),
    ) = RecordRates(formats) { format -> asked += format; accepted.getValue(format) }

    @Test
    fun `the phone is asked nothing until the microphone first opens`() {
        val rates = rates()
        assertEquals(emptyList<SampleFormat>(), asked)

        rates.order()
        assertEquals(SampleFormat.entries, asked)
    }

    @Test
    fun `the phone is asked once, however often the microphone opens`() {
        val rates = rates()
        rates.order()
        rates.opened(SampleFormat.FLOAT, 44_100)
        rates.order()
        rates.order()

        assertEquals(SampleFormat.entries, asked)
    }

    @Test
    fun `before anything opened, the first format at its best rate comes first`() {
        val order = rates().order()

        assertEquals(listOf(SampleFormat.FLOAT, SampleFormat.PCM_16BIT), order.formats)
        assertEquals(listOf(44_100, 48_000, 16_000), order.rates.getValue(SampleFormat.FLOAT))
        // The other format tries the first format's best rate first, then its own.
        assertEquals(listOf(44_100, 48_000, 32_000, 16_000, 8_000), order.rates.getValue(SampleFormat.PCM_16BIT))
        assertEquals(SampleFormat.FLOAT, order.first)
        assertEquals(44_100, order.firstRate)
    }

    @Test
    fun `a format the phone accepts no rate in gives way to one it does`() {
        val order = rates(
            accepted = mapOf(SampleFormat.FLOAT to emptyList(), SampleFormat.PCM_16BIT to listOf(48_000, 16_000)),
        ).order()

        assertEquals(listOf(SampleFormat.PCM_16BIT, SampleFormat.FLOAT), order.formats)
        assertEquals(listOf(48_000, 16_000), order.rates.getValue(SampleFormat.PCM_16BIT))
        assertEquals(listOf(48_000), order.rates.getValue(SampleFormat.FLOAT))
    }

    @Test
    fun `a phone that accepts no rate at all is still tried at 48 kHz`() {
        val order = rates(accepted = mapOf(SampleFormat.FLOAT to emptyList(), SampleFormat.PCM_16BIT to emptyList())).order()

        assertEquals(listOf(SampleFormat.FLOAT, SampleFormat.PCM_16BIT), order.formats)
        assertEquals(listOf(AudioConfig.DEFAULT_SAMPLE_RATE), order.rates.getValue(SampleFormat.FLOAT))
        assertEquals(listOf(AudioConfig.DEFAULT_SAMPLE_RATE), order.rates.getValue(SampleFormat.PCM_16BIT))
    }

    @Test
    fun `what opened last is tried first next time`() {
        val rates = rates()
        rates.order()
        rates.opened(SampleFormat.PCM_16BIT, 32_000)
        val order = rates.order()

        assertEquals(listOf(SampleFormat.PCM_16BIT, SampleFormat.FLOAT), order.formats)
        assertEquals(listOf(32_000, 44_100, 48_000, 16_000, 8_000), order.rates.getValue(SampleFormat.PCM_16BIT))
        assertEquals(listOf(32_000, 44_100, 48_000, 16_000), order.rates.getValue(SampleFormat.FLOAT))
    }

    @Test
    fun `a recorder for one format tries only that one`() {
        val order = rates(formats = listOf(SampleFormat.PCM_16BIT)).order()

        assertEquals(listOf(SampleFormat.PCM_16BIT), order.formats)
        assertEquals(listOf(SampleFormat.PCM_16BIT), asked)
    }
}
