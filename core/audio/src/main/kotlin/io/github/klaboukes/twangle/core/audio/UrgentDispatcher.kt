package io.github.klaboukes.twangle.core.audio

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * A dispatcher on one thread of its own at urgent-audio priority, for what must happen at a moment
 * (a beat told as it is heard, a tap on the wrist): its timers and wakes park the thread
 * (`ScheduledExecutorService`, `LockSupport.parkNanos`). On the Galaxy Watch 5 (Phase 80) such a
 * thread woke within 2 ms of a 250 ms wait (median 0.8 ms), a timer on `Dispatchers.Default` up to
 * 30 ms late (median 6) and a `Handler` thread at the same priority 4–8 ms late. The thread lives
 * as long as the app, asleep while there is nothing to do; make one per job, once.
 */
fun urgentAudioDispatcher(name: String): CoroutineDispatcher =
    Executors.newSingleThreadScheduledExecutor { work ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            work.run()
        }, name).apply { isDaemon = true }
    }.asCoroutineDispatcher()
