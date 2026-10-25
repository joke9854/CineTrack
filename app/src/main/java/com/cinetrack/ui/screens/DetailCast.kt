@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import com.cinetrack.ui.components.softTab
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.PersonCard
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.blueEdgeClickable
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.cinetrack.ui.theme.GlassBare
import com.cinetrack.ui.theme.LocalCardAppearance
import com.cinetrack.ui.theme.LocalDetailAccent
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing

/** On detail pages the accent follows the poster (see [LocalDetailAccent]). */
private val Accent: Color
    @Composable get() = LocalDetailAccent.current?.value ?: com.cinetrack.ui.theme.Accent
private val AccentLight: Color
    @Composable get() = LocalDetailAccent.current?.value?.let { androidx.compose.ui.graphics.lerp(it, Color.White, .32f) }
        ?: com.cinetrack.ui.theme.AccentLight

@Composable
internal fun CastAvatar(person: PersonCard, onPerson: (PersonCard) -> Unit, showCharacter: Boolean = false) {
    // A character without a TVDB picture keeps the actor photo.
    val photo = person.characterImageUrl?.takeIf { showCharacter && it.isNotBlank() } ?: person.profileUrl
    Column(
        Modifier.width(DetailLayout.CastWidth).clip(RoundedCornerShape(Radius.Medium))
            .clickable { onPerson(person) }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Settings > Appearance picks a round photo or a 2:3 poster; names use the full card width.
        val poster = LocalCardAppearance.current.castPhotoShape == "poster"
        val shape = if (poster) RoundedCornerShape(Radius.Small) else CircleShape
        Box(
            (if (poster) Modifier.padding(horizontal = Spacing.xs).fillMaxWidth().aspectRatio(2f / 3f) else Modifier.size(DetailLayout.CastPhoto))
                .clip(shape).background(GlassBare)
                .border(.6.dp, Color.White.copy(alpha = .12f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (!photo.isNullOrBlank()) {
                androidx.compose.animation.Crossfade(photo, label = "castPhoto") { url ->
                    AsyncImage(url, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            } else {
                Icon(Icons.Filled.Person, null, tint = TextSecondary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // Characters mode leads with the character, then the actor.
        // Character names only, never a crew job such as Executive Producer.
        val characterName = person.character.ifBlank { person.role }
        val (primary, secondary) = if (showCharacter && characterName.isNotBlank()) characterName to person.name else person.name to person.role
        // One line each keeps every name and role aligned across the row.
        Text(primary, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(secondary, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun CastSection(
    people: List<PersonCard>,
    showCharacters: Boolean = false,
    onShowCharacters: (Boolean) -> Unit = {},
    onViewAll: () -> Unit,
    onPerson: (PersonCard) -> Unit,
) {
    // The Characters / Cast switch appears only when Floppy found character pictures.
    val hasCharacters = people.any { !it.characterImageUrl.isNullOrBlank() }
    Column {
        if (hasCharacters) {
            CastModeHeader(showCharacters, onShowCharacters, onViewAll, Modifier.padding(horizontal = DetailLayout.Gutter))
        } else {
            SectionHeader(stringResource(R.string.cast_title), Modifier.padding(horizontal = DetailLayout.Gutter), stringResource(R.string.see_all), onViewAll)
        }
        Spacer(Modifier.height(Spacing.md))
        LazyRow(contentPadding = PaddingValues(horizontal = DetailLayout.Gutter - 4.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Directors, producers and other crew appear only under Cast.
            val shown = if (showCharacters && hasCharacters) people.filter(PersonCard::isCastMember) else people
            items(shown.take(18), key = PersonCard::id) { person ->
                CastAvatar(person, onPerson, showCharacter = showCharacters && hasCharacters)
            }
        }
    }
}

/** Characters / Cast headlines that switch like the Film / Serie header. */
@Composable
private fun CastModeHeader(showCharacters: Boolean, onShowCharacters: (Boolean) -> Unit, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CastModeHeadline(stringResource(R.string.characters_title), showCharacters) { onShowCharacters(true) }
        Spacer(Modifier.width(14.dp))
        CastModeHeadline(stringResource(R.string.cast_title), !showCharacters) { onShowCharacters(false) }
        Spacer(Modifier.weight(1f))
        val viewAll = rememberUiAction(onViewAll)
        Row(
            Modifier.clip(RoundedCornerShape(Radius.Pill)).clickable(onClick = viewAll).padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.see_all), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun CastModeHeadline(text: String, selected: Boolean, onClick: () -> Unit) {
    val color by androidx.compose.animation.animateColorAsState(
        if (selected) TextPrimary else TextPrimary.copy(alpha = .42f),
        androidx.compose.animation.core.tween(Motion.Extended),
        label = "castModeHeadline",
    )
    Text(
        text,
        color = color,
        lineHeight = 21.sp,
        fontWeight = FontWeight.ExtraBold,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .62f), androidx.compose.ui.geometry.Offset(0f, 2.5f), 6f),
        ),
        modifier = Modifier.softTab(selected, onClick),
    )
}

@Composable
internal fun FullCastSheet(people: List<PersonCard>, showCharacters: Boolean = false, onDismiss: () -> Unit, onPerson: (PersonCard) -> Unit) {
    val hasCharacters = people.any { !it.characterImageUrl.isNullOrBlank() }
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter)) {
            SectionHeader(stringResource(R.string.full_cast), Modifier.padding(horizontal = DetailLayout.Inner))
            Spacer(Modifier.height(Spacing.md))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(DetailLayout.CastWidth),
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                gridItems(if (showCharacters && hasCharacters) people.filter(PersonCard::isCastMember) else people, key = PersonCard::id) { person ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        CastAvatar(person, onPerson, showCharacter = showCharacters && hasCharacters)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActorSheet(person: PersonCard, detailViewModel: com.cinetrack.ui.detail.DetailViewModel, onDismiss: () -> Unit, onMedia: (MediaCard) -> Unit) {
    var details by remember(person.id) { mutableStateOf(person) }
    var biographyExpanded by remember(person.id) { mutableStateOf(false) }
    var biographyOverflowing by remember(person.id) { mutableStateOf(false) }
    LaunchedEffect(person.id) { details = detailViewModel.loadPerson(person) }
    SharedGlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.72f).padding(horizontal = Spacing.lg)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(80.dp).height(100.dp).clip(RoundedCornerShape(Radius.Medium)).background(GlassBare)) {
                    if (!details.profileUrl.isNullOrBlank()) AsyncImage(details.profileUrl, details.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(details.name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(details.role, color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    val age = details.age()
                    val died = formatFullDate(details.deathday).takeIf(String::isNotBlank)
                    val dates = listOfNotNull(formatFullDate(details.birthday).takeIf(String::isNotBlank), died).joinToString(" – ")
                    val ageLabel = age?.let { stringResource(if (died != null) R.string.age_at_death else R.string.years_old, it) }
                    val born = listOfNotNull(dates.takeIf(String::isNotBlank), ageLabel).joinToString(" · ")
                    if (born.isNotBlank()) Text(born, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    details.placeOfBirth?.let { Text(it, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }
            if (details.biography.isNotBlank()) {
                Spacer(Modifier.height(13.dp))
                Text(
                    details.biography,
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                    maxLines = if (biographyExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (!biographyExpanded) biographyOverflowing = result.hasVisualOverflow },
                )
                if (biographyOverflowing || biographyExpanded) {
                    Text(
                        if (biographyExpanded) stringResource(R.string.collapse) else stringResource(R.string.see_all),
                        color = AccentLight,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = Spacing.xs).clickable { biographyExpanded = !biographyExpanded },
                    )
                }
            }
            if (details.movieCredits.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg)); SectionHeader(stringResource(R.string.recent_credits))
                Spacer(Modifier.height(Spacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    details.movieCredits.forEach { movie ->
                        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Small))
                            .blueEdgeClickable(RoundedCornerShape(Radius.Small)) { onMedia(movie); onDismiss() }.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(42.dp).height(60.dp).clip(RoundedCornerShape(Radius.Compact)).background(Accent.copy(alpha = .18f))) {
                                if (!movie.posterUrl.isNullOrBlank()) AsyncImage(movie.posterUrl, movie.title, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(movie.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(movie.year, color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
