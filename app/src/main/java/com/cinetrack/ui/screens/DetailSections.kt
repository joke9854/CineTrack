@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.animation.core.animateFloat
import com.cinetrack.ui.components.sharedBackdrop
import com.cinetrack.ui.components.sharedPoster
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import com.cinetrack.ui.components.liquidGlassPill
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinetrack.R
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.RatingScore
import com.cinetrack.ui.components.MediaPoster
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.SectionHeader
import com.cinetrack.ui.components.SharedGlassSheet
import com.cinetrack.ui.components.GlassMaterial
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Success
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.cinetrack.ui.theme.LocalDetailAccent
import com.cinetrack.ui.theme.Motion
import com.cinetrack.ui.theme.PosterAspectRatio
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette

/** On detail pages the accent follows the poster (see [LocalDetailAccent]). */
private val Accent: Color
    @Composable get() = LocalDetailAccent.current?.value ?: com.cinetrack.ui.theme.Accent
private val AccentLight: Color
    @Composable get() = LocalDetailAccent.current?.value?.let { androidx.compose.ui.graphics.lerp(it, Color.White, .32f) }
        ?: com.cinetrack.ui.theme.AccentLight

@Composable
internal fun TrailerActionButton(onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Box(
        Modifier.size(48.dp)
            .liquidGlassPill(under = com.cinetrack.ui.components.GlassMaterial.DetailSurface)
            .clickable(onClick = clickAction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, stringResource(R.string.trailer), tint = TextSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun TrailerPlayerSheet(
    title: String,
    trailerKey: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    SharedGlassSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            Text(
                stringResource(R.string.watch_trailer),
                color = TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(title, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Radius.Large))
                    .background(SurfacePalette.BottomFade),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> com.cinetrack.ui.components.SkeletonBox(Modifier.fillMaxSize(), description = stringResource(R.string.loading))
                    trailerKey.isNullOrBlank() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.trailer_unavailable), color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        PrimaryAction(stringResource(R.string.retry), Icons.Filled.PlayArrow, Modifier.width(150.dp), onClick = onRetry)
                    }
                    else -> YouTubeTrailerPlayer(trailerKey)
                }
            }
        }
    }
}

@Composable
private fun YouTubeTrailerPlayer(videoKey: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerView by remember(videoKey) { mutableStateOf<YouTubePlayerView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            YouTubePlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoKey, 0f)
                    }
                })
                playerView = this
            }
        },
        update = { },
    )
    DisposableEffect(videoKey, lifecycleOwner) {
        onDispose {
            playerView?.let { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
            playerView = null
        }
    }
}

@Composable
internal fun DetailHero(media: MediaCard) {
    // A tapped wide cover grows into this header.
    // Sized like Trakt's: the poster spans about two thirds of the width and
    // starts just below the back button row.
    Box(Modifier.fillMaxWidth().sharedBackdrop(media.stableKey, slot = null)) {
        // The backdrop art, blurred, fades into the page behind the poster.
        media.backdropUrl?.takeIf(String::isNotBlank)?.let { backdrop ->
            // matchParentSize: the hero now takes its height from the poster.
            AsyncImage(com.cinetrack.ui.components.rememberBlurredArtRequest(backdrop), null, Modifier.matchParentSize().blur(18.dp).fadeOutBottom(.72f), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x33080A0C), .55f to Color(0x4D080A0C), 1f to Color.Transparent)))
        }
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, bottom = 24.dp).fillMaxWidth(.68f).aspectRatio(PosterAspectRatio)
                // The tapped poster flies into this slot.
                .sharedPoster(media.stableKey, slot = null)
                .shadow(18.dp, RoundedCornerShape(Radius.Large), clip = false)
                .clip(RoundedCornerShape(Radius.Large)).background(Brush.linearGradient(listOf(Accent, SurfacePalette.BlueSurface)))
        ) {
                if (!media.posterUrl.isNullOrBlank()) com.cinetrack.ui.components.SharedPosterTargetImage(media.posterUrl, media.title, media.stableKey, Modifier.fillMaxSize(), ContentScale.FillBounds)
        }
    }
}

@Composable
internal fun DetailIdentity(media: MediaCard, loaded: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            media.title,
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            lineHeight = 33.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        // Year, runtime and the first genres on one line; details that sharpen once loaded crossfade in place.
        val facts = listOfNotNull(
            media.year.takeIf(String::isNotBlank),
            formatDurationMinutes(media.runtimeMinutes).takeIf { media.type == MediaType.MOVIE && it.isNotBlank() },
            media.genres.take(2).joinToString(", ").takeIf(String::isNotBlank),
        ).joinToString(" · ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.Crossfade(facts, label = "detailFacts", modifier = Modifier.weight(1f, fill = false)) { text ->
                Text(text, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2)
            }
            if (media.type == MediaType.TV) media.tmdbStatus?.takeIf(String::isNotBlank)?.let { SeriesStatusChip(it) }
        }
        if (!loaded && media.genres.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            SkeletonPill(Modifier.width(160.dp).height(12.dp))
        }
    }
}

/** "In corso" / "Conclusa": tinted while a series is still running, neutral once it has ended. */
@Composable
private fun SeriesStatusChip(status: String) {
    val running = status.lowercase() in setOf("returning series", "in production", "post production", "planned", "pilot")
    val tint = if (running) Success else TextMuted
    Text(
        tmdbStatusLabel(status),
        color = if (running) androidx.compose.ui.graphics.lerp(Success, Color.White, .35f) else TextSecondary,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).background(tint.copy(alpha = .18f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
internal fun PlotSection(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().animateContentSize()
            .clickable(enabled = overflows || expanded, interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
    ) {
        SectionHeader(stringResource(R.string.overview))
        Spacer(Modifier.height(8.dp))
        Text(
            text, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, lineHeight = 22.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                stringResource(if (expanded) R.string.plot_less else R.string.plot_more), color = AccentLight,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun RatingsSection(ratings: List<RatingScore>, tmdbScore: Double?, pending: Boolean) {
    // TMDB comes from the card straight away, in the card's own format; the other sources
    // hold their cell as a skeleton and fill in as they load.
    val others = ratings.filter { it.score.any(Char::isDigit) && (tmdbScore == null || !it.source.equals("TMDB", ignoreCase = true)) }
    val shown = (listOfNotNull(tmdbScore?.let { RatingScore("TMDB", String.format(com.cinetrack.ui.currentLocale(), "%.1f", it)) }) + others).take(4)
    if (shown.isEmpty() && !pending) return
    val cells = if (pending) 4 else shown.size
    Row(
        Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth().height(58.dp)
            .liquidGlassPill(under = com.cinetrack.ui.components.GlassMaterial.DetailSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) { SkeletonPillGroup(enabled = shown.size < cells) {
        repeat(cells) { index ->
            if (index > 0) Box(Modifier.width(.6.dp).fillMaxHeight(.46f).background(Color.White.copy(alpha = .12f)))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val rating = shown.getOrNull(index)
                if (rating != null) {
                    androidx.compose.runtime.key(rating.source) { RatingCell(rating) }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkeletonPill(Modifier.width(30.dp).height(14.dp))
                        Spacer(Modifier.height(6.dp))
                        SkeletonPill(Modifier.width(44.dp).height(9.dp))
                    }
                }
            }
        }
    } }
}

@Composable
private fun RatingCell(rating: RatingScore) {
    // Fades in where its placeholder was.
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(Motion.Medium)) }
    Column(Modifier.graphicsLayer { alpha = appear.value }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(rating.score, color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(ratingSourceLabel(rating.source), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun ratingSourceLabel(source: String): String {
    val key = source.lowercase().replace(".", "").replace(" ", "")
    return when {
        "rotten" in key || key.startsWith("rtom") -> "Rotten T."
        "imdb" in key -> "IMDb"
        "tmdb" in key -> "TMDB"
        "metacritic" in key -> "Metacritic"
        "letterboxd" in key -> "Letterboxd"
        "trakt" in key -> "Trakt"
        "mal" == key || "myanimelist" in key -> "MAL"
        else -> source.lowercase().replaceFirstChar { it.titlecase() }
    }
}

/** Fades from a skeleton to the real section once its data is known. */
@Composable
internal fun LoadedSection(loaded: Boolean, skeleton: @Composable () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.animation.Crossfade(loaded, animationSpec = androidx.compose.animation.core.tween(220), label = "loadedSection") { ready ->
        if (ready) Column { content() } else skeleton()
    }
}

@Composable
internal fun OverviewSkeleton() {
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth()) {
        // Same heading as the loaded plot, so nothing moves when the text arrives.
        SectionHeader(stringResource(R.string.overview))
        Spacer(Modifier.height(10.dp))
        SkeletonPillGroup {
            listOf(1f, .96f, .92f, .58f).forEach { width ->
                SkeletonPill(Modifier.fillMaxWidth(width).height(12.dp))
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun CastSkeleton() {
    Column {
        // Same header as the loaded section, so the title doesn't move when the cast arrives.
        SectionHeader(stringResource(R.string.cast_title), Modifier.padding(horizontal = DetailLayout.Gutter), stringResource(R.string.see_all)) {}
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = DetailLayout.Gutter - 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { SkeletonPillGroup {
            repeat(4) {
                Column(Modifier.width(DetailLayout.CastWidth).padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonPill(Modifier.size(DetailLayout.CastPhoto), CircleShape)
                    Spacer(Modifier.height(10.dp))
                    SkeletonPill(Modifier.fillMaxWidth(.8f).height(11.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonPill(Modifier.fillMaxWidth(.55f).height(9.dp))
                }
            }
        } }
    }
}

/** A soft pulsing placeholder in a pill shape, for values that are still loading. */
@Composable
private fun SkeletonPill(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(Radius.Pill)) {
    val pulse = com.cinetrack.ui.components.skeletonPulse(SkeletonPillPulseMillis, reducedMotion = false)
    Box(modifier.clip(shape).drawBehind { drawRect(Color.White.copy(alpha = androidx.compose.ui.util.lerp(.05f, .11f, pulse.fraction.value))) })

}

private const val SkeletonPillPulseMillis = 800

/** One pulse for every [SkeletonPill] in [content]. */
@Composable
private fun SkeletonPillGroup(enabled: Boolean = true, content: @Composable () -> Unit) =
    com.cinetrack.ui.components.SkeletonPulseGroup(SkeletonPillPulseMillis, reducedMotion = false, enabled = enabled, content = content)

/**
 * The best way to watch (subscription first) as the page's one card, with Apri.
 * Every other service folds into a quiet line that opens the full list.
 */
@Composable
internal fun WatchSection(media: MediaCard) {
    val uriHandler = LocalUriHandler.current
    val offers = com.cinetrack.domain.visibleProviderOffers(media)
    val order = listOf(
        "flatrate" to R.string.offer_flatrate, "free" to R.string.offer_free, "ads" to R.string.offer_ads,
        "rent" to R.string.offer_rent, "buy" to R.string.offer_buy,
    )
    val best = order.firstNotNullOfOrNull { (type, label) -> offers[type].orEmpty().firstOrNull()?.let { it to label } }
    val link = media.providerLink?.takeIf(String::isNotBlank)
    var expanded by remember(media.stableKey) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = DetailLayout.Gutter).fillMaxWidth()) {
        SectionHeader(stringResource(if (media.type == MediaType.MOVIE) R.string.where_to_watch_movie else R.string.where_to_watch_tv))
        Spacer(Modifier.height(10.dp))
        if (best == null) {
            Text(
                stringResource(if (media.providerAvailabilityExists) R.string.no_matching_watch_providers else R.string.no_watch_providers),
                color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            return@Column
        }
        val (provider, label) = best
        val others = order.flatMap { offers[it.first].orEmpty() }.distinct().filterNot { it == provider }
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProviderLogo(provider, media.providerLogos[provider], 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.watch_on, provider), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(label), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
                if (link != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.open_action),
                        color = if (Accent.luminance() > .6f) Color.Black else Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).background(Accent)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { runCatching { uriHandler.openUri(link) } }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            if (others.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(.5.dp).background(Color.White.copy(alpha = .08f)))
                val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "providersChevron")
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val names = others.take(3).joinToString(", ") + if (others.size > 3) " +${others.size - 3}" else ""
                    Text(stringResource(R.string.also_on, names), color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = TextMuted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        listOf("flatrate" to R.string.subscription, "rent" to R.string.rent, "buy" to R.string.buy,
                            "free" to R.string.provider_free, "ads" to R.string.provider_ads).forEach { (type, category) ->
                            ProviderCategory(stringResource(category), offers[type].orEmpty(), media.providerLogos)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderLogo(provider: String, logo: String?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .24f)).background(Accent.copy(alpha = .26f)), contentAlignment = Alignment.Center) {
        if (!logo.isNullOrBlank()) AsyncImage(logo, provider, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(provider.take(1), color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ProviderCategory(label: String, providers: List<String>, logos: Map<String, String>) {
    if (providers.isEmpty()) return
    Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(providers, key = { it }) { provider ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(provider, logos[provider], 26.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(provider, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
        }
    }
}

@Composable
internal fun CollectionSection(current: MediaCard, related: List<MediaCard>, onMedia: (MediaCard) -> Unit) {
    val collection = (listOf(current) + related.filter { it.type == current.type })
        .distinctBy(MediaCard::stableKey)
        .sortedWith(
            compareBy<MediaCard> { it.releaseDate.isNullOrBlank() }
                .thenBy { it.releaseDate.orEmpty() }
                .thenBy(MediaCard::title),
        )
    val selectedIndex = collection.indexOfFirst { it.stableKey == current.stableKey }.coerceAtLeast(0)
    val collectionState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0),
    )
    LaunchedEffect(current.stableKey, collection.size) {
        // Show the selected film immediately with one chronological neighbour
        // before it whenever the rail has enough items.
        collectionState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    val today = remember { java.time.LocalDate.now() }
    val seen = collection.count { it.watched || it.status == LibraryStatus.COMPLETED }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DetailLayout.Gutter), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.collection_title), Modifier.weight(1f))
            Text(stringResource(R.string.saga_seen, seen, collection.size), color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = collectionState,
            contentPadding = PaddingValues(horizontal = DetailLayout.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collection, key = MediaCard::stableKey) { item ->
                val selected = item.stableKey == current.stableKey
                // Films that aren't out yet sit back a little.
                val upcoming = item.releaseDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.isAfter(today) == true
                Column(Modifier.alpha(if (upcoming && !selected) .55f else 1f)) {
                    MediaPoster(
                        item,
                        width = 104.dp,
                        showYear = false,
                        selectedBorder = TextPrimary.takeIf { selected },
                        onClick = { onMedia(item) },
                    )
                    Text(
                        listOfNotNull("${collection.indexOf(item) + 1}", item.releaseDate?.take(4)?.takeIf(String::isNotBlank)).joinToString(" · "),
                        color = if (selected) TextPrimary else TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun tmdbStatusLabel(status: String): String = when (status.lowercase()) {
    "returning series" -> stringResource(R.string.tmdb_returning_series)
    "in production" -> stringResource(R.string.tmdb_in_production)
    "post production" -> stringResource(R.string.tmdb_post_production)
    "released" -> stringResource(R.string.tmdb_released)
    "ended" -> stringResource(R.string.tmdb_ended)
    "planned" -> stringResource(R.string.tmdb_planned)
    "canceled", "cancelled" -> stringResource(R.string.tmdb_canceled)
    "pilot" -> stringResource(R.string.tmdb_pilot)
    "rumored", "rumoured" -> stringResource(R.string.tmdb_rumored)
    else -> status
}

/** Only what the header doesn't already say, as a plain label / value list. */
@Composable
internal fun UsefulInfoSection(media: MediaCard) {
    val locale = com.cinetrack.ui.currentLocale()
    val rows = buildList {
        formatFullDate(media.releaseDate).takeIf { it.isNotBlank() && it != "—" }?.let {
            add(stringResource(if (media.type == MediaType.TV) R.string.first_aired else R.string.release_date) to it)
        }
        if (media.type == MediaType.MOVIE) {
            formatFullDate(media.digitalRelease).takeIf { it.isNotBlank() && it != "—" }?.let { date ->
                add(stringResource(R.string.digital_release) to (media.digitalReleaseCountry?.let { "$date ($it)" } ?: date))
            }
        }
        if (media.type == MediaType.MOVIE) media.tmdbStatus?.takeIf(String::isNotBlank)?.let { add(stringResource(R.string.status) to tmdbStatusLabel(it)) }
        if (media.type == MediaType.TV) {
            val regular = media.seasons.filter { it.number > 0 }
            if (regular.isNotEmpty()) {
                add(stringResource(R.string.seasons) to regular.size.toString())
                regular.sumOf { it.episodeCount }.takeIf { it > 0 }?.let { add(stringResource(R.string.episodes) to it.toString()) }
            }
            if (media.networks.isNotEmpty()) add(stringResource(R.string.networks) to media.networks.joinToString(" · "))
        } else {
            formatUsd(media.budget).takeIf(String::isNotBlank)?.let { add(stringResource(R.string.budget) to it) }
            formatUsd(media.boxOffice).takeIf(String::isNotBlank)?.let { add(stringResource(R.string.box_office) to it) }
        }
        if (media.productionCountries.isNotEmpty()) add(stringResource(R.string.production_countries) to media.productionCountries.joinToString(" · "))
        media.originalLanguage?.takeIf(String::isNotBlank)?.let { code ->
            val name = java.util.Locale.forLanguageTag(code.lowercase()).getDisplayLanguage(locale)
                .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
                ?.replaceFirstChar { it.titlecase(locale) } ?: code.uppercase()
            add(stringResource(R.string.original_language) to name)
        }
        if (media.productionCompanies.isNotEmpty()) add(stringResource(R.string.production_companies) to media.productionCompanies.take(3).joinToString(" · "))
    }
    if (rows.isNotEmpty()) DetailsList(stringResource(R.string.details), rows)
}

@Composable
internal fun DetailsList(title: String, rows: List<Pair<String, String>>, inset: androidx.compose.ui.unit.Dp = DetailLayout.Gutter) {
    Column(Modifier.padding(horizontal = inset).fillMaxWidth()) {
        SectionHeader(title)
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .08f)))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 16.dp))
                Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun formatUsd(amount: Long?): String {
    val value = amount?.takeIf { it > 0L } ?: return ""
    fun compact(divisor: Double, suffix: String): String {
        val number = value / divisor
        val formatted = if (number >= 100 || number % 1.0 == 0.0) "%.0f".format(java.util.Locale.US, number)
        else "%.1f".format(java.util.Locale.US, number)
        return "\$$formatted$suffix"
    }
    return when {
        value >= 1_000_000_000L -> compact(1_000_000_000.0, "B")
        value >= 1_000_000L -> compact(1_000_000.0, "M")
        else -> java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(value)
    }
}
