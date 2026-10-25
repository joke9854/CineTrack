@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cinetrack.ui.screens

import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import com.cinetrack.ui.components.liquidGlass
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import com.cinetrack.R
import androidx.compose.material.icons.filled.LinkOff
import com.cinetrack.domain.AppUiState
import com.cinetrack.ui.components.GlassBackButton
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.rememberUiAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextMuted
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary
import com.cinetrack.ui.theme.Glass
import com.cinetrack.ui.theme.Radius
import com.cinetrack.ui.theme.Spacing
import com.cinetrack.ui.theme.SurfacePalette
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedTextField

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)) {
        SettingsGroupLabel(title)
        Spacer(Modifier.height(Spacing.sm))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.Medium)).padding(vertical = Spacing.xs)) { content() }
    }
}

@Composable
internal fun currentAppLanguageLabel(): String {
    val language = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').substringBefore(',')
        .ifBlank { com.cinetrack.ui.currentLocale().language }
    return stringResource(if (language == "it") R.string.italian else R.string.english)
}

@Composable
internal fun ToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChecked).padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        CompactSwitch(checked)
    }
}

@Composable
internal fun CompactSwitch(checked: Boolean) {
    val knobOffset by animateDpAsState(if (checked) 22.dp else 3.dp, com.cinetrack.ui.theme.Motion.snappy(), label = "switchKnob")
    Box(
        Modifier.width(52.dp).height(32.dp).clip(CircleShape)
            .background(if (checked) Accent else Glass)
            .border(.7.dp, AccentLight.copy(alpha = .24f), CircleShape),
    ) {
        // Glass knob over the track; the track and card it covers are redrawn under it.
        val track = if (checked) Accent else Glass
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(knobOffset.roundToPx(), 3.dp.roundToPx()) }.size(26.dp)
                .liquidGlass(
                    com.cinetrack.ui.components.GlassSurface.Toggle,
                    surface = if (checked) Color.White.copy(alpha = .78f) else TextMuted.copy(alpha = .45f),
                    under = track.compositeOver(com.cinetrack.ui.components.GlassMaterial.Content),
                    shadow = false,
                ) { clip(CircleShape).background(if (checked) Color.White else TextMuted.copy(alpha = .55f)) },
        )
    }
}

@Composable
internal fun ValueRow(title: String, value: String, success: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = if (success) TextPrimary else TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ProviderRow(name: String, subtitle: String, configured: Boolean, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        ServiceLogo(name)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) { Text(name, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Text(subtitle, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        StatusDot(configured)
        Spacer(Modifier.width(Spacing.sm))
        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun ServiceLogo(name: String, siteUrl: String? = null, size: androidx.compose.ui.unit.Dp = 38.dp) {
    val uriHandler = LocalUriHandler.current
    val (logo, background, logoSize) = when (name.lowercase()) {
        "tmdb" -> Triple(R.drawable.ic_service_tmdb, SurfacePalette.OceanDeep, 29.dp)
        "mdblist" -> Triple(R.drawable.ic_service_mdblist, SurfacePalette.CoolText, 24.dp)
        // Floppy's own app icon, full bleed.
        "floppy" -> Triple(R.drawable.floppy_app_icon, Color.Transparent, 38.dp)
        // Official marks (drawable-nodpi/ic_service_tvmaze.png is TVmaze's own app icon from tvmaze.com,
        // ic_service_fanart.png Fanart.tv's TV-in-a-circle): on their sites' white and charcoal.
        "tvmaze" -> Triple(R.drawable.ic_service_tvmaze, Color.White, 30.dp)
        "fanart.tv" -> Triple(R.drawable.ic_service_fanart, Color(0xFF1E1E1E), 26.dp)
        "tvdb" -> Triple(R.drawable.ic_service_tvdb, Color(0xFF0F171C), 30.dp)
        else -> Triple(R.drawable.ic_service_simkl, SurfacePalette.WarmText, 24.dp)
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(background)
            .border(.6.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(size * .3f))
            .then(
                if (siteUrl != null) Modifier.clickable(onClick = rememberUiAction { runCatching { uriHandler.openUri(siteUrl) } })
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = name,
            modifier = Modifier.size(logoSize * (size / 38.dp)),
        )
    }
}

@Composable
internal fun ChoiceRow(title: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = clickAction).padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) AccentLight else TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
            }
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = AccentLight, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------- settings kit
// Shared pieces for the settings pages: a brand header for services, grouped icon rows,
// toggles that say what they do, numbered steps and status cards.

internal data class ServiceBrand(
    val name: String,
    val brush: Brush,
    val button: Color,
    val logo: Int,
    val wordmark: Int? = null,
)

internal fun serviceBrand(page: String): ServiceBrand? = when (page) {
    SettingsPages.ServiceTmdb -> ServiceBrand("TMDB", Brush.linearGradient(listOf(Color(0xFF0D253F), Color(0xFF0B5C7A), Color(0xFF01B4E4))), Color(0xFF0B7FA6), R.drawable.ic_service_tmdb)
    SettingsPages.ServiceSimkl -> ServiceBrand("Simkl", Brush.linearGradient(listOf(Color(0xFF151515), Color(0xFF2A2A2A), Color(0xFF141414))), Color(0xFF3B8F5A), R.drawable.ic_service_simkl)
    SettingsPages.ServiceFloppy -> ServiceBrand("Floppy", Brush.linearGradient(listOf(Color(0xFF0E1B33), Color(0xFF1C2F5C), Color(0xFF14204A))), Color(0xFF185FA5), R.drawable.floppy_app_icon, R.drawable.floppy_wordmark)
    // TVmaze's teal, Fanart.tv's charcoal and TVDB's green, as on their own sites.
    SettingsPages.ServiceTvmaze -> ServiceBrand("TVmaze", Brush.linearGradient(listOf(Color(0xFF0B1F1D), Color(0xFF1C5A54), Color(0xFF3C948B))), Color(0xFF2F7C74), R.drawable.ic_service_tvmaze)
    SettingsPages.ServiceFanart -> ServiceBrand("Fanart.tv", Brush.linearGradient(listOf(Color(0xFF121212), Color(0xFF2A2A2A), Color(0xFF3A3A3A))), Color(0xFF5A5A5A), R.drawable.ic_service_fanart)
    SettingsPages.ServiceTvdb -> ServiceBrand("TVDB", Brush.linearGradient(listOf(Color(0xFF0D171C), Color(0xFF15372A), Color(0xFF3E8F5F))), Color(0xFF3E8F5F), R.drawable.ic_service_tvdb)
    SettingsPages.ServiceMdblist -> ServiceBrand("MDBList", Brush.linearGradient(listOf(Color(0xFF0C447C), Color(0xFF2A6FB5))), Color(0xFF2A6FB5), R.drawable.ic_service_mdblist)
    else -> null
}

/** The service's header: its colours, its mark repeated faintly, and where things stand. */
@Composable
internal fun ServiceHero(brand: ServiceBrand, state: AppUiState, onBack: () -> Unit) {
    val active = when (brand.name) {
        "TMDB" -> state.tmdbApiConfigured
        "Simkl" -> state.simklConnected
        "Floppy" -> state.floppyConnected
        "TVmaze" -> state.tvmazeEnabled
        "Fanart.tv" -> state.fanartConfigured
        "TVDB" -> state.tvdbConfigured
        else -> state.mdbListApiConfigured
    }
    val status = when {
        brand.name == "Simkl" && active && !state.simklUsername.isNullOrBlank() -> stringResource(R.string.simkl_connected_as, state.simklUsername.orEmpty())
        brand.name == "Floppy" && active -> floppyConnectedLabel(state)
        brand.name in setOf("Simkl", "Floppy") -> stringResource(if (active) R.string.connected else R.string.not_connected)
        brand.name == "TVmaze" -> stringResource(if (active) R.string.service_on else R.string.service_off)
        else -> stringResource(if (active) R.string.configured else R.string.not_configured)
    }
    val description = stringResource(
        when (brand.name) {
            "TMDB" -> R.string.tmdb_description
            "Simkl" -> R.string.simkl_description
            "Floppy" -> R.string.floppy_self_hosted_description
            "TVmaze" -> R.string.tvmaze_description
            "Fanart.tv" -> R.string.fanart_description
            "TVDB" -> R.string.tvdb_description
            else -> R.string.mdblist_description
        },
    )
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 6.dp).fillMaxWidth().clip(shape)
            .background(brand.brush).border(.6.dp, Color.White.copy(alpha = .10f), shape),
    ) {
        Image(
            painterResource(brand.logo), null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 18.dp).size(96.dp)
                .clip(RoundedCornerShape(26.dp)).graphicsLayer { alpha = .14f },
        )
        Column(Modifier.padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = Spacing.xl)) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(start = Spacing.sm)) {
                if (brand.wordmark != null) {
                    Image(painterResource(brand.wordmark), brand.name, modifier = Modifier.height(46.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(brand.name, size = 40.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Text(brand.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                KitStatusPill(active, status, Modifier.padding(top = Spacing.md))
                Text(description, color = Color.White.copy(alpha = .78f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
internal fun KitStatusPill(active: Boolean, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(Radius.Pill)).background(Color.Black.copy(alpha = .28f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(active)
        Spacer(Modifier.width(7.dp))
        Text(text, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Icon, title, optional description, then a value, a chevron when it opens something, or [trailing]. */
@Composable
internal fun KitRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickAction = onClick?.let { rememberUiAction(it) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (clickAction != null) Modifier.clickable(onClick = clickAction) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(value, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        }
        when {
            trailing != null -> { Spacer(Modifier.width(Spacing.sm)); trailing() }
            clickAction != null -> { Spacer(Modifier.width(Spacing.xs)); Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
internal fun KitToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        CompactSwitch(checked)
    }
}

/** Big numbers side by side, for counts and times. */
@Composable
internal fun KitStatTiles(stats: List<Pair<String, String>>) {
    Row(
        Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm).fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        stats.forEach { (value, label) ->
            Column(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(Radius.Medium)).padding(horizontal = Spacing.md, vertical = Spacing.md)) {
                Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun KitPrimaryButton(
    text: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    /** Shows a spinner in place of the icon while the action runs. */
    loading: Boolean = false,
    /** False inside a form that already has its own side padding. */
    padded: Boolean = true,
    onClick: () -> Unit,
) {
    val clickAction = rememberUiAction(onClick)
    val shape = RoundedCornerShape(Radius.Pill)
    Row(
        modifier.then(if (padded) Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm) else Modifier).fillMaxWidth().height(52.dp)
            .clip(shape).background(if (enabled) color.copy(alpha = .9f) else Color.White.copy(alpha = .08f))
            .border(.6.dp, Color.White.copy(alpha = .14f), shape)
            .clickable(enabled = enabled, onClick = clickAction),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Icon(icon, null, tint = if (enabled) Color.White else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, color = if (enabled || loading) Color.White else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun KitDangerText(text: String, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Box(Modifier.fillMaxWidth().padding(top = Spacing.md), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = com.cinetrack.ui.theme.SurfacePalette.DangerText,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).clickable(onClick = clickAction).padding(horizontal = 18.dp, vertical = Spacing.md),
        )
    }
}

/** A destructive text button that asks for confirmation before running [onConfirm]. */
@Composable
internal fun ConfirmedDangerText(text: String, title: String, body: String, onConfirm: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    KitDangerText(text) { asking = true }
    if (asking) {
        // The confirm button repeats the action's own label ("Disconnect account").
        com.cinetrack.ui.components.ConfirmPrompt(
            title = title,
            message = body,
            confirmLabel = text,
            icon = androidx.compose.material.icons.Icons.Filled.LinkOff,
            onDismiss = { asking = false },
        ) {
            asking = false
            onConfirm()
        }
    }
}

@Composable
internal fun KitFootnote(text: String) {
    Text(text, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = 2.dp))
}

@Composable
internal fun KitNotice(text: String) {
    Text(
        text,
        color = com.cinetrack.ui.theme.SurfacePalette.DangerSoftText,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs).fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Medium)).background(com.cinetrack.ui.theme.SurfacePalette.DangerContainer).padding(14.dp),
    )
}

/** All-clear or problem summary with a large icon. */
@Composable
internal fun KitStatusCard(ok: Boolean, title: String, subtitle: String) {
    val tint = if (ok) com.cinetrack.ui.theme.SurfacePalette.SuccessBright else com.cinetrack.ui.theme.SurfacePalette.WarningSoft
    Row(
        Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm).fillMaxWidth()
            .glass(RoundedCornerShape(Radius.Medium)).padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.ErrorOutline, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
    }
}

/** A short call to action, e.g. a permission that is missing. */
@Composable
internal fun KitCallout(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    val clickAction = rememberUiAction(onAction)
    Row(
        Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm).fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Medium)).background(Accent.copy(alpha = .16f))
            .border(.6.dp, Accent.copy(alpha = .35f), RoundedCornerShape(Radius.Medium)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccentLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            Text(body, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(action, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).background(Accent).clickable(onClick = clickAction).padding(horizontal = 14.dp, vertical = Spacing.sm))
    }
}

/** Instructions as short numbered steps, with an optional link underneath. */
@Composable
internal fun KitSteps(title: String, steps: List<String>, linkLabel: String? = null, onLink: () -> Unit = {}) {
    SettingsSection(title) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (linkLabel != null) {
                val clickAction = rememberUiAction(onLink)
                Row(
                    Modifier.clip(RoundedCornerShape(Radius.Pill)).clickable(onClick = clickAction).padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = AccentLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(linkLabel, color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * A credential as one row ("Saved and verified · Edit"); the form unfolds when there is
 * none yet or when editing. No characters of the saved value are ever shown.
 */
@Composable
internal fun CredentialBlock(service: String, configured: Boolean, onSave: (String, (Result<Unit>) -> Unit) -> Unit) {
    var editing by rememberSaveable(service) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val showForm = editing || !configured
    if (configured && !editing) {
        SettingsSection(stringResource(R.string.api_credential)) {
            KitRow(Icons.Filled.Key, stringResource(R.string.credential_saved_verified), trailing = {
                Text(
                    stringResource(R.string.credential_edit),
                    color = AccentLight,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).clickable { editing = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            })
        }
    }
    if (showForm) {
        ApiCredentialSettings(service, configured, onSave, compact = true, onSaved = { editing = false })
        val steps = when (service) {
            "TMDB" -> listOf(R.string.tmdb_step_1, R.string.tmdb_step_2, R.string.tmdb_step_3)
            "Fanart.tv" -> listOf(R.string.fanart_step_1, R.string.fanart_step_2, R.string.fanart_step_3)
            else -> listOf(R.string.mdblist_step_1, R.string.mdblist_step_2, R.string.mdblist_step_3)
        }.map { stringResource(it) }
        val (url, link) = when (service) {
            "TMDB" -> TMDB_API_KEY_URL to R.string.get_tmdb_api_key
            "Fanart.tv" -> FANART_API_KEY_URL to R.string.get_fanart_api_key
            else -> MDBLIST_API_KEY_URL to R.string.get_mdblist_api_key
        }
        KitSteps(stringResource(R.string.how_to_get_key), steps, stringResource(link)) {
            runCatching { uriHandler.openUri(url) }
        }
    }
}

/**
 * A credential made of two fields (TVmaze username and key, TVDB key and PIN), checked with the
 * service before it is saved. Saved, it is one row with Edit and Remove; no saved value is shown.
 */
@Composable
internal fun PairedCredentialBlock(
    service: String,
    configured: Boolean,
    first: Pair<String, Boolean>,
    second: Pair<String, Boolean>,
    steps: List<String>,
    link: Pair<String, String>,
    onSave: (String, String, (Result<Unit>) -> Unit) -> Unit,
    onRemove: () -> Unit,
) {
    var editing by rememberSaveable(service) { mutableStateOf(false) }
    var firstValue by remember(service) { mutableStateOf("") }
    var secondValue by remember(service) { mutableStateOf("") }
    var validating by remember(service) { mutableStateOf(false) }
    var error by remember(service) { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val verificationFailed = stringResource(R.string.verification_failed)
    if (configured && !editing) {
        SettingsSection(stringResource(R.string.api_credential)) {
            KitRow(Icons.Filled.Key, stringResource(R.string.credential_saved_verified), trailing = {
                Row {
                    listOf(stringResource(R.string.credential_edit) to { editing = true }, stringResource(R.string.credential_remove) to onRemove).forEach { (label, action) ->
                        Text(
                            label,
                            color = AccentLight,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).clickable(onClick = action).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            })
        }
        return
    }
    SettingsSection(stringResource(R.string.api_credential)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(Triple(first, firstValue, { value: String -> firstValue = value }), Triple(second, secondValue, { value: String -> secondValue = value }))
                .forEach { (field, value, onChange) ->
                    val (label, secret) = field
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onChange(it); error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !validating,
                        label = { Text(label) },
                        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (secret) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text,
                            autoCorrectEnabled = false,
                        ),
                        colors = credentialFieldColors(),
                    )
                }
            error?.let { message ->
                Text(
                    stringResource(R.string.api_credential_invalid, service, message),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            KitPrimaryButton(
                stringResource(R.string.save_and_verify), Icons.Filled.Key, Accent,
                enabled = !validating && firstValue.isNotBlank() && (!second.second || secondValue.isNotBlank() || service == "TVDB"),
                loading = validating, padded = false,
            ) {
                validating = true
                error = null
                onSave(firstValue, secondValue) { result ->
                    validating = false
                    result.onSuccess {
                        firstValue = ""
                        secondValue = ""
                        editing = false
                    }.onFailure { error = it.message ?: verificationFailed }
                }
            }
            if (validating) Text(stringResource(R.string.api_credential_verifying, service), color = AccentLight, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
    KitSteps(stringResource(R.string.how_to_get_key), steps, link.first) { runCatching { uriHandler.openUri(link.second) } }
}
