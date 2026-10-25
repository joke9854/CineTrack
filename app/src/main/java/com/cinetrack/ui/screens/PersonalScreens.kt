@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.data.personal.CustomList
import com.cinetrack.domain.MediaCard
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.ConfirmPrompt
import com.cinetrack.ui.components.EmptyState
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.GlassIconButton
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.PageTitle
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.SheetTitle
import com.cinetrack.ui.components.liquidGlassPill
import com.cinetrack.ui.components.rememberAppHaptics
import com.cinetrack.ui.personal.LocalPersonal
import com.cinetrack.ui.personal.PersonalViewModel
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** "Your rating" and "Lists" as two pills under a title's main actions. */
@Composable
internal fun PersonalRow(media: MediaCard, modifier: Modifier = Modifier) {
    val personal = LocalPersonal.current ?: return
    val ratings by personal.ratings.collectAsStateWithLifecycle()
    val memberships by personal.memberships.collectAsStateWithLifecycle()
    val rating = ratings[media.stableKey]
    val inLists = memberships[media.stableKey].orEmpty().size
    var ratingSheet by rememberSaveable { mutableStateOf(false) }
    var listsSheet by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PersonalPill(
                if (rating?.score != null) Icons.Filled.Star else Icons.Filled.StarOutline,
                rating?.score?.let { stringResource(R.string.your_rating_value, it) } ?: stringResource(R.string.rate_title),
                highlighted = rating?.score != null,
                modifier = Modifier.weight(1f),
            ) { ratingSheet = true }
            PersonalPill(
                Icons.AutoMirrored.Filled.PlaylistAdd,
                if (inLists > 0) pluralStringResource(R.plurals.in_lists, inLists, inLists) else stringResource(R.string.add_to_list),
                highlighted = inLists > 0,
                modifier = Modifier.weight(1f),
            ) { listsSheet = true }
        }
        rating?.note?.takeIf(String::isNotBlank)?.let { note ->
            Text(
                "“$note”", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(Radius.Small)).clickable { ratingSheet = true }.padding(Spacing.xs),
            )
        }
    }
    if (ratingSheet) RatingSheet(media, rating?.score, rating?.note.orEmpty(), personal) { ratingSheet = false }
    if (listsSheet) ListsSheet(media, personal) { listsSheet = false }
}

@Composable
private fun PersonalPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, highlighted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 44.dp).liquidGlassPill(under = com.cinetrack.ui.components.GlassMaterial.DetailSurface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (highlighted) AccentLight else TextSecondary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(text, color = if (highlighted) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Score 1-10 on a slider (0 = no score) and a free note. */
@Composable
internal fun RatingSheet(media: MediaCard, score: Int?, note: String, personal: PersonalViewModel, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf((score ?: 0).toFloat()) }
    var text by rememberSaveable { mutableStateOf(note) }
    val haptics = rememberAppHaptics()
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            SheetTitle(stringResource(R.string.your_rating))
            Text(media.title, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.lg))
            val current = value.roundToInt()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (current == 0) "–" else current.toString(), color = TextPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                Text(" / 10", color = TextMuted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                if (current == 0) Text(stringResource(R.string.no_score), color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            Slider(
                value = value,
                onValueChange = { next -> if (next.roundToInt() != value.roundToInt()) haptics.tick(); value = next },
                valueRange = 0f..10f,
                steps = 9,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(alpha = .12f)),
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= com.cinetrack.data.personal.PersonalRepository.NOTE_LIMIT) text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.your_note)) },
                minLines = 3,
                maxLines = 6,
                colors = credentialFieldColors(),
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryAction(stringResource(R.string.save), Icons.Filled.Star, Modifier.fillMaxWidth()) {
                haptics.confirm()
                personal.setRating(media, value.roundToInt().takeIf { it > 0 }, text)
                onDismiss()
            }
            if (score != null || note.isNotBlank()) {
                TextButton(onClick = { personal.setRating(media, null, ""); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.remove_rating), color = SurfacePalette.DangerText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Ticks for every list, plus a field to make a new one with this title in it. */
@Composable
internal fun ListsSheet(media: MediaCard, personal: PersonalViewModel, onDismiss: () -> Unit) {
    val lists by personal.lists.collectAsStateWithLifecycle()
    val memberships by personal.memberships.collectAsStateWithLifecycle()
    val mine = memberships[media.stableKey].orEmpty()
    var newName by rememberSaveable { mutableStateOf("") }
    val haptics = rememberAppHaptics()
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            SheetTitle(stringResource(R.string.add_to_list))
            Spacer(Modifier.height(Spacing.sm))
            if (lists.isEmpty()) {
                Text(stringResource(R.string.lists_empty_sheet), color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(lists, key = CustomList::id) { list ->
                    val checked = list.id in mine
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small))
                            .clickable(role = Role.Checkbox) { haptics.toggle(!checked); personal.setInList(list.id, media, !checked) }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = TextMuted))
                        Spacer(Modifier.width(Spacing.md))
                        Text(list.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(list.count.toString(), color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= com.cinetrack.data.personal.PersonalRepository.NAME_LIMIT) newName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.new_list)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { personal.createList(newName, media); newName = "" }),
                    colors = credentialFieldColors(),
                )
                Spacer(Modifier.width(Spacing.sm))
                GlassIconButton(Icons.Filled.Add, stringResource(R.string.create_list), {
                    haptics.confirm()
                    personal.createList(newName, media)
                    newName = ""
                })
            }
        }
    }
}

/** Every list you made, as cards with up to three covers. */
@Composable
internal fun ListsScreen(onBack: () -> Unit, onList: (Long) -> Unit) {
    val personal = LocalPersonal.current ?: return
    val lists by personal.lists.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    AdaptiveBackground {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().readableWidth(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg, bottom = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    GlassBackButton(onClick = onBack)
                    Spacer(Modifier.size(8.dp))
                    PageTitle(stringResource(R.string.your_lists), Modifier.weight(1f))
                    GlassIconButton(Icons.Filled.Add, stringResource(R.string.create_list), { creating = true })
                }
            }
            if (lists.isEmpty()) item {
                EmptyState(
                    Icons.Filled.CollectionsBookmark,
                    stringResource(R.string.lists_empty_title),
                    body = stringResource(R.string.lists_empty_body),
                    actionLabel = stringResource(R.string.create_list),
                    onAction = { creating = true },
                )
            }
            items(lists, key = CustomList::id) { list ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xs).clip(RoundedCornerShape(Radius.Medium))
                        .clickable { onList(list.id) }.padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListCovers(list.posters)
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text(list.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.list_titles, list.count, list.count), color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (creating) ListNameSheet(stringResource(R.string.create_list), "", onDismiss = { creating = false }) { personal.createList(it); creating = false }
}

/** Three covers fanned out; an empty list shows one placeholder. */
@Composable
private fun ListCovers(posters: List<String>) {
    Box(Modifier.width(92.dp).height(80.dp)) {
        val shown = posters.take(3).ifEmpty { listOf("") }
        shown.reversed().forEachIndexed { reversedIndex, url ->
            val index = shown.size - 1 - reversedIndex
            Box(
                Modifier.offset(x = (index * 18).dp).width(54.dp).aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(Radius.Compact)).background(SurfacePalette.ArtworkBackdrop)
                    .border(.6.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(Radius.Compact)),
                contentAlignment = Alignment.Center,
            ) {
                if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.CollectionsBookmark, null, tint = TextMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** One list's titles as a poster grid, with rename and delete in the menu. */
@Composable
internal fun ListDetailScreen(listId: Long, onBack: () -> Unit, onMedia: (MediaCard) -> Unit) {
    val personal = LocalPersonal.current ?: return
    val lists by personal.lists.collectAsStateWithLifecycle()
    val list = lists.firstOrNull { it.id == listId }
    val items by remember(listId) { personal.listMedia(listId) }.collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    AdaptiveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg, bottom = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onClick = onBack)
                Spacer(Modifier.size(8.dp))
                PageTitle(list?.name.orEmpty(), Modifier.weight(1f))
                Box {
                    GlassIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_options), { menuOpen = true })
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(Radius.Medium), containerColor = SurfacePalette.MenuSurface) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_list), color = TextPrimary, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = TextSecondary) },
                            onClick = { menuOpen = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_list), color = SurfacePalette.DangerText, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = SurfacePalette.DangerText) },
                            onClick = { menuOpen = false; deleting = true },
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.list_empty_title), body = stringResource(R.string.list_empty_body))
            } else {
                LazyVerticalGrid(
                    GridCells.Adaptive(104.dp),
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    items(items, key = MediaCard::stableKey) { media ->
                        MediaPoster(media, Modifier.fillMaxWidth(), width = 104.dp, showYear = true, onClick = { onMedia(media) })
                    }
                }
            }
        }
    }
    if (renaming && list != null) ListNameSheet(stringResource(R.string.rename_list), list.name, onDismiss = { renaming = false }) { personal.renameList(listId, it); renaming = false }
    if (deleting && list != null) ConfirmPrompt(
        title = stringResource(R.string.delete_list_title, list.name),
        message = stringResource(R.string.delete_list_message),
        confirmLabel = stringResource(R.string.delete_list),
        icon = Icons.Filled.Delete,
        onDismiss = { deleting = false },
    ) {
        deleting = false
        personal.deleteList(listId)
        onBack()
    }
}

@Composable
private fun ListNameSheet(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            SheetTitle(title)
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= com.cinetrack.data.personal.PersonalRepository.NAME_LIMIT) name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.list_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name) }),
                colors = credentialFieldColors(),
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryAction(stringResource(R.string.save), Icons.Filled.CollectionsBookmark, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { onSave(name) }
        }
    }
}
