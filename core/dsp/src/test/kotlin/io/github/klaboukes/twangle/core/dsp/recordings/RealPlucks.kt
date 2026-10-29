package io.github.klaboukes.twangle.core.dsp.recordings

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.music.centsBetween
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.pitch.TunerChain
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a recorded string was played, as `scripts/record-plucks.sh` names the files. */
enum class Touch(val fileName: String, val label: String, val quietRoom: Boolean) {
    PICK("pick", "pick", true),
    FINGER("finger", "finger", true),
    PICK_NOISY("pick-noisy", "pick, noisy room", false),
}

/**
 * One recorded pluck of a real guitar from `src/test/resources/fixtures/`
 * (`guitar-<string>-<pick|finger|pick-noisy>-<take>.wav`), recorded through the tuner's own
 * recorder by `PluckRecorder` (docs/architecture/testing.md, *Real recordings*).
 */
internal class RealPluck(
    val name: String,
    /** The open string played. */
    val string: Note,
    val touch: Touch,
    val take: Int,
    val wav: WavFile,
) {
    val sampleRate: Int get() = wav.sampleRate
    val samples: FloatArray get() = wav.samples

    /**
     * Where the pluck starts, found from the signal: the player plucks some time after the phone
     * says "Pluck now", 0.1–2 s into the file.
     */
    val onsetSample: Int by lazy { RealPlucks.onsetOf(samples, sampleRate) }

    val onsetSeconds: Float get() = onsetSample / sampleRate.toFloat()

    /** The loudest sample, in dBFS. */
    val peakDbfs: Float get() = SignalGate.peakDbfs(samples)

    /** The room before the pluck (RMS of the 50 ms before the onset), in dBFS, or NaN with no room before it. */
    val roomDbfs: Float
        get() {
            val length = sampleRate / 20
            return if (onsetSample < length) Float.NaN else SignalGate.rmsDbfs(samples.copyOfRange(onsetSample - length, onsetSample))
        }

    private val settings: AnalysisSettings get() = AnalysisSettings.forSampleRate(sampleRate)

    /**
     * The whole chain's frames, at the file's own rate, the standard range; without the
     * refinement on the lowest partials to compare (what the whole waveform reads).
     */
    fun frames(refineOnLowestPartials: Boolean = true): List<TunerChain.Frame> =
        TunerChain(settings, refineOnLowestPartials = refineOnLowestPartials).run(samples)

    /**
     * What the tuner shows [seconds] after the pluck: the first frame whose window ends then or
     * later, or null past the end of the file.
     */
    fun shownAt(frames: List<TunerChain.Frame>, seconds: Float): TunerChain.Frame? {
        val at = onsetSample + (seconds * sampleRate).roundToInt()
        return frames.firstOrNull { it.endSample >= at }
    }

    /**
     * The settled reading: what is shown [SETTLED_SECONDS] after the pluck, or, where the file ends
     * or the string was muted before that, the last reading shown at least [SETTLED_AT_LEAST_SECONDS]
     * in; null when the string didn't ring that long.
     */
    fun settled(frames: List<TunerChain.Frame>): TunerChain.Frame? {
        val at = shownAt(frames, SETTLED_SECONDS)
        if (at != null && at.isShown) return at
        return frames.lastOrNull { it.isShown && secondsOf(it) >= SETTLED_AT_LEAST_SECONDS && secondsOf(it) <= SETTLED_SECONDS }
    }

    /** Seconds after the onset at which [frame]'s window ends. */
    fun secondsOf(frame: TunerChain.Frame): Float = (frame.endSample - onsetSample) / sampleRate.toFloat()

    /** The first frame shown after the onset, or null when nothing is. */
    fun firstShown(frames: List<TunerChain.Frame>): TunerChain.Frame? = frames.firstOrNull { it.isShown && secondsOf(it) >= 0f }

    /**
     * When the needle settles: seconds after the onset from which every shown reading up to the
     * [settled] one stays within [withinCents] of it (the first frame of that final stretch), or
     * NaN without a settled reading. A string's own glide counts: a hard pick on a low string
     * settles with its partials, over a second or two.
     */
    fun settleSeconds(frames: List<TunerChain.Frame>, withinCents: Float): Float {
        val settled = settled(frames) ?: return Float.NaN
        val shown = frames.filter { it.isShown && secondsOf(it) >= 0f && it.endSample <= settled.endSample }
        var from = shown.size - 1
        while (from > 0 && abs(NoteMapper.centsBetween(shown[from - 1].shownHz, settled.shownHz)) <= withinCents) from--
        return secondsOf(shown[from])
    }

    /**
     * The string's own fundamental, partial 1 alone, in the window ending [seconds] after the pluck:
     * the recording low-passed at 1.3 × the nominal pitch, twice (8th order, so a 2nd partial louder
     * than the fundamental, as a low string's is through a phone, is 30 dB down), read by YIN, which
     * on a lone partial is exact to about 0.1 cent. NaN when nothing is found or the window runs
     * past the file. One reference for the tuner's reading: a real string glides, and its lowest
     * partials sit a few cents from where its upper partials point ([Partials.seriesHz]).
     */
    fun fundamentalAt(seconds: Float): Float {
        val end = onsetSample + (seconds * sampleRate).roundToInt()
        val window = settings.windowSize
        if (end > samples.size || end < window) return Float.NaN
        val detector = settings.newPitchDetector(refineOnLowestPartials = false)
        val result = detector.detect(fundamentalOnly.copyOfRange(end - window, end))
        return if (result.isPitched) result.frequencyHz else Float.NaN
    }

    private val fundamentalOnly: FloatArray by lazy {
        val cutoff = 1.3f * RealPlucks.nominalHz(string)
        TestSignals.bandLimit(TestSignals.bandLimit(samples, sampleRate, lowHz = 0f, highHz = cutoff), sampleRate, lowHz = 0f, highHz = cutoff)
    }

    override fun toString(): String = name

    companion object {
        /** When a pluck counts as settled: the roadmap's "2 s in". */
        const val SETTLED_SECONDS = 2f

        /** A take whose string stopped before 2 s still counts as settled from here. */
        const val SETTLED_AT_LEAST_SECONDS = 1.5f
    }
}

/** The real recordings, read once for every test and report. */
internal object RealPlucks {

    private val mapper = NoteMapper()

    /** Every take, low string first, then pick, finger, noisy, then by take. */
    val all: List<RealPluck> by lazy { load() }

    /** The quiet-room takes, what the accuracy test asserts on. */
    val quietRoom: List<RealPluck> get() = all.filter { it.touch.quietRoom }

    /** The strings the session records: the standard tuning, low E to high E. */
    val strings: List<Note> = Tunings.STANDARD.strings

    /** The open string's frequency at A4 = 440 Hz. */
    fun nominalHz(string: Note): Float = mapper.frequencyOf(string)

    /**
     * The sample at which the pluck starts, from the signal. An attack is a rise of [ATTACK_RISE_DB]
     * or more in 10 ms (RMS of 10 ms blocks every 5 ms) to within [LOUD_DB] of the file's loudest
     * block: a pluck rises 15–40 dB in 10 ms, where a voice or the TV in the noisy room, the tap on
     * the phone before the pluck (20 dB under it) and the previous take's string still ringing
     * (25 dB under) rise less or stay too quiet. The pluck is the attack that rings longest, until
     * the level falls [RINGING_DB] under the attack's own peak or the file ends: a first try the
     * player muted loses to the pluck after it, and in the noisy room, where nothing dies away, the
     * first attack wins. The onset is that attack's foot, walking back while the level still falls.
     * The 50 ms of digital silence the recorder starts with are no rise.
     */
    fun onsetOf(samples: FloatArray, sampleRate: Int): Int {
        val block = sampleRate / 100
        val hop = block / 2
        val count = (samples.size - block) / hop + 1
        val level = FloatArray(count) { SignalGate.rmsDbfs(samples, hop * it, block) }
        val loudest = level.max()
        var best = -1
        var bestRinging = -1
        var i = 2
        while (i < count) {
            val rise = if (level[i - 2] > SignalGate.SILENCE_DBFS) level[i] - level[i - 2] else 0f
            if (rise < ATTACK_RISE_DB || level[i] < loudest - LOUD_DB) {
                i++
                continue
            }
            // This attack's peak (within 100 ms) and how long the sound stays within RINGING_DB of it.
            val peak = (i until minOf(count, i + 20)).maxOf { level[it] }
            var end = i
            while (end < count && level[end] > peak - RINGING_DB) end++
            if (end - i > bestRinging) {
                bestRinging = end - i
                best = i
            }
            i = maxOf(i + 1, end)
        }
        require(best > 0) { "no attack found" }
        var foot = best
        while (foot > 0 && level[foot - 1] < level[foot] && level[foot - 1] > SignalGate.SILENCE_DBFS) foot--
        return hop * foot
    }

    /** A rise of this much in 10 ms is an attack. */
    private const val ATTACK_RISE_DB = 12f

    /** An attack counts only within this of the file's loudest 10 ms. */
    private const val LOUD_DB = 10f

    /** A pluck rings until its level has fallen this far under its peak. */
    private const val RINGING_DB = 25f

    private fun load(): List<RealPluck> {
        val folder = File(requireNotNull(RealPlucks::class.java.getResource("/fixtures")) { "no fixtures folder on the class path" }.toURI())
        val files = folder.listFiles { file -> file.name.startsWith("guitar-") && file.name.endsWith(".wav") }.orEmpty()
        val plucks = files.map { file ->
            val match = requireNotNull(NAME.matchEntire(file.name)) { "unexpected fixture name ${file.name}" }
            val (stringName, touchName, take) = match.destructured
            val string = requireNotNull(strings.firstOrNull { it.toString() == stringName }) { "$stringName is no open string" }
            val touch = requireNotNull(Touch.entries.firstOrNull { it.fileName == touchName }) { "unknown touch $touchName" }
            RealPluck(file.name.removeSuffix(".wav"), string, touch, take.toInt(), WavFile.read(file.inputStream()))
        }
        return plucks.sortedWith(compareBy({ it.string.midi }, { it.touch }, { it.take }))
    }

    private val NAME = Regex("""guitar-([A-G]#?\d)-(pick|finger|pick-noisy)-(\d+)\.wav""")
}

/** RMS level of [length] samples from [from] in dBFS, as [SignalGate.rmsDbfs] measures it. */
private fun SignalGate.Companion.rmsDbfs(samples: FloatArray, from: Int, length: Int): Float =
    rmsDbfs(samples.copyOfRange(from, from + length))
