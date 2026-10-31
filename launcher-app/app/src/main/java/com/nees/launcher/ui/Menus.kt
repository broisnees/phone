package com.nees.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nees.launcher.AppInfo
import com.nees.launcher.AppRepo
import com.nees.launcher.Fav
import com.nees.launcher.ClockStyle
import com.nees.launcher.LauncherState

@Composable
private fun MenuSheet(onDismiss: () -> Unit, title: String?, content: @Composable () -> Unit) {
    Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().background(C.bg).padding(S.s)) {
            if (title != null) Text(title, style = T.title, color = C.text, modifier = Modifier.padding(horizontal = S.l, vertical = S.m), maxLines = 1, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(label, style = T.list, color = C.text, modifier = Modifier.fillMaxWidth().tappable(Haptic.LIGHT, onClick = onClick).padding(horizontal = S.l, vertical = 14.dp))
}

/** Long-press on a home favourite. */
@Composable
fun FavMenu(state: LauncherState, fav: Fav, onDismiss: () -> Unit, onRename: (Fav) -> Unit) {
    MenuSheet(onDismiss, state.label(fav)) {
        MenuItem("Rename") { onRename(fav); onDismiss() }
        MenuItem("Unpin from home") { state.removeFav(fav.key); onDismiss() }
        MenuItem("Hide app") { state.hide(fav.key); onDismiss() }
        MenuItem("App info") { AppRepo.appInfo(fav.key); onDismiss() }
    }
}

/** Long-press on a drawer app. */
@Composable
fun DrawerAppMenu(state: LauncherState, app: AppInfo, onDismiss: () -> Unit) {
    val isFav = state.isFav(app.key)
    MenuSheet(onDismiss, app.label) {
        if (isFav) MenuItem("Unpin from home") { state.removeFav(app.key); onDismiss() }
        else MenuItem("Pin to home") { state.addFav(app.key); onDismiss() }
        MenuItem("Hide app") { state.hide(app.key); onDismiss() }
        MenuItem("App info") { AppRepo.appInfo(app.key); onDismiss() }
    }
}

@Composable
fun RenameDialog(state: LauncherState, fav: Fav, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(state.label(fav)) }
    Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().background(C.bg).padding(S.xl)) {
            Text("Rename", style = T.label, color = C.text3)
            Spacer(Modifier.height(S.m))
            BasicTextField(
                name, { name = it }, singleLine = true,
                textStyle = T.title.copy(color = C.text), cursorBrush = SolidColor(C.text), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(S.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Reset", style = T.body, color = C.text2, modifier = Modifier.tappable(Haptic.SELECT) { state.rename(fav.key, ""); onDismiss() })
                Spacer(Modifier.width(S.xl))
                Text("Save", style = T.body, color = C.text, modifier = Modifier.tappable(Haptic.SUCCESS) { state.rename(fav.key, name); onDismiss() })
            }
        }
    }
}

/** Long-press on empty home space. */
@Composable
fun HomeMenu(onAddWidget: () -> Unit, onWallpaper: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    MenuSheet(onDismiss, null) {
        MenuItem("Add widget") { onAddWidget(); onDismiss() }
        MenuItem("Wallpaper") { onWallpaper(); onDismiss() }
        MenuItem("Settings") { onSettings(); onDismiss() }
    }
}

/** Pick a third-party widget to bind (secondary feature). */
@Composable
fun WidgetPicker(state: LauncherState, onPick: (android.appwidget.AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val providers = remember {
        runCatching { com.nees.launcher.App.instance.widgetManager.installedProviders }.getOrDefault(emptyList())
            .sortedBy { it.loadLabel(state.ctx.packageManager).toString().lowercase() }
    }
    Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().background(C.bg).padding(S.m)) {
            Text("Widgets", style = T.title, color = C.text, modifier = Modifier.padding(S.m))
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(providers, key = { it.provider.flattenToString() }) { p ->
                    Text(
                        p.loadLabel(state.ctx.packageManager).toString(),
                        style = T.list, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().tappable(Haptic.LIGHT) { onPick(p) }.padding(horizontal = S.m, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/** Full-screen launcher settings: toggles, favourites, hidden apps, widgets. */
@Composable
fun SettingsScreen(state: LauncherState, onAddWidget: () -> Unit, onClose: () -> Unit) {
    val store = state.store
    LazyColumn(Modifier.fillMaxSize().background(C.bg).statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = S.edge, vertical = S.l)) {
        item {
            Text("Settings", style = T.display, color = C.text)
            Spacer(Modifier.height(S.l))
            Section("Home")
            Toggle("Clock", store.showClock) { store.showClock = it }
            ClockStylePicker(state)
            Toggle("Date", store.showDate) { store.showDate = it }
            Toggle("Battery", store.showBattery) { store.showBattery = it }
            Toggle("Next alarm", store.showAlarm) { store.showAlarm = it }
            Toggle("Claude usage", store.showClaude) { store.showClaude = it }
            Toggle("Show wallpaper", store.showWallpaper) { store.showWallpaper = it }
            Spacer(Modifier.height(S.l))
            Section("Gestures")
            Toggle("Double-tap to lock", store.doubleTapLock) { store.doubleTapLock = it }
            Toggle("Swipe down for notifications", store.swipeDownNotifications) { store.swipeDownNotifications = it }
            Toggle("Glyph sweep on open", store.glyphOnOpen) { store.glyphOnOpen = it }
            Spacer(Modifier.height(S.l))
            Section("Widgets")
            MenuItem("Add a widget") { onAddWidget() }
            Spacer(Modifier.height(S.l))
            Section("Favourites")
        }
        itemsIndexedFavs(state)
        item {
            Spacer(Modifier.height(S.l))
            Section("Hidden apps")
        }
        items(state.hidden.value.toList(), key = { it }) { key ->
            val label = AppRepo.find(key)?.label ?: key
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = T.list, color = C.text2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Unhide", style = T.body, color = C.text, modifier = Modifier.tappable(Haptic.SELECT) { state.unhide(key) })
            }
        }
        item {
            if (state.hidden.value.isEmpty()) Text("Nothing hidden", style = T.body, color = C.text3, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(S.xxl))
            Text("Close", style = T.title, color = C.text, modifier = Modifier.fillMaxWidth().tappable(Haptic.SELECT, onClick = onClose).padding(vertical = S.m))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedFavs(state: LauncherState) {
    items(state.favourites.toList(), key = { it.key }) { fav ->
        val idx = state.favourites.indexOfFirst { it.key == fav.key }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.label(fav), style = T.list, color = C.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("↑", style = T.title, color = C.text2, modifier = Modifier.tappable(Haptic.SELECT) { if (idx > 0) state.moveFav(idx, idx - 1) }.padding(horizontal = S.s))
            Text("↓", style = T.title, color = C.text2, modifier = Modifier.tappable(Haptic.SELECT) { if (idx < state.favourites.lastIndex) state.moveFav(idx, idx + 1) }.padding(horizontal = S.s))
            Text("✕", style = T.title, color = C.text2, modifier = Modifier.tappable(Haptic.SELECT) { state.removeFav(fav.key) }.padding(horizontal = S.s))
        }
    }
}

@Composable
private fun ClockStylePicker(state: LauncherState) {
    var style by remember { mutableStateOf(state.store.clockStyle) }
    Row(Modifier.fillMaxWidth().padding(start = S.m, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Clock style", style = T.body, color = C.text2, modifier = Modifier.weight(1f))
        ClockStyle.entries.forEach { opt ->
            val on = style == opt
            Text(
                opt.name,
                style = T.body,
                color = if (on) C.text else C.text3,
                modifier = Modifier.tappable(Haptic.SELECT) { style = opt; state.store.clockStyle = opt }.padding(horizontal = S.s),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = T.label, color = C.text3, modifier = Modifier.padding(bottom = S.s, top = S.s))
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var c by remember { mutableStateOf(checked) }
    val h = rememberHaptics()
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.list, color = C.text, modifier = Modifier.weight(1f))
        Switch(c, { h(Haptic.SELECT); c = it; onChange(it) }, colors = SwitchDefaults.colors(
            checkedThumbColor = C.bg, checkedTrackColor = C.text,
            uncheckedThumbColor = C.text3, uncheckedTrackColor = C.bg, uncheckedBorderColor = C.text3,
        ))
    }
}
