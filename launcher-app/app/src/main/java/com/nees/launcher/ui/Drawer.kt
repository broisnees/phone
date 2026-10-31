package com.nees.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun close() { keyboard?.hide(); onClose() }
    fun launch(key: String) { keyboard?.hide(); AppRepo.launch(key); onClose() }

    // Auto-focus the search field and raise the keyboard when the drawer opens; hide it on close.
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    Column(Modifier.fillMaxSize().background(C.scrim).statusBarsPadding().padding(horizontal = S.edge)) {
        Spacer(Modifier.height(S.l))
        Box(Modifier.fillMaxWidth()) {
            if (query.isEmpty()) Text("Search", style = T.display, color = C.text3)
            BasicTextField(
                query, { query = it }, singleLine = true,
                textStyle = T.display.copy(color = C.text), cursorBrush = SolidColor(C.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { list.firstOrNull()?.let { launch(it.key) } }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.height(S.l))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            items(list, key = { it.key }) { app ->
                val pinned = state.isFav(app.key)
                Row(
                    Modifier.fillMaxWidth()
                        .tappable(Haptic.LIGHT, onLongClick = { onLongApp(app) }) { launch(app.key) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.label, style = T.list, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (pinned) {
                        Spacer(Modifier.width(S.s))
                        Text("· pinned", style = T.sub, color = C.text3, maxLines = 1)
                    }
                }
            }
            if (list.isEmpty()) item { Text("No apps", style = T.list, color = C.text3, modifier = Modifier.padding(vertical = 14.dp)) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
