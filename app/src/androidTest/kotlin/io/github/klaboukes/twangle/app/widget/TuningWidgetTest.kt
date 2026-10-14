package io.github.klaboukes.twangle.app.widget

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.klaboukes.twangle.app.MainActivity
import io.github.klaboukes.twangle.app.R
import io.github.klaboukes.twangle.app.navigation.Destination
import io.github.klaboukes.twangle.app.navigation.NavigationTestTags
import io.github.klaboukes.twangle.core.designsystem.tokensFor
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.github.klaboukes.twangle.feature.tuner.R as TunerR

/**
 * The home-screen widget: drawn as the launcher draws it, it names the instrument and tuning in the
 * style's colours and opens nothing by itself (the microphone stays off); its tap brings the open
 * app back to the tuner.
 *
 * The app runs as itself here (`ActivityScenario`, no Compose test rule): its opening screen waits
 * for the first real frame, which a Compose rule's test clock would hold back.
 */
@RunWith(AndroidJUnit4::class)
class TuningWidgetTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    /** The widget for [settings], applied to a view as a launcher would. */
    private fun drawn(settings: AppSettings): View {
        val views = TuningWidget.views(context, WidgetState.from(settings))
        lateinit var view: View
        instrumentation.runOnMainSync { view = views.apply(context, FrameLayout(context)) }
        return view
    }

    @Test
    fun namesTheInstrumentAndTuningInTheStylesColours() {
        val view = drawn(AppSettings(tuning = Tunings.DROP_D, style = VisualStyle.RETRO, theme = ThemeMode.DARK))
        val title = view.findViewById<TextView>(R.id.widget_title)
        val guitar = context.getString(TunerR.string.instrument_guitar)
        val dropD = context.getString(TunerR.string.tuning_drop_d)
        assertEquals(context.getString(R.string.widget_title, guitar, dropD), title.text.toString())
        assertEquals(context.getString(TunerR.string.tuner_spoken_picker, guitar, dropD), title.contentDescription)
        val scheme = tokensFor(VisualStyle.RETRO, dark = true).scheme
        assertEquals(scheme.onBackground.toArgb(), title.currentTextColor)
        assertEquals(scheme.onSurfaceVariant.toArgb(), view.findViewById<TextView>(R.id.widget_subtitle).currentTextColor)
        assertTrue("the whole tile can be tapped", view.findViewById<View>(android.R.id.background).hasOnClickListeners())
    }

    @Test
    fun chromaticModeSaysChromatic() {
        val title = drawn(AppSettings(mode = TunerMode.CHROMATIC)).findViewById<TextView>(R.id.widget_title)
        assertEquals(context.getString(TunerR.string.tuner_mode_chromatic), title.text.toString())
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
            val settings = NavigationTestTags.tab(Destination.SETTINGS)
            val tuner = NavigationTestTags.tab(Destination.TUNER)
            assertTrue("the app opens", waitFor(5_000) { tab(settings) != null })
            tab(settings)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            assertTrue("Settings opens", waitFor(3_000) { tab(settings)?.isSelected == true })

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
}
