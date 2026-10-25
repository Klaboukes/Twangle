package io.github.klaboukes.twangle.core.dsp.metronome

/**
 * The instrument the metronome clicks with (owner's choice, Phase 45, 2026-10-25): four, each in
 * its own register and with its own nature, so they never sound alike. [ClickSounds] renders them.
 * The order is the order they are offered in.
 */
enum class ClickSound {
    /** A short wooden "tock" (880 Hz, 40 ms): the metronome's click from the start, and the default. */
    WOOD_BLOCK,

    /** A choked metal bell (560 Hz, clanging partials to 3 kHz, 180 ms). */
    COWBELL,

    /** A glassy rosewood tone (2.2 kHz, 120 ms). */
    CLAVES,

    /** A dry, noisy crack of a stick on a snare drum's hoop, with a short thump of the drum (80 ms). */
    RIM_CLICK,
    ;

    companion object {
        /** The click of a new metronome. */
        val DEFAULT = WOOD_BLOCK
    }
}
