@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.cinetrack.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinetrack.ui.theme.AccentLight
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun LongPullRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val pullState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onRefresh,
        refreshThreshold = 132.dp,
        refreshingOffset = 62.dp,
    )
    Box(modifier.fillMaxSize().pullRefresh(pullState, enabled)) {
        content()
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp),
            backgroundColor = Color(0xE6272B32),
            contentColor = AccentLight,
            scale = true,
        )
    }
}

internal fun formatFullDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return runCatching {
        LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(raw)
}

/** Formats long runtimes as hours instead of leaving values such as "97 min". */
internal fun formatDurationMinutes(minutes: Int?): String {
    val value = minutes?.takeIf { it > 0 } ?: return ""
    if (value < 60) return "$value min"
    val hours = value / 60
    val remainder = value % 60
    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}

@Composable
internal fun NavCollapseEffect(listState: LazyListState, onCompact: (Boolean) -> Unit) {
    LaunchedEffect(listState) {
        var previousIndex = 0
        var previousOffset = 0
        var accumulatedDown = 0
        var compact = false
        fun update(value: Boolean) {
            if (compact != value) {
                compact = value
                onCompact(value)
            }
        }
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            if (index == 0 && offset < 40) {
                accumulatedDown = 0
                update(false)
            } else {
                val delta = if (index == previousIndex) offset - previousOffset else if (index > previousIndex) 80 else -80
                if (delta > 0) {
                    accumulatedDown += delta
                    if (accumulatedDown > 16) update(true)
                } else if (delta < -3) {
                    accumulatedDown = 0
                    update(false)
                }
            }
            previousIndex = index
            previousOffset = offset
        }
    }
}

@Composable
internal fun NavCollapseGridEffect(gridState: LazyGridState, onCompact: (Boolean) -> Unit) {
    LaunchedEffect(gridState) {
        var previousIndex = 0
        var previousOffset = 0
        var accumulatedDown = 0
        var compact = false
        fun update(value: Boolean) {
            if (compact != value) {
                compact = value
                onCompact(value)
            }
        }
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            if (index == 0 && offset < 40) {
                accumulatedDown = 0
                update(false)
            } else {
                val delta = if (index == previousIndex) offset - previousOffset else if (index > previousIndex) 80 else -80
                if (delta > 0) {
                    accumulatedDown += delta
                    if (accumulatedDown > 16) update(true)
                } else if (delta < -3) {
                    accumulatedDown = 0
                    update(false)
                }
            }
            previousIndex = index
            previousOffset = offset
        }
    }
}
