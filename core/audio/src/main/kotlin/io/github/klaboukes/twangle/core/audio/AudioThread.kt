package io.github.klaboukes.twangle.core.audio

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * One thread of its own at `THREAD_PRIORITY_AUDIO`, named [name], for blocking audio work: the
 * microphone's reads, a sound's writes, and the set-up around them that calls the audio server. At
 * that priority a busy phone (the screen drawing, a garbage collection) can't hold the read back
 * long enough to overrun the record buffer, nor a write long enough to starve the speaker; Android's
 * own audio threads run there too.
 *
 * Its own thread, never one of `Dispatchers.IO`'s: those are shared, so a priority set on one would
 * stay with whatever other work ran on it next. Work runs one piece at a time, in order. The thread
 * is made when work comes and ends once idle for [keepAliveSeconds], so a recorder or player that is
 * never closed (the app keeps its own; the watch makes new ones each time it opens) holds no thread
 * while quiet.
 */
fun audioThreadExecutor(name: String, keepAliveSeconds: Long = AUDIO_THREAD_KEEP_ALIVE_SECONDS): ExecutorService =
    ThreadPoolExecutor(1, 1, keepAliveSeconds, TimeUnit.SECONDS, LinkedBlockingQueue()) { work ->
        Thread({
            // A phone that refuses it plays on at the normal priority.
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
            work.run()
        }, name).apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

/**
 * [audioThreadExecutor] as a coroutine dispatcher: everything dispatched to it runs on that one
 * thread, a coroutine resuming after a `delay` too.
 */
fun audioThreadDispatcher(name: String, keepAliveSeconds: Long = AUDIO_THREAD_KEEP_ALIVE_SECONDS): CoroutineDispatcher =
    audioThreadExecutor(name, keepAliveSeconds).asCoroutineDispatcher()

/** How long an audio thread waits for more work before it ends: a pause between two sessions keeps it. */
const val AUDIO_THREAD_KEEP_ALIVE_SECONDS = 5L
