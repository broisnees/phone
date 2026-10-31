package com.nees.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nees.launcher.AppInfo
import com.nees.launcher.AppRepo
import com.nees.launcher.LauncherState

@Composable
fun Drawer(state: LauncherState, onClose: () -> Unit, onLongApp: (AppInfo) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val all by AppRepo.apps
    val list = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.label.contains(q, ignoreCase = true) }
    }
    fun launchTop() { list.firstOrNull()?.let { AppRepo.launch(ctx, it.key); onClose() } }

    Column(Modifier.fillMaxSize().background(C.scrim).statusBarsPadding().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(Radius.field)).background(C.raised).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = C.text2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search apps", style = T.body, color = C.text3)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = T.body.copy(color = C.text), cursorBrush = SolidColor(C.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { launchTop() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) Text("Clear", style = T.caption, color = C.accent, modifier = Modifier.pressable(Haptic.SELECT) { query = "" })
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(GridCells.Fixed(state.columns), Modifier.fillMaxSize()) {
            items(list, key = { it.key }) { app ->
                Column(
                    Modifier.padding(vertical = 10.dp).pressable(Haptic.LIGHT, onLongClick = { onLongApp(app) }) {
                        AppRepo.launch(ctx, app.key); onClose()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app.key, 52.dp)
                    Spacer(Modifier.height(5.dp))
                    Text(app.label, style = T.iconLabel, color = C.text, maxLines = 1)
                }
            }
        }
    }
}
