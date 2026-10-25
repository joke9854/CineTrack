package com.cinetrack.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.CardAppearance
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.settings.AppearanceSettings
import com.cinetrack.ui.settings.SettingsViewModel
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.theme.*
import kotlin.math.roundToInt

/** Width the miniatures are laid out at before being scaled into the preview. */
private val MiniatureWidth = 400.dp
private val MiniatureHeight = 560.dp

/**
 * Aspetto: a live preview of Scopri and the poster lists stays fixed under the
 * title, and every choice below it updates the preview straight away.
 */
@Composable
internal fun AppearancePage(title: String, state: AppUiState, settingsViewModel: SettingsViewModel, onBack: () -> Unit) {
    var posterMode by rememberSaveable { mutableStateOf("rows") }
    val appearance by settingsViewModel.appearance.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalCardAppearance provides CardAppearance(appearance.heroLayout, appearance.posterFormat, appearance.posterSize, appearance.castPhotoShape, appearance.posterTitles)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                Spacer(Modifier.size(8.dp))
                PageTitle(title, Modifier.weight(1f))
            }
            AppearancePreview(state, posterMode, Modifier.padding(horizontal = Spacing.xl))
            Spacer(Modifier.height(Spacing.sm))
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    // The choices fade out under the preview instead of being cut by a hard line.
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, (18.dp.toPx() / size.height).coerceIn(0f, 1f) to Color.Black),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.sm, bottom = 40.dp)
                    .navigationBarsPadding(),
            ) {
                AppearanceChoices(appearance, settingsViewModel, posterMode) { posterMode = it }
            }
        }
    }
}

@Composable
private fun AppearanceChoices(state: AppearanceSettings, viewModel: SettingsViewModel, posterMode: String, onPosterMode: (String) -> Unit) {
    AppearanceLabel(stringResource(R.string.appearance_color_short))
    val choices = listOf(
        Triple("green", stringResource(R.string.color_green), AccentPalette.Green),
        Triple("silver", stringResource(R.string.color_silver), AccentPalette.Silver),
        Triple("champagne", stringResource(R.string.color_champagne), AccentPalette.Champagne),
        Triple("blue", stringResource(R.string.color_blue), AccentPalette.Blue),
    )
    val selectedAccent = AccentPalette.normalize(state.uiAccent)
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).selectableGroup().padding(vertical = Spacing.sm)) {
        choices.forEach { (key, label, color) ->
            val selected = selectedAccent == key
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.Small))
                    .selectable(selected = selected, role = Role.RadioButton) { viewModel.setUiAccent(key) }
                    .padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The chosen colour sits in a white ring with a gap, like a picked swatch.
                Box(
                    Modifier.size(44.dp)
                        .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                        .padding(Spacing.xs)
                        .background(color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, null, tint = if (color.luminance() > .6f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(label, color = if (selected) TextPrimary else TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium))
            .selectable(selected = state.posterAccent, role = Role.Switch) { viewModel.setPosterAccent(!state.posterAccent) }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.poster_accent_title), color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.poster_accent_subtitle), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(Spacing.md))
        // The same switch as every other settings toggle.
        CompactSwitch(state.posterAccent)
    }

    AppearanceLabel(stringResource(R.string.appearance_discover_short))
    SegmentedChoice(
        listOf("a" to stringResource(R.string.discover_layout_classic), "b" to stringResource(R.string.discover_layout_mosaic)),
        state.discoverLayout, onSelected = viewModel::setDiscoverLayout,
    )

    val mosaic = state.discoverLayout == "b"
    AppearanceLabel(stringResource(R.string.appearance_cover_short), note = if (mosaic) stringResource(R.string.appearance_cover_classic_only) else null)
    SegmentedChoice(
        listOf("standard" to stringResource(R.string.hero_original_short), "landscape" to "16:9", "balanced" to "4:3"),
        state.heroLayout, enabled = !mosaic, onSelected = viewModel::setHeroLayout,
    )

    AppearanceLabel(stringResource(R.string.appearance_posters_short))
    SegmentedChoice(
        listOf("rows" to stringResource(R.string.appearance_rows_short), "grid" to stringResource(R.string.appearance_grid_short)),
        posterMode, onSelected = onPosterMode,
    )
    Spacer(Modifier.height(Spacing.sm))
    SegmentedChoice(listOf("classic" to "2:3", "compact" to "3:4", "tall" to "9:16"), state.posterFormat, onSelected = viewModel::setPosterFormat)
    Spacer(Modifier.height(Spacing.sm))
    if (posterMode == "rows") {
        SegmentedChoice(
            listOf("small" to stringResource(R.string.poster_small), "standard" to stringResource(R.string.standard), "large" to stringResource(R.string.poster_large)),
            state.posterSize, onSelected = viewModel::setPosterSize,
        )
    } else {
        SegmentedChoice(
            listOf("large" to "2", "standard" to "3", "compact" to "4").map { (key, columns) -> key to "$columns ${stringResource(R.string.appearance_columns).lowercase(com.cinetrack.ui.currentLocale())}" },
            state.cardDensity, onSelected = viewModel::setCardDensity,
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    SegmentedChoice(
        listOf("show" to stringResource(R.string.poster_titles_show), "hide" to stringResource(R.string.poster_titles_hide)),
        if (state.posterTitles) "show" else "hide", onSelected = { viewModel.setPosterTitles(it == "show") },
    )

    AppearanceLabel(stringResource(R.string.appearance_cast_photos_short))
    SegmentedChoice(
        listOf("circle" to stringResource(R.string.cast_photo_circle), "poster" to stringResource(R.string.cast_photo_poster)),
        state.castPhotoShape, onSelected = viewModel::setCastPhotoShape,
    )
}

@Composable
private fun AppearanceLabel(text: String, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.lg, bottom = Spacing.sm), verticalAlignment = Alignment.Bottom) {
        Text(
            text.uppercase(com.cinetrack.ui.currentLocale()), color = TextMuted,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = com.cinetrack.ui.theme.EyebrowLetterSpacing,
        )
        if (note != null) {
            Spacer(Modifier.weight(1f))
            Text(note, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A pill track with a sliding accent thumb behind the chosen option. */
@Composable
private fun SegmentedChoice(
    choices: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    val shape = RoundedCornerShape(Radius.Pill)
    val index = choices.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val onAccent = if (Accent.luminance() > .6f) Color.Black else Color.White
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(46.dp).alpha(if (enabled) 1f else .42f)
            .glass(shape).padding(Spacing.xs),
    ) {
        val segment = maxWidth / choices.size
        val offset by animateDpAsState(segment * index, Motion.snappy(), label = "segmentThumb")
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }.width(segment).fillMaxHeight().clip(shape).background(Accent.copy(alpha = .9f)))
        val segmentHaptics = com.cinetrack.ui.components.rememberAppHaptics()
        Row(Modifier.fillMaxSize().selectableGroup()) {
            choices.forEach { (key, label) ->
                val chosen = key == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton) { if (!chosen) segmentHaptics.tick(); onSelected(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, color = if (chosen) onAccent else TextSecondary, textAlign = TextAlign.Center, maxLines = 1,
                        style = MaterialTheme.typography.bodySmall, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Two miniature screens: Scopri with the chosen layout and cover, and a poster list. */
@Composable
private fun AppearancePreview(state: AppUiState, posterMode: String, modifier: Modifier = Modifier) {
    val sampleTitle = stringResource(R.string.appearance_sample_title)
    val samples = remember(state.allMedia, sampleTitle) {
        state.allMedia.distinctBy { it.stableKey }.take(8).ifEmpty {
            List(8) { MediaCard(-it - 1, MediaType.MOVIE, sampleTitle, score = 8.2) }
        }
    }
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Large)).padding(10.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniScreen(Modifier.weight(1f)) { DiscoverMiniature(state, samples) }
        MiniScreen(Modifier.weight(1f)) { PosterMiniature(state, samples, posterMode) }
    }
}

@Composable
private fun MiniScreen(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier.aspectRatio(MiniatureWidth / MiniatureHeight)
            .clip(RoundedCornerShape(Radius.Medium))
            .background(Background0)
            .border(.5.dp, GlassStrokeMedium, RoundedCornerShape(Radius.Medium)),
    ) {
        val scale = maxWidth / MiniatureWidth
        Box(Modifier.layout { measurable, _ ->
            // Lay the real components out at phone width, then draw them scaled down.
            val placeable = measurable.measure(Constraints.fixed(MiniatureWidth.roundToPx(), MiniatureHeight.roundToPx()))
            layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }) { content() }
    }
}

@Composable
private fun MiniHeader(title: String, tabs: List<String>) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, top = 22.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(Modifier.glass(RoundedCornerShape(Radius.Pill)).padding(Spacing.xs)) {
            tabs.forEachIndexed { index, tab ->
                Text(
                    tab, color = if (index == 0) (if (Accent.luminance() > .6f) Color.Black else Color.White) else TextSecondary,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.Pill))
                        .background(if (index == 0) Accent else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoverMiniature(state: AppUiState, samples: List<MediaCard>) {
    Column(Modifier.fillMaxSize()) {
        MiniHeader(stringResource(R.string.appearance_discover_short), listOf(stringResource(R.string.movies), stringResource(R.string.tv_shows)))
        if (state.discoverLayout == "b") {
            Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MiniArt(samples[0], wide = true, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MiniArt(samples[1 % samples.size], wide = false, Modifier.weight(1f).aspectRatio(2f / 3f))
                    MiniArt(samples[2 % samples.size], wide = false, Modifier.weight(1f).aspectRatio(2f / 3f))
                }
            }
        } else {
            Box(Modifier.padding(horizontal = Spacing.xl)) {
                HeroCard(samples.first(), {}, { _, _ -> }, {}, { null }, state.metadataLanguage,
                    preview = true, previewTagline = stringResource(R.string.appearance_sample_tagline), onInteraction = {})
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.popular_movies), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = Spacing.xl))
            Spacer(Modifier.height(10.dp))
            PosterRow(samples.drop(1))
        }
    }
}

@Composable
private fun PosterMiniature(state: AppUiState, samples: List<MediaCard>, posterMode: String) {
    Column(Modifier.fillMaxSize()) {
        MiniHeader(stringResource(R.string.library), listOf(stringResource(R.string.movies), stringResource(R.string.tv_shows)))
        if (posterMode == "grid") {
            val columns = CardAppearance.gridColumns(state.cardDensity)
            val width = (MiniatureWidth - 40.dp - 12.dp * (columns - 1)) / columns
            Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        repeat(columns) { MediaPoster(samples[(row * columns + it) % samples.size], width = width, showTitle = LocalCardAppearance.current.posterTitles, showYear = false, onClick = {}) }
                    }
                }
            }
        } else {
            PosterRow(samples)
            Spacer(Modifier.height(18.dp))
            PosterRow(samples.drop(3) + samples.take(3))
        }
    }
}

@Composable
private fun PosterRow(items: List<MediaCard>) {
    Row(
        Modifier.horizontalScroll(rememberScrollState(), enabled = false).padding(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items.take(4).forEach { MediaPoster(it, showTitle = LocalCardAppearance.current.posterTitles, showYear = false, onClick = {}) }
    }
}

@Composable
private fun MiniArt(media: MediaCard, wide: Boolean, modifier: Modifier) {
    val art = if (wide) media.backdropUrl ?: media.posterUrl else media.posterUrl ?: media.backdropUrl
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(GlassBare)) {
        if (!art.isNullOrBlank()) coil3.compose.AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

