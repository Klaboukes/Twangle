package io.github.klaboukes.twangle.app.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The Play Store's icon and feature graphics (docs/store-listing.md, *Icon and feature graphic*),
 * made on the phone from the app itself, so they always match it and can be made again:
 *
 * - `icon.png`: the launcher icon ([R.mipmap.ic_launcher], an adaptive icon) at [ICON_PX] × [ICON_PX],
 *   its background and foreground layers drawn as a full square, without a mask: Google adds the round
 *   corners and the shadow itself. It shows the middle [ICON_VIEW_DP] dp of the layers' 108 dp: the
 *   whole canvas left the mark small and thin in the yellow square, the launcher's own 72 dp crowded
 *   it against Play's round corners. 32-bit (with alpha), as Play asks.
 * - `featureGraphic-<Play locale>.png`, one for each store language: [FeatureGraphic] at
 *   [FEATURE_WIDTH] × [FEATURE_HEIGHT] with that language's line, without alpha (Play takes a 24-bit PNG).
 *
 * Opt-in, as it only makes files: without the instrumentation argument `storeGraphics=true` it has no
 * cases. `storeGraphicLines` carries every language's line in Base64 (the shell would mangle other
 * scripts), a line `<Play locale>\t<line>` each. `scripts/store-graphics.sh` passes both and puts the
 * pictures where Play and F-Droid look for them. Changes nothing on the phone.
 */
@RunWith(Parameterized::class)
class StoreGraphics(private val picture: Picture) {

    /** The icon ([locale] null), or one language's feature graphic. */
    data class Picture(val locale: String?, val line: String = "") {
        val file: String get() = if (locale == null) "icon.png" else "featureGraphic-$locale.png"

        override fun toString(): String = locale?.let { "featureGraphic $it" } ?: "icon"
    }

    @get:Rule
    val timeout: Timeout = Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val out = File(arguments.getString(OUTPUT_DIR_ARGUMENT) ?: error("no $OUTPUT_DIR_ARGUMENT")).apply { mkdirs() }

    @Test
    fun picture() {
        val locale = picture.locale
        val bitmap = if (locale == null) {
            icon()
        } else {
            val icon = icon().asImageBitmap()
            // Play's Norwegian folder is no-NO; the language is Norwegian Bokmål.
            val language = Locale.forLanguageTag(if (locale == "no-NO") "nb-NO" else locale)
            renderCanvas(FEATURE_WIDTH, FEATURE_HEIGHT) { FeatureGraphic(icon, picture.line, language) }.apply {
                // Opaque, so the PNG is written without an alpha channel.
                setHasAlpha(false)
            }
        }
        File(out, picture.file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The middle [ICON_VIEW_DP] dp of the launcher icon's two layers, as a full square. */
    private fun icon(): Bitmap {
        val context = instrumentation.targetContext
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as? AdaptiveIconDrawable
            ?: error("the launcher icon is not an adaptive icon")
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // The layers drawn larger than the picture, so their middle fills it.
        val whole = (ICON_PX * LAYER_DP / ICON_VIEW_DP).toInt()
        val inset = (ICON_PX - whole) / 2
        listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
            layer.setBounds(inset, inset, inset + whole, inset + whole)
            layer.draw(canvas)
        }
        return bitmap
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun pictures(): List<Picture> {
            val arguments = InstrumentationRegistry.getArguments()
            if (arguments.getString(ARGUMENT) != "true") return emptyList()
            val lines = arguments.getString(LINES_ARGUMENT)?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.orEmpty()
            val graphics = lines.replace("\r\n", "\n").lines().filter { it.isNotBlank() }.map { row ->
                val (locale, line) = row.split('\t', limit = 2)
                Picture(locale, line.trim())
            }
            return listOf(Picture(null)) + graphics
        }

        /** The instrumentation argument that turns the pictures on. */
        const val ARGUMENT = "storeGraphics"
        const val LINES_ARGUMENT = "storeGraphicLines"
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"

        /** Play's app icon, exactly. */
        const val ICON_PX = 512

        /** An adaptive icon's layers are 108 dp square; launchers show the middle 72 dp. */
        private const val LAYER_DP = 108f

        /** How much of the layers the store icon shows: the mark fills about three quarters of its width. */
        private const val ICON_VIEW_DP = 90f

        private const val TEST_TIMEOUT_SECONDS = 60L
    }
}
