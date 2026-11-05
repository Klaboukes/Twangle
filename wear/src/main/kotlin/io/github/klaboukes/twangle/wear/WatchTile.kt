package io.github.klaboukes.twangle.wear

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * The Twangle tile, beside the watch face: the name, the mark and one Tune button that opens the
 * tuner. It never listens: the microphone opens only once [WatchActivity] is on screen (Hard rule 4).
 * It never changes either, so it asks for no updates.
 *
 * A plain [TileService] with the mark handed over as a classic resource, rather than the library's
 * `Material3TileService`: that one sends images along with the layout, which the Wear OS 6 emulator's
 * tile host didn't show (2026-10-30), and older watches can't either.
 */
class WatchTileService : TileService() {

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        immediately(tuneTile(this, requestParams.deviceConfiguration))

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        immediately(tileResources())
}

private fun <T : Any> immediately(value: T): ListenableFuture<T> =
    CallbackToFutureAdapter.getFuture { it.set(value); "tile" }

/** The tile's id for the Tune button, so a test can find it. */
internal const val TILE_TUNE_ID = "tune"

/** The mark's name in the tile's resources. */
internal const val TILE_MARK_ID = "mark"

/** Raised whenever [tileResources] or the mark's drawing changes, so watches fetch them again (3: the new overlap colours). */
internal const val TILE_RESOURCES_VERSION = "3"

private const val TILE_MARK_DP = 64f

/** The whole tile for a watch of [device]: one layout that never changes. */
internal fun tuneTile(context: Context, device: DeviceParameters): Tile = Tile.Builder()
    .setResourcesVersion(TILE_RESOURCES_VERSION)
    .setTileTimeline(
        Timeline.fromLayoutElement(
            materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = watchTileColors()) {
                tuneTileLayout()
            },
        ),
    )
    .build()

/** The tile's one image, the mark in its colours ([R.drawable.ic_twangle_mark_colour]), under [TILE_MARK_ID]. */
internal fun tileResources(): Resources = Resources.Builder()
    .setVersion(TILE_RESOURCES_VERSION)
    .addIdToImageMapping(
        TILE_MARK_ID,
        ImageResource.Builder()
            .setAndroidResourceByResId(
                AndroidImageResourceByResId.Builder().setResourceId(R.drawable.ic_twangle_mark_colour).build(),
            )
            .build(),
    )
    .build()

/**
 * The tile's layout: Twangle at the top, the mark in the middle in the launcher icon's own colours (an image
 * left as it is, not an icon tinted in the accent, which made it all blue), Tune along the bottom edge.
 */
internal fun MaterialScope.tuneTileLayout(): LayoutElement = primaryLayout(
    titleSlot = { text(LayoutString(context.getString(R.string.app_name))) },
    mainSlot = {
        Image.Builder()
            .setResourceId(TILE_MARK_ID)
            .setWidth(dp(TILE_MARK_DP))
            .setHeight(dp(TILE_MARK_DP))
            .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
            .build()
    },
    bottomSlot = {
        textEdgeButton(
            onClick = clickable(
                launchAction(ComponentName(context, WatchActivity::class.java)),
                TILE_TUNE_ID,
            ),
            labelContent = { text(LayoutString(context.getString(R.string.wear_tile_tune))) },
        )
    },
)

/**
 * The tile in the watch's colours ([WatchColors], the phone's Ink, dark), not the watch's
 * own dynamic theme: the accent for the button, ink for the name, on black.
 */
internal fun watchTileColors(): ColorScheme = ColorScheme(
    primary = WatchColors.accent.layout,
    onPrimary = WatchColors.onAccent.layout,
    onSurface = WatchColors.ink.layout,
    onSurfaceVariant = WatchColors.muted.layout,
    background = WatchColors.background.layout,
    onBackground = WatchColors.ink.layout,
)

private val Color.layout: LayoutColor get() = LayoutColor(toArgb())
