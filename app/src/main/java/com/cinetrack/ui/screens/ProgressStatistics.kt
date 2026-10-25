@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.cinetrack.ui.components.LoadingPane
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.produceState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.AppUiState
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.PersonCard
import com.cinetrack.domain.TimelineCard
import com.cinetrack.domain.releaseDateTime
import com.cinetrack.domain.ViewingPeopleInsights
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.Info
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing

@Composable
internal fun StatisticsSection(state: com.cinetrack.ui.progress.ProgressUiState, people: ViewingPeopleInsights) {
    val now = remember(state.metadataTimezone) {
        val zone = if (state.metadataTimezone == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(state.metadataTimezone) }.getOrDefault(ZoneId.systemDefault())
        LocalDate.now(zone)
    }
    // Thousands of history timestamps are parsed off the main thread, once per change;
    // re-entering the tab reuses the last summary instead of stalling the first frame.
    val inputs = StatsInputs(state.history, state.rails[com.cinetrack.domain.RailIds.LIBRARY].orEmpty(), now)
    val stats by produceState(initialValue = StatsCache.result.takeIf { StatsCache.inputs == inputs }, inputs) {
        if (value == null || StatsCache.inputs != inputs) {
            val computed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { computeStats(inputs) }
            StatsCache.inputs = inputs
            StatsCache.result = computed
            value = computed
        }
    }
    val summary = stats ?: run {
        LoadingPane(Modifier.height(320.dp))
        return
    }
    val completed = summary.completed
    val abandoned = summary.abandoned
    val finishedTotal = completed + abandoned
    val completionRate = if (finishedTotal == 0) 0 else completed * 100 / finishedTotal
    var yearSheet by rememberSaveable { mutableStateOf(false) }
    // Headline first: the year in one number, four facts, then the detail.
    Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        YearHero(summary, now) { yearSheet = true }
        StatsFacts(summary)
        ThisWeekCard(summary.week, now)
        GenreBars(summary.genreShares)
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatisticTile(stringResource(R.string.completion_rate), "$completionRate%", Modifier.weight(1f))
            StatisticTile(stringResource(R.string.completed), completed.toString(), Modifier.weight(1f))
            StatisticTile(stringResource(R.string.dropped), abandoned.toString(), Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.md)) {
            Text(stringResource(R.string.most_watched_people), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(9.dp))
            if (people.loading) {
                Text(stringResource(R.string.loading), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            } else {
                PeopleStatisticsRow(stringResource(R.string.actors), people.actors)
                Spacer(Modifier.height(10.dp))
                PeopleStatisticsRow(stringResource(R.string.directors), people.directors)
            }
        }
    }
    if (yearSheet) {
        com.cinetrack.ui.components.SharedGlassSheet(onDismiss = { yearSheet = false }) {
            Column(
                Modifier.fillMaxWidth().then(Modifier.verticalScroll(rememberScrollState())).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                YearReviewCard(summary.year)
                ActivityHeatmap(summary, now)
                ViewingHabits(summary)
            }
        }
    }
}

/** The year in one number: hours watched, the change from last year and a month-by-month curve. */
@Composable
private fun YearHero(summary: StatsSummary, now: LocalDate, onOpen: () -> Unit) {
    val hours = summary.year.minutes / 60
    val shape = RoundedCornerShape(Radius.Medium)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = .50f), Info.copy(alpha = .10f))))
            .border(.6.dp, Color.White.copy(alpha = .14f), shape)
            .clickable(onClick = onOpen)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrackedLabel(stringResource(R.string.stats_watched_in, now.year), Modifier.weight(1f))
            Text(stringResource(R.string.stats_open_year), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(hours.toString(), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, lineHeight = 46.sp)
            Text(
                pluralStringResource(R.plurals.hours_unit, hours), color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
        }
        if (summary.lastYearMinutes > 0) {
            val delta = (summary.year.minutes - summary.lastYearMinutes) / 60
            Text(
                stringResource(R.string.stats_vs_last_year, (if (delta >= 0) "+" else "−") + kotlin.math.abs(delta), now.year - 1),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        MonthCurve(summary.yearMonthMinutes, now.monthValue, Modifier.fillMaxWidth().height(58.dp).padding(top = 10.dp))
    }
}

/** Minutes per month of this year, up to the current month: an area and its line, the last point marked. */
@Composable
private fun MonthCurve(months: List<Int>, upToMonth: Int, modifier: Modifier) {
    val line = Info
    val dot = TextPrimary
    androidx.compose.foundation.Canvas(modifier) {
        val shown = months.take(upToMonth)
        if (shown.isEmpty()) return@Canvas
        val peak = (shown.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val stepX = size.width / 11f
        val points = shown.mapIndexed { index, value -> Offset(index * stepX, size.height - (value / peak) * (size.height - 6.dp.toPx()) - 3.dp.toPx()) }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = androidx.compose.ui.graphics.Path().apply {
            addPath(path)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = .30f), line.copy(alpha = 0f))))
        drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(dot, 3.5.dp.toPx(), points.last())
    }
}

@Composable
private fun StatsFacts(summary: StatsSummary) {
    val year = summary.year
    val usual = summary.usualSlot?.let { (day, hour) ->
        day.getDisplayName(java.time.format.TextStyle.SHORT, com.cinetrack.ui.currentLocale()).replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) } + " " + "%02d:00".format(hour)
    } ?: "—"
    val facts = listOf(
        pluralStringResource(R.plurals.episodes_count, year.episodes, year.episodes) to pluralStringResource(R.plurals.shows_count, summary.yearShows, summary.yearShows),
        pluralStringResource(R.plurals.movies_count, year.movies, year.movies) to stringResource(R.string.this_year),
        pluralStringResource(R.plurals.days_count, year.longestStreak, year.longestStreak) to stringResource(R.string.year_longest_streak),
        usual to stringResource(R.string.stats_usual_time),
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        facts.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { (value, label) ->
                    Column(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(Radius.Medium)).padding(horizontal = 14.dp, vertical = Spacing.md)) {
                        Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThisWeekCard(week: WeekActivity, now: LocalDate) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            TrackedLabel(stringResource(R.string.this_week))
            Text(formatWatchedDurationMinutes(week.minutes).ifBlank { "0m" }, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = Spacing.xs))
            Text(
                pluralStringResource(R.plurals.episodes_count, week.episodes, week.episodes) + " · " + pluralStringResource(R.plurals.movies_count, week.movies, week.movies),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }
        WeekBars(week, now, 12.dp, 46.dp, labels = true)
    }
}

/** Genres as proportional bars with their share of plays. */
@Composable
private fun GenreBars(shares: List<Pair<String, Int>>) {
    if (shares.isEmpty()) return
    val peak = shares.maxOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(stringResource(R.string.most_watched_genres), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
        shares.forEach { (genre, percent) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(genre, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(92.dp))
                Box(Modifier.weight(1f).height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f))) {
                    Box(Modifier.fillMaxWidth(percent.toFloat() / peak).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Accent, Info))))
                }
                Text("$percent%", color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun ActivityHeatmap(summary: StatsSummary, now: LocalDate) {
    val watchedDays = summary.watchedDays
    val heatmapWeeks = summary.heatmapWeeks
    val maxDailyActivity = summary.maxDailyActivity
    val longestStreak = summary.longestStreak
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.activity_heatmap), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.heatmap_summary, watchedDays.size, longestStreak), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(start = Spacing.xxl), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            heatmapWeeks.forEachIndexed { index, week ->
                val monthChanged = index == 0 || week.first().month != heatmapWeeks[index - 1].first().month
                Text(
                    if (monthChanged) week.first().format(com.cinetrack.ui.UiDateFormatters.current.month) else "",
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Column(Modifier.width(22.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                heatmapWeeks.first().forEach { day ->
                    Text(
                        day.format(com.cinetrack.ui.UiDateFormatters.current.narrowWeekday),
                        color = TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                    )
                }
            }
            heatmapWeeks.forEach { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    week.forEach { day ->
                        val count = watchedDays[day] ?: 0
                        val level = if (count == 0) 0 else ((count * 4 + maxDailyActivity - 1) / maxDailyActivity).coerceIn(1, 4)
                        Box(
                            Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(3.dp))
                                .background(
                                    Accent.copy(
                                        alpha = when {
                                            day.isAfter(now) -> .035f
                                            level == 0 -> .09f
                                            level == 1 -> .28f
                                            level == 2 -> .48f
                                            level == 3 -> .70f
                                            else -> .96f
                                        },
                                    ),
                                )
                                .then(if (day == now) Modifier.border(1.dp, AccentLight, RoundedCornerShape(3.dp)) else Modifier),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.less_activity), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(5.dp))
            listOf(.09f, .28f, .48f, .70f, .96f).forEach { alpha ->
                Box(Modifier.padding(horizontal = 1.5.dp).size(9.dp).clip(RoundedCornerShape(2.dp)).background(Accent.copy(alpha = alpha)))
            }
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.more_activity), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatisticTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().glass(RoundedCornerShape(Radius.Medium)).padding(horizontal = Spacing.sm, vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            value.ifBlank { "—" },
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun PeopleStatisticsRow(title: String, people: List<PersonCard>) {
    Text(title, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    if (people.isEmpty()) {
        Text("—", color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(people, key = PersonCard::id) { person ->
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent.copy(alpha = .55f), Info.copy(alpha = .45f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!person.profileUrl.isNullOrBlank()) {
                        AsyncImage(
                            person.profileUrl,
                            person.name,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(person.name.take(1).uppercase(), color = TextPrimary, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    person.name,
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    lineHeight = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Minutes for one watch event; TV episodes without a runtime count as 45. */
private fun watchedMinutesOf(event: com.cinetrack.domain.TimelineCard): Int =
    event.media.runtimeMinutes ?: if (event.media.type == MediaType.TV) 45 else 0

private class WeekActivity(val minutes: Int, val episodes: Int, val movies: Int, val perDay: List<Int>, val days: List<LocalDate>)

private data class StatsInputs(
    val history: List<com.cinetrack.domain.TimelineCard>,
    val library: List<MediaCard>,
    val today: LocalDate,
)

private class StatsSummary(
    val monthly: Int,
    val yearly: Int,
    val completed: Int,
    val abandoned: Int,
    val topGenres: List<String>,
    val watchedDays: Map<LocalDate, Int>,
    val heatmapWeeks: List<List<LocalDate>>,
    val maxDailyActivity: Int,
    val longestStreak: Int,
    val currentStreak: Int,
    val week: WeekActivity,
    val habits: List<Int>,
    val monthMinutes: Int,
    val year: YearReview,
    /** Minutes watched in each month of this year (12 entries). */
    val yearMonthMinutes: List<Int>,
    val lastYearMinutes: Int,
    val yearShows: Int,
    /** The weekday and hour this year's plays most often fall on. */
    val usualSlot: Pair<DayOfWeek, Int>?,
    /** The top genres with their share of all genre tags, in percent. */
    val genreShares: List<Pair<String, Int>>,
)

/** The current year at a glance: time, counts, the series watched most, streak, busiest month, genres. */
private class YearReview(
    val year: Int,
    val minutes: Int,
    val episodes: Int,
    val movies: Int,
    val topShow: MediaCard?,
    val topShowEpisodes: Int,
    val longestStreak: Int,
    val busiestMonth: java.time.Month?,
    val topGenres: List<String>,
)

/** Last grouped history, so returning to the tab draws immediately. */
internal object HistoryCache {
    @Volatile var input: List<com.cinetrack.domain.TimelineCard>? = null
    @Volatile var result: List<HistoryEntry>? = null
}

/** Last statistics summary, so returning to the tab draws immediately. */
private object StatsCache {
    @Volatile var inputs: StatsInputs? = null
    @Volatile var result: StatsSummary? = null
}

private fun computeStats(inputs: StatsInputs): StatsSummary {
    val now = inputs.today
    // Parse every timestamp once and reuse it for all figures.
    val dated = inputs.history.mapNotNull { event -> timelineLocalDate(event.timestamp)?.let { it to event } }
    val watchedDays = dated.groupingBy { it.first }.eachCount()
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    watchedDays.keys.sorted().forEach { day ->
        run = if (previous?.plusDays(1) == day) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    var streakDay = if (watchedDays.containsKey(now)) now else now.minusDays(1)
    var streak = 0
    while (watchedDays.containsKey(streakDay)) { streak++; streakDay = streakDay.minusDays(1) }
    val monday = now.with(DayOfWeek.MONDAY)
    val weekDays = (0L until 7L).map { monday.plusDays(it) }
    val weekEvents = dated.filter { it.first >= monday && it.first <= weekDays.last() }
    val firstMonday = now.minusWeeks(15).with(DayOfWeek.MONDAY)
    val tv = inputs.history.filter { it.media.type == MediaType.TV }
    val movies = inputs.history.filter { it.media.type == MediaType.MOVIE }
    return StatsSummary(
        monthly = dated.count { it.first.year == now.year && it.first.month == now.month },
        yearly = dated.count { it.first.year == now.year },
        completed = inputs.library.count { it.status == com.cinetrack.domain.LibraryStatus.COMPLETED },
        abandoned = inputs.library.count { it.status == com.cinetrack.domain.LibraryStatus.DROPPED },
        topGenres = inputs.history.flatMap { it.media.genres }.groupingBy { it.lowercase() }.eachCount().entries
            .sortedByDescending { it.value }.take(4).map { it.key.replaceFirstChar { char -> char.uppercase() } },
        watchedDays = watchedDays,
        heatmapWeeks = (0L until 16L).map { week -> (0L until 7L).map { day -> firstMonday.plusWeeks(week).plusDays(day) } },
        maxDailyActivity = watchedDays.values.maxOrNull()?.coerceAtLeast(1) ?: 1,
        longestStreak = longest,
        currentStreak = streak,
        week = WeekActivity(
            minutes = weekEvents.sumOf { watchedMinutesOf(it.second) },
            episodes = weekEvents.count { it.second.media.type == MediaType.TV },
            movies = weekEvents.count { it.second.media.type == MediaType.MOVIE },
            perDay = weekDays.map { day -> weekEvents.filter { it.first == day }.sumOf { watchedMinutesOf(it.second) } },
            days = weekDays,
        ),
        habits = listOf(tv.sumOf(::watchedMinutesOf), tv.size, movies.sumOf(::watchedMinutesOf), movies.size),
        monthMinutes = dated.filter { it.first.year == now.year && it.first.month == now.month }.sumOf { watchedMinutesOf(it.second) },
        year = yearReview(dated, now),
        yearMonthMinutes = (1..12).map { month -> dated.filter { it.first.year == now.year && it.first.monthValue == month }.sumOf { watchedMinutesOf(it.second) } },
        // Compared with the same stretch of last year, not all of it: in October a full
        // year always looks ahead.
        lastYearMinutes = now.minusYears(1).let { sameDay -> dated.filter { it.first.year == sameDay.year && !it.first.isAfter(sameDay) } }.sumOf { watchedMinutesOf(it.second) },
        yearShows = dated.filter { it.first.year == now.year && it.second.media.type == MediaType.TV }.map { it.second.media.stableKey }.distinct().size,
        usualSlot = inputs.history.asSequence()
            .mapNotNull { releaseDateTime(it.timestamp, ZoneId.systemDefault()) }
            .filter { it.year == now.year }
            .groupingBy { it.dayOfWeek to it.hour }.eachCount()
            .maxByOrNull { it.value }?.key,
        // This year's genres, like the rest of the page; all time until something is watched this year.
        genreShares = dated.filter { it.first.year == now.year }.map { it.second }.ifEmpty { inputs.history }
            .flatMap { it.media.genres }.groupingBy { it.lowercase() }.eachCount().let { counts ->
            val total = counts.values.sum().coerceAtLeast(1)
            counts.entries.sortedByDescending { it.value }.take(4).map { it.key.replaceFirstChar { char -> char.uppercase() } to (it.value * 100 / total) }
        },
    )
}

private fun yearReview(dated: List<Pair<LocalDate, com.cinetrack.domain.TimelineCard>>, now: LocalDate): YearReview {
    val events = dated.filter { it.first.year == now.year }
    val shows = events.filter { it.second.media.type == MediaType.TV }.groupBy { it.second.media.stableKey }
    val top = shows.maxByOrNull { it.value.size }
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    events.map { it.first }.distinct().sorted().forEach { day ->
        run = if (previous?.plusDays(1) == day) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    return YearReview(
        year = now.year,
        minutes = events.sumOf { watchedMinutesOf(it.second) },
        episodes = events.count { it.second.media.type == MediaType.TV },
        movies = events.count { it.second.media.type == MediaType.MOVIE },
        topShow = top?.value?.first()?.second?.media,
        topShowEpisodes = top?.value?.size ?: 0,
        longestStreak = longest,
        busiestMonth = events.groupingBy { it.first.month }.eachCount().maxByOrNull { it.value }?.key,
        topGenres = events.flatMap { it.second.media.genres }.groupingBy { it.lowercase() }.eachCount().entries
            .sortedByDescending { it.value }.take(3).map { it.key.replaceFirstChar { char -> char.uppercase() } },
    )
}

@Composable
private fun YearReviewCard(review: YearReview) {
    if (review.episodes + review.movies == 0) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Medium))
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = .30f), Accent.copy(alpha = .06f))))
            .glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            TrackedLabel(stringResource(R.string.year_review_title, review.year))
            Text(
                formatWatchedDurationMinutes(review.minutes).ifBlank { "0m" }, color = TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                pluralStringResource(R.plurals.episodes_count, review.episodes, review.episodes) + " · " +
                    pluralStringResource(R.plurals.movies_count, review.movies, review.movies),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        review.topShow?.let { show ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(Radius.Compact)).background(Color.White.copy(alpha = .06f))) {
                    show.posterUrl?.let { coil3.compose.AsyncImage(it, show.title, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.year_top_show), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Text(show.title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.episodes_count, review.topShowEpisodes, review.topShowEpisodes), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            HabitValue(stringResource(R.string.year_longest_streak), pluralStringResource(R.plurals.days_count, review.longestStreak, review.longestStreak), Modifier.weight(1f))
            HabitValue(
                stringResource(R.string.year_busiest_month),
                review.busiestMonth?.getDisplayName(java.time.format.TextStyle.FULL, com.cinetrack.ui.currentLocale())
                    ?.replaceFirstChar { it.titlecase(com.cinetrack.ui.currentLocale()) } ?: "—",
                Modifier.weight(1f),
            )
        }
        if (review.topGenres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                review.topGenres.forEach { genre ->
                    Text(
                        genre, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).background(Color.White.copy(alpha = .08f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekBars(week: WeekActivity, today: LocalDate, barWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp, labels: Boolean) {
    val peak = week.perDay.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        week.days.forEachIndexed { index, day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(barWidth).height(maxOf(4.dp, maxHeight * (week.perDay[index].toFloat() / peak)))
                        .clip(RoundedCornerShape(barWidth / 2))
                        .background(if (day == today) Accent else TextPrimary.copy(alpha = .18f)),
                )
                if (labels) Text(
                    day.format(com.cinetrack.ui.UiDateFormatters.current.narrowWeekday),
                    color = if (day == today) TextPrimary else TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackedLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(com.cinetrack.ui.currentLocale()),
        color = TextMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = com.cinetrack.ui.theme.EyebrowLetterSpacing,
        maxLines = 1,
        modifier = modifier,
    )
}

/** All-time viewing: durations on full-width lines, counts in a grid. */
@Composable
private fun ViewingHabits(summary: StatsSummary) {
    val habits = summary.habits
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TrackedLabel(stringResource(R.string.viewing_habits))
        HabitValue(stringResource(R.string.all_time), formatWatchedDurationMinutes(habits[0] + habits[2]).ifBlank { "0m" }, Modifier.fillMaxWidth())
        HabitValue(stringResource(R.string.tv_time), formatWatchedDurationMinutes(habits[0]).ifBlank { "0m" }, Modifier.fillMaxWidth())
        HabitValue(stringResource(R.string.movie_time), formatWatchedDurationMinutes(habits[2]).ifBlank { "0m" }, Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(.6.dp).background(Color.White.copy(alpha = .07f)))
        Row(Modifier.fillMaxWidth()) {
            HabitValue(stringResource(R.string.episodes), habits[1].toString(), Modifier.weight(1f))
            HabitValue(stringResource(R.string.movies), habits[3].toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            HabitValue(stringResource(R.string.this_month), summary.monthly.toString(), Modifier.weight(1f))
            HabitValue(stringResource(R.string.this_year), summary.yearly.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HabitValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}
