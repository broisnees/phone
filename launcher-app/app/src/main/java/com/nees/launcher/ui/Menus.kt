package com.nees.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nees.launcher.AppRepo
import com.nees.launcher.IconPack
import com.nees.launcher.Item
import com.nees.launcher.ItemType
import com.nees.launcher.LauncherState

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(C.raised).padding(S.s),
        ) { content() }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = C.text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).pressable(Haptic.LIGHT, onClick = onClick).padding(horizontal = S.m, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(S.m))
        Text(label, style = T.body, color = tint)
    }
}

/** Long-press menu for a home/dock item. */
@Composable
fun ItemSheet(
    state: LauncherState,
    item: Item,
    onDismiss: () -> Unit,
    onOpenFolder: (Item) -> Unit,
    onResizeWidget: (Item) -> Unit,
) {
    Sheet(onDismiss) {
        when (item.type) {
            ItemType.FOLDER -> {
                Action(Icons.Rounded.Add, "Open folder") { onOpenFolder(item); onDismiss() }
                Action(Icons.Rounded.Delete, "Remove", C.accent) { state.remove(item); onDismiss() }
            }
            ItemType.WIDGET -> {
                Action(Icons.Rounded.Widgets, "Resize") { onResizeWidget(item); onDismiss() }
                Action(Icons.Rounded.Delete, "Remove", C.accent) { state.remove(item); onDismiss() }
            }
            ItemType.APP -> {
                Action(Icons.Rounded.Info, "App info") { AppRepo.appInfo(state.ctx, item.pkg); onDismiss() }
                Action(Icons.Rounded.Delete, "Remove", C.accent) { state.remove(item); onDismiss() }
            }
        }
    }
}

/** Long-press on empty space. */
@Composable
fun HomeMenu(onWidgets: () -> Unit, onWallpaper: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        Action(Icons.Rounded.Widgets, "Widgets") { onWidgets(); onDismiss() }
        Action(Icons.Rounded.Wallpaper, "Wallpaper") { onWallpaper(); onDismiss() }
        Action(Icons.Rounded.Settings, "Launcher settings") { onSettings(); onDismiss() }
    }
}

/** Folder contents, with rename. */
@Composable
fun FolderPopup(state: LauncherState, folder: Item, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(folder.label) }
    Dialog(onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(C.raised).padding(S.l),
        ) {
            BasicTextField(
                name, { name = it; state.renameFolder(folder, it) }, singleLine = true,
                textStyle = T.title.copy(color = C.text), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(S.m))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 320.dp)) {
                items(folder.children.toList(), key = { it }) { key ->
                    val app = AppRepo.find(key)
                    Column(
                        Modifier.padding(8.dp).pressable(Haptic.LIGHT, onLongClick = { state.removeFromFolder(folder, key); if (folder.children.size <= 1) onDismiss() }) {
                            AppRepo.launch(state.ctx, key); onDismiss()
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(key, 48.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(app?.label ?: "", style = T.iconLabel, color = C.text, maxLines = 1)
                    }
                }
            }
            Text("Long-press to remove from folder", style = T.caption, color = C.text3, modifier = Modifier.padding(top = S.s))
        }
    }
}

/** Pick an installed widget provider. The caller handles binding + placing. */
@Composable
fun WidgetPicker(state: LauncherState, onPick: (android.appwidget.AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val providers = remember {
        runCatching { com.nees.launcher.App.instance.widgetManager.installedProviders }.getOrDefault(emptyList())
            .sortedBy { it.loadLabel(state.ctx.packageManager).toString().lowercase() }
    }
    Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(C.raised).padding(S.m)) {
            Text("Widgets", style = T.title, color = C.text, modifier = Modifier.padding(S.s))
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(providers, key = { it.provider.flattenToString() }) { p ->
                    val label = p.loadLabel(state.ctx.packageManager).toString()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).pressable(Haptic.LIGHT) { onPick(p) }.padding(S.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = T.body, color = C.text, maxLines = 1)
                            Text(providerApp(state, p), style = T.caption, color = C.text2, maxLines = 1)
                        }
                        Text("${p.minWidth / 70 + 1}×${p.minHeight / 70 + 1}", style = T.caption, color = C.text3)
                    }
                }
            }
        }
    }
}

private fun providerApp(state: LauncherState, p: android.appwidget.AppWidgetProviderInfo): String =
    runCatching { state.ctx.packageManager.getApplicationLabel(state.ctx.packageManager.getApplicationInfo(p.provider.packageName, 0)).toString() }
        .getOrDefault(p.provider.packageName)

/** Launcher settings. */
@Composable
fun SettingsSheet(state: LauncherState, onDismiss: () -> Unit) {
    val store = state.store
    var dtLock by remember { mutableStateOf(store.doubleTapLock) }
    var swDown by remember { mutableStateOf(store.swipeDownNotifications) }
    var glyph by remember { mutableStateOf(store.glyphOnOpen) }
    var pack by remember { mutableStateOf(store.iconPack) }
    val packs = remember { IconPack.installed(state.ctx) }

    Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(C.raised).padding(S.l)) {
            Text("Launcher", style = T.title, color = C.text)
            Spacer(Modifier.height(S.m))
            Toggle("Double-tap to lock", dtLock) { dtLock = it; store.doubleTapLock = it }
            Toggle("Swipe down for notifications", swDown) { swDown = it; store.swipeDownNotifications = it }
            Toggle("Glyph sweep on unlock", glyph) { glyph = it; store.glyphOnOpen = it }
            Spacer(Modifier.height(S.m))
            Text("ICON PACK", style = T.label, color = C.text3)
            Spacer(Modifier.height(S.s))
            IconPackRow("System", pack.isEmpty()) { pack = ""; store.iconPack = ""; AppRepo.setIconPack("") }
            packs.forEach { (p, label) ->
                IconPackRow(label, pack == p) { pack = p; store.iconPack = p; AppRepo.setIconPack(p) }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val h = rememberHaptics()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body, color = C.text, modifier = Modifier.weight(1f))
        Switch(checked, { h(Haptic.SELECT); onChange(it) }, colors = SwitchDefaults.colors(
            checkedThumbColor = C.onAccent, checkedTrackColor = C.accent,
            uncheckedThumbColor = C.text2, uncheckedTrackColor = C.card, uncheckedBorderColor = C.card,
        ))
    }
}

@Composable
private fun IconPackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).pressable(Haptic.SELECT, onClick = onClick).padding(vertical = 12.dp, horizontal = S.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = T.body, color = if (selected) C.accent else C.text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Resize a placed widget by whole grid cells. */
@Composable
fun WidgetResize(state: LauncherState, item: Item, onDismiss: () -> Unit) {
    var sx by remember { mutableStateOf(item.spanX) }
    var sy by remember { mutableStateOf(item.spanY) }
    Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(C.raised).padding(S.l)) {
            Text("Resize widget", style = T.title, color = C.text)
            Spacer(Modifier.height(S.m))
            Stepper("Width", sx, 1, state.columns) { sx = it; state.resizeWidget(item, sx, sy) }
            Stepper("Height", sy, 1, state.rows) { sy = it; state.resizeWidget(item, sx, sy) }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body, color = C.text, modifier = Modifier.weight(1f))
        Text("–", style = T.title, color = C.accent, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.tile)).pressable(Haptic.SELECT) { if (value > min) onChange(value - 1) }.padding(4.dp))
        Text("$value", style = T.body.copy(fontWeight = FontWeight.SemiBold), color = C.text, modifier = Modifier.padding(horizontal = S.m))
        Text("+", style = T.title, color = C.accent, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.tile)).pressable(Haptic.SELECT) { if (value < max) onChange(value + 1) }.padding(4.dp))
    }
}
