package io.github.klaboukes.twangle.core.audio

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.catch
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

/** Records from the real microphone, so it needs a device (and a screen in the foreground). */
@RunWith(AndroidJUnit4::class)
class AndroidAudioRecorderTest {

    @get:Rule
    val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    /** A screen in the foreground: in the background Android silences the microphone. */
    @get:Rule
    val foreground = ActivityScenarioRule(ForegroundActivity::class.java)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val recorder = AndroidAudioRecorder(context)

    @Test
    fun picksASupportedNativeRate() {
        val rate = recorder.config.sampleRate
        assertTrue("rate $rate", rate in SampleRateSelector.SUPPORTED_RATES)
        assertTrue(
            AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) > 0 ||
                AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0,
        )
    }

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
    fun recordsIn16BitWhereFloatIsRefused() = runBlocking {
        // As on a phone that refuses float: the 16-bit path alone, on this phone's real microphone.
        val pcm16 = AndroidAudioRecorder(context, null, listOf(SampleFormat.PCM_16BIT))
        val chunks = pcm16.chunks().map { it.copyOf() }.take(CHUNKS).toList()

        assertEquals(CHUNKS, chunks.size)
        for (chunk in chunks) {
            assertEquals(pcm16.config.chunkSize, chunk.size)
            for (sample in chunk) {
                assertTrue("sample $sample outside -1..1", sample >= -1f && sample < 1f)
                // Every sample is a whole number of 16-bit steps: it came through the conversion.
                val steps = sample * 32_768f
                assertEquals("sample $sample", Math.round(steps).toFloat(), steps, 0f)
            }
        }
        assertNull("mic must be released after collection", pcm16.activeInput)
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

    @Test
    fun missingPermissionEndsTheFlowWithATypedError() = runBlocking {
        // Revoking the real permission would kill the test process, so pretend it was denied.
        val denied = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                PackageManager.PERMISSION_DENIED
        }
        var failure: Throwable? = null
        AndroidAudioRecorder(denied).chunks().catch { failure = it }.toList()

        assertTrue("got $failure", failure is AudioRecorderException.PermissionMissing)
        assertNull(recorder.activeInput)
    }

    private companion object {
        const val CHUNKS = 20
    }
}
