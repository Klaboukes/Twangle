package io.github.klaboukes.twangle.core.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

/** The [SpeechEffects] of `android.media.audiofx`, on an `AudioRecord`'s session id. */
internal object AndroidSpeechEffects : SpeechEffects {

    override fun isAvailable(effect: SpeechEffect): Boolean = when (effect) {
        SpeechEffect.AUTOMATIC_GAIN -> AutomaticGainControl.isAvailable()
        SpeechEffect.NOISE_SUPPRESSION -> NoiseSuppressor.isAvailable()
        SpeechEffect.ECHO_CANCELLING -> AcousticEchoCanceler.isAvailable()
    }

    override fun attach(effect: SpeechEffect, sessionId: Int): EffectHandle? {
        val audioEffect: AudioEffect? = when (effect) {
            SpeechEffect.AUTOMATIC_GAIN -> AutomaticGainControl.create(sessionId)
            SpeechEffect.NOISE_SUPPRESSION -> NoiseSuppressor.create(sessionId)
            SpeechEffect.ECHO_CANCELLING -> AcousticEchoCanceler.create(sessionId)
        }
        return audioEffect?.let(::Handle)
    }

    private class Handle(private val effect: AudioEffect) : EffectHandle {
        override val isEnabled: Boolean get() = effect.enabled

        override fun disable(): Boolean = effect.setEnabled(false) == AudioEffect.SUCCESS

        override fun release() = effect.release()
    }
}
