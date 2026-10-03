package com.nees.glyph.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nees.glyph.Engine
import com.nees.glyph.GlyphService
import com.nees.glyph.Layer
import com.nees.glyph.Pattern
import com.nees.glyph.Settings
import com.nees.glyph.Step
import com.nees.glyph.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { Home, Apps, Contacts, Visualizer, Light, Patterns }

// ============================================================ Shared bits

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
    Row(Modifier.fillMaxWidth().padding(start = S.edge, end = S.edge, top = S.m, bottom = S.s), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(S.m))
            Text(title, style = T.title, color = C.text, modifier = Modifier.weight(1f))
        } else {
            Text(title, style = T.display, color = C.text, modifier = Modifier.weight(1f).padding(start = S.xs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(S.s)) { actions() }
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = rememberHaptics()
    Switch(
        checked, { haptic(Haptic.SELECT); onChange(it) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = C.onAccent, checkedTrackColor = C.accent,
            uncheckedThumbColor = C.text2, uncheckedTrackColor = C.raised, uncheckedBorderColor = C.raised,
        ),
    )
}

@Composable
fun LevelSlider(label: String, value: Int, range: IntRange = 0..100, suffix: String = "%", onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = S.pad, vertical = S.m)) {
        Row {
            Text(label, style = T.body, color = C.text, modifier = Modifier.weight(1f))
            Text("$value$suffix", style = T.body.tabular(), color = C.text2)
        }
        Slider(
            value.toFloat(), { onChange(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.raised),
        )
    }
}

/** Card row: icon tile, title + subtitle, then a switch and/or chevron. */
@Composable
fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean? = null,
    onCheck: ((Boolean) -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.rowPress(onClick = onOpen) else Modifier)
            .padding(start = S.pad, end = S.m, top = S.m, bottom = S.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(S.avatar).clip(RoundedCornerShape(14.dp)).background(C.raised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (checked == true) C.accent else C.text2, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(S.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.headline, color = C.text)
            Text(subtitle, style = T.sub, color = C.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (checked != null && onCheck != null) Toggle(checked, onCheck)
        if (onOpen != null && checked == null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.text3)
    }
}

/** Live settings snapshot that re-reads whenever any preference changes. */
@Composable
fun rememberSettingsVersion(settings: Settings): Int {
    var v by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(settings) {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> v++ }
        settings.listen(l)
        onDispose { settings.unlisten(l) }
    }
    return v
}

// ============================================================ Home

@Composable
fun HomeScreen(settings: Settings, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    rememberSettingsVersion(settings)
    var preview by remember { mutableStateOf(IntArray(Zone.COUNT)) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { if (tick > 0) { delay(600); preview = IntArray(Zone.COUNT) } }

    fun change(block: () -> Unit) { block(); GlyphService.sync(ctx) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = S.xxl)) {
        item {
            Header("Glyph") { CircleAction(Icons.Rounded.Tune, "Patterns", tint = C.accent) { open(Screen.Patterns) } }
            Card {
                Row(Modifier.padding(S.pad), verticalAlignment = Alignment.CenterVertically) {
                    GlyphDiagram(preview, Modifier.width(112.dp)) { z ->
                        preview = Engine.frame(z to 100)
                        Engine.flash(Layer.Indicator, Engine.frame(z to 100), 600)
                        tick++
                    }
                    Spacer(Modifier.width(S.l))
                    Column {
                        Text("15 zones", style = T.title, color = C.text)
                        Spacer(Modifier.height(S.xs))
                        Text("Tap a zone to light it on the back of your phone.", style = T.sub, color = C.text2)
                    }
                }
            }
            Spacer(Modifier.height(S.s))
            Card { LevelSlider("Glyph brightness", settings.brightness) { change { settings.brightness = it; Engine.ceiling = it } } }
        }
        item {
            SectionLabel("Light up for")
            Card {
                FeatureRow(Icons.Rounded.Notifications, "Notifications", "Pattern per app", settings.notifications, { v -> change { settings.notifications = v } })
                Hairline(); FeatureRow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Choose app patterns", "${settings.appsWithPatterns().size} apps customised", onOpen = { open(Screen.Apps) })
                Hairline(); FeatureRow(Icons.Rounded.Call, "Calls", "Pattern per contact while ringing", settings.calls, { v -> change { settings.calls = v } })
                Hairline(); FeatureRow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Choose contact patterns", "Default: ${settings.pattern(settings.defaultCallPattern)?.name ?: "—"}", onOpen = { open(Screen.Contacts) })
            }
        }
        item {
            SectionLabel("Indicators")
            Card {
                FeatureRow(Icons.Rounded.VolumeUp, "Volume level", "Shows on the strip when you press volume", settings.volumeIndicator, { v -> change { settings.volumeIndicator = v } })
                Hairline(); FeatureRow(Icons.Rounded.BrightnessMedium, "Brightness level", "Shows on the strip when brightness changes", settings.brightnessIndicator, { v -> change { settings.brightnessIndicator = v } })
                Hairline(); FeatureRow(Icons.Rounded.BatteryChargingFull, "Charging meter", "Battery level on plug-in and as it fills", settings.chargingMeter, { v -> change { settings.chargingMeter = v } })
            }
        }
        item {
            SectionLabel("Effects")
            Card {
                FeatureRow(Icons.Rounded.GraphicEq, "Music visualizer", "Glyphs move with whatever is playing", settings.visualizer, { v -> change { settings.visualizer = v } })
                Hairline(); FeatureRow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Visualizer settings", "Sensitivity ${settings.vizGain}%", onOpen = { open(Screen.Visualizer) })
                Hairline(); FeatureRow(Icons.Rounded.Nightlight, "Ambient glow", "Ring breathes while you have unread notifications", settings.ambient, { v -> change { settings.ambient = v } })
                Hairline(); FeatureRow(Icons.Rounded.ScreenRotation, "Face-down mode", "Full brightness and ambient ring when face down", settings.faceDown, { v -> change { settings.faceDown = v } })
            }
        }
        item {
            SectionLabel("Tools")
            Card {
                FeatureRow(Icons.Rounded.FlashlightOn, "Glyph light & timer", "Adjustable light, countdown timer, battery check", onOpen = { open(Screen.Light) })
            }
        }
    }
}

// ============================================================ Pattern picking

