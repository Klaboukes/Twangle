package com.intune.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.intune.core.dsp.music.Notation
import com.intune.core.settings.AppSettings
import com.intune.core.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val a4Changes = mutableListOf<Float>()
    private val notationChanges = mutableListOf<Notation>()
    private val themeChanges = mutableListOf<ThemeMode>()
    private val dynamicColorChanges = mutableListOf<Boolean>()
    private val keepScreenOnChanges = mutableListOf<Boolean>()
    private var backs = 0

    private fun show(settings: AppSettings, dynamicColorAvailable: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                SettingsContent(
                    settings,
                    onBack = { backs++ },
                    onA4Change = { a4Changes += it },
                    onNotationChange = { notationChanges += it },
                    onThemeChange = { themeChanges += it },
                    onDynamicColorChange = { dynamicColorChanges += it },
                    onKeepScreenOnChange = { keepScreenOnChanges += it },
                    dynamicColorAvailable = dynamicColorAvailable,
                )
            }
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
        show(AppSettings(dynamicColor = true, keepScreenOn = false))
        val dynamic = compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title))
        dynamic.performScrollTo().assertIsOn().performClick()
        val keepOn = compose.onNodeWithText(context.getString(R.string.settings_keep_screen_on_title))
        keepOn.performScrollTo().assertIsOff().performClick()
        assertEquals(listOf(false), dynamicColorChanges)
        assertEquals(listOf(true), keepScreenOnChanges)
    }

    @Test
    fun wallpaperColoursAreHiddenWithoutDynamicColor() {
        show(AppSettings(), dynamicColorAvailable = false)
        compose.onNodeWithText(context.getString(R.string.settings_dynamic_color_title)).assertDoesNotExist()
    }
}
