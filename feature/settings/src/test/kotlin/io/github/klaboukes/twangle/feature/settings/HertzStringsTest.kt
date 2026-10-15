package io.github.klaboukes.twangle.feature.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "442 Hz" never breaks across lines: in every language a number and its unit are joined by a no-break space. */
class HertzStringsTest {

    @Test
    fun `numbers and hertz are joined by a no-break space in every language`() {
        val files = File("src/main/res").listFiles { dir -> dir.name.startsWith("values") }!!
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
        assertTrue(files.size > 1)
        // A number (Latin or Arabic-Indic digits, not the "4" of "A4") or a number placeholder, a plain
        // space, then hertz.
        val breakable = Regex("""(?<![A-Z])(\d|%\d\${'$'}d|[٠-٩]) (Hz|Гц|هرتز)""")
        val found = files.flatMap { file -> breakable.findAll(file.readText()).map { "${file.parentFile.name}: ${it.value}" } }
        assertEquals(emptyList<String>(), found)
    }
}
