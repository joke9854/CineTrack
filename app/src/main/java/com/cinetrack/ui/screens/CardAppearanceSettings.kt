package com.cinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.CardAppearance
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.theme.*

@Composable
internal fun CardAppearanceSettings(state: AppUiState, viewModel: CineTrackViewModel) {
    val sampleTitle = stringResource(R.string.appearance_sample_title)
    val samples = remember(state.allMedia, sampleTitle) {
        state.allMedia.distinctBy { it.stableKey }.take(4).ifEmpty {
            List(4) { MediaCard(-it - 1, MediaType.MOVIE, sampleTitle, score = 8.2) }
        }
    }
    var posterPreview by rememberSaveable { mutableStateOf("rows") }
    CompositionLocalProvider(LocalCardAppearance provides CardAppearance(state.heroLayout, state.posterFormat, state.posterSize)) {
        SettingsSection(stringResource(R.string.main_ui_color)) {
            val choices = listOf(
                Triple("watching", stringResource(R.string.color_blue), StatusWatching),
                Triple("planned", stringResource(R.string.color_gold), StatusPlanned),
                Triple("paused", stringResource(R.string.color_orange), StatusPaused),
                Triple("completed", stringResource(R.string.color_green), Success),
                Triple("dropped", stringResource(R.string.color_red), StatusDropped),
            )
            Row(Modifier.fillMaxWidth().selectableGroup().padding(Spacing.sm)) {
                choices.forEach { (key, label, color) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(Radius.Small))
                        .selectable(selected = state.uiAccent == key, role = Role.RadioButton) { viewModel.setUiAccent(key) }
                        .padding(vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(36.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            if (state.uiAccent == key) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text(label, color = TextPrimary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        SettingsSection(stringResource(R.string.hero_layout)) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppearanceOptions(listOf(
                    "standard" to stringResource(R.string.hero_original_short), "landscape" to "16:9", "balanced" to "4:3",
                ), state.heroLayout, viewModel::setHeroLayout)
                Box(Modifier.clearAndSetSemantics { }) {
                    HeroCard(samples.first(), {}, { _, _ -> }, {}, { null }, state.metadataLanguage,
                        preview = true, previewTagline = stringResource(R.string.appearance_sample_tagline), onInteraction = {})
                }
                AppearanceHint(stringResource(R.string.hero_layout_hint))
            }
        }
        SettingsSection(stringResource(R.string.appearance_posters)) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppearanceOptions(listOf("rows" to stringResource(R.string.appearance_rows), "grid" to stringResource(R.string.appearance_grid)),
                    posterPreview, { posterPreview = it })
                BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                    if (posterPreview == "grid") {
                        val columns = CardAppearance.gridColumns(state.cardDensity)
                        val width = (maxWidth - 12.dp * (columns - 1)) / columns
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(columns) { MediaPoster(samples[it % samples.size], width = width, showYear = false, onClick = {}) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            MediaPoster(samples.first(), showYear = false, onClick = {})
                        }
                    }
                }
                Text(stringResource(R.string.poster_format), color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                AppearanceOptions(listOf("classic" to "2:3", "compact" to "3:4", "tall" to "9:16"), state.posterFormat, viewModel::setPosterFormat)
                if (posterPreview == "rows") {
                    Text(stringResource(R.string.appearance_row_size), color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    AppearanceOptions(listOf(
                        "small" to stringResource(R.string.poster_small), "standard" to stringResource(R.string.standard), "large" to stringResource(R.string.poster_large),
                    ), state.posterSize, viewModel::setPosterSize)
                } else {
                    Text(stringResource(R.string.appearance_columns), color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    AppearanceOptions(listOf("large" to "2", "standard" to "3", "compact" to "4"), state.cardDensity, viewModel::setCardDensity)
                }
                AppearanceHint(stringResource(if (posterPreview == "rows") R.string.appearance_rows_hint else R.string.appearance_grid_hint))
            }
        }
    }
}

@Composable
private fun AppearanceOptions(choices: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        choices.forEach { (key, label) ->
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(Radius.Small))
                .background(if (key == selected) Accent.copy(alpha = .24f) else Color.Transparent)
                .border(if (key == selected) 1.dp else .5.dp, if (key == selected) AccentLight else GlassStrong, RoundedCornerShape(Radius.Small))
                .selectable(selected = key == selected, role = Role.RadioButton) { onSelected(key) }
                .padding(horizontal = 4.dp, vertical = Spacing.sm), contentAlignment = Alignment.Center) {
                Text(label, color = if (key == selected) TextPrimary else TextSecondary, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, fontWeight = if (key == selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun AppearanceHint(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
}
