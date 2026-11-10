package io.github.klaboukes.twangle.app.widget

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.app.FocusModeOffRule
import io.github.klaboukes.twangle.app.MainActivity
import io.github.klaboukes.twangle.app.R
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.designsystem.tokensFor
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.github.klaboukes.twangle.feature.tuner.R as TunerR

/**
 * The home-screen widget (2.0's look, Phase 75): drawn as the launcher draws it, it names the
 * instrument and tuning in a chip like the tuner's picker chip, with "Open the tuner" under it, in
 * Ink's colours, light or dark (the picker's preview too), and opens nothing by itself (the
 * microphone stays off); its tap brings the open app back to the tuner.
 *
 * The app runs as itself here (`ActivityScenario`, no Compose test rule): its opening screen waits
 * for the first real frame, which a Compose rule's test clock would hold back.
 */
@RunWith(AndroidJUnit4::class)
class TuningWidgetTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    /** Focus mode's full-screen tuner hides the tabs a test opens Settings from. */
    @get:Rule
    val focusModeOff = FocusModeOffRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    /** The widget for [settings], applied to a view as a launcher would, on a phone whose text is [fontScale] times as big. */
    private fun drawn(settings: AppSettings, fontScale: Float? = null): View {
        val views = TuningWidget.views(context, WidgetState.from(settings))
        val launcher = fontScale?.let { scale ->
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { this.fontScale = scale })
        } ?: context
        lateinit var view: View
        instrumentation.runOnMainSync { view = views.apply(launcher, FrameLayout(launcher)) }
        return view
    }

    /** The colour an image of the widget is drawn in (its shape tinted by its colour filter): its middle, drawn alone. */
    private fun View.tint(id: Int): Int {
        val image = findViewById<ImageView>(id)
        val bitmap = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync {
            image.measure(
                View.MeasureSpec.makeMeasureSpec(SAMPLE_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(SAMPLE_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            image.layout(0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
            image.draw(Canvas(bitmap))
        }
        return bitmap.getPixel(SAMPLE_WIDTH / 2, SAMPLE_HEIGHT / 2)
    }

    @Test
    fun namesTheInstrumentAndTuningInAChipInInksColours() {
        // Ink's own colours, said outright: the wallpaper's (when picked) differ from phone to phone.
        val view = drawn(AppSettings(tuning = Tunings.DROP_D, theme = ThemeMode.DARK, dynamicColor = false))
        val title = view.findViewById<TextView>(R.id.widget_title)
        val guitar = context.getString(TunerR.string.instrument_guitar)
        val dropD = context.getString(TunerR.string.tuning_drop_d)
        assertEquals(context.getString(R.string.widget_title, guitar, dropD), title.text.toString())
        assertEquals(context.getString(TunerR.string.tuner_spoken_picker, guitar, dropD), title.contentDescription)
        val scheme = tokensFor(dark = true).scheme
        assertEquals(scheme.onSurface.toArgb(), title.currentTextColor)
        val subtitle = view.findViewById<TextView>(R.id.widget_subtitle)
        assertEquals(context.getString(R.string.widget_open_tuner), subtitle.text.toString())
        assertEquals(scheme.onSurfaceVariant.toArgb(), subtitle.currentTextColor)
        assertEquals("the chip's fill", scheme.surface.toArgb(), view.tint(R.id.widget_chip))
        assertEquals("the chip's line", scheme.outlineVariant.toArgb(), view.tint(R.id.widget_chip_line))
        assertEquals("the tile", scheme.background.toArgb(), view.tint(R.id.widget_backdrop))
        assertTrue("the whole tile can be tapped", view.findViewById<View>(android.R.id.background).hasOnClickListeners())
    }

    @Test
    fun inTheLightThemeItIsInkLight() {
        val view = drawn(AppSettings(theme = ThemeMode.LIGHT, dynamicColor = false))
        val scheme = tokensFor(dark = false).scheme
        assertEquals(scheme.onSurface.toArgb(), view.findViewById<TextView>(R.id.widget_title).currentTextColor)
        assertEquals(scheme.onSurfaceVariant.toArgb(), view.findViewById<TextView>(R.id.widget_subtitle).currentTextColor)
        assertEquals(scheme.surface.toArgb(), view.tint(R.id.widget_chip))
        assertEquals(scheme.outlineVariant.toArgb(), view.tint(R.id.widget_chip_line))
        assertEquals(scheme.background.toArgb(), view.tint(R.id.widget_backdrop))
    }

    /** The widget picker shows the layout's own colours before the app draws it: Ink's too, light and dark. */
    @Test
    fun thePickersPreviewIsInInk() {
        for (dark in listOf(false, true)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val preview = context.createConfigurationContext(configuration)
            val scheme = tokensFor(dark).scheme
            val case = if (dark) "dark" else "light"
            assertEquals("$case background", scheme.background.toArgb(), preview.getColor(R.color.widget_background))
            assertEquals("$case chip", scheme.surface.toArgb(), preview.getColor(R.color.widget_surface))
            assertEquals("$case chip's line", scheme.outlineVariant.toArgb(), preview.getColor(R.color.widget_line))
            assertEquals("$case ink", scheme.onSurface.toArgb(), preview.getColor(R.color.widget_ink))
            assertEquals("$case muted", scheme.onSurfaceVariant.toArgb(), preview.getColor(R.color.widget_muted))
        }
    }

    // Until Phase 89 the chip was 30 dp high whatever the text's size, so at 2× its words were cut off.
    @Test
    fun theChipGrowsWithTheTextAtTheLargestFont() {
        val density = context.resources.displayMetrics.density
        for (scale in listOf(1f, 2f)) {
            val widget = drawn(AppSettings(), fontScale = scale)
            val title = widget.findViewById<TextView>(R.id.widget_title)
            // Laid out as the launcher lays out a tile of three cells by one, the layout's own sizes applying.
            instrumentation.runOnMainSync {
                widget.measure(
                    View.MeasureSpec.makeMeasureSpec((WIDGET_WIDTH_DP * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((WIDGET_HEIGHT_DP * density).toInt(), View.MeasureSpec.EXACTLY),
                )
                widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
            }
            assertEquals("text at ${scale}×", 14f * scale * density, title.textSize, density)
            val words = title.paint.fontMetricsInt.let { it.descent - it.ascent }
            assertTrue(
                "at ${scale}× the chip is ${title.height} px for words $words px high",
                title.height >= words + title.paddingTop + title.paddingBottom,
            )
            assertTrue("at least 30 dp at ${scale}×", title.height >= (30 * density).toInt())
        }
    }

    @Test
    fun chromaticModeSaysAnyNote() {
        val title = drawn(AppSettings(mode = TunerMode.CHROMATIC)).findViewById<TextView>(R.id.widget_title)
        assertEquals(context.getString(TunerR.string.tuner_any_note), title.text.toString())
    }

    @Test
    fun drawingTheWidgetLeavesTheMicrophoneAlone() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertTrue("released before the check", waitFor(8_000) { audio.activeRecordingConfigurations.isEmpty() })
        repeat(3) { drawn(AppSettings()) }
        TuningWidget.update(context, WidgetState.from(AppSettings()))
        SystemClock.sleep(500)
        assertTrue("no recording after drawing the widget", audio.activeRecordingConfigurations.isEmpty())
    }

    @Test
    fun aTapOpensTheTunerFromAnotherScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The gear opens Settings (Phase 69), which has no bar.
            val gear = NavigationTestTags.SETTINGS
            val tuner = NavigationTestTags.tab(Destination.TUNER)
            assertTrue("the app opens", waitFor(5_000) { tab(gear) != null })
            tab(gear)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            assertTrue("Settings opens", waitFor(3_000) { tab(tuner) == null })

            TuningWidget.openTuner(context).send()
            assertTrue("the widget's tap shows the tuner", waitFor(5_000) { tab(tuner)?.isSelected == true })
        }
    }

    /** The navigation bar's tab with test tag [tag] (a resource id, see `MainActivity`), if shown. */
    private fun tab(tag: String): AccessibilityNodeInfo? {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        return automation.rootInActiveWindow?.find { it.viewIdResourceName == tag }
    }

    /** The first node at or under [this] that matches [predicate], depth first. */
    private fun AccessibilityNodeInfo.find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(this)) return this
        for (i in 0 until childCount) getChild(i)?.find(predicate)?.let { return it }
        return null
    }

    /** Waits up to [timeoutMs] for [condition]; returns whether it came true. */
    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    private companion object {
        /** The size each image is drawn at to read its colour: a small pill. */
        const val SAMPLE_WIDTH = 120
        const val SAMPLE_HEIGHT = 40

        /** A tile of three cells by one on a phone, as the launcher gives the widget. */
        const val WIDGET_WIDTH_DP = 250
        const val WIDGET_HEIGHT_DP = 110
    }
}
