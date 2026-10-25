package com.cinetrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary

/**
 * The one empty state: an icon, a title, an optional line of help and an optional action.
 * Used whenever a list has nothing to show (empty library, no filter results).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, null, tint = AccentLight, modifier = Modifier.size(40.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body?.let { Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel, color = TextPrimary) }
    }
}

/** A failed load: what went wrong and Retry, the same on every screen. */
@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = TextPrimary, fontWeight = FontWeight.Bold) }
    }
}

/** A small spinner for "loading more" and section loads; first loads use skeletons. */
@Composable
fun InlineLoader(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    CircularProgressIndicator(modifier.size(size), color = TextMuted, strokeWidth = 2.dp)
}
