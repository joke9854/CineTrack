package com.cinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.R
import com.cinetrack.domain.DiscoverMovieFilters
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.rememberLightHapticAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import java.time.Year

@Composable
fun DiscoverFiltersScreen(
    results: List<MediaCard>,
    loading: Boolean,
    onApply: (DiscoverMovieFilters) -> Unit,
    onBack: () -> Unit,
    onMedia: (MediaCard) -> Unit,
    onStatus: (MediaCard, LibraryStatus) -> Unit,
) {
    var mediaType by remember { mutableStateOf(MediaType.MOVIE) }
    val genres = if (mediaType == MediaType.TV) {
        listOf(
            10759 to stringResource(R.string.genre_action),
            16 to stringResource(R.string.genre_animation),
            35 to stringResource(R.string.genre_comedy),
            80 to stringResource(R.string.genre_crime),
            99 to stringResource(R.string.genre_documentary),
            18 to stringResource(R.string.genre_drama),
            10751 to stringResource(R.string.genre_family),
            9648 to stringResource(R.string.genre_mystery),
            10765 to stringResource(R.string.genre_science_fiction),
        )
    } else {
        listOf(
            28 to stringResource(R.string.genre_action),
            12 to stringResource(R.string.genre_adventure),
            16 to stringResource(R.string.genre_animation),
            35 to stringResource(R.string.genre_comedy),
            80 to stringResource(R.string.genre_crime),
            99 to stringResource(R.string.genre_documentary),
            18 to stringResource(R.string.genre_drama),
            14 to stringResource(R.string.genre_fantasy),
            27 to stringResource(R.string.genre_horror),
            10749 to stringResource(R.string.genre_romance),
            878 to stringResource(R.string.genre_science_fiction),
            53 to stringResource(R.string.genre_thriller),
        )
    }
    val years = listOf<Int?>(null) + (Year.now().value downTo Year.now().value - 8).toList()
    val ratings = listOf<Double?>(null, 6.0, 7.0, 8.0)
    val sorts = listOf(
        "popularity.desc" to stringResource(R.string.sort_popularity),
        "primary_release_date.desc" to stringResource(R.string.sort_release_date),
        "vote_average.desc" to stringResource(R.string.sort_rating),
    )
    var selectedGenres by remember { mutableStateOf(emptySet<Int>()) }
    var year by remember { mutableStateOf<Int?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var sortBy by remember { mutableStateOf("popularity.desc") }
    fun filters() = DiscoverMovieFilters(
        mediaType = mediaType,
        genreIds = selectedGenres,
        releaseYear = year,
        minimumRating = rating,
        sortBy = sortBy,
    )

    LaunchedEffect(Unit) { onApply(filters()) }
    AdaptiveBackground(artworkUrl = results.firstOrNull()?.backdropUrl ?: results.firstOrNull()?.posterUrl) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassBackButton(onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    PageTitle(stringResource(R.string.content_filters), Modifier.weight(1f))
                }
            }
            item { FilterSection(stringResource(R.string.media_type)) {
                FilterPill(stringResource(R.string.movies), mediaType == MediaType.MOVIE) {
                    mediaType = MediaType.MOVIE
                    selectedGenres = emptySet()
                }
                FilterPill(stringResource(R.string.tv_shows), mediaType == MediaType.TV) {
                    mediaType = MediaType.TV
                    selectedGenres = emptySet()
                }
            } }
            item { FilterSection(stringResource(R.string.genres)) {
                genres.forEach { (id, label) ->
                    FilterPill(label, id in selectedGenres) {
                        selectedGenres = if (id in selectedGenres) selectedGenres - id else selectedGenres + id
                    }
                }
            } }
            item { FilterSection(stringResource(R.string.release_year)) {
                years.forEach { value -> FilterPill(value?.toString() ?: stringResource(R.string.any), year == value) { year = value } }
            } }
            item { FilterSection(stringResource(R.string.minimum_rating)) {
                ratings.forEach { value -> FilterPill(value?.let { "$it+" } ?: stringResource(R.string.any), rating == value) { rating = value } }
            } }
            item { FilterSection(stringResource(R.string.order_by)) {
                sorts.forEach { (value, label) -> FilterPill(label, sortBy == value) { sortBy = value } }
            } }
            item {
                PrimaryAction(
                    stringResource(R.string.apply_filters),
                    Icons.Filled.Tune,
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) { onApply(filters()) }
            }
            if (loading) item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(30.dp))
                }
            }
            if (!loading && results.isEmpty()) item {
                Text(stringResource(R.string.no_filter_results), color = TextMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
            }
            items(results.chunked(3), key = { row -> row.joinToString("|") { it.stableKey } }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { media ->
                        BoxWithConstraints(Modifier.weight(1f)) {
                            MediaPoster(media, width = maxWidth, onStatus = { onStatus(media, it) }, onClick = { onMedia(media) })
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val hapticClick = rememberLightHapticAction(onClick)
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).glass(RoundedCornerShape(999.dp))
            .background(if (selected) Accent.copy(alpha = .30f) else Color.Transparent)
            .clickable(onClick = hapticClick).padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = AccentLight, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (selected) TextPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
