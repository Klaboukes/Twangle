package com.intune.core.dsp

import java.lang.management.ManagementFactory

/**
 * Bytes allocated on the heap by the current thread while running [block], used to check hard
 * rule 2 (no allocations in the audio/DSP hot loop). Warm the code up first so class loading and
 * JIT compilation don't count.
 *
 * The block runs [attempts] times and the smallest count is returned. The JVM occasionally
 * allocates a one-off internal buffer on the test thread (seen as a single 8 KB blip); code that
 * really allocates per call shows up in every attempt.
 */
inline fun allocatedBytes(attempts: Int = 3, block: () -> Unit): Long {
    val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    bean.currentThreadAllocatedBytes // The first call initialises the counter and allocates; don't measure that.
    var least = Long.MAX_VALUE
    repeat(attempts) {
        val before = bean.currentThreadAllocatedBytes
        block()
        least = minOf(least, bean.currentThreadAllocatedBytes - before)
    }
    return least
}
