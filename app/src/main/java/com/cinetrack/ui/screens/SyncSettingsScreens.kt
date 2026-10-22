@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.FloppyBootstrapStage
import com.cinetrack.domain.FloppyBootstrapProgress
import com.cinetrack.domain.allowsRetry
import com.cinetrack.domain.isManagedActive
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.domain.SyncProgress
import com.cinetrack.domain.SyncConflictChoice
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncDeliveryCard
import com.cinetrack.domain.SyncOperationStatus
import com.cinetrack.ui.settings.SyncSettingsViewModel
import com.cinetrack.ui.settings.TrackingSyncControls
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.StatusPaused
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.cinetrack.ui.theme.GlassSubtle
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing

@Composable
internal fun SyncSettingsHost(state: AppUiState, viewModel: SyncSettingsViewModel, controls: TrackingSyncControls, onConnect: () -> Unit) {
    val sync by controls.syncProgress.collectAsStateWithLifecycle()
    SyncSettings(state, sync, viewModel, controls.onSync, onConnect)
}

@Composable
private fun SyncSettings(
    state: AppUiState,
    sync: SyncProgress,
    viewModel: SyncSettingsViewModel,
    onSync: () -> Unit,
    onConnect: () -> Unit,
) {
    LaunchedEffect(state.simklConnected, state.simklUsername) {
        if (state.simklConnected && state.simklUsername == null) viewModel.loadSimklUsername()
    }
    val library = state.rails[RailIds.LIBRARY].orEmpty()
    if (!state.simklConnected) when (state.simklReconnectReason) {
        "upgrade" -> KitNotice(stringResource(R.string.simkl_reconnect_notice))
        "signed_out" -> KitNotice(stringResource(R.string.simkl_signed_out_notice))
    }
    if (state.simklConnected && state.simklFirstSyncPending) KitNotice(stringResource(R.string.simkl_first_sync_pending))
    if (state.simklConnected) {
        KitStatTiles(
            listOf(
                library.count { it.type == MediaType.TV }.toString() to stringResource(R.string.stat_shows),
                library.count { it.type == MediaType.MOVIE }.toString() to stringResource(R.string.stat_movies),
                (sync.report.lastIncrementalSync ?: sync.report.lastFullSync)?.let { relativeSyncTime(it) } .orEmpty().ifBlank { stringResource(R.string.never) } to stringResource(R.string.stat_last_sync),
            ),
        )
    }
    KitPrimaryButton(
        text = when {
            !state.simklConnected -> stringResource(R.string.connect_simkl)
            sync.report.failedOperations > 0 -> stringResource(R.string.retry_failed_sync)
            else -> stringResource(R.string.sync_now)
        },
        icon = if (state.simklConnected) Icons.Filled.Refresh else Icons.Filled.Link,
        color = Color(0xFF3B8F5A),
        onClick = if (state.simklConnected) onSync else onConnect,
    )
    SettingsSection(stringResource(R.string.synchronization)) {
        KitToggleRow(Icons.Filled.Refresh, stringResource(R.string.background_sync), stringResource(R.string.background_sync_sub), state.backgroundSync, viewModel::setBackgroundSync)
        KitToggleRow(Icons.Filled.Wifi, stringResource(R.string.wifi_only), stringResource(R.string.wifi_only_sub), state.wifiOnly, viewModel::setWifiOnly)
    }
    if (sync.report.lastIncrementalSync != null || sync.report.failedOperations > 0) {
        val report = sync.report
        SettingsSection(stringResource(R.string.sync_activity)) {
            KitRow(Icons.Filled.Download, stringResource(R.string.downloaded), value = report.downloaded.toString())
            KitRow(Icons.Filled.Upload, stringResource(R.string.uploaded), value = report.uploaded.toString())
            KitRow(Icons.Filled.SwapVert, stringResource(R.string.added_removed), value = "${report.added} / ${report.removed}")
            KitRow(Icons.Filled.Schedule, stringResource(R.string.pending_local_changes), value = report.pendingLocalChanges.toString())
            KitRow(Icons.Filled.ErrorOutline, stringResource(R.string.failed_operations), value = report.failedOperations.toString())
            KitRow(Icons.Filled.SyncProblem, stringResource(R.string.sync_conflicts), value = report.conflicts.toString())
            report.lastFullSync?.let {
                KitRow(Icons.Filled.History, stringResource(R.string.last_full_sync), value = com.cinetrack.ui.UiDateFormatters.shortDateTime(it))
            }
        }
    }
    state.error?.takeIf(String::isNotBlank)?.let { KitNotice(it) }
    sync.message?.takeIf { sync.stage == com.cinetrack.domain.SyncStage.ERROR && it.isNotBlank() }?.let { KitNotice(it) }
    if (state.simklConnected) {
        ConfirmedDangerText(
            text = stringResource(R.string.disconnect_account),
            title = stringResource(R.string.disconnect_simkl_title),
            body = stringResource(R.string.disconnect_simkl_body),
            onConfirm = viewModel::disconnectSimkl,
        )
    }
}

@Composable
internal fun SyncOperationsSettings(viewModel: SyncSettingsViewModel, controls: TrackingSyncControls) {
    val operations by viewModel.syncOperations.collectAsStateWithLifecycle()
    val bootstrap by viewModel.managedBootstrapSummary.collectAsStateWithLifecycle()
    val bootstrapProgress by controls.floppyBootstrapProgress.collectAsStateWithLifecycle()
    val syncRunning = controls.syncProgress.collectAsStateWithLifecycle().value.running
    // On open, and whenever a sync or the initial sync moves on, since either changes the list.
    LaunchedEffect(syncRunning, bootstrapProgress?.stage) { viewModel.refreshSyncOperations() }
    val conflicts = operations.filter { it.status == SyncOperationStatus.CONFLICT }
    val failed = operations.filter { it.status in setOf(SyncOperationStatus.FAILED, SyncOperationStatus.PARTIAL) }
    val pending = operations.filter { it.status == SyncOperationStatus.PENDING }

    val effectiveProgress = bootstrapProgress ?: bootstrap?.let { summary ->
        FloppyBootstrapProgress(
            stage = when (summary.state) {
                com.cinetrack.data.sync.ProviderBootstrapState.READY -> FloppyBootstrapStage.COMPLETE
                com.cinetrack.data.sync.ProviderBootstrapState.FAILED -> FloppyBootstrapStage.NEEDS_ATTENTION
                com.cinetrack.data.sync.ProviderBootstrapState.RUNNING -> FloppyBootstrapStage.SYNCING
                com.cinetrack.data.sync.ProviderBootstrapState.NOT_STARTED -> FloppyBootstrapStage.PREPARING
            },
            processed = summary.completed,
            total = summary.total,
            succeeded = summary.completed,
            failed = summary.failed,
            providerInstanceId = summary.connectionId,
        )
    }
    // Managed bootstrap rows are intentionally compacted into one card, but
    // their terminal failure must still be reflected in this screen's status
    // summary. A Floppy HTTP failure is transport attention, never a local
    // reconciliation conflict.
    val bootstrapFailed = effectiveProgress?.stage == FloppyBootstrapStage.NEEDS_ATTENTION
    KitStatTiles(
        listOf(
            pending.size.toString() to stringResource(R.string.pending_writes),
            (failed.size + if (bootstrapFailed) 1 else 0).toString() to stringResource(R.string.failed_actions),
            conflicts.size.toString() to stringResource(R.string.sync_conflicts),
        ),
    )

    // A finished initial sync has nothing left to show or retry.
    if (effectiveProgress != null && effectiveProgress.stage != FloppyBootstrapStage.COMPLETE) {
        SettingsSection(stringResource(R.string.initial_synchronization)) {
            ManagedFloppyBootstrapCard(effectiveProgress, bootstrap, viewModel::retryFloppyInitialSync)
        }
    }

    val managedActive = effectiveProgress?.stage?.isManagedActive() == true
    if (operations.isEmpty() && !managedActive) {
        KitStatusCard(ok = true, title = stringResource(R.string.ops_all_synced), subtitle = stringResource(R.string.ops_all_synced_sub))
    }
    if (conflicts.isNotEmpty()) SyncOperationSection(stringResource(R.string.conflicts_to_resolve), conflicts, viewModel)
    if (failed.isNotEmpty()) SyncOperationSection(stringResource(R.string.failed_actions), failed, viewModel)
    if (pending.isNotEmpty()) SyncOperationSection(stringResource(R.string.pending_writes), pending, viewModel)
}

@Composable
private fun ManagedFloppyBootstrapCard(
    progress: com.cinetrack.domain.FloppyBootstrapProgress,
    summary: com.cinetrack.data.sync.ManagedBootstrapSummary?,
    onRetry: () -> Unit,
) {
    val stage = progress.stage
    val determinate = stage == FloppyBootstrapStage.SYNCING && progress.total > 0
    val planningDeterminate = stage == FloppyBootstrapStage.MATERIALIZING_QUEUE && progress.planningTotal > 0
    val percent = if (determinate) (progress.processed * 100 / progress.total).coerceIn(0, 100) else 0
    val retryable = stage.allowsRetry()
    Column(
        Modifier.fillMaxWidth().padding(Spacing.lg)
            .glass(RoundedCornerShape(Radius.Medium))
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudSync, contentDescription = null, tint = AccentLight, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text("Floppy", color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.initial_synchronization), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Text(
                when (stage) {
                    FloppyBootstrapStage.COMPLETE -> stringResource(R.string.floppy_ready_short)
                    FloppyBootstrapStage.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_needs_attention)
                    FloppyBootstrapStage.STALLED -> stringResource(R.string.floppy_taking_longer)
                    else -> stringResource(R.string.floppy_syncing_with_floppy)
                },
                color = if (retryable) androidx.compose.material3.MaterialTheme.colorScheme.error else TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            when (stage) {
                FloppyBootstrapStage.PREPARING,
                FloppyBootstrapStage.BUILDING_PLAN -> stringResource(R.string.floppy_building_sync_plan)
                FloppyBootstrapStage.MATERIALIZING_QUEUE -> stringResource(R.string.floppy_preparing_sync_queue)
                FloppyBootstrapStage.CHECKING_REMOTE_STATE -> stringResource(R.string.floppy_checking_existing_history)
                FloppyBootstrapStage.QUEUED -> stringResource(R.string.floppy_waiting_for_server)
                FloppyBootstrapStage.SYNCING -> stringResource(R.string.floppy_syncing_with_floppy)
                FloppyBootstrapStage.VERIFYING -> stringResource(R.string.floppy_verifying_sync)
                FloppyBootstrapStage.WAITING_FOR_SERVER -> stringResource(R.string.floppy_waiting_for_server)
                FloppyBootstrapStage.STALLED -> stringResource(R.string.floppy_taking_longer)
                FloppyBootstrapStage.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_needs_attention)
                FloppyBootstrapStage.COMPLETE -> stringResource(R.string.floppy_ready_short)
            },
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (determinate) {
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { (progress.processed.toFloat() / progress.total).coerceIn(0f, 1f) },
                color = AccentLight,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.floppy_progress_count, progress.processed, progress.total), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text("$percent%", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        } else if (planningDeterminate) {
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { (progress.planningProcessed.toFloat() / progress.planningTotal).coerceIn(0f, 1f) },
                color = AccentLight,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.floppy_progress_count, progress.planningProcessed, progress.planningTotal), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text("${(progress.planningProcessed * 100 / progress.planningTotal).coerceIn(0, 100)}%", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        } else if (stage != FloppyBootstrapStage.COMPLETE && stage != FloppyBootstrapStage.NEEDS_ATTENTION) {
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(color = AccentLight, modifier = Modifier.fillMaxWidth())
        }
        progress.currentTitle?.takeIf(String::isNotBlank)?.let { title ->
            Spacer(Modifier.height(Spacing.sm))
            Text("${stringResource(R.string.current)}: $title", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        if (stage == FloppyBootstrapStage.SYNCING) {
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.floppy_leave_screen_copy), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        summary?.lastError?.takeIf(String::isNotBlank)?.let { error ->
            Spacer(Modifier.height(Spacing.sm))
            Text(error, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        if (retryable) {
            Spacer(Modifier.height(Spacing.xs))
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(if (stage == FloppyBootstrapStage.NEEDS_ATTENTION) stringResource(R.string.retry) else stringResource(R.string.retry_now))
            }
        }
    }
}

@Composable
private fun SyncOperationSection(
    title: String,
    operations: List<SyncOperationCard>,
    viewModel: SyncSettingsViewModel,
) {
    SettingsSection(title) {
        operations.forEachIndexed { index, operation ->
            SyncOperationRow(operation, viewModel)
        }
    }
}

@Composable
private fun SyncOperationRow(operation: SyncOperationCard, viewModel: SyncSettingsViewModel) {
    val terminalCancelled = operation.deliveries.isNotEmpty() && operation.deliveries
        .filter { it.required }
        .all { it.status in setOf("CANCELLED_PROVIDER_INSTANCE_CHANGED", "CANCELLED_PROVIDER_REMOVED", "SUPERSEDED", "SKIPPED_UNSUPPORTED", "ACKNOWLEDGED") }
    val statusColor = when (operation.status) {
        SyncOperationStatus.CONFLICT -> StatusPaused
        SyncOperationStatus.FAILED -> androidx.compose.material3.MaterialTheme.colorScheme.error
        SyncOperationStatus.PARTIAL -> androidx.compose.material3.MaterialTheme.colorScheme.error
        SyncOperationStatus.PENDING -> AccentLight
    }
    val providerName = operation.providerId.lowercase().replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) }
    val actionLabel = when (operation.operation) {
        "LIBRARY_STATUS" -> stringResource(R.string.sync_library_change)
        "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> stringResource(R.string.sync_library_conflict)
        "WATCHED_CONFLICT" -> stringResource(R.string.sync_watched_conflict)
        "EPISODE_WATCHED_CONFLICT" -> stringResource(R.string.sync_episode_watched_conflict)
        "EPISODE_WATCHED" -> stringResource(R.string.sync_episode_watched)
        "EPISODE_UNWATCHED" -> stringResource(R.string.sync_episode_unwatched)
        "MEDIA_HISTORY_REMOVE" -> stringResource(R.string.sync_history_removed)
        else -> stringResource(R.string.sync_library_change)
    }
    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(operation.title, color = TextPrimary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Text(actionLabel, color = statusColor, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                com.cinetrack.ui.UiDateFormatters.shortDateTime(operation.updatedAt),
                color = TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        val conflictMessage = when (operation.operation) {
            "LIBRARY_STATUS_CONFLICT", "LIBRARY_CONFLICT" -> stringResource(R.string.sync_library_conflict_message, providerName)
            "WATCHED_CONFLICT" -> stringResource(R.string.sync_watched_conflict_message, providerName)
            "EPISODE_WATCHED_CONFLICT" -> stringResource(R.string.sync_episode_watched_conflict_message, providerName)
            else -> operation.message
        }
        conflictMessage?.takeIf(String::isNotBlank)?.let { message ->
            Spacer(Modifier.height(Spacing.sm))
            Text(message, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        if (operation.deliveries.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            operation.deliveries.forEach { delivery -> SyncDeliveryRow(delivery) }
        }
        if (operation.status == SyncOperationStatus.CONFLICT) {
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { viewModel.resolveSyncConflict(operation.id, SyncConflictChoice.KEEP_LOCAL) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(Radius.Pill),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSubtle),
                ) { Text(stringResource(R.string.keep_local), style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
                Button(
                    onClick = { viewModel.resolveSyncConflict(operation.id, SyncConflictChoice.USE_REMOTE) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(Radius.Pill),
                ) { Text(stringResource(R.string.use_provider, providerName), style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.local_value, localizedSyncValue(operation.localValue)), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.provider_value, providerName, localizedSyncValue(operation.remoteValue)), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        } else if (!terminalCancelled) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                operation.localValue?.takeIf(String::isNotBlank)?.let { value ->
                    Text(localizedSyncValue(value), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                } ?: Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.retrySyncOperation(operation.id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(Radius.Pill),
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(if (operation.status == SyncOperationStatus.FAILED) stringResource(R.string.retry) else stringResource(R.string.try_now))
                }
            }
        } else {
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.sync_delivery_cancelled_instance), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SyncDeliveryRow(delivery: SyncDeliveryCard) {
    val provider = delivery.providerId.lowercase().replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) }
    val label = when (delivery.status) {
        "ACKNOWLEDGED" -> stringResource(R.string.sync_delivery_synced)
        "FAILED" -> stringResource(R.string.sync_delivery_retry)
        "PENDING" -> stringResource(R.string.sync_delivery_pending)
        "SKIPPED_UNSUPPORTED" -> stringResource(R.string.sync_delivery_skipped)
        "CANCELLED_PROVIDER_REMOVED" -> stringResource(R.string.sync_delivery_cancelled)
        "CANCELLED_PROVIDER_INSTANCE_CHANGED" -> stringResource(R.string.sync_delivery_cancelled_instance)
        else -> stringResource(R.string.sync_delivery_pending)
    }
    val color = when (delivery.status) {
        "ACKNOWLEDGED" -> Success
        "FAILED" -> androidx.compose.material3.MaterialTheme.colorScheme.error
        "CANCELLED_PROVIDER_INSTANCE_CHANGED", "CANCELLED_PROVIDER_REMOVED", "SUPERSEDED" -> TextMuted
        else -> TextSecondary
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(provider, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(label, color = color, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
    delivery.lastError?.takeIf(String::isNotBlank)?.let { error ->
        Text(error, color = if (delivery.status == "FAILED") androidx.compose.material3.MaterialTheme.colorScheme.error else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun localizedSyncValue(value: String?): String = when (value) {
    "PLAN_TO_WATCH" -> stringResource(R.string.status_plan_to_watch)
    "COMPLETED" -> stringResource(R.string.status_completed)
    "DROPPED" -> stringResource(R.string.status_dropped)
    "PAUSED" -> stringResource(R.string.status_paused)
    "true" -> stringResource(R.string.watched)
    "false" -> stringResource(R.string.not_watched)
    else -> value.orEmpty().replace(':', ' ').replace('_', ' ')
}
    .replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) }

/** "2 min fa", "3 h fa", or a date for older syncs. */
@Composable
private fun relativeSyncTime(millis: Long): String = when (val elapsed = com.cinetrack.ui.elapsedSince(millis)) {
    com.cinetrack.ui.Elapsed.JustNow -> stringResource(R.string.just_now_short)
    is com.cinetrack.ui.Elapsed.Minutes -> stringResource(R.string.minutes_ago_short, elapsed.count)
    is com.cinetrack.ui.Elapsed.Hours -> stringResource(R.string.hours_ago_short, elapsed.count)
    is com.cinetrack.ui.Elapsed.Days -> com.cinetrack.ui.UiDateFormatters.shortDate(millis)
}
