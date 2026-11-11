package io.github.klaboukes.twangle.core.audio

import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** The audio threads' priority is Android's (Linux's) own, so this needs a device. */
@RunWith(AndroidJUnit4::class)
class AudioThreadTest {

    @Test
    fun workRunsOnOneThreadOfItsOwnAtAudioPriority() {
        val executor = audioThreadExecutor(NAME)
        val seen = (1..3).map { executor.submit<Pair<Int, Int>> { Process.myTid() to Process.getThreadPriority(Process.myTid()) }.get(1, TimeUnit.SECONDS) }

        assertEquals("one thread", 1, seen.map { it.first }.distinct().size)
        assertTrue("not the caller's thread", seen.first().first != Process.myTid())
        for ((_, priority) in seen) assertEquals(Process.THREAD_PRIORITY_AUDIO, priority)
        assertEquals(listOf(Process.THREAD_PRIORITY_AUDIO), audioThreadPriorities(NAME))
        executor.shutdown()
    }

    @Test
    fun theThreadEndsWhenIdleAndComesBackAtAudioPriority() {
        val executor = audioThreadExecutor(IDLE_NAME, keepAliveSeconds = 1)
        executor.submit { }.get(1, TimeUnit.SECONDS)
        assertEquals(1, audioThreadPriorities(IDLE_NAME).size)

        val deadline = SystemClock.uptimeMillis() + 5_000
        while (audioThreadPriorities(IDLE_NAME).isNotEmpty() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertEquals("the idle thread must end", emptyList<Int>(), audioThreadPriorities(IDLE_NAME))

        val priority = executor.submit<Int> { Process.getThreadPriority(Process.myTid()) }.get(1, TimeUnit.SECONDS)
        assertEquals(Process.THREAD_PRIORITY_AUDIO, priority)
        executor.shutdown()
    }

    @Test
    fun aCoroutineComesBackToTheSameThreadAfterADelay() = runBlocking {
        val dispatcher = audioThreadDispatcher(COROUTINE_NAME)
        val (before, after) = withContext(dispatcher) {
            val before = Process.myTid()
            delay(20)
            before to Process.myTid()
        }
        assertEquals(before, after)
        assertEquals(Process.THREAD_PRIORITY_AUDIO, Process.getThreadPriority(after))
    }

    private companion object {
        const val NAME = "AudioThreadTest"
        const val IDLE_NAME = "AudioThreadIdle"
        const val COROUTINE_NAME = "AudioThreadCo"
    }
}

/**
 * The priorities (`Process.getThreadPriority`, a Linux nice value) of this process's threads named
 * [name], read from `/proc`, which keeps the first 15 characters of a thread's name.
 */
fun audioThreadPriorities(name: String): List<Int> =
    File("/proc/self/task").listFiles().orEmpty().mapNotNull { task ->
        val comm = runCatching { File(task, "comm").readText().trim() }.getOrNull()
        val tid = task.name.toIntOrNull()
        if (comm == name.take(15) && tid != null) runCatching { Process.getThreadPriority(tid) }.getOrNull() else null
    }
