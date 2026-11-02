package io.github.klaboukes.twangle.app.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.app.R
import io.github.klaboukes.twangle.core.designsystem.StyleButton

/**
 * The gear that opens Settings (2.0, Phase 69: Settings is no tab, it is set once). Upright it stands in
 * the top start corner of every tab (the tuner's header row, the metronome's and the Practice tools' top
 * rows, which `MainActivity` hands it to), across from the tuner's focus mode button and drawn as that
 * one: the style's button ([StyleButton]) with a [GEAR_FACE] face, so the two corners match (owner,
 * 2026-10-19: a bare icon in a corner was hardly visible). On its side it stands at the top of the rail
 * ([AppNavigationRail]), which is that corner there. No word, only the icon; TalkBack hears "Settings", a
 * button. Its test tag is the Settings tab's old one ([NavigationTestTags.SETTINGS]), so the Baseline
 * Profile's journey and UI Automator find it as they found the tab.
 */
@Composable
internal fun SettingsGear(onClick: () -> Unit, modifier: Modifier = Modifier) {
    StyleButton(
        onClick = onClick,
        label = stringResource(R.string.nav_settings),
        modifier = modifier,
        face = GEAR_FACE,
        faceModifier = Modifier.testTag(NavigationTestTags.SETTINGS),
    ) {
        Icon(GEAR_ICON, contentDescription = null, modifier = Modifier.size(GEAR_ICON_SIZE))
    }
}

/** The gear's face: the tuner's focus mode button's 44 dp, in a 48 dp touch area. */
internal val GEAR_FACE = 44.dp
private val GEAR_ICON_SIZE = 24.dp
