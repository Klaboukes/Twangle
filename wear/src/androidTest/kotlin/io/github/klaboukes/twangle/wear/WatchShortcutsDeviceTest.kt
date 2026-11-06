package io.github.klaboukes.twangle.wear

import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.watchface.complications.data.ComplicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The tile's layout and colours and the shortcut's slot contents, built on the watch (Wear OS emulator). */
@RunWith(AndroidJUnit4::class)
class WatchShortcutsDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val roundWatch: DeviceParameters = DeviceParameters.Builder()
        .setScreenWidthDp(227)
        .setScreenHeightDp(227)
        .setScreenDensity(2f)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .build()

    /** The builders' own description of the tile's layout, e.g. "Clickable{id=tune, onClick=LaunchAction{...}}". */
    private fun layoutOf(metronomeBpm: Int?): String =
        twangleTile(context, roundWatch, metronomeBpm).tileTimeline!!.timelineEntries.single().layout!!.root!!.toString()

    /** The clickable [id]'s description, from its id to the next clickable's (or the end). */
    private fun String.clickable(id: String): String {
        val start = indexOf("Clickable{id=$id,")
        assertTrue("a button $id", start >= 0)
        val next = indexOf("Clickable{id=", start + 1)
        return if (next < 0) substring(start) else substring(start, next)
    }

    private val opensTheApp
        get() = "onClick=LaunchAction{androidActivity=AndroidActivity{" +
            "packageName=${context.packageName}, className=${WatchActivity::class.java.name}"

    @Test
    fun theTileWithoutTheMetronomeHasOneTuneButtonThatOpensTheTuner() {
        val tile = twangleTile(context, roundWatch, metronomeBpm = null)
        val layout = layoutOf(metronomeBpm = null)

        assertEquals("one button", 1, Regex("LaunchAction\\{").findAll(layout).count())
        val tune = layout.clickable(TILE_TUNE_ID)
        assertTrue("the Tune button opens the app", tune.contains(opensTheApp))
        assertFalse("on no page in particular, as before Phase 80", tune.contains(WatchActivity.EXTRA_PAGE))
        assertTrue("says Tune", layout.contains("StringProp{value=Tune,"))
        assertTrue("says Twangle", layout.contains("StringProp{value=Twangle,"))
        assertTrue("shows the mark", layout.contains("value=$TILE_MARK_ID,"))
        assertFalse("no Metronome", layout.contains("Metronome"))
        assertEquals(TILE_RESOURCES_VERSION, tile.resourcesVersion)
    }

    @Test
    fun theTileWithTheMetronomeOpensTheMetronomeAtTheLastTempoAndTuneTheTuner() {
        val layout = layoutOf(metronomeBpm = 96)

        assertEquals("two buttons", 2, Regex("LaunchAction\\{").findAll(layout).count())
        val metronome = layout.clickable(TILE_METRONOME_ID)
        assertTrue("Metronome opens the app", metronome.contains(opensTheApp))
        assertTrue("on the metronome page", metronome.contains(WatchActivity.EXTRA_PAGE) && metronome.contains("value=METRONOME"))
        val tune = layout.clickable(TILE_TUNE_ID)
        assertTrue("Tune opens the app", tune.contains(opensTheApp))
        assertTrue("on the tuner page", tune.contains(WatchActivity.EXTRA_PAGE) && tune.contains("value=TUNER"))

        assertTrue("says Metronome", layout.contains("StringProp{value=${text(R.string.wear_tile_metronome)},"))
        assertTrue("with the tempo", layout.contains("StringProp{value=${text(R.string.wear_tile_tempo, 96)},"))
        assertTrue("says Tune", layout.contains("StringProp{value=${text(R.string.wear_tile_tune)},"))
        assertTrue("says Twangle", layout.contains("StringProp{value=Twangle,"))
        assertTrue("TalkBack hears the Metronome button", layout.contains(spokenMetronome(96)))
        assertTrue("and Tune's", layout.contains(text(R.string.wear_shortcut_open)))
        assertTrue("shows the metronome icon", layout.contains("value=$TILE_METRONOME_ICON_ID,"))
        assertFalse("the mark makes room", layout.contains("value=$TILE_MARK_ID,"))
    }

    @Test
    fun theTileShowsTheTempoItIsGiven() {
        assertTrue(layoutOf(metronomeBpm = 30).contains("StringProp{value=${text(R.string.wear_tile_tempo, 30)},"))
        assertTrue(layoutOf(metronomeBpm = 250).contains("StringProp{value=${text(R.string.wear_tile_tempo, 250)},"))
        assertTrue(layoutOf(metronomeBpm = 250).contains(spokenMetronome(250)))
    }

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** What TalkBack hears for the Metronome button, e.g. "Open the metronome, 96 beats per minute". */
    private fun spokenMetronome(bpm: Int): String = context.getString(
        R.string.wear_tile_open_metronome,
        context.resources.getQuantityString(R.plurals.wear_metronome_tempo, bpm, bpm),
    )

    @Test
    fun theTileHandsOverItsImages() {
        val resources = tileResources()
        assertEquals(TILE_RESOURCES_VERSION, resources.version)
        assertEquals(
            R.drawable.ic_twangle_mark_colour,
            resources.idToImageMapping[TILE_MARK_ID]!!.androidResourceByResId!!.resourceId,
        )
        assertEquals(
            R.drawable.ic_tile_metronome,
            resources.idToImageMapping[TILE_METRONOME_ICON_ID]!!.androidResourceByResId!!.resourceId,
        )
    }

    @Test
    // The protolayout colour scheme needs Android, so this runs on the watch, not the JVM.
    fun theTileIsInTheWatchsColours() {
        val colors = watchTileColors()
        assertEquals(WatchColors.accent.toArgb(), colors.primary.staticArgb)
        assertEquals(WatchColors.onAccent.toArgb(), colors.onPrimary.staticArgb)
        assertEquals(WatchColors.ink.toArgb(), colors.onSurface.staticArgb)
        assertEquals(WatchColors.ink.toArgb(), colors.onBackground.staticArgb)
        assertEquals(WatchColors.muted.toArgb(), colors.onSurfaceVariant.staticArgb)
        assertEquals(WatchColors.background.toArgb(), colors.background.staticArgb)
    }

    @Test
    fun theShortcutFillsItsSlotsAndEveryOneOpensTheTuner() {
        for (type in SHORTCUT_TYPES) {
            val data = shortcutData(context, type)
            assertNotNull("$type", data)
            assertEquals(type, data!!.type)
            val tap = data.tapAction
            assertNotNull("$type opens something", tap)
            assertEquals(context.packageName, tap!!.creatorPackage)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) assertTrue("$type opens an activity", tap.isActivity)
        }
    }

    @Test
    fun theShortcutLeavesOtherSlotsAlone() {
        for (type in listOf(ComplicationType.LONG_TEXT, ComplicationType.RANGED_VALUE, ComplicationType.PHOTO_IMAGE)) {
            assertNull("$type", shortcutData(context, type))
        }
    }
}
