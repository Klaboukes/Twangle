package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordDetector
import io.github.klaboukes.twangle.core.dsp.chord.ChordResult
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange

/** What a sudden sound is read as, as the player said ([StrumWatcher.start]). */
enum class StrumKind {
    /** Nothing read: before any strum ([StrumWatcher.kind]), and the tuner's paused readings. */
    NONE,

    /** A strum of the tuning's open strings: [StrumDetector] reads each one. */
    OPEN_STRINGS,

    /** A fretted chord: [ChordDetector] names it. */
    CHORD,
}

/**
 * The strum check's button and Listen in Chords (2.0, Phase 74): hears every microphone chunk the tuner (or Chords)
 * hears and, at each sudden sound ([StrumListener]'s strum), reads it as what the player said comes next: a strum
 * of the open strings, read like 1.1's strum check, or a fretted chord, named like 1.1's chord name, every 0.1 s
 * while it rings, from its first frame. Method, figures and limits in docs/architecture/strum-and-metronome.md,
 * *The strum check's button*. A rise before the strum's first frame (a slow strum, a quick arpeggio, a bass's
 * strings beating) counts with it.
 *
 * Until the owner's review of 2026-11-03 it could also decide by itself whether a sound was one string, a strum or
 * a chord (docs/architecture/history.md, *Strum check and chord name*): that flickered on the owner's guitar, both
 * got a button, and the deciding went unused.
 *
 * **Cost.** A strum of open strings costs one [StrumDetector] pass a frame, a chord one [ChordDetector] pass.
 *
 * Use like [StrumListener]: [push] stops when a frame is ready, so call [next] after each push until it gives
 * [Event.NOTHING]. Nothing allocates after [start] (only a new [PitchRange] makes new buffers). Not thread-safe.
 */
class StrumWatcher(val sampleRate: Int) {

    /** What [next] reports. */
    enum class Event {
        /** Nothing new: push more samples. */
        NOTHING,

        /** A sudden sound began ([strumCount] went up), read as [kind]. */
        STRUM,

        /** A reading of the latest strum, of its [kind]: [strings] for open strings, [readingChord] for a chord. */
        READING,

        /** The latest strum stopped ringing. */
        QUIET,
    }

    /** The range the frames are sized for ([StrumDetector.frameLengthFor]). */
    var range: PitchRange = PitchRange.STANDARD
        private set

    private var listener = StrumListener(sampleRate, range)
    private var chordDetector = chordDetectorFor(range, listener.frameLength)
    private var strumDetector = StrumDetector(sampleRate, listener.frameLength)
    private var frame = FloatArray(listener.frameLength)

    /** The notes and chord of the latest frame [ChordDetector] looked at. */
    val notes = ChordResult()

    /** The open strings of the latest [Event.READING] of a strum of open strings, in the tuning's order. */
    val strings = StrumResult(StrumDetector.MAX_STRINGS)

    private val openHz = FloatArray(StrumDetector.MAX_STRINGS)

    /** How many open strings are checked; 0 when chords are read. */
    var stringCount: Int = 0
        private set
    private var a4Hz = DEFAULT_A4_HZ

    // What every sudden sound is read as, from [start].
    private var every = StrumKind.OPEN_STRINGS

    /** The latest strum's kind: what [start] was told, or [StrumKind.NONE] before any strum. */
    var kind: StrumKind = StrumKind.NONE
        private set

    /** The chord of the latest [Event.READING] of a chord (null: none clearly), and its [readingBass]. */
    var readingChord: Chord? = null
        private set
    var readingBass: PitchClass? = null
        private set

    /** The latest [Event.READING]'s frame level in dBFS ([StrumListener.frameLevelDbfs]), which weighs it. */
    var readingLevelDbfs: Float = 0f
        private set

    /** The audio's own time of the latest [Event.READING] (seconds since [start]). */
    var readingSeconds: Double = 0.0
        private set

    /** Readings of the latest strum so far. */
    var readingsSinceStrum: Int = 0
        private set

    private var samples = 0L
    private var lastStrumCount = 0
    private var lastRinging = false

    /** Strums (sudden sounds) noticed since [start]; a rise before the latest one's first frame counts with it. */
    var strumCount: Int = 0
        private set

    /** Whether the latest strum still rings. */
    val isRinging: Boolean get() = listener.isRinging

    /** Seconds of audio pushed since [start]. */
    val seconds: Double get() = samples.toDouble() / sampleRate

    /**
     * Starts listening afresh, every sudden sound read as [every]: [StrumKind.OPEN_STRINGS], a strum of the open
     * strings at [openStringsHz] (Hz, the tuning's order), or [StrumKind.CHORD], a chord named on the grid at [a4Hz]
     * ([openStringsHz] unused); frames sized for [range]. Whatever already rings is where the next strum must rise from.
     */
    fun start(openStringsHz: FloatArray?, a4Hz: Float, every: StrumKind, range: PitchRange = PitchRange.STANDARD) {
        require(a4Hz > 0f) { "a4Hz must be positive, was $a4Hz" }
        require(every != StrumKind.NONE) { "every sound must be read as a strum of the open strings or a chord" }
        require(every != StrumKind.OPEN_STRINGS || openStringsHz != null) { "a strum of the open strings needs them" }
        if (range != this.range) {
            this.range = range
            listener = StrumListener(sampleRate, range)
            chordDetector = chordDetectorFor(range, listener.frameLength)
            strumDetector = StrumDetector(sampleRate, listener.frameLength)
            frame = FloatArray(listener.frameLength)
        } else {
            listener.reset()
        }
        this.a4Hz = a4Hz
        this.every = every
        stringCount = if (openStringsHz == null || every != StrumKind.OPEN_STRINGS) 0 else minOf(openStringsHz.size, StrumDetector.MAX_STRINGS)
        for (i in 0 until stringCount) openHz[i] = openStringsHz!![i]
        samples = 0L
        lastStrumCount = 0
        lastRinging = false
        strumCount = 0
        kind = StrumKind.NONE
        readingsSinceStrum = 0
    }

    /** Takes samples like [StrumListener.push]: stops when a frame is ready, so call [next] after each push. */
    fun push(samples: FloatArray, offset: Int, length: Int): Int {
        val used = listener.push(samples, offset, length)
        this.samples += used
        return used
    }

    /** The next thing to report, or [Event.NOTHING]; call until it gives that. */
    fun next(): Event {
        if (listener.strumCount != lastStrumCount) {
            lastStrumCount = listener.strumCount
            lastRinging = listener.isRinging
            // A rise before the strum's first frame is part of it (a slow strum, a quick arpeggio, low strings beating).
            if (strumCount > 0 && readingsSinceStrum == 0) return next()
            strumCount++
            readingsSinceStrum = 0
            kind = every
            return Event.STRUM
        }
        if (listener.isFrameReady) {
            listener.readFrame(frame)
            if (kind == StrumKind.NONE) return next()
            read()
            return Event.READING
        }
        if (listener.isRinging != lastRinging) {
            lastRinging = listener.isRinging
            if (!lastRinging) return Event.QUIET
        }
        return Event.NOTHING
    }

    private fun read() {
        if (kind == StrumKind.OPEN_STRINGS) {
            strumDetector.detect(frame, openHz, stringCount, strings)
        } else {
            readingChord = chordDetector.detect(frame, notes, a4Hz)
            readingBass = notes.bass
        }
        readingLevelDbfs = listener.frameLevelDbfs
        readingSeconds = seconds
        readingsSinceStrum++
    }

    private fun chordDetectorFor(range: PitchRange, frameLength: Int) = ChordDetector(
        sampleRate,
        frameLength,
        lowestMidi = if (range == PitchRange.LOW) LOW_RANGE_LOWEST_MIDI else ChordDetector.DEFAULT_LOWEST_MIDI,
    )

    companion object {
        /** In the low range notes are looked for from B0 (a 5-string bass), not B1. */
        const val LOW_RANGE_LOWEST_MIDI = 23

        private const val DEFAULT_A4_HZ = 440f
    }
}
