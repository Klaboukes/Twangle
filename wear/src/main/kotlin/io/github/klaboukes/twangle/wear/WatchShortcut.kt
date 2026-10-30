package io.github.klaboukes.twangle.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * The watch-face shortcut: a complication (a small slot a watch face offers) with Twangle's mark
 * that opens the tuner with one tap. It shows the same thing all day, so it asks for no updates
 * (`UPDATE_PERIOD_SECONDS` 0 in the manifest), and it never listens: the microphone opens only once
 * [WatchActivity] is on screen (Hard rule 4).
 */
class WatchShortcutService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        shortcutData(this, request.complicationType)

    override fun getPreviewData(type: ComplicationType): ComplicationData? = shortcutData(this, type)
}

/** The slot types the shortcut fills; the manifest lists the same ones. */
internal val SHORTCUT_TYPES = listOf(
    ComplicationType.SHORT_TEXT,
    ComplicationType.MONOCHROMATIC_IMAGE,
    ComplicationType.SMALL_IMAGE,
)

/**
 * What the shortcut shows in a slot of [type], or null for a type it doesn't fill: the mark with
 * "Tune" in a short-text slot ("Twangle" was cut to "TWAN…" on the emulator's watch face), the mark alone (tinted by the watch face) in an icon slot, and
 * the launcher icon in a small-image slot. Every one opens the tuner.
 */
internal fun shortcutData(context: Context, type: ComplicationType): ComplicationData? {
    val description = PlainComplicationText.Builder(context.getString(R.string.wear_shortcut_open)).build()
    val mark = MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_twangle_mark)).build()
    val openTuner = PendingIntent.getActivity(
        context,
        0,
        Intent(context, WatchActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(context.getString(R.string.wear_shortcut_tune)).build(),
            description,
        ).setMonochromaticImage(mark).setTapAction(openTuner).build()

        ComplicationType.MONOCHROMATIC_IMAGE ->
            MonochromaticImageComplicationData.Builder(mark, description).setTapAction(openTuner).build()

        ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
            SmallImage.Builder(Icon.createWithResource(context, R.mipmap.ic_launcher), SmallImageType.ICON).build(),
            description,
        ).setTapAction(openTuner).build()

        else -> null
    }
}
