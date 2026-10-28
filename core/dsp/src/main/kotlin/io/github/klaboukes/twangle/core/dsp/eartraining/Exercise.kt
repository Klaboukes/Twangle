package io.github.klaboukes.twangle.core.dsp.eartraining

/**
 * Ear training's three exercises (Phase 55), picked with three icons in a row (the owner's pick A in
 * docs/design/ear-training-preview.html). No fourth one: a scale by ear stays in IDEAS.md.
 */
enum class Exercise(
    /** Whether the exercise hears the instrument through the microphone; the others are answered with buttons. */
    val listens: Boolean,
) {
    /** Twangle plays a note, the player finds it on the instrument, and the tuner hears whether it is right. */
    PLAY_IT_BACK(listens = true),

    /** Two notes; the player names the interval with buttons. Works away from the instrument, in headphones. */
    INTERVALS(listens = false),

    /** A chord strummed; the player names its type with buttons. */
    CHORD_TYPE(listens = false),
    ;

    companion object {
        /** A first visit starts on Play it back, the one only a tuner can do. */
        val DEFAULT = PLAY_IT_BACK
    }
}
