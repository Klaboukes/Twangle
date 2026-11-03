package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordDetector
import io.github.klaboukes.twangle.core.dsp.chord.ChordResult
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import kotlin.math.ln
import kotlin.math.roundToInt

/** What a sudden sound was, as [StrumWatcher] decides it. */
enum class StrumKind {
    /** One string (with its overtones), a string plucked while others still ring, a reference tone, a knock: no strum. */
    NONE,

    /** A strum of the tuning's open strings: [StrumDetector] reads each one. */
    OPEN_STRINGS,

    /** A fretted chord: [ChordDetector] names it. */
    CHORD,
}

/**
 * The strum check and the chord name without a mode (2.0, Phase 74): listens to every microphone chunk the
 * tuner hears, beside the pitch detector, and decides whether each sudden sound ([StrumListener]'s strum) was
 * one string, a strum of the open strings or a fretted chord; then reads the strum like the strum check, or
 * names the chord like the chord name, every 0.1 s while it rings. Method, figures and limits in
 * docs/architecture/strum-and-metronome.md, *The strum check's button*.
 *
 * **Deciding.** Each of a strum's first frames is looked at by [ChordDetector], which finds its notes
 * whatever they are. Of those, a note counts as a string of its own only when it is at least [OWN_SHARE] of the
 * strongest and doesn't lie on a harmonic of another note found ([ChordDetector.isHarmonicInterval]): a low E's
 * strong 3rd and 5th partials (B3, G#4) are no strings. Every such note must have been **struck by this
 * sound**: at least [STRUCK_RATIO] times its salience in the frame that ended as the sound began
 * ([StrumListener.readBeforeStrum]), so plucking the A string while the low E still rings is one new string,
 * not a power chord, and plucking the D, G and B strings in turn is never G/D. Then the frame is
 * - a strum of the open strings with at least [MIN_OPEN_NOTES] such notes, every one an open string of the
 *   tuning and [StrumDetector] hearing at least half the strings and [MIN_OPEN_NOTES];
 * - a chord when [ChordDetector] names one and at least [MIN_CHORD_NOTES] notes are strings of their own
 *   (two only as a power chord's root and the fifth above it, never a fourth such as two open strings side by
 *   side);
 * - else nothing.
 * The strum's kind is what [DECIDE_WINS] of its first [DECIDE_READINGS] frames say (none when they all
 * differ), about 0.5 s after it; after that it is not decided again until the next strum. A rise before the
 * strum's first frame (a slow strum, a quick arpeggio, a bass's strings beating) counts with it.
 *
 * **Cost.** One sound costs two or three [ChordDetector] passes, and a [StrumDetector] pass on frames that may
 * be open strings; a strum decided as nothing is not read further. A strum of open strings or a chord is then
 * read every 0.1 s while it rings by its one detector, as the 1.1 tools did.
 *
 * **The strum check's button** (2.0, Phase 74, after the owner's guitar check: deciding by itself flickered between a
 * string, a strum and a chord). Started with `everySoundIsAStrum`, nothing is decided: every sudden sound is a strum
 * of the open strings and read from its first frame, as 1.1's strum check read it, because the player said a strum
 * comes next. A rise before the strum's first frame still counts with it.
 *
 * Use like [StrumListener]: [push] stops when a frame is ready, so call [next] after each push until it gives
 * [Event.NOTHING]. Nothing allocates after [start] (only a new [PitchRange] makes new buffers). Not thread-safe.
 */
class StrumWatcher(val sampleRate: Int) {

    /** What [next] reports. */
    enum class Event {
        /** Nothing new: push more samples. */
        NOTHING,

        /** A sudden sound began ([strumCount] went up); its [kind] is null until it is decided. */
        STRUM,

        /** The latest strum's [kind] was decided; [Event.READING]s of the frames that decided it may follow. */
        DECIDED,

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
    private val before = ChordResult()

    /** The open strings of the latest [Event.READING] of a strum of open strings, in the tuning's order. */
    val strings = StrumResult(StrumDetector.MAX_STRINGS)

    private val openHz = FloatArray(StrumDetector.MAX_STRINGS)
    private val openMidi = IntArray(StrumDetector.MAX_STRINGS)

    /** How many open strings are checked; 0 when strums of open strings aren't looked for. */
    var stringCount: Int = 0
        private set
    private var a4Hz = DEFAULT_A4_HZ
    private var nameChords = false

    /** Every sudden sound is a strum of the open strings, not decided (the strum check's button). */
    var everySoundIsAStrum: Boolean = false
        private set

    // The salience of each note (by MIDI number) in the frame before the latest strum.
    private val beforeSalience = FloatArray(MIDI_NOTES)

    /** The latest strum's kind: null while it is being decided, or before any strum. */
    var kind: StrumKind? = null
        private set

    // The latest strum's undecided frames: what each said, and its reading, replayed once decided.
    private val candidates = arrayOfNulls<StrumKind>(DECIDE_READINGS)
    private var candidateCount = 0
    private val pendingStrings = Array(DECIDE_READINGS) { StrumResult(StrumDetector.MAX_STRINGS) }
    private val pendingChord = arrayOfNulls<Chord>(DECIDE_READINGS)
    private val pendingBass = arrayOfNulls<PitchClass>(DECIDE_READINGS)
    private val pendingLevel = FloatArray(DECIDE_READINGS)
    private val pendingSeconds = DoubleArray(DECIDE_READINGS)
    private var replayNext = 0

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

    /** Readings of the latest strum so far, decided ones only. */
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
     * Starts listening afresh: for strums of the open strings at [openStringsHz] (Hz, the tuning's order; null:
     * not looked for), chords named when [nameChords], notes on the grid at [a4Hz], frames sized for [range].
     * With [everySoundIsAStrum] (and open strings given) every sudden sound is a strum of them, nothing decided.
     * Whatever already rings is where the next strum must rise from.
     */
    fun start(
        openStringsHz: FloatArray?,
        a4Hz: Float,
        nameChords: Boolean,
        range: PitchRange = PitchRange.STANDARD,
        everySoundIsAStrum: Boolean = false,
    ) {
        require(a4Hz > 0f) { "a4Hz must be positive, was $a4Hz" }
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
        this.nameChords = nameChords
        stringCount = if (openStringsHz == null) 0 else minOf(openStringsHz.size, StrumDetector.MAX_STRINGS)
        for (i in 0 until stringCount) {
            openHz[i] = openStringsHz!![i]
            openMidi[i] = (A4_MIDI + SEMITONES_PER_OCTAVE * ln(openHz[i] / a4Hz.toDouble()) / LN2).roundToInt()
        }
        this.everySoundIsAStrum = everySoundIsAStrum && stringCount > 0
        samples = 0L
        lastStrumCount = 0
        lastRinging = false
        strumCount = 0
        kind = null
        candidateCount = 0
        replayNext = DECIDE_READINGS
        readingsSinceStrum = 0
        beforeSalience.fill(0f)
    }

    /** Takes samples like [StrumListener.push]: stops when a frame is ready, so call [next] after each push. */
    fun push(samples: FloatArray, offset: Int, length: Int): Int {
        val used = listener.push(samples, offset, length)
        this.samples += used
        return used
    }

    /** The next thing to report, or [Event.NOTHING]; call until it gives that. */
    fun next(): Event {
        if (replayNext < candidateCount) return replay()
        if (listener.strumCount != lastStrumCount) {
            lastStrumCount = listener.strumCount
            lastRinging = listener.isRinging
            // A rise before the strum's first frame is part of it (a slow strum, a quick arpeggio, low strings
            // beating): it is decided against what rang before its start.
            if (strumCount > 0 && readingsSinceStrum == 0 && (everySoundIsAStrum || kind == null && candidateCount == 0)) return next()
            strumCount++
            onStrum()
            // The strum check's button: the player said a strum comes, so it is one, read from its first frame.
            if (everySoundIsAStrum) kind = StrumKind.OPEN_STRINGS
            return Event.STRUM
        }
        if (listener.isFrameReady) {
            listener.readFrame(frame)
            val decided = kind
            if (decided == null) return look()
            if (decided == StrumKind.NONE) return next()
            read(decided)
            return Event.READING
        }
        if (listener.isRinging != lastRinging) {
            lastRinging = listener.isRinging
            if (!lastRinging) {
                // Too short to read (a knock) or not decided by the end: nothing.
                if (kind == null) kind = StrumKind.NONE
                replayNext = candidateCount
                return Event.QUIET
            }
        }
        return Event.NOTHING
    }

    private fun onStrum() {
        kind = null
        candidateCount = 0
        replayNext = DECIDE_READINGS
        readingsSinceStrum = 0
        listener.readBeforeStrum(frame)
        chordDetector.detect(frame, before, a4Hz)
        beforeSalience.fill(0f)
        for (n in 0 until before.noteCount) {
            val midi = before.noteMidi[n]
            beforeSalience[midi] = maxOf(beforeSalience[midi], before.noteSalience[n])
        }
    }

    // An undecided frame: what it says, kept for the decision and its replay.
    private fun look(): Event {
        val i = candidateCount
        chordDetector.detect(frame, notes, a4Hz)
        val said = candidate(pendingStrings[i])
        candidates[i] = said
        pendingChord[i] = notes.chord
        pendingBass[i] = notes.bass
        pendingLevel[i] = listener.frameLevelDbfs
        pendingSeconds[i] = seconds
        candidateCount++
        val decided = decide() ?: return next()
        kind = decided
        replayNext = if (decided == StrumKind.NONE) candidateCount else 0
        return Event.DECIDED
    }

    // The next undecided frame that agrees with the decision, as a reading.
    private fun replay(): Event {
        while (replayNext < candidateCount && candidates[replayNext] != kind) replayNext++
        if (replayNext == candidateCount) return next()
        val i = replayNext++
        if (kind == StrumKind.OPEN_STRINGS) copy(pendingStrings[i], strings)
        readingChord = pendingChord[i]
        readingBass = pendingBass[i]
        readingLevelDbfs = pendingLevel[i]
        readingSeconds = pendingSeconds[i]
        readingsSinceStrum++
        return Event.READING
    }

    private fun read(decided: StrumKind) {
        if (decided == StrumKind.OPEN_STRINGS) {
            strumDetector.detect(frame, openHz, stringCount, strings)
        } else {
            readingChord = chordDetector.detect(frame, notes, a4Hz)
            readingBass = notes.bass
        }
        readingLevelDbfs = listener.frameLevelDbfs
        readingSeconds = seconds
        readingsSinceStrum++
    }

    /** What the frame just put through [ChordDetector] into [notes] says; [open] receives [StrumDetector]'s reading if it ran. */
    private fun candidate(open: StrumResult): StrumKind {
        if (notes.crowded) return StrumKind.NONE
        var own = 0
        var allOpen = true
        for (n in 0 until notes.noteCount) {
            if (notes.noteStrength[n] < OWN_SHARE || onHarmonicOfAnother(n)) continue
            if (!struck(n)) return StrumKind.NONE
            own++
            if (!atOpenString(notes.noteMidi[n])) allOpen = false
        }
        if (stringCount > 0 && allOpen && own >= MIN_OPEN_NOTES) {
            strumDetector.detect(frame, openHz, stringCount, open)
            var heard = 0
            for (s in 0 until stringCount) if (open.present[s] && !open.cents[s].isNaN()) heard++
            if (heard >= MIN_OPEN_NOTES && heard * 2 >= stringCount) return StrumKind.OPEN_STRINGS
        }
        val chord = notes.chord
        // Two strings are a chord only as a power chord's root with its fifth above: a fourth (A over E, two
        // open strings side by side) is no chord here.
        if (nameChords && chord != null && own >= MIN_CHORD_NOTES && (own > MIN_CHORD_NOTES || notes.bass == chord.root)) return StrumKind.CHORD
        return StrumKind.NONE
    }

    private fun onHarmonicOfAnother(n: Int): Boolean {
        for (m in 0 until notes.noteCount) {
            if (ChordDetector.isHarmonicInterval(notes.noteMidi[n] - notes.noteMidi[m])) return true
        }
        return false
    }

    // Struck by this sound: well above what rang at its note (or a semitone either side) just before it.
    private fun struck(n: Int): Boolean {
        val midi = notes.noteMidi[n]
        var was = beforeSalience[midi]
        if (midi > 0) was = maxOf(was, beforeSalience[midi - 1])
        if (midi + 1 < MIDI_NOTES) was = maxOf(was, beforeSalience[midi + 1])
        return notes.noteSalience[n] >= STRUCK_RATIO * was
    }

    private fun atOpenString(midi: Int): Boolean {
        for (s in 0 until stringCount) if (midi == openMidi[s]) return true
        return false
    }

    /** The kind [DECIDE_WINS] of the frames so far agree on, none when [DECIDE_READINGS] couldn't, or null to wait. */
    private fun decide(): StrumKind? {
        for (k in KINDS) {
            var votes = 0
            for (i in 0 until candidateCount) if (candidates[i] == k) votes++
            if (votes >= DECIDE_WINS) return k
        }
        return if (candidateCount == DECIDE_READINGS) StrumKind.NONE else null
    }

    private fun copy(from: StrumResult, to: StrumResult) {
        to.stringCount = from.stringCount
        from.frequencyHz.copyInto(to.frequencyHz)
        from.cents.copyInto(to.cents)
        from.levelDb.copyInto(to.levelDb)
        from.present.copyInto(to.present)
        from.shadowed.copyInto(to.shadowed)
    }

    private fun chordDetectorFor(range: PitchRange, frameLength: Int) = ChordDetector(
        sampleRate,
        frameLength,
        lowestMidi = if (range == PitchRange.LOW) LOW_RANGE_LOWEST_MIDI else ChordDetector.DEFAULT_LOWEST_MIDI,
    )

    companion object {
        /** A note counts as a string of its own from this share of the strongest note's salience ([ChordDetector.SURE]). */
        const val OWN_SHARE = ChordDetector.SURE

        /** A note was struck by the sound when its salience is at least this many times what rang there just before (+3.5 dB). */
        const val STRUCK_RATIO = 1.5f

        /** A strum of open strings has at least this many strings of their own struck, and [StrumDetector] hears as many. */
        const val MIN_OPEN_NOTES = 3

        /** A chord has at least this many strings of their own (a power chord's root and fifth). */
        const val MIN_CHORD_NOTES = 2

        /** A strum's kind is decided by its first this many frames (0.1 s apart)… */
        const val DECIDE_READINGS = 3

        /** …when this many of them agree. */
        const val DECIDE_WINS = 2

        /** In the low range notes are looked for from B0 (a 5-string bass), not B1. */
        const val LOW_RANGE_LOWEST_MIDI = 23

        private const val DEFAULT_A4_HZ = 440f
        private const val A4_MIDI = 69
        private const val SEMITONES_PER_OCTAVE = 12
        private const val MIDI_NOTES = 128
        private val LN2 = ln(2.0)

        // Looped over by index, so deciding allocates no iterator.
        private val KINDS = StrumKind.entries.toTypedArray()
    }
}
