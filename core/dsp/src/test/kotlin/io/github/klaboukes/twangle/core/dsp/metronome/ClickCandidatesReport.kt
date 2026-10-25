package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Phase 45, "Hear them first", round 3: clicks to keep time by. Renders the app's four clicks
 * ([ClickSounds], every [ClickSound]: the wood block and the owner's choice of cowbell, claves and
 * rim click) into `docs/design/click-sounds-preview/` as 16-bit mono 48 kHz WAV files, each twice:
 * - `<sound>.wav`: one bar of 4/4 at 100 BPM (beat 1 accented, as the app plays it), then a second
 *   of silence;
 * - `<sound>-with-guitar.wav`: the same click under two bars of a strummed steel-string guitar
 *   (down and up eighths, E minor then C, ending on E minor), the same guitar under every sound.
 * It prints each one's peak and RMS next to the wood block's.
 *
 * Not a test: run it with the benchmarks
 * (`./gradlew :core:dsp:benchmark --tests '*ClickCandidatesReport*'`). The output is the same on
 * every run, so the committed files can be made again byte for byte; `ClickSoundsTest` checks the
 * app still makes the clicks in them sample for sample. The recipes are the app's own
 * ([ClickRecipes], [PercussionSynth]); this report also checks each recipe's listening places are
 * still the ones its search picks, so after a change to a recipe it tells the new ones.
 *
 * Round 3's rules, from the owner (2026-10-25): a click that helps a musician keep time, not a
 * realistic instrument. A sharp attack (the peak within 1–2 ms), short (about 40 dB down within
 * 30–60 ms, at most 80 ms in all, no room, no ring), identical on every beat, unpitched or only
 * weakly pitched (inharmonic modes and noise). Then (owner, the same day) the set became four
 * instruments, each clearly its own: today's wood block (the default), a cowbell, claves and a rim
 * click, each in its own register (main pitches at least 5 semitones apart, plain and beat 1
 * alike) and with its own nature and ring length (up to 180 ms), beat 1 at least as clear as the
 * wood block's (hit harder, +5 dB at the peak, an octave or a fifth up). The report prints how
 * clearly beat 1 stands out ([mainPitch]) and how far apart every pair of sounds is
 * ([printDistinctness]). Round 2's texture stays (contact pulse, strike noise, a few
 * inharmonic modes, [PercussionSynth]), damped hard. Each set is scaled so a bar has the wood
 * block's RMS, its peak kept at or under 0.8 ([ClickSounds]).
 */
class ClickCandidatesReport {

    @Test
    fun writeCandidates() {
        val dir = File(repoRoot(), "docs/design/click-sounds-preview").apply { mkdirs() }

        val sounds = ClickSounds(RATE)
        val target = rms(barFromClickTrack(ClickSound.WOOD_BLOCK))
        val clickSets = ClickSound.entries.map { sound ->
            val clicks = BeatAccent.entries.associateWith { a -> sounds.waveform(sound, a).map { it.toDouble() }.toDoubleArray() }
            assertArrayEquals("$sound: the bar matches ClickTrack's", barFromClickTrack(sound), renderBar { clicks.getValue(it) }, 0.0)
            fileOf(sound) to clicks
        }

        for (sound in ClickSound.entries) {
            val recipe = ClickRecipes.of(sound) ?: continue
            // Where the click is heard sets each mode's sign, and so how far the modes add up in
            // the first instant. For each accent, of 300 places, take the one with the least peak
            // for its energy (the most loudness under the 0.8 peak) among those whose energy still
            // peaks within ATTACK_MS of the onset, so the beat's moment stays exact.
            for (accent in BeatAccent.entries) {
                fun hit(place: Long) = PercussionSynth.hit(recipe, accent, RATE, place)
                val place = (0L until 300L).filter { peakMs(hit(it)) <= ATTACK_MS }.minBy { crest(hit(it)) }
                assertEquals("$sound $accent: the place in ClickRecipes", place, PercussionSynth.listenerOf(recipe, accent))
            }
        }
        // How clearly beat 1 stands out from beat 2: the level steps, each one's main pitch and how
        // clear it is (autocorrelation), the distance between the two pitches, and the brightness.
        println("sound          RMS step  peak step   beat 1 pitch (clarity)   beat 2 pitch (clarity)   apart       centroid")
        for ((name, clicks) in clickSets) {
            val down = clicks.getValue(BeatAccent.DOWNBEAT)
            val plain = clicks.getValue(BeatAccent.PLAIN)
            val (downHz, downClarity) = mainPitch(down)
            val (plainHz, plainClarity) = mainPitch(plain)
            println(
                "%-13s  %+5.1f dB  %+5.1f dB   %6.0f Hz (%.2f)          %6.0f Hz (%.2f)          %+5.1f st   x%.2f".format(
                    name, 20 * log10(rms(down) / rms(plain)), 20 * log10(down.maxOf { abs(it) } / plain.maxOf { abs(it) }),
                    downHz, downClarity, plainHz, plainClarity, 12 * log2(downHz / plainHz), centroid(down) / centroid(plain),
                ),
            )
        }

        printDistinctness(clickSets.map { (name, clicks) -> name to clicks.getValue(BeatAccent.PLAIN) })
        println("beat 1 of each:")
        printDistinctness(clickSets.map { (name, clicks) -> name to clicks.getValue(BeatAccent.DOWNBEAT) })

        val guitar = guitarTrack()
        val strummed = 8 * BEAT_FRAMES // the two bars
        val files = mutableListOf<Pair<String, FloatArray>>()
        for ((name, clicks) in clickSets) {
            files += name to renderBar { clicks.getValue(it) }.toFloats(1.0)
            // The guitar GUITAR_OVER_CLICK_DB louder than the click by RMS over the two bars.
            val track = renderClicks(guitar.size, 9) { clicks.getValue(it) }
            val guitarGain = rms(track, strummed) / rms(guitar, strummed) * 10.0.pow(GUITAR_OVER_CLICK_DB / 20)
            val mix = DoubleArray(guitar.size) { guitarGain * guitar[it] + track[it] }
            val mixGain = minOf(1.0, MIX_PEAK / mix.maxOf { abs(it) })
            files += "$name-with-guitar" to mix.toFloats(mixGain)
            val guitarInMix = DoubleArray(guitar.size) { guitarGain * guitar[it] }
            println(
                "%-13s  with guitar: mix scaled %+.1f dB to stay under %.2f; %.0f %% of the click's energy is in bands where it is louder than the guitar".format(
                    name, 20 * log10(mixGain), MIX_PEAK, 100 * shareAboveGuitar(track, guitarInMix, 9),
                ),
            )
        }

        // Only this round's files stay in the folder.
        val names = files.map { "${it.first}.wav" }.toSet()
        dir.listFiles { f -> f.name.endsWith(".wav") && f.name !in names }?.forEach { it.delete() }

        val failures = mutableListOf<String>()
        println("file                        peak   peak dBFS   RMS dBFS   vs wood block")
        for ((name, bar) in files) {
            val peak = bar.maxOf { abs(it) }
            val r = rms(DoubleArray(bar.size) { bar[it].toDouble() })
            val vsWood = 20 * log10(r / target)
            println("%-26s  %5.3f   %7.1f    %7.1f     %+5.1f dB".format(name, peak, db(peak.toDouble()), db(r), vsWood))
            writeWav(File(dir, "$name.wav"), bar)
            if (bar.first() != 0f || bar.last() != 0f) failures += "$name doesn't start and end on silence"
            if (name.endsWith("-with-guitar")) continue
            if (peak > MAX_PEAK + 1e-6f) failures += "$name clips"
            if (abs(vsWood) > 1.0) failures += "$name is not within 1 dB of the wood block"
        }
        println("WAV files in ${dir.canonicalPath}")
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    // One bar of 4/4 at BPM, each beat's click on its exact sample, then SILENCE_SECONDS of silence.
    private fun renderBar(click: (BeatAccent) -> DoubleArray): DoubleArray =
        renderClicks(Meter.FOUR_FOUR.beatsPerBar * BEAT_FRAMES + (SILENCE_SECONDS * RATE).roundToInt(), 4, click)

    // [beats] clicks in 4/4 from sample 0, in a track of [frames] samples.
    private fun renderClicks(frames: Int, beats: Int, click: (BeatAccent) -> DoubleArray): DoubleArray {
        val out = DoubleArray(frames)
        for (beat in 0 until beats) {
            val samples = click(Meter.FOUR_FOUR.accentOf(beat % 4))
            check(samples.size <= BEAT_FRAMES) { "a click is longer than a beat" }
            for (i in samples.indices) out[beat * BEAT_FRAMES + i] += samples[i]
        }
        return out
    }

    // The bar as the app's ClickTrack plays it.
    private fun barFromClickTrack(sound: ClickSound): DoubleArray {
        val track = ClickTrack(RATE, BPM, Meter.FOUR_FOUR, sound)
        val bar = FloatArray(Meter.FOUR_FOUR.beatsPerBar * BEAT_FRAMES)
        track.render(bar)
        return (bar + FloatArray((SILENCE_SECONDS * RATE).roundToInt())).map { it.toDouble() }.toDoubleArray()
    }

    private fun fileOf(sound: ClickSound): String = sound.name.lowercase().replace('_', '-')

    /**
     * Two bars of steady eighth-note strums at [BPM] on a steel-string guitar (the app's own
     * reference-tone synth, [StringSynth] with [StringVoices.STEEL_GUITAR]): E minor, then C, then
     * one last E minor on the next downbeat left to ring. Downstrokes play every string of the chord
     * low to high, 9 ms apart, centred on the beat; upstrokes the top four, high to low, 7 ms apart
     * and softer. A string is damped (8 ms) when it is struck again or the chord changes; the last
     * chord rings and fades out. Then silence.
     */
    private fun guitarTrack(): DoubleArray {
        class Pluck(val string: Int, val hz: Double, val onset: Int, val gain: Double)

        val em = intArrayOf(40, 47, 52, 55, 59, 64) // E2 B2 E3 G3 B3 E4 (MIDI): 0 2 2 0 0 0
        val c = intArrayOf(-1, 48, 52, 55, 60, 64) //  –  C3 E3 G3 C4 E4: x 3 2 0 1 0
        val plucks = mutableListOf<Pluck>()
        fun strum(chord: IntArray, beatAt: Int, down: Boolean) {
            val strings = chord.indices.filter { chord[it] >= 0 }.let { if (down) it else it.takeLast(4).reversed() }
            val spread = (if (down) 0.009 else 0.007) * RATE
            val first = beatAt - spread * (strings.size - 1) / 2
            for ((i, s) in strings.withIndex()) {
                val hz = 440.0 * 2.0.pow((chord[s] - 69) / 12.0)
                plucks += Pluck(s, hz, (first + i * spread).roundToInt().coerceAtLeast(0), if (down) 1.0 else 0.6)
            }
        }
        for (eighth in 0 until 16) {
            val chord = if (eighth < 8) em else c
            strum(chord, eighth * BEAT_FRAMES / 2, down = eighth % 2 == 0)
        }
        val lastChord = 8 * BEAT_FRAMES
        strum(em, lastChord, down = true)

        val ringFrames = (1.6 * RATE).roundToInt()
        val total = lastChord + ringFrames + (0.6 * RATE).roundToInt()
        val chordChange = 4 * BEAT_FRAMES
        val damp = (0.008 * RATE).roundToInt()
        val out = DoubleArray(total)
        val synth = StringSynth(RATE, StringVoices.STEEL_GUITAR)
        val tone = FloatArray(ringFrames)
        for (p in plucks) {
            val next = plucks.filter { it.string == p.string && it.onset > p.onset }.minOfOrNull { it.onset }
            val change = if (p.onset < chordChange) chordChange else Int.MAX_VALUE
            val last = p.onset >= lastChord - BEAT_FRAMES / 4
            val end = if (last) p.onset + ringFrames else minOf(next ?: Int.MAX_VALUE, change) + damp
            val fade = if (last) (0.4 * RATE).roundToInt() else damp
            val length = minOf(end - p.onset, ringFrames, total - p.onset)
            synth.start(p.hz.toFloat())
            tone.fill(0f)
            val written = synth.render(tone, 0, length)
            for (i in 0 until written) {
                val left = length - 1 - i
                val gain = if (left < fade) 0.5 - 0.5 * cos(PI * left / fade) else 1.0
                out[p.onset + i] += p.gain * gain * tone[i]
            }
        }
        return out
    }

    /**
     * How well the click stands out from the guitar: over the first 80 ms after each of [beats]
     * beats, the share of the click's energy that lies in third-octave bands (50 Hz–16 kHz) where
     * the click has more energy than the guitar. A rough masking measure, not a hearing model.
     */
    private fun shareAboveGuitar(click: DoubleArray, guitar: DoubleArray, beats: Int): Double {
        val window = (0.08 * RATE).roundToInt()
        fun bandEnergy(x: DoubleArray, centerHz: Double): Double {
            // RBJ band-pass, a third of an octave wide (Q ≈ 4.32).
            val w0 = 2 * PI * centerHz / RATE
            val alpha = kotlin.math.sin(w0) / (2 * 4.32)
            val a0 = 1 + alpha
            val b0 = alpha / a0
            val a1 = -2 * cos(w0) / a0
            val a2 = (1 - alpha) / a0
            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            var energy = 0.0
            for (n in x.indices) {
                val y = b0 * x[n] - b0 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = x[n]
                y2 = y1
                y1 = y
                if ((n % BEAT_FRAMES) < window && n / BEAT_FRAMES < beats) energy += y * y
            }
            return energy
        }
        var above = 0.0
        var all = 0.0
        var center = 50.0
        while (center < 16_000) {
            val c = bandEnergy(click, center)
            if (c > bandEnergy(guitar, center)) above += c
            all += c
            center *= 2.0.pow(1.0 / 3)
        }
        return above / all
    }

    /**
     * The main pitch of a click and how clear it is: the highest peak of the normalised
     * autocorrelation over its first 40 ms, past the autocorrelation's first zero crossing, for
     * lags of 150 Hz to 8 kHz (refined between samples by a parabola), the shortest lag whose peak
     * comes within 20 % of the highest. The peak's height, 0 to 1,
     * is the pitch's clarity (salience): near 1 for a clear note, low for noise.
     */
    private fun mainPitch(x: DoubleArray): Pair<Double, Double> {
        val n = minOf(x.size, (0.04 * RATE).roundToInt())
        val maxLag = RATE / 150
        fun r(lag: Int): Double {
            var xy = 0.0
            var xx = 0.0
            var yy = 0.0
            for (i in 0 until n - lag) {
                xy += x[i] * x[i + lag]
                xx += x[i] * x[i]
                yy += x[i + lag] * x[i + lag]
            }
            return xy / sqrt(xx * yy + 1e-30)
        }
        val ac = DoubleArray(maxLag + 2) { if (it == 0) 1.0 else r(it) }
        var lag = 1
        while (lag < maxLag && ac[lag] > 0) lag++
        lag = maxOf(lag, RATE / 8000)
        var top = lag
        for (l in lag..maxLag) if (ac[l] > ac[top]) top = l
        // A note's period repeats at every multiple of it, nearly as strongly: take the first peak
        // within 20 % of the highest, as YIN does, so the pitch isn't read octaves low.
        var best = top
        for (l in lag + 1 until top) {
            if (ac[l] >= ac[l - 1] && ac[l] >= ac[l + 1] && ac[l] >= 0.8 * ac[top]) {
                best = l
                break
            }
        }
        val a = ac[best - 1]
        val b = ac[best]
        val c = ac[best + 1]
        val curve = a - 2 * b + c
        val shift = if (curve == 0.0) 0.0 else 0.5 * (a - c) / curve
        return RATE / (best + shift) to b
    }

    /** What sets a click apart: its main pitch and how clear it is, brightness, noisiness, ring and timbre. */
    private class Features(
        val pitchHz: Double,
        val clarity: Double,
        val centroidHz: Double,
        val flatness: Double,
        val decayMs: Double,
        val mfcc: DoubleArray,
    )

    /**
     * [Features] of a click: [mainPitch]; from the power spectrum of its first 85 ms (Hann window,
     * 50 Hz–16 kHz) the centroid; the spectral flatness over 26 mel bands (their energies'
     * geometric over arithmetic mean: low for a note, towards 1 for noise spread evenly); the time
     * until its 1 ms RMS envelope is 40 dB under its peak; and 12 mel-frequency cepstral
     * coefficients (c1–c12 of the same bands, so the level doesn't count), whose distance is a
     * usual measure of how different two timbres are.
     */
    private fun features(x: DoubleArray): Features {
        val size = 4096
        val table = DoubleArray(size) { cos(2 * PI * it / size) }
        val frame = DoubleArray(size) { if (it < x.size) x[it] * (0.5 - 0.5 * cos(2 * PI * it / size)) else 0.0 }
        val power = DoubleArray(size / 2 + 1) { k ->
            var re = 0.0
            var im = 0.0
            for (n in 0 until size) {
                val i = (k.toLong() * n % size).toInt()
                re += frame[n] * table[i]
                im -= frame[n] * table[(i + size * 3 / 4) % size] // sin(θ) = cos(θ − π/2)
            }
            re * re + im * im
        }
        val binHz = RATE.toDouble() / size
        val bins = (1..size / 2).filter { it * binHz in 50.0..16_000.0 }
        val centroid = bins.sumOf { it * binHz * power[it] } / bins.sumOf { power[it] }

        // Mel bands, then a DCT of their logs.
        fun mel(hz: Double) = 2595 * log10(1 + hz / 700)
        fun hzOf(mel: Double) = 700 * (10.0.pow(mel / 2595) - 1)
        val edges = DoubleArray(28) { hzOf(mel(50.0) + it * (mel(16_000.0) - mel(50.0)) / 27) }
        val bands = DoubleArray(26) { b ->
            var e = 0.0
            for (k in bins) {
                val hz = k * binHz
                val w = when {
                    hz < edges[b] || hz > edges[b + 2] -> 0.0
                    hz <= edges[b + 1] -> (hz - edges[b]) / (edges[b + 1] - edges[b])
                    else -> (edges[b + 2] - hz) / (edges[b + 2] - edges[b + 1])
                }
                e += w * power[k]
            }
            e + 1e-20
        }
        val flatness = exp(bands.sumOf { ln(it) } / bands.size) / bands.average()
        val mfcc = DoubleArray(12) { c -> (0 until 26).sumOf { b -> ln(bands[b]) * cos(PI * (c + 1) * (b + 0.5) / 26) } }

        val (pitch, clarity) = mainPitch(x)
        return Features(pitch, clarity, centroid, flatness, decayMs(x), mfcc)
    }

    // The time until the 1 ms RMS envelope is 40 dB under its peak, in ms.
    private fun decayMs(x: DoubleArray): Double {
        val w = RATE / 1000
        val env = DoubleArray(x.size)
        var sum = 0.0
        for (n in x.indices) {
            sum += x[n] * x[n]
            if (n >= w) sum -= x[n - w] * x[n - w]
            env[n] = sum
        }
        val peak = env.max()
        val last = env.indexOfLast { it > peak * 1e-4 }
        return last * 1000.0 / RATE
    }

    // Prints how far apart every pair of sounds is (plain beats), and flags pairs close on most.
    private fun printDistinctness(plains: List<Pair<String, DoubleArray>>) {
        val all = plains.map { (name, x) -> name to features(x) }
        println("sound          pitch (clarity)    centroid   flatness   40 dB down")
        for ((name, f) in all) {
            println("%-13s  %6.0f Hz (%.2f)   %6.0f Hz    %.3f      %4.0f ms".format(name, f.pitchHz, f.clarity, f.centroidHz, f.flatness, f.decayMs))
        }
        val pairs = mutableListOf<Pair<String, DoubleArray>>()
        for (i in all.indices) for (j in i + 1 until all.size) {
            val (a, fa) = all[i]
            val (b, fb) = all[j]
            // A click whose pitch is unclear (clarity under 0.3) has no pitch to be near: it counts as apart.
            val unpitched = fa.clarity < UNPITCHED || fb.clarity < UNPITCHED
            val semitones = if (unpitched) Double.NaN else abs(12 * log2(fa.pitchHz / fb.pitchHz))
            val centroid = maxOf(fa.centroidHz, fb.centroidHz) / minOf(fa.centroidHz, fb.centroidHz)
            val flatness = abs(fa.flatness - fb.flatness)
            val decay = maxOf(fa.decayMs, fb.decayMs) / minOf(fa.decayMs, fb.decayMs)
            val timbre = sqrt(fa.mfcc.indices.sumOf { (fa.mfcc[it] - fb.mfcc[it]).pow(2) })
            pairs += "$a / $b" to doubleArrayOf(semitones, centroid, flatness, decay, timbre)
        }
        val medianTimbre = pairs.map { it.second[4] }.sorted()[pairs.size / 2]
        println("pair                         pitch apart  centroid  flatness  decay   timbre (MFCC)")
        for ((pair, d) in pairs) {
            // Close: under 5 semitones, centroids within 25 %, flatness within 0.05, decays within
            // 1.5×, timbre under half the median pair's. Close on three or more of five: flagged.
            val close = listOf(d[0] < 5, d[1] < 1.25, d[2] < 0.05, d[3] < 1.5, d[4] < medianTimbre / 2).count { it }
            val apart = if (d[0].isNaN()) "unpitched" else "%5.1f st".format(d[0])
            println(
                "%-27s  %-9s    ×%.2f     %.3f     ×%.2f   %5.1f%s".format(
                    pair, apart, d[1], d[2], d[3], d[4], if (close >= 3) "   CLOSE on $close of 5" else "",
                ),
            )
        }
    }

    // Spectral centroid of [x] by a plain DFT on a 50 Hz grid (slow, but only a few clicks).
    private fun centroid(x: DoubleArray): Double {
        var weighted = 0.0
        var total = 0.0
        var hz = 50.0
        while (hz < RATE / 2) {
            var re = 0.0
            var im = 0.0
            val w = 2 * PI * hz / RATE
            for (n in x.indices) {
                re += x[n] * cos(w * n)
                im -= x[n] * kotlin.math.sin(w * n)
            }
            val power = re * re + im * im
            weighted += hz * power
            total += power
            hz += 50.0
        }
        return weighted / total
    }

    private fun crest(x: DoubleArray): Double = x.maxOf { abs(it) } / rms(x)

    // When the energy peaks: the loudest millisecond (a 1 ms moving RMS), in ms from the onset.
    private fun peakMs(x: DoubleArray): Double {
        val w = RATE / 1000
        var sum = 0.0
        var best = 0.0
        var at = 0
        for (n in x.indices) {
            sum += x[n] * x[n]
            if (n >= w) sum -= x[n - w] * x[n - w]
            if (sum > best) {
                best = sum
                at = n
            }
        }
        return at * 1000.0 / RATE
    }

    private fun DoubleArray.toFloats(gain: Double) = FloatArray(size) { (this[it] * gain).toFloat() }

    private fun rms(x: DoubleArray, frames: Int = x.size): Double {
        var sum = 0.0
        for (i in 0 until frames) sum += x[i] * x[i]
        return sqrt(sum / frames)
    }

    private fun db(x: Double): Double = 20 * log10(x + 1e-12)

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "no settings.gradle.kts above ${System.getProperty("user.dir")}" }
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
        DataOutputStream(file.outputStream().buffered()).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(data.capacity())
            out.write(header.array())
            out.write(data.array())
        }
    }

    private companion object {
        const val RATE = 48_000
        const val BPM = 100
        const val BEAT_FRAMES = RATE * 60 / BPM // 28 800: exact at 100 BPM and 48 kHz.
        const val SILENCE_SECONDS = 1.0
        const val MAX_PEAK = 0.8f
        const val MIX_PEAK = 0.9

        /** The energy of every click peaks within this many ms of its onset. */
        const val ATTACK_MS = 1.6

        /** Below this autocorrelation peak a click counts as unpitched. */
        const val UNPITCHED = 0.3

        /** How much louder (RMS over the two bars) the guitar is than the click in the mixes. */
        const val GUITAR_OVER_CLICK_DB = 14.0
    }
}
