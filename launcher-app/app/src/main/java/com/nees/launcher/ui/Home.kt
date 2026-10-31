package com.nees.launcher.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nees.launcher.AppRepo
import com.nees.launcher.Fav
import com.nees.launcher.LauncherState

@Composable
fun HomeScreen(
    state: LauncherState,
    onOpenDrawer: () -> Unit,
    onLongFav: (Fav) -> Unit,
    onEmptyLong: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeDown: () -> Unit,
    onClockTap: () -> Unit,
    onAlarmTap: () -> Unit,
) {
    AppRepo.apps.value // subscribe so labels fill in once loaded
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }, onLongPress = { onEmptyLong() }) }
            .pointerInput(Unit) { detectSwipes(onUp = onOpenDrawer, onDown = onSwipeDown) },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical = S.xl),
        ) {
            Spacer(Modifier.height(S.xxl))
            NativeWidgets(state, onClockTap, onAlarmTap)
            state.widgetIds.forEach { id ->
                Spacer(Modifier.height(S.m))
                ThirdPartyWidget(id, Modifier.padding(horizontal = S.edge))
            }

            Spacer(Modifier.weight(1f))

            // Favourites, left-aligned text, generous spacing. The calm core of the home screen.
            Column(Modifier.fillMaxWidth().padding(horizontal = S.edge), verticalArrangement = Arrangement.spacedBy(S.s)) {
                state.favourites.forEach { fav ->
                    Text(
                        state.label(fav),
                        style = T.fav,
                        color = C.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                            .tappable(Haptic.LIGHT, onLongClick = { onLongFav(fav) }) { AppRepo.launch(fav.key) }
                            .padding(vertical = S.xs),
                    )
                }
                if (state.favourites.isEmpty()) {
                    Text("Swipe up for apps", style = T.list, color = C.text3)
                }
            }
            Spacer(Modifier.height(S.l))
        }
    }
}

/** Up-swipe opens the drawer; down-swipe pulls notifications. One action per gesture. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectSwipes(onUp: () -> Unit, onDown: () -> Unit) {
    val threshold = 90.dp.toPx()
    var total = 0f
    var fired = false
    detectVerticalDragGestures(onDragStart = { total = 0f; fired = false }) { change, delta ->
        change.consume()
        total += delta
        if (!fired && total < -threshold) { fired = true; onUp() }
        if (!fired && total > threshold) { fired = true; onDown() }
    }
}
