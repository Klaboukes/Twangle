package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import org.junit.Test

/**
 * Prints the shapes [ChordShapeFinder] ranks for every chord on a few tunings, for reading by eye.
 * Print-only: run with `./gradlew :core:dsp:benchmark --tests '*ChordShapeReport*'`.
 */
class ChordShapeReport {

    @Test
    fun report() {
        val tunings = Instruments.GUITAR.tunings + Instruments.UKULELE.tunings + Instruments.MANDOLIN.tunings +
            Instruments.GUITAR_7.tunings + Instruments.BASS_4.tunings
        for (tuning in tunings) {
            println("== ${tuning.id}")
            for (quality in ChordQuality.entries) for (root in PitchClass.entries) print(tuning, Chord.of(root, quality))
        }
    }

    private fun print(tuning: Tuning, chord: Chord) {
        val shapes = ChordShapeFinder.shapesOf(chord, tuning)
        val symbol = ChordSpelling.symbolOf(chord, null, Notation.SHARPS)
        println(
            symbol.padEnd(8) + shapes.joinToString("  ") { shape ->
                val fingers = shape.fingers.joinToString("") { if (it == 0) "-" else it.toString() }
                val barre = shape.barre?.let { "|b${it.fret}:${it.firstString}-${it.lastString}" } ?: ""
                "$shape[$fingers$barre]"
            },
        )
    }
}
