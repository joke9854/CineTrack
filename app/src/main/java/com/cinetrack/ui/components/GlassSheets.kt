@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cinetrack.R
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import com.cinetrack.ui.theme.Radius

@Composable
fun SharedGlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // With the app already blurred behind it, a tinted panel reads as glass and needs no
    // recording of the page; that recording made popups stutter on heavy pages.
    val appBlur = rememberAppBlurActive()
    val page = if (appBlur) null else rememberFloatingPageBackdrop()
    val state = if (page == null && !appBlur) rememberFloatingGlassState() else null
    val shape = PopupMaterial.FloatingShape
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        DialogWindowDim()
        PopupBackdropBlur()
        Column(Modifier.padding(horizontal = 14.dp).widthIn(max = 520.dp).fillMaxWidth()
            .then(if (appBlur) Modifier.clip(shape).background(PopupMaterial.Panel).border(.5.dp, ControlEdgeBrush, shape)
            else if (page != null) Modifier.liquidGlassPanel(page, shape)
                else Modifier.clip(shape)
                    .then(if (state != null) Modifier.hazeBlur(input = HazeInput.Sources(state), style = NavGlassStyle)
                        else Modifier.background(GlassMaterial.OverlayFallback))
                    .border(.5.dp, ControlEdgeBrush, shape))) {
            // Controls inside sit on the panel, not on the page the panel refracts.
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
        }
    }
}

@Composable
fun SharedGlassSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable () -> Unit,
) {
    val appBlur = rememberAppBlurActive()
    val page = if (appBlur) null else rememberFloatingPageBackdrop()
    val hazeState = if (page == null && !appBlur) rememberFloatingGlassState() else null
    // Floating: inset from the screen edges and rounded on every corner, the glass
    // refracting the page behind it, over a light scrim.
    val shape = PopupMaterial.FloatingShape
    val rim = Modifier.border(.6.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = .12f), .18f to Color.White.copy(alpha = .05f), 1f to Color.White.copy(alpha = .03f)), shape)
    val handle: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .30f)))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = TextPrimary,
        scrimColor = PopupMaterial.Scrim.copy(alpha = .34f),
        shape = shape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        PopupBackdropBlur(visible = sheetState.targetValue != androidx.compose.material3.SheetValue.Hidden)
        val material = when {
            appBlur -> Modifier.clip(shape).background(PopupMaterial.Panel)
            page != null -> Modifier.liquidGlassPanel(page, shape)
            hazeState != null -> Modifier.clip(shape).hazeBlur(input = HazeInput.Sources(hazeState), style = NavGlassStyle)
            else -> Modifier.clip(shape).background(GlassMaterial.OverlayFallback)
        }
        Column(
            Modifier.fillMaxWidth()
                .padding(start = PopupMaterial.FloatingMargin, end = PopupMaterial.FloatingMargin, bottom = PopupMaterial.FloatingMargin)
                .then(material)
                .then(rim)
                .padding(bottom = 18.dp),
        ) {
            handle()
            // Controls inside sit on the panel, not on the page the panel refracts.
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
        }
    }
}

@Composable
fun LibraryStatusSheet(
    media: MediaCard,
    onDismiss: () -> Unit,
    onStatus: (LibraryStatus) -> Unit,
) {
    val dismissAction = rememberUiAction(onDismiss)
    SharedGlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            SheetTitle(stringResource(R.string.add_to_library))
            val choices = listOf(
                Triple(LibraryStatus.PLAN_TO_WATCH, stringResource(R.string.plan_to_watch), Icons.Filled.Bookmark),
                Triple(LibraryStatus.WATCHING, stringResource(R.string.in_progress), Icons.Filled.Visibility),
                Triple(LibraryStatus.PAUSED, stringResource(R.string.paused), Icons.Filled.Pause),
                Triple(LibraryStatus.COMPLETED, stringResource(R.string.completed), Icons.Filled.Check),
                Triple(LibraryStatus.DROPPED, stringResource(R.string.dropped), Icons.Filled.VisibilityOff),
            )
            GlassMenuGroup {
                choices.forEachIndexed { index, (status, label, icon) ->
                    val selected = media.status == status
                    GlassMenuRow(
                        label = label,
                        icon = icon,
                        tint = libraryStatusColor(status),
                        supporting = when (status) {
                            LibraryStatus.PLAN_TO_WATCH -> stringResource(R.string.status_plan_description)
                            LibraryStatus.WATCHING -> stringResource(R.string.status_watching_description)
                            LibraryStatus.PAUSED -> stringResource(R.string.paused_description)
                            LibraryStatus.COMPLETED -> stringResource(if (media.type == MediaType.MOVIE) R.string.watched_movie else R.string.watched_tv)
                            LibraryStatus.DROPPED -> stringResource(R.string.status_dropped_description)
                            else -> null
                        },
                        selected = selected,
                        divider = index < choices.lastIndex,
                    ) { onStatus(if (selected) LibraryStatus.NONE else status) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(42.dp).glass(RoundedCornerShape(Radius.Pill)).clickable(onClick = dismissAction),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(stringResource(android.R.string.cancel), color = TextSecondary, fontWeight = FontWeight.Bold) }
        }
    }
}
