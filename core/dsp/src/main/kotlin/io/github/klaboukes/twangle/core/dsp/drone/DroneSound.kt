package io.github.klaboukes.twangle.core.dsp.drone

/**
 * The drone's four sounds (Phase 48, the owner's pick of 2026-10-26: all four of round 2), in the
 * order the drone panel lists them. The names will be saved, so they are never renamed.
 */
enum class DroneSound {
    /** A tanpura re-plucked in its cycle, each pluck blooming into the jawari bridge's buzz. */
    TANPURA,

    /** A harmonium (shruti box): two reeds a note, a few cents apart, nasal, the bellows swelling. */
    HARMONIUM,

    /** A stopped flute organ pipe: nearly pure, round and perfectly still. */
    FLUTE_ORGAN,

    /** A warm synth pad: five detuned sawtooth voices through a low-pass that slowly opens and closes. */
    WARM_PAD,
}

/** Which notes the drone sounds over its root. The names will be saved, so they are never renamed. */
enum class DroneVoicing {
    /** The root alone. */
    ROOT,

    /** The root and its fifth (a pure 3:2; the tanpura's Pa, a fourth below the root). */
    FIFTH,

    /** The root, its fifth and its octave (2:1). */
    FIFTH_OCTAVE,
}
