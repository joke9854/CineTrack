package com.cinetrack.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.ui.theme.Accent
import java.util.Locale

/** One slider: a label, its range and how a value is read from and written to the settings. */
private class LabControl<P>(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (P) -> Float,
    val put: (P, Float) -> P,
)

private val GlassControls = listOf<LabControl<GlassParams>>(
    LabControl("Tint strength ×", 0f..2.5f, { it.tint }, { p, v -> p.copy(tint = v) }),
    LabControl("Refraction band (dp)", 0f..30f, { it.refraction }, { p, v -> p.copy(refraction = v) }),
    LabControl("Bend distance (× band)", 0f..4f, { it.displacement }, { p, v -> p.copy(displacement = v) }),
    LabControl("Refraction strength", 0f..1f, { it.strength }, { p, v -> p.copy(strength = v) }),
    LabControl("Frost / blur (dp)", 0f..40f, { it.blur }, { p, v -> p.copy(blur = v) }),
    LabControl("Depth (0 sharp, 1 frosted)", 0f..1f, { it.depth }, { p, v -> p.copy(depth = v) }),
    LabControl("Rim highlight", 0f..1f, { it.specular }, { p, v -> p.copy(specular = v) }),
    LabControl("Highlight sharpness", 1f..64f, { it.specularExponent }, { p, v -> p.copy(specularExponent = v) }),
    LabControl("Dark edge", 0f..0.6f, { it.edgeShadow }, { p, v -> p.copy(edgeShadow = v) }),
    LabControl("Edge glow", 0f..1f, { it.ambient }, { p, v -> p.copy(ambient = v) }),
    LabControl("Edge glow falloff", .5f..8f, { it.fresnel }, { p, v -> p.copy(fresnel = v) }),
    LabControl("Edge softness (dp)", 0f..8f, { it.edgeSoftness }, { p, v -> p.copy(edgeSoftness = v) }),
    LabControl("Light position (-1 top, 1 bottom)", -1f..1f, { it.light }, { p, v -> p.copy(light = v) }),
    LabControl("White point", -1f..1f, { it.whitePoint }, { p, v -> p.copy(whitePoint = v) }),
    LabControl("Contrast", -1f..1f, { it.contrast }, { p, v -> p.copy(contrast = v) }),
    LabControl("Saturation", 0f..2f, { it.chroma }, { p, v -> p.copy(chroma = v) }),
    LabControl("Colour fringe", 0f..1f, { it.dispersion }, { p, v -> p.copy(dispersion = v) }),
    LabControl("Shadow lift (dp)", 0f..16f, { it.lift }, { p, v -> p.copy(lift = v) }),
    LabControl("Quality (0 fast, 1 sharp)", 0f..1f, { it.quality }, { p, v -> p.copy(quality = v) }),
)

private val BlurControls = listOf<LabControl<BlurParams>>(
    LabControl("Blur radius (dp)", 0f..60f, { it.radius }, { p, v -> p.copy(radius = v) }),
    LabControl("Tint alpha", 0f..1f, { it.tint }, { p, v -> p.copy(tint = v) }),
    LabControl("Grain", 0f..0.3f, { it.noise }, { p, v -> p.copy(noise = v) }),
    LabControl("Quality (0 fast, 1 sharp)", 0f..1f, { it.quality }, { p, v -> p.copy(quality = v) }),
)

private val LabPanel = Color(0xF20E1014)
private val LabShape = RoundedCornerShape(22.dp)

/**
 * Glass lab (debug and preview builds only): a floating panel over the real app with a
 * slider for every Haze setting of every glass surface. Changes apply live and are kept
 * across restarts; Copy puts the changed values on the clipboard.
 */
@Composable
internal fun GlassLabHost() {
    if (!GlassTuning.available) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { GlassTuning.load(context) }
    var open by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var atTop by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var selected by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        if (!open) {
            Text(
                "Glass",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(Accent.copy(alpha = .85f))
                    .clickable { open = true }
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                        .then(if (atTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                        .padding(8.dp)
                        .fillMaxWidth()
                        .height(maxHeight * .48f)
                        .clip(LabShape)
                        .background(LabPanel)
                        .border(.6.dp, Color.White.copy(alpha = .12f), LabShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    LabHeader(
                        atTop = atTop,
                        onMove = { atTop = !atTop },
                        onCopy = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("CineTrack glass lab", GlassTuning.export()))
                            Toast.makeText(context, "Glass settings copied", Toast.LENGTH_SHORT).show()
                        },
                        onResetAll = { GlassTuning.resetAll(context) },
                        onClose = { open = false },
                    )
                    SurfaceChips(selected) { selected = it }
                    val glassCount = GlassSurface.entries.size
                    if (selected < glassCount) {
                        val surface = GlassSurface.entries[selected]
                        val params = GlassTuning.params(surface)
                        LabControls(
                            controls = GlassControls,
                            params = params,
                            changed = params != GlassTuning.defaults(surface),
                            onChange = { GlassTuning.set(context, surface, it) },
                            onReset = { GlassTuning.set(context, surface, GlassTuning.defaults(surface)) },
                        )
                    } else {
                        val surface = BlurSurface.entries[selected - glassCount]
                        val params = GlassTuning.params(surface)
                        LabControls(
                            controls = BlurControls,
                            params = params,
                            changed = params != GlassTuning.defaults(surface),
                            onChange = { GlassTuning.set(context, surface, it) },
                            onReset = { GlassTuning.set(context, surface, GlassTuning.defaults(surface)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabHeader(atTop: Boolean, onMove: () -> Unit, onCopy: () -> Unit, onResetAll: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Glass lab", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        LabButton(if (atTop) "Move down" else "Move up", onMove)
        LabButton("Copy", onCopy)
        LabButton("Reset all", onResetAll)
        LabButton("Close", onClose)
    }
}

@Composable
private fun LabButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = .10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

@Composable
private fun SurfaceChips(selected: Int, onSelect: (Int) -> Unit) {
    val labels = GlassSurface.entries.map { it.label } + BlurSurface.entries.map { it.label }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                label,
                color = if (on) Color.White else Color.White.copy(alpha = .70f),
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Accent.copy(alpha = .45f) else Color.White.copy(alpha = .07f))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun <P> LabControls(
    controls: List<LabControl<P>>,
    params: P,
    changed: Boolean,
    onChange: (P) -> Unit,
    onReset: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(controls, key = { it.label }) { control ->
            val value = control.get(params)
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(control.label, color = Color.White.copy(alpha = .85f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.ROOT, "%.2f", value), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = value.coerceIn(control.range),
                    onValueChange = { onChange(control.put(params, it)) },
                    valueRange = control.range,
                    modifier = Modifier.height(28.dp),
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                if (changed) Text("Changed", color = Accent, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp).align(Alignment.CenterVertically))
                LabButton("Reset this surface", onReset)
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}
