@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.data.repository.ProfileImageKind
import com.cinetrack.data.repository.ProfileImages
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RailIds
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.GlassIconButton
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.personal.LocalPersonal
import com.cinetrack.ui.settings.SettingsViewModel
import com.cinetrack.ui.settings.recentPosters
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import java.io.File
import java.text.NumberFormat

/** The name shown on the Profile card: yours, else the tracker account's, else "You". */
@Composable
internal fun profileDisplayName(saved: String, simklUsername: String?, floppyUsername: String?): String =
    saved.trim().ifBlank { simklUsername?.takeIf(String::isNotBlank) ?: floppyUsername?.takeIf(String::isNotBlank) ?: stringResource(R.string.profile_default_name) }

/** Opaque, so the avatar's ring reads as a cut-out of the card. */
private val ProfileCardColor = SurfacePalette.GlassSurface

/** Your photo, else your initial on the accent; [ring] draws a border in that colour around it. */
@Composable
private fun ProfileAvatar(name: String, size: Dp, photo: String?, ring: Color? = null) {
    val inner = @Composable {
        Box(
            Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, AccentLight.copy(alpha = .55f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(File(photo), stringResource(R.string.profile_photo), contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            } else {
                Text(
                    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = TextPrimary,
                    style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
    if (ring == null) inner() else Box(Modifier.size(size + 6.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) { inner() }
}

/** Your banner image, else the recent posters side by side; faded towards [fadeTo] at the bottom. */
@Composable
private fun ProfileBanner(banner: String?, posters: List<String>, fadeTo: Color, modifier: Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Accent.copy(alpha = .45f), SurfacePalette.OceanMid)))) {
        if (banner != null) {
            AsyncImage(File(banner), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else if (posters.isNotEmpty()) {
            Row(Modifier.matchParentSize()) {
                posters.forEach { poster ->
                    AsyncImage(poster, null, contentScale = ContentScale.Crop, alpha = .72f, modifier = Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .10f), .55f to fadeTo.copy(alpha = .45f), 1f to fadeTo)))
    }
}

/** Connected accounts by name ("Simkl · Floppy"), or with the account name on the Profile page. */
@Composable
private fun accountsLine(simklConnected: Boolean, simklUsername: String?, floppyConnected: Boolean, floppyUsername: String?, withNames: Boolean): String =
    listOfNotNull(
        "Simkl".takeIf { simklConnected }?.let { name -> simklUsername?.takeIf { withNames && it.isNotBlank() }?.let { "$name: $it" } ?: name },
        "Floppy".takeIf { floppyConnected }?.let { name -> floppyUsername?.takeIf { withNames && it.isNotBlank() }?.let { "$name: $it" } ?: name },
    ).joinToString(" · ").ifBlank { stringResource(R.string.profile_card_hint) }

/** The card at the top of Settings, design A "poster banner": banner, avatar, name, accounts, one line of numbers. */
@Composable
internal fun ProfileCard(state: com.cinetrack.ui.settings.SettingsUiState, name: String, images: ProfileImages, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val format = NumberFormat.getIntegerInstance(com.cinetrack.ui.currentLocale())
    val yearMinutes = rememberYearMinutes(state.history, state.library, state.metadataTimezone)
    val numbers = listOfNotNull(
        pluralStringResource(R.plurals.profile_titles_count, state.libraryTitles, format.format(state.libraryTitles)),
        pluralStringResource(R.plurals.profile_episodes_count, state.episodesWatched, format.format(state.episodesWatched)),
        yearMinutes?.let { stringResource(R.string.profile_hours_this_year, format.format(it / 60)) },
    ).joinToString(" · ")
    val bannerHeight = 78.dp
    val avatarSize = 56.dp
    Box(
        Modifier.fillMaxWidth().clip(shape).background(ProfileCardColor)
            .border(.6.dp, Color.White.copy(alpha = .10f), shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column {
            ProfileBanner(images.banner, state.recentPosters, ProfileCardColor, Modifier.fillMaxWidth().height(bannerHeight))
            Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = avatarSize / 2 + Spacing.sm, bottom = Spacing.lg)) {
                Text(name, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    accountsLine(state.simklConnected, state.simklUsername, state.floppyConnected, state.floppyUsername, withNames = false),
                    color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(numbers, color = TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Spacing.md))
            }
        }
        Box(Modifier.padding(start = Spacing.lg - 3.dp, top = bannerHeight - avatarSize / 2 - 3.dp)) {
            ProfileAvatar(name, avatarSize, images.avatar, ring = ProfileCardColor)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.align(Alignment.TopEnd).padding(top = bannerHeight + Spacing.md, end = Spacing.md))
    }
}

/** Profile, layout 1 "one scrolling page": banner, you, numbers, statistics, your things, accounts. */
@Composable
internal fun ProfileScreen(
    state: AppUiState,
    progressState: com.cinetrack.ui.progress.ProgressUiState,
    viewingInsights: com.cinetrack.domain.ViewingPeopleInsights,
    onLoadViewingInsights: () -> Unit,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPage: (String) -> Unit,
    onLists: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onLoadViewingInsights() }
    val saved by settingsViewModel.profileName.collectAsStateWithLifecycle()
    val images by settingsViewModel.profileImages.collectAsStateWithLifecycle()
    val name = profileDisplayName(saved, state.simklUsername, state.floppyUsername)
    var editing by rememberSaveable { mutableStateOf(false) }
    val posters = remember(state.history, state.rails) { state.recentPosters() }
    com.cinetrack.ui.components.AdaptiveBackground {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().readableWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
        ) {
            item { ProfileHeader(state, name, images, posters, onBack) { editing = true } }
            item { ProfileNumbers(state) }
            item {
                Text(
                    stringResource(R.string.statistics), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg, bottom = Spacing.xs),
                )
            }
            item { StatisticsSection(progressState, viewingInsights) }
            item { ProfileYoursAndAccounts(state, onPage, onLists) }
        }
    }
    if (editing) ProfileEditSheet(state, saved, images, posters, settingsViewModel) { editing = false }
}

@Composable
private fun ProfileHeader(state: AppUiState, name: String, images: ProfileImages, posters: List<String>, onBack: () -> Unit, onEdit: () -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bannerHeight = top + 150.dp
    val avatarSize = 76.dp
    Box(Modifier.fillMaxWidth()) {
        ProfileBanner(images.banner, posters, SurfacePalette.BottomFade, Modifier.fillMaxWidth().height(bannerHeight))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            GlassIconButton(Icons.Filled.Edit, stringResource(R.string.profile_edit), onEdit)
        }
        Column(Modifier.fillMaxWidth().padding(top = bannerHeight - avatarSize / 2), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(name, avatarSize, images.avatar, ring = SurfacePalette.BottomFade)
            Spacer(Modifier.height(Spacing.sm))
            Text(name, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = Spacing.xl))
            Text(
                accountsLine(state.simklConnected, state.simklUsername, state.floppyConnected, state.floppyUsername, withNames = true),
                color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
        }
    }
}

/** Library, plays and personal counts for the Profile page. */
internal data class ProfileStats(val titles: Int, val movies: Int, val episodes: Int)

internal fun AppUiState.profileStats(): ProfileStats = ProfileStats(
    titles = rails[RailIds.LIBRARY].orEmpty().size,
    movies = history.asSequence().filter { it.media.type == MediaType.MOVIE }.map { it.media.id }.distinct().count(),
    episodes = history.count { it.season != null },
)

/** Four numbers in one dark card: titles, movies, episodes, rated. */
@Composable
private fun ProfileNumbers(state: AppUiState) {
    val personal = LocalPersonal.current
    val ratings = personal?.ratings?.collectAsStateWithLifecycle()?.value.orEmpty()
    val stats = remember(state.history, state.rails) { state.profileStats() }
    val format = NumberFormat.getIntegerInstance(com.cinetrack.ui.currentLocale())
    val shape = RoundedCornerShape(Radius.Medium)
    Row(
        Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg).fillMaxWidth().clip(shape)
            .background(Color.Black.copy(alpha = .38f)).border(.6.dp, Color.White.copy(alpha = .08f), shape)
            .padding(vertical = Spacing.md),
    ) {
        listOf(
            stats.titles to R.string.profile_stat_titles,
            stats.movies to R.string.profile_stat_movies,
            stats.episodes to R.string.profile_stat_episodes,
            ratings.values.count { it.score != null } to R.string.profile_stat_rated,
        ).forEach { (value, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(format.format(value), color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(stringResource(label), color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProfileYoursAndAccounts(state: AppUiState, onPage: (String) -> Unit, onLists: () -> Unit) {
    val personal = LocalPersonal.current
    val ratings = personal?.ratings?.collectAsStateWithLifecycle()?.value.orEmpty()
    val lists = personal?.lists?.collectAsStateWithLifecycle()?.value.orEmpty()
    val format = NumberFormat.getIntegerInstance(com.cinetrack.ui.currentLocale())
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SettingsSection(stringResource(R.string.profile_yours)) {
            KitRow(Icons.Filled.Star, stringResource(R.string.profile_ratings_notes), value = format.format(ratings.values.count { it.score != null || it.note.isNotBlank() }))
            KitRow(Icons.Filled.CollectionsBookmark, stringResource(R.string.your_lists), value = format.format(lists.size), onClick = onLists)
        }
        SettingsSection(stringResource(R.string.profile_accounts)) {
            KitRow(Icons.Filled.Tv, "Simkl", value = stringResource(if (state.simklConnected) R.string.connected else R.string.not_connected), onClick = { onPage(SettingsPages.ServiceSimkl) })
            KitRow(Icons.Filled.Movie, "Floppy", value = stringResource(if (state.floppyConnected) R.string.connected else R.string.not_connected), onClick = { onPage(SettingsPages.ServiceFloppy) })
        }
    }
}

/** Name, profile photo and banner. Images come from the photo picker (no storage permission). */
@Composable
private fun ProfileEditSheet(state: AppUiState, saved: String, images: ProfileImages, posters: List<String>, settingsViewModel: SettingsViewModel, onDismiss: () -> Unit) {
    var draft by rememberSaveable(saved) { mutableStateOf(saved) }
    var failed by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val onResult: (Boolean) -> Unit = { ok -> failed = !ok }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) settingsViewModel.setProfileImage(ProfileImageKind.AVATAR, uri, onResult)
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) settingsViewModel.setProfileImage(ProfileImageKind.BANNER, uri, onResult)
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val name = profileDisplayName(draft, state.simklUsername, state.floppyUsername)
    SharedGlassSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.profile_edit), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = Spacing.xl))
            SettingsSection(stringResource(R.string.profile_name_section)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 40) draft = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    placeholder = { Text(profileDisplayName("", state.simklUsername, state.floppyUsername), color = TextMuted) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { settingsViewModel.setProfileName(draft); focus.clearFocus() }),
                    colors = credentialFieldColors(),
                )
                Text(stringResource(R.string.profile_name_note), color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
                if (draft.trim() != saved.trim()) {
                    KitPrimaryButton(stringResource(R.string.save), Icons.Filled.Save, Accent) { settingsViewModel.setProfileName(draft); focus.clearFocus() }
                }
            }
            SettingsSection(stringResource(R.string.profile_photo)) {
                Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(name, 56.dp, images.avatar)
                    Spacer(Modifier.width(Spacing.md))
                    Text(stringResource(R.string.profile_photo_note), color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                KitRow(Icons.Filled.PhotoLibrary, stringResource(R.string.profile_choose_photo), onClick = { pickAvatar.launch(imageOnly) })
                if (images.avatar != null) {
                    KitRow(Icons.Filled.Delete, stringResource(R.string.profile_remove_photo), onClick = { settingsViewModel.setProfileImage(ProfileImageKind.AVATAR, null, onResult) })
                }
            }
            SettingsSection(stringResource(R.string.profile_banner)) {
                ProfileBanner(images.banner, posters, ProfileCardColor, Modifier.padding(Spacing.md).fillMaxWidth().height(78.dp).clip(RoundedCornerShape(Radius.Medium)))
                KitRow(Icons.Filled.Image, stringResource(R.string.profile_choose_banner), onClick = { pickBanner.launch(imageOnly) })
                if (images.banner != null) {
                    KitRow(Icons.Filled.CollectionsBookmark, stringResource(R.string.profile_use_recent_posters), onClick = { settingsViewModel.setProfileImage(ProfileImageKind.BANNER, null, onResult) })
                }
            }
            if (failed) {
                Text(stringResource(R.string.profile_image_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.xl))
            }
        }
    }
}
