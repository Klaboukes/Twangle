package io.github.klaboukes.twangle.wear

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders.LaunchAction
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.ActionBuilders.stringExtra
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Twangle tile, beside the watch face. With the metronome's switch off ([watchToolSwitchesFor]): the name,
 * the mark and one Tune button that opens the tuner, a tile that never changes. With it on (Phase 80): the
 * name, a wide Metronome button with the last tempo ("96 BPM", [WatchMetronomeStore]) that opens the
 * metronome page, stopped, and Tune along the bottom edge; the mark makes room. It never listens: the
 * microphone opens only once the tuner is on screen (Hard rule 4).
 *
 * It asks for no updates on a timer: the store asks the watch to fetch it again when the kept tempo changes
 * ([WatchMetronomeStore.of]), and the app does each time it opens.
 *
 * A plain [TileService] with its images handed over as classic resources, rather than the library's
 * `Material3TileService`: that one sends images along with the layout, which the Wear OS 6 emulator's
 * tile host didn't show (2026-10-30), and older watches can't either.
 */
class WatchTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> {
        val device = requestParams.deviceConfiguration
        if (!watchToolSwitchesFor(BuildConfig.DEBUG).metronome) return immediately(twangleTile(this, device, metronomeBpm = null))
        return CallbackToFutureAdapter.getFuture { done ->
            val reading = scope.launch {
                val bpm = WatchMetronomeStore.of(this@WatchTileService).choices.first().bpm
                done.set(twangleTile(this@WatchTileService, device, bpm))
            }
            reading.invokeOnCompletion { failure -> if (failure != null) done.setException(failure) }
            done.addCancellationListener({ reading.cancel() }, Runnable::run)
            "tile"
        }
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        immediately(tileResources())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private fun <T : Any> immediately(value: T): ListenableFuture<T> =
    CallbackToFutureAdapter.getFuture { it.set(value); "tile" }

/** The tile's id for the Tune button, so a test can find it. */
internal const val TILE_TUNE_ID = "tune"

/** The tile's id for the Metronome button (Phase 80). */
internal const val TILE_METRONOME_ID = "metronome"

/** The mark's name in the tile's resources. */
internal const val TILE_MARK_ID = "mark"

/** The Metronome button's icon's name in the tile's resources. */
internal const val TILE_METRONOME_ICON_ID = "metronome_icon"

/**
 * Raised whenever [tileResources] or one of its drawings changes, so watches fetch them again
 * (3: the mark's new overlap colours; 4: the Metronome button's icon).
 */
internal const val TILE_RESOURCES_VERSION = "4"

private const val TILE_MARK_DP = 64f

/**
 * The whole tile for a watch of [device]: with [metronomeBpm] the Metronome button showing that tempo
 * above Tune, without it (the metronome's switch off) the mark above Tune, as before Phase 80.
 */
internal fun twangleTile(context: Context, device: DeviceParameters, metronomeBpm: Int?): Tile = Tile.Builder()
    .setResourcesVersion(TILE_RESOURCES_VERSION)
    .setTileTimeline(
        Timeline.fromLayoutElement(
            materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = watchTileColors()) {
                twangleTileLayout(metronomeBpm)
            },
        ),
    )
    .build()

/**
 * The tile's images: the mark in its colours ([R.drawable.ic_twangle_mark_colour]) under [TILE_MARK_ID], and
 * the metronome ([R.drawable.ic_tile_metronome], tinted by the tile) under [TILE_METRONOME_ICON_ID]. Both are
 * always handed over, so the resources don't depend on the switch.
 */
internal fun tileResources(): Resources = Resources.Builder()
    .setVersion(TILE_RESOURCES_VERSION)
    .addIdToImageMapping(TILE_MARK_ID, imageOf(R.drawable.ic_twangle_mark_colour))
    .addIdToImageMapping(TILE_METRONOME_ICON_ID, imageOf(R.drawable.ic_tile_metronome))
    .build()

private fun imageOf(resourceId: Int): ImageResource = ImageResource.Builder()
    .setAndroidResourceByResId(AndroidImageResourceByResId.Builder().setResourceId(resourceId).build())
    .build()

/**
 * The tile's layout: Twangle at the top and Tune along the bottom edge; between them, with [metronomeBpm],
 * the Metronome button (docs/design/watch-metronome-preview.html, card 4), else the mark in the launcher
 * icon's own colours (an image left as it is, not an icon tinted in the accent, which made it all blue).
 */
internal fun MaterialScope.twangleTileLayout(metronomeBpm: Int?): LayoutElement = primaryLayout(
    titleSlot = { text(LayoutString(context.getString(R.string.app_name))) },
    mainSlot = {
        if (metronomeBpm != null) metronomeButton(metronomeBpm) else mark()
    },
    bottomSlot = {
        textEdgeButton(
            // With the metronome there, Tune says where it goes, as it may find the app on the metronome page.
            onClick = clickable(
                opening(if (metronomeBpm != null) WatchPage.TUNER else null),
                TILE_TUNE_ID,
            ),
            modifier = if (metronomeBpm != null) {
                LayoutModifier.contentDescription(context.getString(R.string.wear_shortcut_open))
            } else {
                LayoutModifier
            },
            labelContent = { text(LayoutString(context.getString(R.string.wear_tile_tune))) },
        )
    },
)

private fun MaterialScope.mark(): LayoutElement = Image.Builder()
    .setResourceId(TILE_MARK_ID)
    .setWidth(dp(TILE_MARK_DP))
    .setHeight(dp(TILE_MARK_DP))
    .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
    .build()

/**
 * The wide Metronome button: the metronome in the accent, "Metronome" in ink and the last tempo muted under
 * it, on the watch's dark surface; TalkBack hears "Open the metronome, 96 beats per minute". It opens the
 * metronome page stopped, at that tempo ([WatchActivity.EXTRA_PAGE]).
 */
private fun MaterialScope.metronomeButton(bpm: Int): LayoutElement {
    val tempo = context.resources.getQuantityString(R.plurals.wear_metronome_tempo, bpm, bpm)
    return button(
        onClick = clickable(opening(WatchPage.METRONOME), TILE_METRONOME_ID),
        modifier = LayoutModifier.contentDescription(context.getString(R.string.wear_tile_open_metronome, tempo)),
        labelContent = { text(LayoutString(context.getString(R.string.wear_tile_metronome))) },
        secondaryLabelContent = { text(LayoutString(context.getString(R.string.wear_tile_tempo, bpm))) },
        iconContent = { icon(TILE_METRONOME_ICON_ID) },
        width = expand(),
        colors = ButtonColors(
            containerColor = WatchColors.surface.layout,
            iconColor = WatchColors.accent.layout,
            labelColor = WatchColors.ink.layout,
            secondaryLabelColor = WatchColors.muted.layout,
        ),
    )
}

/** Opens [WatchActivity], on [page] when one is given (the string extra [WatchActivity.EXTRA_PAGE]). */
private fun MaterialScope.opening(page: WatchPage?): LaunchAction {
    val activity = ComponentName(context, WatchActivity::class.java)
    return if (page == null) {
        launchAction(activity)
    } else {
        launchAction(activity, mapOf(WatchActivity.EXTRA_PAGE to stringExtra(page.name)))
    }
}

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
