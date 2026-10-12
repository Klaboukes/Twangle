package io.github.klaboukes.twangle.feature.tuner

import kotlin.math.max
import kotlin.math.min

/**
 * A tip about how the tuner hears the player, shown through a [SteadyHint] only when the player
 * can do something about it. Tips settle slowly and stay long, so a single pluck or a passing sound
 * never brings one up and none blinks on and off. See [TipCue] for when each one is wanted.
 */
enum class ReceptionTip(override val settleSeconds: Float, override val minShowSeconds: Float) : SteadyMessage {
    /** The input clips: "Too loud, move the phone back". */
    TOO_LOUD(0.5f, TIP_MIN_SHOW_SECONDS),

    /** The note starts too quietly to read well: "Play a bit louder". */
    TOO_SOFT(0.6f, TIP_MIN_SHOW_SECONDS),

    /** The room is loud, or the note barely rises above it: "Too noisy here". */
    TOO_NOISY(1f, TIP_MIN_SHOW_SECONDS),
}

/** A tip stays on screen at least this long, long enough to read, and through the gap between plucks. */
const val TIP_MIN_SHOW_SECONDS = 3f

/** Once a tip goes, no other tip comes for at least this long. */
const val TIP_GAP_SECONDS = 1f

/**
 * The [ReceptionTip] the sound asks for, frame by frame (before [SteadyHint]'s timing). Each tip
 * has separate on and off limits, so a level hovering at a limit can't make it flicker:
 *
 * - [ReceptionTip.TOO_LOUD] while the frame's peak reaches [CLIP_ON_DBFS] (the mic is at full
 *   scale), until it falls below [CLIP_OFF_DBFS]. One hard pluck clips only for its attack, shorter
 *   than the tip's settle time, so only playing that keeps clipping brings it up.
 * - [ReceptionTip.TOO_SOFT] when the *loudest* level of the note so far is below [SOFT_NOTE_DBFS].
 *   The note's peak only rises, so the fading tail of a well-played note never asks for it.
 * - [ReceptionTip.TOO_NOISY] when the room's background (between notes) is at least
 *   [NOISY_ROOM_ON_DBFS], until it drops below [NOISY_ROOM_OFF_DBFS]; also when the note's best
 *   SNR stays below [NOISY_NOTE_SNR_DB] (it rises too little above the room).
 *
 * What a note asked for (too soft, or too noisy) is still asked for [NOTE_TIP_HOLD_SECONDS] after it
 * ends, so the tip stays through the gap until the next pluck instead of going and coming back.
 *
 * Too loud comes first (it distorts everything else), then too soft (playing louder also lifts the
 * note above the noise), then too noisy. Feed every frame ([update]); [noteHeard] says whether the
 * tuner shows a note, and the note's figures start afresh when it doesn't. Allocation-free and not
 * thread-safe.
 */
class TipCue {
    private var clipping = false
    private var noisyRoom = false
    private var notePeakDbfs = Float.NEGATIVE_INFINITY
    private var noteBestSnrDb = Float.NaN
    private var lastNoteTip: ReceptionTip? = null
    private var sinceNoteSeconds = 0f

    /** Feeds one frame's [reading], lasting [frameSeconds], and returns the tip it asks for, or null for none. */
    fun update(reading: PitchReading, noteHeard: Boolean, frameSeconds: Float): ReceptionTip? {
        clipping = if (clipping) reading.peakDbfs >= CLIP_OFF_DBFS else reading.peakDbfs >= CLIP_ON_DBFS
        val floor = reading.noiseFloorDbfs
        if (!floor.isNaN()) {
            noisyRoom = if (noisyRoom) floor >= NOISY_ROOM_OFF_DBFS else floor >= NOISY_ROOM_ON_DBFS
        }
        if (noteHeard && reading.isPitched) {
            notePeakDbfs = max(notePeakDbfs, reading.levelDbfs)
            if (!reading.snrDb.isNaN()) {
                noteBestSnrDb = if (noteBestSnrDb.isNaN()) reading.snrDb else max(noteBestSnrDb, reading.snrDb)
            }
        } else if (!noteHeard) {
            notePeakDbfs = Float.NEGATIVE_INFINITY
            noteBestSnrDb = Float.NaN
        }
        val noteTip = if (noteHeard) {
            sinceNoteSeconds = 0f
            lastNoteTip = when {
                notePeakDbfs > Float.NEGATIVE_INFINITY && notePeakDbfs < SOFT_NOTE_DBFS -> ReceptionTip.TOO_SOFT
                !noteBestSnrDb.isNaN() && noteBestSnrDb < NOISY_NOTE_SNR_DB -> ReceptionTip.TOO_NOISY
                else -> null
            }
            lastNoteTip
        } else {
            sinceNoteSeconds = min(sinceNoteSeconds + frameSeconds, NOTE_TIP_HOLD_SECONDS)
            if (sinceNoteSeconds < NOTE_TIP_HOLD_SECONDS) lastNoteTip else null
        }
        return when {
            clipping -> ReceptionTip.TOO_LOUD
            noteTip == ReceptionTip.TOO_SOFT -> ReceptionTip.TOO_SOFT
            noisyRoom || noteTip == ReceptionTip.TOO_NOISY -> ReceptionTip.TOO_NOISY
            else -> null
        }
    }

    /** Forgets everything, e.g. when the microphone restarts. */
    fun reset() {
        clipping = false
        noisyRoom = false
        notePeakDbfs = Float.NEGATIVE_INFINITY
        noteBestSnrDb = Float.NaN
        lastNoteTip = null
        sinceNoteSeconds = 0f
    }

    companion object {
        /** A peak this close to full scale means the mic is clipping. */
        const val CLIP_ON_DBFS = -0.5f

        /** Clipping counts as over once the peak stays below this. */
        const val CLIP_OFF_DBFS = -3f

        /**
         * A note whose loudest level stays below this is too soft: the reception's level score is
         * still under 0.4 there (`ReceptionTracker`: 0 at −50, 1 at −35 dBFS), and the note is
         * about to drop through the signal gate at −50 dBFS.
         */
        const val SOFT_NOTE_DBFS = -44f

        /**
         * The room counts as noisy from this background level up: about 15 dB above a quiet phone's
         * own background (−52…−56 dBFS on the S26), where talking or music nearby lands.
         */
        const val NOISY_ROOM_ON_DBFS = -38f

        /** A noisy room counts as quiet again below this. */
        const val NOISY_ROOM_OFF_DBFS = -42f

        /** A note's tip is still asked for this long after it ends: longer than the gap between plucks. */
        const val NOTE_TIP_HOLD_SECONDS = 2.5f

        /** A note whose best SNR stays below this rises too little above the room (clarity ≈ 0.6). */
        const val NOISY_NOTE_SNR_DB = 8f
    }
}
