package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTrainerTest {

    @Test
    fun `it rises by the step every so many bars and holds at the goal`() {
        val trainer = SpeedTrainer(startBpm = 80, goalBpm = 100, stepBpm = 5, bars = 4)
        val tempos = (0L until 24L).map { trainer.bpmOfBar(it) }
        assertEquals(List(4) { 80 } + List(4) { 85 } + List(4) { 90 } + List(4) { 95 } + List(8) { 100 }, tempos)
    }

    @Test
    fun `it never goes past the goal, even when the last step would`() {
        val trainer = SpeedTrainer(startBpm = 97, goalBpm = 120, stepBpm = 10, bars = 1)
        assertEquals(listOf(97, 107, 117, 120, 120), (0L..4L).map { trainer.bpmOfBar(it) })
        assertEquals("the last step adds only what is left", 3, trainer.nextStepBpm(2))
        for (bar in 0L..10_000L) assertTrue(trainer.bpmOfBar(bar) <= 120)
        assertEquals("a far bar doesn't overflow", 120, trainer.bpmOfBar(Long.MAX_VALUE))
    }

    @Test
    fun `a goal at or below the start holds at the start`() {
        val trainer = SpeedTrainer(startBpm = 120, goalBpm = 100, stepBpm = 5, bars = 1)
        assertEquals(listOf(120, 120, 120), (0L..2L).map { trainer.bpmOfBar(it) })
        assertTrue(trainer.atGoal(0))
        assertEquals(0, trainer.barsToNextStep(0))
    }

    @Test
    fun `bars to the next step count down to each step`() {
        val trainer = SpeedTrainer(startBpm = 80, goalBpm = 90, stepBpm = 5, bars = 2)
        assertEquals("before the first downbeat", 2, trainer.barsToNextStep(-1))
        assertEquals(listOf(2, 1, 2, 1, 0, 0), (0L..5L).map { trainer.barsToNextStep(it) })
        assertEquals(listOf(5, 5, 5, 5, 0), (0L..4L).map { trainer.nextStepBpm(it) })
        assertFalse(trainer.atGoal(3))
        assertTrue(trainer.atGoal(4))
        assertEquals("before the first downbeat it plays the start", 80, trainer.bpmOfBar(-1))
    }

    @Test
    fun `a new trainer starts at the tempo shown and climbs 20 BPM, +5 every 4 bars`() {
        assertEquals(SpeedTrainer(96, 116, 5, 4), SpeedTrainer.startingAt(96))
        assertEquals("room to climb at the top", SpeedTrainer(245, 250, 5, 4), SpeedTrainer.startingAt(250))
        assertEquals(SpeedTrainer(240, 250, 5, 4), SpeedTrainer.startingAt(240))
        assertEquals(SpeedTrainer(30, 50, 5, 4), SpeedTrainer.startingAt(12))
    }

    @Test
    fun `saved values are kept within the lists`() {
        assertEquals(SpeedTrainer(80, 120, 2, 8), SpeedTrainer.of(80, 120, 2, 8))
        assertEquals("out of range", SpeedTrainer(30, 250, 5, 4), SpeedTrainer.of(5, 999, 3, 3))
        assertEquals("the goal stays above the start", SpeedTrainer(100, 105, 1, 1), SpeedTrainer.of(100, 90, 1, 1))
        assertEquals(SpeedTrainer(245, 250, 10, 2), SpeedTrainer.of(250, 250, 10, 2))
    }

    @Test
    fun `start and goal step on a grid of 5`() {
        assertEquals(100, SpeedTrainer.higher(97))
        assertEquals(105, SpeedTrainer.higher(100))
        assertEquals(95, SpeedTrainer.lower(97))
        assertEquals(90, SpeedTrainer.lower(95))
        assertEquals(30, SpeedTrainer.lower(34))
        assertEquals(35, SpeedTrainer.higher(30))
    }

    @Test
    fun `asking a tempo allocates nothing`() {
        val trainer = SpeedTrainer(60, 200, 2, 1)
        var sum = 0L
        val bytes = allocatedBytes {
            for (bar in 0L until 100_000L) sum += trainer.bpmOfBar(bar) + trainer.barsToNextStep(bar)
        }
        assertTrue(sum > 0)
        assertEquals(0L, bytes)
    }
}
