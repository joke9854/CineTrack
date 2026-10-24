@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import androidx.compose.material.icons.filled.SwapHoriz
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.FloppyUiState
import com.cinetrack.domain.FloppyConnectionError
import com.cinetrack.domain.FloppyConnectionStage
import com.cinetrack.domain.FloppyConnectionUiState
import com.cinetrack.domain.FloppyBootstrapStage
import com.cinetrack.domain.allowsRetry
import com.cinetrack.ui.settings.SyncSettingsViewModel
import com.cinetrack.ui.settings.TrackingSyncControls
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.cinetrack.ui.theme.GlassStrong
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing

@Composable
internal fun IntegrationsSettings(state: AppUiState, onPage: (String) -> Unit) {
    SettingsSection(stringResource(R.string.integrations)) {
        ProviderRow("TMDB", stringResource(R.string.tmdb_description), state.tmdbApiConfigured) { onPage(SettingsPages.ServiceTmdb) }
        ProviderRow("MDBList", stringResource(R.string.mdblist_description), state.mdbListApiConfigured) { onPage(SettingsPages.ServiceMdblist) }
        ProviderRow("Trakt", stringResource(R.string.trakt_description), state.traktConfigured) { onPage(SettingsPages.ServiceTrakt) }
        ProviderRow("Simkl", stringResource(R.string.simkl_description), state.simklConnected) { onPage(SettingsPages.ServiceSimkl) }
        ProviderRow("Floppy", floppyIntegrationSubtitle(state), state.floppyConnected) { onPage(SettingsPages.ServiceFloppy) }
    }
}

@Composable
private fun floppyIntegrationSubtitle(state: AppUiState): String = if (state.floppyIsMain) stringResource(R.string.floppy_main) else when (state.floppyUiState) {
    FloppyUiState.NOT_CONNECTED -> stringResource(R.string.floppy_self_hosted_tracking)
    FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_connected_inactive)
    FloppyUiState.SETTING_UP -> stringResource(R.string.floppy_setting_up)
    FloppyUiState.READY -> stringResource(R.string.floppy_ready)
    FloppyUiState.NEEDS_ATTENTION -> stringResource(R.string.floppy_needs_attention)
}

/** "Connected as <name>" once Floppy is ready and has sent a name; otherwise the setup stage. */
@Composable
internal fun floppyConnectedLabel(state: AppUiState): String = floppyConnectedLabel(state.floppyUiState, state.floppyUsername)

@Composable
internal fun floppyConnectedLabel(uiState: FloppyUiState, username: String?): String =
    if (uiState == FloppyUiState.READY && !username.isNullOrBlank()) stringResource(R.string.floppy_connected_as, username)
    else floppyStatusLabel(uiState)

@Composable
private fun floppyStatusLabel(state: FloppyUiState): String = when (state) {
    FloppyUiState.NOT_CONNECTED -> stringResource(R.string.floppy_not_connected)
    FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_connected_inactive)
    FloppyUiState.SETTING_UP -> stringResource(R.string.floppy_setting_up)
    FloppyUiState.READY -> stringResource(R.string.floppy_ready)
    FloppyUiState.NEEDS_ATTENTION -> stringResource(R.string.floppy_needs_attention)
}

@Composable
internal fun FloppySettingsHost(state: AppUiState, viewModel: SyncSettingsViewModel, controls: TrackingSyncControls) {
    val connectionUiState by viewModel.floppyConnectionUiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.floppyConnected, state.floppyUsername) {
        if (state.floppyConnected && state.floppyUsername == null) viewModel.loadFloppyUsername()
    }
    val observedBootstrapProgress by controls.floppyBootstrapProgress.collectAsStateWithLifecycle()
    // The header already says "not connected"; the status details matter once connected.
    if (state.floppyConnected || state.floppyUiState == FloppyUiState.NEEDS_ATTENTION) SettingsSection(stringResource(R.string.floppy_status)) {
        // Whether it's connected; the role and the initial sync have their own rows.
        val attention = state.floppyUiState == FloppyUiState.NEEDS_ATTENTION
        ValueRow(
            stringResource(R.string.floppy_status),
            stringResource(when {
                attention -> R.string.floppy_needs_attention
                state.floppyConnected -> R.string.floppy_connected
                else -> R.string.floppy_not_connected
            }),
            state.floppyConnected && !attention,
        )
        if (state.floppyConnected) {
            ValueRow(stringResource(R.string.floppy_role), when {
                state.floppyIsMain -> stringResource(R.string.floppy_role_main)
                state.floppyUiState == FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_role_connected_inactive)
                else -> stringResource(R.string.floppy_role_secondary)
            })
            state.floppyServerVersion?.let { ValueRow(stringResource(R.string.floppy_server_version), it) }
            ValueRow(stringResource(R.string.floppy_initial_sync), when (state.floppyUiState) {
                FloppyUiState.READY -> stringResource(R.string.floppy_ready_short)
                FloppyUiState.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_failed)
                else -> stringResource(R.string.floppy_setting_up)
            })
            val bootstrapProgress = observedBootstrapProgress ?: state.floppyBootstrapProgress
            if (bootstrapProgress != null && bootstrapProgress.stage != FloppyBootstrapStage.COMPLETE) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    val determinate = bootstrapProgress.total > 0 && bootstrapProgress.stage == FloppyBootstrapStage.SYNCING
                    if (determinate) {
                        LinearProgressIndicator(progress = { (bootstrapProgress.processed.toFloat() / bootstrapProgress.total).coerceIn(0f, 1f) }, color = AccentLight, modifier = Modifier.weight(1f))
                    } else CircularProgressIndicator(color = AccentLight, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        when (bootstrapProgress.stage) {
                            FloppyBootstrapStage.PREPARING,
                            FloppyBootstrapStage.BUILDING_PLAN -> stringResource(R.string.floppy_building_sync_plan)
                            FloppyBootstrapStage.MATERIALIZING_QUEUE -> stringResource(R.string.floppy_preparing_sync_queue)
                            FloppyBootstrapStage.QUEUED -> stringResource(R.string.floppy_waiting_for_server)
                            FloppyBootstrapStage.CHECKING_REMOTE_STATE -> stringResource(R.string.floppy_checking_existing_history)
                            FloppyBootstrapStage.WAITING_FOR_SERVER -> stringResource(R.string.floppy_waiting_for_server)
                            FloppyBootstrapStage.SYNCING -> stringResource(R.string.floppy_syncing_with_floppy) + " · ${bootstrapProgress.processed} / ${bootstrapProgress.total}"
                            FloppyBootstrapStage.VERIFYING -> stringResource(R.string.floppy_verifying_sync)
                            FloppyBootstrapStage.STALLED -> stringResource(R.string.floppy_taking_longer)
                            FloppyBootstrapStage.COMPLETE -> stringResource(R.string.floppy_ready_short)
                            FloppyBootstrapStage.NEEDS_ATTENTION -> stringResource(R.string.floppy_initial_sync_needs_attention)
                        },
                        color = TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
                Text(stringResource(R.string.floppy_leave_screen_copy), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs))
            }
        }
        val retryableStage = observedBootstrapProgress?.stage?.allowsRetry() == true
        if (state.floppyUiState == FloppyUiState.NEEDS_ATTENTION || retryableStage) {
            TextButton(onClick = viewModel::retryFloppyInitialSync, modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(if (retryableStage && observedBootstrapProgress?.stage != FloppyBootstrapStage.NEEDS_ATTENTION) stringResource(R.string.retry_now) else stringResource(R.string.retry))
            }
        }
    }
    MainTrackerSection(state, controls)
    val unplaced by viewModel.floppyUnplaced.collectAsStateWithLifecycle()
    LaunchedEffect(state.floppyUiState, observedBootstrapProgress?.stage) { viewModel.refreshFloppyUnplaced() }
    val floppyBaseUrl = state.floppyBaseUrl
    if (state.floppyConnected && unplaced.isNotEmpty() && !floppyBaseUrl.isNullOrBlank()) {
        SettingsSection(stringResource(R.string.floppy_unplaced_title) + " (${unplaced.size})") {
            Text(
                stringResource(R.string.floppy_unplaced_copy),
                color = TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            unplaced.forEach { item ->
                FloppyUnplacedCard(item, floppyBaseUrl, onDismiss = { viewModel.dismissFloppyUnplaced(item) })
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
    val signInContext = LocalContext.current
    val sync by controls.syncProgress.collectAsStateWithLifecycle()
    FloppyConnectionSettingsForm(
        state,
        connectionUiState,
        { url, key, allowHttp -> viewModel.connectFloppy(url, key, allowHttp) },
        onSignInRequested = { url, allowHttp -> viewModel.beginFloppySignIn(signInContext, url, allowHttp) },
        syncing = sync.running,
        onSyncRequested = controls.onSync,
    )
    if (state.floppyConnected) {
        val crashContext = LocalContext.current
        var sendCrashReports by remember { mutableStateOf(com.cinetrack.crash.CrashReportSettings.isEnabled(crashContext)) }
        SettingsSection(stringResource(R.string.floppy_send_crash_reports)) {
            ToggleRow(stringResource(R.string.floppy_send_crash_reports), sendCrashReports) {
                sendCrashReports = it
                com.cinetrack.crash.CrashReporting.setEnabled(crashContext, it)
            }
        }
    }
    val floppyUri = LocalUriHandler.current
    KitSteps(
        stringResource(R.string.where_find_token),
        listOf(stringResource(R.string.floppy_step_1), stringResource(R.string.floppy_step_2), stringResource(R.string.floppy_step_3), stringResource(R.string.floppy_step_4)),
        stringResource(R.string.floppy_api_guide),
    ) { runCatching { floppyUri.openUri(FLOPPY_API_GUIDE_URL) } }
    KitFootnote(stringResource(R.string.floppy_connection_security))
    if (state.floppyConnected) {
        ConfirmedDangerText(
            text = stringResource(R.string.floppy_disconnect),
            title = stringResource(R.string.disconnect_floppy_title),
            body = stringResource(R.string.disconnect_floppy_body),
            onConfirm = viewModel::disconnectFloppy,
        )
    }
}

/** Choose which tracker is MAIN (two-way) and which is SECONDARY (receives
 * CineTrack's changes only). Offered once both are connected and the
 * candidate finished its initial sync. */
@Composable
private fun MainTrackerSection(state: AppUiState, controls: TrackingSyncControls) {
    if (!state.floppyConnected || !state.simklConnected) return
    val swap by controls.mainTrackerSwap.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    val floppyReady = state.floppyUiState == FloppyUiState.READY
    val canSwap = state.floppyIsMain || (floppyReady && state.floppyCanBeMain)
    SettingsSection(stringResource(R.string.main_tracker_title)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            // Two choices side by side; the current one is filled. Picking the
            // other asks first, so Floppy becomes main only by the user's choice.
            val shape = RoundedCornerShape(Radius.Pill)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = .06f)).border(.6.dp, Color.White.copy(alpha = .10f), shape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(false to "Simkl", true to "Floppy").forEach { (floppy, name) ->
                    val current = state.floppyIsMain == floppy
                    val switchingHere = swap.running && !current
                    val fill by androidx.compose.animation.animateColorAsState(if (current) Accent else Color.Transparent, label = "mainTrackerFill")
                    Row(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(shape).drawBehind { drawRect(fill) }
                            .clickable(enabled = !current && canSwap && !swap.running, role = androidx.compose.ui.semantics.Role.RadioButton) { asking = true }
                            .semantics { selected = current },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (switchingHere) {
                            CircularProgressIndicator(color = AccentLight, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(Spacing.sm))
                        } else if (current) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (switchingHere) stringResource(R.string.main_tracker_switching) else name,
                            color = when {
                                current -> Color.White
                                canSwap -> TextPrimary
                                else -> TextMuted
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                stringResource(
                    when {
                        state.floppyIsMain -> R.string.main_tracker_floppy_copy
                        !floppyReady -> R.string.main_tracker_wait_copy
                        !state.floppyCanBeMain -> R.string.main_tracker_update_floppy_copy
                        else -> R.string.main_tracker_simkl_copy
                    },
                ),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = Spacing.md),
            )
        }
        swap.error?.let { KitNotice(it) }
    }
    if (asking) {
        com.cinetrack.ui.components.ConfirmPrompt(
            title = stringResource(if (state.floppyIsMain) R.string.main_tracker_make_simkl else R.string.main_tracker_make_floppy),
            message = stringResource(if (state.floppyIsMain) R.string.main_tracker_confirm_simkl else R.string.main_tracker_confirm_floppy),
            confirmLabel = stringResource(R.string.main_tracker_switch),
            icon = androidx.compose.material.icons.Icons.Filled.SwapHoriz,
            destructive = false,
            onDismiss = { asking = false },
        ) {
            asking = false
            controls.onSwapMainTracker()
        }
    }
}

/**
 * Floppy recognised the account already connected, at an address CineTrack hasn't
 * used. The same server keeps the connection, pending changes and finished initial
 * sync; a new one starts over with a full initial sync. Tapping outside cancels.
 */
@Composable
internal fun FloppyAddressMoveDialog(viewModel: SyncSettingsViewModel) {
    val pending by viewModel.floppyAddressMove.collectAsStateWithLifecycle()
    val move = pending ?: return
    val host = remember(move.baseUrl) { runCatching { java.net.URI(move.baseUrl).host }.getOrNull() ?: move.baseUrl }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::cancelFloppyAddressMove,
        title = { Text(stringResource(R.string.floppy_move_title)) },
        text = { Text(stringResource(R.string.floppy_move_body, host), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { viewModel.answerFloppyAddressMove(same = true) }) {
                Text(stringResource(R.string.floppy_move_same), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.answerFloppyAddressMove(same = false) }) { Text(stringResource(R.string.floppy_move_new)) }
        },
    )
}

@Composable
private fun FloppyUnplacedCard(
    item: com.cinetrack.domain.FloppyUnplacedItem,
    floppyBaseUrl: String,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .glass(RoundedCornerShape(Radius.Medium))
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.showTitle,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        if (item.season == 0) stringResource(R.string.floppy_unplaced_special, item.label) else item.label,
                        item.episodeTitle,
                    ).joinToString(" · "),
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.floppy_unplaced_dismiss), tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.FilledTonalButton(
                onClick = { runCatching { uriHandler.openUri(item.floppySearchUrl(floppyBaseUrl)) } },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.floppy_unplaced_open), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.floppy_unplaced_marked), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun FloppyConnectionSettingsForm(
    state: AppUiState,
    connectionUiState: FloppyConnectionUiState = FloppyConnectionUiState(),
    onConnectRequested: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSignInRequested: ((String, Boolean) -> Unit)? = null,
    syncing: Boolean = false,
    onSyncRequested: (() -> Unit)? = null,
) {
    var url by rememberSaveable(state.floppyBaseUrl) { mutableStateOf(state.floppyBaseUrl.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var allowLocalHttp by rememberSaveable(state.floppyAllowInsecureLocalHttp) { mutableStateOf(state.floppyAllowInsecureLocalHttp) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val editing = state.floppyConnected
    // Sign-in replaced API keys: the key field is left only where sign-in
    // isn't offered. Once connected, neither shows (like the Simkl section).
    val keyFlow = onSignInRequested == null && !editing
    val signInFlow = onSignInRequested != null && !editing
    // An address typed without a scheme is taken as https://, or http:// when
    // local-network HTTP is on.
    val fullUrl = withDefaultFloppyScheme(url, allowLocalHttp)
    val validUrl = isFloppyUrlValid(fullUrl)
    val canSubmit = validUrl && (apiKey.isNotBlank() || editing) && !connectionUiState.running

    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    SettingsSection(stringResource(R.string.floppy_connection)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            // Three steps: server address, token, verify. The bar fills as each is done.
            val step = when {
                !validUrl -> 1
                apiKey.isBlank() && !editing -> 2
                else -> 3
            }
            if (keyFlow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { index ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (index < step) Color(0xFF85B7EB) else Color.White.copy(alpha = .12f)))
                }
            }
            Text(
                stringResource(R.string.floppy_step_label, step, 3) + " · " + stringResource(
                    when (step) { 1 -> R.string.floppy_step_server; 2 -> R.string.floppy_step_token; else -> R.string.floppy_step_verify },
                ),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth().testTag("floppy_server_url_field"),
                singleLine = true,
                enabled = !connectionUiState.running,
                label = { Text(stringResource(R.string.floppy_server_url)) },
                prefix = if (url.isNotBlank() && !hasUrlScheme(url)) {
                    { Text(if (allowLocalHttp) "http://" else "https://", color = TextMuted) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = credentialFieldColors(),
                isError = url.isNotBlank() && !validUrl,
            )
            if (url.isNotBlank() && !validUrl) Text(stringResource(R.string.floppy_error_invalid_url), color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
            if (signInFlow) {
                Spacer(Modifier.height(Spacing.md))
                // Like Simkl: sign in on Floppy's own page, no key to create or paste.
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSignInRequested(fullUrl, allowLocalHttp)
                    },
                    enabled = validUrl && !connectionUiState.running,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("floppy_sign_in_button"),
                    shape = RoundedCornerShape(Radius.Pill),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185FA5), disabledContainerColor = Color.White.copy(alpha = .08f)),
                ) {
                    Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.floppy_sign_in), fontWeight = FontWeight.ExtraBold)
                }
            }
            if (keyFlow) {
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = apiKey,
                 onValueChange = { apiKey = if (apiKey.isEmpty() && editing) it.replace("•", "") else it },
                modifier = Modifier.fillMaxWidth().testTag("floppy_api_key_field"),
                singleLine = true,
                enabled = !connectionUiState.running,
                label = { Text(stringResource(if (editing) R.string.floppy_api_key_keep else R.string.floppy_api_key)) },
                placeholder = { if (editing) Text(stringResource(R.string.floppy_saved_securely), style = androidx.compose.material3.MaterialTheme.typography.bodySmall) },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                // Password keyboard: no suggestions or learned words from a pasted key.
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onConnectRequested(fullUrl, apiKey.trim(), allowLocalHttp)
                }),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }, enabled = !connectionUiState.running) {
                        Icon(if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(if (showApiKey) R.string.hide_api_key else R.string.show_api_key), tint = TextSecondary)
                    }
                },
                colors = credentialFieldColors(),
            )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { advancedExpanded = !advancedExpanded }.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.floppy_advanced), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextMuted)
            }
            if (advancedExpanded) {
                ToggleRow(stringResource(R.string.floppy_allow_http_local_network), allowLocalHttp) { allowLocalHttp = it }
                Text(stringResource(R.string.floppy_http_local_supporting), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = Spacing.sm))
            }
            if (connectionUiState.stage != FloppyConnectionStage.IDLE) {
                Text(
                    floppyConnectionStageLabel(connectionUiState.stage, state.floppyUiState),
                    color = if (connectionUiState.stage == FloppyConnectionStage.FAILED) androidx.compose.material3.MaterialTheme.colorScheme.error else TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            }
            connectionUiState.error?.let { error ->
                Text(
                    connectionUiState.message ?: floppyConnectionErrorMessage(error),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            // Connected to this address, the button syncs (like Simkl's); a changed
            // address turns it back into Reconnect.
            val syncAction = editing && onSyncRequested != null && fullUrl == state.floppyBaseUrl.orEmpty().trim()
            if (syncAction) Button(
                onClick = { onSyncRequested?.invoke() },
                enabled = !syncing && !connectionUiState.running,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("floppy_sync_button"),
                shape = RoundedCornerShape(Radius.Pill),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185FA5), disabledContainerColor = Color.White.copy(alpha = .08f)),
            ) {
                if (syncing) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                } else {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(stringResource(R.string.sync_now), fontWeight = FontWeight.ExtraBold)
            }
            // Signing in is the connect action until a connection exists.
            else if (!signInFlow) Button(
                onClick = {
                    focusManager.clearFocus()
                    onConnectRequested(fullUrl, apiKey.trim(), allowLocalHttp)
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("floppy_connect_button"),
                shape = RoundedCornerShape(Radius.Pill),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185FA5), disabledContainerColor = Color.White.copy(alpha = .08f)),
            ) {
                if (connectionUiState.running) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                } else {
                    Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(stringResource(if (editing) R.string.floppy_reconnect else R.string.floppy_connect), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun floppyConnectionStageLabel(stage: FloppyConnectionStage, persistentState: FloppyUiState): String = when (stage) {
    FloppyConnectionStage.CHECKING_SERVER -> stringResource(R.string.floppy_stage_checking_server)
    FloppyConnectionStage.AUTHENTICATING -> stringResource(R.string.floppy_stage_authenticating)
    FloppyConnectionStage.ACTIVATING -> stringResource(R.string.floppy_stage_activating)
    FloppyConnectionStage.INITIAL_SYNC -> stringResource(R.string.floppy_stage_initial_sync)
    FloppyConnectionStage.COMPLETE -> when (persistentState) {
        FloppyUiState.READY -> stringResource(R.string.floppy_ready)
        FloppyUiState.CONNECTED_INACTIVE -> stringResource(R.string.floppy_connected_inactive)
        else -> stringResource(R.string.floppy_stage_complete)
    }
    FloppyConnectionStage.FAILED -> stringResource(R.string.floppy_connection_failed)
    FloppyConnectionStage.IDLE -> ""
}

@Composable
private fun floppyConnectionErrorMessage(error: FloppyConnectionError): String = when (error) {
    FloppyConnectionError.INVALID_REQUEST -> stringResource(R.string.floppy_error_invalid_url)
    FloppyConnectionError.AUTHENTICATION -> stringResource(R.string.floppy_error_authentication)
    FloppyConnectionError.DNS -> stringResource(R.string.floppy_error_dns)
    FloppyConnectionError.TIMEOUT -> stringResource(R.string.floppy_error_timeout)
    FloppyConnectionError.TLS -> stringResource(R.string.floppy_error_tls)
    FloppyConnectionError.NETWORK -> stringResource(R.string.floppy_error_network)
    FloppyConnectionError.WRONG_API -> stringResource(R.string.floppy_error_wrong_api)
    FloppyConnectionError.INVALID_RESPONSE -> stringResource(R.string.floppy_error_invalid_response)
    FloppyConnectionError.BOOTSTRAP -> stringResource(R.string.floppy_connected_attention)
    FloppyConnectionError.UNKNOWN -> stringResource(R.string.floppy_error_unavailable)
}

@Composable
/** Shared credential/input treatment used by TMDB and Floppy. */
internal fun credentialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = GlassStrong,
    errorBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
)

internal fun hasUrlScheme(value: String): Boolean = Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(value.trim())

internal fun withDefaultFloppyScheme(value: String, allowLocalHttp: Boolean): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || hasUrlScheme(trimmed)) return trimmed
    return (if (allowLocalHttp) "http://" else "https://") + trimmed
}

private fun isFloppyUrlValid(value: String): Boolean = runCatching {
    val uri = java.net.URI(value.trim())
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

@Composable
private fun FloppyStatePreviewContent(state: AppUiState) {
    SettingsSection(stringResource(R.string.floppy_status)) {
        ValueRow(stringResource(R.string.floppy_status), floppyStatusLabel(state.floppyUiState), state.floppyUiState == FloppyUiState.READY)
        if (state.floppyConnected) {
            ValueRow(stringResource(R.string.floppy_role), if (state.floppyUiState == FloppyUiState.CONNECTED_INACTIVE) stringResource(R.string.floppy_role_connected_inactive) else stringResource(R.string.floppy_role_secondary))
            ValueRow(stringResource(R.string.floppy_initial_sync), if (state.floppyUiState == FloppyUiState.NEEDS_ATTENTION) stringResource(R.string.floppy_initial_sync_failed) else stringResource(R.string.floppy_ready_short))
        }
    }
}

@Preview(name = "Floppy not connected", showBackground = true)
@Composable
private fun FloppyNotConnectedPreview() = FloppyStatePreviewContent(AppUiState(floppyUiState = FloppyUiState.NOT_CONNECTED))

@Preview(name = "Floppy connected inactive", showBackground = true)
@Composable
private fun FloppyConnectedInactivePreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.CONNECTED_INACTIVE))

@Preview(name = "Floppy setting up", showBackground = true)
@Composable
private fun FloppySettingUpPreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.SETTING_UP))

@Preview(name = "Floppy ready", showBackground = true)
@Composable
private fun FloppyReadyPreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.READY))

@Preview(name = "Floppy needs attention", showBackground = true)
@Composable
private fun FloppyNeedsAttentionPreview() = FloppyStatePreviewContent(AppUiState(floppyConnected = true, floppyUiState = FloppyUiState.NEEDS_ATTENTION))
