package io.github.klaboukes.twangle.core.audio

/**
 * The speech processing Android lets an app reach on its own recording (`android.media.audiofx`).
 * Android's rules say `UNPROCESSED` and `VOICE_RECOGNITION` carry none of it, but some makers
 * attach it anyway, which bends a string's level and its fading tail.
 */
internal enum class SpeechEffect(val label: String) {
    AUTOMATIC_GAIN("automatic gain"),
    NOISE_SUPPRESSION("noise suppression"),
    ECHO_CANCELLING("echo cancelling"),
}

/** How the phone offers the [SpeechEffect]s: `android.media.audiofx` in the app, a fake in tests. */
internal interface SpeechEffects {

    /** Whether this phone has [effect] at all (the effect class's `isAvailable()`). */
    fun isAvailable(effect: SpeechEffect): Boolean

    /** A handle on [effect] in the recording with [sessionId], or null when Android gives none. May throw. */
    fun attach(effect: SpeechEffect, sessionId: Int): EffectHandle?
}

/** One [SpeechEffect] on one recording, as `AudioEffect` offers it. Every call may throw. */
internal interface EffectHandle {

    /** Whether the effect processes the recording now. */
    val isEnabled: Boolean

    /** Switches the effect off; false when Android refused (another app controls it). */
    fun disable(): Boolean

    /** Lets go of the handle. */
    fun release()
}

/** What was found and done about one [SpeechEffect] on one recording. */
internal enum class EffectOutcome(val label: String) {
    /** The phone has no such effect. */
    NOT_ON_PHONE("not on this phone"),

    /** The phone has it, but Android gave no handle on this recording. */
    NO_HANDLE("no handle"),

    /** Attached but not processing: left alone. */
    ALREADY_OFF("off"),

    /** Was processing the recording; switched off until the recording ends. */
    SWITCHED_OFF("was on, switched off"),

    /** Was processing the recording and could not be switched off. */
    STAYS_ON("on, could not switch it off"),
}

/**
 * The speech effects switched off on one recording: [outcomes] says what was found, and [release]
 * lets go of the handles kept to hold them off. Processing built into the microphone's own chip is
 * out of an app's reach and stays.
 */
internal class SpeechEffectsOff private constructor(
    val outcomes: Map<SpeechEffect, EffectOutcome>,
    private var held: List<EffectHandle>,
) {
    /** One line for the log, e.g. "automatic gain: was on, switched off; noise suppression: off; …". */
    fun summary(): String = outcomes.entries.joinToString("; ") { (effect, outcome) -> "${effect.label}: ${outcome.label}" }

    /** Lets go of every handle still held; safe to call more than once, never throws. */
    fun release() {
        val handles = held
        held = emptyList()
        for (handle in handles) releaseQuietly(handle)
    }

    companion object {
        /**
         * Looks for each [SpeechEffect] on the recording with [sessionId] and switches off the ones
         * that are on. A handle is kept only for an effect switched off, so it stays off (the newest
         * handle has control of an effect) until [release]; the others are let go at once, which
         * leaves the recording as it was. Failures are caught and reported in the outcome: an
         * effect must never stop the tuner. Called once per opened recording, never per chunk.
         */
        fun switchOff(effects: SpeechEffects, sessionId: Int): SpeechEffectsOff {
            val outcomes = LinkedHashMap<SpeechEffect, EffectOutcome>()
            val held = ArrayList<EffectHandle>(SpeechEffect.entries.size)
            for (effect in SpeechEffect.entries) {
                outcomes[effect] = switchOff(effects, effect, sessionId, held)
            }
            return SpeechEffectsOff(outcomes, held)
        }

        private fun switchOff(
            effects: SpeechEffects,
            effect: SpeechEffect,
            sessionId: Int,
            held: MutableList<EffectHandle>,
        ): EffectOutcome {
            val available = try {
                effects.isAvailable(effect)
            } catch (e: Exception) {
                // Unknown: try to attach anyway.
                true
            }
            if (!available) return EffectOutcome.NOT_ON_PHONE
            val handle = try {
                effects.attach(effect, sessionId)
            } catch (e: Exception) {
                null
            } ?: return EffectOutcome.NO_HANDLE
            val wasOn = try {
                handle.isEnabled
            } catch (e: Exception) {
                // A handle that can't say whether it is on is no handle at all.
                releaseQuietly(handle)
                return EffectOutcome.NO_HANDLE
            }
            if (!wasOn) {
                releaseQuietly(handle)
                return EffectOutcome.ALREADY_OFF
            }
            val off = try {
                handle.disable()
            } catch (e: Exception) {
                false
            }
            return if (off) {
                held += handle
                EffectOutcome.SWITCHED_OFF
            } else {
                releaseQuietly(handle)
                EffectOutcome.STAYS_ON
            }
        }

        private fun releaseQuietly(handle: EffectHandle) {
            try {
                handle.release()
            } catch (e: Exception) {
                // Already gone with its recording: nothing left to let go.
            }
        }
    }
}

/**
 * The inputs whose speech effects were already logged, so the log says it once per input for
 * the app's life (one shared [AndroidAudioRecorder.loggedEffects]): enough for a tester's report,
 * not a line every time the tuner opens.
 */
internal class LoggedOnce {

    private val seen = HashSet<AudioInput>()

    /** True the first time [input] comes, false after. */
    @Synchronized
    fun first(input: AudioInput): Boolean = seen.add(input)
}
