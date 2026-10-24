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

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)) {
        SettingsGroupLabel(title)
        Spacer(Modifier.height(Spacing.sm))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(vertical = 4.dp)) { content() }
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
private fun CompactSwitch(checked: Boolean) {
    val knobOffset by animateDpAsState(if (checked) 22.dp else 3.dp, label = "switchKnob")
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
        Spacer(Modifier.width(8.dp))
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
        "trakt" -> Triple(R.drawable.ic_service_trakt, androidx.compose.ui.graphics.Color(0xFFED1C24), 26.dp)
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
    SettingsPages.ServiceTrakt -> ServiceBrand("Trakt", Brush.linearGradient(listOf(Color(0xFF7D2C92), Color(0xFFC2185B), Color(0xFFED1C24))), Color(0xFFC2185B), R.drawable.ic_service_trakt)
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
        "Trakt" -> state.traktConfigured
        else -> state.mdbListApiConfigured
    }
    val status = when {
        brand.name == "Simkl" && active && !state.simklUsername.isNullOrBlank() -> stringResource(R.string.simkl_connected_as, state.simklUsername.orEmpty())
        brand.name == "Floppy" && active -> floppyConnectedLabel(state)
        brand.name in setOf("Simkl", "Floppy") -> stringResource(if (active) R.string.connected else R.string.not_connected)
        else -> stringResource(if (active) R.string.configured else R.string.not_configured)
    }
    val description = stringResource(
        when (brand.name) {
            "TMDB" -> R.string.tmdb_description
            "Simkl" -> R.string.simkl_description
            "Floppy" -> R.string.floppy_self_hosted_description
            "Trakt" -> R.string.trakt_description
            else -> R.string.mdblist_description
        },
    )
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp).fillMaxWidth().clip(shape)
            .background(brand.brush).border(.6.dp, Color.White.copy(alpha = .10f), shape),
    ) {
        Image(
            painterResource(brand.logo), null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 18.dp).size(96.dp)
                .clip(RoundedCornerShape(26.dp)).graphicsLayer { alpha = .14f },
        )
        Column(Modifier.padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 20.dp)) {
            GlassBackButton(onClick = onBack)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(start = 8.dp)) {
                if (brand.wordmark != null) {
                    Image(painterResource(brand.wordmark), brand.name, modifier = Modifier.height(46.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(brand.name, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(brand.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                KitStatusPill(active, status, Modifier.padding(top = 12.dp))
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
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        }
        when {
            trailing != null -> { Spacer(Modifier.width(8.dp)); trailing() }
            clickAction != null -> { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
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
        Spacer(Modifier.width(12.dp))
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.forEach { (value, label) ->
            Column(Modifier.weight(1f).fillMaxHeight().glass(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(value, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun KitPrimaryButton(text: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    val shape = RoundedCornerShape(Radius.Pill)
    Row(
        Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm).fillMaxWidth().height(52.dp)
            .clip(shape).background(if (enabled) color.copy(alpha = .9f) else Color.White.copy(alpha = .08f))
            .border(.6.dp, Color.White.copy(alpha = .14f), shape)
            .clickable(enabled = enabled, onClick = clickAction),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (enabled) Color.White else TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun KitDangerText(text: String, onClick: () -> Unit) {
    val clickAction = rememberUiAction(onClick)
    Box(Modifier.fillMaxWidth().padding(top = Spacing.md), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = Color(0xFFF09595),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).clickable(onClick = clickAction).padding(horizontal = 18.dp, vertical = 12.dp),
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
        color = Color(0xFFF7C1C1),
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(Color(0x33E24B4A)).padding(14.dp),
    )
}

/** All-clear or problem summary with a large icon. */
@Composable
internal fun KitStatusCard(ok: Boolean, title: String, subtitle: String) {
    val tint = if (ok) Color(0xFF5DCAA5) else Color(0xFFF0997B)
    Row(
        Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm).fillMaxWidth()
            .glass(RoundedCornerShape(20.dp)).padding(16.dp),
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
            .clip(RoundedCornerShape(20.dp)).background(Accent.copy(alpha = .16f))
            .border(.6.dp, Accent.copy(alpha = .35f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccentLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            Text(body, color = TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(8.dp))
        Text(action, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clip(RoundedCornerShape(Radius.Pill)).background(Accent).clickable(onClick = clickAction).padding(horizontal = 14.dp, vertical = 8.dp))
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
        SettingsSection(stringResource(if (service == "Trakt") R.string.client_id_label else R.string.api_credential)) {
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
            "Trakt" -> listOf(R.string.trakt_step_1, R.string.trakt_step_2, R.string.trakt_step_3)
            else -> listOf(R.string.mdblist_step_1, R.string.mdblist_step_2, R.string.mdblist_step_3)
        }.map { stringResource(it) }
        val (url, link) = when (service) {
            "TMDB" -> TMDB_API_KEY_URL to R.string.get_tmdb_api_key
            "Trakt" -> TRAKT_API_APPS_URL to R.string.get_trakt_client_id
            else -> MDBLIST_API_KEY_URL to R.string.get_mdblist_api_key
        }
        KitSteps(stringResource(if (service == "Trakt") R.string.how_to_get_client_id else R.string.how_to_get_key), steps, stringResource(link)) {
            runCatching { uriHandler.openUri(url) }
        }
    }
}
