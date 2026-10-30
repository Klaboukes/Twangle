package io.github.klaboukes.twangle.wear

import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.watchface.complications.data.ComplicationType
import org.junit.Assert.assertEquals
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

    @Test
    fun theTileHasOneTuneButtonThatOpensTheTuner() {
        val tile = tuneTile(context, roundWatch)
        // The builders' own description of the layout, e.g. "Clickable{id=tune, onClick=LaunchAction{...}}".
        val layout = tile.tileTimeline!!.timelineEntries.single().layout!!.root!!.toString()

        assertEquals("one button", 1, Regex("LaunchAction\\{").findAll(layout).count())
        assertTrue(
            "the Tune button opens the tuner",
            layout.contains(
                "Clickable{id=$TILE_TUNE_ID, onClick=LaunchAction{androidActivity=AndroidActivity{" +
                    "packageName=${context.packageName}, className=${WatchActivity::class.java.name}",
            ),
        )
        assertTrue("says Tune", layout.contains("StringProp{value=Tune,"))
        assertTrue("says Twangle", layout.contains("StringProp{value=Twangle,"))
        assertTrue("shows the mark", layout.contains("value=$TILE_MARK_ID,"))
        assertEquals(TILE_RESOURCES_VERSION, tile.resourcesVersion)
    }

    @Test
    fun theTileHandsOverItsMark() {
        val resources = tileResources()
        assertEquals(TILE_RESOURCES_VERSION, resources.version)
        assertEquals(
            R.drawable.ic_twangle_mark,
            resources.idToImageMapping[TILE_MARK_ID]!!.androidResourceByResId!!.resourceId,
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
