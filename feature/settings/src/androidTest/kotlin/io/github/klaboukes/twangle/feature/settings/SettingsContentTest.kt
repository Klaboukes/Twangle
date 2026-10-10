package io.github.klaboukes.twangle.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.styleCardTag
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val a4Changes = mutableListOf<Float>()
    private val notationChanges = mutableListOf<Notation>()
    private val styleChanges = mutableListOf<VisualStyle>()
    private val themeChanges = mutableListOf<ThemeMode>()
    private val dynamicColorChanges = mutableListOf<Boolean>()
    private val keepScreenOnChanges = mutableListOf<Boolean>()
    private val vibrateChanges = mutableListOf<Boolean>()
    private val showWavesChanges = mutableListOf<Boolean>()
    private val waveLookChanges = mutableListOf<WaveLook>()
    private var backs = 0

    /** Shows [settings], or as if the window were [size]; [ForcedSize] scales it down to fit. */
    private fun show(settings: AppSettings, dynamicColorAvailable: Boolean = true, size: DpSize? = null) {
        compose.setContent {
            val content = @Composable {
                MaterialTheme {
                    SettingsContent(
                        settings,
                        onBack = { backs++ },
                        onA4Change = { a4Changes += it },
                        onNotationChange = { notationChanges += it },
                        onStyleChange = { styleChanges += it },
                        onThemeChange = { themeChanges += it },
                        onDynamicColorChange = { dynamicColorChanges += it },
                        onKeepScreenOnChange = { keepScreenOnChanges += it },
                        onVibrateInTuneChange = { vibrateChanges += it },
                        onShowWavesChange = { showWavesChanges += it },
                        onWaveLookChange = { waveLookChanges += it },
                        modifier = Modifier.testTag(WINDOW),
                        dynamicColorAvailable = dynamicColorAvailable,
                    )
                }
            }
            if (size != null) DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) { content() } else content()
        }
    }

    @Test
    fun showsTheCurrentA4() {
        show(AppSettings(a4Hz = 442f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_value, 442)).assertIsDisplayed()
    }

    @Test
    fun plusAndMinusStepByOneHertz() {
        show(AppSettings(a4Hz = 442f))
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_raise)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_lower)).performClick()
        assertEquals(listOf(443f, 441f), a4Changes)
    }

    @Test
    fun resetGoesBackTo440() {
        show(AppSettings(a4Hz = 432f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_reset, 440)).performClick()
        assertEquals(listOf(440f), a4Changes)
    }

    @Test
    fun resetIsDisabledAt440() {
        show(AppSettings(a4Hz = 440f))
        compose.onNodeWithText(context.getString(R.string.settings_a4_reset, 440)).assertIsNotEnabled()
    }

    @Test
    fun buttonsStopAtTheEndsOfTheRange() {
        show(AppSettings(a4Hz = 466f))
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_raise)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_a4_lower)).assertIsEnabled()
    }

    @Test
    fun backArrowGoesBack() {
        show(AppSettings())
        compose.onNodeWithContentDescription(context.getString(R.string.settings_back)).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun notationShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(notation = Notation.FLATS))
        compose.onNodeWithText(context.getString(R.string.settings_notation_flats)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_notation_sharps)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_notation_solfege)).performClick()
        assertEquals(listOf(Notation.SOLFEGE), notationChanges)
    }

    @Test
    fun themeShowsTheChoiceAndReportsANewOne() {
        show(AppSettings(theme = ThemeMode.SYSTEM))
        compose.onNodeWithText(context.getString(R.string.settings_theme_system)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_theme_dark)).performScrollTo().performClick()
        assertEquals(listOf(ThemeMode.DARK), themeChanges)
    }

    @Test
    fun switchesShowTheirStateAndToggle() {
        show(AppSettings(dynamicColor = true, keepScreenOn = false, vibrateInTune = true, showWaves = true))
        val dynamic = compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title))
        dynamic.performScrollTo().assertIsOn().performClick()
        val keepOn = compose.onNodeWithText(context.getString(R.string.settings_keep_screen_on_title))
        keepOn.performScrollTo().assertIsOff().performClick()
        val vibrate = compose.onNodeWithText(context.getString(R.string.settings_vibrate_in_tune_title))
        vibrate.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), dynamicColorChanges)
        assertEquals(listOf(true), keepScreenOnChanges)
        assertEquals(listOf(false), vibrateChanges)
        val waves = compose.onNodeWithText(context.getString(R.string.settings_show_waves_title))
        waves.performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), showWavesChanges)
    }

    @Test
    fun waveLooksShowTheChoiceAndReportANewOne() {
        show(AppSettings(showWaves = true, waveLook = WaveLook.LOOP))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_loop)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_ribbon)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_fireflies)).performScrollTo().performClick()
        assertEquals(listOf(WaveLook.FIREFLIES), waveLookChanges)
    }

    @Test
    fun waveLooksAreHiddenWhileTheWaveViewIsOff() {
        show(AppSettings(showWaves = false))
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_wave_look_ribbon)).assertDoesNotExist()
    }

    @Test
    fun wallpaperColoursAreHiddenWithoutDynamicColor() {
        show(AppSettings(), dynamicColorAvailable = false)
        compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title)).assertDoesNotExist()
    }

    @Test
    fun wallpaperColoursOnlyApplyToTheContemporaryStyle() {
        show(AppSettings(style = VisualStyle.RETRO))
        compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title)).assertDoesNotExist()
    }

    @Test
    fun styleCardsShowTheChoiceAndReportANewOne() {
        show(AppSettings(style = VisualStyle.CLASSIC))
        compose.onNodeWithTag(styleCardTag(VisualStyle.CLASSIC)).assertIsSelected()
        compose.onNodeWithTag(styleCardTag(VisualStyle.MIDDLE_AGES)).assertIsNotSelected().performClick()
        assertEquals(listOf(VisualStyle.MIDDLE_AGES), styleChanges)
    }

    @Test
    fun onAWideScreenTheSettingsStayInAReadableColumn() {
        show(AppSettings(), size = DpSize(1200.dp, 800.dp))
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val row = compose.onNodeWithText(context.getString(R.string.settings_theme_dark)).performScrollTo().getBoundsInRoot()
        // 600 of the 1200 dp, in the middle.
        val share = (row.right - row.left) / (window.right - window.left)
        assertTrue("row takes $share of the width", share in 0.4f..0.51f)
        val leftGap = row.left - window.left
        val rightGap = window.right - row.right
        assertTrue("gaps $leftGap and $rightGap", abs((leftGap - rightGap).value) < 2f)
    }

    private companion object {
        const val WINDOW = "window"
    }
}
