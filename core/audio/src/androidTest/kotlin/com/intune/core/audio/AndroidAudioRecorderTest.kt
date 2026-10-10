package com.intune.core.audio

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records from the real microphone, so it needs a device. */
@RunWith(AndroidJUnit4::class)
class AndroidAudioRecorderTest {

    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val recorder = AndroidAudioRecorder(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun deliversFullChunksOfFloatSamples() = runBlocking {
        val chunks = recorder.chunks().map { it.copyOf() }.take(CHUNKS).toList()

        assertEquals(CHUNKS, chunks.size)
        for (chunk in chunks) {
            assertEquals(recorder.config.chunkSize, chunk.size)
            assertTrue("samples must be finite", chunk.all { it.isFinite() })
        }
    }

    @Test
    fun reusesAPoolOfArrays() = runBlocking {
        val identities = recorder.chunks().take(CHUNKS).toList().map { System.identityHashCode(it) }.toSet()

        assertTrue("expected a small pool, got ${identities.size} arrays", identities.size <= 4)
    }

    @Test
    fun opensTheMicOnlyWhileCollecting() = runBlocking {
        assertNull(recorder.activeInput)
        var inputWhileRecording: AudioInput? = null
        recorder.chunks().onEach { inputWhileRecording = recorder.activeInput }.take(3).toList()

        assertNotNull(inputWhileRecording)
        assertNull("mic must be released after collection", recorder.activeInput)

        // Released properly, so it can be opened again.
        assertEquals(3, recorder.chunks().take(3).toList().size)
    }

    private companion object {
        const val CHUNKS = 20
    }
}
