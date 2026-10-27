package io.github.klaboukes.twangle.core.dsp.synth

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One resonance of an instrument's body. Partials near [hz] come out [gainDb] louder than the
 * string alone would give them (the peak of a resonance of quality [q]: the higher, the narrower),
 * and a pluck sets it ringing for a moment, the knock of wood heard at the start of a note.
 */
data class BodyMode(val hz: Double, val q: Double, val gainDb: Double) {

    init {
        require(hz > 0 && q > 0) { "A body mode needs a positive frequency and q, was $hz Hz, q $q" }
    }

    /**
     * How much the resonance raises a partial at [frequencyHz]: 10^([gainDb]/20) at [hz], falling
     * to 1 away from it along the magnitude curve of a second-order resonance (Fletcher & Rossing,
     * *The Physics of Musical Instruments*, 2nd ed. 1998, §1.2).
     */
    fun gainAt(frequencyHz: Double): Double {
        val x = frequencyHz / hz
        val detuning = 1.0 - x * x
        val shape = 1.0 / sqrt(q * q * detuning * detuning + x * x) // 1 at hz, 1/q at 0, → 0 far above
        return 1.0 + (10.0.pow(gainDb / 20) - 1.0) * shape
    }
}

/**
 * How an instrument's string sounds: everything [StringSynth] needs to make a note of it that
 * sounds like the instrument, not like a keyboard. The presets are in [StringVoices].
 *
 * **The string's own spectrum.** A string plucked at fraction [pluckPoint] of its length from the
 * bridge gives partial k a level ∝ |sin(kπβ)| / k^[tilt] (Fletcher & Rossing, ch. 2: the comb
 * `sin(kπβ)` is the pluck point, the tilt is 1 for the force a plectrum's sharp pluck puts on the
 * bridge and nearer 2 for a soft fingertip). A pickup at [pickupPoint] adds its own comb. Above
 * [brightnessHz] the partials roll off (two poles), as the finite width of a pick, a finger or a
 * bow rounds the string's corner. The body then colours it: every [body] mode multiplies in its
 * [BodyMode.gainAt]. A bowed string ([bowed]) has no pluck comb: Helmholtz motion puts a sawtooth's
 * force on the bridge whatever the bow's position, so its partials sit at 1/k before the body.
 *
 * **Stiffness.** A real string is stiff, so partial k sits at `k·f·√(1 + B·k²)` (Fletcher 1964), the
 * shimmer of steel strings. [stiffness] is B for a string at [stiffnessHz]; thicker, lower strings
 * are stiffer, so B grows as the pitch falls ([stiffnessAt]). Nylon strings have none that matters.
 * The fundamental is kept exactly on pitch, so the reference stays true.
 *
 * **Decay.** A partial's level falls exponentially with a time constant that shrinks as its
 * frequency rises, [sustainSeconds] at 100 Hz and [trebleSustainSeconds] at 2 kHz (1/τ linear in
 * frequency between them): a note starts bright and mellows. The string swings in two planes that
 * lose energy to the bridge at different rates (Weinreich 1977), so a share [fastShare] of every
 * partial dies [fastRatio] times as fast as the rest: the quick fall then the long sing of a real
 * string, instead of one straight exponential.
 *
 * **Courses.** A mandolin's [courses] of two strings never agree to the cent: the pair sit
 * [courseSpreadCents] apart, evenly around the pitch, and their beating is the instrument's shimmer.
 * A 12-string's lower pairs have their second string an octave up ([StringSynth.start] with
 * `octavePair`): a thinner, lighter string at [octaveStringLevel] of the main one's level, half
 * [courseSpreadCents] sharp of the exact octave, the main string exactly on pitch.
 *
 * **The start.** A hard pluck stretches the string, so a plucked note begins [glideCents] sharp and
 * settles with time constant [glideSeconds] (nylon stretches most). A plectrum or finger scrapes
 * for a moment: highpassed noise above [attackNoiseHz] at level [attackNoise], dying over
 * [attackNoiseSeconds], and the same burst sets the body modes ringing at level [bodyThump]. A bowed
 * note swells in over [attackSeconds] with the bow's noise under it: lowpassed below [bowNoiseHz],
 * at [bowNoise], louder while the bow catches, coloured by the body, and the bow's pressure sways
 * the level by up to ±[sway] (a fraction) a few times a second, so it never stands still like an organ.
 */
data class StringVoice(
    val id: String,
    val bowed: Boolean = false,
    val pluckPoint: Double = 0.0,
    val pickupPoint: Double = 0.0,
    val tilt: Double = 1.0,
    val brightnessHz: Double = 4_000.0,
    val stiffness: Double = 0.0,
    val stiffnessHz: Double = 100.0,
    val sustainSeconds: Double = 1.5,
    val trebleSustainSeconds: Double = 0.3,
    val fastShare: Double = 0.0,
    val fastRatio: Double = 0.3,
    val body: List<BodyMode> = emptyList(),
    val courses: Int = 1,
    val courseSpreadCents: Double = 0.0,
    val octaveStringLevel: Double = 1.0,
    val glideCents: Double = 0.0,
    val glideSeconds: Double = 0.04,
    val attackNoise: Double = 0.0,
    val attackNoiseSeconds: Double = 0.003,
    val attackNoiseHz: Double = 2_000.0,
    val bodyThump: Double = 0.0,
    val attackSeconds: Double = 0.004,
    val bowNoise: Double = 0.0,
    val bowNoiseHz: Double = 4_000.0,
    val sway: Double = 0.0,
) {

    init {
        require(id.isNotBlank()) { "A voice needs an id" }
        require(pluckPoint in 0.0..0.5 && pickupPoint in 0.0..0.5) { "$id: pluck and pickup points are fractions of the string up to 0.5" }
        require(tilt >= 0 && brightnessHz > 0) { "$id: tilt and brightness must be positive" }
        require(stiffness >= 0 && stiffnessHz > 0) { "$id: stiffness must be 0 or more, at a positive frequency" }
        require(sustainSeconds > 0 && trebleSustainSeconds > 0) { "$id: sustain must be positive" }
        require(fastShare >= 0 && fastShare < 1 && fastRatio > 0 && fastRatio <= 1) { "$id: fastShare must be below 1 and fastRatio in (0, 1]" }
        require(courses in 1..StringSynth.MAX_COURSES && courseSpreadCents >= 0) { "$id: 1 or 2 courses, spread 0 or more" }
        require(octaveStringLevel > 0 && octaveStringLevel <= 1) { "$id: the octave string's level must be in (0, 1]" }
        require(glideCents >= 0 && glideSeconds > 0) { "$id: glide must be 0 or more over a positive time" }
        require(attackNoise >= 0 && attackNoiseSeconds > 0 && attackNoiseHz > 0 && bodyThump >= 0) { "$id: attack noise levels must be 0 or more" }
        require(attackSeconds > 0 && bowNoise >= 0 && bowNoiseHz > 0 && sway in 0.0..0.5) { "$id: attack, bow noise and sway out of range" }
        require(body.size <= StringSynth.MAX_MODES) { "$id: at most ${StringSynth.MAX_MODES} body modes" }
    }

    /**
     * The inharmonicity B of this instrument's string at [frequencyHz]: [stiffness] at
     * [stiffnessHz], growing as the pitch falls (`(stiffnessHz / f)^1.45`, the slope of the guitar's
     * measured strings: 1.5·10⁻⁴ on the low E, 2·10⁻⁵ on the high E two octaves up), never above
     * [MAX_STIFFNESS].
     */
    fun stiffnessAt(frequencyHz: Double): Double =
        if (stiffness == 0.0) 0.0 else minOf(MAX_STIFFNESS, stiffness * (stiffnessHz / frequencyHz).pow(STIFFNESS_SLOPE))

    /**
     * Partial [k]'s starting level at [partialHz], relative to the others: comb, tilt, brightness
     * and body. The comb keeps its sign, so the partials start with the phases of a real pluck: a
     * pulse on the bridge once a period (the snap of a pick), not a sawtooth.
     */
    fun partialLevel(k: Int, partialHz: Double): Double {
        var level = 1.0 / k.toDouble().pow(tilt)
        if (pluckPoint > 0) level *= sin(k * PI * pluckPoint)
        if (pickupPoint > 0) level *= sin(k * PI * pickupPoint)
        val x = partialHz / brightnessHz
        level /= sqrt(1.0 + x * x * x * x)
        for (i in body.indices) level *= body[i].gainAt(partialHz) // by index: no iterator, so start() allocates nothing
        return level
    }

    /** Time constant of a partial's decay at [partialHz] (its slower plane), from [sustainSeconds] and [trebleSustainSeconds]. */
    fun decaySecondsAt(partialHz: Double): Double {
        val f = partialHz.coerceAtLeast(LOW_HZ)
        val rate = 1.0 / sustainSeconds + (f - LOW_HZ) * (1.0 / trebleSustainSeconds - 1.0 / sustainSeconds) / (HIGH_HZ - LOW_HZ)
        return (1.0 / rate).coerceIn(MIN_DECAY_SECONDS, MAX_DECAY_SECONDS)
    }

    override fun toString(): String = "StringVoice($id)"

    companion object {
        /** [sustainSeconds] is the decay time constant at this frequency. */
        const val LOW_HZ = 100.0

        /** [trebleSustainSeconds] is the decay time constant at this frequency. */
        const val HIGH_HZ = 2_000.0

        const val STIFFNESS_SLOPE = 1.45

        /**
         * B never grows past this on the lowest strings: a reference tone keeps its partials closer to
         * harmonic than a heavy 8-string or 5-string bass string (which the detector reads sharp by
         * several cents early in the note, see ROADMAP Phase 46), yet with a steel string's shimmer.
         */
        const val MAX_STIFFNESS = 8e-5
        const val MIN_DECAY_SECONDS = 0.02
        const val MAX_DECAY_SECONDS = 20.0
    }
}

/**
 * The voices of the instruments the tuner knows ([io.github.klaboukes.twangle.core.dsp.music.Instruments]).
 * The body modes are the main resonances measured on fine instruments of each kind (the air
 * resonance A0, the top's B1− and B1+, and the bridge hill of the violin family), rounded.
 */
object StringVoices {

    /** A steel-string dreadnought, picked over the sound hole: A0 near 98 Hz, the top near 196 Hz. */
    val STEEL_GUITAR = StringVoice(
        id = "steel_guitar",
        pluckPoint = 0.17,
        tilt = 1.0,
        brightnessHz = 4_500.0,
        stiffness = 4e-5,
        stiffnessHz = 82.4,
        sustainSeconds = 1.6,
        trebleSustainSeconds = 0.3,
        fastShare = 0.45,
        fastRatio = 0.3,
        body = listOf(BodyMode(98.0, 15.0, 8.0), BodyMode(196.0, 18.0, 6.0), BodyMode(400.0, 6.0, 3.0), BodyMode(2_600.0, 1.6, 3.0)),
        glideCents = 4.0,
        glideSeconds = 0.035,
        attackNoise = 0.05,
        attackNoiseSeconds = 0.0025,
        attackNoiseHz = 2_500.0,
        bodyThump = 0.4,
    )

    /** An electric bass played with the fingers over the pickup, through a warm, rolled-off amplifier. */
    val ELECTRIC_BASS = StringVoice(
        id = "electric_bass",
        pluckPoint = 0.22,
        pickupPoint = 0.27,
        tilt = 1.3,
        brightnessHz = 2_200.0,
        stiffness = 6e-5,
        stiffnessHz = 41.2,
        sustainSeconds = 2.4,
        trebleSustainSeconds = 0.35,
        fastShare = 0.35,
        fastRatio = 0.35,
        body = listOf(BodyMode(95.0, 1.2, 2.5)),
        glideCents = 5.0,
        glideSeconds = 0.045,
        attackNoise = 0.02,
        attackNoiseSeconds = 0.005,
        attackNoiseHz = 1_200.0,
        bodyThump = 0.15,
    )

    /** A concert ukulele with nylon strings, plucked with a fingertip: short, warm, a quick two-stage decay. */
    val UKULELE = StringVoice(
        id = "ukulele",
        pluckPoint = 0.26,
        tilt = 1.6,
        brightnessHz = 2_800.0,
        sustainSeconds = 1.4,
        trebleSustainSeconds = 0.14,
        fastShare = 0.5,
        fastRatio = 0.3,
        body = listOf(BodyMode(330.0, 10.0, 7.0), BodyMode(560.0, 12.0, 5.0), BodyMode(1_100.0, 5.0, 3.0)),
        glideCents = 7.0,
        glideSeconds = 0.05,
        attackNoise = 0.03,
        attackNoiseSeconds = 0.004,
        attackNoiseHz = 1_800.0,
        bodyThump = 0.35,
    )

    /** A mandolin's pair of steel strings, picked near the bridge: bright, short, shimmering. */
    val MANDOLIN = StringVoice(
        id = "mandolin",
        pluckPoint = 0.1,
        tilt = 1.0,
        brightnessHz = 5_000.0,
        stiffness = 3e-5,
        stiffnessHz = 196.0,
        sustainSeconds = 1.2,
        trebleSustainSeconds = 0.3,
        fastShare = 0.35,
        fastRatio = 0.35,
        body = listOf(BodyMode(300.0, 12.0, 6.0), BodyMode(560.0, 10.0, 5.0), BodyMode(1_800.0, 3.0, 3.0)),
        courses = 2,
        courseSpreadCents = 2.4,
        glideCents = 2.0,
        glideSeconds = 0.03,
        attackNoise = 0.06,
        attackNoiseSeconds = 0.002,
        attackNoiseHz = 3_000.0,
        bodyThump = 0.3,
    )

    /**
     * A 12-string: the steel guitar's pick and body with every course a pair, the top two in unison
     * 2 cents apart (a 12-string's chorus), the lower four with a thinner string an octave up, a
     * little quieter than the main one.
     */
    val TWELVE_STRING = STEEL_GUITAR.copy(
        id = "twelve_string",
        courses = 2,
        courseSpreadCents = 2.0,
        octaveStringLevel = 0.6,
    )

    /** A violin bowed on an open string: A0 near 275 Hz, B1− and B1+ near 450 and 540 Hz, the bridge hill near 2.5 kHz. */
    val VIOLIN = StringVoice(
        id = "violin",
        bowed = true,
        brightnessHz = 5_000.0,
        sustainSeconds = 0.35,
        trebleSustainSeconds = 0.12,
        body = listOf(BodyMode(275.0, 12.0, 9.0), BodyMode(450.0, 14.0, 6.0), BodyMode(540.0, 14.0, 8.0), BodyMode(2_500.0, 1.5, 7.0)),
        attackSeconds = 0.09,
        bowNoise = 0.012,
        bowNoiseHz = 5_000.0,
        sway = 0.06,
    )

    /** A viola: the violin's resonances about a fifth lower in a body not much bigger, hence its darker, nasal tone. */
    val VIOLA = StringVoice(
        id = "viola",
        bowed = true,
        brightnessHz = 4_200.0,
        sustainSeconds = 0.4,
        trebleSustainSeconds = 0.12,
        body = listOf(BodyMode(220.0, 12.0, 9.0), BodyMode(370.0, 14.0, 6.0), BodyMode(430.0, 14.0, 8.0), BodyMode(2_000.0, 1.5, 6.0)),
        attackSeconds = 0.1,
        bowNoise = 0.012,
        bowNoiseHz = 4_500.0,
        sway = 0.06,
    )

    /** A cello: A0 near 100 Hz, the top's modes near 165 and 205 Hz, the bridge hill near 1.1 kHz, thick strings that round the bow's corner. */
    val CELLO = StringVoice(
        id = "cello",
        bowed = true,
        brightnessHz = 3_000.0,
        sustainSeconds = 0.5,
        trebleSustainSeconds = 0.15,
        body = listOf(BodyMode(100.0, 10.0, 9.0), BodyMode(165.0, 12.0, 6.0), BodyMode(205.0, 12.0, 7.0), BodyMode(1_100.0, 1.5, 6.0)),
        attackSeconds = 0.12,
        bowNoise = 0.012,
        bowNoiseHz = 3_500.0,
        sway = 0.06,
    )

    /** Every voice, each used by at least one instrument. */
    val all: List<StringVoice> = listOf(STEEL_GUITAR, TWELVE_STRING, ELECTRIC_BASS, UKULELE, MANDOLIN, VIOLIN, VIOLA, CELLO)

    /** The voice of the default instrument, the six-string guitar. */
    val DEFAULT: StringVoice = STEEL_GUITAR
}
