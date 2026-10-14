package com.cinetrack.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.EpisodeCard
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.releaseDateTime
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.cinetrack.ui.theme.Violet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * Calendar and History share one timeline language: the same day headers
 * ("Today", "Yesterday", "Friday 17", then the date), glass cards, and a
 * poster on the left. Calendar looks ahead with a week strip; History looks
 * back with each day's total and binge sessions folded into one card.
 */

private val CardShape = RoundedCornerShape(com.cinetrack.ui.theme.Radius.Medium)

internal fun timelineDate(raw: String): LocalDate? = releaseDateTime(raw, ZoneId.systemDefault())?.toLocalDate()

private fun timelineClock(raw: String): String =
    releaseDateTime(raw, ZoneId.systemDefault())?.format(com.cinetrack.ui.UiDateFormatters.current.time).orEmpty()

/** "Today", "Yesterday", "Tomorrow", the weekday within six days, otherwise the date. */
@Composable
internal fun relativeDayLabel(date: LocalDate, today: LocalDate): String {
    val locale = com.cinetrack.ui.currentLocale()
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> stringResource(R.string.day_today)
        days == -1L -> stringResource(R.string.day_yesterday)
        days == 1L -> stringResource(R.string.day_tomorrow)
        days in -6L..6L -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d", locale)).replaceFirstChar { it.titlecase(locale) }
        else -> date.format(com.cinetrack.ui.UiDateFormatters.current.date)
    }
}

private fun episodeCode(season: Int?, number: Int?): String? =
    if (season != null && number != null) "S%02dE%02d".format(season, number) else null

private fun TimelineCard.toEpisode(): EpisodeCard? {
    val season = season ?: return null
    val number = episodeNumber ?: return null
    if (media.type != MediaType.TV) return null
    return EpisodeCard(
        id = episodeId ?: -1,
        showId = media.id,
        season = season,
        number = number,
        title = episodeTitle ?: episodeLabel?.substringAfter(" · ", "").orEmpty(),
        overview = "",
        airDate = timestamp,
    )
}

private fun open(item: TimelineCard, onMedia: (MediaCard) -> Unit, onEpisode: (EpisodeCard) -> Unit) {
    item.toEpisode()?.let(onEpisode) ?: onMedia(item.media)
}

@Composable
private fun PosterThumb(media: MediaCard, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(width).height(height).clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Small))
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = .5f), Info.copy(alpha = .32f)))),
    ) {
        if (!media.posterUrl.isNullOrBlank()) AsyncImage(media.posterUrl, media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun DayHeader(title: String, detail: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.xl + 4.dp, end = Spacing.xl + 4.dp, top = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (!detail.isNullOrBlank()) Text(detail, color = TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- Calendar

internal class CalendarDay(val date: LocalDate, val items: List<TimelineCard>)

internal fun calendarDays(items: List<TimelineCard>): List<CalendarDay> =
    items.mapNotNull { item -> timelineDate(item.timestamp)?.let { it to item } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (date, dayItems) -> CalendarDay(date, dayItems) }

/**
 * Calendar: a week strip (dots on days with releases; a tap jumps there),
 * then the releases by day. The next release gets a large card with its
 * art; the rest are compact rows. Each has a bell for its release reminder.
 */
internal fun LazyListScope.calendarTimeline(
    days: List<CalendarDay>,
    today: LocalDate,
    listState: LazyListState,
    scope: CoroutineScope,
    reminders: Set<String>,
    onReminder: (TimelineCard, Boolean) -> Unit,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
) {
    // Item indices: 0 is the strip, then each day's header and its rows.
    val headerIndex = HashMap<LocalDate, Int>()
    var index = 1
    days.forEach { day -> headerIndex[day.date] = index; index += 1 + day.items.size }
    item(key = "calendar:week") {
        WeekStrip(today, days.map(CalendarDay::date).toSet()) { picked ->
            val target = headerIndex[picked] ?: headerIndex.entries.filter { it.key >= picked }.minByOrNull { it.key }?.value
            if (target != null) scope.launch { listState.animateScrollToItem(target) }
        }
    }
    var featured = false
    days.forEach { day ->
        item(key = "calendar:day:${day.date}") {
            DayHeader(relativeDayLabel(day.date, today), pluralStringResource(R.plurals.releases_count, day.items.size, day.items.size))
        }
        day.items.forEachIndexed { position, item ->
            val big = !featured && !day.date.isBefore(today)
            if (big) featured = true
            item(key = "calendar:${item.media.stableKey}:${item.timestamp}:${item.season}:${item.episodeNumber}:$position") {
                val key = com.cinetrack.data.sync.ReleaseNotifier.reminderKey(item.media.stableKey, item.season, item.episodeNumber, item.timestamp)
                val reminderOn = key in reminders
                if (big) FeaturedRelease(item, reminderOn, { onReminder(item, !reminderOn) }) { open(item, onMedia, onEpisode) }
                else CompactRelease(item, reminderOn, { onReminder(item, !reminderOn) }) { open(item, onMedia, onEpisode) }
            }
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, withReleases: Set<LocalDate>, onPick: (LocalDate) -> Unit) {
    var selected by remember(today) { mutableStateOf(today) }
    val locale = com.cinetrack.ui.currentLocale()
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0L until 7L).map(today::plusDays).forEach { date ->
            val chosen = date == selected
            val fill by androidx.compose.animation.animateColorAsState(if (chosen) Accent else Color.Transparent, label = "weekDay")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(fill)
                    .clickable(role = Role.Tab) { selected = date; onPick(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.format(java.time.format.DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale).take(3),
                    color = if (chosen) Color.White.copy(alpha = .85f) else TextMuted,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                Text(
                    date.dayOfMonth.toString(), color = if (chosen) Color.White else TextPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (date in withReleases) (if (chosen) Color.White else Info) else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun ReminderBell(on: Boolean, onToggle: () -> Unit, onArt: Boolean = false) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Switch, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).then(if (onArt) Modifier.glass(CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (on) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                stringResource(if (on) R.string.reminder_on else R.string.reminder_off),
                tint = if (on) AccentLight else if (onArt) Color.White else TextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun releaseTitle(item: TimelineCard): String =
    listOfNotNull(episodeCode(item.season, item.episodeNumber), item.episodeTitle?.takeIf(String::isNotBlank)).joinToString(" · ")
        .ifBlank { item.episodeLabel ?: item.media.year }

@Composable
private fun FeaturedRelease(item: TimelineCard, reminderOn: Boolean, onReminder: () -> Unit, onOpen: () -> Unit) {
    val art = item.media.backdropUrl ?: item.media.posterUrl
    Box(
        Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.md).fillMaxWidth().aspectRatio(16f / 8.5f)
            .clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Large))
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = .55f), Info.copy(alpha = .25f))))
            .clickable(onClick = onOpen),
    ) {
        if (!art.isNullOrBlank()) AsyncImage(art, item.media.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, 1f to Color.Black.copy(alpha = .82f))))
        val clock = if (com.cinetrack.domain.hasExplicitReleaseTime(item.timestamp)) timelineClock(item.timestamp) else ""
        if (clock.isNotBlank()) Text(
            clock, color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(10.dp).glass(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Compact)).padding(horizontal = 9.dp, vertical = 4.dp),
        )
        Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) { ReminderBell(reminderOn, onReminder, onArt = true) }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(item.media.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(releaseTitle(item), color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactRelease(item: TimelineCard, reminderOn: Boolean, onReminder: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.sm).fillMaxWidth()
            .glass(CardShape).clip(CardShape).clickable(onClick = onOpen)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterThumb(item.media, 38.dp, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.media.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val clock = if (com.cinetrack.domain.hasExplicitReleaseTime(item.timestamp)) timelineClock(item.timestamp) else ""
            Text(
                listOf(releaseTitle(item), clock).filter(String::isNotBlank).joinToString(" · "),
                color = TextMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ReminderBell(reminderOn, onReminder)
    }
}

// ---------------------------------------------------------------- History

internal sealed class HistoryEntry {
    class Day(val date: LocalDate, val plays: Int, val minutes: Int) : HistoryEntry()

    /** Plays of one title in a row on one day (one entry for a movie). */
    class Session(val events: List<TimelineCard>, val rewatch: Boolean, val seasonFinished: Boolean) : HistoryEntry() {
        val media: MediaCard get() = events.first().media
        val key: String get() = "${media.stableKey}:${events.first().timestamp}:${events.last().timestamp}"
    }
}

private fun minutesOf(event: TimelineCard): Int = event.media.runtimeMinutes ?: if (event.media.type == MediaType.TV) 45 else 0

/** History is newest first; consecutive plays of one show on one day form a session. */
internal fun historyEntries(history: List<TimelineCard>): List<HistoryEntry> {
    val dated = history.mapNotNull { event -> timelineDate(event.timestamp)?.let { it to event } }
    fun playKey(event: TimelineCard) = "${event.media.stableKey}:${event.season}:${event.episodeNumber}"
    // A play is a rewatch when an older play of the same episode (or movie) exists.
    val remaining = dated.groupingBy { playKey(it.second) }.eachCount().toMutableMap()
    val rewatched = HashSet<TimelineCard>()
    dated.forEach { (_, event) ->
        val key = playKey(event)
        val left = (remaining[key] ?: 1) - 1
        remaining[key] = left
        if (left > 0) rewatched += event
    }
    val sessions = mutableListOf<Pair<LocalDate, MutableList<TimelineCard>>>()
    dated.forEach { (date, event) ->
        val last = sessions.lastOrNull()
        val joins = last != null && last.first == date && event.media.type == MediaType.TV &&
            last.second.first().media.type == MediaType.TV && last.second.first().media.stableKey == event.media.stableKey
        if (joins) last!!.second += event else sessions += date to mutableListOf(event)
    }
    val entries = mutableListOf<HistoryEntry>()
    sessions.groupBy({ it.first }, { it.second }).forEach { (date, daySessions) ->
        val events = daySessions.flatten()
        entries += HistoryEntry.Day(date, events.size, events.sumOf(::minutesOf))
        daySessions.forEach { events ->
            // Newest first in the list; read the session oldest to newest.
            val ordered = events.reversed()
            val media = ordered.first().media
            val lastEpisode = ordered.last()
            val seasonLength = media.seasons.firstOrNull { it.number == lastEpisode.season }?.episodeCount ?: 0
            val finished = media.type == MediaType.TV && seasonLength > 0 && lastEpisode.episodeNumber == seasonLength
            entries += HistoryEntry.Session(ordered, rewatch = ordered.any { it in rewatched }, seasonFinished = finished)
        }
    }
    return entries
}

internal fun LazyListScope.historyTimeline(
    entries: List<HistoryEntry>,
    today: LocalDate,
    onMedia: (MediaCard) -> Unit,
    onEpisode: (EpisodeCard) -> Unit,
) {
    entries.forEachIndexed { index, entry ->
        when (entry) {
            is HistoryEntry.Day -> item(key = "history:day:${entry.date}") {
                DayHeader(
                    relativeDayLabel(entry.date, today),
                    listOf(
                        pluralStringResource(R.plurals.plays_count, entry.plays, entry.plays),
                        formatWatchedDurationMinutes(entry.minutes),
                    ).filter(String::isNotBlank).joinToString(" · "),
                )
            }
            is HistoryEntry.Session -> item(key = "history:${entry.key}:$index") { HistorySession(entry, onMedia, onEpisode) }
        }
    }
}

@Composable
private fun Milestone(text: String, color: Color) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(com.cinetrack.ui.theme.Radius.Pill)).background(color.copy(alpha = .16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun HistorySession(session: HistoryEntry.Session, onMedia: (MediaCard) -> Unit, onEpisode: (EpisodeCard) -> Unit) {
    val events = session.events
    val first = events.first()
    val last = events.last()
    val several = events.size > 1
    var expanded by rememberSaveable(session.key) { mutableStateOf(false) }
    val isMovie = first.media.type == MediaType.MOVIE
    Column(
        Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.sm).fillMaxWidth()
            .glass(CardShape).clip(CardShape).animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { if (several) expanded = !expanded else open(first, onMedia, onEpisode) }
                .padding(start = 9.dp, top = 9.dp, bottom = 9.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterThumb(first.media, 46.dp, 68.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val start = timelineClock(first.timestamp)
                val end = timelineClock(last.timestamp)
                val time = if (several && start != end) "$start – $end" else end
                Text(
                    listOfNotNull(time.takeIf(String::isNotBlank), stringResource(R.string.movie_label).takeIf { isMovie }).joinToString(" · "),
                    color = TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                )
                Text(first.media.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = when {
                    isMovie -> listOf(first.media.year, formatDurationMinutes(first.media.runtimeMinutes)).filter(String::isNotBlank).joinToString(" · ")
                    several -> {
                        val from = episodeCode(first.season, first.episodeNumber)
                        val to = if (first.season == last.season) last.episodeNumber?.let { "E%02d".format(it) } else episodeCode(last.season, last.episodeNumber)
                        listOfNotNull(listOfNotNull(from, to).joinToString(" – ").takeIf(String::isNotBlank), pluralStringResource(R.plurals.episodes_count, events.size, events.size)).joinToString(" · ")
                    }
                    else -> releaseTitle(first)
                }
                Text(detail, color = TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (session.seasonFinished || session.rewatch) {
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (session.seasonFinished) Milestone(stringResource(R.string.milestone_season_finished), com.cinetrack.ui.theme.SurfacePalette.PosterMint)
                        if (session.rewatch) Milestone(stringResource(R.string.milestone_rewatch), Violet)
                    }
                }
            }
            if (several) Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextMuted, modifier = Modifier.size(20.dp))
            else Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(22.dp))
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(start = 67.dp, end = 12.dp, bottom = 6.dp)) {
                events.forEach { event ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { open(event, onMedia, onEpisode) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(releaseTitle(event), color = TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(timelineClock(event.timestamp), color = TextMuted, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
