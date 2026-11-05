package io.github.klaboukes.twangle.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The tile and the watch-face shortcut as the watch sees them in the manifest, their texts, and
 * that neither of them can listen: only the tuner screen opens the microphone (Hard rule 4).
 */
class WatchShortcutsTest {

    // Gradle runs unit tests in the module folder.
    private val manifest = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))

    private fun Element.android(name: String) = getAttributeNS(ANDROID, name)

    private fun service(name: String): Element {
        val services = manifest.getElementsByTagName("service")
        return (0 until services.length).map { services.item(it) as Element }
            .single { it.android("name") == name }
    }

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.metaData(name: String): Element = children("meta-data").single { it.android("name") == name }

    @Test
    fun theTileIsBoundOnlyByTheTileHostAndHasAPreview() {
        val tile = service(".WatchTileService")
        assertEquals("true", tile.android("exported"))
        assertEquals("com.google.android.wearable.permission.BIND_TILE_PROVIDER", tile.android("permission"))
        assertEquals(
            listOf("androidx.wear.tiles.action.BIND_TILE_PROVIDER"),
            tile.children("action").map { it.android("name") },
        )
        assertEquals("@drawable/tile_preview", tile.metaData("androidx.wear.tiles.PREVIEW").android("resource"))
    }

    @Test
    fun theShortcutIsBoundOnlyByTheWatchFaceFillsItsSlotTypesAndNeverUpdates() {
        val shortcut = service(".WatchShortcutService")
        assertEquals("true", shortcut.android("exported"))
        assertEquals("com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER", shortcut.android("permission"))
        assertEquals(
            listOf("android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"),
            shortcut.children("action").map { it.android("name") },
        )
        assertEquals(
            SHORTCUT_TYPES.map { it.name },
            shortcut.metaData("android.support.wearable.complications.SUPPORTED_TYPES").android("value").split(","),
        )
        assertEquals("0", shortcut.metaData("android.support.wearable.complications.UPDATE_PERIOD_SECONDS").android("value"))
    }

    @Test
    fun theMicrophoneIsStillTheOnlyPermissionAskedFor() {
        // VIBRATE (the in-tune tap) is granted at install and never asked for; no INTERNET.
        val permissions = manifest.getElementsByTagName("uses-permission")
        assertEquals(
            listOf("android.permission.RECORD_AUDIO", "android.permission.VIBRATE"),
            (0 until permissions.length).map { (permissions.item(it) as Element).android("name") },
        )
    }

    @Test
    fun neitherTheTileNorTheShortcutTouchesTheMicrophone() {
        for (file in listOf("WatchTile.kt", "WatchShortcut.kt")) {
            val code = File(SOURCES, file).readText()
                .lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                .joinToString("\n")
            for (listening in listOf("AudioRecorder", "AudioRecord", "WatchTunerViewModel", "WatchPitchPipeline")) {
                assertFalse("$file uses $listening", code.contains(listening))
            }
        }
    }

    @Test
    fun theTileAndShortcutHaveTheirTextsInEnglishAndDutch() {
        for (values in listOf("values", "values-nl")) {
            val texts = File("src/main/res/$values/strings.xml").readText()
            for (name in listOf("wear_tile_tune", "wear_tile_metronome", "wear_tile_open_metronome", "wear_shortcut_tune", "wear_shortcut_open")) {
                assertNotNull("$name in $values", Regex("name=\"$name\"").find(texts))
            }
        }
        // "96 BPM" is the same in every language, as "BPM" under the metronome's tempo.
        assertNotNull(Regex("name=\"wear_tile_tempo\" translatable=\"false\"").find(File("src/main/res/values/strings.xml").readText()))
    }

    @Test
    fun theTilesButtonsReachTheOpenAppInsteadOfStackingASecondOne() {
        // In the standard mode a tile launch after the launcher's put a second activity on top (2026-11-05).
        val activities = manifest.getElementsByTagName("activity")
        val watch = (0 until activities.length).map { activities.item(it) as Element }.single { it.android("name") == ".WatchActivity" }
        assertEquals("singleTask", watch.android("launchMode"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val SOURCES = File("src/main/kotlin/io/github/klaboukes/twangle/wear")
    }
}
