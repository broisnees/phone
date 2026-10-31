package com.nees.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nees.launcher.AppInfo
import com.nees.launcher.AppRepo
import com.nees.launcher.LauncherState

@Composable
fun Drawer(state: LauncherState, onClose: () -> Unit, onLongApp: (AppInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    val base = state.drawerApps()
    val list = remember(base, query) {
        val q = query.trim()
        if (q.isEmpty()) base else base.filter { it.label.contains(q, ignoreCase = true) }
    }
    fun launchTop() { list.firstOrNull()?.let { AppRepo.launch(it.key); onClose() } }

    Column(Modifier.fillMaxSize().background(C.scrim).statusBarsPadding().padding(horizontal = S.edge)) {
        Spacer(Modifier.height(S.l))
        Box(Modifier.fillMaxWidth()) {
            if (query.isEmpty()) Text("Search", style = T.display, color = C.text3)
            BasicTextField(
                query, { query = it }, singleLine = true,
                textStyle = T.display.copy(color = C.text), cursorBrush = SolidColor(C.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { launchTop() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(S.l))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp)) {
            items(list, key = { it.key }) { app ->
                Text(
                    app.label,
                    style = T.list,
                    color = C.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                        .tappable(Haptic.LIGHT, onLongClick = { onLongApp(app) }) { AppRepo.launch(app.key); onClose() }
                        .padding(vertical = 14.dp),
                )
            }
            if (list.isEmpty()) item { Text("No apps", style = T.list, color = C.text3, modifier = Modifier.padding(vertical = 14.dp)) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
