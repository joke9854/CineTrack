@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.StreamingProvider
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.*
import com.cinetrack.ui.theme.*
import java.util.Locale

internal fun providerRegion(state: AppUiState): String = com.cinetrack.domain.resolveProviderRegion(
    state.providerRegion, state.contentRegions, state.metadataRegion, Locale.getDefault().country,
)

@Composable
internal fun StreamingSettings(state: AppUiState, viewModel: CineTrackViewModel, onPage: (String) -> Unit) {
    SettingsSection(stringResource(R.string.preferred_providers)) {
        Row(Modifier.fillMaxWidth().clickable { onPage(SettingsPages.Providers) }.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tv, null, tint = AccentLight, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.my_favorites), color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(state.preferredProviders.sorted().joinToString(", ").ifBlank { stringResource(R.string.all_services) },
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, stringResource(R.string.manage_providers), tint = TextMuted)
        }
    }
    SettingsSection(stringResource(R.string.visible_offer_types)) {
        val types = listOf("flatrate" to R.string.subscription, "rent" to R.string.rent,
            "buy" to R.string.buy, "free" to R.string.provider_free, "ads" to R.string.provider_ads)
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.visible_offer_types_description), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            types.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { (key, label) ->
                        val selected = key in state.visibleProviderTypes
                        FilterChip(selected = selected, onClick = {
                            viewModel.setVisibleProviderTypes(if (selected) state.visibleProviderTypes - key else state.visibleProviderTypes + key)
                        }, label = { Text(stringResource(label)) }, modifier = Modifier.weight(1f),
                            leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(labelColor = TextSecondary, selectedLabelColor = TextPrimary,
                                selectedContainerColor = Accent.copy(alpha = .22f), selectedLeadingIconColor = AccentLight))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun StreamingProvidersScreen(state: AppUiState, viewModel: CineTrackViewModel, onBack: () -> Unit) {
    val providers by viewModel.settingsProviders.collectAsStateWithLifecycle()
    val loading by viewModel.settingsProvidersLoading.collectAsStateWithLifecycle()
    val failed by viewModel.settingsProvidersError.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showCountries by rememberSaveable { mutableStateOf(false) }
    val region = providerRegion(state)
    LaunchedEffect(region, state.tmdbApiConfigured) { viewModel.loadSettingsStreamingProviders(region) }
    val filtered = remember(providers, query) { providers.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    val unavailable = remember(providers, state.preferredProviders) { state.preferredProviders - providers.map { it.name }.toSet() }
    AdaptiveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onBack)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.streaming_providers), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.providers_selected_count, state.preferredProviders.size), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).clickable { showCountries = true }.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.provider_country), color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("${Locale("", region).getDisplayCountry(Locale.getDefault())} ($region)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.change), color = AccentLight, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.search_providers)) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.Medium),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentLight, unfocusedBorderColor = GlassStrong))
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(stringResource(R.string.available_services), Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                        actionLabel = if (state.preferredProviders.isNotEmpty()) stringResource(R.string.clear_selection) else null,
                        onAction = { viewModel.setPreferredProviders(emptySet()) })
                }
                if (!state.tmdbApiConfigured || loading || failed || filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.lg)) {
                            Text(stringResource(when {
                                !state.tmdbApiConfigured -> R.string.providers_require_tmdb
                                loading -> R.string.loading
                                failed -> R.string.providers_load_failed
                                providers.isEmpty() -> R.string.no_providers_in_country
                                else -> R.string.no_provider_matches
                            }), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            if (failed && !loading) TextButton(onClick = { viewModel.loadSettingsStreamingProviders(region) }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
                items(filtered, key = StreamingProvider::id) { provider ->
                    ProviderTile(provider, provider.name in state.preferredProviders) { viewModel.togglePreferredProvider(provider.name) }
                }
                if (!loading && !failed && unavailable.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = Spacing.md)) {
                            SectionHeader(stringResource(R.string.other_saved_services))
                            Text(stringResource(R.string.providers_preserved), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(unavailable.sorted().filter { it.contains(query, ignoreCase = true) }, key = { "saved:$it" }) { name ->
                        ProviderTile(StreamingProvider(id = -1, name = name, logoUrl = null), true) { viewModel.togglePreferredProvider(name) }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.favorites_filter_help), color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Spacing.md))
                }
            }
        }
    }
    if (showCountries) ProviderCountrySheet(region, onDismiss = { showCountries = false }) {
        viewModel.setProviderRegion(it)
        showCountries = false
    }
}

@Composable
private fun ProviderTile(provider: StreamingProvider, selected: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).glass(RoundedCornerShape(Radius.Small))
        .background(if (selected) Accent.copy(alpha = .22f) else androidx.compose.ui.graphics.Color.Transparent)
        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }).padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(Radius.Compact)).background(GlassSubtle), contentAlignment = Alignment.Center) {
            if (provider.logoUrl != null) AsyncImage(provider.logoUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Icon(Icons.Filled.Tv, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(provider.name, color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = AccentLight, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ProviderCountrySheet(selected: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val locale = Locale.getDefault()
    val countries = remember(locale) { Locale.getISOCountries().map { it to Locale("", it).getDisplayCountry(locale) }.sortedBy { it.second } }
    SearchableChoiceSheet(stringResource(R.string.provider_country), stringResource(R.string.search_countries), countries, selected, onDismiss, onSelected)
}

@Composable
internal fun SearchableChoiceSheet(
    title: String,
    searchLabel: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val matches = remember(choices, query) { choices.filter { (code, name) -> code.contains(query.trim(), true) || name.contains(query.trim(), true) } }
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl)) {
            SectionHeader(title)
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text(searchLabel) }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(Radius.Medium),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentLight, unfocusedBorderColor = GlassStrong))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                if (matches.isEmpty()) item { Text(stringResource(R.string.choice_no_results), color = TextSecondary, modifier = Modifier.padding(vertical = Spacing.lg)) }
                items(matches, key = { it.first }) { (code, name) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(Radius.Small))
                        .clickable { onSelected(code) }.padding(vertical = Spacing.md, horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (code == "system" || code == name) name else "$name ($code)", color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (code == selected) Icon(Icons.Filled.Check, stringResource(R.string.poster_selected), tint = AccentLight)
                    }
                }
            }
        }
    }
}
