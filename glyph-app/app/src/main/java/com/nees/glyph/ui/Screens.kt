package com.nees.glyph.ui

import com.nees.glyph.PressEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.FlashOn
import com.nees.glyph.LevelStyle
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.mutableLongStateOf
import com.nees.glyph.Buzz
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.LockOpen
import com.nees.glyph.VizStyle
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PowerSettingsNew
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

/**
 * A large pill you drag anywhere on: the fill follows the finger, the value sits inside it, a
 * selection tick fires at every 10 %, [onPreview] runs live and [onCommit] once on release.
 */
@Composable
fun PillSlider(label: String, value: Int, range: IntRange = 0..100, onPreview: (Int) -> Unit = {}, onCommit: (Int) -> Unit) {
    val haptic = rememberHaptics()
    var current by remember { mutableIntStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    // Follow outside changes only while the finger is up, so a save can't yank the thumb mid-drag.
    LaunchedEffect(value) { if (!dragging) current = value }
    var width by remember { mutableIntStateOf(1) }
    fun at(x: Float) = (range.first + (x / width).coerceIn(0f, 1f) * (range.last - range.first)).roundToInt()
    fun update(v: Int) {
        if (v == current) return
        if (v / 10 != current / 10) haptic(Haptic.SELECT)
        current = v
        onPreview(v)
    }
    Column(Modifier.fillMaxWidth().padding(S.pad)) {
        Row {
            Text(label, style = T.body, color = C.text, modifier = Modifier.weight(1f))
            Text("$current%", style = T.body.tabular(), color = C.text2)
        }
        Spacer(Modifier.height(S.m))
        val fraction = (current - range.first).toFloat() / (range.last - range.first).coerceAtLeast(1)
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.raised)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .pointerInput(range) {
                    awaitEachGesture {
                        // Jump to the finger on touch-down, then follow every move until lift.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        down.consume()
                        update(at(down.position.x))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume() // keep the page from scrolling while adjusting
                            update(at(change.position.x))
                        }
                        dragging = false
                        onCommit(current)
                    }
                },
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.08f, 1f)).clip(RoundedCornerShape(24.dp)).background(C.accent))
            Icon(Icons.Rounded.LightMode, null, tint = if (fraction > 0.12f) C.onAccent else C.text2, modifier = Modifier.align(Alignment.CenterStart).padding(start = S.l).size(20.dp))
        }
    }
}

/**
 * Chips choosing where a level is drawn (picking one previews it at 60 %), led by a "Blink" chip
 * that switches the diagonal's blink-on-every-press on or off.
 */
@Composable
fun StylePicker(current: LevelStyle, effect: PressEffect, onEffect: (PressEffect) -> Unit, onPick: (LevelStyle) -> Unit) {
    Column(Modifier.padding(start = S.pad + S.avatar + S.m, bottom = S.m)) {
        Text("SHOW LEVEL ON", style = T.label, color = C.text3, modifier = Modifier.padding(bottom = S.s))
        LazyRow(contentPadding = PaddingValues(end = S.pad), horizontalArrangement = Arrangement.spacedBy(S.s)) {
            items(LevelStyle.entries) { st -> Chip(st.label, current == st) { onPick(st) } }
        }
        Text("⚡ EFFECT ON EACH PRESS", style = T.label, color = C.text3, modifier = Modifier.padding(top = S.m, bottom = S.s))
        LazyRow(contentPadding = PaddingValues(end = S.pad), horizontalArrangement = Arrangement.spacedBy(S.s)) {
            items(PressEffect.entries) { fx -> Chip(fx.label, effect == fx) { onEffect(fx) } }
        }
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
            // Hero: the phone back, large enough to hit each light, with whole-set controls under it.
            Card {
                Column(Modifier.fillMaxWidth().padding(vertical = S.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphDiagram(preview, Modifier.height(300.dp)) { z ->
                        preview = Engine.frame(z to 100)
                        Engine.flash(Layer.Indicator, Engine.frame(z to 100), 600)
                        tick++
                    }
                    Spacer(Modifier.height(S.l))
                    Text("Tap a light to test it", style = T.sub, color = C.text2)
                    Spacer(Modifier.height(S.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(S.s)) {
                        Chip("All on", selected = false) {
                            val all = IntArray(Zone.COUNT) { 100 }
                            preview = all; Engine.flash(Layer.Indicator, all, 1500); tick++
                        }
                        Chip("All off", selected = false) {
                            preview = IntArray(Zone.COUNT); Engine.clear(Layer.Indicator)
                        }
                    }
                }
            }
            Spacer(Modifier.height(S.s))
            Card {
                PillSlider(
                    "Effect brightness", settings.brightness, range = 1..100,
                    // Live: every glyph lights at the dragged level so you see exactly what you pick.
                    onPreview = { v ->
                        Engine.ceiling = v
                        val all = IntArray(Zone.COUNT) { 100 }
                        preview = all
                        Engine.flash(Layer.Indicator, all, 900)
                    },
                    onCommit = { v -> change { settings.brightness = v; Engine.ceiling = v }; tick++ },
                )
            }
        }
        item {
            SectionLabel("Control")
            Card {
                FeatureRow(
                    Icons.Rounded.PowerSettingsNew, "Glyph effects",
                    if (settings.paused) "Paused — only the Glyph light works" else "On",
                    !settings.paused, { v -> change { settings.paused = !v } },
                )
                Hairline(); FeatureRow(
                    Icons.Rounded.DoNotDisturbOn, "Respect Do Not Disturb",
                    "Notifications, calls and ambient stay dark during DND",
                    settings.respectDnd, { v -> change { settings.respectDnd = v } },
                )
            }
        }
        item {
            SectionLabel("Light up for")
            Card {
                FeatureRow(Icons.Rounded.Notifications, "Notifications", "Pattern per app", settings.notifications, { v -> change { settings.notifications = v } })
                Hairline(); FeatureRow(Icons.Rounded.Apps, "Choose app patterns", "${settings.appsWithPatterns().size} apps customised", onOpen = { open(Screen.Apps) })
                Hairline(); FeatureRow(Icons.Rounded.Call, "Calls", "Pattern per contact while ringing", settings.calls, { v -> change { settings.calls = v } })
                Hairline(); FeatureRow(Icons.Rounded.Contacts, "Choose contact patterns", "Default: ${settings.pattern(settings.defaultCallPattern)?.name ?: "—"}", onOpen = { open(Screen.Contacts) })
            }
        }
        item {
            SectionLabel("Indicators")
            Card {
                FeatureRow(Icons.Rounded.VolumeUp, "Volume level", "Shows on the glyphs when you press volume", settings.volumeIndicator, { v -> change { settings.volumeIndicator = v } })
                if (settings.volumeIndicator) StylePicker(settings.volumeStyle, settings.volumeEffect, { fx -> settings.volumeEffect = fx; Engine.flashWithEffect(Layer.Indicator, Engine.level(0.6f, settings.volumeStyle), 1200, fx, true) }) { st -> settings.volumeStyle = st; Engine.flash(Layer.Indicator, Engine.level(0.6f, st), 1200) }
                Hairline(); FeatureRow(Icons.Rounded.BrightnessMedium, "Brightness level", "Shows on the glyphs when brightness changes", settings.brightnessIndicator, { v -> change { settings.brightnessIndicator = v } })
                if (settings.brightnessIndicator) StylePicker(settings.brightnessStyle, settings.brightnessEffect, { fx -> settings.brightnessEffect = fx; Engine.flashWithEffect(Layer.Indicator, Engine.level(0.6f, settings.brightnessStyle), 1200, fx, true) }) { st -> settings.brightnessStyle = st; Engine.flash(Layer.Indicator, Engine.level(0.6f, st), 1200) }
                Hairline(); FeatureRow(Icons.Rounded.BatteryChargingFull, "Charging meter", "Battery level on plug-in and as it fills", settings.chargingMeter, { v -> change { settings.chargingMeter = v } })
            }
        }
        item {
            SectionLabel("Effects")
            Card {
                FeatureRow(Icons.Rounded.GraphicEq, "Music visualizer", "Glyphs move with whatever is playing", settings.visualizer, { v -> change { settings.visualizer = v } })
                Hairline(); FeatureRow(Icons.Rounded.Tune, "Visualizer style", "${settings.vizStyle.label} · sensitivity ${settings.vizGain}%", onOpen = { open(Screen.Visualizer) })
                Hairline(); FeatureRow(Icons.Rounded.Nightlight, "Ambient glow", "Ring breathes while you have unread notifications", settings.ambient, { v -> change { settings.ambient = v } })
                Hairline(); FeatureRow(Icons.Rounded.ScreenRotation, "Face-down mode", "Full brightness and ambient ring when face down", settings.faceDown, { v -> change { settings.faceDown = v } })
            }
        }
        item {
            SectionLabel("Live")
            Card {
                FeatureRow(Icons.Rounded.LockOpen, "Unlock animation", "A sweep of light runs up the back when you unlock", settings.unlockAnim, { v -> change { settings.unlockAnim = v } })
                Hairline(); FeatureRow(Icons.Rounded.Downloading, "Busy dot", "Bottom dot shows background activity", settings.busyDot, { v -> change { settings.busyDot = v } })
                if (settings.busyDot) {
                    LazyRow(
                        contentPadding = PaddingValues(start = S.pad + S.avatar + S.m, end = S.pad, bottom = S.m),
                        horizontalArrangement = Arrangement.spacedBy(S.s),
                    ) {
                        item { Chip("Downloads", settings.busyDownloads) { change { settings.busyDownloads = !settings.busyDownloads } } }
                        item { Chip("Background tasks", settings.busyOngoing) { change { settings.busyOngoing = !settings.busyOngoing } } }
                        item { Chip("Mic in use", settings.busyMic) { change { settings.busyMic = !settings.busyMic } } }
                        item { Chip("Camera in use", settings.busyCamera) { change { settings.busyCamera = !settings.busyCamera } } }
                    }
                }
                Hairline(); FeatureRow(Icons.Rounded.LinearScale, "Live progress", "Any app's progress bar shows on the strip", settings.liveProgress, { v -> change { settings.liveProgress = v } })
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

@Composable
fun PatternPicker(settings: Settings, current: String?, allowDefault: Boolean, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val options = buildList {
        if (allowDefault) add(null to "Default")
        add("off" to "Off")
        settings.allPatterns().forEach { add(it.id to it.name) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text("Pattern", style = T.headline, color = C.text) },
        text = {
            LazyColumn {
                items(options) { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).rowPress(C.raised) {
                            settings.pattern(id)?.let { Engine.play(Layer.Indicator, it) }
                            onPick(id)
                        }.padding(horizontal = S.m, vertical = S.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = T.body, color = if (id == current) C.accent else C.text, modifier = Modifier.weight(1f))
                        if (id == current) Text("Selected", style = T.caption, color = C.accent)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done", style = T.headline, color = C.accent) } },
    )
}

// ============================================================ Apps

private data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

@Composable
fun AppsScreen(settings: Settings, back: () -> Unit) {
    val ctx = LocalContext.current
    rememberSettingsVersion(settings)
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<String?>(null) }
    var pickDefault by remember { mutableStateOf(false) }
    val apps by produceState(emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val shown = apps.filter { query.isBlank() || it.label.contains(query, true) }
    fun name(id: String?) = when (id) { null -> "Default"; "off" -> "Off"; else -> settings.pattern(id)?.name ?: "Default" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = S.xxl)) {
        item {
            Header("App patterns", back)
            Card { FeatureRow(Icons.Rounded.Notifications, "Default pattern", name(settings.defaultNotifPattern), onOpen = { pickDefault = true }) }
            Spacer(Modifier.height(S.m))
            SearchBar(query, { query = it }, "Search apps")
            Spacer(Modifier.height(S.s))
        }
        itemsIndexed(shown, key = { _, a -> a.pkg }) { i, a ->
            Column(Modifier.cardSlice(i, shown.size)) {
                Row(
                    Modifier.fillMaxWidth().rowPress { picking = a.pkg }.padding(horizontal = S.pad, vertical = S.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val bmp = remember(a.pkg) { runCatching { a.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                    if (bmp != null) Image(bmp, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(S.m))
                    Text(a.label, style = T.body, color = C.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val chosen = settings.appPattern(a.pkg)
                    Text(name(chosen), style = T.sub, color = if (chosen != null) C.accent else C.text3)
                }
                if (i < shown.lastIndex) Hairline(start = S.pad + 36.dp + S.m)
            }
        }
    }
    picking?.let { pkg ->
        PatternPicker(settings, settings.appPattern(pkg), allowDefault = true, onDismiss = { picking = null }) { settings.setAppPattern(pkg, it) }
    }
    if (pickDefault) {
        PatternPicker(settings, settings.defaultNotifPattern, allowDefault = false, onDismiss = { pickDefault = false }) { settings.defaultNotifPattern = it ?: "pulse" }
    }
}

// ============================================================ Contacts

private data class ContactEntry(val key: String, val name: String)

@Composable
fun ContactsScreen(settings: Settings, back: () -> Unit) {
    val ctx = LocalContext.current
    rememberSettingsVersion(settings)
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<String?>(null) }
    var pickDefault by remember { mutableStateOf(false) }
    val contacts by produceState(emptyList<ContactEntry>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val out = ArrayList<ContactEntry>()
                ctx.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                    "${ContactsContract.Contacts.HAS_PHONE_NUMBER}=1", null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
                )?.use { c -> while (c.moveToNext()) out += ContactEntry(c.getString(0), c.getString(1) ?: "") }
                out
            }.getOrDefault(emptyList())
        }
    }
    val shown = contacts.filter { query.isBlank() || it.name.contains(query, true) }
    fun name(id: String?) = when (id) { null -> "Default"; "off" -> "Off"; else -> settings.pattern(id)?.name ?: "Default" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = S.xxl)) {
        item {
            Header("Contact patterns", back)
            Card { FeatureRow(Icons.Rounded.Call, "Default ringing pattern", name(settings.defaultCallPattern), onOpen = { pickDefault = true }) }
            Spacer(Modifier.height(S.m))
            SearchBar(query, { query = it }, "Search contacts")
            Spacer(Modifier.height(S.s))
        }
        itemsIndexed(shown, key = { _, c -> c.key }) { i, c ->
            Column(Modifier.cardSlice(i, shown.size)) {
                Row(
                    Modifier.fillMaxWidth().rowPress { picking = c.key }.padding(horizontal = S.pad, vertical = S.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.name, null, 36.dp)
                    Spacer(Modifier.width(S.m))
                    Text(c.name, style = T.body, color = C.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val chosen = settings.contactPattern(c.key)
                    Text(name(chosen), style = T.sub, color = if (chosen != null) C.accent else C.text3)
                }
                if (i < shown.lastIndex) Hairline(start = S.pad + 36.dp + S.m)
            }
        }
    }
    picking?.let { key ->
        PatternPicker(settings, settings.contactPattern(key), allowDefault = true, onDismiss = { picking = null }) { settings.setContactPattern(key, it) }
    }
    if (pickDefault) {
        PatternPicker(settings, settings.defaultCallPattern, allowDefault = false, onDismiss = { pickDefault = false }) { settings.defaultCallPattern = it ?: "comet" }
    }
}

// ============================================================ Visualizer

@Composable
fun VisualizerScreen(settings: Settings, back: () -> Unit) {
    val ctx = LocalContext.current
    rememberSettingsVersion(settings)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header("Music visualizer", back)
            Card {
                FeatureRow(Icons.Rounded.GraphicEq, "Visualizer", "Runs only while music is playing", settings.visualizer, { settings.visualizer = it; GlyphService.sync(ctx) })
                Hairline(start = S.pad)
                LevelSlider("Sensitivity", settings.vizGain) { settings.vizGain = it }
                Hairline(start = S.pad)
                FeatureRow(Icons.Rounded.Vibration, "Feel the beat", "A soft tick on each kick drum", settings.vizHaptics, { settings.vizHaptics = it; GlyphService.sync(ctx) })
            }
            SectionLabel("Style")
            Card {
                VizStyle.entries.forEachIndexed { i, st ->
                    val on = settings.vizStyle == st
                    Row(
                        Modifier.fillMaxWidth().rowPress { settings.vizStyle = st; GlyphService.sync(ctx) }
                            .padding(horizontal = S.pad, vertical = S.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(st.label, style = T.headline, color = if (on) C.accent else C.text)
                            Text(st.blurb, style = T.sub, color = C.text2)
                        }
                        if (on) Icon(Icons.Rounded.CheckCircle, "Selected", tint = C.accent, modifier = Modifier.size(22.dp))
                    }
                    if (i < VizStyle.entries.lastIndex) Hairline(start = S.pad)
                }
            }
            Spacer(Modifier.height(S.s))
            Text(
                "Uses all 15 lights. Plays only while music is playing and the screen is on.",
                style = T.caption, color = C.text3, modifier = Modifier.padding(horizontal = S.edge + S.pad),
            )
        }
    }
}

// ============================================================ Light, timer, battery

@Composable
fun LightScreen(settings: Settings, back: () -> Unit) {
    val ctx = LocalContext.current
    rememberSettingsVersion(settings)
    var torch by remember { mutableStateOf(Engine.isActive(Layer.Torch)) }
    var timerOn by remember { mutableStateOf(GlyphService.timerEnd > 0) }
    fun service(action: (GlyphService) -> Unit, fallback: String) {
        GlyphService.instance?.let(action) ?: GlyphService.start(ctx, fallback)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = S.xxl)) {
        item {
            Header("Light & timer", back)
            Card {
                FeatureRow(Icons.Rounded.FlashlightOn, "Glyph light", "Every zone as a soft fill light", torch, { on ->
                    torch = on
                    service({ it.setTorch(on) }, GlyphService.ACTION_TORCH)
                })
                Hairline(start = S.pad)
                LevelSlider("Light brightness", settings.torchLevel, 5..100) { settings.torchLevel = it; Engine.torchLevel = it }
            }
            SectionLabel("Countdown timer")
            Card {
                Text("The strip drains as time runs out, then flashes.", style = T.sub, color = C.text2, modifier = Modifier.padding(start = S.pad, end = S.pad, top = S.l))
                LazyRow(contentPadding = PaddingValues(horizontal = S.pad, vertical = S.m), horizontalArrangement = Arrangement.spacedBy(S.s)) {
                    items(listOf(1, 3, 5, 10, 15, 25, 45)) { m -> Chip("$m min", settings.timerMinutes == m) { settings.timerMinutes = m } }
                }
                FeatureRow(Icons.Rounded.Timer, if (timerOn) "Timer running" else "Start timer", "${settings.timerMinutes} minutes · also in Quick Settings", timerOn, { on ->
                    timerOn = on
                    service({ if (on) it.startTimer(settings.timerMinutes) else it.cancelTimer() }, GlyphService.ACTION_TIMER)
                })
            }
            SectionLabel("Battery")
            Card {
                FeatureRow(Icons.Rounded.BatteryChargingFull, "Show battery level", "Lights the strip for 3 seconds · also a Quick Settings tile", onOpen = {
                    service({ it.showBattery(3000) }, GlyphService.ACTION_BATTERY)
                })
            }
        }
    }
}

// ============================================================ Patterns + editor

@Composable
fun PatternsScreen(settings: Settings, back: () -> Unit) {
    rememberSettingsVersion(settings)
    var editing by remember { mutableStateOf<Pattern?>(null) }
    editing?.let { p -> EditorScreen(settings, p) { editing = null }; return }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = S.xxl)) {
        item {
            Header("Patterns", back) {
                CircleAction(Icons.Rounded.Add, "New pattern", tint = C.accent) {
                    editing = Pattern(UUID.randomUUID().toString(), "My pattern", listOf(Step.all(200, 100), Step.all(200, 0)))
                }
            }
            SectionLabel("Presets")
        }
        val presets = com.nees.glyph.Presets.all
        itemsIndexed(presets, key = { _, p -> p.id }) { i, p ->
            Column(Modifier.cardSlice(i, presets.size)) {
                PatternRow(p, onPlay = { Engine.play(Layer.Indicator, p) }, onOpen = { editing = p.copy(id = UUID.randomUUID().toString(), name = "${p.name} copy") })
                if (i < presets.lastIndex) Hairline(start = S.pad)
            }
        }
        val custom = settings.customPatterns()
        if (custom.isNotEmpty()) item { SectionLabel("Yours") }
        itemsIndexed(custom, key = { _, p -> p.id }) { i, p ->
            Column(Modifier.cardSlice(i, custom.size)) {
                PatternRow(p, onPlay = { Engine.play(Layer.Indicator, p) }, onOpen = { editing = p })
                if (i < custom.lastIndex) Hairline(start = S.pad)
            }
        }
    }
}

@Composable
private fun PatternRow(p: Pattern, onPlay: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().rowPress(onClick = onOpen).padding(start = S.pad, end = S.s, top = S.m, bottom = S.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.headline, color = C.text)
            Text("${p.steps.size} steps · ${p.duration} ms", style = T.sub.tabular(), color = C.text2)
        }
        CircleAction(Icons.Rounded.PlayArrow, "Play", tint = C.accent, onClick = onPlay)
    }
}

@Composable
fun EditorScreen(settings: Settings, initial: Pattern, close: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    val steps = remember { mutableStateListOf(*initial.steps.toTypedArray()) }
    var selected by remember { mutableIntStateOf(0) }
    val isCustom = settings.customPatterns().any { it.id == initial.id }
    // Record mode: each zone tap becomes a step; its length is the time until the next tap.
    var recording by remember { mutableStateOf(false) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    fun current() = Pattern(initial.id, name.ifBlank { "Pattern" }, steps.toList())

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Header("Edit pattern", close) {
            CircleAction(Icons.Rounded.PlayArrow, "Preview", tint = C.accent) { Engine.play(Layer.Indicator, current()) }
            if (isCustom) CircleAction(Icons.Rounded.Delete, "Delete", tint = C.red, haptic = Haptic.REJECT) { settings.deletePattern(initial.id); close() }
        }
        Card {
            Row(Modifier.padding(horizontal = S.pad, vertical = S.m), verticalAlignment = Alignment.CenterVertically) {
                Text("Name", style = T.body, color = C.text2, modifier = Modifier.width(64.dp))
                BasicTextField(name, { name = it }, singleLine = true, textStyle = T.body.copy(color = C.text), cursorBrush = SolidColor(C.accent), modifier = Modifier.weight(1f))
            }
        }
        // Step timeline.
        LazyRow(contentPadding = PaddingValues(horizontal = S.edge, vertical = S.m), horizontalArrangement = Arrangement.spacedBy(S.s)) {
            itemsIndexed(steps) { i, s ->
                Column(
                    Modifier.clip(RoundedCornerShape(Radius.tile)).background(if (i == selected) C.accentSoft else C.card)
                        .pressable(Haptic.SELECT) { selected = i }.padding(S.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlyphDiagram(s.levels, Modifier.width(44.dp))
                    Spacer(Modifier.height(S.xs))
                    Text("${s.ms} ms", style = T.caption.tabular(), color = if (i == selected) C.accent else C.text2)
                }
            }
            item {
                Box(
                    Modifier.size(width = 60.dp, height = 112.dp).clip(RoundedCornerShape(Radius.tile)).background(C.card)
                        .pressable(Haptic.SELECT) { steps.add(steps.getOrNull(selected) ?: Step.all(200, 0)); selected = steps.lastIndex },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Add step", tint = C.accent) }
            }
        }
        val step = steps.getOrNull(selected)
        if (step != null) {
            var level by remember(selected) { mutableFloatStateOf(step.levels.maxOrNull()?.takeIf { it > 0 }?.toFloat() ?: 100f) }
            Card(Modifier.weight(1f)) {
                Row(Modifier.padding(S.pad), verticalAlignment = Alignment.CenterVertically) {
                    GlyphDiagram(if (recording) IntArray(Zone.COUNT) else step.levels, Modifier.width(120.dp)) { z ->
                        if (recording) {
                            val now = android.os.SystemClock.uptimeMillis()
                            if (lastTapAt == 0L) steps.clear()
                            else if (steps.isNotEmpty()) steps[steps.lastIndex] = steps.last().copy(ms = (now - lastTapAt).toInt().coerceIn(40, 1500))
                            steps.add(Step.of(250, z to level.toInt()).vibe(Buzz.TICK))
                            lastTapAt = now
                            selected = steps.lastIndex
                            Engine.flash(Layer.Indicator, Engine.frame(z to 100), 300)
                            Buzz.fire(Buzz.TICK, android.os.VibrationAttributes.USAGE_TOUCH)
                        } else {
                            val l = step.levels.copyOf()
                            l[z.ordinal] = if (l[z.ordinal] > 0) 0 else level.toInt()
                            steps[selected] = step.copy(levels = l)
                            Engine.flash(Layer.Indicator, l, 500)
                        }
                    }
                    Spacer(Modifier.width(S.l))
                    Column(Modifier.weight(1f)) {
                        Text(if (recording) "Recording" else "Step ${selected + 1}", style = T.headline, color = if (recording) C.accent else C.text)
                        Text(
                            if (recording) "Tap lights in rhythm. Each tap is a step; the gap sets its length." else "Tap zones to switch them on or off.",
                            style = T.sub, color = C.text2,
                        )
                        Spacer(Modifier.height(S.s))
                        Chip(if (recording) "Stop recording" else "Record taps", recording) {
                            recording = !recording
                            lastTapAt = 0L
                        }
                        Spacer(Modifier.height(S.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(S.s)) {
                            Chip("All", false) { steps[selected] = Step.all(step.ms, level.toInt()) }
                            Chip("None", false) { steps[selected] = Step.all(step.ms, 0) }
                        }
                        if (steps.size > 1) {
                            Spacer(Modifier.height(S.s))
                            Chip("Delete step", false) { steps.removeAt(selected); selected = (selected - 1).coerceAtLeast(0) }
                        }
                    }
                }
                Text("Vibration", style = T.body, color = C.text, modifier = Modifier.padding(start = S.pad, top = S.s))
                Row(Modifier.padding(horizontal = S.pad, vertical = S.s), horizontalArrangement = Arrangement.spacedBy(S.s)) {
                    Buzz.labels.forEachIndexed { b, label ->
                        Chip(label, step.buzz == b) {
                            steps[selected] = step.vibe(b)
                            Buzz.fire(b, android.os.VibrationAttributes.USAGE_TOUCH)
                        }
                    }
                }
                LevelSlider("Brightness of new zones", level.toInt()) { level = it.toFloat() }
                LevelSlider("Duration", step.ms, 40..1500, " ms") { steps[selected] = step.copy(ms = it) }
            }
        }
        Spacer(Modifier.height(S.m))
        Box(
            Modifier.padding(horizontal = S.edge).fillMaxWidth().height(52.dp).clip(CircleShape).background(C.accent)
                .pressable(Haptic.SUCCESS) { settings.savePattern(current()); close() },
            contentAlignment = Alignment.Center,
        ) { Text("Save pattern", style = T.headline, color = C.onAccent) }
        Spacer(Modifier.height(S.m))
    }
}
