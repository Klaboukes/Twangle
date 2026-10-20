package io.github.klaboukes.twangle.feature.tuner

/** Where Keep screen on stands: the screen held on, the last seconds' warning, or let go to the phone's own timeout. */
enum class ScreenAwakePhase { ON, WARNING, OFF }

/**
 * Keep screen on's quiet timer (owner, 2026-10-20): the screen stays on while the tuner hears
 * playing, and [quietMillis] after the last of it (or the last touch) a warning shows for
 * [warningMillis]; then the screen is left to the phone's own timeout, so a phone forgotten on a
 * music stand goes dark. A note ([Sound.SOUNDING]) counts once it has sounded [noteMillis]: a
 * quiet room's sounds come and go as notes of about 0.2 s (Xiaomi 12X, 2026-10-20), a plucked
 * string rings for seconds. It then holds the screen on however long it lasts, and the quiet time
 * counts from the moment it stops. Not thread-safe.
 */
class ScreenAwakeRule(
    startMillis: Long,
    private val quietMillis: Long = DEFAULT_QUIET_MILLIS,
    private val warningMillis: Long = DEFAULT_WARNING_MILLIS,
    private val noteMillis: Long = DEFAULT_NOTE_MILLIS,
) {
    /** What a new tuner state tells of the player. */
    enum class Sound {
        /** A note sounds now (the tuner shows it). */
        SOUNDING,

        /** Something was played (a tool's panel changed). */
        PLAYED,

        /** Nothing played: listening in silence, or an error. */
        QUIET,
    }

    private var lastActivityMillis = startMillis

    // When the note sounding now began; null without one.
    private var noteStartMillis: Long? = null

    /** A touch, or the tuner coming back to the screen, at [nowMillis]: the quiet time starts afresh. */
    fun touched(nowMillis: Long) {
        lastActivityMillis = nowMillis
    }

    /** A new tuner state at [nowMillis] that tells [sound]. */
    fun heard(sound: Sound, nowMillis: Long) {
        // The quiet time starts when a note that counted stops, not when it began.
        if (sound == Sound.PLAYED || noteCounts(nowMillis)) lastActivityMillis = nowMillis
        noteStartMillis = if (sound == Sound.SOUNDING) noteStartMillis ?: nowMillis else null
    }

    fun phase(nowMillis: Long): ScreenAwakePhase {
        if (noteCounts(nowMillis)) return ScreenAwakePhase.ON
        val quiet = nowMillis - lastActivityMillis
        return when {
            quiet < quietMillis -> ScreenAwakePhase.ON
            quiet < quietMillis + warningMillis -> ScreenAwakePhase.WARNING
            else -> ScreenAwakePhase.OFF
        }
    }

    /** How much of the warning is left at [nowMillis], 1 when it starts to 0 when it ends. */
    fun warningLeft(nowMillis: Long): Float {
        val left = quietMillis + warningMillis - (nowMillis - lastActivityMillis)
        return (left.toFloat() / warningMillis).coerceIn(0f, 1f)
    }

    /**
     * How long from [nowMillis] until [phase] may next change without anything new heard or
     * touched (while a note that counts sounds, as if it stopped now); null once
     * [ScreenAwakePhase.OFF] with no note on the way to counting, which only activity ends.
     */
    fun millisUntilChange(nowMillis: Long): Long? {
        if (noteCounts(nowMillis)) return quietMillis
        val quiet = nowMillis - lastActivityMillis
        val untilQuietEnds = when {
            quiet < quietMillis -> quietMillis - quiet
            quiet < quietMillis + warningMillis -> quietMillis + warningMillis - quiet
            else -> null
        }
        val untilNoteCounts = noteStartMillis?.let { it + noteMillis - nowMillis }
        return listOfNotNull(untilQuietEnds, untilNoteCounts).minOrNull()
    }

    private fun noteCounts(nowMillis: Long): Boolean = noteStartMillis?.let { nowMillis - it >= noteMillis } == true

    companion object {
        const val DEFAULT_QUIET_MILLIS = 3 * 60_000L
        const val DEFAULT_WARNING_MILLIS = 10_000L
        const val DEFAULT_NOTE_MILLIS = 1_000L

        /** What [state] tells of the player. */
        fun soundOf(state: TunerUiState): Sound = when (state) {
            is TunerUiState.Detected -> Sound.SOUNDING
            is TunerUiState.StrumCheck, is TunerUiState.IntonationCheck, is TunerUiState.ChordName -> Sound.PLAYED
            is TunerUiState.Listening, is TunerUiState.Error, TunerUiState.PermissionRequired -> Sound.QUIET
        }
    }
}
