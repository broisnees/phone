package com.nees.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Holds the live home layout and all mutations, persisting after each change. */
class LauncherState(val ctx: Context) {
    val store = Store(ctx)
    val items = mutableStateListOf<Item>()
    var columns by mutableIntStateOf(store.columns)
    var rows by mutableIntStateOf(store.rows)
    var pageCount by mutableIntStateOf(1)
    var version by mutableIntStateOf(0) // bump to force recomposition after in-place edits

    private fun snapshot(): Layout = Layout().also {
        it.items.addAll(items); it.columns = columns; it.rows = rows; it.pageCount = pageCount
    }

    fun save() { store.save(snapshot()); version++ }

    fun load() {
        val l = store.load()
        items.clear()
        if (l != null) {
            items.addAll(l.items)
            columns = l.columns; rows = l.rows; pageCount = l.pageCount.coerceAtLeast(1)
        }
    }

    // ---- queries
    fun page(p: Int) = items.filter { !it.inDock && it.page == p }
    fun dock() = items.filter { it.inDock }.sortedBy { it.x }
    fun item(id: Long) = items.firstOrNull { it.id == id }

    fun occupied(page: Int, x: Int, y: Int, ignore: Item? = null): Item? = items.firstOrNull {
        !it.inDock && it.page == page && it !== ignore &&
            x in it.x until (it.x + it.spanX) && y in it.y until (it.y + it.spanY)
    }

    fun freeCell(page: Int): Pair<Int, Int>? {
        for (y in 0 until rows) for (x in 0 until columns) if (occupied(page, x, y) == null) return x to y
        return null
    }

    // ---- mutations
    fun addApp(app: AppInfo): Boolean {
        if (items.any { it.type == ItemType.APP && it.appKey == app.key }) return false
        for (p in 0 until pageCount) {
            val c = freeCell(p)
            if (c != null) { items.add(appItem(app, p, c.first, c.second)); save(); return true }
        }
        // new page
        pageCount++
        items.add(appItem(app, pageCount - 1, 0, 0)); save(); return true
    }

    private fun appItem(app: AppInfo, page: Int, x: Int, y: Int) =
        Item(store.nextId(), ItemType.APP, app.pkg, app.cls, app.label, page = page, x = x, y = y)

    fun remove(item: Item) {
        items.remove(item)
        if (item.type == ItemType.WIDGET && item.widgetId >= 0) runCatching { App.instance.widgetHost.deleteAppWidgetId(item.widgetId) }
        save()
    }

    /** Move an item to a home cell (or dock slot when [dock]). Returns false if blocked. */
    fun move(item: Item, page: Int, x: Int, y: Int, dock: Boolean): Boolean {
        if (dock) {
            val slots = dock().filter { it !== item }
            if (slots.size >= 5 && !item.inDock) return false
            item.inDock = true; item.page = 0; item.x = x.coerceIn(0, 4); item.y = 0
            // re-pack dock x's
            dock().filter { it !== item }.forEachIndexed { i, d -> if (d.x >= item.x) d.x = i + 1 }
            save(); return true
        }
        if (x + item.spanX > columns || y + item.spanY > rows || x < 0 || y < 0) return false
        val target = occupied(page, x, y, ignore = item)
        if (target != null) return false
        item.inDock = false; item.page = page; item.x = x; item.y = y
        save(); return true
    }

    fun makeFolder(dragged: Item, onto: Item): Boolean {
        if (dragged.type != ItemType.APP || onto.type !in listOf(ItemType.APP, ItemType.FOLDER)) return false
        if (onto.type == ItemType.FOLDER) {
            onto.children.add(dragged.appKey); items.remove(dragged); save(); return true
        }
        val folder = Item(
            store.nextId(), ItemType.FOLDER, label = "Folder",
            children = mutableListOf(onto.appKey, dragged.appKey),
            page = onto.page, x = onto.x, y = onto.y, inDock = onto.inDock,
        )
        items.remove(onto); items.remove(dragged); items.add(folder); save(); return true
    }

    fun renameFolder(folder: Item, name: String) {
        val i = items.indexOf(folder); if (i < 0) return
        items[i] = folder.copy(label = name.ifBlank { "Folder" }); save()
    }

    fun removeFromFolder(folder: Item, appKey: String) {
        folder.children.remove(appKey)
        if (folder.children.size == 1) {
            // collapse back to a single app
            val only = AppRepo.find(folder.children.first())
            if (only != null) { items[items.indexOf(folder)] = appItem(only, folder.page, folder.x, folder.y).also { it.inDock = folder.inDock } }
        }
        save()
    }

    fun addWidget(id: Int, spanX: Int, spanY: Int): Boolean {
        for (p in 0 until pageCount) {
            for (y in 0..(rows - spanY)) for (x in 0..(columns - spanX)) {
                if ((0 until spanX).all { dx -> (0 until spanY).all { dy -> occupied(p, x + dx, y + dy) == null } }) {
                    items.add(Item(store.nextId(), ItemType.WIDGET, widgetId = id, page = p, x = x, y = y, spanX = spanX, spanY = spanY))
                    save(); return true
                }
            }
        }
        pageCount++
        items.add(Item(store.nextId(), ItemType.WIDGET, widgetId = id, page = pageCount - 1, x = 0, y = 0, spanX = spanX, spanY = spanY))
        save(); return true
    }

    fun resizeWidget(item: Item, spanX: Int, spanY: Int) {
        item.spanX = spanX.coerceIn(1, columns); item.spanY = spanY.coerceIn(1, rows); save()
    }

    /** First-run: put common apps in the dock and the rest is reachable from the drawer. */
    fun seedIfEmpty() {
        if (items.isNotEmpty()) return
        val apps = AppRepo.apps.value
        val wanted = listOf("dialer", "phone", "contacts", "messag", "messenger", "chrome", "camera")
        val dockApps = mutableListOf<AppInfo>()
        for (w in wanted) {
            val a = apps.firstOrNull { it.pkg.contains(w, true) || it.label.contains(w, true) } ?: continue
            if (a !in dockApps) dockApps.add(a)
            if (dockApps.size == 4) break
        }
        dockApps.forEachIndexed { i, a ->
            items.add(Item(store.nextId(), ItemType.APP, a.pkg, a.cls, a.label, x = i, inDock = true))
        }
        save()
    }
}
