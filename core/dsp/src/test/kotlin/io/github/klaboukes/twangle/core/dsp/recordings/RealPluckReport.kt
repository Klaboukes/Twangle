package io.github.klaboukes.twangle.core.dsp.recordings

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.centsBetween
import io.github.klaboukes.twangle.core.dsp.music.nearestNote
import io.github.klaboukes.twangle.core.dsp.pitch.TunerChain
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the tuner shows for every real pluck in the fixtures (docs/architecture/testing.md, *Real
 * recordings*), and how the real strings' partials compare with the test models. Prints only, asserts
 * nothing (that is [RealPluckTest]'s job); the numbers behind docs/architecture/signal-pipeline.md,
 * *Accuracy target*. Not part of `test`; run with `./gradlew :core:dsp:benchmark --tests '*RealPluckReport*'`.
 */
internal class RealPluckReport {

    private val mapper = NoteMapper()

    @Test
    fun trajectories() {
        println("=== Every real pluck through the app's chain (TunerChain at the file's rate). Cents from the string's nominal note")
        println("    at seconds after the pluck's onset (found from the signal); another note shown as its name; '.' nothing shown;")
        println("    'end' past the file. Three rows a take: 'shown', what the tuner shows; 'partial 1', the string's own fundamental")
        println("    (the recording low-passed at 1.3 × the pitch, twice, read by YIN: exact on a lone partial, but a real string's")
        println("    partial 1 beats between its two planes of vibration, so single frames stray); 'whole', the chain without the")
        println("    refinement on the lowest partials. first: when the first reading shows after the onset, and what. settles:")
        println("    from when every reading stays within ${SETTLE_WITHIN_CENTS} cents of the settled one (RealPlucks.settleSeconds). settled:")
        println("    the reading 2 s in (or the last one shown from 1.5 s, where the file ends or the string was muted first).")
        println("    |0.5s−settled|: the roadmap's figure, 2 cents asked; worst: the most any reading from 0.5 s to the settled one")
        println("    strays from it (RealPluckTest's limits). wrong: shown frames with another note, and the first of them;")
        println("    multiples: frames the detector read a whole number of periods from the string, which the smoother held.")
        println()
        val header = "%-26s %-9s %5s %6s %6s %15s %7s".format("take", "", "onset", "peak", "room", "first", "settles")
        println(header + TIMES.joinToString("") { "%8s".format("${it}s") } + "  settled        |0.5s−settled| worst  wrong")
        for (pluck in RealPlucks.all) {
            val frames = pluck.frames()
            val whole = pluck.frames(refineOnLowestPartials = false)
            val nominal = RealPlucks.nominalHz(pluck.string)
            val first = frames.firstOrNull { it.isShown }
            val firstText = if (first == null) "never" else "%.3f %s".format(pluck.secondsOf(first), cell(first, nominal, pluck))
            val wrongFrames = frames.filter { it.isShown && mapper.nearestNote(it.shownHz) != pluck.string }
            val multiples = frames.filter { it.rawHz > 0f && pluck.secondsOf(it) >= 0f && mapper.nearestNote(it.rawHz) != pluck.string }
            val wrongText = (if (wrongFrames.isEmpty()) "0" else {
                val w = wrongFrames.first()
                "%d, first at %.2f s: %s (raw %.1f Hz)".format(wrongFrames.size, pluck.secondsOf(w), mapper.nearestNote(w.shownHz), w.rawHz)
            }) + if (multiples.isEmpty()) "" else {
                val m = multiples.first()
                "; multiples %d (%s from %.2f s)".format(multiples.size, multiples.map { mapper.nearestNote(it.rawHz) }.distinct().joinToString(" "), pluck.secondsOf(m))
            }
            val settleText = pluck.settleSeconds(frames, SETTLE_WITHIN_CENTS).let { if (it.isNaN()) "-" else "%.2f".format(it) }
            val settled = pluck.settled(frames)
            val settledText = if (settled == null) "-" else "%s at %.2f s".format(cell(settled, nominal, pluck), pluck.secondsOf(settled))
            val half = pluck.shownAt(frames, 0.5f)
            val delta = if (settled == null || half == null || !half.isShown) "-" else "%.1f".format(abs(NoteMapper.centsBetween(half.shownHz, settled.shownHz)))
            val worst = if (settled == null) "-" else {
                frames.filter { it.isShown && pluck.secondsOf(it) >= 0.5f && it.endSample <= settled.endSample }
                    .maxOfOrNull { abs(NoteMapper.centsBetween(it.shownHz, settled.shownHz)) }?.let { "%.1f".format(it) } ?: "-"
            }
            println(
                "%-26s %-9s %5.2f %6.1f %6.1f %15s %7s".format(pluck.name, "shown", pluck.onsetSeconds, pluck.peakDbfs, pluck.roomDbfs, firstText, settleText) +
                    TIMES.joinToString("") { "%8s".format(cell(pluck.shownAt(frames, it), nominal, pluck)) } +
                    "  %-14s %6s %6s  %s".format(settledText, delta, worst, wrongText),
            )
            println(
                "%-26s %-9s %5s %6s %6s %15s %7s".format("", "partial 1", "", "", "", "", "") +
                    TIMES.joinToString("") { "%8s".format(centsCell(pluck.fundamentalAt(it), nominal, pluck)) },
            )
            println(
                "%-26s %-9s %5s %6s %6s %15s %7s".format("", "whole", "", "", "", "", "") +
                    TIMES.joinToString("") { "%8s".format(cell(pluck.shownAt(whole, it), nominal, pluck)) },
            )
        }
        println()
        println("The noisy room: when the needle last showed the string, and what came after (held, then blank, is the aim):")
        for (pluck in RealPlucks.all.filter { !it.touch.quietRoom }) {
            val frames = pluck.frames()
            val last = frames.lastOrNull { it.isShown }
            val after = if (last == null) emptyList() else frames.filter { it.endSample > last.endSample && it.rawHz > 0f }
            println(
                "  %-26s last shown %s; after it the detector found a pitch in %d frames (%s)".format(
                    pluck.name, if (last == null) "never" else "at %.2f s".format(pluck.secondsOf(last)), after.size,
                    after.map { mapper.nearestNote(it.rawHz) }.distinct().joinToString(" ").ifEmpty { "-" },
                ),
            )
        }
        println()
        println("Frames after 0.5 s that are not shown (the needle gone while the string rings), per take:")
        for (pluck in RealPlucks.all) {
            val frames = pluck.frames()
            val after = frames.filter { pluck.secondsOf(it) >= 0.5f }
            val gaps = after.count { !it.isShown }
            if (gaps > 0) {
                val from = after.first { !it.isShown }
                println("  %-26s %d of %d frames, the first at %.2f s (gate %s, sustained %s, raw %.1f Hz)".format(pluck.name, gaps, after.size, pluck.secondsOf(from), from.gateOpen, from.sustained, from.rawHz))
            }
        }
    }

    @Test
    fun partials() {
        println("=== The real strings' partials against the test models (StrumChords.stiffStrum, EdgeCaseTest, IntonationTest).")
        println("    Level of partial k relative to partial 1 in dB, in a ${Partials.DEFAULT_WINDOW}-sample window from ${PARTIAL_TIMES[0]} s after the onset;")
        println("    drop: how much lower it is in the window from ${PARTIAL_TIMES[1]} s. B: inharmonicity of the stiff-string series fitted")
        println("    through partials 3 and up (Fletcher 1964); f1: partial 1 in cents from the nominal note; series: that series'")
        println("    own fundamental, where the upper partials say the string is (a real guitar's lowest partials sit a few cents")
        println("    from it, pulled by the body's resonances), at ${PARTIAL_TIMES[0]} s and at ${PARTIAL_TIMES[1]} s.")
        println("    Models: a pick starts partial k at 1/k (%s), a finger at 1/k² (%s);".format(modelRow(Partials::pickModelDb), modelRow(Partials::fingerModelDb)))
        println("    the model's drop over that time, partial k at τ = 1.5 s / (1 + (k − 1)/2): %s.".format((1..SHOWN_PARTIALS).joinToString(" ") { "%.0f".format(modelDropDb(it)) }))
        println("    Model B, low E to high E: ${Partials.MODEL_B.joinToString(" ") { "%.0e".format(it) }}.")
        println()
        println(
            "%-26s %-${6 * SHOWN_PARTIALS}s  %-${4 * SHOWN_PARTIALS}s  %8s %7s  %6s %6s  %6s %6s".format(
                "take", "level k=2..${SHOWN_PARTIALS} (dB rel. k=1)", "drop (dB)", "B", "B/model", "f1", "series", "f1", "series",
            ),
        )
        val byGroup = LinkedHashMap<String, MutableList<Partials>>()
        val bByGroup = LinkedHashMap<String, MutableList<Double>>()
        for (pluck in RealPlucks.all) {
            val early = measure(pluck, PARTIAL_TIMES[0])
            val late = measure(pluck, PARTIAL_TIMES[1])
            val stringIndex = RealPlucks.strings.indexOf(pluck.string)
            val nominal = RealPlucks.nominalHz(pluck.string)
            val b = early.inharmonicity
            val levels = (2..SHOWN_PARTIALS).joinToString("") { k -> "%6s".format(fmt(early.relativeDb(k))) }
            val drops = (1..SHOWN_PARTIALS).joinToString("") { k -> "%4s".format(fmt(early.dbfs[k - 1] - late.dbfs[k - 1], 0)) }
            println(
                "%-26s %s  %s  %8s %7s  %6s %6s  %6s %6s".format(
                    pluck.name, levels, drops, if (b.isNaN()) "-" else "%.1e".format(b),
                    if (b.isNaN()) "-" else "%.1f".format(b / Partials.MODEL_B[stringIndex]),
                    centsOf(early.hz[0], nominal), centsOf(early.seriesHz, nominal), centsOf(late.hz[0], nominal), centsOf(late.seriesHz, nominal),
                ),
            )
            val group = "${pluck.string} ${pluck.touch.label}"
            byGroup.getOrPut(group) { ArrayList() } += early
            if (!b.isNaN()) bByGroup.getOrPut(group) { ArrayList() } += b
        }
        println()
        println("Per string and touch, the mean level of partial k relative to partial 1 at ${PARTIAL_TIMES[0]} s, against the model it is tested with:")
        println("%-22s %-${6 * SHOWN_PARTIALS}s  %-${6 * SHOWN_PARTIALS}s  %10s %10s".format("string, touch", "measured k=2..$SHOWN_PARTIALS", "measured − model", "mean B", "model B"))
        for ((group, list) in byGroup) {
            val string = RealPlucks.strings.first { group.startsWith("$it ") }
            val pick = !group.endsWith("finger")
            val model: (Int) -> Double = if (pick) Partials::pickModelDb else Partials::fingerModelDb
            val means = (2..SHOWN_PARTIALS).map { k -> list.map { it.relativeDb(k) }.filter { !it.isNaN() }.let { if (it.isEmpty()) Double.NaN else it.average() } }
            val meanB = bByGroup[group]?.average() ?: Double.NaN
            println(
                "%-22s %s  %s  %10s %10.1e".format(
                    group,
                    means.joinToString("") { "%6s".format(fmt(it)) },
                    means.withIndex().joinToString("") { (i, m) -> "%6s".format(fmt(m - model(i + 2))) },
                    if (meanB.isNaN()) "-" else "%.1e".format(meanB),
                    Partials.MODEL_B[RealPlucks.strings.indexOf(string)],
                ),
            )
        }
    }

    private fun measure(pluck: RealPluck, secondsAfterOnset: Double): Partials =
        Partials(pluck.samples, pluck.sampleRate, pluck.onsetSample + (secondsAfterOnset * pluck.sampleRate).roundToInt(), RealPlucks.nominalHz(pluck.string))

    private fun modelRow(model: (Int) -> Double) = (2..SHOWN_PARTIALS).joinToString(" ") { "%.0f".format(model(it)) }

    // StrumChords.stiffStrum: partial k decays with τ = 1.5 s / (1 + (k − 1) / 2).
    private fun modelDropDb(k: Int): Double {
        val tau = 1.5 / (1 + (k - 1) / 2.0)
        return 20 * kotlin.math.log10(Math.E) * (PARTIAL_TIMES[1] - PARTIAL_TIMES[0]) / tau
    }

    private fun fmt(value: Double, decimals: Int = 1): String = if (value.isNaN()) "-" else "%.${decimals}f".format(value)

    private fun centsOf(hz: Double, nominalHz: Float): String =
        if (hz.isNaN() || hz <= 0.0) "-" else "%+.1f".format(NoteMapper.centsBetween(hz.toFloat(), nominalHz))

    // The shown reading as cents from [nominalHz] when it is the string's note, else the note shown.
    private fun cell(frame: TunerChain.Frame?, nominalHz: Float, pluck: RealPluck): String = when {
        frame == null -> "end"
        !frame.isShown -> "."
        else -> centsCell(frame.shownHz, nominalHz, pluck)
    }

    private fun centsCell(hz: Float, nominalHz: Float, pluck: RealPluck): String = when {
        hz.isNaN() -> "-"
        mapper.nearestNote(hz) != pluck.string -> mapper.nearestNote(hz).toString()
        else -> "%+.1f".format(NoteMapper.centsBetween(hz, nominalHz))
    }

    private companion object {
        val TIMES = listOf(0.1f, 0.25f, 0.5f, 1f, 1.5f, 2f, 3f)

        /** The Precise in-tune window: the needle has settled once it stays this close to where it ends up. */
        const val SETTLE_WITHIN_CENTS = 2f
        val PARTIAL_TIMES = doubleArrayOf(0.15, 1.0)
        const val SHOWN_PARTIALS = 8
    }
}
