package com.cinetrack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.cinetrack.R
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.Radius
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.CloudDownload

/**
 * Shown once after connecting a Simkl account, before its first sync. It cannot be dismissed
 * by tapping outside: the only ways out are a choice or disconnecting.
 */
@Composable
fun SimklFirstSyncDialog(onMerge: () -> Unit, onReplace: () -> Unit, onDisconnect: () -> Unit) {
    // Replacing deletes the titles only on this device: it takes a second, explicit step.
    var confirmReplace by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmReplace) {
        com.cinetrack.ui.components.ConfirmPrompt(
            title = stringResource(R.string.simkl_replace_confirm_title),
            message = stringResource(R.string.simkl_replace_confirm_message),
            confirmLabel = stringResource(R.string.simkl_first_sync_replace),
            icon = androidx.compose.material.icons.Icons.Filled.CloudDownload,
            onDismiss = { confirmReplace = false },
        ) {
            confirmReplace = false
            onReplace()
        }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.simkl_first_sync_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.simkl_first_sync_body), style = MaterialTheme.typography.bodyMedium)
                FirstSyncChoice(
                    title = stringResource(R.string.simkl_first_sync_merge),
                    body = stringResource(R.string.simkl_first_sync_merge_sub),
                    highlighted = true,
                    onClick = onMerge,
                )
                FirstSyncChoice(
                    title = stringResource(R.string.simkl_first_sync_replace),
                    body = stringResource(R.string.simkl_first_sync_replace_sub),
                    highlighted = false,
                    onClick = { confirmReplace = true },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect_account)) } },
    )
}

@Composable
private fun FirstSyncChoice(title: String, body: String, highlighted: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Medium),
        color = if (highlighted) accent.copy(alpha = .16f) else Color.White.copy(alpha = .05f),
        border = BorderStroke(1.dp, if (highlighted) accent.copy(alpha = .7f) else Color.White.copy(alpha = .12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
