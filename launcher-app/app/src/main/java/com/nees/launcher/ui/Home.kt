package com.nees.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nees.launcher.App
import com.nees.launcher.AppRepo
import com.nees.launcher.Item
import com.nees.launcher.ItemType
import com.nees.launcher.LauncherState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Shared drag state while an icon is lifted. */
private class Drag {
    var item by mutableStateOf<Item?>(null)
    var offset by mutableStateOf(Offset.Zero) // px in page-grid space
    var overId by mutableLongStateOf(-1L)     // item being hovered (folder target)
}

@Composable
fun HomeScreen(
    state: LauncherState,
    onOpenDrawer: () -> Unit,
    onLongItem: (Item) -> Unit,
    onEmptyLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    state.version // subscribe
    val haptics = rememberHaptics()
    val pager = rememberPagerState(pageCount = { state.pageCount })
    val drag = remember { Drag() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = drag.item == null,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                PageGrid(state, pageIndex, drag, haptics, onLongItem, onEmptyLongPress, onDoubleTap, onOpenDrawer, onSwipeDown)
            }
        }

        // page dots
        if (state.pageCount > 1) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(state.pageCount) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 7.dp else 5.dp)
                            .clip(CircleShape).background(if (i == pager.currentPage) C.text else C.text3),
                    )
                }
            }
        }

        Dock(state, haptics, onLongItem)
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** One home page: an absolutely-positioned grid with long-press drag. */
@Composable
private fun PageGrid(
    state: LauncherState,
    pageIndex: Int,
    drag: Drag,
    haptics: (Int) -> Unit,
    onLongItem: (Item) -> Unit,
    onEmptyLong: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    state.version
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(horizontal = 8.dp)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onEmptyLong() }, onDoubleTap = { onDoubleTap() }) }
            .pointerInput(Unit) { detectSwipeUpDown(onUp = { onSwipeUp() }, onDown = { onSwipeDown() }) },
    ) {
        val density = LocalDensity.current
        val cellW = maxWidth / state.columns
        val cellH = maxHeight / state.rows
        val cellWpx = with(density) { cellW.toPx() }
        val cellHpx = with(density) { cellH.toPx() }

        state.page(pageIndex).forEach { item ->
            val lifted = drag.item === item
            Box(
                Modifier
                    .offset(x = cellW * item.x, y = cellH * item.y)
                    .size(cellW * item.spanX, cellH * item.spanY)
                    .graphicsLayer {
                        if (lifted) {
                            translationX = drag.offset.x - (item.x + 0.5f) * cellWpx
                            translationY = drag.offset.y - (item.y + 0.5f) * cellHpx
                            scaleX = 1.1f; scaleY = 1.1f; alpha = 0.85f
                        }
                        if (drag.overId == item.id) { scaleX = 0.85f; scaleY = 0.85f }
                    },
            ) {
                when (item.type) {
                    ItemType.WIDGET -> WidgetCell(item, Modifier.fillMaxSize().padding(2.dp))
                    else -> HomeCell(
                        state, item, cellW,
                        onTap = {
                            if (item.type == ItemType.FOLDER) onLongItem(item) else AppRepo.launch(state.ctx, item.appKey)
                        },
                        onLong = { onLongItem(item) },
                        onDragStart = {
                            haptics(Haptic.LONG)
                            drag.item = item
                            drag.offset = Offset((item.x + 0.5f) * cellWpx, (item.y + 0.5f) * cellHpx)
                        },
                        onDrag = { delta ->
                            drag.offset += delta
                            val tx = (drag.offset.x / cellWpx).toInt().coerceIn(0, state.columns - 1)
                            val ty = (drag.offset.y / cellHpx).toInt().coerceIn(0, state.rows - 1)
                            drag.overId = state.occupied(pageIndex, tx, ty, ignore = item)?.takeIf { it.id != item.id }?.id ?: -1L
                        },
                        onDrop = {
                            val tx = (drag.offset.x / cellWpx).toInt().coerceIn(0, state.columns - 1)
                            val ty = (drag.offset.y / cellHpx).toInt().coerceIn(0, state.rows - 1)
                            val onto = state.occupied(pageIndex, tx, ty, ignore = item)
                            if (onto != null && onto.id != item.id) {
                                if (!state.makeFolder(item, onto)) haptics(Haptic.SELECT)
                                else haptics(Haptic.SUCCESS)
                            } else {
                                state.move(item, pageIndex, tx, ty, dock = false)
                            }
                            drag.item = null; drag.overId = -1L
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCell(
    state: LauncherState,
    item: Item,
    cellW: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onLong: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
) {
    val iconSize = cellW * 0.62f
    Box(
        Modifier.fillMaxSize().padding(2.dp)
            .pointerInput(item.id) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() })
            }
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, delta -> change.consume(); onDrag(delta) },
                    onDragEnd = { onDrop() },
                    onDragCancel = { onDrop() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (item.type) {
            ItemType.FOLDER -> FolderPreview(item, iconSize)
            else -> LabeledIcon(item.appKey, item.label, iconSize)
        }
    }
}

@Composable
fun FolderPreview(item: Item, iconSize: androidx.compose.ui.unit.Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(iconSize).clip(RoundedCornerShape(iconSize / 4)).background(C.glass).padding(iconSize * 0.12f),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                for (r in 0 until 2) Row {
                    for (c in 0 until 2) {
                        val k = item.children.getOrNull(r * 2 + c)
                        Box(Modifier.size(iconSize * 0.33f).padding(1.dp)) {
                            if (k != null) AppIcon(k, iconSize * 0.3f)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(item.label, style = T.iconLabel, color = C.text, maxLines = 1)
    }
}

/** Embeds a live Android widget via the shared AppWidgetHost. */
@Composable
fun WidgetCell(item: Item, modifier: Modifier = Modifier) {
    val provider = remember(item.widgetId) {
        runCatching { App.instance.widgetManager.getAppWidgetInfo(item.widgetId) }.getOrNull()
    }
    if (provider == null) {
        Box(modifier.clip(RoundedCornerShape(Radius.tile)).background(C.card))
        return
    }
    AndroidView(
        factory = { ctx ->
            App.instance.widgetHost.createView(ctx, item.widgetId, provider).apply {
                setAppWidget(item.widgetId, provider)
            }
        },
        modifier = modifier.clip(RoundedCornerShape(Radius.tile)),
    )
}

@Composable
private fun Dock(state: LauncherState, haptics: (Int) -> Unit, onLongItem: (Item) -> Unit) {
    val dock = state.dock()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dock.forEach { item ->
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                when (item.type) {
                    ItemType.FOLDER -> Box(Modifier.pressable(Haptic.LIGHT, onLongClick = { onLongItem(item) }) { onLongItem(item) }) { FolderPreview(item, 48.dp) }
                    else -> Box(
                        Modifier.pressable(Haptic.LIGHT, onLongClick = { onLongItem(item) }) { AppRepo.launch(state.ctx, item.appKey) },
                    ) { LabeledIcon(item.appKey, item.label, 48.dp, showLabel = false) }
                }
            }
        }
    }
}

/** Fires [onUp] on a clear upward drag, [onDown] on a clear downward drag, once per gesture. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectSwipeUpDown(onUp: () -> Unit, onDown: () -> Unit) {
    val threshold = 80.dp.toPx()
    var total = 0f
    var fired = false
    detectVerticalDragGestures(
        onDragStart = { total = 0f; fired = false },
    ) { change, delta ->
        change.consume()
        total += delta
        if (!fired && total < -threshold) { fired = true; onUp() }
        if (!fired && total > threshold) { fired = true; onDown() }
    }
}
