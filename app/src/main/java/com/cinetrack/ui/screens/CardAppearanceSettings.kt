package com.cinetrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.CardAppearance
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.CineTrackViewModel
import com.cinetrack.ui.components.GlassDivider
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
    // Use the same components and dimensions as the live pages, including empty-artwork fallbacks.
    CompositionLocalProvider(LocalCardAppearance provides CardAppearance(state.heroLayout, state.posterFormat, state.posterSize)) {
        SettingsSection(stringResource(R.string.hero_layout)) {
            AppearanceChoices(listOf(
                "standard" to stringResource(R.string.hero_standard),
                "landscape" to stringResource(R.string.hero_landscape),
                "balanced" to stringResource(R.string.hero_balanced),
            ), state.heroLayout, viewModel::setHeroLayout)
        }
        Text(stringResource(R.string.hero_preview_hint), color = TextMuted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm))
        Box(Modifier.padding(horizontal = Spacing.xl).clearAndSetSemantics { }) {
            HeroCard(media = samples.first(), onMedia = {}, onStatus = { _, _ -> }, onNotInterested = {},
                loadTagline = { null }, metadataLanguage = state.metadataLanguage,
                preview = true, previewTagline = stringResource(R.string.appearance_sample_tagline), onInteraction = {})
        }
        SettingsSection(stringResource(R.string.card_density)) {
            listOf(
                Triple("compact", stringResource(R.string.compact), stringResource(R.string.density_compact_description)),
                Triple("standard", stringResource(R.string.standard), stringResource(R.string.density_standard_description)),
                Triple("large", stringResource(R.string.large), stringResource(R.string.density_large_description)),
            ).forEachIndexed { index, (key, label, description) ->
                ChoiceRow(label, state.cardDensity == key, description) { viewModel.setCardDensity(key) }
                if (index != 2) GlassDivider()
            }
        }
        PreviewCaption(stringResource(R.string.grid_preview))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).clearAndSetSemantics { }) {
            val columns = CardAppearance.gridColumns(state.cardDensity)
            val width = (maxWidth - 12.dp * (columns - 1)) / columns
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(columns) { index ->
                    MediaPoster(samples[index % samples.size], width = width, showYear = false, onClick = {})
                }
            }
        }
        SettingsSection(stringResource(R.string.poster_format)) {
            AppearanceChoices(listOf(
                "classic" to stringResource(R.string.poster_classic),
                "compact" to stringResource(R.string.poster_compact),
                "tall" to stringResource(R.string.poster_tall),
            ), state.posterFormat, viewModel::setPosterFormat)
        }
        SettingsSection(stringResource(R.string.poster_size)) {
            AppearanceChoices(listOf(
                "small" to stringResource(R.string.poster_small),
                "standard" to stringResource(R.string.poster_standard),
                "large" to stringResource(R.string.poster_large),
            ), state.posterSize, viewModel::setPosterSize)
        }
        PreviewCaption(stringResource(R.string.poster_preview_hint))
        Box(Modifier.padding(horizontal = Spacing.xl).clearAndSetSemantics { }) {
            MediaPoster(samples.first(), showYear = false, onClick = {})
        }
    }
}

@Composable
private fun AppearanceChoices(choices: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    choices.forEachIndexed { index, (key, label) ->
        ChoiceRow(label, key == selected) { onSelected(key) }
        if (index != choices.lastIndex) GlassDivider()
    }
}

@Composable
private fun PreviewCaption(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md))
}
