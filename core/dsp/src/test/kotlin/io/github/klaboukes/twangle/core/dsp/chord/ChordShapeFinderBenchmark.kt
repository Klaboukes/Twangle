package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timing check for the chord charts: a whole chord list (every root and quality) is worked out in
 * under 20 ms a chord on the JVM, so Chords opens without a wait.
 *
 * Not part of `test`: timings depend on the machine and how busy it is. Run
 * with `./gradlew :core:dsp:benchmark`.
 */
class ChordShapeFinderBenchmark {

    @Test
    fun `a whole chord list is quick`() {
        val start = System.nanoTime()
        for (quality in ChordQuality.entries) for (root in PitchClass.entries) ChordShapeFinder.shapesOf(Chord.of(root, quality), Tunings.STANDARD)
        val perChordMs = (System.nanoTime() - start) / 1e6 / (ChordQuality.entries.size * PitchClass.entries.size)
        println("ChordShapeFinder.shapesOf: %.2f ms a chord".format(perChordMs))
        assertTrue("$perChordMs ms a chord", perChordMs < 20.0)
    }
}
