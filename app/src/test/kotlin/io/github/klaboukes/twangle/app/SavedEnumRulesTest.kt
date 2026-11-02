package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every enum the settings are saved by name (`DataStoreSettingsRepository`: `enumOrNull<…>`,
 * `enumSetOrNull<…>` and the setters' `.name`) has its constants kept in `app/proguard-rules.pro`, so
 * a release build's shrinking can never make a saved setting stop matching. A new saved enum fails
 * this test until its rule is added (six were missing until Phase 65).
 */
class SavedEnumRulesTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val repository = File(
        root,
        "core/settings/src/main/kotlin/io/github/klaboukes/twangle/core/settings/DataStoreSettingsRepository.kt",
    ).readText()

    private val rules = File(root, "app/proguard-rules.pro").readLines()

    /** The simple names of the enums kept by a `-keepclassmembers enum` rule. */
    private val kept: Set<String> = rules
        .mapNotNull { KEEP_RULE.find(it)?.groupValues?.get(1) }
        .map { it.substringAfterLast('.') }
        .toSet()

    @Test
    fun everyEnumReadByNameIsKept() {
        // The enums read through enumOrNull / enumSetOrNull, plus those read by a helper of their own.
        // (Leaving out the helpers' own type parameter, E.)
        val read = READ.findAll(repository).map { it.groupValues[1] }.filter { it.length > 1 }.toSet() + HELPER_READ
        assertTrue("the scan found the saved enums", read.size >= 20)
        assertEquals("saved enums without a keep rule in app/proguard-rules.pro", emptySet<String>(), read - kept)
    }

    private companion object {
        val KEEP_RULE = Regex("""^-keepclassmembers enum ([\w.]+) \{ public static final \*\* \*; }$""")
        val READ = Regex("""enum(?:Set)?OrNull<(\w+)>""")

        /** Read through waveLookOrNull and meterLookOrNull, which map old names first. */
        val HELPER_READ = setOf("WaveLook", "MeterLook")
    }
}
