@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.R
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary

/**
 * The one confirmation for actions that can't be undone (removing titles with their history,
 * unmarking, restoring, replacing a library): a glass sheet with the action and Cancel.
 */
@Composable
fun ConfirmPrompt(
    title: String,
    message: String,
    confirmLabel: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text(message, color = TextSecondary, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
            Spacer(Modifier.height(Spacing.lg))
            PrimaryAction(
                confirmLabel,
                icon,
                Modifier.fillMaxWidth(),
                containerColor = if (destructive) SurfacePalette.DangerAction else Accent,
                onClick = onConfirm,
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(Radius.Pill)) {
                Text(stringResource(R.string.cancel), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
