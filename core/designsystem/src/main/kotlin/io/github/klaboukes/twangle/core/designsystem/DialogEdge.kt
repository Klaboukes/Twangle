package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * A dialog's edge (Material's AlertDialog, in its own shape): none in Ink, where the dialog's lighter surface sets it
 * apart; in the high-contrast look (Phase 86), whose page and surfaces are one black or white, a 2 dp line in the ink,
 * so the dialog stands clear of the screen under it.
 */
@Composable
fun Modifier.dialogEdge(): Modifier =
    if (MaterialTheme.styleTokens.highContrast) {
        border(DIALOG_EDGE, MaterialTheme.colorScheme.onSurface, AlertDialogDefaults.shape)
    } else {
        this
    }

private val DIALOG_EDGE = 2.dp
