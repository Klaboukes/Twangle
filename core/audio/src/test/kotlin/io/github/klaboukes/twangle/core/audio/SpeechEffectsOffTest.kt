package io.github.klaboukes.twangle.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SpeechEffectsOff] with fake phones: effects that are on are switched off and held off until the
 * recording ends, the rest is left as it was, and no failure of the phone's effects ever escapes.
 */
class SpeechEffectsOffTest {

    /** How one fake effect behaves once attached. */
    private class FakeHandle(
        var enabled: Boolean,
        private val disableWorks: Boolean = true,
        private val failing: Boolean = false,
    ) : EffectHandle {
        var releases = 0
        var disableCalls = 0

        override val isEnabled: Boolean
            get() {
                if (failing) throw IllegalStateException("effect gone")
                return enabled
            }

        override fun disable(): Boolean {
            disableCalls++
            if (failing) throw IllegalStateException("effect gone")
            if (disableWorks) enabled = false
            return disableWorks
        }

        override fun release() {
            releases++
            if (failing) throw IllegalStateException("effect gone")
        }
    }

    /**
     * A fake phone: [handles] are the effects it attaches (one per attach); effects missing from
     * [available] it doesn't have; effects in [attachThrows] throw when attached.
     */
    private class FakeEffects(
        private val handles: Map<SpeechEffect, FakeHandle> = emptyMap(),
        private val available: Set<SpeechEffect> = SpeechEffect.entries.toSet(),
        private val attachThrows: Set<SpeechEffect> = emptySet(),
    ) : SpeechEffects {
        val sessions = mutableListOf<Int>()

        override fun isAvailable(effect: SpeechEffect) = effect in available

        override fun attach(effect: SpeechEffect, sessionId: Int): EffectHandle? {
            sessions += sessionId
            if (effect in attachThrows) throw UnsupportedOperationException("no $effect here")
            return handles[effect]
        }
    }

    @Test
    fun `effects that are on are switched off and held off until release`() {
        val gain = FakeHandle(enabled = true)
        val noise = FakeHandle(enabled = true)
        val echo = FakeHandle(enabled = true)
        val phone = FakeEffects(
            mapOf(SpeechEffect.AUTOMATIC_GAIN to gain, SpeechEffect.NOISE_SUPPRESSION to noise, SpeechEffect.ECHO_CANCELLING to echo),
        )

        val off = SpeechEffectsOff.switchOff(phone, SESSION)

        assertEquals(SpeechEffect.entries.associateWith { EffectOutcome.SWITCHED_OFF }, off.outcomes)
        assertEquals(listOf(SESSION, SESSION, SESSION), phone.sessions)
        for (handle in listOf(gain, noise, echo)) {
            assertFalse(handle.enabled)
            assertEquals("held while recording", 0, handle.releases)
        }

        off.release()
        off.release()
        for (handle in listOf(gain, noise, echo)) assertEquals("released exactly once", 1, handle.releases)
    }

    @Test
    fun `a phone with none of them gets nothing attached`() {
        val phone = FakeEffects(available = emptySet())
        val off = SpeechEffectsOff.switchOff(phone, SESSION)
        assertEquals(SpeechEffect.entries.associateWith { EffectOutcome.NOT_ON_PHONE }, off.outcomes)
        assertTrue(phone.sessions.isEmpty())
        off.release()
    }

    @Test
    fun `an effect that is already off is left alone and let go at once`() {
        val gain = FakeHandle(enabled = false)
        val off = SpeechEffectsOff.switchOff(FakeEffects(mapOf(SpeechEffect.AUTOMATIC_GAIN to gain)), SESSION)

        assertEquals(EffectOutcome.ALREADY_OFF, off.outcomes[SpeechEffect.AUTOMATIC_GAIN])
        assertEquals(0, gain.disableCalls)
        assertEquals(1, gain.releases)
        off.release()
        assertEquals(1, gain.releases)
    }

    @Test
    fun `no handle, a throwing attach and a refused switch never stop the recording`() {
        val noise = FakeHandle(enabled = true, disableWorks = false)
        val phone = FakeEffects(
            handles = mapOf(SpeechEffect.NOISE_SUPPRESSION to noise),
            attachThrows = setOf(SpeechEffect.ECHO_CANCELLING),
        )

        val off = SpeechEffectsOff.switchOff(phone, SESSION)

        assertEquals(EffectOutcome.NO_HANDLE, off.outcomes[SpeechEffect.AUTOMATIC_GAIN])
        assertEquals(EffectOutcome.STAYS_ON, off.outcomes[SpeechEffect.NOISE_SUPPRESSION])
        assertEquals(EffectOutcome.NO_HANDLE, off.outcomes[SpeechEffect.ECHO_CANCELLING])
        assertEquals("a handle without control is let go at once", 1, noise.releases)
        off.release()
        assertEquals(1, noise.releases)
    }

    @Test
    fun `an effect whose every call throws is reported, released and never escapes`() {
        val broken = FakeHandle(enabled = true, failing = true)
        val off = SpeechEffectsOff.switchOff(FakeEffects(mapOf(SpeechEffect.ECHO_CANCELLING to broken)), SESSION)
        assertEquals(EffectOutcome.NO_HANDLE, off.outcomes[SpeechEffect.ECHO_CANCELLING])
        assertEquals(1, broken.releases)
        off.release()
    }

    @Test
    fun `a disable that throws counts as staying on`() {
        val gain = object : EffectHandle {
            var released = false
            override val isEnabled = true
            override fun disable(): Boolean = throw IllegalStateException("no control")
            override fun release() {
                released = true
            }
        }
        val phone = object : SpeechEffects {
            override fun isAvailable(effect: SpeechEffect) = effect == SpeechEffect.AUTOMATIC_GAIN
            override fun attach(effect: SpeechEffect, sessionId: Int): EffectHandle = gain
        }
        val off = SpeechEffectsOff.switchOff(phone, SESSION)
        assertEquals(EffectOutcome.STAYS_ON, off.outcomes[SpeechEffect.AUTOMATIC_GAIN])
        assertTrue(gain.released)
    }

    @Test
    fun `the log line names every effect and what was done`() {
        val off = SpeechEffectsOff.switchOff(
            FakeEffects(
                handles = mapOf(SpeechEffect.AUTOMATIC_GAIN to FakeHandle(enabled = true), SpeechEffect.NOISE_SUPPRESSION to FakeHandle(enabled = false)),
                available = setOf(SpeechEffect.AUTOMATIC_GAIN, SpeechEffect.NOISE_SUPPRESSION),
            ),
            SESSION,
        )
        assertEquals(
            "automatic gain: was on, switched off; noise suppression: off; echo cancelling: not on this phone",
            off.summary(),
        )
        off.release()
    }

    @Test
    fun `each input is logged once`() {
        val logged = LoggedOnce()
        assertTrue(logged.first(AudioInput.UNPROCESSED))
        assertFalse(logged.first(AudioInput.UNPROCESSED))
        assertTrue(logged.first(AudioInput.VOICE_RECOGNITION))
        assertFalse(logged.first(AudioInput.VOICE_RECOGNITION))
    }

    /**
     * The recorder as [AndroidAudioRecorder] wires it: each opened input switches off its own
     * recording's effects, and letting go of an input lets go of them too, also when a silent
     * input gives way to the next one mid-session.
     */
    @Test
    fun `an input that gives way lets go of its effects, and the next one switches off its own`() {
        class Input(val sessionId: Int, val effects: SpeechEffectsOff, private val silent: Boolean) : MicInput {
            override val isSilenced = false
            override fun read(buffer: FloatArray, offset: Int, count: Int): Int {
                for (i in 0 until count) buffer[offset + i] = if (silent) 0f else 1e-3f
                return count
            }
        }
        val handles = mutableMapOf<Int, FakeHandle>()
        val phone = object : SpeechEffects {
            override fun isAvailable(effect: SpeechEffect) = effect == SpeechEffect.NOISE_SUPPRESSION
            override fun attach(effect: SpeechEffect, sessionId: Int) = FakeHandle(enabled = true).also { handles[sessionId] = it }
        }
        fun open(input: AudioInput): Input {
            val session = if (input == AudioInput.UNPROCESSED) 1 else 2
            return Input(session, SpeechEffectsOff.switchOff(phone, session), silent = input == AudioInput.UNPROCESSED)
        }
        val inputs = listOf(AudioInput.UNPROCESSED, AudioInput.VOICE_RECOGNITION)
        val reader = FallbackReader(
            first = open(AudioInput.UNPROCESSED),
            firstInput = AudioInput.UNPROCESSED,
            inputs = inputs,
            sampleRate = RATE,
            skipped = SkippedInputs(),
            open = ::open,
            release = { it.effects.release() },
            pause = {},
        )
        val chunk = FloatArray(AudioConfig.forSampleRate(RATE).chunkSize)
        val chunksIn2s = Math.ceil(RATE * ChunkReader.SILENT_SECONDS / chunk.size.toDouble()).toInt()

        assertEquals(0, handles.getValue(1).releases)
        repeat(chunksIn2s) { reader.fill(chunk) }
        assertEquals(AudioInput.VOICE_RECOGNITION, reader.input)
        assertEquals("the silent input's effects are let go", 1, handles.getValue(1).releases)
        assertFalse("the next input's effects are off", handles.getValue(2).enabled)
        assertEquals(0, handles.getValue(2).releases)

        reader.close()
        assertEquals("closing lets go of the next one's", 1, handles.getValue(2).releases)
    }

    private companion object {
        const val SESSION = 4242
        const val RATE = 48_000
    }
}
