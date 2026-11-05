package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.Burst
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.MEMBRANE
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.Mode
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.Recipe
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.barRatios
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.barShape
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.besselJ
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.ringRatio
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.sign
import io.github.klaboukes.twangle.core.dsp.metronome.PercussionSynth.woodDecay
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The recipes of the cowbell, claves and rim click (the owner's choice, Phase 45, 2026-10-25),
 * for [PercussionSynth]. The approved samples are in `src/test/resources/clicks/`;
 * `ClickSoundsTest` checks these still make them sample for sample.
 */
internal object ClickRecipes {

    /** The recipe of [sound], or null for the wood block, which [ClickSounds] makes its own way. */
    fun of(sound: ClickSound): Recipe? = when (sound) {
        ClickSound.WOOD_BLOCK -> null
        ClickSound.COWBELL -> COWBELL
        ClickSound.CLAVES -> CLAVES
        ClickSound.RIM_CLICK -> RIM_CLICK
    }

    // A cowbell, choked by a hand but still audibly ringing: unmistakably metal. No published
    // table of cowbell modes was at hand, so the modes come from the physics of a thin bell
    // shell: its (m, 0) modes, where the mouth bends into m waves round the rim, follow a thin
    // ring's inextensional bending, f ∝ m(m² − 1)/√(m² + 1), 1 : 2.83 : 5.42 for m = 2, 3, 4
    // (Rayleigh; Fletcher & Rossing ch. 2 and 21); between them a cluster of modes with one
    // nodal line across the shell, at 1.47, 1.94, 2.37 and 3.38 × the lowest (an estimate).
    // The lowest, the bell's tone, at 560 Hz (a real cowbell's sits at about 550–800 Hz),
    // below the wood block; the clashing cluster above it, 0.8–3 kHz, keeps it audible over a
    // guitar. Every mode is a pair split by the flattened, uneven shell (±0.5 % for the tone,
    // ±1.5–2.5 % for the cluster, an estimate), so it beats and clangs. A hard stick on metal
    // (0.15 ms) and a strike noise around 4 kHz; the ring dies in about 150 ms. Beat 1 is the
    // pair's smaller bell, an octave up, as an agogo has two.
    val COWBELL = Recipe(
        name = "cowbell",
        lengthSeconds = 0.18,
        fadeSeconds = 0.04,
        contactSeconds = 0.00015,
        plainVelocity = 0.66,
        downbeatVelocity = 1.3,
        downbeatPitch = 2.0,
        groupPitch = 1.5,
        subdivisionVelocity = 0.4,
        subdivisionPitch = 0.75,
        bursts = listOf(Burst(4000.0, 1.0, 0.0015, 0.15)),
        downbeatListener = 160,
        groupListener = 165,
        plainListener = 254,
        subdivisionListener = 163,
    ) { p, listener ->
        val f = 560.0 * p
        // (ratio, strength, pair split, time constant)
        val modes = listOf(
            listOf(1.0, 3.0, 0.005, 0.034),
            listOf(1.47, 1.2, 0.02, 0.026),
            listOf(1.94, 1.3, 0.025, 0.024),
            listOf(2.37, 1.4, 0.02, 0.022),
            listOf(ringRatio(3), 1.6, 0.015, 0.02),
            listOf(3.38, 1.2, 0.02, 0.018),
            listOf(ringRatio(4), 0.8, 0.015, 0.014),
        )
        buildList {
            for ((ratio, amp, split, decay) in modes) {
                val sign = listener.sign() // a split pair starts together, then beats
                add(Mode(f * ratio * (1 - split), amp * sign, decay))
                add(Mode(f * ratio * (1 + split), amp * sign, decay))
            }
        }
    }

    // Rosewood claves: one rod held loosely at its node in a cupped hand, struck near its
    // middle by the other. High, clear and glassy: mostly the rod's lowest bending mode, at
    // 2.2 kHz (real claves sound at about 2–2.5 kHz), ringing briefly. The bending modes of a
    // thick free rod (a Timoshenko estimate, the second at about 2.3× the first instead of a
    // thin bar's 2.756), two weak torsional modes from the slightly off-centre blow, the hand
    // damping the upper ones (the loss law of Chaigne & Doutaut), and only a trace of strike
    // noise. Wood on wood is very hard (0.2 ms). Beat 1 is the higher clave of a pair, a fifth
    // up (an octave, at 4.4 kHz, would be shrill).
    val CLAVES = Recipe(
        name = "claves",
        lengthSeconds = 0.12,
        fadeSeconds = 0.03,
        contactSeconds = 0.0002,
        plainVelocity = 0.66,
        downbeatVelocity = 1.35,
        downbeatPitch = 1.5,
        groupPitch = 1.25,
        subdivisionVelocity = 0.4,
        subdivisionPitch = 0.75,
        bursts = listOf(Burst(5000.0, 0.8, 0.0005, 0.06)),
        downbeatListener = 231,
        groupListener = 42,
        plainListener = 232,
        subdivisionListener = 248,
    ) { p, listener ->
        val f = 2200.0 * p
        val ratios = barRatios(4, gyrationOverLength = 0.03125, eOverKappaG = 13.5)
        buildList {
            for (n in ratios.indices) {
                val hz = f * ratios[n]
                val amp = if (n == 0) 1.0 else 0.4 * abs(barShape(n, 0.42))
                add(Mode(hz, amp * (if (n == 0) 1.0 else listener.sign()), woodDecay(hz / p, 25.0, 4e-6)))
            }
            for (t in 1..2) add(Mode(f * 1.48 * t, 0.08 * listener.sign(), woodDecay(f * 1.48 * t / p, 25.0, 4e-6)))
        }
    }

    // A snare drum's rim click: the stick's shaft cracks on the metal hoop. Mostly a dry crack,
    // noise: the strike itself, broad around 3 kHz and 6 kHz, and the stick's own wooden
    // bending modes (a hickory rod, Euler–Bernoulli 1 : 2.76 : 5.40 …) dying within a few ms.
    // Under it a short, low thump of the drum (the 14" head's first membrane modes, Bessel
    // ratios, f01 = 180 Hz, driven at the edge) and a short ring of the hoop at 1.2 kHz,
    // faint on the plain beats. Beat 1 is struck harder and higher on the stick: the crack is
    // brighter (an octave up) and the hoop rings out clearly, an octave up too.
    val RIM_CLICK = Recipe(
        name = "rim-click",
        lengthSeconds = 0.08,
        fadeSeconds = 0.02,
        contactSeconds = 0.00012,
        plainVelocity = 0.66,
        downbeatVelocity = 0.72,
        downbeatPitch = 2.0,
        groupPitch = 1.5,
        subdivisionVelocity = 0.4,
        subdivisionPitch = 0.75,
        bursts = listOf(
            Burst(3000.0, 0.5, 0.009, 2.5),
            Burst(5000.0, 0.7, 0.004, 1.0),
        ),
        downbeatListener = 69,
        groupListener = 33,
        plainListener = 215,
        subdivisionListener = 112,
    ) { p, listener ->
        buildList {
            val stick = barRatios(5, gyrationOverLength = 0.00875, eOverKappaG = 13.0)
            for (n in stick.indices) {
                val hz = 700.0 * p * stick[n]
                add(Mode(hz, 0.5 * barShape(n, 0.3) * listener.sign(), 0.003))
            }
            for ((m, _, j) in MEMBRANE) {
                val hz = 180.0 * j / MEMBRANE[0].third
                add(Mode(hz, 1.6 * besselJ(m, j * 0.93) * listener.sign(), 0.025 * sqrt(180.0 / hz)))
            }
            // The hoop's ring: faint on a plain beat and between beats (strength 0.3), clear on
            // beat 1's harder hit (1.3).
            val ring = 1200.0 * p
            val ringSign = listener.sign()
            val ringLevel = maxOf(p - 0.7, 0.3)
            add(Mode(ring * 0.997, ringLevel * ringSign, 0.012))
            add(Mode(ring * 1.003, ringLevel * ringSign, 0.012))
        }
    }
}
