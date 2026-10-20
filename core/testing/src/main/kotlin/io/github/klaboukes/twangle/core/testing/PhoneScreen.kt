package io.github.klaboukes.twangle.core.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** How the phone's navigation is set: the gesture handle, or three buttons. */
enum class Navigation { GESTURES, BUTTONS }

/**
 * The system bars a modern phone draws over an app shown edge to edge, in dp of the shown size: the
 * window insets the screens see. The same on every phone, so a test gives the same answer on any of
 * them (docs/architecture/testing.md, *Any phone*). [cutoutLeft] is the camera hole on the left when
 * the phone lies on its side; upright it is inside the status bar.
 */
data class SystemBars(
    val left: Dp = 0.dp,
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val navigation: Navigation = Navigation.GESTURES,
    val cutoutLeft: Dp = 0.dp,
) {

    /** The window that holds [area] inside these bars. */
    fun around(area: DpSize) = DpSize(area.width + left + right, area.height + top + bottom)

    /** What these bars leave of a whole phone's [window]: the area to give [PhoneScreen]. */
    fun inside(window: DpSize) = DpSize(window.width - left - right, window.height - top - bottom)

    /** Where [area] lies in the window [around] it: the part the bars leave clear. */
    fun safeArea(area: DpSize) = DpRect(left, top, left + area.width, top + area.height)

    companion object {
        val STATUS_BAR = 24.dp
        val GESTURE_BAR = 24.dp
        val BUTTON_BAR = 48.dp

        /** No bars: the screen fills the whole window. */
        val NONE = SystemBars()

        /**
         * The whole phone screen: the status bar on top; upright the navigation bar at the bottom, and on
         * its side the gesture handle at the bottom or the buttons on the right, the camera on the left.
         */
        fun phone(onItsSide: Boolean, navigation: Navigation = Navigation.GESTURES) = when {
            !onItsSide -> SystemBars(top = STATUS_BAR, bottom = navigation.bar, navigation = navigation)
            navigation == Navigation.GESTURES -> SystemBars(STATUS_BAR, STATUS_BAR, 0.dp, GESTURE_BAR, navigation, cutoutLeft = STATUS_BAR)
            else -> SystemBars(STATUS_BAR, STATUS_BAR, BUTTON_BAR, 0.dp, navigation, cutoutLeft = STATUS_BAR)
        }

        /**
         * What one tab's screen gets in the app: the app's navigation bar (upright, at the bottom) or
         * rail (on its side, on the left) stands over the system's bars on its side and keeps them off
         * the tab.
         */
        fun tab(onItsSide: Boolean, navigation: Navigation = Navigation.GESTURES) =
            phone(onItsSide, navigation).let { if (onItsSide) it.copy(left = 0.dp, cutoutLeft = 0.dp) else it.copy(bottom = 0.dp) }

        private val Navigation.bar get() = if (this == Navigation.GESTURES) GESTURE_BAR else BUTTON_BAR
    }
}

/**
 * Shows [content] as a phone would, the same on every phone: a window of [area] plus the [bars] around it
 * (edge to edge, the bars drawn over the window's edges as window insets), at [fontScale] text and in
 * [locale] (null: the phone's own language).
 *
 * The window is forced with [DeviceConfigurationOverride.ForcedSize]: where the phone's screen is smaller,
 * everything is drawn smaller (a lower density) but laid out in the window's own dp. Bounds read through
 * `getBoundsInRoot()` and the like are in those dp too (they use the node's density), so tests compare them
 * with the window's dp on every phone; px values (text layouts, captures) are the phone's own and differ.
 * The phone's real bars never reach the content: [bars] replace them.
 */
@Composable
fun PhoneScreen(
    area: DpSize,
    bars: SystemBars = SystemBars.NONE,
    fontScale: Float = 1f,
    locale: String? = null,
    content: @Composable () -> Unit,
) {
    val window = bars.around(area)
    SubcomposeLayout { constraints ->
        val shown = subcompose(Unit) { Forced(window, bars, fontScale, locale, constraints.maxWidth, constraints.maxHeight, content) }
            .map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) { shown.forEach { it.place(0, 0) } }
    }
}

/**
 * [PhoneScreen]'s window, for a capture or touches across it. PhoneScreen shows it as a root of its
 * own (the bars come through a view of their own), so `onRoot()` finds two roots.
 */
fun SemanticsNodeInteractionsProvider.onPhoneScreen() = onNodeWithTag(PHONE_SCREEN)

private const val PHONE_SCREEN = "phone_screen"

/** The process's default language, set for the language a [PhoneScreen] shows; the phone's again after the test. */
internal object DefaultLocale {
    private val phone: Pair<java.util.Locale, android.os.LocaleList> = java.util.Locale.getDefault() to android.os.LocaleList.getDefault()

    fun set(tag: String) {
        val locale = java.util.Locale.forLanguageTag(tag)
        java.util.Locale.setDefault(locale)
        android.os.LocaleList.setDefault(android.os.LocaleList(locale))
    }

    fun reset() {
        java.util.Locale.setDefault(phone.first)
        android.os.LocaleList.setDefault(phone.second)
    }
}

/** [content] in [window] on a screen of [widthPx] x [heightPx], with [bars] as its insets. */
@Composable
private fun Forced(
    window: DpSize,
    bars: SystemBars,
    fontScale: Float,
    locale: String?,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit,
) {
    run {
        // The density ForcedSize will draw at (as it works it out), to give the insets in its px.
        val phone = LocalDensity.current.density
        val scale = min(1f, min(widthPx / phone / window.width.value, heightPx / phone / window.height.value))
        val density = floor(phone * scale * 160f) / 160f
        fun px(dp: Dp) = (dp.value * density).roundToInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(bars.top), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, px(bars.right), px(bars.bottom)))
            .setInsets(
                WindowInsetsCompat.Type.tappableElement(),
                Insets.of(0, px(bars.top), px(bars.right), if (bars.navigation == Navigation.BUTTONS) px(bars.bottom) else 0),
            )
            .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(0, px(bars.top), 0, px(bars.bottom)))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(px(bars.cutoutLeft), 0, 0, 0))
            .build()
        var override = DeviceConfigurationOverride.WindowInsets(insets) then
            DeviceConfigurationOverride.ForcedSize(window) then
            DeviceConfigurationOverride.FontScale(fontScale)
        if (locale != null) override = override then DeviceConfigurationOverride.Locales(LocaleList(locale))
        DeviceConfigurationOverride(override) {
            // Text is laid out by the process's default language, not the configuration's (Japanese
            // breaks its lines by its own rules): that of the language shown, not the phone's.
            // Before the texts are laid out (in this composition), so not in a SideEffect.
            if (locale != null) remember(locale) { DefaultLocale.set(locale).let { locale } }
            // Checked when laid out: a recomposition can come before ForcedSize has taken the new window.
            Layout(content, Modifier.testTag(PHONE_SCREEN)) { measurables, constraints ->
                check(abs(this.density - density) < 0.001f) {
                    "ForcedSize drew at ${this.density}, not $density: the bars' insets are off ($widthPx x $heightPx px at $phone, window $window)"
                }
                val placeables = measurables.map { it.measure(constraints) }
                layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
            }
        }
    }
}
