package com.intune.core.dsp.music

/** How note names are written. Saved in settings by [name], so don't rename the entries. */
enum class Notation {
    /** Letter names with sharps: C, C♯, D … */
    SHARPS,

    /** Letter names with flats: C, D♭, D … */
    FLATS,

    /** Fixed-do solfège: Do, Do♯, Re … */
    SOLFEGE,
    ;

    companion object {
        val DEFAULT = SHARPS
    }
}
