package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WatchSpokenReadingTest {

    private fun reading(cents: Int, inTune: Boolean = false, pitchClass: PitchClass = PitchClass.E, octave: Int = 2) =
        WatchTunerState.Detected(Note.of(pitchClass, octave), cents, inTune)

    @Test
    fun `flat, sharp, in tune and right on the note are four situations`() {
        val keys = setOf(
            watchReadingKey(reading(-12)),
            watchReadingKey(reading(5)),
            watchReadingKey(reading(-2, inTune = true)),
            watchReadingKey(reading(0)),
        )
        assertEquals(4, keys.size)
    }

    @Test
    fun `only the cents moving keeps the situation`() {
        assertEquals(watchReadingKey(reading(-12)), watchReadingKey(reading(-30)))
        assertEquals(watchReadingKey(reading(-4, inTune = true)), watchReadingKey(reading(3, inTune = true)))
    }

    @Test
    fun `another note or octave is another situation`() {
        assertNotEquals(watchReadingKey(reading(-12)), watchReadingKey(reading(-12, pitchClass = PitchClass.A)))
        assertNotEquals(watchReadingKey(reading(-12)), watchReadingKey(reading(-12, octave = 3)))
    }

    @Test
    fun `turning past the note is said after 400 ms, as on the phone`() {
        val throttle = SpokenReadingThrottle()
        throttle.publish("E 2, 12 cents flat, tune up", watchReadingKey(reading(-12)), 0)
        assertEquals(
            SpokenReadingThrottle.DEFAULT_SETTLE_MILLIS,
            throttle.delayFor("E 2, 9 cents sharp, tune down", watchReadingKey(reading(9)), 100),
        )
        // Only the cents moving: at most every 2 s.
        assertEquals(1_900L, throttle.delayFor("E 2, 10 cents flat, tune up", watchReadingKey(reading(-10)), 100))
    }

    /** The watch says note names as the phone does: its `speech.xml` is a copy of the phone's, every language. */
    @Test
    fun `the watch's speech settings match the phone's in every language`() {
        // Gradle runs unit tests in the module folder.
        val phone = speechValues(File("../core/designsystem/src/main/res"), prefix = "spoken_")
        val watch = speechValues(File("src/main/res"), prefix = "wear_spoken_")
        assertEquals(phone, watch)
    }

    /** Folder → (name without [prefix] → value) of every speech.xml in a values folder under [res]. */
    private fun speechValues(res: File, prefix: String): Map<String, Map<String, String>> {
        val files = res.listFiles { dir -> dir.name.startsWith("values") }.orEmpty().map { File(it, "speech.xml") }.filter { it.exists() }
        check(files.isNotEmpty()) { "no speech.xml under $res" }
        return files.associate { file ->
            val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            file.parentFile.name to (0 until strings.length).associate { i ->
                val element = strings.item(i)
                element.attributes.getNamedItem("name").nodeValue.removePrefix(prefix) to element.textContent
            }
        }
    }
}
